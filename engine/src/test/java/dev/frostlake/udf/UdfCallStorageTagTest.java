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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL function whose body is an expression is read as if its call were inlined, so SYSTEM$TYPEOF tags
 * the call by the interval the body produces over its arguments' own: a literal argument brings its value
 * (a text or FLOAT constant converted into the parameter folds to one), a column argument its table's
 * statistics, and a body without an interval rule keeps its declared width. The plan's shape rules read
 * the inlined body too: SUM over a call whose body shifts a column by a constant is rewritten as SUM over
 * the shifted column is, reporting the wider intermediate and tagged by its whole width, and MIN or MAX
 * over such a call is answered from the statistics in one row. Every cell is live-verified.
 */
public class UdfCallStorageTagTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRoutines() {
        engine.execute("CREATE FUNCTION num_p(a NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'a'");
        engine.execute("CREATE FUNCTION inc_p(a NUMBER) RETURNS NUMBER AS 'a + 1'");
        engine.execute("CREATE FUNCTION mul_p(a NUMBER(10,2), b NUMBER(10,2)) RETURNS NUMBER(20,4) AS 'a * b'");
        engine.execute("CREATE FUNCTION const_p() RETURNS NUMBER AS '42'");
        engine.execute("CREATE FUNCTION case_p(a NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'CASE WHEN a > 0 THEN a ELSE 1000000 END'");
        engine.execute("CREATE FUNCTION txt_p(a VARCHAR) RETURNS NUMBER AS 'a::NUMBER'");
        engine.execute("CREATE FUNCTION round_p(a NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'ROUND(a)'");
        engine.execute("CREATE FUNCTION small_p(a NUMBER(3,1)) RETURNS NUMBER(3,1) AS 'a'");
        engine.execute("CREATE FUNCTION two_p(a NUMBER, b NUMBER) RETURNS NUMBER AS 'a + b'");
        engine.execute("CREATE FUNCTION big_p() RETURNS NUMBER AS '3000000000'");
        engine.execute("CREATE FUNCTION id_p(a NUMBER) RETURNS NUMBER AS 'a'");
        engine.execute("CREATE FUNCTION inc2_p(a NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'a + 1'");
        engine.execute("CREATE FUNCTION add_p(a NUMBER, b NUMBER) RETURNS NUMBER AS 'a + b'");
        engine.execute("CREATE TABLE t (n NUMBER(5,2), n12 NUMBER(12,2), n50 NUMBER(5,0), big NUMBER(38,0), g NUMBER(1,0), one NUMBER(5,2))");
        engine.execute("INSERT INTO t VALUES (1.5, 1.25, 7, 5, 1, 4.5), (2.5, 300.00, 40000, 3000000000, 2, 4.5)");
    }

    /** Every row's first cell, joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(row.getValue(0));
        }
        return answer.toString();
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aCallIsTaggedByItsInlinedBody() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(num_p(1))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(num_p(1.25))", "NUMBER(3,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(num_p('5'))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(NULL))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(1.5))", "NUMBER(4,1)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(num_p(1)))", "NUMBER(11,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(inc_p('5'))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n)) FROM t", "NUMBER(5,2)[SB2] | NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n12)) FROM t", "NUMBER(12,2)[SB2] | NUMBER(12,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n50)) FROM t", "NUMBER(10,2)[SB4] | NUMBER(10,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(n)) FROM t", "NUMBER(6,2)[SB2] | NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(big)) FROM t", "NUMBER(38,0)[SB8] | NUMBER(38,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(n50)) FROM t", "NUMBER(6,0)[SB4] | NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(mul_p(n, n12)) FROM t", "NUMBER(17,4)[SB4] | NUMBER(17,4)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n) + 1) FROM t", "NUMBER(6,2)[SB2] | NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n * 2)) FROM t", "NUMBER(6,2)[SB2] | NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(inc_p(n))) FROM t", "NUMBER(6,2)[SB2] | NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n)) FROM t WHERE n > 2", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(const_p())", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(case_p(n)) FROM t", "NUMBER(9,2)[SB2] | NUMBER(9,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(case_p(-n)) FROM t", "NUMBER(9,2)[SB4] | NUMBER(9,2)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(round_p(n)) FROM t", "NUMBER(6,0)[SB4] | NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(small_p(12.34))", "NUMBER(4,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(small_p(n)) FROM t", "NUMBER(5,2)[SB2] | NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(two_p(1, 2))", "NUMBER(2,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(two_p(n, big)) FROM t", "NUMBER(38,2)[SB8] | NUMBER(38,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(big_p())", "NUMBER(10,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(1) * 1000)", "NUMBER(6,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(99999999.99))", "NUMBER(10,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(9223372036854775807))", "NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(two_p(1, NULL))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(txt_p(n::VARCHAR)) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(SUM(n))) FROM t", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n))) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(num_p(n)) FROM (SELECT n FROM t)", "NUMBER(5,2)[SB2] | NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT num_p(n) x FROM t)", "NUMBER(5,2)[SB2] | NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(1.5::FLOAT))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(num_p(1.5::FLOAT))", "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(TO_VARIANT(5)))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(small_p(n12)) FROM t WHERE n12 < 2", "NUMBER(12,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(case_p(1))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(case_p(-1))", "NUMBER(10,2)[SB4]"},
        });
    }

    @Test
    public void anAggregateOverACallReadsTheInlinedBody() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n))) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(num_p(n))) FROM t", "NUMBER(17,2)[SB8] | NUMBER(17,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(MAX(inc_p(n))) FROM t", "NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(AVG(inc_p(n))) FROM t", "NUMBER(24,8)[SB16] | NUMBER(24,8)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(two_p(n, n))) FROM t", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n50))) FROM t", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(id_p(n))) FROM t", "NUMBER(17,2)[SB8] | NUMBER(17,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(1))) FROM t", "NUMBER(14,0)[SB8] | NUMBER(14,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(MIN(num_p(n))) FROM t", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(SUM(mul_p(n, n12))) FROM t", "NUMBER(29,4)[SB8] | NUMBER(29,4)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n))) FROM t GROUP BY g", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(n)) FROM t GROUP BY n", "NUMBER(6,2)[SB2] | NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(COUNT(inc_p(n))) FROM t", "NUMBER(18,0)[SB8] | NUMBER(18,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n)) + 0) FROM t", "NUMBER(19,2)[SB16] | NUMBER(19,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(id_p(n50))) FROM t", "NUMBER(17,0)[SB8] | NUMBER(17,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(id_p(big))) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(id_p(g))) FROM t", "NUMBER(13,0)[SB8] | NUMBER(13,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(num_p(g))) FROM t", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(round_p(1.25))", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(round_p(99.99))", "NUMBER(5,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(LENGTH('abc')))", "NUMBER(19,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(inc_p(ROUND(1.5)))", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(num_p(ABS(-1)))", "NUMBER(10,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(ROUND(1.25))", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(LENGTH('abc') + 1)", "NUMBER(19,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF('5'::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(1.5::FLOAT::NUMBER + 1)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(('5' || '0')::NUMBER)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT(5)::NUMBER + 1)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(round_p(n)) FROM t WHERE n < 2", "NUMBER(6,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(txt_p(TO_VARCHAR(g))) FROM t", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
        });
    }

    @Test
    public void aSumOverAShiftedColumnReadsTheInlinedBody() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1) + 0) FROM t", "NUMBER(19,2)[SB16] | NUMBER(19,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n50 + 1) + 0) FROM t", "NUMBER(19,0)[SB16] | NUMBER(19,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n50 + 1)) FROM t", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(one + 1)) FROM t", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n - 1)) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(SUM(n + 1), 0)) FROM t", "NUMBER(18,2)[SB16] | NUMBER(18,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n)) * 1) FROM t", "NUMBER(19,2)[SB16] | NUMBER(19,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(id_p(n) + 1)) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(num_p(n) + 1)) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc2_p(n))) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(add_p(n, 1))) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(add_p(1, n))) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(one))) FROM t", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc_p(n50))) FROM t", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(inc2_p(n50))) FROM t", "NUMBER(23,2)[SB8] | NUMBER(23,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t GROUP BY one", "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1) + SUM(n)) FROM t", "NUMBER(19,2)[SB16] | NUMBER(19,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(MAX(n + 1)) FROM t", "NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(MAX(inc_p(n))) FROM t", "NUMBER(6,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(MIN(n)) FROM t", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(MAX(id_p(n))) FROM t", "NUMBER(5,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t WHERE n > 0", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n) + 0) FROM t", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
        });
    }
}
