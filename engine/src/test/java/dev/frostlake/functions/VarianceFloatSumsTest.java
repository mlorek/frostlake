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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The variance family over FLOATs reads the account's sums: {@code (n·Σx² − (Σx)²) / (n·(n − 1))} and
 * the same numerator over {@code n²}, each sum added with Kahan compensation and read back with its last
 * compensation applied. Every digit below is live-verified through a thirty-seventh-decimal cast, over
 * GENERATOR rows, UNION ALL branches, a GROUP BY group and a window; an exact NUMBER input keeps its
 * own path.
 */
public class VarianceFloatSumsTest extends BaseDatabaseTest {

    private static final String ZERO37 = "0.0000000000000000000000000000000000000";

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String copies(final String function, final String value, final int count) {
        return rows("SELECT TO_VARCHAR(" + function + "(x)::NUMBER(38,37)) FROM (SELECT " + value
            + "::FLOAT AS x FROM TABLE(GENERATOR(ROWCOUNT => " + count + ")))");
    }

    private String union(final String function, final String scale, final String... values) {
        final StringBuilder source = new StringBuilder();
        for (final String value : values) {
            if (source.length() == 0) {
                source.append("SELECT ").append(value).append("::FLOAT AS x");
            } else {
                source.append(" UNION ALL SELECT ").append(value).append("::FLOAT");
            }
        }
        return rows("SELECT TO_VARCHAR(" + function + "(x)::NUMBER(38," + scale + ")) FROM (" + source + ")");
    }

    /** Identical values leave the sums' rounding behind, where a running mean would give zero. */
    @Test
    public void identicalValuesKeepTheSumsRounding() {
        assertEquals("0.0000000000000000024671622769447924062", copies("VARIANCE", "0.1", 10));
        assertEquals("0.0000000000000000022204460492503131271", copies("VAR_POP", "0.1", 10));
        assertEquals("0.0000000000000000027755575615628915051", copies("VARIANCE", "0.1", 5));
        assertEquals("0.0000000000000000030839528461809902189", copies("VARIANCE", "0.1", 9));
        assertEquals("0.0000000000000000023373116307898031903", copies("VARIANCE", "0.1", 20));
        assertEquals("0.0000000000000000028708797404448492334", copies("VARIANCE", "0.1", 100));
        assertEquals("0.0000000000000000018208102137596159744", copies("VARIANCE", "0.1", 1000));
        assertEquals("0.0000000000000001480297366166875305058", copies("VARIANCE", "0.7", 3));
        assertEquals(ZERO37, copies("VARIANCE", "0.1", 2));
        assertEquals(ZERO37, copies("VARIANCE", "0.1", 7));
        assertEquals(ZERO37, copies("VARIANCE", "0.3", 10));
        assertEquals(ZERO37, copies("VARIANCE", "0.7", 10));
    }

    /** The squares cancel at the scale of the values, where a running mean would not. */
    @Test
    public void largeValuesCancelInTheSquares() {
        assertEquals("0.000000000000000000000000000000", union("VARIANCE", "30", "1e8", "(1e8 + 1)", "(1e8 + 2)"));
        assertEquals("0.000000000000000000000000000000",
            union("VARIANCE", "30", "1e8", "(1e8 + 0.1)", "(1e8 + 0.2)"));
        assertEquals("8.000000000000000000000000000000", union("VARIANCE", "30", "1e8", "(1e8 + 3)", "(1e8 + 6)"));
        assertEquals("101.333333333333328596381761599332",
            union("VARIANCE", "30", "1e8", "(1e8 + 10)", "(1e8 + 20)"));
        assertEquals("0.009999990463256835937500000000", union("VARIANCE", "30", "1e4", "(1e4 + 0.1)", "(1e4 + 0.2)"));
        assertEquals("0.009765625000000000000000000000", union("VARIANCE", "30", "1e6", "(1e6 + 0.1)", "(1e6 + 0.2)"));
        assertEquals("0.006510416666666666955787245996", union("VAR_POP", "30", "1e6", "(1e6 + 0.1)", "(1e6 + 0.2)"));
    }

    /** Short series, the order they arrive in included. */
    @Test
    public void shortSeriesReadTheSumsInOrder() {
        assertEquals("0.0049999999999999975019981945933977840", union("VARIANCE", "37", "0.1", "0.2"));
        assertEquals("0.0024999999999999987509990972966988920", union("VAR_POP", "37", "0.1", "0.2"));
        assertEquals("0.0049999999999999975019981945933977840", union("VARIANCE", "37", "0.2", "0.1"));
        assertEquals("0.0799999999999998490096686509787105024", union("VARIANCE", "37", "0.3", "0.7"));
        assertEquals("0.0799999999999999600319711134943645447", union("VARIANCE", "37", "0.7", "0.3"));
        assertEquals("0.0200000000000000177635683940025046468", union("VARIANCE", "37", "0.1", "0.3"));
        assertEquals("0.1799999999999998268052081584755796939", union("VARIANCE", "37", "0.3", "0.9"));
        assertEquals("0.0233333333333333343972970652657750179", union("VARIANCE", "37", "0.4", "0.2", "0.1"));
        assertEquals("0.0155555555555555568431058688361190434", union("VAR_POP", "37", "0.4", "0.2", "0.1"));
        assertEquals("0.0100000000000000088817841970012523234", union("VARIANCE", "37", "0.1", "0.2", "0.3"));
        assertEquals("0.0033333333333333361493677760023501833", union("VARIANCE", "37", "0.1", "0.1", "0.2"));
        assertEquals("0.0033333333333333456903468938747892025", union("VARIANCE", "37", "0.1", "0.2", "0.2"));
        assertEquals("0.0399999999999998898103648059532133630", union("VARIANCE", "37", "0.3", "0.7", "0.5"));
        assertEquals(ZERO37, union("VARIANCE", "37", "0.1", "0.1"));
        assertEquals("0.5000000000000000000000000000000000000", union("VARIANCE", "37", "1", "2"));
    }

    /** The standard deviations are the roots of those variances. */
    @Test
    public void theStandardDeviationsAreTheirRoots() {
        assertEquals("0.0707106781186547378448281619967019651", union("STDDEV", "37", "0.1", "0.2"));
        assertEquals("0.0499999999999999888977697537484345958", union("STDDEV_POP", "37", "0.1", "0.2"));
    }

    /** Longer series, a group and a window read the same sums. */
    @Test
    public void everyShapeReadsTheSameSums() {
        assertEquals("0.091666666666666660190365689687", rows("SELECT TO_VARCHAR(VARIANCE(x)::NUMBER(38,30)) "
            + "FROM (SELECT (SEQ4() / 10)::FLOAT AS x FROM TABLE(GENERATOR(ROWCOUNT => 10)))"));
        assertEquals("0.082500000000000003885780586188", rows("SELECT TO_VARCHAR(VAR_POP(x)::NUMBER(38,30)) "
            + "FROM (SELECT (SEQ4() / 10)::FLOAT AS x FROM TABLE(GENERATOR(ROWCOUNT => 10)))"));
        assertEquals("7.415416666666666323237677715952", rows("SELECT TO_VARCHAR(VARIANCE(x)::NUMBER(38,30)) "
            + "FROM (SELECT (SEQ4() * 0.37 + 1e6)::FLOAT AS x FROM TABLE(GENERATOR(ROWCOUNT => 25)))"));
        assertEquals("1, 0.0049999999999999975019981945933977840 | 2, 0.0799999999999998490096686509787105024",
            rows("SELECT k, TO_VARCHAR(VARIANCE(x)::NUMBER(38,37)) FROM (SELECT 1 AS k, 0.1::FLOAT AS x "
                + "UNION ALL SELECT 1, 0.2::FLOAT UNION ALL SELECT 2, 0.3::FLOAT UNION ALL SELECT 2, 0.7::FLOAT) "
                + "GROUP BY k ORDER BY k"));
        assertEquals("0.0049999999999999975019981945933977840", rows("SELECT TO_VARCHAR((VARIANCE(x) OVER ())"
            + "::NUMBER(38,37)) FROM (SELECT 0.1::FLOAT AS x UNION ALL SELECT 0.2::FLOAT) LIMIT 1"));
        engine.execute("CREATE OR REPLACE TABLE vb (x FLOAT)");
        engine.execute("INSERT INTO vb VALUES (1e8), (1e8 + 0.1), (1e8 + 0.2)");
        assertEquals("0.000000000000000000000000000000", rows("SELECT TO_VARCHAR(VARIANCE(x)::NUMBER(38,30)) FROM vb"));
        engine.execute("CREATE OR REPLACE TABLE v2 (x FLOAT)");
        engine.execute("INSERT INTO v2 VALUES (0.1), (0.2)");
        assertEquals("0.0049999999999999975019981945933977840",
            rows("SELECT TO_VARCHAR(VARIANCE(x)::NUMBER(38,37)) FROM v2"));
    }

    /** An exact NUMBER input keeps its own path: no cancellation, and its declared scale. */
    @Test
    public void anExactInputIsNotReadThroughTheSums() {
        assertEquals("1.000000, 0.666667", rows("SELECT VARIANCE(x), VAR_POP(x) FROM "
            + "(SELECT 100000000 AS x UNION ALL SELECT 100000001 UNION ALL SELECT 100000002)"));
        assertEquals("0.01000000, 0.00666667", rows("SELECT VARIANCE(x), VAR_POP(x) FROM "
            + "(SELECT 100000000.1 AS x UNION ALL SELECT 100000000.2 UNION ALL SELECT 100000000.3)"));
    }
}
