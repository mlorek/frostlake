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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake's select-list rule under the super-group forms, all live-verified: GROUPING SETS /
 * ROLLUP / CUBE apply the SAME check as a plain GROUP BY, with the union of every grouping set as
 * the grouped keys. A column in ANY set is legal (its rows print NULL for the sets that aggregate
 * it away); a column in NONE is refused with the plain form's exact sentence —
 * {@code '<TABLE>.<COLUMN>' in select clause is neither an aggregate nor in the group by clause.} —
 * positioned at the offending REFERENCE (its own line and 0-based column), not at the select-list
 * start. The rule reaches through a {@code GROUPING()} argument too, and a key written as a select
 * alias or a 1-based ordinal resolves to the aliased/numbered select expression, for validation and
 * for execution alike.
 */
public class GroupingSetsSelectListValidationTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, amount NUMBER)");
        engine.execute("INSERT INTO sales VALUES ('east','a',10),('west','b',20)");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    private void expectUngroupedRegion(final String sql, final int line, final int position,
                                       final String spelledAs) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q(sql);
            }
        });
        assertEquals("SQL compilation error: error line " + line + " at position " + position
            + "\n'" + spelledAs + "' in select clause is neither an aggregate nor in the group by clause.",
            e.getMessage());
    }

    // ── the refusals ─────────────────────────────────────────────────────────

    @Test
    public void aColumnOutsideEveryGroupingSetIsRefused() {
        expectUngroupedRegion(
            "SELECT region, product, SUM(amount) FROM sales GROUP BY GROUPING SETS ((product))",
            1, 7, "SALES.REGION");
    }

    @Test
    public void thePositionIsTheOffendingReferencesOwn() {
        // The offender is the SECOND item, so the position moves with it.
        expectUngroupedRegion(
            "SELECT product, region, SUM(amount) FROM sales GROUP BY GROUPING SETS ((product))",
            1, 16, "SALES.REGION");
    }

    @Test
    public void rollupAndCubeApplyTheSameRule() {
        expectUngroupedRegion(
            "SELECT region, product, SUM(amount) FROM sales GROUP BY ROLLUP (product)",
            1, 7, "SALES.REGION");
        expectUngroupedRegion(
            "SELECT region, product, SUM(amount) FROM sales GROUP BY CUBE (product)",
            1, 7, "SALES.REGION");
    }

    @Test
    public void aGroupingFunctionArgumentIsCheckedToo() {
        // GROUPING() does not launder an ungrouped column; the position is the ARGUMENT's own.
        expectUngroupedRegion(
            "SELECT GROUPING(region), SUM(amount) FROM sales GROUP BY GROUPING SETS ((product))",
            1, 16, "SALES.REGION");
    }

    @Test
    public void aFromAliasSpellsTheRefusalQualifier() {
        expectUngroupedRegion(
            "SELECT s.region, product, SUM(amount) FROM sales s GROUP BY GROUPING SETS ((product))",
            1, 7, "S.REGION");
    }

    @Test
    public void theRefusalCarriesTheReferencesOwnLine() {
        expectUngroupedRegion(
            "SELECT product,\n  region, SUM(amount) FROM sales GROUP BY GROUPING SETS ((product))",
            2, 2, "SALES.REGION");
    }

    // ── the legal shapes ─────────────────────────────────────────────────────

    @Test
    public void aColumnInAnyOfTheSetsIsLegal() {
        final ResultSet rs = q("SELECT region, product, SUM(amount) FROM sales "
            + "GROUP BY GROUPING SETS ((region),(product)) ORDER BY 1,2");
        assertEquals(4, rs.getRowCount());
        assertEquals("east", rs.getRows().get(0).getValue(0));
        assertNull(rs.getRows().get(0).getValue(1));
        assertNull(rs.getRows().get(2).getValue(0));
        assertEquals("a", rs.getRows().get(2).getValue(1));
    }

    @Test
    public void aGroupingCallOverAGroupedColumnIsLegal() {
        final ResultSet rs = q("SELECT region, GROUPING(region), SUM(amount) FROM sales "
            + "GROUP BY GROUPING SETS ((region),()) ORDER BY 2,1");
        assertEquals(3, rs.getRowCount());
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(0L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(2).getValue(1)).longValue());
        assertEquals(30L, ((Number) rs.getRows().get(2).getValue(2)).longValue());
    }

    @Test
    public void anExpressionOverAGroupedColumnIsLegal() {
        final ResultSet rs = q("SELECT region || 'x', SUM(amount) FROM sales "
            + "GROUP BY GROUPING SETS ((region)) ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("eastx", rs.getRows().get(0).getValue(0));
        assertEquals("westx", rs.getRows().get(1).getValue(0));
    }

    // ── alias and ordinal keys resolve, for validation AND execution ─────────

    @Test
    public void aSelectAliasIsALegalGroupingSetKey() {
        final ResultSet rs = q("SELECT region AS r, SUM(amount) FROM sales "
            + "GROUP BY GROUPING SETS ((r)) ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("east", rs.getRows().get(0).getValue(0));
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals("west", rs.getRows().get(1).getValue(0));
        assertEquals(20L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void aSelectOrdinalIsALegalGroupingSetKey() {
        final ResultSet rs = q("SELECT region, SUM(amount) FROM sales "
            + "GROUP BY GROUPING SETS ((1)) ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("east", rs.getRows().get(0).getValue(0));
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals("west", rs.getRows().get(1).getValue(0));
        assertEquals(20L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void anAllEmptyUnionStillValidates() {
        // GROUPING SETS (()) groups by nothing at all, so a bare column has nowhere to hide.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT region, SUM(amount) FROM sales GROUP BY GROUPING SETS (())");
            }
        });
        assertTrue(e.getMessage().contains(
            "'SALES.REGION' in select clause is neither an aggregate nor in the group by clause."),
            "unexpected message: " + e.getMessage());
    }
}
