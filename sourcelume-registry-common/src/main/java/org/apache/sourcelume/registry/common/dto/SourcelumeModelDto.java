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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Entity DTO for a {@code sourcelume_model} entity in Apache Atlas: the
 * stored form of a {@code TrainingProvenanceRecord} — the model producer's
 * claim of which datasets a model was trained on, submitted through
 * {@code POST /records}.
 *
 * <p>The ingest lifecycle fields live on {@link RecordEntityDto}; this
 * subtype adds the provenance attributes the ingest pipeline maps from a
 * conforming record. The referenced datasets themselves are not mapped
 * onto attributes: their provenance state is looked up at read time, per
 * the spec design — the registry never stores an "approved" flag on a
 * model record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourcelumeModelDto extends RecordEntityDto {

    public static final String TYPE_NAME = "sourcelume_model";

    /** The model's own identifier (typically a persistent IRI), not this record's id. */
    private String modelUri;
    /** How much of the training data the producer discloses: complete, partial, or undisclosed. */
    private String disclosure;
    /** Model version or checkpoint, if versioned. */
    private String modelVersion;

    public SourcelumeModelDto() {}

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    public String getModelUri() {
        return modelUri;
    }

    public void setModelUri(String modelUri) {
        this.modelUri = modelUri;
    }

    public String getDisclosure() {
        return disclosure;
    }

    public void setDisclosure(String disclosure) {
        this.disclosure = disclosure;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
