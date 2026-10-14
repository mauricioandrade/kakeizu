# Kakeizu — Leitor de comprovantes com IA local

> **Kakeizu** = *Kakeibo* (家計簿, o caderno japonês de controle de gastos) + *Kakuzu* (o Akatsuki que só pensa em dinheiro).
> Um leitor de comprovantes para pequenos comerciantes que roda 100% local: a foto entra, os dados saem estruturados, e nada sai da máquina do dono do negócio.

---

## 0. Contexto para o Claude

- Projeto para o **Hacktoberfest Open-Source AI Challenge — Semana 2 (tema "Side Hustle")** do DEV.
- **Prazo duro:** 18/10/2026, 23h59 PDT. Repositório novo, criado nesta semana.
- Critério de maior peso é a escrita do post, então o código precisa ser **simples, legível e demonstrável**. Na dúvida, menos é mais.
- O post e o README serão em inglês. **Todo identificador de código, commit e comentário fica em inglês.** Este documento fica em português.
- Ambiente do desenvolvedor: **Windows 11 + WSL2**. O projeto roda no WSL; o Ollama pode estar no Windows (ver seção 3).

### Regras de trabalho

1. Siga as fases da seção 9 **em ordem**. Ao fim de cada fase, pare, resuma o que foi feito e aguarde confirmação.
2. Não adicione dependências fora da seção 2 sem perguntar antes.
3. Não use preview features do Java salvo onde este documento disser explicitamente.
4. Sempre que introduzir um recurso novo do Java 25 ou do Quarkus, deixe um bloco `📚 Estudo` curto no PR/resumo da fase explicando o que é e por que foi usado.

---

## 1. Problema e usuário

**Quem paga:** dono ou gerente de pequeno comércio (posto, mercadinho, restaurante) que recebe dezenas de comprovantes por dia e fecha o caixa na mão.

**Dor:** digitar valor, data e fornecedor de cada nota/Pix/cupom numa planilha. Ferramentas na nuvem existem, mas exigem mandar dados financeiros do negócio para servidor de terceiros e cobram por documento.

**Proposta:** tira a foto → Gemma (local) extrai os dados → sistema valida → humano confirma → entra no resumo do dia.

> **Princípio do projeto:** *a IA sugere, o domínio valida, o humano decide.* Nenhum comprovante é confirmado automaticamente.

### Escopo do MVP

- Upload de uma imagem por vez (JPG/PNG)
- Extração de: tipo, valor, data, nome do emissor/pagador, CNPJ/CPF quando houver, identificador único (chave NF-e, ID Pix ou código de autorização do cartão)
- Tela de revisão com campos editáveis e destaque para o que falhou na validação
- Resumo do dia: total por tipo e por categoria
- Detecção de duplicata (mesma imagem ou mesmo identificador único)

### Fora do escopo

Autenticação, multiusuário, integração com SEFAZ, exportação contábil, app mobile, upload em lote (fica como extra opcional na fase 8).

---

## 2. Stack

| Camada | Escolha |
|---|---|
| Linguagem | Java 25 (LTS) |
| Framework | Quarkus — versão estável mais recente (≥ 3.31, primeira com suporte completo a Java 25) |
| Build | Maven |
| IA | `quarkus-langchain4j-ollama` + Ollama |
| Modelo | Gemma multimodal. Default `gemma3:4b`; nome configurável por propriedade. Verifique se há versão mais nova do Gemma com visão no Ollama e use-a se houver |
| Banco | PostgreSQL + Flyway (`quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-hibernate-orm-panache`) |
| Web | `quarkus-rest` + `quarkus-rest-qute` + htmx (via `<script>` pinado em versão exata) |
| Validação | `quarkus-hibernate-validator` |
| Segurança | `quarkus-security` (núcleo, para o form login). Usuário único `admin`, senha com hash PBKDF2 do próprio JDK |
| Testes | `quarkus-junit5`, `rest-assured`, `quarkus-junit5-mockito` |

**Sem Lombok. Sem Redis** (não há requisito de cache neste projeto).

---

## 3. Ambiente local

- **Postgres:** use o **Dev Services** do Quarkus no modo dev/test (sobe um container sozinho). Mantenha também um `docker-compose.yml` com `postgres:16-alpine` para rodar fora do modo dev.
- **Ollama:** dois cenários possíveis, documente ambos no README:
  - Ollama dentro do WSL → `http://localhost:11434`
  - Ollama no Windows (melhor uso de GPU) → com `networkingMode=mirrored` no `.wslconfig`, `localhost` funciona; sem isso, use o IP do host Windows
- A URL do Ollama e o nome do modelo vêm de `application.properties` com override por variável de ambiente (`OLLAMA_BASE_URL`, `KAKEIZU_MODEL`).
- Segredos e overrides locais em `application-local.properties` (no `.gitignore`).
- Timeout da chamada ao modelo generoso (60s): primeira inferência local é lenta.

---

## 4. Convenções (adaptadas de Spring para Quarkus)

- Injeção **somente por construtor**, campos `private final`. Construtor único dispensa `@Inject`.
- Configuração via `@ConfigProperty` **nos parâmetros do construtor** (equivalente ao `@Value` do Spring), ou `@ConfigMapping` com interface para grupos de propriedades.
- DTOs e value objects como **records**.
- **`BigDecimal`** para qualquer valor monetário. Nunca `double`.
- Clean Architecture: o pacote `domain` não importa nada de Quarkus, JPA ou LangChain4j.
- Exceções de domínio próprias; o handler REST devolve mensagens genéricas.
- **Conventional Commits em inglês**, um commit por concern.

---

## 5. Estrutura de pacotes

```
dev.mauricioandrade.kakeizu/
├── domain/
│   ├── model/        # Receipt (sealed), ReceiptFields, Money, Cnpj, NfeAccessKey, PixEndToEndId, ReceiptStatus, Category
│   ├── port/
│   │   ├── in/       # ExtractReceiptUseCase, ConfirmReceiptUseCase, DailySummaryUseCase
│   │   └── out/      # ReceiptReader (IA), ReceiptRepository, ImageStorage
│   ├── service/      # ReceiptValidator, ExtractionMapper
│   └── exception/
├── application/
│   └── usecase/      # implementações dos ports de entrada
└── infrastructure/
    ├── ai/           # GemmaReceiptReader + AI Service do LangChain4j + ExtractedReceipt (record bruto)
    ├── persistence/  # entidades Panache + adapter do repositório
    ├── storage/      # imagens em disco local (diretório configurável)
    ├── security/     # login do admin: hash da senha, bloqueio por tentativas, IdentityProviders
    ├── config/       # producers CDI (Clock, serviços do domínio)
    └── web/          # resources REST, templates Qute, request/response records
```

---

## 6. Modelo de domínio

```java
public sealed interface Receipt permits FiscalInvoice, FiscalCoupon, PixReceipt, CardReceipt, ChequeReceipt {
    Money amount();
    LocalDate issuedOn();
    String counterparty();
}

public record FiscalInvoice(Money amount, LocalDate issuedOn, String counterparty,
                            Cnpj issuerCnpj, NfeAccessKey accessKey) implements Receipt {}

public record FiscalCoupon(Money amount, LocalDate issuedOn, String counterparty,
                           Cnpj issuerCnpj, NfeAccessKey accessKey) implements Receipt {}

// Fase 7: o Pix feito na maquininha (Getnet, Stone...) imprime o ID da transação da máquina, não o
// end-to-end ID; por isso endToEndId é opcional e, se vier, é validado.
public record PixReceipt(Money amount, LocalDate issuedOn, String counterparty,
                         PixEndToEndId endToEndId) implements Receipt {}

// Via do lojista da maquininha (Stone, Cielo...). Decidido no spike da fase 2:
// é o comprovante mais comum do dia num posto/mercadinho.
// Fase 7: também o comprovante de compra no app do banco, que nunca mostra o CNPJ da loja.
// CNPJ e código de autorização são opcionais; se o CNPJ vier, os DVs são validados.
public record CardReceipt(Money amount, LocalDate issuedOn, String counterparty,
                          Cnpj merchantCnpj, String authorizationCode) implements Receipt {}

// Fase 7: cheque, só se o estabelecimento aceitar (KAKEIZU_SHOP_ACCEPTS_CHEQUES, padrão false; se desligado,
// cheque ou NFC-e paga em cheque viram erro na revisão). counterparty é quem paga (o titular impresso).
// CMC-7 opcional (o modelo não lê a linha magnética da foto); se vier, os 3 DVs módulo 10 são validados e ele
// deduplica. Cheque pré-datado ou com mais de 6 meses (prescrito ou ano mal lido) gera só alerta.
public record ChequeReceipt(Money amount, LocalDate issuedOn, String counterparty, Cmc7 cmc7) implements Receipt {}
```

Fase 7 (decidido com o usuário): cada comprovante confirmado também guarda `Payment(time, method, installments, brand)`
(`PaymentMethod`: CASH, CREDIT, DEBIT, PIX, CHEQUE, OTHER; `CardBrand`: VISA, MASTERCARD, ELO, AMEX, HIPERCARD, OTHER) e
`Direction` (SALE/EXPENSE). Regras: Pix é sempre PIX; cartão é CREDIT ou DEBIT; só crédito parcela (1–24); bandeira só
em cartão. Venda/gasto vem do CNPJ da loja (`KAKEIZU_SHOP_CNPJ`): quem recebeu foi a loja → venda; outro CNPJ → gasto;
sem CNPJ ou com CPF → a pessoa escolhe. No cheque é ao contrário (o CNPJ é de quem paga): CNPJ da loja → gasto,
outro → venda. Migration `V2__add_payment_details.sql`.

Entidade persistida: `ReceiptRecord` com `id`, `type`, campos acima, `category`, `status` (`PENDING_REVIEW`, `CONFIRMED`, `REJECTED`), `imageSha256`, `rawModelOutput` (JSON, para depuração e para o post), `createdAt`.

### Value objects com validação real

Aqui está o diferencial técnico do projeto: o modelo erra, o domínio pega.

- **`Money`**: aceita `"1234.56"` e `"1.234,56"`; escala 2; rejeita negativo.
- **`Cnpj`**: 12 caracteres alfanuméricos + 2 DVs numéricos (CNPJ alfanumérico, IN RFB 2.229/2024, emitido desde jul/2026). Cada caractere vale ASCII − 48, o que mantém válidos os CNPJs só com dígitos.
- **`NfeAccessKey`** (NF-e e NFC-e): 44 caracteres + dígito verificador módulo 11; só a parte do CNPJ pode ser alfanumérica. Os dígitos 3–6 (AAMM) e 7–20 (CNPJ do emissor) devem bater com os outros campos; se não baterem, gera alerta na revisão, não erro fatal.
- **`PixEndToEndId`**: 32 caracteres, começa com `E`, alfanumérico.

> 📚 **Estudo — por que validar o que a IA devolveu?**
> Modelos pequenos rodando local são ótimos em "ler" mas erram dígitos em letra miúda. Dígitos verificadores (módulo 11 na chave da NF-e, os dois DVs do CNPJ) existem justamente para detectar erro de digitação, e servem igualmente bem para detectar erro de leitura da IA. É validação determinística barata em cima de uma saída probabilística.

---

## 7. Fronteira com a IA

```java
// infrastructure/ai — o que o modelo devolve, sem confiar em nada
public record ExtractedReceipt(
        String type,            // "NFE" | "NFCE" | "PIX" | "CARD" | "UNKNOWN"
        String amount,
        String date,            // ISO-8601
        String counterparty,
        String taxId,
        String uniqueId,
        String suggestedCategory) {}

@RegisterAiService
public interface ReceiptExtractionService {
    @SystemMessage("""
        You read Brazilian payment receipts: NF-e, NFC-e/cupom fiscal, Pix transfer receipts
        and card machine slips.
        Return only the requested fields. If a field is not visible, return null. Never guess digits.
        """)
    @UserMessage("Extract the receipt data from this image.")
    ExtractedReceipt extract(@ImageUrl Image image);
}
```

- `GemmaReceiptReader` implementa o port `ReceiptReader`, monta a `Image` em base64 e chama o serviço. Confirme na documentação da versão do quarkus-langchain4j a forma atual de passar imagem (anotação e tipo).
- O domínio não enxerga `ExtractedReceipt`: o adapter converte para `ReceiptFields` (domínio, todos `String`). O formulário de revisão devolve o mesmo `ReceiptFields` corrigido, então saída do modelo e correção do humano passam pela **mesma** validação.
- `ExtractionMapper` (domínio) converte `ReceiptFields` → `ExtractionResult`, que contém o `Receipt` candidato (ou campos parciais) **e a lista de problemas de validação**. Use `switch` com pattern matching sobre o tipo.
- Categorias iniciais (enum): `FUEL_SUPPLY`, `GOODS`, `SERVICES`, `UTILITIES`, `PAYROLL`, `OTHER`. O modelo sugere, o usuário troca na revisão.
- Guarde o JSON bruto do modelo em `rawModelOutput`.

---

## 8. Telas e endpoints

| Rota | Função |
|---|---|
| `GET /` | Upload + lista dos comprovantes do dia |
| `POST /receipts` | Recebe a imagem, checa duplicata por SHA-256, extrai, salva como `PENDING_REVIEW`, redireciona para revisão |
| `GET /receipts/{id}/review` | Imagem ao lado do formulário editável; problemas de validação destacados |
| `POST /receipts/{id}/confirm` | Revalida com os dados corrigidos e confirma |
| `POST /receipts/{id}/reject` | Rejeita |
| `GET /summary?date=` | Totais do dia por tipo e categoria (só `CONFIRMED`) |
| `GET /report?from=&to=` | Vendas confirmadas no período: por hora, dia da semana, forma, parcelas e bandeira. NF-e/NFC-e paga com cartão, Pix ou cheque é pareada com o slip/Pix/cheque de mesmo valor, mesmo dia e até 15 min de diferença e conta uma vez só (fase 7) |

Qute + htmx, sem build de frontend. Visual limpo e legível no celular (o dono do posto vai usar no telefone).

---

## 9. Fases

Cada fase termina com testes passando, commit(s) e resumo.

**Fase 1 — Esqueleto**
Projeto Quarkus com Java 25, extensões da seção 2, estrutura de pacotes vazia, `docker-compose.yml`, `.gitignore`, README mínimo.
`chore: bootstrap quarkus project with java 25`

**Fase 2 — Spike de IA**
Endpoint temporário que recebe uma imagem e devolve o `ExtractedReceipt` cru. Objetivo único: provar que Ollama + Gemma leem um comprovante. Coloque 3 imagens de exemplo anonimizadas em `src/test/resources/samples/`.
`feat(ai): extract raw receipt data with local gemma`

**Fase 3 — Domínio**
Value objects, `Receipt` selado, `ReceiptValidator`, `ExtractionMapper`. Testes unitários puros cobrindo DVs de CNPJ e chave NF-e, parsing de `Money` nos dois formatos e campos nulos.
`feat(domain): add receipt model and validation`

**Fase 4 — Persistência**
Migration Flyway `V1__create_receipts.sql`, entidade Panache, adapter do repositório, deduplicação por hash e por identificador único.
`feat(persistence): store receipts with flyway schema`

**Fase 5 — Casos de uso**
Extract, confirm, reject, daily summary. Teste com o port `ReceiptReader` mockado (sem chamar o modelo).
`feat(app): add receipt use cases`

**Fase 6 — Web**
Templates Qute, fluxo upload → revisão → confirmação → resumo.
`feat(web): add upload, review and summary pages`

**Fase 7 — Prova com dados reais**
Rodar com comprovantes reais, ajustar o prompt, registrar no README uma tabela simples: quantos campos o modelo acertou de primeira vs. quantos a validação pegou. Esse número vai para o post.
`docs: add accuracy notes from real receipts`

**Fase 8 — Extras (só se sobrar tempo)**
Upload em lote com structured concurrency (preview, exige `--enable-preview` no compilador e no runtime; isole numa classe só) e `ScopedValue` para propagar um id de correlação nos logs.

---

## 10. Java 25 que deve aparecer no código

Use onde fizer sentido natural, sem forçar:

- **Sealed interfaces + records + `switch` com pattern matching** no `ExtractionMapper` e no resumo diário (switch exaustivo, sem `default`).
- **Record patterns** para desestruturar (`case PixReceipt(var amount, _, _, var e2e) -> ...`).
- **Variáveis não nomeadas `_`** em patterns e em `catch` onde a exceção não é usada.
- **Flexible constructor bodies (JEP 513)**: construtor alternativo `Money(String raw)` que normaliza e valida a string **antes** de chamar `this(...)`.
- **Scoped values (JEP 506)**: fase 8.
- **Module import declarations (JEP 511)**: opcional em testes ou scripts, não no código de produção.

> 📚 **Estudo — flexible constructor bodies**
> Até o Java 24, a primeira linha de um construtor tinha que ser `this(...)` ou `super(...)`. No 25 dá para escrever lógica antes, desde que não use `this`. Em um record, isso permite um construtor auxiliar que limpa `"R$ 1.234,56"` e só então delega ao canônico, sem método estático de fábrica.

---

## 11. Definição de pronto

- `./mvnw verify` verde
- `./mvnw quarkus:dev` sobe com Dev Services e o fluxo completo funciona com Ollama rodando
- README em inglês: o problema, como rodar (incluindo os dois cenários de Ollama no WSL), screenshots, tabela de acurácia da fase 7, e a seção *Why open matters* (dados financeiros não saem da máquina; custo zero por documento; modelo trocável por propriedade)
- Nenhuma credencial ou comprovante real não anonimizado no repositório

---

## 12. Checklist de segurança

Triado para a realidade do projeto: app local, um usuário, sem contas. "Fase" indica quando o item é tratado; os demais ficam para a revisão final antes do post.

> ✅ **Decidido — acesso pelo celular.** O dono usa no telefone, pela rede do estabelecimento; sem login, qualquer aparelho no mesmo Wi-Fi abriria a URL. Solução: form login do Quarkus com usuário fixo `admin` e senha com hash PBKDF2 (variável `KAKEIZU_ADMIN_PASSWORD_HASH`), sessão em cookie criptografado. Consequência: **HTTPS passa a ser obrigatório na fase 6**, senão a senha e o cookie trafegam em texto puro pelo Wi-Fi.

| Item | Aplica? | Fase | Como |
|---|---|---|---|
| SQL injection | Sim | 4 | Panache só com parâmetros (`?1`, `:nome`); nunca concatenar string em query |
| Migrations | Sim | 4 | Flyway versionado; migration aplicada nunca é editada, só corrigida por uma nova |
| Rollback | Sim | 4–5 | Dados: `@Transactional` por caso de uso. Schema: Flyway community não tem undo, então migrations forward-only + backup antes de migrar |
| Secrets fora do código | Sim | 4 | Env vars / `application-local.properties`; `%prod` sem senha default (falha ao subir se faltar) |
| Logs de segurança | Sim | 4–6 | Não logar dados financeiros (`log-responses` só em `%dev`); logar eventos (upload, confirmação, rejeição, duplicata) com id, sem conteúdo; handler REST com mensagem genérica |
| Validação de inputs | Sim | 3 ✅ / 6 | Domínio valida tudo que vem do modelo e do formulário; na web: Bean Validation nos records, tamanho máximo de upload, tipo da imagem por magic bytes (não pelo `Content-Type`) |
| Sanitização / XSS | Sim | 6 | Qute escapa HTML por padrão; proibido `{raw}`. Saída do modelo é input não confiável (uma imagem pode conter texto malicioso ou *prompt injection*): o domínio valida e o humano revisa |
| Rate limit | Sim | 4 ✅ / 6 | Login: 5 senhas erradas seguidas bloqueiam por 1 min (`LoginThrottle`). Extração: limitar chamadas simultâneas à GPU (semáforo) + `quarkus.http.limits.max-body-size` |
| CORS | Sim | 6 | Não habilitar: o app é same-origin, e o filtro CORS do Quarkus vem desligado |
| HTTPS | Sim | 6 | Obrigatório por causa do login pela rede; TLS nativo do Quarkus + cookie `Secure` |
| Menor privilégio | Sim | 4 / final | Usuário do banco da aplicação sem superuser; container não-root; Ollama não exposto além do host; diretório de imagens com permissão restrita |
| Dependências atualizadas | Sim | final | Dependabot no GitHub; `./mvnw quarkus:update`; htmx pinado com hash SRI |
| Backups automáticos | Sim | final | `pg_dump` agendado + cópia do diretório de imagens; documentar no README |
| Monitoramento / alertas | Parcial | final | Health check de banco e Ollama (`quarkus-smallrye-health`, dependência nova: perguntar); alertas externos fora do escopo local |
| Controle de acesso | Sim | 4 ✅ | Tudo exige login, exceto `/login`; usuário único `admin` (sem multiusuário, seção 1) |
| Expiração de sessão | Sim | 4 ✅ | 30 min de inatividade; cookie `HttpOnly` + `SameSite=Strict`, criptografado com `KAKEIZU_SESSION_KEY` |
| MFA | Não | — | Usuário único, local; a senha + bloqueio por tentativas cobrem o risco de rede local |
