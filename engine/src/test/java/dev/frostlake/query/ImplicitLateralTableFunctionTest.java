/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/plans/LICENSE-2.0
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table function in a comma-separated FROM list is implicitly LATERAL in Snowflake — it may reference
 * columns of the tables written before it, and the LATERAL keyword is optional. The ubiquitous
 * {@code FROM t, TABLE(FLATTEN(input => t.col)) f} form was executed once with no lateral context, so the
 * correlated reference failed with "Column not found: T.COL"; only the explicit {@code LATERAL FLATTEN(…)}
 * spelling worked.
 */
public class ImplicitLateralTableFunctionTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (id INTEGER, src VARIANT, arr ARRAY)");
        engine.execute("INSERT INTO t SELECT 1, PARSE_JSON('{\"items\":[{\"n\":\"a\"},{\"n\":\"b\"}]}'), "
            + "ARRAY_CONSTRUCT('x', 'y')");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    // ── a LATERAL may reference a SELECT-list alias of the same query ────────

    @Test
    public void aLateralFlattenResolvesASelectListAlias() {
        // Snowflake allows `SELECT ARRAY_CONSTRUCT(…) ex, … FROM t, TABLE(FLATTEN(ex))`. The alias is not a
        // column of any FROM table, so the lateral used to fail with
        // "Unable to evaluate expression 'ex' in LATERAL context: Column not found: OBS".
        engine.execute("CREATE TABLE stage (id INTEGER, src VARIANT)");
        engine.execute("INSERT INTO stage SELECT 1, PARSE_JSON('{\"a\":{\"n\":\"first\"},\"b\":{\"n\":\"second\"}}')");
        engine.execute("INSERT INTO stage SELECT 2, PARSE_JSON('{\"a\":{\"n\":\"only\"}}')");

        final ResultSet rs = q("SELECT id, ARRAY_CONSTRUCT_COMPACT(src:a, src:b) extras, "
            + "ex.value:n::VARCHAR AS extra_name "
            + "FROM stage s, TABLE(FLATTEN(extras, OUTER => TRUE)) AS ex ORDER BY id, extra_name");
        assertEquals(3, rs.getRowCount());
        assertEquals("first", rs.getRows().get(0).getValue(2));
        assertEquals("second", rs.getRows().get(1).getValue(2));
        assertEquals("only", rs.getRows().get(2).getValue(2));
    }

    @Test
    public void theExplicitLateralKeywordAlsoSeesASelectListAlias() {
        engine.execute("CREATE TABLE stage2 (id INTEGER, src VARIANT)");
        engine.execute("INSERT INTO stage2 SELECT 1, PARSE_JSON('{\"a\":{\"n\":\"x\"},\"b\":{\"n\":\"y\"}}')");
        final ResultSet rs = q("SELECT id, ARRAY_CONSTRUCT_COMPACT(src:a, src:b) ex, f.value:n::VARCHAR AS nm "
            + "FROM stage2 s, LATERAL FLATTEN(input => ex) f ORDER BY nm");
        assertEquals(2, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(2));
        assertEquals("y", rs.getRows().get(1).getValue(2));
    }

    @Test
    public void aRealColumnWinsOverASelectAliasOfTheSameName() {
        engine.execute("CREATE TABLE stage3 (id INTEGER, src VARIANT)");
        engine.execute("INSERT INTO stage3 SELECT 1, PARSE_JSON('[{\"n\":\"fromColumn\"}]')");
        // SRC is both a real column and this list's alias; the lateral must flatten the COLUMN.
        final ResultSet rs = q("SELECT id, ARRAY_CONSTRUCT('fromAlias') AS src, f.value:n::VARCHAR AS v "
            + "FROM stage3 s, TABLE(FLATTEN(input => src)) f");
        assertEquals(1, rs.getRowCount());
        assertEquals("fromColumn", rs.getRows().get(0).getValue(2));
    }

    @Test
    public void aLateralAfterAnInnerJoinReadsTheJoinedTablesColumns() {
        // The per-row lateral context accumulated column offsets in the alias map's HASH order rather than
        // the combined row's layout, so after a JOIN the lateral read the wrong slots — FLATTEN over the
        // joined table's column got NULL and expanded to nothing, silently emptying the whole query.
        engine.execute("CREATE TABLE orders (oid INTEGER, aid INTEGER)");
        engine.execute("INSERT INTO orders VALUES (10, 1)");
        engine.execute("CREATE TABLE vendors2 (aid INTEGER, plans VARIANT)");
        engine.execute("INSERT INTO vendors2 SELECT 1, PARSE_JSON('{\"GOLD\":{\"ok\":1},\"SILVER\":{\"ok\":0}}')");
        final ResultSet rs = q("SELECT f.oid, l.key FROM orders f INNER JOIN vendors2 a ON a.aid = f.aid, "
            + "LATERAL FLATTEN(input => a.plans) l WHERE l.key = 'GOLD'");
        assertEquals(1, rs.getRowCount());
        assertEquals("GOLD", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void tableFlattenResolvesAliasQualifiedInput() {
        // The loader shape: FROM <table> AS t, TABLE(FLATTEN(INPUT => t.<variant path>)) AS f
        final ResultSet rs = q("SELECT f.value:n::VARCHAR FROM t, "
            + "TABLE(FLATTEN(INPUT => t.src:items)) f ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(0).toString());
        assertEquals("b", rs.getRows().get(1).getValue(0).toString());
    }

    @Test
    public void tableFlattenResolvesUnqualifiedInput() {
        assertEquals(2, q("SELECT f.value:n::VARCHAR FROM t, TABLE(FLATTEN(INPUT => src:items)) f").getRowCount());
    }

    @Test
    public void tableFlattenResolvesThroughATableAlias() {
        assertEquals(2, q("SELECT f.value:n::VARCHAR FROM t x, TABLE(FLATTEN(INPUT => x.src:items)) f").getRowCount());
    }

    @Test
    public void tableFlattenOverAPlainArrayColumn() {
        final ResultSet rs = q("SELECT f.value::VARCHAR FROM t x, TABLE(FLATTEN(x.arr)) f ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void tableFlattenPositionalInputWithNamedOuter() {
        // FLATTEN(<expr>, outer => TRUE) — positional input plus a named argument.
        assertEquals(2, q("SELECT f.value::VARCHAR FROM t x, TABLE(FLATTEN(x.arr, outer => TRUE)) f").getRowCount());
    }

    @Test
    public void tableFlattenCorrelatesToTheSecondTableOfAJoin() {
        assertEquals(2, q("SELECT f.value::VARCHAR FROM t x JOIN t y ON x.id = y.id, "
            + "TABLE(FLATTEN(y.arr)) f").getRowCount());
    }

    @Test
    public void oneOutputRowPerSourceRowPerElement() {
        // Correlation is per row: two source rows with two elements each produce four rows.
        engine.execute("INSERT INTO t SELECT 2, PARSE_JSON('{}'), ARRAY_CONSTRUCT('p', 'q')");
        assertEquals(4, q("SELECT f.value::VARCHAR FROM t x, TABLE(FLATTEN(x.arr)) f").getRowCount());
    }

    @Test
    public void explicitLateralFlattenStillWorks() {
        // Regression: the spelling that already worked must be unchanged.
        assertEquals(2, q("SELECT f.value::VARCHAR FROM t x, LATERAL FLATTEN(INPUT => x.arr) f").getRowCount());
    }

    @Test
    public void nonCorrelatedTableFunctionStillCrossJoins() {
        // A table function that references nothing outside itself yields the same cross join as before:
        // 2 source rows x 3 generated rows.
        engine.execute("INSERT INTO t SELECT 2, PARSE_JSON('{}'), ARRAY_CONSTRUCT('p')");
        assertEquals(6, q("SELECT t.id FROM t, TABLE(GENERATOR(ROWCOUNT => 3)) g").getRowCount());
    }

    // ---- a LATERAL item written AFTER an explicit JOIN may correlate to the JOINED table ----

    @Test
    public void lateralAfterAJoinSeesTheJoinedTable() {
        engine.execute("CREATE TABLE oc (k INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO oc VALUES (1, 'x')");
        engine.execute("CREATE TABLE d2 (k INTEGER, pairs ARRAY, sz INTEGER)");
        engine.execute("INSERT INTO d2 SELECT 1, ARRAY_CONSTRUCT('p', 'q'), 2");
        // Comma items were applied before the explicit joins, so the lateral ran while only `oc` was in
        // scope and failed with "Column not found: D2.PAIRS".
        assertEquals(2, q("SELECT f.value::VARCHAR FROM oc JOIN d2 ON oc.k = d2.k, "
            + "LATERAL FLATTEN(d2.pairs) f").getRowCount());
    }

    @Test
    public void lateralAfterAJoinUsingResolvesUnqualifiedInput() {
        engine.execute("CREATE TABLE oc (k INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO oc VALUES (1, 'x')");
        engine.execute("CREATE TABLE d2 (k INTEGER, pairs ARRAY, sz INTEGER)");
        engine.execute("INSERT INTO d2 SELECT 1, ARRAY_CONSTRUCT('p', 'q'), 2");
        // The loader shape: JOIN … USING (k), LATERAL FLATTEN(<unqualified col of the joined table>),
        // with a WHERE on the joined table too.
        assertEquals(2, q("SELECT f.value::VARCHAR FROM oc JOIN d2 USING (k), "
            + "LATERAL FLATTEN(pairs) f WHERE d2.sz > 1").getRowCount());
        assertEquals(2, q("SELECT f.value::VARCHAR FROM oc JOIN d2 USING (k), "
            + "TABLE(FLATTEN(d2.pairs)) f").getRowCount());
    }

    @Test
    public void emptySourceYieldsNoRows() {
        engine.execute("CREATE TABLE empty_t (id INTEGER, arr ARRAY)");
        assertEquals(0, q("SELECT f.value::VARCHAR FROM empty_t e, TABLE(FLATTEN(e.arr)) f").getRowCount());
    }

    @Test
    public void bareJoinToTableFunctionIsImplicitlyLateral() {
        // An explicit JOIN to a table function — no LATERAL keyword, no ON — still correlates to the
        // left row in Snowflake: FROM t JOIN TABLE(FLATTEN(t_col:path)). The join loop only honored
        // the LATERAL keyword, so the FLATTEN argument was evaluated without the left row in scope
        // and failed with "Column not found".
        engine.execute("CREATE TABLE jb (k INTEGER, attrs OBJECT)");
        engine.execute("INSERT INTO jb SELECT 1, OBJECT_CONSTRUCT('tags', ARRAY_CONSTRUCT('a', 'b'))");
        engine.execute("INSERT INTO jb SELECT 2, OBJECT_CONSTRUCT('tags', ARRAY_CONSTRUCT('c'))");
        engine.execute("INSERT INTO jb SELECT 3, OBJECT_CONSTRUCT('other', 1)");
        final ResultSet rs = q("""
            SELECT k, value::VARCHAR AS tag FROM jb
            JOIN TABLE(FLATTEN(attrs:tags))
            WHERE attrs:tags IS NOT NULL
            ORDER BY k, tag""");
        assertEquals(3, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(1));
        assertEquals("c", rs.getRows().get(2).getValue(1));
    }
}
