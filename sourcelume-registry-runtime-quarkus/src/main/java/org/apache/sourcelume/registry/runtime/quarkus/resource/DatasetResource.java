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
import java.util.Map;
import org.apache.sourcelume.registry.common.dto.SourcelumeDatasetDto;
import org.apache.sourcelume.registry.core.AtlasAdapter;
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
     * Creates or updates a Sourcelume dataset entity.
     *
     * @param dataset the dataset DTO to persist
     * @return 201 Created with the persisted dataset DTO including assigned GUID, or 400 Bad Request
     */
    @POST
    public Response createOrUpdateDataset(SourcelumeDatasetDto dataset) {
        if (dataset == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body cannot be null"))
                    .build();
        }
        if (dataset.getQualifiedName() == null || dataset.getQualifiedName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "qualifiedName is required"))
                    .build();
        }
        if (dataset.getName() == null || dataset.getName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "name is required"))
                    .build();
        }

        String guid = atlasAdapter.createOrUpdateDatasetEntity(dataset);
        dataset.setGuid(guid);
        log.info("Persisted dataset entity '{}' (GUID: {})", dataset.getQualifiedName(), guid);
        return Response.status(Response.Status.CREATED).entity(dataset).build();
    }

    /**
     * Query dataset by query parameters, e.g. {@code /api/v1/datasets?qualifiedName=...}.
     */
    @GET
    public Response getDatasetByQuery(@QueryParam("qualifiedName") String qualifiedName) {
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            SourcelumeDatasetDto dto = atlasAdapter.getDatasetByQualifiedName(qualifiedName);
            if (dto == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Dataset not found for qualifiedName: " + qualifiedName))
                        .build();
            }
            return Response.ok(dto).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Query parameter 'qualifiedName' is required"))
                .build();
    }

    /**
     * Retrieves a dataset by its Atlas GUID.
     */
    @GET
    @Path("/guid/{guid}")
    public Response getDatasetByGuid(@PathParam("guid") String guid) {
        if (guid == null || guid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "guid is required"))
                    .build();
        }
        SourcelumeDatasetDto dto = atlasAdapter.getDatasetByGuid(guid);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Dataset not found for guid: " + guid))
                    .build();
        }
        return Response.ok(dto).build();
    }

    /**
     * Retrieves a dataset by its qualified name passed in the path.
     */
    @GET
    @Path("/{qualifiedName: .+}")
    public Response getDatasetByPath(@PathParam("qualifiedName") String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "qualifiedName is required"))
                    .build();
        }
        SourcelumeDatasetDto dto = atlasAdapter.getDatasetByQualifiedName(qualifiedName);
        if (dto == null && qualifiedName.contains(":/") && !qualifiedName.contains("://")) {
            // Handle URI schemes where HTTP path normalization collapsed '//' to '/'
            String reconstructed = qualifiedName.replaceFirst(":/", "://");
            dto = atlasAdapter.getDatasetByQualifiedName(reconstructed);
        }
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Dataset not found for qualifiedName: " + qualifiedName))
                    .build();
        }
        return Response.ok(dto).build();
    }
}
