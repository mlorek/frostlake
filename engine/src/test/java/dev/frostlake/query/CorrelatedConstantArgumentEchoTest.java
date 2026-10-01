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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A subquery's refusal that re-prints its plan names a column of the query around it as a correlation,
 * {@code CORRELATION(RT.N)} — bare or qualified, through an alias, inside an operator, a conversion or an
 * aggregate the outer query computes — in the constant-argument and the arity sentences alike, over an empty
 * table as over a full one. Each expected answer is the account's own.
 */
public class CorrelatedConstantArgumentEchoTest extends BaseDatabaseTest {

    private static final String RANDOM = "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (n INT, n52 NUMBER(5,2))");
        engine.execute("CREATE TABLE t2 (a INT, b INT)");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void anOuterColumnIsACorrelation() {
        assertEquals(RANDOM + "CORRELATION(RT.N)'", answer("SELECT (SELECT RANDOM(n)) FROM rt"));
        assertEquals(RANDOM + "CORRELATION(RT.N)'", answer("SELECT (SELECT RANDOM(rt.n)) FROM rt"));
        assertEquals(RANDOM + "CORRELATION(RT.N)'", answer("SELECT (SELECT RANDOM(n) FROM t2) FROM rt"));
        assertEquals(RANDOM + "T2.A + CORRELATION(RT.N)'", answer("SELECT (SELECT RANDOM(a + n) FROM t2) FROM rt"));
        assertEquals(RANDOM + "CORRELATION(R.N)'", answer("SELECT (SELECT RANDOM(r.n)) FROM rt r"));
        assertEquals(RANDOM + "CORRELATION(R.N)'", answer("SELECT (SELECT RANDOM(n)) FROM rt r"));
        assertEquals(RANDOM + "CORRELATION(RT.N) + 1'", answer("SELECT (SELECT RANDOM(n + 1)) FROM rt"));
        assertEquals(RANDOM + "CORRELATION(RT.N52) * 2'", answer("SELECT (SELECT RANDOM(n52 * 2)) FROM rt"));
        assertEquals(RANDOM + "T2.A'", answer("SELECT (SELECT RANDOM(a) FROM t2) FROM rt"), "the subquery's own column is its own");
        assertEquals(RANDOM + "CORRELATION(RT.N)'", answer("SELECT 1 FROM rt WHERE EXISTS (SELECT RANDOM(n))"));
        assertEquals(RANDOM + "CORRELATION(RT.N)'",
            answer("SELECT (SELECT RANDOM(n) FROM t2 WHERE t2.a = rt.n) FROM rt"));
    }

    @Test
    public void theCorrelationIsConvertedAndAggregatedAsAnyValueIs() {
        assertEquals(RANDOM + "SQRT(CAST(CORRELATION(RT.N) AS FLOAT))'", answer("SELECT (SELECT RANDOM(SQRT(n))) FROM rt"));
        assertEquals(RANDOM + "CORRELATION(MAX(RT.N))'", answer("SELECT (SELECT RANDOM(MAX(n))) FROM rt"));
        assertEquals("SQL compilation error:|argument 0 to function GETVARIABLE needs to be constant, found "
                + "'CAST(CORRELATION(RT.N) AS VARCHAR(134217728))'",
            answer("SELECT (SELECT GETVARIABLE(n)) FROM rt"));
        assertEquals("SQL compilation error:|argument 2 to function UNIFORM needs to be constant, found 'CORRELATION(RT.N)'",
            answer("SELECT (SELECT UNIFORM(1, n, 2)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 15|too many arguments for function "
                + "[ABS(CORRELATION(RT.N), 1)] expected 1, got 2",
            answer("SELECT (SELECT ABS(n, 1)) FROM rt"));
    }

    @Test
    public void aRowReachingTheSubqueryChangesNothing() {
        engine.execute("INSERT INTO rt VALUES (1, 1.5), (2, 2.5)");
        assertEquals(RANDOM + "CORRELATION(RT.N)'", answer("SELECT (SELECT RANDOM(n)) FROM rt"));
        assertEquals(RANDOM + "CORRELATION(RT.N) + 1'", answer("SELECT (SELECT RANDOM(n + 1)) FROM rt"));
    }
}
