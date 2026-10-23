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
package org.apache.sourcelume.registry.runtime.quarkus.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import io.smallrye.openapi.api.SmallRyeOpenAPI;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.sourcelume.registry.common.dto.RecordType;
import org.apache.sourcelume.registry.common.spec.SpecResourceLoader;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.examples.Example;
import org.eclipse.microprofile.openapi.models.media.Schema;

/** Imports the same versioned record schemas that the validator uses, without changing raw JSON-LD ingestion. */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class RecordSchemaFilter implements OASFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Schema submission = OASFactory.createSchema()
                .description("A dataset or model provenance record. The spec schemas describe conforming records;"
                        + " incomplete records with a nonblank id may also be submitted for curation.");
        Map<String, Schema> recordSchemas = recordSchemas();
        for (RecordType type : RecordType.values()) {
            openAPI.getComponents().addSchema(type.typeName(), recordSchemas.get(type.typeName()));
            submission.addAnyOf(reference(type.typeName()));
        }

        // Ingest deliberately retains non-conforming records. A strict oneOf
        // of the spec schemas would incorrectly rule out successful INCOMPLETE submissions.
        Schema incomplete = OASFactory.createSchema()
                .type(List.of(Schema.SchemaType.OBJECT))
                .description("A non-conforming record retained for curation. Only a nonblank id is needed for storage;"
                        + " missing or unknown record types are reported through validation issues.")
                .addRequired("id")
                .addProperty(
                        "id",
                        OASFactory.createSchema()
                                .type(List.of(Schema.SchemaType.STRING))
                                .pattern("\\S"));
        openAPI.getComponents().addSchema("IncompleteRecordSubmission", incomplete);
        submission.addAnyOf(reference("IncompleteRecordSubmission"));
        openAPI.getComponents().addSchema("RecordSubmission", submission);

        for (String path : List.of("/records", "/records/validate")) {
            openAPI.getPaths()
                    .getPathItem(path)
                    .getPOST()
                    .getRequestBody()
                    .getContent()
                    .getMediaTypes()
                    .values()
                    .forEach(media -> {
                        media.addExample("dataset", example("Dataset provenance", "openapi/dataset-record.jsonld"));
                        media.addExample(
                                "model", example("Model training provenance", "openapi/training-record.jsonld"));
                        media.addExample(
                                "incomplete",
                                OASFactory.createExample()
                                        .summary("Incomplete record: returns validation issues")
                                        .value(Map.of("id", "https://example.org/records/incomplete")));
                    });
        }
    }

    private static Schema reference(String name) {
        return OASFactory.createSchema().ref("#/components/schemas/" + name);
    }

    private static Map<String, Schema> recordSchemas() {
        try {
            ObjectNode document = OBJECT_MAPPER.createObjectNode().put("openapi", "3.1.0");
            ObjectNode schemas = document.putObject("components").putObject("schemas");
            for (RecordType type : RecordType.values()) {
                schemas.set(
                        type.typeName(),
                        OBJECT_MAPPER.readTree(SpecResourceLoader.loadResourceString(type.schemaResource())));
            }
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(document);
            // Parse only the bundled schemas; do not scan annotations, application
            // static documents or filters again while this build-time filter runs.
            return SmallRyeOpenAPI.builder()
                    .enableAnnotationScan(false)
                    .enableModelReader(false)
                    .enableStandardStaticFiles(false)
                    .enableStandardFilter(false)
                    .withCustomStaticFile(() -> new ByteArrayInputStream(json))
                    .build()
                    .model()
                    .getComponents()
                    .getSchemas();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not import the spec schemas into OpenAPI", e);
        }
    }

    private static Example example(String summary, String resource) {
        try {
            Object value = OBJECT_MAPPER.readValue(SpecResourceLoader.loadResourceString(resource), Object.class);
            return OASFactory.createExample().summary(summary).value(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid OpenAPI example: " + resource, e);
        }
    }
}
