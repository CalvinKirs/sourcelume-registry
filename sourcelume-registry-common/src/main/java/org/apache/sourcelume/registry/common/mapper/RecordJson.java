/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sourcelume.registry.common.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * Type-agnostic access to a raw JSON-LD record document: the fields the
 * registry needs before it knows which record type it is looking at —
 * the record IRI ({@code id}) that keys the store, and the {@code type}
 * term that selects schema, shapes, and entity type. Validators decide
 * whether the record conforms; this helper only reads.
 */
public final class RecordJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RecordJson() {}

    /**
     * Extracts the record IRI ({@code id}) from a raw document without
     * requiring any other field to be present. The ingest API uses this to
     * key the submission before validation runs — a document without an
     * {@code id} cannot be stored and is rejected up front.
     *
     * @param rawJsonLd the raw JSON-LD document
     * @return the record IRI, never null or blank
     * @throws IllegalArgumentException if the document is not parseable JSON or carries no usable {@code id}
     */
    public static String extractRecordId(String rawJsonLd) {
        JsonNode id = root(rawJsonLd).path("id");
        if (!id.isTextual() || id.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Record carries no usable 'id' — the record IRI is required to store a record");
        }
        return id.asText();
    }

    /**
     * Extracts the record {@code type} term if the document carries a
     * usable one. An absent or non-textual {@code type} is no error here —
     * the validator chain reports it; the caller uses the type only to
     * route the record to its entity type.
     *
     * @param rawJsonLd the raw JSON-LD document
     * @return the record type term, or empty if absent/blank
     * @throws IllegalArgumentException if the document is not parseable JSON
     */
    public static Optional<String> extractRecordType(String rawJsonLd) {
        JsonNode type = root(rawJsonLd).path("type");
        if (!type.isTextual() || type.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(type.asText());
    }

    private static JsonNode root(String rawJsonLd) {
        requireText(rawJsonLd, "rawJsonLd");
        try {
            return OBJECT_MAPPER.readTree(rawJsonLd);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Record is not parseable JSON: " + e.getOriginalMessage());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}
