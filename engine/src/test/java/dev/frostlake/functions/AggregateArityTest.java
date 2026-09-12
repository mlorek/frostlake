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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An aggregate call is held to its DECLARED arity at compile time, positioned at the call
 * (live-verified across the family): too MANY echoes the call and drops the comma —
 * {@code too many arguments for function [SUM(SA.A, SA.B)] expected 1, got 2} — while too FEW keeps
 * it: {@code not enough arguments for function [OBJECT_AGG(SA.A)], expected 2, got 1}.
 *
 * <p>★ A STAR EXPANDS FIRST AND ONE RULE JUDGES THE EXPANDED LIST: {@code ARRAY_AGG(*)} over three
 * columns earns the very sentence the written-out list earns, echoing the expanded QUALIFIED
 * columns — and the windowed star ({@code SUM(*) OVER ()}) behaves exactly like the plain one.
 *
 * <p>★ THE TRULY VARIADIC AGGREGATES TAKE ANY WIDTH: COUNT(a, b), COUNT(a, b, c), HASH_AGG(a, b, c)
 * and HASH_AGG(*) all answer, and HASH_AGG's star answers the SAME value as its written-out list.
 */
public class AggregateArityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sa (a INT, b INT, c INT)");
        engine.execute("INSERT INTO sa VALUES (1, 2, 3)");
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    @Test
    public void tooManyArgumentsRefusesWithTheDeclaredMaximum() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [ARRAY_AGG(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT ARRAY_AGG(a, b) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [SUM(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT SUM(a, b) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [AVG(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT AVG(a, b) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [MIN(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT MIN(a, b) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [MODE(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT MODE(a, b) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [SUM(SA.A, SA.B, SA.C)] expected 1, got 3",
            refusal("SELECT SUM(a, b, c) FROM sa"));
    }

    @Test
    public void notEnoughArgumentsKeepsItsCommaAndTheDeclaredMinimum() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "not enough arguments for function [OBJECT_AGG(SA.A)], expected 2, got 1",
            refusal("SELECT OBJECT_AGG(a) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "not enough arguments for function [CORR(SA.A)], expected 2, got 1",
            refusal("SELECT CORR(a) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "not enough arguments for function [LISTAGG()], expected 1, got 0",
            refusal("SELECT LISTAGG() FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "not enough arguments for function [SUM()], expected 1, got 0",
            refusal("SELECT SUM() FROM sa"));
    }

    @Test
    public void aStarExpandsAndTheExpandedListIsJudged() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [ARRAY_AGG(SA.A, SA.B, SA.C)] expected 1, got 3",
            refusal("SELECT ARRAY_AGG(*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [SUM(SA.A, SA.B, SA.C)] expected 1, got 3",
            refusal("SELECT SUM(*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [LISTAGG(SA.A, SA.B, SA.C)] expected 2, got 3",
            refusal("SELECT LISTAGG(*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [OBJECT_AGG(SA.A, SA.B, SA.C)] expected 2, got 3",
            refusal("SELECT OBJECT_AGG(*) FROM sa"));
    }

    @Test
    public void theWindowedFormsShareTheSentences() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [SUM(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT SUM(a, b) OVER () FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [SUM(SA.A, SA.B, SA.C)] expected 1, got 3",
            refusal("SELECT SUM(*) OVER () FROM sa"));
    }

    @Test
    public void aGroupedQuerySharesTheSentence() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [ARRAY_AGG(SA.A, SA.B)] expected 1, got 2",
            refusal("SELECT ARRAY_AGG(a, b) FROM sa GROUP BY c"));
    }

    @Test
    public void theVariadicAggregatesTakeAnyWidth() {
        assertEquals("1", answer("SELECT COUNT(a, b) FROM sa"));
        assertEquals("1", answer("SELECT COUNT(a, b, c) FROM sa"));
        assertEquals("1", answer("SELECT COUNT(*) FROM sa"));
        assertEquals("1", answer("SELECT LISTAGG(a, ',') FROM sa"));
        final String written = answer("SELECT HASH_AGG(a, b, c) FROM sa");
        assertEquals(written, answer("SELECT HASH_AGG(*) FROM sa"));
    }
}
