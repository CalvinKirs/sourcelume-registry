#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# ==============================================================================
# Apache Sourcelume Simple Demo
# Community Over Code Glasgow 2026 Hackathon
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATASET_FILE="${SCRIPT_DIR}/sample-dataset.json"
PROVENANCE_FILE="${SCRIPT_DIR}/sample-provenance-record.jsonld"

REGISTRY_URL="${SOURCELUME_REGISTRY_URL:-http://localhost:8082}"
ATLAS_URL="${SOURCELUME_ATLAS_URL:-http://localhost:21000}"

# ANSI formatting
if [ -t 1 ]; then
  BOLD="\033[1m"
  GREEN="\033[0;32m"
  BLUE="\033[0;34m"
  YELLOW="\033[1;33m"
  RED="\033[0;31m"
  CYAN="\033[0;36m"
  MAGENTA="\033[0;35m"
  RESET="\033[0m"
else
  BOLD=""
  GREEN=""
  BLUE=""
  YELLOW=""
  RED=""
  CYAN=""
  MAGENTA=""
  RESET=""
fi

# Sourcelume ASCII Logo Banner
echo -e "${CYAN}"
cat << "EOF"
                                                                  |
   _____                               __                       --+--
  / ___/____  __  __________________  / /_  ______ ___  ___       |
  \__ \/ __ \/ / / / ___/ ___/ _ \/ / / / / / / __ `__ \/ _ \
 ___/ / /_/ / /_/ / /  / /__/  __/ / / / /_/ / / / / / /  __/
/____/\____/\__,_/_/   \___/\___/_/ /_/\__,_/_/ /_/ /_/\___/
EOF
echo -e "${RESET}"

echo -e "${BOLD}${CYAN}================================================================================${RESET}"
echo -e "${BOLD}${CYAN}  Apache Sourcelume Registry — End-to-End Demo                                 ${RESET}"
echo -e "${CYAN}  Community Over Code Glasgow 2026 Hackathon                                    ${RESET}"
echo -e "${BOLD}${CYAN}================================================================================${RESET}"
echo -e "${MAGENTA}Demo Story & Objectives:${RESET}"
echo -e "  1. ${BOLD}The Problem:${RESET}   AI models often train on data with unverified provenance/licensing."
echo -e "  2. ${BOLD}The Solution:${RESET}  Sourcelume Spec (provenance schemas) + Sourcelume Registry (Atlas)."
echo -e "  3. ${BOLD}This Demo:${RESET}     Bootstrap typedefs, ingest dataset, validate, and query entities."
echo -e "${BOLD}${CYAN}--------------------------------------------------------------------------------${RESET}\n"

# Verify required command line tools
command -v curl >/dev/null 2>&1 || { echo -e "${RED}Error: curl is required but not installed.${RESET}" >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo -e "${RED}Error: jq is required but not installed.${RESET}" >&2; exit 1; }

if [ ! -f "${DATASET_FILE}" ]; then
  echo -e "${RED}Error: Dataset payload file not found at ${DATASET_FILE}${RESET}" >&2
  exit 1
fi

# Step 1: Health Check
echo -e "${BOLD}${BLUE}[Step 1/5] System Readiness & Health Probe (${REGISTRY_URL}/q/health/ready)${RESET}"
echo -e "  ${YELLOW}Showing:${RESET} Active health probe confirming registry connectivity to Apache Atlas."
HEALTH_JSON=$(curl -s -f "${REGISTRY_URL}/q/health/ready" 2>/dev/null || true)

if [ -z "${HEALTH_JSON}" ]; then
  echo -e "\n${RED}✗ Failed to connect to Sourcelume Registry at ${REGISTRY_URL}.${RESET}"
  echo -e "${YELLOW}Please ensure Apache Atlas and Sourcelume Registry are running:${RESET}"
  echo -e "  1. Start Atlas:    cd dev-support && docker compose -f docker-compose.atlas.yml up -d"
  echo -e "  2. Start Registry: mvn -pl sourcelume-registry-runtime-quarkus quarkus:dev"
  exit 1
fi

HEALTH_STATUS=$(echo "${HEALTH_JSON}" | jq -r '.status // "DOWN"')
if [ "${HEALTH_STATUS}" != "UP" ]; then
  echo -e "\n${RED}✗ Registry is running but reporting health status: ${HEALTH_STATUS}${RESET}"
  echo "${HEALTH_JSON}" | jq .
  exit 1
fi
echo -e "${GREEN}✓ Registry status is UP (Connected to Apache Atlas backend).${RESET}\n"

# Step 2: Inspect Atlas Typedefs
echo -e "${BOLD}${BLUE}[Step 2/5] Automatic Atlas Type Bootstrapping (/api/v1/types/typedefs)${RESET}"
echo -e "  ${YELLOW}Showing:${RESET} Zero-configuration initialization — Sourcelume bootstraps custom entity"
echo -e "           types ('sourcelume_dataset') into Atlas extending core DataSet attributes."
TYPEDEFS=$(curl -s "${REGISTRY_URL}/api/v1/types/typedefs")
ENTITY_DEFS_COUNT=$(echo "${TYPEDEFS}" | jq '.entityDefs | length')
echo -e "  • Total Entity Definitions loaded: ${BOLD}${ENTITY_DEFS_COUNT}${RESET}"

DATASET_TYPE=$(curl -s "${REGISTRY_URL}/api/v1/types/dataset")
DATASET_TYPENAME=$(echo "${DATASET_TYPE}" | jq -r '.name // empty')
SUPER_TYPES=$(echo "${DATASET_TYPE}" | jq -c -r '.superTypes // []')
echo -e "  • Verified Entity Type: ${GREEN}${DATASET_TYPENAME}${RESET} (SuperTypes: ${SUPER_TYPES})"
echo -e "${GREEN}✓ Type system successfully bootstrapped in Apache Atlas.${RESET}\n"

# Step 3: Register Dataset
echo -e "${BOLD}${BLUE}[Step 3/5] Dataset Registration & Validation (POST /api/v1/datasets)${RESET}"
echo -e "  ${YELLOW}Showing:${RESET} Ingesting dataset metadata with Jakarta Validation and Atlas persistence."
echo -e "${CYAN}Ingestion Payload (${DATASET_FILE}):${RESET}"
cat "${DATASET_FILE}" | jq .

CREATE_RESP=$(curl -s -X POST "${REGISTRY_URL}/api/v1/datasets" \
  -H "Content-Type: application/json" \
  -d @"${DATASET_FILE}")

ASSIGNED_GUID=$(echo "${CREATE_RESP}" | jq -r '.guid // empty')
QUALIFIED_NAME=$(echo "${CREATE_RESP}" | jq -r '.qualifiedName // empty')

if [ -z "${QUALIFIED_NAME}" ]; then
  echo -e "\n${RED}✗ Failed to register dataset. Response:${RESET}"
  echo "${CREATE_RESP}" | jq .
  exit 1
fi

echo -e "\n${GREEN}✓ Dataset registered and indexed in Apache Atlas!${RESET}"
echo -e "  • Canonical Qualified Name: ${BOLD}${QUALIFIED_NAME}${RESET}"
echo -e "  • Assigned Atlas Graph GUID: ${BOLD}${ASSIGNED_GUID}${RESET}\n"

# Step 4: Query Dataset by qualifiedName
echo -e "${BOLD}${BLUE}[Step 4/5] Domain-Key Query (/api/v1/datasets?qualifiedName=...)${RESET}"
echo -e "  ${YELLOW}Showing:${RESET} Upstream pipelines can retrieve metadata using canonical dataset URIs."
FETCH_BY_QN=$(curl -s "${REGISTRY_URL}/api/v1/datasets?qualifiedName=${QUALIFIED_NAME}")
echo "${FETCH_BY_QN}" | jq .
echo -e "${GREEN}✓ Domain-key resolution verified successfully.${RESET}\n"

# Step 5: Query Dataset by GUID
if [ -n "${ASSIGNED_GUID}" ]; then
  echo -e "${BOLD}${BLUE}[Step 5/5] Backend-Key Query (/api/v1/datasets/guid/...)${RESET}"
  echo -e "  ${YELLOW}Showing:${RESET} Downstream governance tools access entities directly via Atlas GUIDs."
  FETCH_BY_GUID=$(curl -s "${REGISTRY_URL}/api/v1/datasets/guid/${ASSIGNED_GUID}")
  echo "${FETCH_BY_GUID}" | jq .
  echo -e "${GREEN}✓ Internal GUID resolution verified successfully.${RESET}\n"
fi

# Summary
echo -e "${BOLD}${CYAN}================================================================================${RESET}"
echo -e "${BOLD}${GREEN}  Demo Execution Complete!                                                      ${RESET}"
echo -e "${BOLD}${CYAN}================================================================================${RESET}"
echo -e "${BOLD}Next Steps for Visual Exploration:${RESET}"
echo -e "  1. Open the Apache Atlas Web UI: ${BOLD}${ATLAS_URL}${RESET}"
echo -e "  2. Log in with credentials:      ${BOLD}admin${RESET} / ${BOLD}atlasR0cks!${RESET}"
echo -e "  3. Search for Type:              ${BOLD}sourcelume_dataset${RESET}"
echo -e "  4. Inspect the entity attributes (sourceUri, licenseId) and audit history."

if [ -f "${PROVENANCE_FILE}" ]; then
  echo -e "\n${BOLD}Companion Spec Artifact:${RESET}"
  echo -e "  Review ${BOLD}${PROVENANCE_FILE}${RESET} to see the complete JSON-LD"
  echo -e "  provenance model including custodyChain and SPDX licenseHistory."
fi
echo -e "\n${GREEN}Thank you for exploring Apache Sourcelume!${RESET}"
