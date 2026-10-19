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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sourcelume.registry.common.dto.SourcelumeModelDto;
import org.apache.sourcelume.registry.common.dto.TrainingProvenanceRecordDto;
import org.apache.sourcelume.registry.common.exception.SourcelumeValidationException;

/**
 * Deterministic mapping between a raw JSON-LD training provenance record
 * and the registry's DTOs: {@link #map(String)} binds the document to
 * {@link TrainingProvenanceRecordDto}, {@link #toModelEntity(TrainingProvenanceRecordDto)}
 * projects a validated record onto the {@code sourcelume_model} entity.
 *
 * <p>Same mapping discipline as {@link ProvenanceRecordMapper}: plain
 * Jackson binding, validation before mapping, record IRI becomes the
 * qualifiedName. The record's {@code trainingData} list is not projected
 * onto entity attributes — per the spec design, the state of the
 * referenced dataset records is looked up when the model record is read;
 * the raw document stays byte-identical on the entity as rawJsonLd.
 */
public final class TrainingProvenanceRecordMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TrainingProvenanceRecordMapper() {}

    /**
     * Binds a raw JSON-LD training provenance record to {@link TrainingProvenanceRecordDto}.
     *
     * @param rawJsonLd the raw JSON-LD document
     * @return the bound record, never null
     * @throws SourcelumeValidationException if the document is not parseable JSON
     */
    public static TrainingProvenanceRecordDto map(String rawJsonLd) {
        requireText(rawJsonLd, "rawJsonLd");
        try {
            return OBJECT_MAPPER.readValue(rawJsonLd, TrainingProvenanceRecordDto.class);
        } catch (JsonProcessingException e) {
            throw new SourcelumeValidationException("Record is not parseable JSON: " + e.getOriginalMessage());
        }
    }

    /**
     * Projects a validated record onto the {@code sourcelume_model}
     * entity: the record IRI becomes the qualifiedName, the model
     * identifier becomes the modelUri, and the model's name, version and
     * disclosure follow. The referenced datasets are deliberately not
     * mapped — the registry resolves them at read time and stores no
     * "approved" flag on the model record.
     *
     * @param record the validated record, never null
     * @return the entity projection, without status and guid (set by the pipeline)
     */
    public static SourcelumeModelDto toModelEntity(TrainingProvenanceRecordDto record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        SourcelumeModelDto entity = new SourcelumeModelDto();
        entity.setQualifiedName(record.getId());
        entity.setName(record.getName());
        entity.setModelUri(record.getIdentifier());
        entity.setModelVersion(record.getVersion());
        entity.setDisclosure(record.getDisclosure());
        entity.setDescription(record.getNote());
        return entity;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}
