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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import org.apache.sourcelume.registry.core.AtlasAdapter;
import org.apache.sourcelume.registry.core.AtlasAdapter.TypeDefinitionModel;

/**
 * REST endpoint exposing Sourcelume model and type definitions.
 *
 * <ul>
 *   <li>{@code GET /api/v1/types/typedefs} — returns full typedefs model</li>
 *   <li>{@code GET /api/v1/types/dataset} — returns dataset entity definition model</li>
 * </ul>
 */
@Path("/api/v1/types")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class TypeResource {

    private final AtlasAdapter atlasAdapter;

    @Inject
    public TypeResource(AtlasAdapter atlasAdapter) {
        this.atlasAdapter = atlasAdapter;
    }

    /**
     * Returns the full Sourcelume typedefs registered or bootstrapped in Atlas.
     */
    @GET
    @Path("/typedefs")
    public Response getTypeDefs() {
        TypeDefinitionModel model = atlasAdapter.loadTypeDefs("models/sourcelume/sourcelume_model.json");
        return Response.ok(new String(model.getRawJson(), StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Returns the Sourcelume dataset entity definition model.
     */
    @GET
    @Path("/dataset")
    public Response getDatasetTypeDef() {
        TypeDefinitionModel model = atlasAdapter.loadTypeDefs("models/sourcelume/sourcelume_model.json");
        return Response.ok(new String(model.getRawJson(), StandardCharsets.UTF_8))
                .build();
    }
}
