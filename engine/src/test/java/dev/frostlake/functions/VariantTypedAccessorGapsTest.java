/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The previously untested corners of the AS_* / IS_* variant-typed accessor families: BINARY,
 * BOOLEAN, DECIMAL, DOUBLE, TIME and TIMESTAMP_NTZ. Each extractor returns the value only when the
 * variant holds exactly that type — anything else is SQL NULL, never an error — and each predicate
 * answers the same question as a boolean.
 */
public class VariantTypedAccessorGapsTest extends BaseDatabaseTest {

    private Object one(final String sql) {
        return engine.executeQuery("SELECT " + sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        return String.valueOf(one(sql));
    }

    @Test
    public void asBinaryExtractsOnlyBinaryVariants() {
        assertEquals("true",
            text("AS_BINARY(TO_VARIANT(TO_BINARY('CAFE', 'HEX'))) = TO_BINARY('CAFE', 'HEX')"));
        assertNull(one("AS_BINARY(TO_VARIANT('CAFE'))")); // a string variant is not binary
        assertNull(one("AS_BINARY(NULL)"));
    }

    @Test
    public void asBooleanExtractsOnlyBooleanVariants() {
        assertEquals("true", text("AS_BOOLEAN(TO_VARIANT(TRUE))"));
        assertEquals("false", text("AS_BOOLEAN(TO_VARIANT(FALSE))"));
        assertNull(one("AS_BOOLEAN(TO_VARIANT(1))"));   // a number variant is not a boolean
        assertNull(one("AS_BOOLEAN(TO_VARIANT('true'))"));
    }

    @Test
    public void asDecimalAndAsDoubleExtractNumericVariants() {
        assertEquals("12.34", text("TO_VARCHAR(AS_DECIMAL(TO_VARIANT(12.34::NUMBER(10,2)), 10, 2))"));
        assertEquals("7", text("TO_VARCHAR(AS_DECIMAL(TO_VARIANT(7)))"));
        assertNull(one("AS_DECIMAL(TO_VARIANT('12.34'))")); // strings do not extract
        assertEquals("2.5", text("TO_VARCHAR(AS_DOUBLE(TO_VARIANT(2.5::DOUBLE)))"));
        assertNull(one("AS_DOUBLE(TO_VARIANT('2.5'))"));
    }

    @Test
    public void asTimeAndAsTimestampExtractTemporalVariants() {
        assertEquals("true",
            text("AS_TIME(TO_VARIANT('12:30:45'::TIME)) = '12:30:45'::TIME"));
        assertNull(one("AS_TIME(TO_VARIANT('12:30:45'))")); // still a string inside the variant
        assertEquals("true",
            text("AS_TIMESTAMP_NTZ(TO_VARIANT('2026-01-02 03:04:05'::TIMESTAMP_NTZ))"
                + " = '2026-01-02 03:04:05'::TIMESTAMP_NTZ"));
        assertNull(one("AS_TIMESTAMP_NTZ(TO_VARIANT('2026-01-02'::DATE))")); // a DATE is not an NTZ
    }

    @Test
    public void predicatesMirrorTheExtractors() {
        assertEquals("true", text("IS_BINARY(TO_VARIANT(TO_BINARY('CAFE', 'HEX')))"));
        assertEquals("false", text("IS_BINARY(TO_VARIANT('CAFE'))"));
        assertEquals("true", text("IS_TIME(TO_VARIANT('12:30:45'::TIME))"));
        assertEquals("false", text("IS_TIME(TO_VARIANT('2026-01-02'::DATE))"));
        assertEquals("true", text("IS_TIMESTAMP_NTZ(TO_VARIANT('2026-01-02 03:04:05'::TIMESTAMP_NTZ))"));
        assertEquals("false", text("IS_TIMESTAMP_NTZ(TO_VARIANT('12:30:45'::TIME))"));
        assertNull(one("IS_TIME(NULL)"));
    }
}
