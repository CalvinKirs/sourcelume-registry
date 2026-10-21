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
package org.apache.sourcelume.registry.runtime.quarkus.test;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sourcelume.registry.common.dto.RecordEntityDto;
import org.apache.sourcelume.registry.common.dto.RecordStatus;
import org.apache.sourcelume.registry.core.AtlasAdapter;

@Mock
@ApplicationScoped
public class TestAtlasAdapter implements AtlasAdapter {

    /** Atlas keys entities by (type, qualifiedName) — so does the fake, or cross-type collisions stay invisible. */
    private record StoreKey(String entityType, String qualifiedName) {}

    private final Map<StoreKey, RecordEntityDto> byQualifiedName = new ConcurrentHashMap<>();
    private final Map<String, RecordEntityDto> byGuid = new ConcurrentHashMap<>();

    @Override
    public boolean isServerReady() {
        return false;
    }

    @Override
    public TypeDefinitionModel loadTypeDefs(String resourcePath) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            return new TypeDefinitionModel(resourcePath, bytes, 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TypeDefinitionModel registerOrUpdateTypeDefs(TypeDefinitionModel typeDefs) {
        return typeDefs;
    }

    @Override
    public String createOrUpdateRecordEntity(RecordEntityDto record) {
        StoreKey key = new StoreKey(record.getTypeName(), record.getQualifiedName());
        RecordEntityDto existing = byQualifiedName.get(key);
        String guid = record.getGuid() != null
                ? record.getGuid()
                : existing != null ? existing.getGuid() : UUID.randomUUID().toString();
        record.setGuid(guid);
        byQualifiedName.put(key, record);
        byGuid.put(guid, record);
        return guid;
    }

    @Override
    public RecordEntityDto getRecordEntityByQualifiedName(String entityType, String qualifiedName) {
        return byQualifiedName.get(new StoreKey(entityType, qualifiedName));
    }

    @Override
    public void deleteRecordEntity(String entityType, String qualifiedName) {
        RecordEntityDto removed = byQualifiedName.remove(new StoreKey(entityType, qualifiedName));
        if (removed != null && removed.getGuid() != null) {
            byGuid.remove(removed.getGuid());
        }
    }

    @Override
    public RecordEntityDto getRecordEntityByGuid(String guid) {
        return byGuid.get(guid);
    }

    @Override
    public List<RecordEntityDto> findRecordEntitiesByStatus(String entityType, RecordStatus status, int limit) {
        return byQualifiedName.values().stream()
                .filter(d -> entityType.equals(d.getTypeName()) && d.getRecordStatus() == status)
                .limit(limit)
                .toList();
    }
}
