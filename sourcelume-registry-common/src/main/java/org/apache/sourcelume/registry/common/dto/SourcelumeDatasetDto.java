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
 * Entity DTO for a {@code sourcelume_dataset} entity in Apache Atlas: a
 * dataset as it is managed directly through the datasets API, or a stored
 * {@code ProvenanceRecord} submitted through {@code POST /records}.
 *
 * <p>The ingest lifecycle fields live on {@link RecordEntityDto}; this
 * subtype adds the provenance attributes the ingest pipeline maps from a
 * conforming record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourcelumeDatasetDto extends RecordEntityDto {

    public static final String TYPE_NAME = "sourcelume_dataset";

    private String sourceUri;
    private String licenseId;

    public SourcelumeDatasetDto() {}

    public SourcelumeDatasetDto(String qualifiedName, String name, String sourceUri, String licenseId) {
        setQualifiedName(qualifiedName);
        setName(name);
        this.sourceUri = sourceUri;
        this.licenseId = licenseId;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(String licenseId) {
        this.licenseId = licenseId;
    }
}
