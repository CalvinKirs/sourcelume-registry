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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.UUID;
import org.apache.sourcelume.registry.common.dto.RecordStatus;
import org.apache.sourcelume.registry.common.dto.SourcelumeDatasetDto;
import org.apache.sourcelume.registry.common.dto.SourcelumeModelDto;
import org.apache.sourcelume.registry.core.AtlasAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class DatasetResourceTest {

    @Inject
    AtlasAdapter atlasAdapter;

    @Inject
    ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(strings = {"qualifiedName", "name"})
    void shouldRejectMissingNullAndBlankRequiredFields(String field) {
        for (JsonNode value : new JsonNode[] {
            null, NullNode.instance, TextNode.valueOf(""), TextNode.valueOf(" \t\r\n"), TextNode.valueOf("\u2003")
        }) {
            ObjectNode body = validRequest();
            String qualifiedName = body.path("qualifiedName").asText();
            if (value == null) {
                body.remove(field);
            } else {
                body.set(field, value);
            }

            given().contentType(ContentType.JSON)
                    .body(body.toString())
                    .when()
                    .post("/api/v1/datasets")
                    .then()
                    .statusCode(400)
                    .body("error", equalTo(field + " is required"));
            assertNull(atlasAdapter.getRecordEntityByQualifiedName(SourcelumeDatasetDto.TYPE_NAME, qualifiedName));
        }
    }

    @Test
    void shouldReportMultipleInvalidFieldsInAFixedOrder() {
        // qualifiedName first, then name, then the verdict fields - not alphabetical.
        assertRejectedWith("{}", "qualifiedName is required");
        assertRejectedWith("{\"qualifiedName\":\"x\",\"recordStatus\":\"VALIDATED\"}", "name is required");
        assertRejectedWith(
                "{\"qualifiedName\":\"x\",\"name\":\"n\",\"recordStatus\":\"VALIDATED\"}",
                "Verdict fields are set by the registry; use POST /records");
        assertRejectedWith("{\"recordStatus\":\"VALIDATED\"}", "qualifiedName is required");
    }

    private static void assertRejectedWith(String body, String message) {
        given().contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(400)
                .body("error", equalTo(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"recordStatus", "rawJsonLd", "validationIssues", "sha256", "validatedBy", "validatedAt"})
    void shouldRejectEachNonNullVerdictField(String field) {
        ObjectNode body = validRequest();
        body.put(field, field.equals("recordStatus") ? "VALIDATED" : "forged");
        body.put("carryingVerdictFields", false);

        given().contentType(ContentType.JSON)
                .body(body.toString())
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(400)
                .body("error", equalTo("Verdict fields are set by the registry; use POST /records"));
        assertNull(atlasAdapter.getRecordEntityByQualifiedName(
                SourcelumeDatasetDto.TYPE_NAME, body.path("qualifiedName").asText()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"recordStatus", "rawJsonLd", "validationIssues", "sha256", "validatedBy", "validatedAt"})
    void shouldContinueToAllowNullVerdictFields(String field) {
        ObjectNode body = validRequest();
        body.putNull(field);

        given().contentType(ContentType.JSON)
                .body(body.toString())
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(201)
                .body("name", equalTo("Test dataset"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null"})
    void shouldRejectMissingRequestBody(String body) {
        given().contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(400)
                .body("error", equalTo("Request body cannot be null"));
    }

    @Test
    void shouldIgnoreUnknownFieldsAndKeepNonblankValuesUnchanged() {
        ObjectNode body = validRequest();
        body.put("name", "  Test dataset  ");
        body.putObject("extension").put("arbitrary", true);

        given().contentType(ContentType.JSON)
                .body(body.toString())
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(201)
                .body("name", equalTo("  Test dataset  "));
    }

    private ObjectNode validRequest() {
        return objectMapper
                .createObjectNode()
                .put("qualifiedName", "https://example.org/datasets/" + UUID.randomUUID())
                .put("name", "Test dataset");
    }

    @Test
    void shouldCreateAndFetchDataset() {
        SourcelumeDatasetDto dataset = new SourcelumeDatasetDto();
        dataset.setQualifiedName("sourcelume://datasets/test-1");
        dataset.setName("Test Dataset 1");
        dataset.setDescription("A test dataset for integration verification");
        dataset.setSourceUri("https://github.com/apache/sourcelume-registry");
        dataset.setLicenseId("Apache-2.0");

        // 1. Create dataset via POST
        String guid = given().contentType(ContentType.JSON)
                .body(dataset)
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(201)
                .body("qualifiedName", equalTo("sourcelume://datasets/test-1"))
                .body("name", equalTo("Test Dataset 1"))
                .body("licenseId", equalTo("Apache-2.0"))
                .body("guid", notNullValue())
                .extract()
                .path("guid");

        // 2. Update the existing dataset
        dataset.setDescription("Updated description");
        given().contentType(ContentType.JSON)
                .body(dataset)
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(200)
                .body("guid", equalTo(guid))
                .body("description", equalTo("Updated description"));

        // 3. Fetch by query parameter
        given().queryParam("qualifiedName", "sourcelume://datasets/test-1")
                .when()
                .get("/api/v1/datasets")
                .then()
                .statusCode(200)
                .body("qualifiedName", equalTo("sourcelume://datasets/test-1"))
                .body("guid", equalTo(guid));

        // 4. Fetch by path parameter (both full and collapsed URI paths)
        given().when()
                .get("/api/v1/datasets/sourcelume://datasets/test-1")
                .then()
                .statusCode(200)
                .body("qualifiedName", equalTo("sourcelume://datasets/test-1"))
                .body("name", equalTo("Test Dataset 1"));

        given().when()
                .get("/api/v1/datasets/sourcelume:/datasets/test-1")
                .then()
                .statusCode(200)
                .body("qualifiedName", equalTo("sourcelume://datasets/test-1"))
                .body("name", equalTo("Test Dataset 1"));

        // 5. Fetch by GUID
        given().when()
                .get("/api/v1/datasets/guid/" + guid)
                .then()
                .statusCode(200)
                .body("qualifiedName", equalTo("sourcelume://datasets/test-1"))
                .body("guid", equalTo(guid));
    }

    @Test
    void shouldReturn404WhenDatasetNotFound() {
        given().queryParam("qualifiedName", "sourcelume://datasets/nonexistent")
                .when()
                .get("/api/v1/datasets")
                .then()
                .statusCode(404);

        given().when()
                .get("/api/v1/datasets/sourcelume://datasets/nonexistent")
                .then()
                .statusCode(404);

        given().when().get("/api/v1/datasets/guid/invalid-guid").then().statusCode(404);
    }

    @Test
    void shouldNotAllowValidatedRecordToBeOverwritten() {
        String recordId = "https://example.org/records/locked-record";
        SourcelumeDatasetDto record = new SourcelumeDatasetDto();
        record.setQualifiedName(recordId);
        record.setName("Locked record");
        record.setLicenseId("https://spdx.org/licenses/Apache-2.0.html");
        record.setRecordStatus(RecordStatus.VALIDATED);
        atlasAdapter.createOrUpdateRecordEntity(record);

        // 1. Forged verdict
        given().contentType(ContentType.JSON)
                .body("{\"qualifiedName\": \"" + recordId + "\", \"name\": \"tampered\","
                        + " \"licenseId\": \"https://spdx.org/licenses/GPL-3.0.html\","
                        + " \"recordStatus\": \"VALIDATED\"}")
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(400);

        // 2. Overwrite without a verdict
        given().contentType(ContentType.JSON)
                .body("{\"qualifiedName\": \"" + recordId + "\", \"name\": \"tampered\","
                        + " \"licenseId\": \"https://spdx.org/licenses/GPL-3.0.html\"}")
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(409);

        // 3. Record is unchanged
        given().queryParam("qualifiedName", recordId)
                .when()
                .get("/api/v1/datasets")
                .then()
                .statusCode(200)
                .body("name", equalTo("Locked record"))
                .body("licenseId", equalTo("https://spdx.org/licenses/Apache-2.0.html"))
                .body("recordStatus", equalTo("VALIDATED"));
    }

    @Test
    void shouldNotShadowARecordOfAnotherType() {
        // The 409 guard must see records in every store: a dataset
        // entity written under a model record's id would shadow it, and
        // GET /records/{id} would answer the plain dataset.
        String recordId = "https://example.org/records/locked-model";
        SourcelumeModelDto record = new SourcelumeModelDto();
        record.setQualifiedName(recordId);
        record.setName("Locked model record");
        record.setRecordStatus(RecordStatus.VALIDATED);
        atlasAdapter.createOrUpdateRecordEntity(record);

        given().contentType(ContentType.JSON)
                .body("{\"qualifiedName\": \"" + recordId + "\", \"name\": \"tampered\"}")
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(409);

        // The record's status view still answers the model record.
        given().urlEncodingEnabled(false)
                .when()
                .get("/records/" + java.net.URLEncoder.encode(recordId, java.nio.charset.StandardCharsets.UTF_8))
                .then()
                .statusCode(200)
                .body("type", equalTo("TrainingProvenanceRecord"))
                .body("recordStatus", equalTo("VALIDATED"));
    }

    @Test
    void shouldReturn400OnInvalidPayload() {
        SourcelumeDatasetDto invalid = new SourcelumeDatasetDto();
        // Missing qualifiedName and name

        given().contentType(ContentType.JSON)
                .body(invalid)
                .when()
                .post("/api/v1/datasets")
                .then()
                .statusCode(400);
    }
}
