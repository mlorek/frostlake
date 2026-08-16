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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The || operator refuses an operand statically typed ARRAY or OBJECT, as CONCAT does: "Invalid argument
 * types for function '||': (VARCHAR(1), ARRAY)", positioned at the operator, in either order, with or
 * without a FROM, over an empty table, and in a WHERE. A chain is reported whole, at its last operator. A
 * VARIANT concatenates whatever it holds, and so does a container cast to VARCHAR. Every cell is
 * live-verified.
 */
public class ContainerConcatenationTest extends BaseDatabaseTest {

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
    public void concatenationRefusesAnArrayOrObjectOperand() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P456_DB");
            engine.execute("CREATE OR REPLACE TABLE P456_DB.PUBLIC.T (a ARRAY, o OBJECT, v VARIANT, s VARCHAR)");
            engine.execute("INSERT INTO P456_DB.PUBLIC.T SELECT ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('k', 1), TO_VARIANT(ARRAY_CONSTRUCT(2)), 'x'");
            engine.execute("CREATE OR REPLACE TABLE P456_DB.PUBLIC.E (a ARRAY, s VARCHAR)");
            assertRefused("SELECT 'x' || ARRAY_CONSTRUCT(1.5::FLOAT)",
                "Invalid argument types for function '||': (VARCHAR(1), ARRAY)");
            assertRefused("SELECT 'x' || OBJECT_CONSTRUCT('k', 2.5::FLOAT)",
                "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
            assertRefused("SELECT ARRAY_CONSTRUCT(1) || 'x'",
                "Invalid argument types for function '||': (ARRAY, VARCHAR(1))");
            assertEquals("x[1]",
                rows("SELECT 'x' || TO_VARIANT(ARRAY_CONSTRUCT(1))"));
            assertEquals("x[1]",
                rows("SELECT 'x' || PARSE_JSON('[1]')"));
            assertRefused("SELECT ARRAY_CONSTRUCT(1) || ARRAY_CONSTRUCT(2)",
                "Invalid argument types for function '||': (ARRAY, ARRAY)");
            assertRefused("SELECT 1 || ARRAY_CONSTRUCT(1)",
                "Invalid argument types for function '||': (NUMBER(1,0), ARRAY)");
            assertRefused("SELECT 'x' || [1, 2]",
                "Invalid argument types for function '||': (VARCHAR(1), ARRAY)");
            assertRefused("SELECT 'x' || {'a': 1}",
                "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
            assertRefused("SELECT s || a FROM P456_DB.PUBLIC.T",
                "Invalid argument types for function '||': (VARCHAR(16777216), ARRAY)");
            assertRefused("SELECT a || s FROM P456_DB.PUBLIC.T",
                "Invalid argument types for function '||': (ARRAY, VARCHAR(16777216))");
            assertRefused("SELECT s || o FROM P456_DB.PUBLIC.T",
                "Invalid argument types for function '||': (VARCHAR(16777216), OBJECT)");
            assertEquals("x[2]",
                rows("SELECT s || v FROM P456_DB.PUBLIC.T"));
            assertRefused("SELECT s || a FROM P456_DB.PUBLIC.E",
                "Invalid argument types for function '||': (VARCHAR(16777216), ARRAY)");
            assertRefused("SELECT 'x' || ARRAY_CONSTRUCT(1) || 'y'",
                "Invalid argument types for function '||': (VARCHAR(1), ARRAY, VARCHAR(1))");
            assertRefused("SELECT CONCAT('x', ARRAY_CONSTRUCT(1))",
                "Invalid argument types for function 'CONCAT': (VARCHAR(1), ARRAY)");
            assertRefused("SELECT 1 WHERE 'x' || ARRAY_CONSTRUCT(1) = 'x'",
                "Invalid argument types for function '||': (VARCHAR(1), ARRAY)");
            assertRefused("SELECT NULL || ARRAY_CONSTRUCT(1)",
                "Invalid argument types for function '||': (NULL, ARRAY)");
            assertEquals("x[1]",
                rows("SELECT 'x' || ARRAY_CONSTRUCT(1)::VARCHAR"));
            assertEquals("x[1]",
                rows("SELECT 'x' || a::VARCHAR FROM P456_DB.PUBLIC.T"));
            assertRefused("SELECT 'x' || OBJECT_CONSTRUCT() FROM P456_DB.PUBLIC.E",
                "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
            assertRefused("SELECT 'a'||'b', 'x' || ARRAY_CONSTRUCT(1)",
                "Invalid argument types for function '||': (VARCHAR(1), ARRAY)");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P456_DB");
        }
    }
}
