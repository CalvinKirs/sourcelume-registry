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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.apache.sourcelume.registry.common.dto.RecordEntityDto;
import org.apache.sourcelume.registry.common.dto.RecordType;
import org.apache.sourcelume.registry.common.dto.SourcelumeDatasetDto;
import org.apache.sourcelume.registry.core.AtlasAdapter;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST endpoint exposing dataset CRUD and query operations backed by {@link AtlasAdapter}.
 *
 * <p>Provides high-utility endpoints for registering and retrieving dataset entities:
 * <ul>
 *   <li>{@code POST /api/v1/datasets} — create or update dataset entity</li>
 *   <li>{@code GET  /api/v1/datasets?qualifiedName=...} — lookup by qualifiedName query param</li>
 *   <li>{@code GET  /api/v1/datasets/{qualifiedName}} — lookup by qualifiedName path param</li>
 *   <li>{@code GET  /api/v1/datasets/guid/{guid}} — lookup by Atlas GUID</li>
 * </ul>
 */
@Path("/api/v1/datasets")
@Tag(name = "Datasets", description = "Plain dataset entities; provenance records are managed via /records")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DatasetResource {

    private static final Logger log = LoggerFactory.getLogger(DatasetResource.class);

    private final AtlasAdapter atlasAdapter;

    @Inject
    public DatasetResource(AtlasAdapter atlasAdapter) {
        this.atlasAdapter = atlasAdapter;
    }

    /**
     * Creates or updates a Sourcelume dataset entity. Provenance records are managed via {@code POST /records}.
     *
     * @param dataset the dataset DTO to persist
     * @return 201 Created for a new dataset, 200 OK for an updated dataset, 400 Bad Request, or 409 Conflict if
     *     the qualifiedName is a provenance record
     */
    @POST
    @Operation(summary = "Create or update a dataset entity")
    @RequestBody(
            required = true,
            description = "Plain dataset attributes. Verdict fields (recordStatus, rawJsonLd, validationIssues,"
                    + " sha256, validatedBy, validatedAt) are set by the registry and rejected.")
    @APIResponse(
            responseCode = "201",
            description = "Dataset stored, with its assigned GUID",
            content = @Content(schema = @Schema(implementation = SourcelumeDatasetDto.class)))
    @APIResponse(
            responseCode = "200",
            description = "Existing dataset updated, with its assigned GUID",
            content = @Content(schema = @Schema(implementation = SourcelumeDatasetDto.class)))
    @APIResponse(
            responseCode = "400",
            description = "Missing qualifiedName or name, or verdict fields set",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "409",
            description = "The qualifiedName is a provenance record; use POST /records",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createOrUpdateDataset(SourcelumeDatasetDto dataset) {
        if (dataset == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Request body cannot be null"))
                    .build();
        }
        if (dataset.getQualifiedName() == null || dataset.getQualifiedName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("qualifiedName is required"))
                    .build();
        }
        if (dataset.getName() == null || dataset.getName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("name is required"))
                    .build();
        }
        // Verdict fields are only ever set by the validator chain in POST /records
        if (carriesVerdictFields(dataset)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Verdict fields are set by the registry; use POST /records"))
                    .build();
        }
        // A provenance record can only be changed via POST /records —
        // under any record type. Checking only the dataset store would
        // let a plain dataset shadow a model record's id, and
        // GET /records/{id} would then answer the shadow instead of the
        // record.
        List<RecordEntityDto> stored =
                atlasAdapter.getRecordEntitiesByQualifiedName(RecordType.entityTypeNames(), dataset.getQualifiedName());
        SourcelumeDatasetDto existing = null;
        for (RecordEntityDto entity : stored) {
            if (entity.getRecordStatus() != null) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse("Provenance record can only be changed via POST /records"))
                        .build();
            }
            if (entity instanceof SourcelumeDatasetDto datasetEntity) {
                existing = datasetEntity;
            }
        }

        String guid = atlasAdapter.createOrUpdateRecordEntity(dataset);
        dataset.setGuid(guid);
        log.info("Persisted dataset entity '{}' (GUID: {})", dataset.getQualifiedName(), guid);
        Response.Status status = existing == null ? Response.Status.CREATED : Response.Status.OK;
        return Response.status(status).entity(dataset).build();
    }

    private static boolean carriesVerdictFields(SourcelumeDatasetDto dataset) {
        return dataset.getRecordStatus() != null
                || dataset.getRawJsonLd() != null
                || dataset.getValidationIssues() != null
                || dataset.getSha256() != null
                || dataset.getValidatedBy() != null
                || dataset.getValidatedAt() != null;
    }

    /**
     * Query dataset by query parameters, e.g. {@code /api/v1/datasets?qualifiedName=...}.
     */
    @GET
    @Operation(summary = "Get a dataset by qualifiedName")
    @APIResponse(
            responseCode = "200",
            description = "The dataset",
            content = @Content(schema = @Schema(implementation = SourcelumeDatasetDto.class)))
    @APIResponse(
            responseCode = "400",
            description = "Missing qualifiedName",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "404",
            description = "No dataset for this qualifiedName",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getDatasetByQuery(
            @Parameter(description = "The dataset's qualified name", required = true) @QueryParam("qualifiedName")
                    String qualifiedName) {
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            SourcelumeDatasetDto dto = getDatasetByQualifiedName(qualifiedName);
            if (dto == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Dataset not found for qualifiedName: " + qualifiedName))
                        .build();
            }
            return Response.ok(dto).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Query parameter 'qualifiedName' is required"))
                .build();
    }

    /**
     * Retrieves a dataset by its Atlas GUID.
     */
    @GET
    @Path("/guid/{guid}")
    @Operation(summary = "Get a dataset by its backend GUID")
    @APIResponse(
            responseCode = "200",
            description = "The dataset",
            content = @Content(schema = @Schema(implementation = SourcelumeDatasetDto.class)))
    @APIResponse(
            responseCode = "404",
            description = "No dataset for this GUID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getDatasetByGuid(@PathParam("guid") String guid) {
        if (guid == null || guid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("guid is required"))
                    .build();
        }
        // A GUID can point at any record entity; the dataset API only
        // answers for dataset entities.
        RecordEntityDto stored = atlasAdapter.getRecordEntityByGuid(guid);
        if (!(stored instanceof SourcelumeDatasetDto)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Dataset not found for guid: " + guid))
                    .build();
        }
        return Response.ok(stored).build();
    }

    /**
     * Retrieves a dataset by its qualified name passed in the path.
     */
    @GET
    @Path("/{qualifiedName: .+}")
    @Operation(summary = "Get a dataset by qualifiedName in the path")
    @APIResponse(
            responseCode = "200",
            description = "The dataset",
            content = @Content(schema = @Schema(implementation = SourcelumeDatasetDto.class)))
    @APIResponse(
            responseCode = "404",
            description = "No dataset for this qualifiedName",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getDatasetByPath(@PathParam("qualifiedName") String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("qualifiedName is required"))
                    .build();
        }
        SourcelumeDatasetDto dto = getDatasetByQualifiedName(qualifiedName);
        if (dto == null && qualifiedName.contains(":/") && !qualifiedName.contains("://")) {
            // Handle URI schemes where HTTP path normalization collapsed '//' to '/'
            String reconstructed = qualifiedName.replaceFirst(":/", "://");
            dto = getDatasetByQualifiedName(reconstructed);
        }
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Dataset not found for qualifiedName: " + qualifiedName))
                    .build();
        }
        return Response.ok(dto).build();
    }

    private SourcelumeDatasetDto getDatasetByQualifiedName(String qualifiedName) {
        RecordEntityDto stored =
                atlasAdapter.getRecordEntityByQualifiedName(SourcelumeDatasetDto.TYPE_NAME, qualifiedName);
        return stored instanceof SourcelumeDatasetDto ? (SourcelumeDatasetDto) stored : null;
    }

    /** Wire DTO for error responses; named apart from the ingest API's ErrorResponse in the OpenAPI document. */
    @Schema(name = "DatasetErrorResponse")
    public record ErrorResponse(String error) {}
}
