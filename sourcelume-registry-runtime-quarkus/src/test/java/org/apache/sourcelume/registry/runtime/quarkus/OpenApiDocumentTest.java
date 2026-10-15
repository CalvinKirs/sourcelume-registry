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
package org.apache.sourcelume.registry.runtime.quarkus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The generated OpenAPI document is the API contract: it must list every
 * endpoint with the status codes the resources actually return, and match
 * the committed snapshot so API changes show up as diffs in review.
 */
@QuarkusTest
class OpenApiDocumentTest {

    /** The committed contract, relative to this module. */
    private static final Path SNAPSHOT = Path.of("../docs/openapi.yaml");

    @Test
    void shouldDocumentRecordEndpoints() {
        given().queryParam("format", "json")
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .body("info.title", equalTo("Apache Sourcelume Registry API"))
                .body("paths.'/records'.post.responses.keySet()", hasItems("200", "201", "400", "409", "415"))
                .body("paths.'/records'.post.requestBody.content", hasKey("application/ld+json"))
                .body("paths.'/records/{id}'.get.responses.keySet()", hasItems("200", "404"))
                .body("paths.'/records/validate'.post.responses.keySet()", containsInAnyOrder("200", "422"));
    }

    @Test
    void shouldDocumentDatasetAndTypeEndpoints() {
        given().queryParam("format", "json")
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths.'/api/v1/datasets'.post.responses.keySet()", hasItems("201", "400", "409"))
                .body("paths.'/api/v1/datasets'.get.responses.keySet()", hasItems("200", "400", "404"))
                .body("paths.'/api/v1/datasets/guid/{guid}'.get.responses.keySet()", hasItems("200", "404"))
                .body("paths.'/api/v1/types/typedefs'.get.responses", hasKey("200"));
    }

    /**
     * Fails when the API changes without the snapshot. To update it, run
     * {@code mvn -pl sourcelume-registry-runtime-quarkus test -Dtest=OpenApiDocumentTest -Dopenapi.snapshot.update=true}
     * from the repository root and commit docs/openapi.yaml.
     */
    @Test
    void shouldMatchCommittedSnapshot() throws IOException {
        String generated = given().when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            String header = licenseHeader(readSnapshot());
            Files.writeString(SNAPSHOT, header + generated.strip() + "\n", StandardCharsets.UTF_8);
        }

        String committed = readSnapshot();
        assertEquals(
                committed.substring(licenseHeader(committed).length()).strip(),
                generated.strip(),
                "docs/openapi.yaml is out of date; regenerate it with: mvn -pl sourcelume-registry-runtime-quarkus"
                        + " test -Dtest=OpenApiDocumentTest -Dopenapi.snapshot.update=true");
    }

    /** The snapshot with LF line endings, as a Windows checkout (core.autocrlf) may carry CRLF. */
    private static String readSnapshot() throws IOException {
        return Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** The leading comment block (the ASF header RAT requires), kept across updates. */
    private static String licenseHeader(String snapshot) {
        StringBuilder header = new StringBuilder();
        for (String line : snapshot.split("\n", -1)) {
            if (!line.startsWith("#")) {
                break;
            }
            header.append(line).append('\n');
        }
        return header.toString();
    }
}
