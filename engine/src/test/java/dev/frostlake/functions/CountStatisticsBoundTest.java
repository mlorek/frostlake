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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SYSTEM$TYPEOF over COUNT reads the account's statistics bound. While every row of the one table a query
 * reads reaches the aggregate, COUNT is tagged by the table's row count and a typeof over it answers one
 * row; a WHERE keeps that bound when the column statistics (least, greatest, any NULL) prove it true of
 * every row. A WHERE they cannot prove, a grouping key, a HAVING or a join take COUNT to its declared
 * width, and a typeof over COUNT, MIN or MAX then folds to the scan like any other aggregate. Every cell is
 * live-verified, over columns of distinct values (a column holding one value is answered like a constant
 * on the account).
 */
public class CountStatisticsBoundTest extends BaseDatabaseTest {

    private static final String BOUND = "NUMBER(18,0)[SB1]";
    private static final String UNBOUND = "NUMBER(18,0)[SB8]";
    private static final String SUM_TYPE = "NUMBER(32,10)[SB16]";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE aw (c20_10 NUMBER(20,10), k VARCHAR, d DATE, n NUMBER(38,0))");
        engine.execute("""
            INSERT INTO aw VALUES (1.5, 'a', '2020-01-01', 10), (3.5, 'b', '2021-06-15', 20),
                (5.5, 'b', '2022-12-31', 30)""");
        engine.execute("CREATE TABLE nw (c NUMBER(10,0))");
        engine.execute("INSERT INTO nw VALUES (1), (2), (NULL)");
        engine.execute("CREATE TABLE bw (j NUMBER)");
        engine.execute("INSERT INTO bw VALUES (1), (2)");
        engine.execute("CREATE TABLE sw (c NUMBER(10,0))");
        engine.execute("INSERT INTO sw VALUES (5), (5)");
    }

    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    line.append(", ");
                }
                line.append(row.getValue(c));
            }
            out.add(line.toString());
        }
        return out;
    }

    private static List<String> times(final int count, final String line) {
        return Collections.nCopies(count, line);
    }

    @Test
    public void aCountOverTheWholeTableIsTaggedByItsRowCount() {
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(c20_10)) FROM aw"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw GROUP BY ALL"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw LIMIT 1"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(c)) FROM nw"));
    }

    @Test
    public void aFilterTheStatisticsProveKeepsTheBound() {
        final List<String> proven = List.of("1 = 1", "TRUE", "c20_10 > 0", "0 < c20_10", "c20_10 > -1",
            "c20_10 >= 1.5", "c20_10 <> 99", "c20_10 BETWEEN 0 AND 10", "c20_10 > 0 AND c20_10 < 10",
            "c20_10 > 0 AND 1 = 1", "NOT (c20_10 < 0)", "c20_10 > 0 OR k = 'zz'", "c20_10 IS NOT NULL",
            "c20_10 = c20_10", "n > 0", "d > '2000-01-01'::DATE", "k > 'A'", "k IS NOT NULL");
        for (final String where : proven) {
            assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE " + where), where);
        }
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM sw WHERE c = 5"));
    }

    @Test
    public void aFilterTheStatisticsCannotProveTakesTheCountPastThem() {
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > 3"));
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > 1.5"));
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(k)) FROM aw WHERE k = 'b'"));
        assertEquals(times(3, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > 3 OR k = 'a'"));
        assertEquals(times(3, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE k IN ('a', 'b')"));
        assertEquals(times(3, UNBOUND),
            rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 IN (1.5, 3.5, 5.5)"));
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM nw WHERE c > 0"));
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM nw WHERE c IS NOT NULL"));
        assertEquals(List.of(), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE 1 = 0"));
        assertEquals(List.of(UNBOUND + ", 2"),
            rows("SELECT SYSTEM$TYPEOF(COUNT(*)), COUNT(*) FROM aw WHERE c20_10 > 3"));
    }

    @Test
    public void groupingHavingAndAJoinTakeTheCountPastThem() {
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw GROUP BY k"));
        assertEquals(List.of("a, " + UNBOUND, "b, " + UNBOUND),
            rows("SELECT k, SYSTEM$TYPEOF(COUNT(*)) FROM aw GROUP BY k ORDER BY k"));
        assertEquals(List.of("a, " + UNBOUND, "b, " + UNBOUND),
            rows("SELECT k, SYSTEM$TYPEOF(COUNT(*)) FROM aw GROUP BY ALL ORDER BY k"));
        assertEquals(List.of("x, " + UNBOUND), rows("SELECT 'x', SYSTEM$TYPEOF(COUNT(*)) FROM aw GROUP BY ALL"));
        assertEquals(List.of(UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw HAVING COUNT(*) > 0"));
        assertEquals(times(6, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw, bw"));
    }

    @Test
    public void theBoundDecidesWhatIsAnsweredFromStatistics() {
        final String max = "NUMBER(20,10)[SB8]";
        assertEquals(List.of(max), rows("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM aw"));
        assertEquals(List.of(max), rows("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM aw WHERE c20_10 > 0"));
        assertEquals(times(2, max), rows("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM aw WHERE c20_10 > 3"));
        assertEquals(times(2, "NUMBER(38,0)[SB1]"), rows("SELECT SYSTEM$TYPEOF(MAX(n)) FROM aw GROUP BY k"));
        assertEquals(times(6, "NUMBER(38,0)[SB1]"), rows("SELECT SYSTEM$TYPEOF(MAX(n)) FROM aw, bw"));
        assertEquals(List.of("NUMBER(38,0)[SB1]"), rows("SELECT SYSTEM$TYPEOF(MIN(n)) FROM aw HAVING MIN(n) > 0"));
        assertEquals(List.of(SUM_TYPE + ", 2"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw WHERE c20_10 > 3"));
        assertEquals(times(3, SUM_TYPE + ", 3"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw WHERE c20_10 > 0"));
        assertEquals(List.of(SUM_TYPE + ", 6"), rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM aw, bw"));
    }

    @Test
    public void aDerivedRelationOverOneTableIsInlined() {
        engine.execute("CREATE OR REPLACE VIEW awv AS SELECT * FROM aw");
        engine.execute("CREATE OR REPLACE VIEW awx AS SELECT c20_10 + 1 AS x, k FROM aw");
        final List<String> inlined = List.of("(SELECT * FROM aw)", "(SELECT * FROM aw WHERE c20_10 > 0)",
            "(SELECT * FROM aw) WHERE c20_10 > 0", "(SELECT * FROM aw WHERE c20_10 > 0) WHERE c20_10 < 10",
            "(SELECT c20_10 FROM aw)", "(SELECT c20_10 AS x FROM aw) WHERE x > 0", "(SELECT c20_10 + 1 AS x FROM aw)",
            "(SELECT c20_10 + 1 AS x FROM aw) WHERE x > 0", "(SELECT * FROM (SELECT * FROM aw))",
            "(SELECT * FROM aw) x", "(SELECT * FROM aw) x WHERE x.c20_10 > 0", "(SELECT * FROM aw WHERE 1 = 1)",
            "(SELECT * FROM aw WHERE c20_10 > (SELECT 0))",
            "(SELECT c20_10, ROW_NUMBER() OVER (ORDER BY c20_10) AS r FROM aw)",
            "(SELECT c20_10, (SELECT 1) AS o FROM aw) WHERE o = 1",
            "(SELECT c20_10 AS a, c20_10 AS b FROM aw) WHERE a > 0 AND b < 10",
            "(SELECT k AS c20_10 FROM aw) WHERE c20_10 > 'A'", "(SELECT * FROM aw) WHERE k IS NOT NULL",
            "(SELECT * FROM nw)", "awv", "awv WHERE c20_10 > 0", "awx", "awx WHERE x > 0",
            "(SELECT * FROM awx) WHERE x > 0");
        for (final String source : inlined) {
            assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM " + source), source);
        }
        assertEquals(List.of(BOUND), rows("WITH x AS (SELECT * FROM aw) SELECT SYSTEM$TYPEOF(COUNT(*)) FROM x"));
        assertEquals(List.of(BOUND), rows("""
            WITH x AS (SELECT * FROM aw), y AS (SELECT * FROM x WHERE c20_10 > 0)
            SELECT SYSTEM$TYPEOF(COUNT(*)) FROM y"""));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(c20_10)) FROM (SELECT * FROM aw)"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(k)) FROM (SELECT * FROM aw)"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(x)) FROM (SELECT c20_10 AS x FROM aw)"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(k)) FROM awx"));
        assertEquals(List.of(BOUND + ", 5.5000000000"),
            rows("SELECT SYSTEM$TYPEOF(COUNT(*)), MAX(c20_10) FROM (SELECT * FROM aw)"));
        assertEquals(List.of("NUMBER(20,10)[SB8]"), rows("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM (SELECT * FROM aw)"));
        assertEquals(List.of("NUMBER(21,10)[SB8]"), rows("SELECT SYSTEM$TYPEOF(MAX(x)) FROM awx"));
    }

    @Test
    public void aRelationThatFiltersOrReshapesReadsPastTheStatistics() {
        engine.execute("CREATE OR REPLACE VIEW awf AS SELECT * FROM aw WHERE c20_10 > 3");
        engine.execute("CREATE OR REPLACE VIEW awg AS SELECT k, COUNT(*) AS n FROM aw GROUP BY k");
        engine.execute("CREATE OR REPLACE VIEW awx AS SELECT c20_10 + 1 AS x, k FROM aw");
        final String count = "SELECT SYSTEM$TYPEOF(COUNT(*)) FROM ";
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT * FROM aw WHERE c20_10 > 3)"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT * FROM aw) WHERE c20_10 > 3"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT * FROM aw WHERE c20_10 > 0) WHERE c20_10 > 3"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT c20_10 AS x FROM aw) WHERE x > 3"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT * FROM (SELECT * FROM aw WHERE c20_10 > 3))"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT k FROM aw GROUP BY k)"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT DISTINCT k FROM aw)"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT * FROM aw LIMIT 2)"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT TOP 2 * FROM aw)"));
        assertEquals(times(3, UNBOUND), rows(count + "(SELECT * FROM aw ORDER BY k)"));
        assertEquals(times(3, UNBOUND),
            rows(count + "(SELECT * FROM aw QUALIFY ROW_NUMBER() OVER (ORDER BY c20_10) < 9)"));
        assertEquals(times(6, UNBOUND), rows(count + "(SELECT * FROM aw UNION ALL SELECT * FROM aw)"));
        assertEquals(times(6, UNBOUND), rows(count + "(SELECT * FROM aw, bw)"));
        assertEquals(times(3, UNBOUND), rows(count + "(SELECT * FROM aw) WHERE c20_10 IN (1.5, 3.5, 5.5)"));
        assertEquals(times(3, UNBOUND), rows(count + "(SELECT * FROM aw WHERE k IN ('a', 'b'))"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT * FROM nw) WHERE c > 0"));
        assertEquals(times(2, UNBOUND), rows(count + "awf"));
        assertEquals(times(2, UNBOUND), rows(count + "awg"));
        assertEquals(times(2, UNBOUND), rows(count + "awx WHERE x > 3"));
        assertEquals(times(2, UNBOUND),
            rows("WITH x AS (SELECT * FROM aw WHERE c20_10 > 3) SELECT SYSTEM$TYPEOF(COUNT(*)) FROM x"));
        assertEquals(times(2, UNBOUND),
            rows("WITH x AS (SELECT * FROM aw) SELECT SYSTEM$TYPEOF(COUNT(*)) FROM x WHERE c20_10 > 3"));
        assertEquals(List.of(UNBOUND), rows(count + "(SELECT COUNT(*) AS n FROM aw)"));
        assertEquals(List.of(UNBOUND), rows(count + "(SELECT 1 AS x)"));
        assertEquals(times(2, UNBOUND), rows(count + "(SELECT 1 AS x UNION ALL SELECT 2)"));
        assertEquals(times(2, UNBOUND), rows(count + "(VALUES (1), (2))"));
        assertEquals(times(3, UNBOUND), rows(count + "TABLE(GENERATOR(ROWCOUNT => 3))"));
        assertEquals(times(3, UNBOUND), rows(count + "TABLE(FLATTEN(INPUT => [1, 2, 3]))"));
        assertEquals(times(2, "NUMBER(20,10)[SB8]"),
            rows("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM (SELECT * FROM aw WHERE c20_10 > 3)"));
        assertEquals(List.of(UNBOUND + ", 2"),
            rows("SELECT SYSTEM$TYPEOF(COUNT(*)), COUNT(*) FROM (SELECT * FROM aw WHERE c20_10 > 3)"));
        assertEquals(List.of(SUM_TYPE + ", 2"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM (SELECT * FROM aw WHERE c20_10 > 3)"));
        assertEquals(times(3, SUM_TYPE + ", 3"),
            rows("SELECT SYSTEM$TYPEOF(SUM(c20_10)), COUNT(*) FROM (SELECT * FROM aw)"));
    }

    @Test
    public void aCountOverAComputedValueScans() {
        engine.execute("CREATE OR REPLACE VIEW awx AS SELECT c20_10 + 1 AS x, k FROM aw");
        for (final String counted : List.of("c20_10 + 1", "ABS(c20_10)", "c20_10::NUMBER(10,1)", "k || 'x'",
                "c20_10, k")) {
            assertEquals(times(3, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(" + counted + ")) FROM aw"), counted);
        }
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(1)) FROM aw"));
        assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(IFF(TRUE, c20_10, 0))) FROM aw"));
        assertEquals(times(3, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(x)) FROM awx"));
        assertEquals(times(3, UNBOUND),
            rows("SELECT SYSTEM$TYPEOF(COUNT(x)) FROM (SELECT c20_10::NUMBER(10,1) AS x FROM aw)"));
        assertEquals(List.of("NUMBER(21,10)[SB8]"), rows("SELECT SYSTEM$TYPEOF(MIN(c20_10 + 1)) FROM aw"));
        assertEquals(List.of("NUMBER(21,10)[SB8]"), rows("SELECT SYSTEM$TYPEOF(MIN(x)) FROM awx"));
    }

    @Test
    public void anExpressionTheColumnIntervalsSettleIsProven() {
        final List<String> proven = List.of("c20_10 + 1 > 0", "c20_10 * 2 > 0", "-c20_10 < 0", "ABS(c20_10) > 0",
            "c20_10 - 1 >= 0.5", "0 < c20_10 + 1", "c20_10 + c20_10 > 0", "c20_10::NUMBER(10,1) > 0");
        for (final String where : proven) {
            assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE " + where), where);
        }
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 + 1 > 3"));
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM nw WHERE c + 1 > 0"),
            "a column holding a NULL proves nothing");
    }

    @Test
    public void aScalarSubqueryThePlannerFoldsIsAConstant() {
        final List<String> folded = List.of("c20_10 > (SELECT 0)", "c20_10 < (SELECT 10)", "c20_10 > (SELECT 0 + 1)",
            "c20_10 > (SELECT -1)", "c20_10 > ((SELECT 0))", "c20_10 > (SELECT 0)::NUMBER(5,1)",
            "c20_10 > (SELECT 0 WHERE 1 = 1)", "c20_10 > (SELECT x FROM (SELECT 0 AS x))",
            "c20_10 BETWEEN (SELECT 0) AND (SELECT 10)", "(SELECT 1) = 1", "k > (SELECT 'A')", "EXISTS (SELECT 1)",
            "c20_10 > (SELECT MIN(j) FROM bw)", "c20_10 > (SELECT MIN(j) - 1 FROM bw)",
            "c20_10 > (SELECT MIN(j) - 1 FROM bw b)", "c20_10 > (SELECT MIN(bw.j) - 1 FROM bw)",
            "c20_10 > (SELECT COUNT(*) - 2 FROM bw)", "c20_10 > (SELECT COUNT(j) - 2 FROM bw)",
            "c20_10 > (SELECT MIN(c20_10) - 2 FROM aw)", "c20_10 > (SELECT MIN(j) FROM bw WHERE j > 0)",
            "c20_10 > (SELECT MIN(j) FROM bw GROUP BY ALL)", "c20_10 > (SELECT MAX(c) - 5 FROM nw)",
            "c20_10 > (SELECT MIN(j) - 1 FROM bw) AND k > 'A'");
        for (final String where : folded) {
            assertEquals(List.of(BOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE " + where), where);
        }
        final List<String> read = List.of("c20_10 > (SELECT j FROM bw LIMIT 1)",
            "c20_10 > (SELECT 0 FROM bw LIMIT 1)", "c20_10 > (SELECT 0 UNION ALL SELECT 0 LIMIT 1)",
            "c20_10 > (SELECT SUM(j) - 3 FROM bw)", "c20_10 > (SELECT AVG(j) - 3 FROM bw)",
            "c20_10 > (SELECT MAX(j) - 2 FROM bw WHERE j > 1)", "c20_10 > (SELECT MIN(j) FROM (SELECT * FROM bw))",
            "c20_10 > (SELECT MIN(j) FROM bw, aw)", "c20_10 > (SELECT MIN(j) FROM bw HAVING MIN(j) > 0)",
            "c20_10 > (SELECT MIN(j) FROM bw GROUP BY j LIMIT 1)", "EXISTS (SELECT 1 FROM bw)");
        for (final String where : read) {
            assertEquals(times(3, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE " + where), where);
        }
        assertEquals(times(2, UNBOUND), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > (SELECT 3)"));
        assertEquals(times(2, UNBOUND),
            rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > (SELECT MIN(j) FROM bw WHERE j > 1)"));
        assertEquals(List.of(), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > (SELECT NULL)"));
        assertEquals(List.of(), rows("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE NOT EXISTS (SELECT 1)"));
    }
}
