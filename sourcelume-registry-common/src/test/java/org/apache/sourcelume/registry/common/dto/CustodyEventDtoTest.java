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
package org.apache.sourcelume.registry.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustodyEventDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void testGettersSettersAndConstructors() {
        CustodyEventDto event = new CustodyEventDto("https://agent/1", "ingested", "2026-10-11T12:00:00Z");
        assertEquals("https://agent/1", event.getAgent());
        assertEquals("ingested", event.getAction());
        assertEquals("2026-10-11T12:00:00Z", event.getStartTime());

        CustodyEventDto empty = new CustodyEventDto();
        empty.setAgent("https://agent/2");
        empty.setAction("transformed");
        empty.setStartTime("2026-10-11T13:00:00Z");

        assertEquals("https://agent/2", empty.getAgent());
        assertEquals("transformed", empty.getAction());
        assertEquals("2026-10-11T13:00:00Z", empty.getStartTime());
    }

    @Test
    void testEqualsHashCodeToString() {
        CustodyEventDto e1 = new CustodyEventDto("https://agent/1", "ingested", "2026-10-11T12:00:00Z");
        CustodyEventDto e2 = new CustodyEventDto("https://agent/1", "ingested", "2026-10-11T12:00:00Z");
        CustodyEventDto e3 = new CustodyEventDto("https://agent/2", "ingested", "2026-10-11T12:00:00Z");

        assertEquals(e1, e1);
        assertEquals(e1, e2);
        assertNotEquals(e1, e3);
        assertNotEquals(e1, null);
        assertNotEquals(e1, "other");

        assertEquals(e1.hashCode(), e2.hashCode());
        assertTrue(e1.toString().contains("ingested"));
    }

    @Test
    void testSerializationAndValidation() throws Exception {
        CustodyEventDto event = new CustodyEventDto("https://agent/1", "ingested", "2026-10-11T12:00:00Z");
        String json = mapper.writeValueAsString(event);
        CustodyEventDto deserialized = mapper.readValue(json, CustodyEventDto.class);

        assertEquals(event, deserialized);
        Set<ConstraintViolation<CustodyEventDto>> violations = validator.validate(deserialized);
        assertTrue(violations.isEmpty());

        CustodyEventDto invalid = new CustodyEventDto("", "", "");
        Set<ConstraintViolation<CustodyEventDto>> invalidViolations = validator.validate(invalid);
        assertFalse(invalidViolations.isEmpty());
        assertEquals(3, invalidViolations.size());
    }
}
