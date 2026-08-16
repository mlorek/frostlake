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
 * Every source a query level reads registers a name, and a second source under a name already taken is
 * refused, {@code duplicate alias 'A'}: the alias when one is written, else a table's own name however it
 * is qualified, the moniker {@code values} for a subquery, {@code VALUES} for a VALUES list and the
 * function's name for a table function. Joins, a parenthesised join, UPDATE's FROM, DELETE's USING and
 * MERGE's target and source all take part; a subquery is its own scope. A missing relation anywhere in the
 * clause is reported first. Every cell is live-verified.
 */
public class DuplicateSourceAliasTest extends BaseDatabaseTest {

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
    public void aSecondSourceUnderATakenNameIsRefused() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P464_DB");
            engine.execute("CREATE OR REPLACE TABLE re (a NUMBER(10,2))");
            engine.execute("CREATE OR REPLACE TABLE ft (d DATE)");
            engine.execute("CREATE OR REPLACE VIEW vv AS SELECT * FROM re");
            assertRefused("SELECT 1 FROM re a, ft a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re a, ft A",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re, re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re JOIN re ON TRUE",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re, P464_DB.PUBLIC.re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re x, (SELECT 1 AS y) x",
                "SQL compilation error:\nduplicate alias 'X'");
            assertRefused("SELECT 1 FROM (SELECT 1 AS x) v, (SELECT 2 AS y) v",
                "SQL compilation error:\nduplicate alias 'V'");
            assertRefused("SELECT 1 FROM (SELECT 1 AS x), (SELECT 2 AS y)",
                "SQL compilation error:\nduplicate alias 'values'");
            assertRefused("SELECT 1 FROM (SELECT 1 AS x) JOIN (SELECT 2 AS y) ON TRUE",
                "SQL compilation error:\nduplicate alias 'values'");
            assertRefused("SELECT 1 FROM re \"a\", ft \"a\"",
                "SQL compilation error:\nduplicate alias 'a'");
            assertEquals("",
                rows("SELECT 1 FROM re a, ft \"a\""));
            assertEquals("",
                rows("SELECT 1 FROM (SELECT 1 AS x), re"));
            assertRefused("WITH c AS (SELECT 1 AS x) SELECT 1 FROM c, c",
                "SQL compilation error:\nduplicate alias 'C'");
            assertRefused("SELECT 1 FROM re, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)), LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(2))",
                "SQL compilation error:\nduplicate alias 'FLATTEN'");
            assertRefused("SELECT 1 FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))), TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(2)))",
                "SQL compilation error:\nduplicate alias 'FLATTEN'");
            assertRefused("SELECT 1 FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) f, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(2))) f",
                "SQL compilation error:\nduplicate alias 'F'");
            assertRefused("SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => 1)), TABLE(GENERATOR(ROWCOUNT => 1))",
                "SQL compilation error:\nduplicate alias 'GENERATOR'");
            assertRefused("SELECT 1 FROM vv, vv",
                "SQL compilation error:\nduplicate alias 'VV'");
            assertEquals("",
                rows("SELECT 1 FROM vv a, vv b"));
            assertRefused("SELECT 1 FROM re a JOIN ft a ON TRUE",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT (SELECT 1 FROM re a, ft a)",
                "SQL compilation error:\nduplicate alias 'A'");
            assertEquals("",
                rows("SELECT 1 FROM re a WHERE EXISTS (SELECT 1 FROM ft a)"));
            assertRefused("SELECT 1 FROM re AS x, ft AS y, re AS x",
                "SQL compilation error:\nduplicate alias 'X'");
            assertRefused("SELECT 1 FROM (VALUES (1)), (VALUES (2))",
                "SQL compilation error:\nduplicate alias 'VALUES'");
            assertRefused("SELECT 1 FROM re JOIN re USING (a)",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re NATURAL JOIN re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("UPDATE re SET a = 1 FROM ft re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("UPDATE re SET a = 1 FROM re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("DELETE FROM re USING re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("UPDATE re x SET a = 1 FROM ft x",
                "SQL compilation error:\nduplicate alias 'X'");
            assertRefused("SELECT 1 FROM re \"RE\", re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertEquals("",
                rows("SELECT 1 FROM re a, ft b, (SELECT 1) c, (SELECT 2)"));
            assertRefused("SELECT 1 FROM re a, ft a, re b, ft b",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT nosuch FROM re a, ft a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re a, nosuch a",
                "SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized.");
            assertRefused("SELECT 1 FROM re a, ft a WHERE nosuch = 1",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re, ft, vv re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re a, LATERAL (SELECT 1 AS z) a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re, (SELECT * FROM re) re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("MERGE INTO re USING ft re ON TRUE WHEN MATCHED THEN DELETE",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re a, re b, ft a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re a INNER JOIN ft b ON TRUE INNER JOIN re b ON TRUE",
                "SQL compilation error:\nduplicate alias 'B'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P464_DB");
        }
    }

    @Test
    public void aMissingRelationOutranksTheDuplicate() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P464B_DB");
            engine.execute("CREATE OR REPLACE TABLE re (a NUMBER(10,2))");
            engine.execute("CREATE OR REPLACE TABLE ft (d DATE)");
            assertRefused("SELECT 1 FROM re a, ft a, nosuch",
                "SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized.");
            assertRefused("SELECT 1 FROM nosuch a, re a",
                "SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized.");
            assertRefused("SELECT 1 FROM re a, ft a WHERE EXISTS (SELECT 1 FROM nosuch)",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re PIVOT(SUM(a) FOR a IN (1)) p, ft p",
                "SQL compilation error:\nduplicate alias 'P'");
            assertRefused("SELECT 1 FROM re, IDENTIFIER('re')",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM TABLE(SPLIT_TO_TABLE('a,b', ',')), TABLE(SPLIT_TO_TABLE('c', ','))",
                "SQL compilation error:\nduplicate alias 'SPLIT_TO_TABLE'");
            assertRefused("SELECT 1 FROM re, LATERAL (SELECT 1 AS z), LATERAL (SELECT 2 AS w)",
                "SQL compilation error:\nduplicate alias 'values'");
            assertRefused("SELECT 1 FROM (re a JOIN ft b ON TRUE), re a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re a JOIN ft b ON TRUE, ft b, re a",
                "SQL compilation error:\nduplicate alias 'B'");
            assertRefused("SELECT 1 FROM re x JOIN ft y ON TRUE, ft x, re y",
                "SQL compilation error:\nduplicate alias 'X'");
            assertRefused("SELECT 1 FROM re, LATERAL SPLIT_TO_TABLE('a', ','), LATERAL SPLIT_TO_TABLE('b', ',')",
                "SQL compilation error:\nduplicate alias 'SPLIT_TO_TABLE'");
            assertRefused("SELECT 1 FROM re, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re a, ft a, re a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re AS a (x), ft AS a (y)",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re \"A\", ft a",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("WITH re AS (SELECT 1 AS q) SELECT 1 FROM re, re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re, (SELECT 1 AS x) AS re",
                "SQL compilation error:\nduplicate alias 'RE'");
            assertRefused("SELECT 1 FROM re a, nosuch b, ft a",
                "SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized.");
            assertRefused("SELECT 1 FROM re a, ft a, nosuch.x.y",
                "SQL compilation error:\nDatabase 'NOSUCH' does not exist or not authorized.");
            assertRefused("SELECT 1 FROM ft a, re a SAMPLE (10)",
                "SQL compilation error:\nduplicate alias 'A'");
            assertRefused("SELECT 1 FROM re a, ft a UNION ALL SELECT 1 FROM nosuch",
                "SQL compilation error:\nduplicate alias 'A'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P464B_DB");
        }
    }
}
