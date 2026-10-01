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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A name two output columns carry is ambiguous where a clause reads it: ORDER BY for any two, the other clauses when
 * one of the two is an alias, and a select item when two items BEFORE it carry the name, one an alias. WHERE and a
 * select item read a relation column of the name first. A position and GROUP BY ALL name no column.
 */
public class AmbiguousOutputNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE amb (a INT, n NUMBER(10,2))");
        engine.execute("INSERT INTO amb VALUES (1, 1.5), (2, 2.5)");
        engine.execute("CREATE TABLE amb2 (a INT, a2 INT)");
        engine.execute("INSERT INTO amb2 VALUES (1, 9), (2, 8)");
        engine.execute("CREATE TABLE ambc (a INT, b INT, c INT)");
        engine.execute("INSERT INTO ambc VALUES (1, 10, 100)");
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() > 0 ? ";" : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void everyClauseRefusesTheName() {
        final String a2 = "SQL compilation error:\nambiguous column name 'A2'";
        final String x = "SQL compilation error:\nambiguous column name 'X'";
        assertEquals(a2, refusal("SELECT a AS a2, a2 FROM amb ORDER BY a2"));
        assertEquals(a2, refusal("SELECT a AS a2, a2 FROM amb WHERE a2 > 1"));
        assertEquals(x, refusal("SELECT a AS x, n AS x FROM amb ORDER BY x"));
        assertEquals(x, refusal("SELECT a AS x, n AS x FROM amb WHERE x > 1"));
        assertEquals(x, refusal("SELECT a AS x, n AS x FROM amb GROUP BY x"));
        assertEquals(x, refusal("SELECT a AS x, n AS x FROM amb GROUP BY 1, 2 HAVING x > 1"));
        assertEquals(x, refusal("SELECT a AS x, n AS x FROM amb QUALIFY ROW_NUMBER() OVER (ORDER BY x) = 1"));
        assertEquals(x, refusal("SELECT a AS x, n AS x, a + 1 AS x FROM amb ORDER BY x"));
        assertEquals(x, refusal("SELECT a AS x, n AS x, x + 0 AS y FROM amb"));
        assertEquals(x, refusal("SELECT a AS x, n AS x, x FROM amb ORDER BY 1"));
        assertEquals(a2, refusal("SELECT a AS a2, a2 FROM amb2 ORDER BY a2"));
        assertEquals("SQL compilation error:\nambiguous column name 'A'", refusal("SELECT a, a FROM amb ORDER BY a"));
    }

    @Test
    public void whatNamesNoColumnPasses() {
        assertEquals("1|1;2|2", rows("SELECT a AS a2, a2 FROM amb ORDER BY 1"));
        assertEquals("1|1;2|2", rows("SELECT a AS a2, a2 FROM amb ORDER BY 2"));
        assertEquals("1|1.50;2|2.50", rows("SELECT a AS x, n FROM amb ORDER BY x"));
        assertEquals("1|1.50;2|2.50", rows("SELECT a AS x, n AS x FROM amb GROUP BY ALL ORDER BY 1"));
        assertEquals("2|2", rows("SELECT a, a FROM amb WHERE a > 1"));
        assertEquals("2|2.50", rows("SELECT a AS x, n AS x FROM amb WHERE amb.a > 1"));
        // WHERE reads the relation's own column of the name first.
        assertEquals("1|9", rows("SELECT a AS a2, a2 FROM amb2 WHERE a2 > 8"));
        // A select item reads only the items before it, and a relation's own column first.
        assertEquals("1|2|1;2|3|2", rows("SELECT a AS d2, a + 1, d2 FROM amb ORDER BY 1"));
        assertEquals("1.5|1;2.5|2", rows("SELECT a + 0.5 AS a, a FROM amb ORDER BY 2"));
        assertEquals("1|2;2|3", rows("SELECT a AS x, x + 1 FROM amb ORDER BY 1"));
        assertEquals("10|100|2", rows("SELECT b AS a, c AS a, a + 1 FROM ambc"));
        assertEquals("10|100|1", rows("SELECT b AS a, c AS a, a FROM ambc"));
    }
}
