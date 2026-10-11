# Apache Sourcelume Simple Demo
**Community Over Code Glasgow 2026 Hackathon**

This demo provides a hands-on walkthrough of **Apache Sourcelume Spec** and **Apache Sourcelume Registry** working together with **Apache Atlas** to persist, index, and query dataset metadata and provenance records.

---

## What This Demo Demonstrates

### The Problem
AI models are often trained on large-scale datasets with murky origins, untracked licensing constraints, and missing audit trails. Without standardized provenance, organizations risk intellectual property infringement, non-compliance with open source licenses (e.g. SPDX), and untraceable data contamination.

### The Sourcelume Solution
1. **Spec (`sourcelume-spec`)**: A standardized JSON-LD / SHACL provenance specification defining data structures for dataset identification, custody chains, actor attestations, and SPDX licensing history.
2. **Registry (`sourcelume-registry`)**: A cloud-native metadata registry service that validates dataset records against spec schemas, automatically bootstraps domain type definitions into **Apache Atlas**, and provides REST APIs for dataset registration and query resolution.

### The Story You Present
Within seconds, an operator or CI pipeline can:
1. Verify system readiness and Atlas backend connectivity.
2. Observe zero-configuration bootstrapping of Sourcelume's custom type system (`sourcelume_dataset`) into Apache Atlas.
3. Ingest dataset provenance metadata (curated during the Glasgow Hackathon) with automated validation.
4. Query the dataset by both canonical domain URI (`qualifiedName`) and internal graph GUID.
5. Visually explore the dataset entity, attributes, and audit history in the Apache Atlas Web UI.

---

## Directory Contents

| File | Description |
|---|---|
| `run-demo.sh` | Automated end-to-end demo script with clear narration, formatted console output, and step-by-step verification. |
| `sample-dataset.json` | Sample dataset payload mapped to `SourcelumeDatasetDto` for registry ingestion. |
| `sample-provenance-record.jsonld` | Spec-compliant JSON-LD provenance card showing full metadata, custody chain, and SPDX licensing. |
| `README.md` | This guided walkthrough and reference documentation. |

---

## Prerequisites

Before running the demo, ensure you have:

- **Docker Engine** (≥ 20.10) & **Docker Compose** (≥ 1.28)
- **Java 21** (LTS) & **Maven 3.9+**
- `curl` and `jq` CLI utilities installed

---

## Quickstart (Automated Execution)

### 1. Start the Environment

Make sure Apache Atlas and the Sourcelume Registry runtime are running:

```bash
# Terminal 1: Start Apache Atlas backend (from project root)
cd dev-support
./setup.sh
# Ensure Atlas container is running (see dev-support/README.md for build steps)
docker compose -f docker-compose.atlas.yml up -d

# Terminal 2: Start Quarkus Registry runtime (from project root)
mvn -pl sourcelume-registry-runtime-quarkus quarkus:dev
```

### 2. Run the Demo Script

From the `examples/simple-demo` directory:

```bash
cd examples/simple-demo
./run-demo.sh
```

---

## What `run-demo.sh` Executes (Step-by-Step Breakdown)

The automated script walks through 5 verification stages:

### Step 1: System Readiness & Health Probe (`/q/health/ready`)
- **What it does**: Issues a SmallRye Health check against the Quarkus registry runtime.
- **What you are showing**: Sourcelume actively monitors its connection to the Apache Atlas backend. If Atlas is reachable, the registry reports `UP` and is ready for traffic.

```text
  +----------------------+             +----------------------------------+             +-------------------------+
  |    Client / CI       |             |   Sourcelume Registry (Quarkus)  |             |      Apache Atlas       |
  +----------------------+             +----------------------------------+             +-------------------------+
             |                                          |                                            |
             |  1. GET /q/health/ready                  |                                            |
             | ---------------------------------------> |                                            |
             |                                          |  2. Probe Atlas connectivity / status      |
             |                                          | -----------------------------------------> |
             |                                          |                                            |
             |                                          |  3. Connection OK (HTTP 200 / UP)          |
             |                                          | <----------------------------------------- |
             |  4. Health check JSON (status: UP)       |                                            |
             | <--------------------------------------- |                                            |
             |                                          |                                            |
```

```bash
curl -s http://localhost:8082/q/health/ready | jq .
```

### Step 2: Automatic Atlas Type Bootstrapping (`/api/v1/types/typedefs`)
- **What it does**: Queries the registry to inspect active entity type definitions in Apache Atlas.
- **What you are showing**: Zero manual Atlas configuration required. On startup, Sourcelume automatically registered the `sourcelume_dataset` type (derived from `sourcelume_model.json`), extending Atlas's native `DataSet` type hierarchy with attributes like `sourceUri` and `licenseId`.

```text
  +----------------------+             +----------------------------------+             +-------------------------+
  |    Client / CI       |             |   Sourcelume Registry (Quarkus)  |             |      Apache Atlas       |
  +----------------------+             +----------------------------------+             +-------------------------+
             |                                          |                                            |
             |                                          |  [On Startup: Auto-Bootstrap Typedefs]     |
             |                                          |  1. Read model (sourcelume_model.json)     |
             |                                          |  2. POST /api/atlas/v2/types/typedefs      |
             |                                          | -----------------------------------------> |
             |                                          |     Register sourcelume_dataset type       |
             |                                          |                                            |
             |  3. GET /api/v1/types/typedefs           |                                            |
             | ---------------------------------------> |                                            |
             |                                          |  4. Fetch registered type definitions      |
             |                                          | -----------------------------------------> |
             |                                          |                                            |
             |                                          |  5. Return Atlas entity & struct defs      |
             |                                          | <----------------------------------------- |
             |  6. Return active Typedef JSON           |                                            |
             | <--------------------------------------- |                                            |
             |                                          |                                            |
```

```bash
# Inspect all registered typedefs
curl -s http://localhost:8082/api/v1/types/typedefs | jq .

# Inspect the sourcelume_dataset entity type definition
curl -s http://localhost:8082/api/v1/types/dataset | jq .
```

### Step 3: Dataset Registration & Ingestion (`POST /api/v1/datasets`)
- **What it does**: Ingests `sample-dataset.json` (representing the Glasgow Hackathon dataset) into the registry.
- **What you are showing**:
  - The registry validates input fields against Jakarta Validation rules and spec definitions (`qualifiedName`, `licenseId`, `sourceUri`).
  - It creates the entity inside Apache Atlas and returns a newly minted Atlas internal `guid` alongside its domain URI.

```text
  +----------------------+             +----------------------------------+             +-------------------------+
  |    Client / CI       |             |   Sourcelume Registry (Quarkus)  |             |      Apache Atlas       |
  +----------------------+             +----------------------------------+             +-------------------------+
             |                                          |                                            |
             |  1. POST /api/v1/datasets                |                                            |
             |     (Payload: qualifiedName, etc.)       |                                            |
             | ---------------------------------------> |                                            |
             |                                          |  2. Validate DTO (Jakarta Validation)      |
             |                                          |  3. Map DTO -> sourcelume_dataset Entity   |
             |                                          | -----------------------------------------> |
             |                                          |     Persist entity into Atlas graph        |
             |                                          |                                            |
             |                                          |  4. Assigned Graph GUID & Indexed          |
             |                                          | <----------------------------------------- |
             |  5. Return DTO with assigned GUID        |                                            |
             | <--------------------------------------- |                                            |
             |                                          |                                            |
```

```bash
curl -s -X POST http://localhost:8082/api/v1/datasets \
  -H "Content-Type: application/json" \
  -d @sample-dataset.json | jq .
```

### Step 4: Domain-Key Query (`GET /api/v1/datasets?qualifiedName=...`)
- **What it does**: Queries the dataset using its canonical URI identifier (`sourcelume://datasets/glasgow-hackathon-corpus-v1`).
- **What you are showing**: Upstream ML pipelines, curators, and compliance tools can retrieve dataset metadata using meaningful domain URIs without needing to know internal database identifiers.

```text
  +----------------------+             +----------------------------------+             +-------------------------+
  |  ML Pipeline / Tool  |             |   Sourcelume Registry (Quarkus)  |             |      Apache Atlas       |
  +----------------------+             +----------------------------------+             +-------------------------+
             |                                          |                                            |
             |  1. GET /api/v1/datasets                 |                                            |
             |     ?qualifiedName=sourcelume://...      |                                            |
             | ---------------------------------------> |                                            |
             |                                          |  2. Query Atlas by unique qualifiedName    |
             |                                          | -----------------------------------------> |
             |                                          |                                            |
             |                                          |  3. Return matched Entity data             |
             |                                          | <----------------------------------------- |
             |  4. Return JSON dataset attributes       |                                            |
             | <--------------------------------------- |                                            |
             |                                          |                                            |
```

```bash
curl -s "http://localhost:8082/api/v1/datasets?qualifiedName=sourcelume://datasets/glasgow-hackathon-corpus-v1" | jq .
```

### Step 5: Backend-Key Query (`GET /api/v1/datasets/guid/...`)
- **What it does**: Queries the dataset using its internal Atlas GUID (`ASSIGNED_GUID`).
- **What you are showing**: Downstream governance tools and Atlas-native services have direct access to entities via their persistent graph GUIDs.

```text
  +----------------------+             +----------------------------------+             +-------------------------+
  |  Governance / Audit  |             |   Sourcelume Registry (Quarkus)  |             |      Apache Atlas       |
  +----------------------+             +----------------------------------+             +-------------------------+
             |                                          |                                            |
             |  1. GET /api/v1/datasets/guid/{guid}     |                                            |
             | ---------------------------------------> |                                            |
             |                                          |  2. Direct Graph node lookup by Atlas GUID |
             |                                          | -----------------------------------------> |
             |                                          |                                            |
             |                                          |  3. Return Entity graph node attributes    |
             |                                          | <----------------------------------------- |
             |  4. Return JSON dataset attributes       |                                            |
             | <--------------------------------------- |                                            |
             |                                          |                                            |
```

```bash
curl -s "http://localhost:8082/api/v1/datasets/guid/<ASSIGNED_GUID>" | jq .
```

---

## Exploring in the Apache Atlas Web UI

To conclude the demo with visual verification:

1. Open your browser to [http://localhost:21000](http://localhost:21000).
2. Log in with:
   - **Username**: `admin`
   - **Password**: `atlasR0cks!`
3. In the search box, select **Type**: `sourcelume_dataset`.
4. Click **Search** to view the `Glasgow Hackathon Instruction Corpus v1` entity, its indexed attributes (`sourceUri`, `licenseId`), and audit history.

---

## Companion Artifact: The Spec Provenance Record

The `sample-provenance-record.jsonld` file illustrates the broader **Sourcelume Spec (v0.0.1)** model and linked-data format:

- **Custody Chain (`custodyChain`)**: Tracking who touched the dataset (`agent`), what operation was performed (`action`), and when (`startTime`).
- **License Governance (`licenseHistory`)**: Tracking SPDX license attribution (`Apache-2.0`) and compatibility verification.
- **JSON-LD Interoperability**: Demonstrating compliance with standard W3C linked data formats.

```json
{
  "@context": "https://sourcelume.apache.org/context/0.0.1/sourcelume.jsonld",
  "id": "https://sourcelume.apache.org/records/rec-glasgow-hackathon-001",
  "type": "ProvenanceRecord",
  "identifier": "sourcelume://datasets/glasgow-hackathon-corpus-v1",
  "name": "Glasgow Hackathon Instruction Corpus v1",
  "version": "1.0.0",
  "license": "https://spdx.org/licenses/Apache-2.0.html",
  "licenseScope": "dataset",
  "licenseCategory": "permissive",
  "licenseNote": "SPDX compliant Apache-2.0 dataset metadata verification",
  "licenseHistory": [
    {
      "license": "https://spdx.org/licenses/Apache-2.0.html",
      "event": "initial_curation",
      "date": "2026-10-11T09:00:00Z"
    }
  ],
  "creator": [
    {
      "type": "schema:Organization",
      "name": "Community Over Code Glasgow 2026 Hackathon Team",
      "role": "originator"
    }
  ],
  "created": "2026-10-11T09:00:00Z",
  "added": "2026-10-11T10:00:00Z",
  "contentCreated": "2026-10-11T08:30:00Z",
  "origin": "Curated multi-turn instruction data assembled during Community Over Code Glasgow 2026 Hackathon.",
  "custodyChain": [
    {
      "agent": "https://sourcelume.apache.org/agents/glasgow-curator-01",
      "action": "dataset_ingestion_and_validation",
      "startTime": "2026-10-11T10:00:00Z"
    }
  ]
}
```

---

## Teardown

To shut down local services after completing the demo:

```bash
# Stop Quarkus (Ctrl+C in terminal)

# Stop Atlas backend
cd dev-support
docker compose -f docker-compose.atlas.yml down
```

## License

This project is licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
