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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MEDIAN, PERCENTILE_CONT and PERCENTILE_DISC over a VARCHAR or VARIANT argument. Live neither reads
 * the text's decimals nor orders it as numbers: every value is converted to a WHOLE number —
 * NUMBER(9,0), whatever length the column declares — rounded half away from zero, and the inputs are
 * ordered by their OWN type, text as text and a VARIANT member by its kind, before the percentile is
 * taken between the converted neighbours. One value that spells no number, or rounds past nine digits,
 * refuses the whole set whichever position it would take. The interpolating pair declares NUMBER(12,3)
 * and the picking one NUMBER(9,0). Live-verified cell by cell.
 */
public class MedianOverTextTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE mt (k VARCHAR(5), t VARCHAR(12))");
        engine.execute("INSERT INTO mt SELECT 'dec', '1.5' UNION ALL SELECT 'dec', '2.25'");
        engine.execute("INSERT INTO mt SELECT 'half', '2.5' UNION ALL SELECT 'half', '3.5'");
        engine.execute("INSERT INTO mt SELECT 'near', '1.4' UNION ALL SELECT 'near', '1.6'");
        engine.execute("INSERT INTO mt SELECT 'o3', '10' UNION ALL SELECT 'o3', '9' UNION ALL SELECT 'o3', '8'");
        engine.execute("INSERT INTO mt SELECT 'o4', '10' UNION ALL SELECT 'o4', '9' UNION ALL SELECT 'o4', '8' UNION ALL SELECT 'o4', '7'");
        engine.execute("INSERT INTO mt SELECT 'neg', '-1.5' UNION ALL SELECT 'neg', '-2.5' UNION ALL SELECT 'neg', '0.5'");
        engine.execute("INSERT INTO mt SELECT 'lead', '01' UNION ALL SELECT 'lead', '1' UNION ALL SELECT 'lead', '+2' UNION ALL SELECT 'lead', '2.50'");
        engine.execute("INSERT INTO mt SELECT 'ws', ' 1' UNION ALL SELECT 'ws', '2 '");
        engine.execute("INSERT INTO mt SELECT 'nul', '3' UNION ALL SELECT 'nul', NULL UNION ALL SELECT 'nul', '1'");
        engine.execute("INSERT INTO mt SELECT 'bad', '1.5' UNION ALL SELECT 'bad', 'x'");
        engine.execute("INSERT INTO mt SELECT 'big', '1' UNION ALL SELECT 'big', '1234567890'");
        engine.execute("INSERT INTO mt SELECT 'edge', '999999999.4' UNION ALL SELECT 'edge', '0.5000000001'");
        engine.execute("INSERT INTO mt SELECT 'over', '999999999.5' UNION ALL SELECT 'over', '1'");
        engine.execute("INSERT INTO mt SELECT 'empty', '' UNION ALL SELECT 'empty', '1'");
        engine.execute("CREATE OR REPLACE TABLE mv (k VARCHAR(5), v VARIANT)");
        engine.execute("INSERT INTO mv SELECT 'str', PARSE_JSON('\"1.5\"') UNION ALL SELECT 'str', PARSE_JSON('\"2.25\"')");
        engine.execute("INSERT INTO mv SELECT 'num', PARSE_JSON('1.5') UNION ALL SELECT 'num', PARSE_JSON('2.25')");
        engine.execute("INSERT INTO mv SELECT 'n3', PARSE_JSON('10') UNION ALL SELECT 'n3', PARSE_JSON('9') UNION ALL SELECT 'n3', PARSE_JSON('8')");
        engine.execute("INSERT INTO mv SELECT 's3', PARSE_JSON('\"10\"') UNION ALL SELECT 's3', PARSE_JSON('\"9\"') UNION ALL SELECT 's3', PARSE_JSON('\"8\"')");
    }

    private String cells(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                for (int i = 0; i < rs.getColumns().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(String.valueOf(rs.getValue(i)));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /**
     * A declared-type cell: live folds {@code SYSTEM$TYPEOF} over an aggregate at compile time and
     * answers it once per INPUT row, so repeated rows collapse to the one type they all name.
     */
    private String declared(final String sql) {
        final String rows = cells(sql);
        final StringBuilder out = new StringBuilder();
        String previous = null;
        for (final String row : rows.split(" \\| ")) {
            if (!row.equals(previous)) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                out.append(row);
            }
            previous = row;
        }
        return out.toString();
    }

    private String median(final String key) {
        return cells("SELECT MEDIAN(t) FROM mt WHERE k = '" + key + "'");
    }

    private String percentileCont(final String fraction, final String key) {
        return cells("SELECT PERCENTILE_CONT(" + fraction + ") WITHIN GROUP (ORDER BY t) FROM mt WHERE k = '" + key + "'");
    }

    private String percentileDisc(final String fraction, final String key) {
        return cells("SELECT PERCENTILE_DISC(" + fraction + ") WITHIN GROUP (ORDER BY t) FROM mt WHERE k = '" + key + "'");
    }

    @Test
    void everyValueBecomesAWholeNumberFirst() {
        assertEquals("2.000", median("dec"));
        assertEquals("3.500", median("half"));
        assertEquals("1.500", median("near"));
        assertEquals("1.000", median("lead"));
        assertEquals("1.500", median("ws"));
        assertEquals("2.000", median("nul"));
        assertEquals("500000000.000", median("edge"));
        assertEquals("2.000", cells("SELECT MEDIAN(t::VARCHAR(50)) FROM mt WHERE k = 'dec'"));
        assertEquals("2.000", cells("SELECT MEDIAN(t) FROM (SELECT '1.5' AS t UNION ALL SELECT '2.25')"));
        assertEquals("2.000", cells("SELECT MEDIAN('1.5')"));
        assertEquals("dec, 2.000 | half, 3.500",
            cells("SELECT k, MEDIAN(t) FROM mt WHERE k IN ('dec', 'half') GROUP BY k ORDER BY k"));
        assertEquals("2.000 | 2.000", cells("SELECT MEDIAN(t) OVER () FROM mt WHERE k = 'dec'"));
        assertEquals("2.000", percentileCont("0.5", "dec"));
        assertEquals("2", percentileDisc("0.5", "dec"));
        assertEquals("3", percentileDisc("0.5", "half"));
    }

    @Test
    void theOrderIsTheTextsOwn() {
        assertEquals("8.000", median("o3"));
        assertEquals("7.500", median("o4"));
        assertEquals("-3.000", median("neg"));
        assertEquals("7.750", percentileCont("0.25", "o4"));
        assertEquals("8.000", cells(
            "SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY t DESC) FROM mt WHERE k = 'o3'"));
        assertEquals("8", percentileDisc("0.5", "o3"));
        assertEquals("10", percentileDisc("0.25", "o4"));
        assertEquals("-3", percentileDisc("0.5", "neg"));
        assertEquals("2", percentileDisc("0", "lead"));
        assertEquals("3", percentileDisc("1", "lead"));
        assertEquals("9.000", cells("SELECT MEDIAN(t::NUMBER) FROM mt WHERE k = 'o3'"));
    }

    @Test
    void variantMembersOrderByTheirKind() {
        assertEquals("2.000", cells("SELECT MEDIAN(v) FROM mv WHERE k = 'str'"));
        assertEquals("2.000", cells("SELECT MEDIAN(v) FROM mv WHERE k = 'num'"));
        assertEquals("9.000", cells("SELECT MEDIAN(v) FROM mv WHERE k = 'n3'"));
        assertEquals("8.000", cells("SELECT MEDIAN(v) FROM mv WHERE k = 's3'"));
        assertEquals("2", cells("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY v) FROM mv WHERE k = 'str'"));
        assertEquals("9", cells("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY v) FROM mv WHERE k = 'n3'"));
        assertEquals("8", cells("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY v) FROM mv WHERE k = 's3'"));
    }

    @Test
    void oneUnconvertibleValueRefusesTheSet() {
        assertEquals("REFUSED Numeric value 'x' is not recognized", median("bad"));
        assertEquals("REFUSED Numeric value 'x' is not recognized", percentileDisc("0", "bad"));
        assertEquals("REFUSED Numeric value 'x' is not recognized",
            cells("SELECT MEDIAN(t) OVER () FROM mt WHERE k = 'bad'"));
        assertEquals("REFUSED Numeric value '1234567890' is out of range", median("big"));
        assertEquals("REFUSED Numeric value '999999999.5' is out of range", median("over"));
        assertEquals("REFUSED Numeric value '999999999.5' is out of range", percentileDisc("0", "over"));
        assertEquals("REFUSED Numeric value '' is not recognized", median("empty"));
    }

    @Test
    void declaredWidthsAreTheConversionsNotTheColumns() {
        assertEquals("NUMBER(12,3)", declared("SELECT SPLIT_PART(SYSTEM$TYPEOF(MEDIAN(t)), '[', 1) FROM mt WHERE k = 'dec'"));
        assertEquals("NUMBER(12,3)", declared("SELECT SPLIT_PART(SYSTEM$TYPEOF(MEDIAN(t::VARCHAR(50))), '[', 1) FROM mt WHERE k = 'dec'"));
        assertEquals("NUMBER(12,3)", declared(
            "SELECT SPLIT_PART(SYSTEM$TYPEOF(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY t)), '[', 1) FROM mt WHERE k = 'dec'"));
        assertEquals("NUMBER(9,0)", declared(
            "SELECT SPLIT_PART(SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY t)), '[', 1) FROM mt WHERE k = 'dec'"));
        assertEquals("NUMBER(12,3)", declared("SELECT SPLIT_PART(SYSTEM$TYPEOF(MEDIAN(v)), '[', 1) FROM mv WHERE k = 'num'"));
        assertEquals("NUMBER(9,0)", declared(
            "SELECT SPLIT_PART(SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY v)), '[', 1) FROM mv WHERE k = 'str'"));
        assertEquals("VARCHAR(12)", declared("SELECT SPLIT_PART(SYSTEM$TYPEOF(MODE(t)), '[', 1) FROM mt WHERE k = 'dec'"));
        assertEquals("null, NUMBER(12,3)", cells(
            "SELECT MEDIAN(t), SPLIT_PART(SYSTEM$TYPEOF(MEDIAN(t)), '[', 1) FROM mt WHERE FALSE"));
    }
}
