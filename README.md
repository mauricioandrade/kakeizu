# Kakeizu

> *Kakeibo* (家計簿, the Japanese household ledger) + *Kakuzu* (the Akatsuki member who only thinks about money).

A receipt reader for small business owners that runs **100% locally**: a photo goes in, structured data comes out, and nothing leaves the owner's machine.

**The AI suggests, the domain validates, the human decides.**

> 🚧 Work in progress for the DEV Hacktoberfest Open-Source AI Challenge.

## Stack

Java 25 · Quarkus 3.40 · LangChain4j + Ollama · Gemma (vision) · PostgreSQL + Flyway · Qute + htmx

## Running

Requirements: Java 25, Docker, [Ollama](https://ollama.com) with a Gemma vision model.

```bash
ollama pull gemma4:e4b
./mvnw quarkus:dev
```

Dev mode starts PostgreSQL automatically (Dev Services). To run against a standalone database, use `docker compose up -d`.

### Configuration

| Variable | Default |
|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `KAKEIZU_MODEL` | `gemma4:e4b` |
| `KAKEIZU_ADMIN_PASSWORD_HASH` | none in prod (dev/test password: `kakeizu`) |
| `KAKEIZU_SESSION_KEY` | none in prod; any random string of 16+ characters |
| `KAKEIZU_ZONE` | `America/Sao_Paulo` |
| `KAKEIZU_MAX_CONCURRENT_READINGS` | `1` (one GPU, one reading at a time) |
| `KAKEIZU_TLS_CERT` / `KAKEIZU_TLS_KEY` | none in prod; PEM certificate and key (see below) |

### Login

There is a single `admin` user. Generate the password hash and export it:

```bash
java src/main/java/dev/mauricioandrade/kakeizu/infrastructure/security/PasswordHasher.java
export KAKEIZU_ADMIN_PASSWORD_HASH='pbkdf2-sha256:600000:...'
```

Local overrides go in `application-local.properties` (gitignored).

### Keeping the model warm

Ollama unloads an idle model after 5 minutes, and loading `gemma4:e4b` back takes ~16 s on top of the
reading. Kakeizu asks Ollama to load the model when it starts and whenever the upload page opens, so it is
usually ready by the time the photo is taken. To keep it loaded all day, set this where Ollama runs
(on Windows: *System Properties → Environment Variables*, then restart Ollama):

```
OLLAMA_KEEP_ALIVE=8h
```

### HTTPS (using it from a phone)

Dev mode listens on `localhost` only, over plain HTTP. The packaged app (prod profile) listens on the
network so phones on the shop Wi-Fi can reach it, and then it **only speaks HTTPS** (port 8443): the
password and the session cookie never cross the Wi-Fi in clear text. It refuses to start without a
certificate.

A self-signed certificate is enough for a single shop. Put the machine's LAN IP in it:

```bash
mkdir -p .certs   # gitignored
openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj "/CN=kakeizu.local" \
  -addext "subjectAltName=DNS:localhost,IP:192.168.0.10" \
  -keyout .certs/kakeizu-key.pem -out .certs/kakeizu.pem
export KAKEIZU_TLS_CERT=.certs/kakeizu.pem KAKEIZU_TLS_KEY=.certs/kakeizu-key.pem
```

The phone shows a warning the first time; accept it once. (For no warning at all, use
[mkcert](https://github.com/FiloSottile/mkcert) and install its CA on the phone.)

## Accuracy on real receipts

Measured on 2026-10-13 with `gemma4:e4b` (thinking on, temperature 0), sending exactly the request the
app sends, and running every answer through the app's own validation. 7 receipts × 3 readings each = 21
readings, 126 fields:

| Receipt | Readings ready to confirm as read | What went wrong |
|---|---|---|
| Pix (Inter) | 3 / 3 | — |
| Pix (Nubank, real) | 2 / 3 | once dropped a character from the Pix ID: **caught** (length) |
| Card purchase in a bank app (Nubank, real) | 3 / 3 | — |
| Cielo debit slip | 0 / 3 | CNPJ misread every time: **caught** (check digits). Authorization code transposed (375579 → 375759): not caught |
| Stone slip, small | 1 / 3 | took the StoneID as the authorization code twice: **not caught** |
| Stone slip, demo | 0 / 3 | store name missing twice: **caught**; once the cardholder as the store: **not caught** |
| NFC-e template | 0 / 3 | fields read right, but the template has no CNPJ and a zeroed access key: **blocked, as it should be** |

Per field, out of 126:

| | Fields |
|---|---|
| Right on the first reading | **110** (87%) |
| Wrong, and the domain flags that field | 6 |
| Wrong, in a receipt the domain blocks for another field | 6 |
| Wrong, and nothing flags it | 4 |

Type, amount and date were right in all 21 readings. What slips through are the fields without a check
digit: authorization codes and names. That is why no receipt is ever confirmed automatically: the app
puts the photo next to the form and a person decides.

Things learned along the way:

- **Thinking matters more than speed.** With thinking off, a reading takes ~2 s instead of ~13 s, but the
  model took an item price as the NFC-e total and the card brand as the store: wrong values in a valid
  format, which no check digit catches.
- **Temperature 0 is not deterministic.** The same image gave different answers between runs; in earlier
  runs the NFC-e template total (146,46) came back as 4.46 and 46.46.
- **The model cannot say null.** LangChain4j sends a strings-only JSON schema, and Ollama enforces it as a
  grammar, so the model writes the word `"null"`. The AI adapter turns it into a missing field.
- **Small sample.** 7 receipts, two of them the author's own. Treat these as a sanity check, not a
  benchmark.

Readings take ~13 s warm (median, 9–16 s) on a consumer GPU; the model is loaded in the background as
soon as the upload page opens.
