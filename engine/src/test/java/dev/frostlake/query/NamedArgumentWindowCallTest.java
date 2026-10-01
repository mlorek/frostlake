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
 * A call written with named arguments and {@code OVER}. A name that is no window function and no aggregate
 * is refused for its KIND, exactly as the positional call is, in every clause and in a FROM-less query; a
 * window function or an aggregate is judged for its arity first — the named values counted and echoed as
 * the positional ones they stand for — and then refused at the call for the named arguments themselves.
 * Every cell is live-verified.
 */
public class NamedArgumentWindowCallTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'a'), (2, 2.5, 'b')");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(' ').append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalidType(final String name) {
        return "SQL compilation error:|Invalid function type [" + name + "] for window function.";
    }

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    private static String noNamedArguments(final int position, final String name) {
        return at(position, "function " + name + " does not support named arguments");
    }

    /** A table function, a scalar and an unknown name alike, named or mixed. */
    @Test
    public void aNonWindowFunctionWithNamedArgumentsIsATypeComplaint() {
        assertEquals(invalidType("GENERATOR"), answer("SELECT GENERATOR(ROWCOUNT => 1) OVER () FROM rt"));
        assertEquals(invalidType("FLATTEN"),
            answer("SELECT FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) OVER () FROM rt"));
        assertEquals(invalidType("SPLIT_TO_TABLE"),
            answer("SELECT SPLIT_TO_TABLE(string => 'a,b', delimiter => ',') OVER () FROM rt"));
        assertEquals(invalidType("NOSUCHFN"), answer("SELECT NOSUCHFN(x => 1) OVER () FROM rt"));
        assertEquals(invalidType("ABS"), answer("SELECT ABS(x => 1) OVER () FROM rt"));
        assertEquals(invalidType("GENERATOR"), answer("SELECT GENERATOR(1, ROWCOUNT => 1) OVER () FROM rt"));
        assertEquals(invalidType("UPPER"), answer("SELECT UPPER(g, x => 2) OVER () FROM rt"));
        assertEquals(invalidType("GENERATOR"), answer("SELECT gEnErAtOr(ROWCOUNT => 1) OVER () FROM rt"));
        assertEquals(invalidType("GENERATOR"), answer("SELECT GENERATOR(ROWCOUNT => 1) OVER () + 1 FROM rt"));
        // The kind is judged before any named argument, whichever call is written first.
        assertEquals(invalidType("ABS"), answer("SELECT SUM(x => 1) OVER (), ABS(1) OVER () FROM rt"));
        assertEquals(invalidType("ABS"), answer("SELECT ABS(1) OVER (), SUM(x => 1) OVER () FROM rt"));
    }

    /** Every clause, the join condition, a FROM-less query and a scalar subquery. */
    @Test
    public void everyClauseAndTheFromlessFormsAreRefusedAlike() {
        assertEquals(invalidType("GENERATOR"),
            answer("SELECT n FROM rt WHERE GENERATOR(ROWCOUNT => 1) OVER () = 1"));
        assertEquals(invalidType("GENERATOR"),
            answer("SELECT n FROM rt QUALIFY GENERATOR(ROWCOUNT => 1) OVER () = 1"));
        assertEquals(invalidType("GENERATOR"),
            answer("SELECT n FROM rt ORDER BY GENERATOR(ROWCOUNT => 1) OVER ()"));
        assertEquals(invalidType("GENERATOR"),
            answer("SELECT n FROM rt GROUP BY GENERATOR(ROWCOUNT => 1) OVER ()"));
        assertEquals(invalidType("GENERATOR"),
            answer("SELECT a.n FROM rt a JOIN rt b ON GENERATOR(ROWCOUNT => 1) OVER () = 1"));
        assertEquals(invalidType("GENERATOR"), answer("SELECT GENERATOR(ROWCOUNT => 1) OVER ()"));
        assertEquals(invalidType("GENERATOR"),
            answer("SELECT (SELECT GENERATOR(ROWCOUNT => 1) OVER ()) FROM rt"));
        assertEquals(invalidType("GENERATOR"), answer("SELECT 1 ORDER BY GENERATOR(ROWCOUNT => 1) OVER ()"));
        // A FROM-less query's ORDER BY holds positional calls to the same rule.
        assertEquals(invalidType("ABS"), answer("SELECT 1 ORDER BY ABS(1) OVER ()"));
        assertEquals(invalidType("NOSUCHFN"), answer("SELECT 1 ORDER BY NOSUCHFN(1) OVER ()"));
        assertEquals("ACCEPTED: 1", answer("SELECT 1 AS x ORDER BY SUM(1) OVER (), x"));
        assertEquals("ACCEPTED: 1", answer("SELECT 1 AS x ORDER BY RANK() OVER (ORDER BY x)"));
    }

    /** A window function or an aggregate takes no named argument, refused at the call in every clause. */
    @Test
    public void aWindowCapableFunctionRefusesNamedArgumentsAtTheCall() {
        assertEquals(noNamedArguments(7, "SUM"), answer("SELECT SUM(x => 1) OVER () FROM rt"));
        assertEquals(noNamedArguments(7, "COUNT"), answer("SELECT COUNT(x => 1, y => 2) OVER () FROM rt"));
        assertEquals(noNamedArguments(7, "LAG"), answer("SELECT LAG(x => n) OVER (ORDER BY n) FROM rt"));
        assertEquals(noNamedArguments(7, "LAG"),
            answer("SELECT LAG(n, x => 1, y => 0) OVER (ORDER BY n) FROM rt"));
        assertEquals(noNamedArguments(7, "NTILE"), answer("SELECT NTILE(x => 2) OVER (ORDER BY n) FROM rt"));
        assertEquals(noNamedArguments(7, "ARRAY_AGG"), answer("SELECT ARRAY_AGG(x => n) OVER () FROM rt"));
        assertEquals(noNamedArguments(7, "PERCENTILE_CONT"),
            answer("SELECT PERCENTILE_CONT(x => 0.5) OVER () FROM rt"));
        assertEquals(noNamedArguments(23, "SUM"), answer("SELECT n FROM rt WHERE SUM(x => 1) OVER () = 1"));
        assertEquals(noNamedArguments(35, "SUM"),
            answer("SELECT n FROM rt GROUP BY n HAVING SUM(x => n) OVER () > 1"));
        assertEquals(noNamedArguments(25, "SUM"), answer("SELECT n FROM rt QUALIFY SUM(x => n) OVER () > 1"));
        assertEquals(noNamedArguments(26, "SUM"), answer("SELECT n FROM rt ORDER BY SUM(x => n) OVER ()"));
        assertEquals(noNamedArguments(7, "SUM"), answer("SELECT SUM(x => 1) OVER ()"));
        assertEquals(noNamedArguments(23, "SUM"), answer("SELECT 1 AS x ORDER BY SUM(x => 1) OVER ()"));
        assertEquals(noNamedArguments(7, "ARRAY_AGG"), answer("SELECT ARRAY_AGG(x => 1) OVER ()"));
        // A later item's argument types wait behind it.
        assertEquals(noNamedArguments(7, "SUM"), answer("SELECT SUM(x => 1) OVER (), n + TRUE FROM rt"));
    }

    /** ★ The named arguments are judged in the select list's item order: an earlier item's arity speaks first. */
    @Test
    public void theNamedArgumentsWaitForTheItemsWrittenBeforeThem() {
        assertEquals(at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2"),
            answer("SELECT UPPER(1, 2), SUM(x => 1) OVER () FROM rt"));
        assertEquals(noNamedArguments(7, "SUM"), answer("SELECT SUM(x => 1) OVER (), UPPER(1, 2) FROM rt"));
        assertEquals(noNamedArguments(7, "SUM"), answer("SELECT SUM(x => 1) OVER (), n + TRUE FROM rt"));
        assertEquals(at(9, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"),
            answer("SELECT n + TRUE, SUM(x => 1) OVER () FROM rt"));
        // A call in another clause waits for the whole select list.
        assertEquals(at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2"),
            answer("SELECT UPPER(1, 2) FROM rt QUALIFY SUM(x => n) OVER () > 1"));
        assertEquals(at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2"),
            answer("SELECT UPPER(1, 2) FROM rt ORDER BY SUM(x => n) OVER ()"));
    }

    /** ★ The arity comes first, the named values counted and echoed as positional ones. */
    @Test
    public void theArityComesFirstWithTheNamedValuesCounted() {
        assertEquals(at(7, "too many arguments for function [SUM(1, 2)] expected 1, got 2"),
            answer("SELECT SUM(x => 1, y => 2) OVER () FROM rt"));
        assertEquals(at(7, "too many arguments for function [SUM(RT.N, 2)] expected 1, got 2"),
            answer("SELECT SUM(n, x => 2) OVER () FROM rt"));
        assertEquals(at(7, "too many arguments for function [MAX(RT.N, 2)] expected 1, got 2"),
            answer("SELECT MAX(x => n, y => 2) OVER () FROM rt"));
        assertEquals(at(7, "too many arguments for function [RANK(1)] expected 0, got 1"),
            answer("SELECT RANK(x => 1) OVER (ORDER BY n) FROM rt"));
        assertEquals(at(7, "too many arguments for function [ROW_NUMBER(1, 2)] expected 0, got 2"),
            answer("SELECT ROW_NUMBER(x => 1, y => 2) OVER (ORDER BY n) FROM rt"));
        assertEquals(at(7, "too many arguments for function [CUME_DIST(1)] expected 0, got 1"),
            answer("SELECT CUME_DIST(x => 1) OVER (ORDER BY n) FROM rt"));
        assertEquals(at(7, "too many arguments for function [FIRST_VALUE(RT.N, 1)] expected 1, got 2"),
            answer("SELECT FIRST_VALUE(x => n, y => 1) OVER (ORDER BY n) FROM rt"));
        assertEquals(at(7, "too many arguments for function [FIRST_VALUE(RT.N, 1)] expected 1, got 2"),
            answer("SELECT FIRST_VALUE(n, x => 1) OVER (ORDER BY n) FROM rt"));
        assertEquals(at(7, "not enough arguments for function [CORR(RT.N)], expected 2, got 1"),
            answer("SELECT CORR(x => n) OVER () FROM rt"));
        assertEquals(at(7, "not enough arguments for function [NTH_VALUE(RT.N)], expected 2, got 1"),
            answer("SELECT NTH_VALUE(x => n) OVER (ORDER BY n) FROM rt"));
    }
}
