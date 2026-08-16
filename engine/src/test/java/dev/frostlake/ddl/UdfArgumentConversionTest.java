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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class UdfArgumentConversionTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void anArgumentIsConvertedToItsParametersType() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P486_DB");
            engine.execute("CREATE OR REPLACE FUNCTION f_number(p NUMBER) RETURNS NUMBER AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_varchar(p VARCHAR) RETURNS VARCHAR AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_boolean(p BOOLEAN) RETURNS BOOLEAN AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_date(p DATE) RETURNS DATE AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_time(p TIME) RETURNS TIME AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_timestamp_ntz(p TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_variant(p VARIANT) RETURNS VARIANT AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_object(p OBJECT) RETURNS OBJECT AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_array(p ARRAY) RETURNS ARRAY AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f_float(p FLOAT) RETURNS FLOAT AS $$ p $$");
            engine.execute("CREATE OR REPLACE FUNCTION f2(a VARCHAR, b NUMBER) RETURNS VARCHAR AS $$ a || b $$");
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_number('abc'))), CHR(10), '~')",
                "Numeric value 'abc' is not recognized");
            assertEquals("12",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_number('12'))), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_number(PARSE_JSON('\"x\"')))), CHR(10), '~')",
                "Failed to cast variant value \"x\" to FIXED");
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_boolean('12'))), CHR(10), '~')",
                "Boolean value '12' is not recognized");
            assertEquals("true",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_boolean('true'))), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_boolean(PARSE_JSON('1')))), CHR(10), '~')",
                "Failed to cast variant value 1 to BOOLEAN");
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_date(PARSE_JSON('1')))), CHR(10), '~')",
                "Failed to cast variant value 1 to DATE");
            assertEquals("\"2024-01-01\"",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_date('2024-01-01'))), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_object(PARSE_JSON('1')))), CHR(10), '~')",
                "Failed to cast variant value 1 to OBJECT");
            assertEquals("{\"a\":1}",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_object(PARSE_JSON('{\"a\":1}')))), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f2('x', 'y'))), CHR(10), '~')",
                "Numeric value 'y' is not recognized");
            assertEquals("\"x7\"",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f2('x', '7'))), CHR(10), '~')"));
            assertEquals("\"x\"",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_variant(PARSE_JSON('\"x\"')))), CHR(10), '~')"));
            assertEquals("[\"x\"]",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_array(PARSE_JSON('\"x\"')))), CHR(10), '~')"));
            assertEquals("[1,2]",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_array(PARSE_JSON('[1,2]')))), CHR(10), '~')"));
            assertEquals("1.500000000000000e+00",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_float('1.5'))), CHR(10), '~')"));
            assertEquals("\"1\"",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(f_varchar(1))), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(f_number(TRUE))), CHR(10), '~')",
                "SQL compilation error: error line 1 at position 34\nInvalid argument types for function 'F_NUMBER': (BOOLEAN)");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P486_DB");
        }
    }

    @Test
    public void theSameConversionACastPerforms() {
        try {
            engine.execute("SELECT 1");
            assertEquals("[\"x\"]",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(CAST(PARSE_JSON('\"x\"') AS ARRAY))), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(TO_JSON(TO_VARIANT(CAST(PARSE_JSON('1') AS OBJECT))), CHR(10), '~')",
                "Failed to cast variant value 1 to OBJECT");
            assertEquals("\"x\"",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(CAST(PARSE_JSON('\"x\"') AS VARIANT))), CHR(10), '~')"));
            assertEquals("[1,2]",
                rows("SELECT REPLACE(TO_JSON(TO_VARIANT(CAST(PARSE_JSON('[1,2]') AS ARRAY))), CHR(10), '~')"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P486B_DB");
        }
    }
}
