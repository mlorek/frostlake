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
 * A subquery written without parentheses of its own is a function's argument when it is the call's whole
 * argument list: ABS(SELECT -1), with a FROM, a WHERE, a WITH, a LIMIT or an ORDER BY, under a scalar
 * function, an aggregate and a table function alike. Its select list runs to the closing parenthesis, so
 * a comma after it is a second select item rather than a second argument, and a bare SELECT after another
 * argument is a syntax error. Every cell is live-verified.
 */
public class BareSubqueryArgumentTest extends BaseDatabaseTest {

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
    public void aBareSubqueryIsACallsWholeArgumentList() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P453_DB");
            engine.execute("CREATE OR REPLACE TABLE P453_DB.PUBLIC.T (x INT)");
            engine.execute("INSERT INTO P453_DB.PUBLIC.T VALUES (1), (-2)");
            assertEquals("1",
                rows("SELECT ABS(SELECT -1)"));
            assertEquals("2",
                rows("SELECT ABS(SELECT MIN(x) FROM T)"));
            assertEquals("2",
                rows("SELECT ABS(SELECT x FROM T WHERE x = -2)"));
            assertEquals("3",
                rows("SELECT ABS(WITH c AS (SELECT -3 AS y) SELECT y FROM c)"));
            assertRefused("SELECT COALESCE(SELECT 1, 2)",
                "], expected 2, got 1");
            assertRefused("SELECT COALESCE(SELECT NULL, 2)",
                "], expected 2, got 1");
            assertEquals("1",
                rows("SELECT SUM(SELECT 1)"));
            assertEquals("2",
                rows("SELECT ABS(SELECT -1) + 1"));
            assertEquals("A",
                rows("SELECT UPPER(SELECT 'a')"));
            assertRefused("SELECT ABS(SELECT x FROM T)",
                "Single-row subquery returns more than one row.");
            assertRefused("SELECT CONCAT('a', SELECT 'b')",
                "syntax error line 1 at position");
            assertRefused("SELECT ABS(SELECT -1 UNION ALL SELECT -2)",
                "Single-row subquery returns more than one row.");
            assertEquals("1, null, [0], 0, 1, [1,2] | 1, null, [1], 1, 2, [1,2]",
                rows("SELECT * FROM TABLE(FLATTEN(SELECT PARSE_JSON('[1,2]')))"));
            assertEquals("1",
                rows("SELECT ROW_NUMBER() OVER (ORDER BY (SELECT 1))"));
            assertEquals("1",
                rows("SELECT ABS((SELECT -1))"));
            assertEquals("1",
                rows("SELECT x FROM T WHERE x = ABS(SELECT -1)"));
            assertEquals("1",
                rows("SELECT ABS( SELECT -1 )"));
            assertEquals("[1]",
                rows("SELECT ARRAY_CONSTRUCT(SELECT 1)"));
            assertEquals("1",
                rows("SELECT COUNT(SELECT 1)"));
            assertEquals("1",
                rows("SELECT ABS(SELECT -1 FROM T LIMIT 1)"));
            assertEquals("1",
                rows("SELECT ABS(SELECT -1 ORDER BY 1)"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P453_DB");
        }
    }
}
