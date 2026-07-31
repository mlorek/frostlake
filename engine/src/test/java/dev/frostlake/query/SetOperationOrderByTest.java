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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A set operation's ORDER BY resolves against the combined OUTPUT only — measured cell by cell on
 * a real account: an output column name (the first branch's alias, or the produced name of an
 * un-aliased item), a positional ordinal, or an expression over those output names all work; a
 * second branch's column name, a branch table's qualifier, an aliased item's pre-alias expression
 * text, a first-branch column that was NOT selected, and any unknown name are each rejected as
 * "invalid identifier '…'". There is no fall-through to any branch's FROM scope.
 */
public class SetOperationOrderByTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE t1 (a INTEGER, c INTEGER)");
        engine.execute("CREATE TABLE t2 (b INTEGER, c INTEGER)");
        engine.execute("INSERT INTO t1 VALUES (3, 30), (1, 10)");
        engine.execute("INSERT INTO t2 VALUES (2, 20)");
    }

    private void assertInvalidIdentifier(final String sql, final String name) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("invalid identifier '" + name + "'"),
            "expected invalid identifier '" + name + "', got: " + error.getMessage());
    }

    /** All first-column values in row order, joined with {@code |}. */
    private String columnValues(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder joined = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(row.getValue(0));
        }
        return joined.toString();
    }

    @Test
    public void outputNamesOrdinalsAndExpressionsOverThemResolve() {
        assertEquals("1|2|3", columnValues(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY a"));
        assertEquals("3|2|1", columnValues(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY 1 DESC"));
        assertEquals("1|2|3", columnValues(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY a + 1"));
        assertEquals("1|2|3", columnValues(
            "SELECT a AS x FROM t1 UNION ALL SELECT b FROM t2 ORDER BY x"));
    }

    @Test
    public void everythingOutsideTheOutputIsAnInvalidIdentifier() {
        assertInvalidIdentifier(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY b", "B");
        assertInvalidIdentifier(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY t1.a", "T1.A");
        assertInvalidIdentifier(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY nosuch", "NOSUCH");
        // The first branch's column NOT selected — not an output name either.
        assertInvalidIdentifier(
            "SELECT a FROM t1 UNION ALL SELECT b FROM t2 ORDER BY c", "C");
        // An alias REPLACES the item's expression text as its output name.
        assertInvalidIdentifier(
            "SELECT a AS x FROM t1 UNION ALL SELECT b FROM t2 ORDER BY a", "A");
    }
}
