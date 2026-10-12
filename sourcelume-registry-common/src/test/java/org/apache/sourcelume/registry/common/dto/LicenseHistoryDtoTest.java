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

class LicenseHistoryDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void testGettersSettersAndConstructors() {
        LicenseHistoryDto history =
                new LicenseHistoryDto("https://spdx.org/licenses/Apache-2.0.html", "2026-10-11T12:00:00Z");
        assertEquals("https://spdx.org/licenses/Apache-2.0.html", history.getIri());
        assertEquals("2026-10-11T12:00:00Z", history.getEffectiveDate());

        LicenseHistoryDto empty = new LicenseHistoryDto();
        empty.setIri("https://spdx.org/licenses/MIT.html");
        empty.setEffectiveDate("2026-10-11T13:00:00Z");

        assertEquals("https://spdx.org/licenses/MIT.html", empty.getIri());
        assertEquals("2026-10-11T13:00:00Z", empty.getEffectiveDate());
    }

    @Test
    void testEqualsHashCodeToString() {
        LicenseHistoryDto h1 =
                new LicenseHistoryDto("https://spdx.org/licenses/Apache-2.0.html", "2026-10-11T12:00:00Z");
        LicenseHistoryDto h2 =
                new LicenseHistoryDto("https://spdx.org/licenses/Apache-2.0.html", "2026-10-11T12:00:00Z");
        LicenseHistoryDto h3 = new LicenseHistoryDto("https://spdx.org/licenses/MIT.html", "2026-10-11T12:00:00Z");

        assertEquals(h1, h1);
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
        assertNotEquals(h1, null);
        assertNotEquals(h1, "other");

        assertEquals(h1.hashCode(), h2.hashCode());
        assertTrue(h1.toString().contains("Apache-2.0"));
    }

    @Test
    void testSerializationAndValidation() throws Exception {
        LicenseHistoryDto history =
                new LicenseHistoryDto("https://spdx.org/licenses/Apache-2.0.html", "2026-10-11T12:00:00Z");
        String json = mapper.writeValueAsString(history);
        LicenseHistoryDto deserialized = mapper.readValue(json, LicenseHistoryDto.class);

        assertEquals(history, deserialized);
        Set<ConstraintViolation<LicenseHistoryDto>> violations = validator.validate(deserialized);
        assertTrue(violations.isEmpty());

        LicenseHistoryDto invalid = new LicenseHistoryDto("", "");
        Set<ConstraintViolation<LicenseHistoryDto>> invalidViolations = validator.validate(invalid);
        assertFalse(invalidViolations.isEmpty());
        assertEquals(2, invalidViolations.size());
    }
}
