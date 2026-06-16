# clj-api-aws

A Clojure API template for AWS Lambda, deployed as a Docker container via the [Lambda Web Adapter](https://github.com/awslabs/aws-lambda-web-adapter) pattern. Intended as a starting point for personal/hobby projects.

## What's included

| Concern | Choice |
|---------|--------|
| HTTP routing | [reitit-ring](https://github.com/metosin/reitit) |
| Schema / coercion | [Malli](https://github.com/metosin/malli) (request validation + OpenAPI docs) |
| JSON | [muuntaja](https://github.com/metosin/muuntaja) + [jsonista](https://github.com/metosin/jsonista) |
| Config | [aero](https://github.com/juxt/aero) (profile-based: `dev` / `default`) |
| Logging | [Telemere](https://github.com/taoensso/telemere) — JSON in prod, human-readable in dev |
| Persistence | DynamoDB via [cognitect/aws-api](https://github.com/cognitect-labs/aws-api) |
| Infrastructure | AWS CDK (TypeScript) — Lambda + API Gateway + DynamoDB |
| CI | GitHub Actions |

## Project layout

```
clj-api/          Clojure application
  src/clj_api/
    core.clj      Routes, handlers, server lifecycle
    db.clj        DynamoDB client + attribute converters
    items.clj     Items domain (CRUD operations)
    middleware.clj Request correlation IDs + access logging
    cli.clj       CLI option parsing
  dev/            REPL utilities (not compiled into the app)
  test/           Integration tests (ring.mock.request, no Jetty needed)
  resources/
    config.edn    Aero config (profile-based)
    logback.xml   Logging config

infrastructure/   AWS CDK stack (TypeScript)
  lib/clj-api-stack.ts  Lambda + API Gateway + DynamoDB table

docker-compose.yml  DynamoDB Local for local development
dev_scripts/        One-time environment setup (Babashka, bbin, dev tools)
```

## Local development

### Prerequisites

- Java 21
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Docker (for DynamoDB Local)

### First-time setup

```bash
# Optional: install Babashka-based dev tools (clj-nrepl-eval, etc.)
bash dev_scripts/setup_dev_tools.sh
```

### Start DynamoDB Local

```bash
docker compose up -d
```

### Run the server

```bash
APP_ENV=dev clojure -M:run
```

The server starts on `http://localhost:8080`.

### REPL workflow

Start a headless nREPL server in one terminal:

```bash
APP_ENV=dev clojure -M:nrepl
```

Connect from your editor, or use `clj-nrepl-eval` from another terminal. Once connected, create the local DynamoDB table:

```clojure
;; In the REPL — creates items-dev table in DynamoDB Local
(require '[cognitect.aws.client.api :as aws])
(aws/invoke @clj-api.db/client
  {:op :CreateTable
   :request {:TableName @clj-api.db/table
             :AttributeDefinitions [{:AttributeName "id" :AttributeType "S"}]
             :KeySchema [{:AttributeName "id" :KeyType "HASH"}]
             :BillingMode "PAY_PER_REQUEST"}})
```

Then hit the API:

```bash
curl -s http://localhost:8080/health | jq
curl -s -X POST http://localhost:8080/items \
  -H 'content-type: application/json' \
  -d '{"title": "hello"}' | jq
curl -s http://localhost:8080/items | jq
```

OpenAPI docs are available at `http://localhost:8080/openapi.json`.

### Configuration

`clj-api/resources/config.edn` uses aero profiles selected by the `APP_ENV` environment variable (`dev` or omitted for `default`).

| Key | dev | default (Lambda) |
|-----|-----|-----------------|
| `server.host` | `localhost` | `0.0.0.0` |
| `server.port` | `8080` | `8080` |
| `dynamodb.table-name` | `items-dev` | `$DYNAMODB_TABLE_NAME` (set by CDK) |
| `dynamodb.endpoint` | `http://localhost:8000` | *(AWS default)* |
| `dynamodb.region` | `us-east-1` | *(Lambda runtime env)* |

### Logging

With `APP_ENV=dev`, logs are human-readable. Without it (Lambda / CI), each line is a JSON object:

```json
{"level":"info","ts":"2026-...","ns":"clj-api.middleware","msg":"GET /health 200 3ms","ctx":{"request-id":"abc-123"}}
```

Every request gets a `request-id` (auto-generated UUID or echoed from the `X-Request-ID` header), which appears in the `ctx` field of every log line emitted during that request.

## Running tests

DynamoDB Local must be running:

```bash
docker compose up -d
AWS_ACCESS_KEY_ID=dummy AWS_SECRET_ACCESS_KEY=dummy AWS_DEFAULT_REGION=us-east-1 \
  clojure -X:test
```

Tests use `ring.mock.request` to call the full Ring middleware stack directly (no Jetty process). The test fixture creates a temporary `items-test` table in DynamoDB Local and waits for it to become active before running.

CI (GitHub Actions) runs the same suite with DynamoDB Local as a service container.

## Infrastructure

The CDK stack (`infrastructure/`) provisions:

- **Lambda** — Docker image function, 512 MB, 30s timeout
- **API Gateway** — HTTP API (v2), catch-all proxy to Lambda
- **DynamoDB** — `items` table, on-demand billing (no idle charges)

### Deploy

```bash
cd infrastructure
npm install
npx cdk bootstrap   # once per account/region
npx cdk deploy
```

To allow `cdk destroy` to delete the DynamoDB table (e.g. in a dev/throwaway environment):

```bash
npx cdk deploy -c dev=true
```

Without `-c dev=true` the table uses `RemovalPolicy.RETAIN` — destroying the stack leaves the table intact.

### Costs

With on-demand DynamoDB billing and Lambda's free tier, a lightly-used hobby deployment costs effectively nothing at rest. You pay only for actual requests.

## API routes

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Empty 200 |
| `GET` | `/health` | Server uptime info |
| `GET` | `/echo` | Echoes the request map |
| `GET` | `/openapi.json` | OpenAPI spec |
| `POST` | `/greet` | `{"name": "Alice"}` → `{"greeting": "Hello, Alice!"}` |
| `GET` | `/items` | List all items |
| `POST` | `/items` | Create item (`title` required, `content` optional) |
| `GET` | `/items/:id` | Get item by ID |
| `DELETE` | `/items/:id` | Delete item |

## Extending

**Adding a new entity** (e.g. `notes`):

1. Create `src/clj_api/notes.clj` following the pattern in `items.clj` — use `db/client`, `db/table`, and the `db/->item` / `db/<-item` converters from `db.clj`
2. Add handlers and routes with Malli schemas in `core.clj`
3. Add a new DynamoDB table in the CDK stack and pass its name as an env var
4. Add integration tests

Malli schemas on routes serve double duty: request coercion (returns 400 on type mismatch) and OpenAPI doc generation.
