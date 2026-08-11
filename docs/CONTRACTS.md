# Telemetry Event Contracts

Contracts for FabOps telemetry ingestion and stream processing. Schemas live in [`schemas/`](../schemas/) and are published from this registry at tag **v1.0**.

## Shared metadata

Every event type includes these required fields:

| Field | Type | Description |
|-------|------|-------------|
| `equipmentId` | string | Producing or related equipment id (`SYSTEM` / `UNKNOWN` when not applicable) |
| `timestamp` | long (timestamp-millis) | UTC event time |
| `payload` | type-specific | Business content of the event |
| `correlationId` | string | End-to-end trace / replay key |
| `schemaVersion` | string | Contract version, e.g. `1.0.0` |

---

## 1. Raw telemetry (`RawTelemetryEvent`)

**File:** `schemas/raw-telemetry.avsc`  
**Topic (logical):** `telemetry.raw`  
**Producers:** Equipment adapters / edge collectors  
**Consumers:** Validation service, status derivation, alerting

### Required / notable fields

- Shared metadata above
- `payload`: map of string→string measurements
- `sourceSystem` (default `unknown`)
- Optional `metricName`, `unit`

### Valid payload example

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600000,
  "payload": {
    "chamber_pressure_torr": "2.45",
    "rf_power_w": "850",
    "wafer_temp_c": "385.2"
  },
  "correlationId": "corr-7f3a9c2e-1b44-4d01-9a10-88e2c1d0ab12",
  "schemaVersion": "1.0.0",
  "sourceSystem": "edge-adapter-cvd",
  "metricName": "chamber_pressure_torr",
  "unit": "torr"
}
```

### Invalid payload examples

Missing required `correlationId`:

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600000,
  "payload": { "rf_power_w": "850" },
  "schemaVersion": "1.0.0"
}
```

Wrong type for `timestamp` (must be epoch millis long, not ISO string):

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": "2026-08-11T08:40:00Z",
  "payload": { "rf_power_w": "850" },
  "correlationId": "corr-bad-ts",
  "schemaVersion": "1.0.0"
}
```

---

## 2. Equipment status (`EquipmentStatusEvent`)

**File:** `schemas/equipment-status.avsc`  
**Topic (logical):** `telemetry.equipment-status`  
**Producers:** Status stream processor  
**Consumers:** Ops portal, alerting, MES integrations

### Payload contract

| Field | Values / notes |
|-------|----------------|
| `status` | `IDLE`, `RUNNING`, `SETUP`, `MAINTENANCE`, `DOWN`, `UNKNOWN` |
| `previousStatus` | same enum or null |
| `reasonCode` | optional string |
| `details` | string map |

### Valid payload example

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600500,
  "payload": {
    "status": "RUNNING",
    "previousStatus": "IDLE",
    "reasonCode": "LOT_START",
    "details": { "lotId": "LOT-77821", "recipe": "CVD-STD-A" }
  },
  "correlationId": "corr-7f3a9c2e-1b44-4d01-9a10-88e2c1d0ab12",
  "schemaVersion": "1.0.0",
  "sourceEventId": "corr-7f3a9c2e-1b44-4d01-9a10-88e2c1d0ab12"
}
```

### Invalid payload examples

Unknown enum symbol:

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600500,
  "payload": {
    "status": "ONLINE",
    "previousStatus": null,
    "reasonCode": null,
    "details": {}
  },
  "correlationId": "corr-bad-status",
  "schemaVersion": "1.0.0"
}
```

Missing `equipmentId`:

```json
{
  "timestamp": 1786437600500,
  "payload": { "status": "RUNNING", "details": {} },
  "correlationId": "corr-missing-eq",
  "schemaVersion": "1.0.0"
}
```

---

## 3. Critical alert (`CriticalAlertEvent`)

**File:** `schemas/critical-alert.avsc`  
**Topic (logical):** `telemetry.critical-alert`  
**Producers:** Alerting stream processor  
**Consumers:** Notification services, on-call routing, ops UI  
**Latency target:** sub-second from triggering telemetry where platform capacity allows

### Payload contract

| Field | Notes |
|-------|-------|
| `alertId` | Stable id across retries |
| `severity` | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` |
| `title`, `description` | Operator-facing text |
| `threshold`, `observedValue` | optional |
| `attributes` | string map |
| top-level `acknowledged` | boolean, default false |

### Valid payload example

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600800,
  "payload": {
    "alertId": "ALT-CVD-0142-PRESSURE-001",
    "severity": "CRITICAL",
    "title": "Chamber pressure exceeded limit",
    "description": "chamber_pressure_torr=4.90 exceeded max=3.00",
    "threshold": "3.00",
    "observedValue": "4.90",
    "attributes": { "metric": "chamber_pressure_torr" }
  },
  "correlationId": "corr-7f3a9c2e-1b44-4d01-9a10-88e2c1d0ab12",
  "schemaVersion": "1.0.0",
  "acknowledged": false
}
```

### Invalid payload examples

Missing `alertId` inside payload:

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600800,
  "payload": {
    "severity": "CRITICAL",
    "title": "Chamber pressure exceeded limit",
    "description": "breach detected"
  },
  "correlationId": "corr-bad-alert",
  "schemaVersion": "1.0.0"
}
```

Invalid severity:

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437600800,
  "payload": {
    "alertId": "ALT-1",
    "severity": "URGENT",
    "title": "x",
    "description": "y"
  },
  "correlationId": "corr-bad-sev",
  "schemaVersion": "1.0.0"
}
```

---

## 4. Dead letter (`DeadLetterEvent`)

**File:** `schemas/dead-letter.avsc`  
**Topic (logical):** `telemetry.dead-letter`  
**Producers:** Ingestion validators and stream processors after exhausted retries  
**Consumers:** Ops remediation tools, replay jobs, audit

### Payload contract

| Field | Notes |
|-------|-------|
| `originalTopic` | Source topic |
| `originalSchema` / `originalSchemaVersion` | optional |
| `failureReason` | human-readable |
| `failureCode` | `SCHEMA_VALIDATION`, `DESERIALIZATION`, `PROCESSING_ERROR`, `TIMEOUT`, `UNKNOWN` |
| `originalPayload` | UTF-8 JSON or Base64 of original bytes |
| `attemptCount` | int, default 1 |

### Dead-letter handling procedure

1. **Capture:** On non-retryable failure or after max attempts, emit a `DeadLetterEvent` preserving `correlationId` when present.
2. **Quarantine:** Do not block the main topic; continue processing subsequent messages.
3. **Triage:** Classify by `failureCode`. Schema failures require producer or registry fixes; processing errors may need code hotfix.
4. **Remediate:** Fix schema/data/processor, then replay (see below). Do not silently drop dead-letter records.
5. **Audit:** Emit an `AuditEvent` with action `DEAD_LETTER` on write and `REPLAY` on successful reprocessing.

### Valid payload example

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437601200,
  "payload": {
    "originalTopic": "telemetry.raw",
    "originalSchema": "RawTelemetryEvent",
    "originalSchemaVersion": "1.0.0",
    "failureReason": "Missing required field correlationId",
    "failureCode": "SCHEMA_VALIDATION",
    "originalPayload": "{\"equipmentId\":\"EQ-CVD-0142\",\"timestamp\":1786437600000,\"payload\":{\"rf_power_w\":\"850\"},\"schemaVersion\":\"1.0.0\"}",
    "attemptCount": 3
  },
  "correlationId": "corr-dlq-generated-9c1e",
  "schemaVersion": "1.0.0"
}
```

### Invalid payload examples

Missing `failureCode`:

```json
{
  "equipmentId": "UNKNOWN",
  "timestamp": 1786437601200,
  "payload": {
    "originalTopic": "telemetry.raw",
    "failureReason": "boom",
    "originalPayload": "{}"
  },
  "correlationId": "corr-dlq-bad",
  "schemaVersion": "1.0.0"
}
```

Empty `originalPayload` is allowed as a string but empty `originalTopic` violates operational contract (must identify source):

```json
{
  "equipmentId": "UNKNOWN",
  "timestamp": 1786437601200,
  "payload": {
    "originalTopic": "",
    "failureReason": "unknown",
    "failureCode": "UNKNOWN",
    "originalPayload": ""
  },
  "correlationId": "corr-dlq-empty-topic",
  "schemaVersion": "1.0.0"
}
```

---

## 5. Audit (`AuditEvent`)

**File:** `schemas/audit.avsc`  
**Topic (logical):** `telemetry.audit`  
**Producers:** All pipeline components  
**Consumers:** Compliance, security, ops forensics

### Payload contract

| Field | Values |
|-------|--------|
| `action` | `INGEST`, `VALIDATE`, `TRANSFORM`, `EMIT_STATUS`, `EMIT_ALERT`, `DEAD_LETTER`, `REPLAY`, `SCHEMA_REGISTER`, `ACKNOWLEDGE` |
| `actor` | service or operator principal (no customer PII) |
| `outcome` | `SUCCESS`, `FAILURE`, `SKIPPED` |
| `resource` | topic, schema subject, alert id, etc. |
| `details` | non-PII string map |

### Valid payload example

```json
{
  "equipmentId": "EQ-CVD-0142",
  "timestamp": 1786437601300,
  "payload": {
    "action": "REPLAY",
    "actor": "svc-telemetry-replay",
    "outcome": "SUCCESS",
    "resource": "telemetry.dead-letter",
    "details": {
      "correlationId": "corr-dlq-generated-9c1e",
      "targetTopic": "telemetry.raw"
    }
  },
  "correlationId": "corr-dlq-generated-9c1e",
  "schemaVersion": "1.0.0"
}
```

### Invalid payload examples

Unknown action:

```json
{
  "equipmentId": "SYSTEM",
  "timestamp": 1786437601300,
  "payload": {
    "action": "DELETE_ALL",
    "actor": "admin",
    "outcome": "SUCCESS",
    "resource": "telemetry.raw"
  },
  "correlationId": "corr-bad-audit",
  "schemaVersion": "1.0.0"
}
```

Missing `actor`:

```json
{
  "equipmentId": "SYSTEM",
  "timestamp": 1786437601300,
  "payload": {
    "action": "INGEST",
    "outcome": "SUCCESS",
    "resource": "telemetry.raw"
  },
  "correlationId": "corr-missing-actor",
  "schemaVersion": "1.0.0"
}
```

---

## Versioning rules

1. Git tags mark registry releases (`v1.0` is the initial publish).
2. The `schemaVersion` field on each event is a semantic version string (`MAJOR.MINOR.PATCH`).
3. **Compatible changes** (add optional fields with defaults): bump MINOR or PATCH; consumers must ignore unknown fields.
4. **Breaking changes** (rename/remove fields, change types, remove enum symbols): bump MAJOR; register a new subject or version; dual-publish until consumers catch up.
5. CI (`avro-tools` lint via `.gitlab-ci.yml`) must pass before merge to `main`.

## Replay semantics

1. Replay is keyed by `correlationId` (and optionally time window / topic offsets).
2. Replayed events keep the original `correlationId` and original business `timestamp` when reconstructing from `DeadLetterPayload.originalPayload`.
3. Replay processors set a distinct processing metadata path (audit `action=REPLAY`) so operators can distinguish first-pass vs replay traffic.
4. Idempotent consumers must treat `(correlationId, schema subject, alertId/status key)` as a natural key to avoid duplicate side effects.
5. Successful replay removes or marks the dead-letter record as remediated; failures remain in DLQ with incremented `attemptCount`.

## Compatibility and consumer guidance

- Prefer Avro binary encoding on the wire with Confluent/Apicurio-compatible subject naming: `com.fabops.telemetry.events.<EventName>-value`.
- Validate on ingest; reject or dead-letter before durable store write.
- Do not put secrets, tokens, or personal data in `payload` or `details`.

## Registry operations checklist

- [ ] Schemas under `schemas/` pass `./scripts/avro-lint.sh`
- [ ] Contracts documented in this file
- [ ] Git tag `v1.0` published after initial commit
- [ ] GitLab project `telemetry-schema-registry` hosts this content
- [ ] Branch protection enabled on `main` (MR required, pipeline must succeed)
