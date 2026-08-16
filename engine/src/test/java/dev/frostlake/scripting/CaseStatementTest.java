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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code CASE … WHEN … END CASE} as a procedural <em>statement</em> (Snowflake Scripting), run in anonymous
 * BEGIN…END blocks. Covers the searched form ({@code CASE WHEN <cond> THEN …}) and the simple form
 * ({@code CASE <operand> WHEN <value> THEN …}), the ELSE fallback, first-match-wins, and the
 * no-match-without-ELSE no-op. (This is distinct from CASE as an <em>expression</em>, which is covered by
 * the procedural-expression tests.)
 */
public class CaseStatementTest extends BaseDatabaseTest {

    private int runReturningInt(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    @Test
    public void searchedCaseTakesFirstTrueBranch() {
        assertEquals(20, runReturningInt("""
            BEGIN
                LET x INTEGER := 2;
                LET result INTEGER := 0;
                CASE
                    WHEN x = 1 THEN result := 10;
                    WHEN x = 2 THEN result := 20;
                    ELSE result := 99;
                END CASE;
                RETURN result;
            END;
            """));
    }

    @Test
    public void searchedCaseFallsToElseWhenNoMatch() {
        assertEquals(99, runReturningInt("""
            BEGIN
                LET x INTEGER := 7;
                LET result INTEGER := 0;
                CASE
                    WHEN x = 1 THEN result := 10;
                    WHEN x = 2 THEN result := 20;
                    ELSE result := 99;
                END CASE;
                RETURN result;
            END;
            """));
    }

    @Test
    public void simpleCaseMatchesOperandValue() {
        assertEquals(20, runReturningInt("""
            BEGIN
                LET x INTEGER := 2;
                LET result INTEGER := 0;
                CASE x
                    WHEN 1 THEN result := 10;
                    WHEN 2 THEN result := 20;
                    ELSE result := 99;
                END CASE;
                RETURN result;
            END;
            """));
    }

    @Test
    public void noMatchWithoutElseIsANoOp() {
        // No branch matches and there is no ELSE → CASE does nothing, result keeps its initial value.
        assertEquals(0, runReturningInt("""
            BEGIN
                LET x INTEGER := 7;
                LET result INTEGER := 0;
                CASE
                    WHEN x = 1 THEN result := 10;
                    WHEN x = 2 THEN result := 20;
                END CASE;
                RETURN result;
            END;
            """));
    }

    @Test
    public void firstMatchingBranchWins() {
        // Both conditions are true; the first matching branch must be taken.
        assertEquals(1, runReturningInt("""
            BEGIN
                LET x INTEGER := 5;
                LET result INTEGER := 0;
                CASE
                    WHEN x > 0 THEN result := 1;
                    WHEN x > 1 THEN result := 2;
                END CASE;
                RETURN result;
            END;
            """));
    }
}
