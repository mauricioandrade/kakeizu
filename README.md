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

Local overrides go in `application-local.properties` (gitignored).
