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

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.apache.sourcelume.registry.common.dto.RecordStatus;
import org.apache.sourcelume.registry.common.dto.SourcelumeDatasetDto;
import org.apache.sourcelume.registry.core.AtlasAdapter;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DatasetResourceTest {

    @Inject
    AtlasAdapter atlasAdapter;

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
