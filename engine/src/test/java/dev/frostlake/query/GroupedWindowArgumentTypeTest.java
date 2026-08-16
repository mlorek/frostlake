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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A window call's ARGUMENT over grouped rows is typed against the relation the groups were formed
 * over. The window stage evaluates against the projected SELECT-list shape, whose slots carry values
 * but not every declaration, and every argument there used to be untyped: SYSTEM$TYPEOF answered NULL
 * for LAG, SUM, AVG, MAX and FIRST_VALUE over grouped rows alike, and RATIO_TO_REPORT kept a nominal
 * NUMBER(38,6) — six decimals where the account declares eight and prints eight.
 *
 * <p>Two routes now reach the declarations. A projected item the projection could type — an aliased
 * aggregate, a selected group key — carries that type in the shape itself; a base column the SELECT
 * list never projects is read from the grouped resolver's base relation. The metadata a CTAS declares
 * was right all along, because the projection typed its columns against the base table; only the
 * row-time channel — SYSTEM$TYPEOF and the value's own scale — had lost the relation.
 *
 * <p>Left out on purpose: {@code RATIO_TO_REPORT(AVG(a))}, whose width follows AVG's own declared
 * width, a separately tracked divergence.
 */
public class GroupedWindowArgumentTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pw (a NUMBER(10,2), b INT, f FLOAT, v VARCHAR(5),"
            + " m NUMBER(5,4))");
        engine.execute("INSERT INTO pw VALUES (1.5, 1, 0.5, 'x', 0.1234), (2.5, 2, 1.5, 'y', 0.2),"
            + " (2.5, 3, 2.0, 'y', 0.3)");
    }

    /** The declared type SYSTEM$TYPEOF names, without the storage tag. */
    private String typeOf(final String expression, final String tail) {
        final ResultSet rs = engine.executeQuery(
            "SELECT SYSTEM$TYPEOF(" + expression + ") FROM pw " + tail + " LIMIT 1");
        rs.next();
        return String.valueOf(rs.getValue(0)).replaceAll("\\[SB[0-9]+\\]|\\[LOB\\]", "");
    }

    /** Every row's text, in order. */
    private String texts(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue(0)));
        }
        return out.toString();
    }

    /** ★ RATIO_TO_REPORT reads its argument's width over grouped rows, whatever the argument is. */
    @Test
    public void ratioToReportReadsItsArgumentOverGroupedRows() {
        assertEquals("NUMBER(18,8)", typeOf("RATIO_TO_REPORT(a) OVER ()", "GROUP BY a"),
            "a group key the SELECT list never projects");
        assertEquals("NUMBER(30,8)", typeOf("RATIO_TO_REPORT(SUM(a)) OVER ()", "GROUP BY a"),
            "a raw aggregate over the group");
        assertEquals("NUMBER(18,8)", typeOf("RATIO_TO_REPORT(MIN(a)) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(24,6)", typeOf("RATIO_TO_REPORT(COUNT(*)) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(38,6)", typeOf("RATIO_TO_REPORT(b) OVER ()", "GROUP BY a, b"));
        assertEquals("NUMBER(38,6)", typeOf("RATIO_TO_REPORT(SUM(b)) OVER ()", "GROUP BY a"));
        assertEquals("FLOAT[DOUBLE]", typeOf("RATIO_TO_REPORT(f) OVER ()", "GROUP BY a, f"),
            "an approximate argument keeps the approximate family");
        assertEquals("FLOAT[DOUBLE]", typeOf("RATIO_TO_REPORT(SUM(f)) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(15,10)", typeOf("RATIO_TO_REPORT(m) OVER ()", "GROUP BY a, m"));
        assertEquals("NUMBER(21,8)", typeOf("RATIO_TO_REPORT(a * 100) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(18,8)", typeOf("RATIO_TO_REPORT(a) OVER (PARTITION BY a)", "GROUP BY a"));
        assertEquals("NUMBER(30,8)", typeOf("RATIO_TO_REPORT(SUM(a)) OVER ()", "GROUP BY a HAVING SUM(a) > 0"));
    }

    /** ★ And every other window call's argument is typed there too — it was never RATIO's problem. */
    @Test
    public void everyWindowArgumentIsTypedOverGroupedRows() {
        assertEquals("NUMBER(10,2)", typeOf("LAG(a) OVER (ORDER BY a)", "GROUP BY a"));
        assertEquals("NUMBER(22,2)", typeOf("LAG(SUM(a)) OVER (ORDER BY a)", "GROUP BY a"));
        assertEquals("NUMBER(22,2)", typeOf("SUM(a) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(34,2)", typeOf("SUM(SUM(a)) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(25,5)", typeOf("AVG(a) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(37,5)", typeOf("AVG(SUM(a)) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(10,2)", typeOf("MAX(a) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(22,2)", typeOf("MAX(SUM(a)) OVER ()", "GROUP BY a"));
        assertEquals("NUMBER(10,2)", typeOf("FIRST_VALUE(a) OVER (ORDER BY a)", "GROUP BY a"));
        assertEquals("NUMBER(22,2)", typeOf("FIRST_VALUE(SUM(a)) OVER (ORDER BY a)", "GROUP BY a"));
        assertEquals("NUMBER(38,0)", typeOf("SUM(b) OVER ()", "GROUP BY a, b"));
        assertEquals("FLOAT[DOUBLE]", typeOf("SUM(f) OVER ()", "GROUP BY a, f"));
        assertEquals("VARCHAR(5)", typeOf("LAG(v) OVER (ORDER BY a)", "GROUP BY a, v"));
        assertEquals("NUMBER(18,0)", typeOf("ROW_NUMBER() OVER (ORDER BY a)", "GROUP BY a"));
    }

    /** The ungrouped shapes, the JOIN shape and a derived-key shape were right and stay right. */
    @Test
    public void theOtherShapesAreUnchanged() {
        assertEquals("NUMBER(18,8)", typeOf("RATIO_TO_REPORT(a) OVER ()", ""));
        assertEquals("NUMBER(10,2)", typeOf("LAG(a) OVER (ORDER BY a)", ""));
        assertEquals("NUMBER(22,2)", typeOf("SUM(a)", "GROUP BY a"), "a plain aggregate item");
        assertEquals("NUMBER(11,2)", typeOf("a + 1", "GROUP BY a"), "a plain group-key expression");
        final ResultSet joined = engine.executeQuery("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(SUM(p.a)) OVER ())"
            + " FROM pw p JOIN pw q ON p.b = q.b GROUP BY p.a LIMIT 1");
        joined.next();
        assertEquals("NUMBER(30,8)", String.valueOf(joined.getValue(0)).replaceAll("\\[SB[0-9]+\\]", ""));
        final ResultSet derived = engine.executeQuery("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(k) OVER ())"
            + " FROM (SELECT a AS k FROM pw) GROUP BY k LIMIT 1");
        derived.next();
        assertEquals("NUMBER(18,8)", String.valueOf(derived.getValue(0)).replaceAll("\\[SB[0-9]+\\]", ""),
            "a derived relation's key carries the inner projection's type");
    }

    /** ★ The value follows the type: eight decimals where six used to be rounded in. */
    @Test
    public void theValueFollowsTheDeclaredScale() {
        assertEquals("0.23076923 | 0.76923077",
            texts("SELECT TO_VARCHAR(RATIO_TO_REPORT(SUM(a)) OVER ()) FROM pw GROUP BY a ORDER BY 1"));
        assertEquals("0.37500000 | 0.62500000",
            texts("SELECT TO_VARCHAR(RATIO_TO_REPORT(a) OVER ()) FROM pw GROUP BY a ORDER BY 1"));
        assertEquals("0.37500000 | 0.62500000",
            texts("SELECT TO_VARCHAR(RATIO_TO_REPORT(MIN(a)) OVER ()) FROM pw GROUP BY a ORDER BY 1"));
        assertEquals("0.166667 | 0.833333",
            texts("SELECT TO_VARCHAR(RATIO_TO_REPORT(SUM(b)) OVER ()) FROM pw GROUP BY a ORDER BY 1"),
            "an integer argument keeps the six-decimal width");
        assertEquals("0.125 | 0.875",
            texts("SELECT TO_VARCHAR(RATIO_TO_REPORT(SUM(f)) OVER ()) FROM pw GROUP BY a ORDER BY 1"),
            "an approximate argument prints as a double");
    }

    /** The metadata a CTAS declares was right before and is right after. */
    @Test
    public void theDeclaredMetadataIsUnchanged() {
        engine.execute("CREATE OR REPLACE TABLE pwc AS SELECT RATIO_TO_REPORT(SUM(a)) OVER () AS r FROM pw GROUP BY a");
        assertEquals("NUMBER(30,8)", describeCell("pwc", "R", "type"));
        engine.execute("CREATE OR REPLACE TABLE pwc AS SELECT LAG(SUM(a)) OVER (ORDER BY a) AS r FROM pw GROUP BY a");
        assertEquals("NUMBER(22,2)", describeCell("pwc", "R", "type"));
    }
}
