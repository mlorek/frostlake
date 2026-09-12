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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inside OVER and WITHIN GROUP a number is the constant it spells, never a position in the select list: a
 * constant PARTITION BY key is one partition, a constant ORDER BY key makes every row a peer, and a constant
 * argument to LAG, FIRST_VALUE, NTH_VALUE or a windowed aggregate is that constant. The ordinal reading
 * belongs to the query's own ORDER BY and GROUP BY alone, and {@code $1} still names the first column. Every
 * cell is live-verified, and none depends on the order the rows arrive in.
 */
public class ConstantWindowKeyTest extends BaseDatabaseTest {

    private static final String THREES = "1.50, 3 | 2.50, 3 | 3.50, 3";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE wf (n NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO wf VALUES (1.5, 'a'), (2.5, 'b'), (3.5, 'b')");
        engine.execute("CREATE TABLE wo (n NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO wo VALUES (3.5, 'c'), (1.5, 'a'), (2.5, 'b')");
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(c));
            }
        }
        return out.toString();
    }

    /** The second column of every row as a number, whatever form a FLOAT arrives in. */
    private List<Double> secondColumnNumbers(final String sql) {
        final List<Double> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(Double.valueOf(String.valueOf(row.getValue(1))));
        }
        return out;
    }

    @Test
    public void aConstantPartitionKeyIsOnePartition() {
        for (final String key : List.of("1", "2", "9", "'x'", "-1", "1.5", "NULL", "(1)", "1::NUMBER")) {
            assertEquals(THREES, rows("SELECT n, COUNT(*) OVER (PARTITION BY " + key + ") FROM wf ORDER BY n"), key);
        }
        assertEquals("1.50, 1 | 2.50, 2 | 3.50, 2", rows("SELECT n, COUNT(*) OVER (PARTITION BY 1, s) FROM wf ORDER BY n"));
        assertEquals("1.50, 7.50 | 2.50, 7.50 | 3.50, 7.50", rows("SELECT n, SUM(n) OVER (PARTITION BY 1) FROM wf ORDER BY n"));
        assertEquals("1.50, 1.50 | 2.50, 1.50 | 3.50, 1.50",
            rows("SELECT n, FIRST_VALUE(n) OVER (PARTITION BY 1 ORDER BY n) FROM wf ORDER BY n"));
        assertEquals("1.50, 3 | 2.50, 2 | 3.50, 1",
            rows("SELECT n, ROW_NUMBER() OVER (PARTITION BY 1 ORDER BY n DESC) FROM wf ORDER BY n"));
        assertEquals("a, 2 | b, 2", rows("SELECT s, COUNT(*) OVER (PARTITION BY 1) FROM wf GROUP BY s ORDER BY s"));
        assertEquals("1.50 | 2.50 | 3.50", rows("SELECT n FROM wf QUALIFY COUNT(*) OVER (PARTITION BY 1) = 3 ORDER BY n"));
        assertEquals("1.50 | 2.50 | 3.50", rows("SELECT n FROM wf ORDER BY COUNT(*) OVER (PARTITION BY 2), n"));
        assertEquals("1.50, 1 | 2.50, 1 | 3.50, 1", rows("SELECT n, COUNT(*) OVER (PARTITION BY $1) FROM wf ORDER BY n"));
    }

    @Test
    public void aConstantOrderKeyMakesEveryRowAPeer() {
        assertEquals("1.50, 1 | 2.50, 1 | 3.50, 1", rows("SELECT n, RANK() OVER (ORDER BY 1) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 1 | 3.50, 1", rows("SELECT n, DENSE_RANK() OVER (ORDER BY 2) FROM wf ORDER BY n"));
        assertEquals(List.of(0.0, 0.0, 0.0), secondColumnNumbers("SELECT n, PERCENT_RANK() OVER (ORDER BY 1) FROM wf ORDER BY n"));
        assertEquals(List.of(1.0, 1.0, 1.0), secondColumnNumbers("SELECT n, CUME_DIST() OVER (ORDER BY 1) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 2 | 3.50, 2",
            rows("SELECT n, COUNT(*) OVER (PARTITION BY s ORDER BY 1) FROM wf ORDER BY n"));
        assertEquals(THREES, rows("SELECT n, COUNT(*) OVER (PARTITION BY 1 ORDER BY 1) FROM wf ORDER BY n"));
        assertEquals("1.50, 7.50 | 2.50, 7.50 | 3.50, 7.50", rows("SELECT n, SUM(n) OVER (ORDER BY 1) FROM wf ORDER BY n"));
        assertEquals("1.50, 7.50 | 2.50, 7.50 | 3.50, 7.50", rows("""
            SELECT n, SUM(n) OVER (ORDER BY 1 RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
            FROM wf ORDER BY n"""));
        assertEquals("1.50, 7.50 | 2.50, 7.50 | 3.50, 7.50", rows("""
            SELECT n, SUM(n) OVER (ORDER BY 1 ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
            FROM wf ORDER BY n"""));
        assertEquals("1.50, 1 | 2.50, 2 | 3.50, 3", rows("SELECT n, ROW_NUMBER() OVER (ORDER BY 1, n) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 2 | 3.50, 3",
            rows("SELECT n, ROW_NUMBER() OVER (ORDER BY 2 DESC, n) FROM wf ORDER BY n"));
        assertEquals("1.50, 1.50 | 2.50, 1.50 | 3.50, 1.50",
            rows("SELECT n, FIRST_VALUE(n) OVER (ORDER BY 1 DESC, n) FROM wf ORDER BY n"));
        assertEquals("1.50, null | 2.50, 1.50 | 3.50, 2.50", rows("SELECT n, LAG(n) OVER (ORDER BY 2, n) FROM wf ORDER BY n"));
        assertEquals("a, 1 | b, 1", rows("SELECT s, DENSE_RANK() OVER (ORDER BY 1) FROM wf GROUP BY s ORDER BY s"));
    }

    @Test
    public void aConstantArgumentIsThatConstant() {
        assertEquals("1.50, 2 | 2.50, 2 | 3.50, 2", rows("SELECT n, FIRST_VALUE(2) OVER (ORDER BY n) FROM wf ORDER BY n"));
        assertEquals("1.50, null | 2.50, 1 | 3.50, 1", rows("SELECT n, LAG(1) OVER (ORDER BY n) FROM wf ORDER BY n"));
        assertEquals("1.50, 2 | 2.50, 2 | 3.50, 0", rows("SELECT n, LEAD(2, 1, 0) OVER (ORDER BY n) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 1 | 3.50, 1", rows("SELECT n, NTH_VALUE(1, 1) OVER (ORDER BY n) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 1 | 3.50, 1", rows("SELECT n, LAST_VALUE(1) OVER (ORDER BY n) FROM wf ORDER BY n"));
        assertEquals(THREES, rows("SELECT n, SUM(1) OVER () FROM wf ORDER BY n"));
        assertEquals("1.50, 2 | 2.50, 4 | 3.50, 6", rows("SELECT n, SUM(2) OVER (ORDER BY n) FROM wf ORDER BY n"));
        assertEquals("1.50, 2 | 2.50, 2 | 3.50, 2", rows("SELECT n, MAX(2) OVER (PARTITION BY s) FROM wf ORDER BY n"));
        assertEquals("1.50, 2 | 2.50, 2 | 3.50, 2",
            rows("SELECT n, MIN(2) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 2 | 3.50, 2", rows("SELECT n, COUNT(1) OVER (PARTITION BY s) FROM wf ORDER BY n"));
        assertEquals("1.50, 1 | 2.50, 2 | 3.50, 3",
            rows("SELECT n, COUNT(2) OVER (ORDER BY n ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM wf ORDER BY n"));
        assertEquals("1.50, 2.000 | 2.50, 2.000 | 3.50, 2.000", rows("SELECT n, AVG(2) OVER () FROM wf ORDER BY n"));
        assertEquals("1.50, 1,1,1 | 2.50, 1,1,1 | 3.50, 1,1,1", rows("SELECT n, LISTAGG(1, ',') OVER () FROM wf ORDER BY n"));
        assertEquals("1.50, [2,2,2] | 2.50, [2,2,2] | 3.50, [2,2,2]", rows("SELECT n, ARRAY_AGG(2) OVER () FROM wf ORDER BY n"));
        assertEquals("1.50, 0.333333 | 2.50, 0.333333 | 3.50, 0.333333",
            rows("SELECT n, RATIO_TO_REPORT(1) OVER () FROM wf ORDER BY n"));
    }

    @Test
    public void withinGroupReadsTheConstantToo() {
        assertEquals("1.000", rows("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY 1) FROM wo"));
        assertEquals("2", rows("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY 2) FROM wo"));
        assertEquals("1.50, 1.000 | 2.50, 1.000 | 3.50, 1.000",
            rows("SELECT n, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY 1) OVER () FROM wo ORDER BY n"));
        assertEquals("[\"a\",\"b\",\"c\"]", rows("SELECT ARRAY_AGG(s) WITHIN GROUP (ORDER BY 1, s) FROM wo"));
        assertEquals("a,b,c", rows("SELECT LISTAGG(s, ',') WITHIN GROUP (ORDER BY s) FROM wo"));
    }

    @Test
    public void theQuerysOwnOrdinalsStillCount() {
        assertEquals("a, 1.50 | b, 2.50 | b, 3.50", rows("SELECT s, n FROM wf ORDER BY 1, 2"));
        assertEquals("a, 1 | b, 2", rows("SELECT s, COUNT(*) FROM wf GROUP BY 1 ORDER BY 1"));
    }
}
