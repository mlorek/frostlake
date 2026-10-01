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
 * A WITHIN GROUP belongs to LISTAGG, ARRAY_AGG and the two ordered percentiles: every other function refuses it
 * while its clause's names are walked, at the call's written place — after the names inside the call and its WITHIN
 * GROUP, ahead of every later name, unknown function, argument count and type, and of the enclosing query's types
 * when the call stands in a subquery.
 */
public class WithinGroupSupportTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT, g VARCHAR(5))");
        engine.execute("INSERT INTO t VALUES (1, 10, 'x'), (2, 20, 'y'), (3, 30, 'x')");
    }

    /** Every row's cells joined, or the refusal with its lines joined by '|'. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(" / ");
                }
                for (int i = 0; i < rs.getColumns().size(); i++) {
                    all.append(i > 0 ? " " : "").append(String.valueOf(rs.getValue(i)));
                }
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String refused(final String name) {
        return "SQL compilation error:|Function " + name + " does not support WITHIN GROUP clause.";
    }

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    @Test
    public void onlyTheOrderedAggregatesTakeAWithinGroup() {
        final String[] calls = {
            "MEDIAN(a)", "AVG(a)", "SUM(a)", "MODE(a)", "APPROX_PERCENTILE(a, 0.5)", "COUNT(a)", "MIN(a)", "MAX(a)",
            "ANY_VALUE(a)", "CORR(a, b)", "STDDEV(a)", "COUNT_IF(a > 1)", "HASH_AGG(a)", "ARRAY_UNIQUE_AGG(a)",
            "BOOLAND_AGG(a > 0)", "VARIANCE(a)", "APPROX_COUNT_DISTINCT(a)", "MAX_BY(a, b)", "BITAND_AGG(a)",
            "APPROX_TOP_K(a)", "HLL(a)", "UPPER(g)", "ABS(a)", "MEDIAN(DISTINCT a)", "COUNT(DISTINCT a)",
        };
        for (final String call : calls) {
            final String name = call.substring(0, call.indexOf('('));
            assertEquals(refused(name), answer("SELECT " + call + " WITHIN GROUP (ORDER BY a) FROM t"), call);
        }
        assertEquals("x,y,x", answer("SELECT LISTAGG(g, ',') WITHIN GROUP (ORDER BY a) FROM t"));
        assertEquals("1.500", answer("SELECT TO_VARCHAR(PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY a)) FROM t"));
        assertEquals("2", answer("SELECT TO_VARCHAR(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY a)) FROM t"));
        assertEquals("[1,2,3]", answer("SELECT TO_VARCHAR(ARRAY_AGG(a) WITHIN GROUP (ORDER BY a)) FROM t"));
        assertEquals("[1,2,3]", answer("SELECT TO_VARCHAR(ARRAYAGG(a) WITHIN GROUP (ORDER BY a)) FROM t"));
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.",
            answer("SELECT NOSUCHFN(a) WITHIN GROUP (ORDER BY a) FROM t"));
        assertEquals("Unsupported feature 'TOK_STAR'.", answer("SELECT COUNT(*) WITHIN GROUP (ORDER BY a) FROM t"));
        assertEquals(refused("MEDIAN"), answer("SELECT MEDIAN(a) WITHIN GROUP (ORDER BY a) OVER () FROM t"));
        assertEquals(refused("SUM"), answer("SELECT SUM(a) WITHIN GROUP (ORDER BY a) OVER () FROM t"));
        assertEquals(refused("MEDIAN"), answer("SELECT MEDIAN(x => a) WITHIN GROUP (ORDER BY a) FROM t"));
    }

    @Test
    public void theRefusalIsJudgedAtItsPlaceInTheNameWalk() {
        final String median = "MEDIAN(a) WITHIN GROUP (ORDER BY a)";
        assertEquals(at(40, "invalid identifier 'NOSUCH'"),
            answer("SELECT MEDIAN(a) WITHIN GROUP (ORDER BY nosuch) FROM t"));
        assertEquals(at(14, "invalid identifier 'NOSUCH'"),
            answer("SELECT MEDIAN(nosuch) WITHIN GROUP (ORDER BY a) FROM t"));
        assertEquals(at(7, "invalid identifier 'NOSUCH'"), answer("SELECT nosuch, " + median + " FROM t"));
        final String[] refusedFirst = {
            "SELECT " + median + ", nosuch FROM t",
            "SELECT " + median + " FROM t WHERE nosuch = 1",
            "SELECT " + median + " FROM t WHERE 'o' + TRUE = 1",
            "SELECT a FROM t WHERE 'o' + TRUE = 1 AND a = (SELECT " + median + " FROM t)",
            "SELECT a FROM t GROUP BY a HAVING " + median + " > 1",
            "SELECT a FROM t ORDER BY " + median,
            "SELECT g, " + median + " FROM t GROUP BY g",
            "SELECT UPPER(1, 2), " + median + " FROM t",
            "SELECT " + median + ", UPPER(1, 2) FROM t",
            "SELECT " + median + ", NOSUCHFN(1) FROM t",
            "SELECT NOSUCHFN(1), " + median + " FROM t",
            "SELECT " + median + " + 'x' FROM t",
            "SELECT a + TRUE, " + median + " FROM t",
            "SELECT MEDIAN(a, b) WITHIN GROUP (ORDER BY a) FROM t",
            "SELECT MEDIAN(a) WITHIN GROUP (ORDER BY a, b) FROM t",
            "SELECT MEDIAN(a) WITHIN GROUP (ORDER BY a) OVER (PARTITION BY nosuch) FROM t",
        };
        for (final String sql : refusedFirst) {
            assertEquals(refused("MEDIAN"), answer(sql), sql);
        }
    }
}
