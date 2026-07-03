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
}
