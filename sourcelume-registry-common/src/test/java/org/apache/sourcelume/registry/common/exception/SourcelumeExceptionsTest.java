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
package org.apache.sourcelume.registry.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SourcelumeExceptionsTest {

    @Test
    void testSourcelumeException() {
        SourcelumeException e1 = new SourcelumeException("Error message");
        assertEquals("Error message", e1.getMessage());
        assertNull(e1.getCause());

        Throwable cause = new RuntimeException("root cause");
        SourcelumeException e2 = new SourcelumeException("Error with cause", cause);
        assertEquals("Error with cause", e2.getMessage());
        assertEquals(cause, e2.getCause());
    }

    @Test
    void testSourcelumeNotFoundException() {
        SourcelumeNotFoundException e1 = new SourcelumeNotFoundException("Not found message");
        assertEquals("Not found message", e1.getMessage());
        assertNull(e1.getEntityType());
        assertNull(e1.getIdentifier());

        SourcelumeNotFoundException e2 = new SourcelumeNotFoundException("dataset", "id-123");
        assertEquals("Entity of type 'dataset' with identifier 'id-123' not found", e2.getMessage());
        assertEquals("dataset", e2.getEntityType());
        assertEquals("id-123", e2.getIdentifier());
    }

    @Test
    void testSourcelumeValidationException() {
        SourcelumeValidationException e1 = new SourcelumeValidationException("Validation failed");
        assertEquals("Validation failed", e1.getMessage());
        assertTrue(e1.getValidationErrors().isEmpty());

        SourcelumeValidationException e2 =
                new SourcelumeValidationException("Validation failed", List.of("error1", "error2"));
        assertEquals("Validation failed", e2.getMessage());
        assertEquals(List.of("error1", "error2"), e2.getValidationErrors());

        SourcelumeValidationException e3 = new SourcelumeValidationException("Validation failed", null);
        assertTrue(e3.getValidationErrors().isEmpty());
    }
}
