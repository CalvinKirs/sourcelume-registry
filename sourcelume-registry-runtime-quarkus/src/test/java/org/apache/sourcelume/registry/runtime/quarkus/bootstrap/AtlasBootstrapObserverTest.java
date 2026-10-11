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
package org.apache.sourcelume.registry.runtime.quarkus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.runtime.StartupEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sourcelume.registry.atlas.adapter.config.SourcelumeAtlasProperties;
import org.junit.jupiter.api.Test;

class AtlasBootstrapObserverTest {

    private SourcelumeAtlasProperties createProperties(boolean bootstrapOnStartup, int maxRetries, long delayMs) {
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
                return "secret";
            }

            @Override
            public Optional<String> passwordFile() {
                return Optional.empty();
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
                return bootstrapOnStartup;
            }

            @Override
            public int maxRetries() {
                return maxRetries;
            }

            @Override
            public long retryDelayMs() {
                return delayMs;
            }
        };
    }

    @Test
    void testDisabledBootstrapOnStartup() {
        AtomicInteger bootstrapCalls = new AtomicInteger(0);
        AtlasBootstrapService mockService = new AtlasBootstrapService(null, null) {
            @Override
            public boolean bootstrap() {
                bootstrapCalls.incrementAndGet();
                return true;
            }
        };

        AtlasBootstrapObserver observer = new AtlasBootstrapObserver(mockService, createProperties(false, 3, 10));
        observer.onStart(new StartupEvent());

        assertEquals(0, bootstrapCalls.get());
    }

    @Test
    void testBootstrapSucceedsOnFirstAttempt() {
        AtomicInteger bootstrapCalls = new AtomicInteger(0);
        AtlasBootstrapService mockService = new AtlasBootstrapService(null, null) {
            @Override
            public boolean bootstrap() {
                bootstrapCalls.incrementAndGet();
                return true;
            }
        };

        AtlasBootstrapObserver observer = new AtlasBootstrapObserver(mockService, createProperties(true, 3, 10));
        observer.onStart(new StartupEvent());

        assertEquals(1, bootstrapCalls.get());
    }

    @Test
    void testBootstrapRetriesAndSucceeds() {
        AtomicInteger bootstrapCalls = new AtomicInteger(0);
        AtlasBootstrapService mockService = new AtlasBootstrapService(null, null) {
            @Override
            public boolean bootstrap() {
                return bootstrapCalls.incrementAndGet() >= 2;
            }
        };

        AtlasBootstrapObserver observer = new AtlasBootstrapObserver(mockService, createProperties(true, 3, 10));
        observer.onStart(new StartupEvent());

        assertEquals(2, bootstrapCalls.get());
    }

    @Test
    void testBootstrapExhaustsRetries() {
        AtomicInteger bootstrapCalls = new AtomicInteger(0);
        AtlasBootstrapService mockService = new AtlasBootstrapService(null, null) {
            @Override
            public boolean bootstrap() {
                bootstrapCalls.incrementAndGet();
                return false;
            }
        };

        AtlasBootstrapObserver observer = new AtlasBootstrapObserver(mockService, createProperties(true, 3, 5));
        observer.onStart(new StartupEvent());

        assertEquals(3, bootstrapCalls.get());
    }

    @Test
    void testBootstrapInterruptedDuringRetry() {
        AtomicInteger bootstrapCalls = new AtomicInteger(0);
        AtlasBootstrapService mockService = new AtlasBootstrapService(null, null) {
            @Override
            public boolean bootstrap() {
                bootstrapCalls.incrementAndGet();
                Thread.currentThread().interrupt();
                return false;
            }
        };

        AtlasBootstrapObserver observer = new AtlasBootstrapObserver(mockService, createProperties(true, 3, 50));
        observer.onStart(new StartupEvent());

        assertEquals(1, bootstrapCalls.get());
        assertTrue(Thread.interrupted(), "Thread interrupt status should be set");
    }
}
