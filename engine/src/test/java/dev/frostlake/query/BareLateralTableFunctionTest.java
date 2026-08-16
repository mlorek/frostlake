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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A table function may be used directly as a FROM source only under {@code LATERAL}:
 * {@code FROM t, LATERAL SPLIT_TO_TABLE(t.s, ',')}. Live-measured: without the {@code LATERAL}
 * keyword — a bare {@code FROM fn(...)} or {@code FROM t, fn(...)} — Snowflake raises a syntax
 * error; only the {@code TABLE(fn(...))} wrapper works there. Frostlake matches both sides.
 */
public class BareLateralTableFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a,b,c'), (2, 'd'), (3, 'e,f')");
    }

    @Test
    public void bareLateralSplitToTableCorrelatesPerRow() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.id, f.value
            FROM t, LATERAL SPLIT_TO_TABLE(t.s, ',') f
            ORDER BY t.id, f.index
            """);
        assertEquals(6, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(1));
        assertEquals("c", rs.getRows().get(2).getValue(1));
        assertEquals("d", rs.getRows().get(3).getValue(1));
        assertEquals("f", rs.getRows().get(5).getValue(1));
    }

    @Test
    public void bareLateralEqualsTheTableWrappedForm() {
        final String bare = "SELECT t.id, f.value FROM t, LATERAL SPLIT_TO_TABLE(t.s, ',') f"
            + " ORDER BY t.id, f.index";
        final String wrapped = "SELECT t.id, f.value FROM t, TABLE(SPLIT_TO_TABLE(t.s, ',')) f"
            + " ORDER BY t.id, f.index";
        final ResultSet b = engine.executeQuery(bare);
        final ResultSet w = engine.executeQuery(wrapped);
        assertEquals(w.getRowCount(), b.getRowCount());
        for (int i = 0; i < w.getRowCount(); i++) {
            assertEquals(String.valueOf(w.getRows().get(i).getValue(1)),
                String.valueOf(b.getRows().get(i).getValue(1)), "row " + i);
        }
    }

    @Test
    public void lateralGeneratorWithNamedArgument() {
        // GENERATOR takes no left correlation, but LATERAL is still the bare-form keyword live requires.
        final ResultSet rs = engine.executeQuery(
            "SELECT COUNT(*) FROM t, LATERAL GENERATOR(ROWCOUNT => 2)");
        assertEquals(6L, ((Number) rs.getRows().get(0).getValue(0)).longValue()); // 3 rows x 2
    }

    @Test
    public void withoutLateralTheBareFormIsRejected() {
        // FROM t, SPLIT_TO_TABLE(...) — no LATERAL — is a syntax error on Snowflake.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM t, SPLIT_TO_TABLE(t.s, ',') f");
            }
        });
    }

    @Test
    public void standaloneBareTableFunctionIsRejected() {
        // FROM SPLIT_TO_TABLE(...) standalone — no LATERAL, no left source — is also refused; only
        // TABLE(SPLIT_TO_TABLE(...)) is valid in that position.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT value FROM SPLIT_TO_TABLE('x,y,z', ',')");
            }
        });
        // the TABLE()-wrapped spelling works there
        final ResultSet ok = engine.executeQuery(
            "SELECT value FROM TABLE(SPLIT_TO_TABLE('x,y,z', ',')) ORDER BY index");
        assertEquals(3, ok.getRowCount());
        assertEquals("x", ok.getRows().get(0).getValue(0));
    }
}
