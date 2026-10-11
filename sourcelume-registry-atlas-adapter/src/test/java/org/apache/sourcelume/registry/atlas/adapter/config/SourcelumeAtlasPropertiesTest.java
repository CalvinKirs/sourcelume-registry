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
package org.apache.sourcelume.registry.atlas.adapter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcelumeAtlasPropertiesTest {

    @TempDir
    Path tempDir;

    private SourcelumeAtlasProperties createProperties(String password, Optional<String> passwordFile) {
        return new SourcelumeAtlasProperties() {
            @Override
            public String url() {
                return "http://localhost:21000";
            }

            @Override
            public String user() {
                return "admin";
            }

            @Override
            public String password() {
                return password;
            }

            @Override
            public Optional<String> passwordFile() {
                return passwordFile;
            }

            @Override
            public String specContextResource() {
                return "context/0.0.1/sourcelume.jsonld";
            }

            @Override
            public String typedefsResource() {
                return "models/sourcelume/sourcelume_model.json";
            }

            @Override
            public boolean bootstrapOnStartup() {
                return true;
            }

            @Override
            public int maxRetries() {
                return 5;
            }

            @Override
            public long retryDelayMs() {
                return 2000;
            }
        };
    }

    @Test
    void testResolvedPasswordDefaultsToPasswordWhenNoFile() {
        SourcelumeAtlasProperties props = createProperties("mySecret", Optional.empty());
        assertEquals("mySecret", props.resolvedPassword());
    }

    @Test
    void testResolvedPasswordDefaultsToPasswordWhenFileNotFound() {
        SourcelumeAtlasProperties props = createProperties(
                "mySecret", Optional.of(tempDir.resolve("nonexistent.txt").toString()));
        assertEquals("mySecret", props.resolvedPassword());
    }

    @Test
    void testResolvedPasswordReadsFromFile() throws IOException {
        Path pwdFile = tempDir.resolve("secret.txt");
        Files.writeString(pwdFile, "  fileSecretPass  \n");

        SourcelumeAtlasProperties props = createProperties("defaultPass", Optional.of(pwdFile.toString()));
        assertEquals("fileSecretPass", props.resolvedPassword());
    }

    @Test
    void testResolvedPasswordThrowsWhenFileReadFails() {
        // tempDir is a directory, reading it as string will fail with IOException
        SourcelumeAtlasProperties props = createProperties("defaultPass", Optional.of(tempDir.toString()));
        assertThrows(IllegalStateException.class, props::resolvedPassword);
    }
}
