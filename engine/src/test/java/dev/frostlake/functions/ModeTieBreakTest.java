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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODE over ties. Live returns "one of" the equally frequent values — whichever its hash table lists
 * first, which shifts with the family, the plan and the insertion order — so a tie is pinned two ways:
 * as membership in the tied set (true on any account) and, for the shapes where the account was
 * measured to hand back the FIRST value seen, as that value, which is the engine's own rule.
 */
public class ModeTieBreakTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE m1 (n NUMBER(10,2), s VARCHAR, v VARIANT, d DATE, b BOOLEAN, f FLOAT, i INTEGER)");
        engine.execute("INSERT INTO m1 SELECT 1.50, 'a', TO_VARIANT(2), '2020-01-01', TRUE, 1.5, 1 "
            + "UNION ALL SELECT 2.50, 'b', TO_VARIANT(4), '2020-01-02', FALSE, 2.5, 2");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    private void assertOneOf(final String sql, final String... tied) {
        final String picked = text(sql);
        assertTrue(Arrays.asList(tied).contains(picked), sql + " -> " + picked);
    }

    @Test
    public void theMostFrequentValueWinsOutright() {
        assertEquals("2", text("SELECT MODE(i) FROM (SELECT 1 i UNION ALL SELECT 2 UNION ALL SELECT 2)"));
        assertEquals("b", text("SELECT MODE(s) FROM (SELECT 'b' s UNION ALL SELECT 'a' UNION ALL SELECT 'b')"));
        assertEquals("1", text("SELECT MODE(i) FROM (SELECT NULL i UNION ALL SELECT NULL UNION ALL SELECT 1)"));
        assertNull(row("SELECT MODE(i) FROM (SELECT NULL i UNION ALL SELECT NULL)").getValue(0));
        assertNull(row("SELECT MODE(i) FROM m1 WHERE i > 100").getValue(0));
        // Equal values share a count however the arms spell them (compared numerically: the folded
        // set-operation scale is its own subject).
        assertEquals(0, new BigDecimal(text("SELECT MODE(x) FROM (SELECT 1.50 x UNION ALL SELECT 1.5 UNION ALL SELECT 2.50)"))
            .compareTo(new BigDecimal("1.5")));
    }

    /**
     * The engine's own rule — the first value seen wins a tie — is pinned embedded only: the account
     * answers the same statements from its hash table's order, which even the spelling of a literal
     * shifts ({@code (1.5, 2.5)} is 2.5 in either order there, while {@code (1.50, 2.50)} is 1.5 and its
     * reverse 2.5), so on the account a tie is only ever one of the tied values.
     */
    @Test
    public void aTieGoesToTheValueSeenFirst() {
        assertOneOf("SELECT MODE(n) FROM (SELECT 1.5 n UNION ALL SELECT 2.5)", "1.5", "2.5");
        assertOneOf("SELECT MODE(s) FROM (SELECT 'x' s UNION ALL SELECT 'y')", "x", "y");
        assertOneOf("SELECT MODE(i) FROM (SELECT 1 i UNION ALL SELECT 2 UNION ALL SELECT 3)", "1", "2", "3");
        assertOneOf("SELECT MODE(i) OVER () FROM (SELECT 2 i UNION ALL SELECT 1) LIMIT 1", "1", "2");
        if (isLiveSnowflake()) {
            return;
        }
        assertEquals("1.5", text("SELECT MODE(n) FROM (SELECT 1.5 n UNION ALL SELECT 2.5)"));
        assertEquals("2.5", text("SELECT MODE(n) FROM (SELECT 2.5 n UNION ALL SELECT 1.5)"));
        assertEquals("x", text("SELECT MODE(s) FROM (SELECT 'x' s UNION ALL SELECT 'y')"));
        assertEquals("y", text("SELECT MODE(s) FROM (SELECT 'y' s UNION ALL SELECT 'x')"));
        assertEquals("10", text("SELECT MODE(i) FROM (SELECT 10 i UNION ALL SELECT 3)"));
        assertEquals("3", text("SELECT MODE(i) FROM (SELECT 3 i UNION ALL SELECT 10)"));
        assertEquals("1", text("SELECT MODE(i) FROM (SELECT 1 i UNION ALL SELECT 2 UNION ALL SELECT 3)"));
        assertEquals("3", text("SELECT MODE(i) FROM (SELECT 3 i UNION ALL SELECT 2 UNION ALL SELECT 1)"));
        assertEquals("2", text("SELECT MODE(i) OVER () FROM (SELECT 2 i UNION ALL SELECT 1) LIMIT 1"));
        assertEquals("1", text("SELECT MODE(i) FROM (SELECT 2 i UNION ALL SELECT 1 UNION ALL SELECT 3 UNION ALL SELECT 3 UNION ALL SELECT 1)"));
        final Row table = row("SELECT MODE(n), MODE(s), MODE(v), MODE(d), MODE(b), MODE(f), MODE(i) FROM m1");
        assertEquals("1.50", String.valueOf(table.getValue(0)));
        assertEquals("a", String.valueOf(table.getValue(1)));
        assertEquals("2", String.valueOf(table.getValue(2)));
        assertEquals("2020-01-01", String.valueOf(table.getValue(3)));
        assertEquals("true", String.valueOf(table.getValue(4)).toLowerCase());
        assertEquals("1.5", String.valueOf(table.getValue(5)));
        assertEquals("1", String.valueOf(table.getValue(6)));
    }

    @Test
    public void everyOtherTieIsOneOfTheTiedValues() {
        assertOneOf("SELECT MODE(i) FROM (SELECT 2 i UNION ALL SELECT 4)", "2", "4");
        assertOneOf("SELECT MODE(i) FROM (SELECT 4 i UNION ALL SELECT 2)", "2", "4");
        assertOneOf("SELECT MODE(v) FROM (SELECT TO_VARIANT(2) v UNION ALL SELECT TO_VARIANT(4))", "2", "4");
        assertOneOf("SELECT MODE(v) FROM (SELECT TO_VARIANT(4) v UNION ALL SELECT TO_VARIANT(2))", "2", "4");
        assertOneOf("SELECT MODE(d) FROM (SELECT '2020-01-02'::DATE d UNION ALL SELECT '2020-01-01'::DATE)",
            "2020-01-01", "2020-01-02");
        assertOneOf("SELECT MODE(f) FROM (SELECT 2.5::FLOAT f UNION ALL SELECT 1.5::FLOAT)", "1.5", "2.5");
        assertOneOf("SELECT MODE(i) FROM (SELECT 7 i UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11)",
            "7", "8", "9", "10", "11");
        assertOneOf("SELECT MODE(i) FROM (SELECT 1 i UNION ALL SELECT 2 UNION ALL SELECT 2 UNION ALL SELECT 1)", "1", "2");
        assertOneOf("SELECT MODE(i) FROM (SELECT 2 i UNION ALL SELECT 1 UNION ALL SELECT 3 UNION ALL SELECT 3 UNION ALL SELECT 1)",
            "1", "3");
        assertOneOf("SELECT MODE(s) FROM (SELECT 'aaa' s UNION ALL SELECT 'b')", "aaa", "b");
        final Row table = row("SELECT MODE(v), MODE(d), MODE(f) FROM m1");
        assertTrue(Arrays.asList("2", "4").contains(String.valueOf(table.getValue(0))), String.valueOf(table.getValue(0)));
        assertTrue(Arrays.asList("2020-01-01", "2020-01-02").contains(String.valueOf(table.getValue(1))),
            String.valueOf(table.getValue(1)));
        assertTrue(Arrays.asList("1.5", "2.5").contains(String.valueOf(table.getValue(2))), String.valueOf(table.getValue(2)));
        final List<Row> grouped = engine.executeQuery("SELECT MODE(i), MODE(s) FROM m1 GROUP BY b ORDER BY b").getRows();
        assertEquals(2, grouped.size());
        assertEquals("2", String.valueOf(grouped.get(0).getValue(0)));
        assertEquals("b", String.valueOf(grouped.get(0).getValue(1)));
        assertEquals("1", String.valueOf(grouped.get(1).getValue(0)));
        assertEquals("a", String.valueOf(grouped.get(1).getValue(1)));
    }
}
