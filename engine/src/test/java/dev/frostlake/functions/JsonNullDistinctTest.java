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
 * A JSON null is distinct from a SQL NULL (Snowflake): IS_NULL_VALUE is TRUE for a JSON null but NULL for a
 * SQL NULL, and a JSON null is not caught by the SQL IS NULL operator. A missing path element is a SQL NULL.
 */
public class JsonNullDistinctTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void isNullValueSeparatesJsonNullFromSqlNull() {
        assertNull(scalar("SELECT IS_NULL_VALUE(NULL)"));                         // SQL NULL -> NULL
        assertEquals(true, scalar("SELECT IS_NULL_VALUE(PARSE_JSON('null'))"));   // JSON null -> TRUE
        assertEquals(false, scalar("SELECT IS_NULL_VALUE(PARSE_JSON('1'))"));
    }

    @Test
    public void jsonNullIsNotSqlNull() {
        // A JSON null is distinct from SQL NULL, so the SQL IS NULL operator does not catch it.
        assertEquals(false, scalar("SELECT PARSE_JSON('null') IS NULL"));
    }

    @Test
    public void pathToAPresentJsonNullIsAJsonNull() {
        assertEquals(true, scalar("SELECT IS_NULL_VALUE(PARSE_JSON('{\"a\":null}'):a)"));
    }

    @Test
    public void pathToAnAbsentElementIsASqlNull() {
        // A missing JSON value converts to SQL NULL, for which IS_NULL_VALUE returns NULL.
        assertNull(scalar("SELECT IS_NULL_VALUE(PARSE_JSON('{\"a\":1}'):missing)"));
    }

    // ── casting a JSON null out of a VARIANT yields SQL NULL ─────────────────
    // The uncast path stays a JSON null (above); CASTING it to an ordinary type is what converts it, and the
    // engine carries a JSON null as the four-character text `null`, so without this every consumer saw that
    // text — TO_TIMESTAMP / TO_DATE / TO_TIME / DATEADD all failed with "Cannot parse date/time: null".

    @Test
    public void castingAJsonNullPathToAnOrdinaryTypeGivesSqlNull() {
        assertNull(scalar("SELECT PARSE_JSON('{\"a\":null}'):a::VARCHAR"));
        assertNull(scalar("SELECT PARSE_JSON('{\"a\":null}'):a::NUMBER"));
        assertNull(scalar("SELECT PARSE_JSON('{\"a\":null}'):a::NUMBER(10,2)"));
        assertNull(scalar("SELECT PARSE_JSON('{\"a\":null}'):a::BOOLEAN"));
        assertNull(scalar("SELECT PARSE_JSON('{\"a\":null}'):a::DATE"));
        assertNull(scalar("SELECT TRY_CAST(PARSE_JSON('{\"a\":null}'):a AS DATE)"));
    }

    @Test
    public void castingToASemiStructuredTypeKeepsTheJsonNull() {
        assertEquals(true, scalar("SELECT IS_NULL_VALUE(PARSE_JSON('{\"a\":null}'):a::VARIANT)"));
    }

    @Test
    public void aNestedOrIndexedJsonNullAlsoCastsToSqlNull() {
        assertNull(scalar("SELECT PARSE_JSON('{\"n\":{\"b\":null}}'):n:b::VARCHAR"));
        assertNull(scalar("SELECT PARSE_JSON('{\"arr\":[null,1]}'):arr[0]::VARCHAR"));
    }

    @Test
    public void temporalFunctionsOverAJsonNullPathReturnNull() {
        assertNull(scalar("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('{\"d\":null}'):d::VARCHAR)"));
        assertNull(scalar("SELECT TO_DATE(PARSE_JSON('{\"d\":null}'):d::VARCHAR)"));
        assertNull(scalar("SELECT TO_TIME(PARSE_JSON('{\"d\":null}'):d::VARCHAR)"));
        assertNull(scalar("SELECT DATEADD(DAY, 1, PARSE_JSON('{\"d\":null}'):d::VARCHAR)"));
    }

    @Test
    public void arealValueThroughTheSamePathIsUntouched() {
        assertEquals("x", scalar("SELECT PARSE_JSON('{\"a\":\"x\"}'):a::VARCHAR"));
        assertEquals("2023-10-11T07:09:15", String.valueOf(
            scalar("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('{\"d\":\"2023-10-11 07:09:15\"}'):d::VARCHAR)")));
    }

    @Test
    public void aVarcharHoldingTheTextNullIsNotAffected() {
        // Only a VARIANT PATH is recognised as a JSON null; an ordinary string keeps its four characters, and
        // feeding it to a date function is still an error rather than NULL.
        assertEquals("null", scalar("SELECT 'null'::VARCHAR"));
        assertEquals(false, scalar("SELECT 'null' IS NULL"));
    }
}
