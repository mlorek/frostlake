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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Function arity refuses with live's two sentences (measured, codes 938/939): {@code not enough
 * arguments for function [F(…)], expected N, got M} — comma after the bracket — and {@code too many
 * arguments for function [F(…)] expected N, got M} — no comma; booleans render UPPER in the
 * brackets, the refusal fires at compile time (an empty input still refuses) anchored on the call,
 * an aggregate refuses an EMPTY argument list while multi-argument aggregate calls stay legal
 * ({@code COUNT(a, b)} is accepted), and the rank family of window functions takes no arguments.
 */
public class FunctionArityTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixture() {
        engine.execute("CREATE TABLE fa_t (a INTEGER)");
        engine.execute("INSERT INTO fa_t VALUES (1)");
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    private void assertRefusedExactly(final String sql, final String message) {
        assertEquals(message, refusal(sql).getMessage());
    }

    private void assertRefusedContaining(final String sql, final String sentence) {
        final String message = refusal(sql).getMessage();
        assertTrue(message.contains(sentence), "unexpected: " + message);
    }

    @Test
    public void tooManyArgumentsRefusesPositionedAtTheCall() {
        assertRefusedExactly("SELECT UPPER('a', 'b') FROM fa_t",
            "SQL compilation error: error line 1 at position 7\n"
                + "too many arguments for function [UPPER('a', 'b')] expected 1, got 2");
    }

    @Test
    public void notEnoughArgumentsCarriesTheCommaForm() {
        assertRefusedExactly("SELECT SUBSTR('a') FROM fa_t",
            "SQL compilation error: error line 1 at position 7\n"
                + "not enough arguments for function [SUBSTR('a')], expected 2, got 1");
        assertRefusedExactly("SELECT IFF(TRUE) FROM fa_t",
            "SQL compilation error: error line 1 at position 7\n"
                + "not enough arguments for function [IFF(TRUE)], expected 3, got 1");
    }

    @Test
    public void theRefusalFiresAtCompileTimeOverAnEmptyInput() {
        assertRefusedContaining("SELECT UPPER('a', 'b') FROM fa_t WHERE 1 = 0",
            "too many arguments for function");
    }

    @Test
    public void fromLessCallsRefuseTheSameSentences() {
        assertRefusedContaining("SELECT UPPER()",
            "not enough arguments for function [UPPER()], expected 1, got 0");
        assertRefusedContaining("SELECT COALESCE()",
            "not enough arguments for function [COALESCE()], expected 2, got 0");
        assertRefusedContaining("SELECT ABS(1, 2)",
            "too many arguments for function [ABS(1, 2)] expected 1, got 2");
    }

    @Test
    public void anAggregateRefusesAnEmptyArgumentList() {
        assertRefusedContaining("SELECT SUM() FROM fa_t",
            "not enough arguments for function [SUM()], expected 1, got 0");
    }

    @Test
    public void multiArgumentAggregatesAndStarStayLegal() {
        assertEquals(1, engine.executeQuery("SELECT COUNT(a, a) FROM fa_t").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT COUNT(*) FROM fa_t").getRowCount());
    }

    @Test
    public void theRankFamilyTakesNoArguments() {
        assertRefusedContaining("SELECT ROW_NUMBER(1) OVER (ORDER BY a) FROM fa_t",
            "too many arguments for function [ROW_NUMBER(1)] expected 0, got 1");
        assertEquals(1, engine.executeQuery(
            "SELECT ROW_NUMBER() OVER (ORDER BY a) FROM fa_t").getRowCount());
    }
}
