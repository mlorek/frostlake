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
 * A window function places each row in its partition by POSITION, never by what the row holds. Two
 * rows can be equal in every column, and a CTE read by both arms of a UNION ALL even hands the window
 * the same row twice; live still numbers, navigates and frames each one separately:
 *
 * <pre>
 *   LAG(v) OVER (ORDER BY v) over 7, 7            NULL, 7     (the second 7 used to find the first's place)
 *   LEAD(v) …                                     7, NULL
 *   ROW_NUMBER() over a CTE read twice            1, 2        (used to be 1, 1)
 *   SUM(v) OVER (… ROWS UNBOUNDED PRECEDING …)    7, 14
 *   NTILE(3) over three equal rows                1, 2, 3
 * </pre>
 *
 * <p>PERCENT_RANK and CUME_DIST read the same peer bounds RANK does, so they follow the WHOLE ORDER BY
 * key in its declared directions; and LAG / LEAD honour IGNORE NULLS, skipping SQL NULLs only.
 */
public class WindowRowPositionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE wp (k INT, v INT, w INT)");
        engine.execute("INSERT INTO wp VALUES (1, 7, 100), (1, 7, 100), (1, 7, 200),"
            + " (2, 8, 300), (2, 8, 300), (2, 9, NULL)");
        engine.execute("CREATE OR REPLACE TABLE wu (v INT, w INT)");
        engine.execute("INSERT INTO wu VALUES (7, 1), (7, 2), (8, 3)");
        engine.execute("CREATE OR REPLACE TABLE wn (g INT, i INT, w INT)");
        engine.execute("INSERT INTO wn VALUES (1, 1, 10), (1, 2, NULL), (1, 3, 30), (1, 4, NULL),"
            + " (1, 5, NULL), (1, 6, 60), (2, 1, NULL), (2, 2, 20), (2, 3, NULL)");
    }

    /** Every row of the result, its cells joined by '|', rows joined by ';' — in the query's own order. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(";");
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    out.append("|");
                }
                out.append(String.valueOf(rs.getValue(c)));
            }
        }
        return out.toString();
    }

    /** LAG and LEAD step to the neighbouring row even when it equals the current one in every column. */
    @Test
    public void lagAndLeadReachTheNeighbourOfAnEqualRow() {
        assertEquals("7|null;7|7", rows("SELECT v, LAG(v) OVER (ORDER BY v) AS l"
            + " FROM (SELECT 7 AS v UNION ALL SELECT 7) ORDER BY 1, 2 NULLS FIRST"));
        assertEquals("7|null;7|7", rows("SELECT v, LEAD(v) OVER (ORDER BY v) AS l"
            + " FROM (SELECT 7 AS v UNION ALL SELECT 7) ORDER BY 1, 2 NULLS FIRST"));
        assertEquals("1|7|100|null;1|7|100|100;1|7|200|100;2|8|300|null;2|8|300|300;2|9|null|300",
            rows("SELECT k, v, w, LAG(w) OVER (PARTITION BY k ORDER BY v, w) AS l FROM wp"
                + " ORDER BY k, v, w NULLS LAST, l NULLS FIRST"));
        assertEquals("1|7|100|100;1|7|100|200;1|7|200|null;2|8|300|null;2|8|300|300;2|9|null|null",
            rows("SELECT k, v, w, LEAD(w) OVER (PARTITION BY k ORDER BY v, w) AS l FROM wp"
                + " ORDER BY k, v, w NULLS LAST, l NULLS FIRST"));
        assertEquals("1|7|100|0;1|7|100|100;1|7|200|100;2|8|300|0;2|8|300|300;2|9|null|300",
            rows("SELECT k, v, w, LAG(w, 1, 0) OVER (PARTITION BY k ORDER BY v, w) AS l FROM wp"
                + " ORDER BY k, v, w NULLS LAST, l NULLS FIRST"));
        assertEquals("1|7|100|-1;1|7|100|200;1|7|200|-1;2|8|300|null;2|8|300|-1;2|9|null|-1",
            rows("SELECT k, v, w, LEAD(w, 2, -1) OVER (PARTITION BY k ORDER BY v, w) AS l FROM wp"
                + " ORDER BY k, v, w NULLS LAST, l NULLS FIRST"));
        assertEquals("1|7|100|1|null;1|7|100|2|1;1|7|200|3|1;2|8|300|1|1;2|8|300|2|2;2|9|null|3|2",
            rows("SELECT k, v, w, ROW_NUMBER() OVER (PARTITION BY k ORDER BY v, w) AS rn,"
                + " LAG(k) OVER (ORDER BY k, v, w) AS l FROM wp ORDER BY k, rn"));
        assertEquals("7|0;7|0;7|0;7|7", rows("SELECT v, LEAD(v, 3, 0) OVER (ORDER BY v) AS l FROM"
            + " (SELECT 7 AS v UNION ALL SELECT 7 UNION ALL SELECT 7 UNION ALL SELECT 7) ORDER BY 1, 2"));
        assertEquals("7|null;7|0;9|-2", rows("SELECT v, LAG(v) OVER (ORDER BY v) - v AS d FROM"
            + " (SELECT 7 AS v UNION ALL SELECT 7 UNION ALL SELECT 9) ORDER BY 1, 2 NULLS FIRST"));
        assertEquals("null;123", rows("WITH c AS (SELECT TO_VARIANT(123) AS v UNION ALL"
            + " SELECT TO_VARIANT(123)) SELECT TO_VARCHAR(LAG(v) OVER (ORDER BY 1)) AS l FROM c"
            + " ORDER BY 1 NULLS FIRST"));
    }

    /** A row object read twice — a CTE on both arms of a UNION ALL — is still two rows. */
    @Test
    public void aRowReadTwiceIsTwoRows() {
        final String twice = " FROM (SELECT * FROM c UNION ALL SELECT * FROM c)";
        assertEquals("7|1|null|1;7|2|7|2", rows("WITH c AS (SELECT 7 AS v) SELECT v,"
            + " ROW_NUMBER() OVER (ORDER BY v) AS rn, LAG(v) OVER (ORDER BY v) AS l,"
            + " NTILE(2) OVER (ORDER BY v) AS nt" + twice + " ORDER BY rn"));
        assertEquals("7|7|1|null|1;7|14|2|7|2", rows("WITH c AS (SELECT 7 AS v) SELECT v,"
            + " SUM(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS s,"
            + " COUNT(*) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS c1,"
            + " NTH_VALUE(v, 2) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS nv,"
            + " CONDITIONAL_TRUE_EVENT(v = 7) OVER (ORDER BY v) AS e" + twice + " ORDER BY s, c1"));
        assertEquals("7", rows("WITH c AS (SELECT 7 AS v) SELECT v" + twice
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY v) = 2"));
        assertEquals("7|2", rows("WITH c AS (SELECT 7 AS v) SELECT v, ROW_NUMBER() OVER (ORDER BY v) AS rn"
            + twice + " QUALIFY LAG(v) OVER (ORDER BY v) IS NOT NULL"));
        assertEquals("7|1|1|0|0.6666666667;7|1|1|0|0.6666666667;8|3|2|1|1", rows(
            "WITH c AS (SELECT 7 AS v) SELECT v, RANK() OVER (ORDER BY v) AS r,"
                + " DENSE_RANK() OVER (ORDER BY v) AS d, TO_VARCHAR(PERCENT_RANK() OVER (ORDER BY v)) AS p,"
                + " TO_VARCHAR(CUME_DIST() OVER (ORDER BY v)) AS cd"
                + " FROM (SELECT * FROM c UNION ALL SELECT * FROM c UNION ALL SELECT 8) ORDER BY v, r"));
    }

    /** NTILE deals equal rows into consecutive buckets like any others. */
    @Test
    public void ntileDealsEqualRowsApart() {
        assertEquals("1|7|100|1;1|7|100|2;1|7|200|3;2|8|300|1;2|8|300|2;2|9|null|3",
            rows("SELECT k, v, w, NTILE(3) OVER (PARTITION BY k ORDER BY v, w) AS n FROM wp"
                + " ORDER BY k, v, w NULLS LAST, n"));
        assertEquals("7|1;7|1;7|2", rows("SELECT v, NTILE(2) OVER (ORDER BY v) AS n"
            + " FROM (SELECT 7 AS v UNION ALL SELECT 7 UNION ALL SELECT 7) ORDER BY 2"));
    }

    /** PERCENT_RANK and CUME_DIST rank by the whole ORDER BY key, in each key's own direction. */
    @Test
    public void percentRankAndCumeDistReadTheWholeKey() {
        assertEquals("7|1|0|0.3333333333;7|2|0.5|0.6666666667;8|3|1|1",
            rows("SELECT v, w, TO_VARCHAR(PERCENT_RANK() OVER (ORDER BY v, w)) AS p,"
                + " TO_VARCHAR(CUME_DIST() OVER (ORDER BY v, w)) AS c FROM wu ORDER BY v, w"));
        assertEquals("7|1|0.5|1;7|2|1|0.6666666667;8|3|0|0.3333333333",
            rows("SELECT v, w, TO_VARCHAR(PERCENT_RANK() OVER (ORDER BY v DESC, w)) AS p,"
                + " TO_VARCHAR(CUME_DIST() OVER (ORDER BY v DESC, w DESC)) AS c FROM wu ORDER BY v, w"));
        assertEquals("1|7|100|0|0.6666666667;1|7|100|0|0.6666666667;1|7|200|1|1;"
                + "2|8|300|0|0.6666666667;2|8|300|0|0.6666666667;2|9|null|1|1",
            rows("SELECT k, v, w, TO_VARCHAR(PERCENT_RANK() OVER (PARTITION BY k ORDER BY v, w)) AS p,"
                + " TO_VARCHAR(CUME_DIST() OVER (PARTITION BY k ORDER BY v, w)) AS c FROM wp"
                + " ORDER BY k, v, w NULLS LAST, p"));
        assertEquals("null|0|0.6666666667;null|0|0.6666666667;8|1|1",
            rows("SELECT v, TO_VARCHAR(PERCENT_RANK() OVER (ORDER BY v NULLS FIRST)) AS p,"
                + " TO_VARCHAR(CUME_DIST() OVER (ORDER BY v NULLS FIRST)) AS c"
                + " FROM (SELECT NULL::INT AS v UNION ALL SELECT NULL UNION ALL SELECT 8)"
                + " ORDER BY v NULLS FIRST"));
    }

    /** IGNORE NULLS walks past SQL NULLs to the n-th value, in whichever direction the offset points. */
    @Test
    public void ignoreNullsSkipsNullsOnly() {
        assertEquals("1|10|null|30|null;2|null|10|30|null;3|30|10|60|null;4|null|30|60|10;"
                + "5|null|30|60|10;6|60|30|null|10",
            rows("SELECT i, w, LAG(w) IGNORE NULLS OVER (ORDER BY i) AS l,"
                + " LEAD(w) IGNORE NULLS OVER (ORDER BY i) AS d,"
                + " LAG(w, 2) IGNORE NULLS OVER (ORDER BY i) AS l2 FROM wn WHERE g = 1 ORDER BY i"));
        assertEquals("1|1|10|-1|60;1|2|null|10|60;1|3|30|10|-2;1|4|null|30|-2;1|5|null|30|-2;"
                + "1|6|60|30|-2;2|1|null|-1|-2;2|2|20|-1|-2;2|3|null|20|-2",
            rows("SELECT g, i, w, LAG(w, 1, -1) IGNORE NULLS OVER (PARTITION BY g ORDER BY i) AS l,"
                + " LEAD(w, 2, -2) IGNORE NULLS OVER (PARTITION BY g ORDER BY i) AS d FROM wn"
                + " ORDER BY g, i"));
        assertEquals("1|1|10|1;1|2|null|10;1|3|30|10;1|4|null|30;1|5|null|30;1|6|60|30;"
                + "2|1|null|1;2|2|20|2;2|3|null|20",
            rows("SELECT g, i, w, LAG(w, 1, i) IGNORE NULLS OVER (PARTITION BY g ORDER BY i) AS l"
                + " FROM wn ORDER BY g, i"));
        assertEquals("1|1|10|30;1|2|null|30;1|3|30|60;1|4|null|60;1|5|null|60;1|6|60|null;"
                + "2|1|null|20;2|2|20|null;2|3|null|null",
            rows("SELECT g, i, w, LAG(w, -1) IGNORE NULLS OVER (PARTITION BY g ORDER BY i) AS l"
                + " FROM wn ORDER BY g, i"));
        assertEquals("1|1|10|10|10;1|2|null|null|null;1|3|30|30|30;1|4|null|null|null;"
                + "1|5|null|null|null;1|6|60|60|60;2|1|null|null|null;2|2|20|20|20;2|3|null|null|null",
            rows("SELECT g, i, w, LAG(w, 0) IGNORE NULLS OVER (PARTITION BY g ORDER BY i) AS l0,"
                + " LEAD(w, 0) IGNORE NULLS OVER (PARTITION BY g ORDER BY i) AS d0 FROM wn ORDER BY g, i"));
        assertEquals("1|null;2|10;3|10;4|30;5|30;6|30",
            rows("SELECT i, TO_VARCHAR(LAG(TO_VARIANT(w)) IGNORE NULLS OVER (ORDER BY i)) AS l"
                + " FROM wn WHERE g = 1 ORDER BY i"));
        // A VARIANT holding a JSON null is a VALUE, not a NULL: it is what LAG lands on.
        assertEquals("1|null|null;2|10|false;3|null|true;4|30|false;5|null|true;6|null|true",
            rows("SELECT i, TO_VARCHAR(l) AS t, IS_NULL_VALUE(l) AS jn FROM (SELECT i,"
                + " LAG(PARSE_JSON(IFF(w IS NULL, 'null', w::VARCHAR))) IGNORE NULLS OVER (ORDER BY i) AS l"
                + " FROM wn WHERE g = 1) ORDER BY i"));
    }

    /** Grouped rows equal by value are still separate rows to the window over them. */
    @Test
    public void groupedRowsEqualByValueStaySeparate() {
        assertEquals("3|null|1;3|3|2", rows("SELECT COUNT(*) AS c, LAG(COUNT(*)) OVER (ORDER BY COUNT(*)) AS l,"
            + " ROW_NUMBER() OVER (ORDER BY COUNT(*)) AS rn FROM wp GROUP BY k ORDER BY rn"));
        assertEquals("null;3", rows("SELECT LAG(COUNT(*)) OVER (ORDER BY COUNT(*)) AS l FROM wp"
            + " GROUP BY k ORDER BY 1 NULLS FIRST"));
    }
}
