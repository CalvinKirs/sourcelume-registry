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
package org.apache.sourcelume.registry.runtime.quarkus.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Consumer;
import org.apache.sourcelume.registry.common.dto.RecordEntityDto;
import org.apache.sourcelume.registry.common.dto.RecordStatus;
import org.apache.sourcelume.registry.common.dto.SourcelumeDatasetDto;
import org.apache.sourcelume.registry.core.AtlasAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatasetResourceUnitTest {

    private DatasetResource resource;
    private StubAtlasAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StubAtlasAdapter();
        resource = new DatasetResource(adapter);
    }

    @Test
    void testGetDatasetByQueryWithMissingParamReturnsBadRequest() {
        Response r1 = resource.getDatasetByQuery(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r1.getStatus());

        Response r2 = resource.getDatasetByQuery("   ");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r2.getStatus());
    }

    @Test
    void testGetDatasetByQueryFoundAndNotFound() {
        SourcelumeDatasetDto ds = new SourcelumeDatasetDto();
        ds.setQualifiedName("sourcelume://datasets/test1");
        ds.setName("Test 1");
        adapter.datasetByQName = ds;

        Response found = resource.getDatasetByQuery("sourcelume://datasets/test1");
        assertEquals(Response.Status.OK.getStatusCode(), found.getStatus());

        adapter.datasetByQName = null;
        Response notFound = resource.getDatasetByQuery("sourcelume://datasets/missing");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), notFound.getStatus());
    }

    @Test
    void testGetDatasetByGuidValidationAndLookup() {
        Response r1 = resource.getDatasetByGuid(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r1.getStatus());

        Response r2 = resource.getDatasetByGuid("   ");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r2.getStatus());

        SourcelumeDatasetDto ds = new SourcelumeDatasetDto();
        ds.setGuid("guid-123");
        adapter.datasetByGuid = ds;

        Response found = resource.getDatasetByGuid("guid-123");
        assertEquals(Response.Status.OK.getStatusCode(), found.getStatus());

        adapter.datasetByGuid = null;
        Response notFound = resource.getDatasetByGuid("guid-missing");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), notFound.getStatus());
    }

    @Test
    void testGetDatasetByPathReconstruction() {
        Response r1 = resource.getDatasetByPath(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r1.getStatus());

        SourcelumeDatasetDto ds = new SourcelumeDatasetDto();
        ds.setQualifiedName("sourcelume://datasets/test1");
        adapter.datasetByQName = ds;

        Response foundReconstructed = resource.getDatasetByPath("sourcelume:/datasets/test1");
        assertEquals(Response.Status.OK.getStatusCode(), foundReconstructed.getStatus());

        adapter.datasetByQName = null;
        Response notFound = resource.getDatasetByPath("sourcelume:/datasets/missing");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), notFound.getStatus());
    }

    @Test
    void testCreateOrUpdateDatasetValidation() {
        Response rNull = resource.createOrUpdateDataset(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), rNull.getStatus());

        SourcelumeDatasetDto missingQName = new SourcelumeDatasetDto();
        missingQName.setName("Name");
        Response rNoQName = resource.createOrUpdateDataset(missingQName);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), rNoQName.getStatus());

        SourcelumeDatasetDto missingName = new SourcelumeDatasetDto();
        missingName.setQualifiedName("sourcelume://datasets/test");
        Response rNoName = resource.createOrUpdateDataset(missingName);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), rNoName.getStatus());

        SourcelumeDatasetDto valid = new SourcelumeDatasetDto();
        valid.setQualifiedName("sourcelume://datasets/test");
        valid.setName("Test");
        Response rOk = resource.createOrUpdateDataset(valid);
        assertEquals(Response.Status.CREATED.getStatusCode(), rOk.getStatus());
    }

    @Test
    void testCreateOrUpdateDatasetRejectsEachVerdictField() {
        List<Consumer<SourcelumeDatasetDto>> verdictFields = List.of(
                d -> d.setRecordStatus(RecordStatus.VALIDATED),
                d -> d.setRawJsonLd("{}"),
                d -> d.setValidationIssues("[]"),
                d -> d.setSha256("abc"),
                d -> d.setValidatedBy("json-schema,shacl"),
                d -> d.setValidatedAt("2026-10-15T00:00:00Z"));

        for (Consumer<SourcelumeDatasetDto> verdictField : verdictFields) {
            SourcelumeDatasetDto dataset = plainDataset("sourcelume://datasets/test");
            verdictField.accept(dataset);

            Response response = resource.createOrUpdateDataset(dataset);

            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
        assertEquals(0, adapter.writes);
    }

    @Test
    void testCreateOrUpdateDatasetRefusesToOverwriteProvenanceRecords() {
        for (RecordStatus status : RecordStatus.values()) {
            SourcelumeDatasetDto record = plainDataset("https://example.org/records/r1");
            record.setRecordStatus(status);
            adapter.datasetByQName = record;

            Response response = resource.createOrUpdateDataset(plainDataset("https://example.org/records/r1"));

            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        }
        assertEquals(0, adapter.writes);
    }

    @Test
    void testCreateOrUpdateDatasetUpdatesExistingPlainDataset() {
        adapter.datasetByQName = plainDataset("sourcelume://datasets/test");

        Response response = resource.createOrUpdateDataset(plainDataset("sourcelume://datasets/test"));

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(1, adapter.writes);
    }

    private static SourcelumeDatasetDto plainDataset(String qualifiedName) {
        SourcelumeDatasetDto dataset = new SourcelumeDatasetDto();
        dataset.setQualifiedName(qualifiedName);
        dataset.setName("Test");
        return dataset;
    }

    private static class StubAtlasAdapter implements AtlasAdapter {
        SourcelumeDatasetDto datasetByQName;
        SourcelumeDatasetDto datasetByGuid;
        int writes;

        @Override
        public boolean isServerReady() {
            return true;
        }

        @Override
        public TypeDefinitionModel loadTypeDefs(String resourcePath) {
            return null;
        }

        @Override
        public TypeDefinitionModel registerOrUpdateTypeDefs(TypeDefinitionModel model) {
            return null;
        }

        @Override
        public String createOrUpdateRecordEntity(RecordEntityDto record) {
            writes++;
            return "guid-created";
        }

        @Override
        public RecordEntityDto getRecordEntityByQualifiedName(String entityType, String qualifiedName) {
            if (datasetByQName != null
                    && entityType.equals(datasetByQName.getTypeName())
                    && datasetByQName.getQualifiedName().equals(qualifiedName)) {
                return datasetByQName;
            }
            return null;
        }

        @Override
        public RecordEntityDto getRecordEntityByGuid(String guid) {
            return datasetByGuid;
        }

        @Override
        public List<RecordEntityDto> findRecordEntitiesByStatus(String entityType, RecordStatus status, int limit) {
            return List.of();
        }
    }
}
