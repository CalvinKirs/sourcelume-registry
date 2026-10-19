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
package org.apache.sourcelume.registry.common.dto;

import java.util.Optional;
import org.apache.sourcelume.registry.common.spec.SpecResourceLoader;

/**
 * The record types the registry knows, mirroring the per-type schema table
 * of the Python reference validator (sourcelume-spec
 * {@code tools/validate.py}, its {@code SCHEMA_FILES} map): one JSON Schema
 * per record type, one entity type per record type, and the shared SHACL
 * shapes file targeting the class each type expands to.
 *
 * <p>This is the single place that binds a record's {@code type} term to
 * the resources that judge and store it. When the spec grows a record
 * type, adding a constant here wires schema selection, the SHACL
 * trivial-conformance guard, and the ingest entity mapping.
 */
public enum RecordType {

    /** A dataset producer's provenance record, stored as a {@code sourcelume_dataset} entity. */
    PROVENANCE_RECORD(
            "ProvenanceRecord",
            SourcelumeDatasetDto.TYPE_NAME,
            SpecResourceLoader.DEFAULT_SCHEMA_RESOURCE,
            "ProvenanceRecord"),

    /** A model producer's training-provenance claim, stored as a {@code sourcelume_model} entity. */
    TRAINING_PROVENANCE_RECORD(
            "TrainingProvenanceRecord",
            SourcelumeModelDto.TYPE_NAME,
            SpecResourceLoader.TRAINING_SCHEMA_RESOURCE,
            "TrainingProvenanceRecord");

    private final String typeName;
    private final String entityTypeName;
    private final String schemaResource;
    private final String shaclClassName;

    RecordType(String typeName, String entityTypeName, String schemaResource, String shaclClassName) {
        this.typeName = typeName;
        this.entityTypeName = entityTypeName;
        this.schemaResource = schemaResource;
        this.shaclClassName = shaclClassName;
    }

    /** The record's {@code type} term value, e.g. {@code "TrainingProvenanceRecord"}. */
    public String typeName() {
        return typeName;
    }

    /** The backend entity type the record is stored under. */
    public String entityTypeName() {
        return entityTypeName;
    }

    /** Classpath resource of this type's JSON Schema inside the spec jar. */
    public String schemaResource() {
        return schemaResource;
    }

    /**
     * The fully-expanded class IRI the SHACL shapes target for this type
     * (the term's expansion under the stable {@code sl:} namespace).
     */
    public String shaclClassIri() {
        return SpecResourceLoader.SOURCELUME_NAMESPACE + shaclClassName;
    }

    /** A fresh entity DTO of this type's entity class. */
    public RecordEntityDto newEntity() {
        return switch (this) {
            case PROVENANCE_RECORD -> new SourcelumeDatasetDto();
            case TRAINING_PROVENANCE_RECORD -> new SourcelumeModelDto();
        };
    }

    /** Resolves a record's {@code type} term to its record type. */
    public static Optional<RecordType> fromTypeName(String typeName) {
        for (RecordType type : values()) {
            if (type.typeName.equals(typeName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Resolves a backend entity type name back to its record type. */
    public static Optional<RecordType> fromEntityTypeName(String entityTypeName) {
        for (RecordType type : values()) {
            if (type.entityTypeName.equals(entityTypeName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * The entity type a record is stored under. Documents carrying no
     * known {@code type} still land somewhere for their verdict — the
     * dataset entity is the fallback so the failure stays curatable,
     * exactly as it did before record types existed.
     */
    public static String entityTypeFor(String typeName) {
        return fromTypeName(typeName).map(RecordType::entityTypeName).orElse(SourcelumeDatasetDto.TYPE_NAME);
    }
}
