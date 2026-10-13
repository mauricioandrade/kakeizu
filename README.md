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
