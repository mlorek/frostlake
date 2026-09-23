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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A result column whose item reads something the projection cannot see on its own is still declared
 * with the type of what it reads, as live declares it — not the VARCHAR(16777216) placeholder a driver
 * would otherwise report:
 *
 * <ul>
 *   <li>an item reading an EARLIER item's alias ({@code SELECT a AS a2, a2 + 0}), with or without a FROM;</li>
 *   <li>a correlated scalar subquery whose item reads the outer row ({@code (SELECT fz.id + 1)}), one level
 *       out or two;</li>
 *   <li>a LATERAL body's item that reads the relations joined before it.</li>
 * </ul>
 *
 * <p>A later alias is not in scope at all, and a name a relation carries still means the column. A CTAS, a
 * view and a derived table over such a query inherit the declared type.
 */
public class DerivedItemTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, n NUMBER(10,2), s VARCHAR(5), d DATE, b BOOLEAN)");
        engine.execute("INSERT INTO t1 VALUES (1, 2.50, 'ab', '2020-01-01', TRUE),"
            + " (2, 3.25, 'cd', '2020-01-02', FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    /** Every result column's declared type, joined by {@code |}: a number with its precision and scale. */
    private String typesOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(spelled(column.getDataType()));
        }
        return out.toString();
    }

    private static String spelled(final DataType type) {
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType) {
            // The FLOAT family carries no precision or scale on the account's metadata.
            return NumericType.isApproximate(type) ? "FLOAT"
                : "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return "VARCHAR(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** The first row's cells, joined by {@code |}. */
    private String firstRow(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue(i)));
        }
        return out.toString();
    }

    /** An item reading an earlier item's alias takes that item's type, and expressions over it type from it. */
    @Test
    public void anItemReadingAnAliasTakesTheAliasedType() {
        assertEquals("NUMBER(38,0) | NUMBER(38,0)", typesOf("SELECT a AS a2, a2 FROM t1"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)", typesOf("SELECT a AS a2, a2 + 0 FROM t1"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)", typesOf("SELECT a AS a2, IDENTIFIER('a2') FROM t1"));
        assertEquals("NUMBER(10,2) | NUMBER(11,2) | NUMBER(12,2)",
            typesOf("SELECT n AS n2, n2 * 2 AS m, m + 1 AS k FROM t1"));
        assertEquals("VARCHAR(5) | VARCHAR(15) | VARCHAR(6) | NUMBER(18,0)",
            typesOf("SELECT s AS s2, UPPER(s2), s2 || 'x', LENGTH(s2) FROM t1"));
        assertEquals("VARCHAR(5) | VARCHAR(5) | VARCHAR(5)", typesOf("SELECT s AS s2, s2 AS s3, s3 FROM t1"));
        assertEquals("DATE | DATE | DATE", typesOf("SELECT d AS d2, DATEADD(day, 1, d2), d2 FROM t1"));
        assertEquals("VARIANT | VARIANT", typesOf("SELECT TO_VARIANT(a) AS v, v FROM t1"));
        assertEquals("ARRAY | ARRAY", typesOf("SELECT ARRAY_CONSTRUCT(a) AS arr, arr FROM t1"));
        assertEquals("FLOAT | FLOAT", typesOf("SELECT n::FLOAT AS f, f + 1 FROM t1"));
    }

    /** A name a relation carries still means the column, even beside a same-named alias. */
    @Test
    public void aColumnStillWinsOverAnAlias() {
        assertEquals("NUMBER(38,1) | NUMBER(38,0)", typesOf("SELECT a + 0.5 AS a, a FROM t1"));
    }

    /** A LATER alias is not in scope. */
    @Test
    public void aLaterAliasIsNotInScope() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT a2 + 0 AS z, a AS a2 FROM t1");
            }
        });
        assertTrue(refused.getMessage().contains("invalid identifier 'A2'"), refused.getMessage());
    }

    /** An aggregate's or a window's alias types what reads it, in a grouped query too. */
    @Test
    public void anAggregateOrWindowAliasTypesWhatReadsIt() {
        assertEquals("NUMBER(38,0) | NUMBER(18,0) | NUMBER(19,0)",
            typesOf("SELECT a AS g, COUNT(*) AS c, c + 1 FROM t1 GROUP BY g"));
        assertEquals("NUMBER(38,0) | NUMBER(22,2) | NUMBER(23,2)",
            typesOf("SELECT a AS g, SUM(n) AS sm, sm * 2 FROM t1 GROUP BY a"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)", typesOf("SELECT a AS a2, SUM(a2) OVER () AS w FROM t1"));
        assertEquals("NUMBER(18,0) | NUMBER(19,0)",
            typesOf("SELECT ROW_NUMBER() OVER (ORDER BY a) AS r, r + 1 AS r1 FROM t1"));
    }

    /** With no FROM, a later item reads an earlier one the same way. */
    @Test
    public void aFromlessItemReadsAnEarlierOne() {
        assertEquals("NUMBER(1,0) | NUMBER(1,0)", typesOf("SELECT 1 AS x, x"));
        assertEquals("NUMBER(2,1) | NUMBER(4,1)", typesOf("SELECT 1.5 AS a, a + 0 AS b"));
        assertEquals("VARCHAR(2) | VARCHAR(2)", typesOf("SELECT 'ab' AS x, x"));
        assertEquals("DATE | DATE", typesOf("SELECT CURRENT_DATE() AS d, d"));
    }

    /** A correlated scalar subquery is typed from the outer column its item reads. */
    @Test
    public void aCorrelatedScalarSubqueryIsTypedFromTheOuterColumn() {
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT id, (SELECT MAX(v) + fz.id FROM g) AS x FROM fz"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)", typesOf("SELECT id, (SELECT fz.id + 1) AS x FROM fz"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT id, (SELECT MAX(v) + fz.id FROM g ORDER BY 1 LIMIT 1) AS x FROM fz"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT id, (SELECT BOOLAND_AGG(v > 0)::INT + fz.id FROM g) AS x FROM fz"));
        assertEquals("NUMBER(38,0) | BOOLEAN", typesOf("SELECT id, (SELECT fz.b) AS x FROM fz"));
        assertEquals("NUMBER(38,0) | NUMBER(38,1)", typesOf("SELECT id, (SELECT fz.id * 1.5) AS x FROM fz"));
        assertEquals("NUMBER(38,0) | VARCHAR(134217728)",
            typesOf("SELECT id, (SELECT fz.id::VARCHAR || 'x') AS x FROM fz"));
        assertEquals("BOOLEAN | NUMBER(38,0)",
            typesOf("SELECT b, (SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id)) AS x FROM fz GROUP BY b"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT id, (SELECT (SELECT fz.id + 1)) AS x FROM fz"), "two levels out");
        assertEquals("5 | 6", firstRow("SELECT id, (SELECT fz.id + 1) AS x FROM fz ORDER BY id"));
    }

    /** A LATERAL body's item that reads the relations before it is typed from them. */
    @Test
    public void aLateralItemIsTypedFromTheRelationsBeforeIt() {
        engine.execute("CREATE OR REPLACE TABLE f1 (y INT, \"x\" INT, \"X\" INT)");
        engine.execute("INSERT INTO f1 VALUES (3, 1, 10), (4, 2, 20)");
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT f.id, l.z FROM fz f JOIN LATERAL (SELECT f.id + 1 AS z) l"));
        assertEquals("NUMBER(38,0)", typesOf("SELECT l.v FROM f1 a, LATERAL (SELECT a.y AS v) l"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT l.v, l.w FROM f1 a, LATERAL (SELECT a.\"x\" AS v, a.\"X\" AS w) l"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT l.v, l.k FROM fz f, LATERAL (SELECT g.v, f.id + g.v AS k FROM g WHERE g.id >= f.id) l"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)", typesOf("SELECT f.id, l.q FROM fz f,"
            + " LATERAL (SELECT x.value::INT + f.id AS q FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1, 2))) x) l"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT f.id, l.z, l.z + 1 FROM fz f, LATERAL (SELECT f.id * 2 AS z) l"));
        assertEquals("5 | 10 | 11",
            firstRow("SELECT f.id, l.z, l.z + 1 FROM fz f, LATERAL (SELECT f.id * 2 AS z) l ORDER BY 1"));
    }

    /** A CTAS, a view, a CTE and a derived table over such items inherit the declared type. */
    @Test
    public void derivedRelationsInheritTheType() {
        engine.execute("CREATE OR REPLACE TABLE c1 AS SELECT a AS a2, a2 + 0 AS b, n AS n2, n2 * 2 AS m FROM t1");
        assertEquals("A2 NUMBER 38 0 | B NUMBER 38 0 | N2 NUMBER 10 2 | M NUMBER 11 2", columnsOf("C1"));
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT id, (SELECT fz.id + 1) AS x, (SELECT fz.b) AS y FROM fz");
        assertEquals("ID NUMBER 38 0 | X NUMBER 38 0 | Y BOOLEAN null null", columnsOf("V1"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("WITH c AS (SELECT a AS a2, a2 * 2 AS d FROM t1) SELECT d, d + 1 FROM c"));
        assertEquals("NUMBER(38,0) | NUMBER(38,0)",
            typesOf("SELECT x.a2, x.b FROM (SELECT a AS a2, a2 + 0 AS b FROM t1) x"));
    }

    /** INFORMATION_SCHEMA's view of a relation's columns: name, type, precision and scale. */
    private String columnsOf(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT column_name, data_type,"
            + " TO_VARCHAR(numeric_precision) AS p, TO_VARCHAR(numeric_scale) AS s"
            + " FROM information_schema.columns WHERE table_name = '" + table + "' ORDER BY ordinal_position");
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(rs.getValue(0)).append(' ').append(rs.getValue(1)).append(' ')
                .append(rs.getValue(2)).append(' ').append(rs.getValue(3));
        }
        return out.toString();
    }
}
