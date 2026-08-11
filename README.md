# Telemetry Schema Registry

Version-controlled Avro event contracts for the FabOps Event Streaming and Telemetry Ingestion platform.

This repository (`telemetry-schema-registry`) is the source of truth for telemetry event schemas used by validated ingestion adapters and stream processors.

## Contents

| Path | Purpose |
|------|---------|
| `schemas/` | Versioned Avro (`.avsc`) definitions |
| `docs/CONTRACTS.md` | Event contracts, versioning, replay, and dead-letter handling |
| `scripts/avro-lint.sh` | Local/CI Avro schema lint via Apache Avro Tools |
| `.gitlab-ci.yml` | CI pipeline that runs `avro-tools` lint on push |

## Event types (v1.0)

| Schema file | Event |
|-------------|-------|
| `schemas/raw-telemetry.avsc` | Raw telemetry from equipment |
| `schemas/equipment-status.avsc` | Derived equipment status |
| `schemas/critical-alert.avsc` | Critical / high-priority alerts |
| `schemas/dead-letter.avsc` | Failed / poison-pill events |
| `schemas/audit.avsc` | Pipeline audit trail |

Common required metadata on every event: `equipmentId`, `timestamp`, `payload`, `correlationId`, `schemaVersion`.

## Local validation

```bash
./scripts/avro-lint.sh
```

Requires Java 11+ and network access on first run (downloads `avro-tools`).

## Versioning

- Initial published release: **v1.0** (git tag `v1.0`)
- Event `schemaVersion` field uses semantic versions (e.g. `1.0.0`)
- Backward-compatible field additions preferred; breaking changes require a new major schema version and coordinated consumer rollout

## Branch protection (GitLab)

Protect `main` so only merge requests with a green `avro-tools-lint` pipeline can land. See `docs/CONTRACTS.md` for operational notes.
