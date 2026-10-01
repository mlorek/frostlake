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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * An unaliased sequence read, {@code <sequence>.NEXTVAL}, is named NEXTVAL however it is qualified, quoted, spaced or
 * parenthesised, with a FROM or without one, and it is typed NUMBER(19,0): the result column, a table and a view
 * built over it carry that type, SYSTEM$TYPEOF tags it [SB8], and arithmetic over it widens as over any NUMBER(19,0).
 * Every cell is live-verified; the values handed out are not asserted, since the account hands them out in cached
 * blocks.
 */
public class SequenceReadColumnTest extends BaseDatabaseTest {

    private static final String NEXTVAL = "NEXTVAL:NUMBER(19,0)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("CREATE SEQUENCE \"lower\"");
        engine.execute("CREATE SEQUENCE \"q\"\"x\"");
        engine.execute("CREATE SEQUENCE s1");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    /** Each result column as NAME:TYPE, in order. */
    private String columns(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> named = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            named.add(column.getName() + ":" + typeText(column.getDataType()));
        }
        return String.join(", ", named);
    }

    private static String typeText(final DataType type) {
        if (type instanceof NumericType && !NumericType.isApproximate(type)) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return "VARCHAR(" + ((StringType) type).getMaxLength() + ")";
        }
        return String.valueOf(type);
    }

    private String columnsOf(final String table) {
        return answer("SELECT LISTAGG(column_name || ':' || data_type || ':' || numeric_precision || ':' || numeric_scale,"
            + " ' ') WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns"
            + " WHERE table_name = '" + table + "'");
    }

    @Test
    public void anUnaliasedReadIsNamedNextval() {
        final String[] reads = {
            "SELECT \"lower\".nextval",
            "SELECT \"lower\".NEXTVAL",
            "SELECT \"q\"\"x\".nextval",
            "SELECT s1.nextval",
            "SELECT \"S1\".nextval",
            "SELECT test_schema.s1.nextval",
            "SELECT test_db.test_schema.s1.nextval",
            "SELECT test_db.test_schema.\"lower\".nextval",
            "SELECT (s1.nextval)",
            "SELECT s1 . nextval",
            "SELECT DISTINCT s1.nextval",
            "SELECT s1.nextval FROM t",
            "SELECT * FROM (SELECT s1.nextval)",
            "WITH c AS (SELECT s1.nextval) SELECT * FROM c",
            "SELECT s1.nextval UNION ALL SELECT 5",
        };
        for (final String read : reads) {
            assertEquals(NEXTVAL, columns(read), read);
        }
        assertEquals(NEXTVAL + ", " + NEXTVAL, columns("SELECT s1.nextval, s1.nextval"));
        assertEquals("N:NUMBER(38,0), " + NEXTVAL, columns("SELECT n, s1.nextval FROM t ORDER BY n"));
    }

    @Test
    public void anAliasedOrComputedReadKeepsItsOwnName() {
        assertEquals("N:NUMBER(19,0)", columns("SELECT \"lower\".nextval AS n"));
        assertEquals("x:NUMBER(19,0)", columns("SELECT s1.nextval AS \"x\""));
        assertEquals("C:NUMBER(19,0), " + NEXTVAL, columns("SELECT s1.nextval AS c, \"lower\".nextval"));
        assertEquals("S1.NEXTVAL + 0:NUMBER(20,0)", columns("SELECT s1.nextval + 0"));
        assertEquals("S1.NEXTVAL * 2:NUMBER(20,0)", columns("SELECT s1.nextval * 2"));
        assertEquals("5:NUMBER(19,0)", columns("SELECT 5 UNION ALL SELECT s1.nextval"));
        assertEquals("S1.NEXTVAL::VARCHAR:VARCHAR(134217728)", columns("SELECT s1.nextval::VARCHAR"));
    }

    /**
     * Named NEXTVAL, a read is still no earlier item's alias: each FROM-less item hands out its own sequence's next
     * value, while a bare {@code nextval} after one reads that item.
     */
    @Test
    public void eachItemReadsItsOwnSequence() {
        final Row repeated = engine.executeQuery("SELECT s1.nextval, s1.nextval").getRows().get(0);
        assertNotEquals(String.valueOf(repeated.getValue(0)), String.valueOf(repeated.getValue(1)));
        final Row two = engine.executeQuery("SELECT s1.nextval, \"lower\".nextval").getRows().get(0);
        assertEquals("1", String.valueOf(two.getValue(1)), "the second sequence's own first value");
        final Row aliased = engine.executeQuery("SELECT s1.nextval, nextval").getRows().get(0);
        assertEquals(String.valueOf(aliased.getValue(0)), String.valueOf(aliased.getValue(1)));
        final Row three = engine.executeQuery("SELECT s1.nextval, s1.nextval, s1.nextval").getRows().get(0);
        assertNotEquals(String.valueOf(three.getValue(1)), String.valueOf(three.getValue(2)));
    }

    @Test
    public void typeofTagsItAsASixtyFourBitInteger() {
        final String[][] cells = {
            {"SELECT SYSTEM$TYPEOF(s1.nextval)", "NUMBER(19,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(s1.nextval) FROM t", "NUMBER(19,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(-s1.nextval)", "NUMBER(19,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, s1.nextval, 1))", "NUMBER(19,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(s1.nextval + 0)", "NUMBER(20,0)[SB16]"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void tablesAndViewsOverItDeclareTheType() {
        engine.execute("CREATE TABLE cq AS SELECT s1.nextval AS c");
        engine.execute("CREATE TABLE cq2 AS SELECT s1.nextval FROM t");
        engine.execute("CREATE TABLE cq3 AS SELECT s1.nextval + 0 AS a, (s1.nextval) AS b FROM t");
        engine.execute("CREATE VIEW vq AS SELECT s1.nextval AS c");
        assertEquals("C:NUMBER:19:0", columnsOf("CQ"));
        assertEquals("NEXTVAL:NUMBER:19:0", columnsOf("CQ2"));
        assertEquals("A:NUMBER:20:0 B:NUMBER:19:0", columnsOf("CQ3"));
        assertEquals("C:NUMBER:19:0", columnsOf("VQ"));
    }
}
