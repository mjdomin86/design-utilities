# Design Backup Service

Microservice to back up and search [Flowable Design](https://www.flowable.com/) apps via the
Design REST API (Java 21, Spring Boot 4.0.6).

## Features

1. **Backup** — downloads every app of a workspace as a zip into
   `{targetDir}/{workspaceKey}/{timestamp}/` plus a `manifest.json` describing the run.
   Trigger manually (`POST /api/backups`) or on a cron schedule.
2. **Model search UI** — web UI at `http://localhost:8090/` to search a term across model
   types (BPMN, DMN, Form, …) with conditions *contains / not contains / equals*.
   BPMN and CMMN models are evaluated **element by element**, so "service registry tasks
   that do NOT contain GEN-902" returns each individual task missing that reference.
3. **Service operation lookup** — within one app, find the service model operation bound
   to an HTTP method + path; returns the service model key, operation key and all
   input/output parameters.

## Configuration (`application.properties`)

```properties
flowable.design.base-url=http://localhost:8081
flowable.design.api-prefix=/design-api

# basic | oauth2 | custom-header
flowable.design.auth.mode=basic
flowable.design.auth.basic.username=admin
flowable.design.auth.basic.password=test
#flowable.design.auth.oauth2.token=<bearer token>
#flowable.design.auth.custom-header.name=X-Api-Key
#flowable.design.auth.custom-header.value=<value>

# Backups may only be written below this directory (path-traversal guard).
flowable.design.backup.allowed-base-dir=${user.home}/flowable-design-backups

# Scheduled backups
flowable.design.backup.scheduled.enabled=false
flowable.design.backup.scheduled.cron=0 0 2 * * *
flowable.design.backup.scheduled.workspace-key=default
flowable.design.backup.scheduled.target-directory=${user.home}/flowable-design-backups
```

The service fails fast at startup when the selected auth mode is missing its credentials,
or when scheduled backups are enabled without workspace/target directory.

## REST API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/backups` | Run a backup. Body: `{"workspaceKey":"...","targetDirectory":"..."}` |
| `POST` | `/api/search` | Search models. Body: `{"workspaceKey":"...","modelTypes":["bpmn"],"condition":"NOT_CONTAINS","term":"GEN-902","elementType":"serviceTask"}` |
| `GET` | `/api/workspaces/{ws}/apps/{app}/service-operations?method=POST&path=/api/customers` | Find service operation by HTTP method + path |
| `GET` | `/api/workspaces/{ws}/unused-models?modelTypes=bpmn&modelTypes=form` | List models referenced by no app and no other model (deletion candidates). `modelTypes` optional — omit to scan all types |
| `GET` | `/api/workspaces` | List workspaces |
| `GET` | `/api/workspaces/{ws}/apps` | List apps of a workspace |

Search semantics:
- `CONTAINS` / `NOT_CONTAINS` — substring match.
- `EQUALS` — the term appears as an exact token (attribute value, id, word), e.g.
  `GEN-902` matches `key="GEN-902"` but not `GEN-9021`.
- For BPMN and CMMN, every element carrying an `id` is evaluated individually; the optional
  `elementType` narrows by tag name (`serviceTask`), `flowable:type` (`service-registry`)
  or `flowable:extensionId`.
- Each matched model also reports the **app(s)** it belongs to, derived from the model's
  parent relations.

Unused-model detection (for cleanup): a model is reported as unused when its
`parent-relations-summary` shows zero referencing packages **and** zero referencing
models. Package types (`app`, `plugin`) are roots and are excluded from the scan.

## Build & run

```bash
mvn test               # unit tests + JaCoCo report (target/site/jacoco)
mvn spring-boot:run    # starts on port 8090
```
