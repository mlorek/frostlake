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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A LIKE pattern that ends in its escape character. Where the pattern is matched in full the escape has nothing
 * to escape and the match is refused with {@code Escape character at end of LIKE pattern}; a case-sensitive
 * pattern that is a literal run behind or before a run of {@code %} is compared directly and keeps the escape
 * as a character. ILIKE always matches in full, and the multi-pattern forms meet a dangling escape only where
 * they reach its pattern. Without ESCAPE nothing is an escape, and with it the escape makes any character
 * literal. Every cell is live-verified.
 */
public class LikeDanglingEscapeTest extends BaseDatabaseTest {

    /** Every row's first cell, lower-cased and joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(String.valueOf(row.getValue(0)).toLowerCase());
        }
        return answer.toString();
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private void assertRefused(final String[][] cells) {
        for (final String[] cell : cells) {
            final String sql = cell[0];
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                sql + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }

    @Test
    public void aPatternMatchedInFullRefusesItsDanglingEscape() {
        assertRefused(new String[][] {
            {"SELECT 'ab' LIKE 'a_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'a' LIKE '_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE 'a_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' NOT LIKE 'a_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('a_') ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a_') ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE ANY ('a_') ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('a_', 'ab') ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('ab', 'a_') ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY (NULL, 'a_') ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT s LIKE p ESCAPE '_' FROM (SELECT 'ab' s, 'a_' p)", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE 'a%_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE 'a___' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT 'xb' LIKE 'a_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT '' LIKE '_' ESCAPE '_'", "Escape character at end of LIKE pattern"},
            {"SELECT LIKE('ab', 'a_', '_')", "Escape character at end of LIKE pattern"},
            {"SELECT ILIKE('ab', 'a_', '_')", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE 'a%' ESCAPE '%'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE '%' ESCAPE '%'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' NOT ILIKE 'a!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a%', 'a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('x%', 'a%!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE 'a%!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE 'ab!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('ab', 'a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a!', 'ab') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE ANY ('a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE ANY ('ab', 'a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT ILIKE('ab', 'a!', '!')", "Escape character at end of LIKE pattern"},
            {"SELECT s LIKE 'a%!' ESCAPE '!' FROM (SELECT 'ab' s UNION ALL SELECT NULL) ORDER BY 1", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE ANY ('ab!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a%', 'a%!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE '_b!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE '%a%!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab!' LIKE 'a%!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('a%!', '_b') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('_b', 'a%!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE ANY ('AB', 'a%!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a%', '%b', 'a%!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a%!', 'x%') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE 'a%b!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' NOT LIKE 'a%!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a%', 'ab!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT '' LIKE ANY ('a%!', '%') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('a%!', '%') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' ILIKE ANY ('a%!', 'a%') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('a_', 'a%!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ANY ('a%!', 'a_') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'ab' LIKE ALL ('a%', 'a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'a!' LIKE ALL ('a%', 'a!') ESCAPE '!'", "Escape character at end of LIKE pattern"},
            {"SELECT 'abc' LIKE '%b%!' ESCAPE '!'", "Escape character at end of LIKE pattern"},
        });
    }

    @Test
    public void aDirectlyComparedPatternKeepsItsEscapeAsACharacter() {
        assertAnswers(new String[][] {
            {"SELECT '_' LIKE '__' ESCAPE '_'", "true"},
            {"SELECT 'ab' LIKE 'a!' ESCAPE '!'", "false"},
            {"SELECT 'a!' LIKE 'a!!' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE 'a\\\\'", "false"},
            {"SELECT NULL LIKE 'a_' ESCAPE '_'", "null"},
            {"SELECT 'ab' LIKE NULL ESCAPE '_'", "null"},
            {"SELECT 1 FROM (SELECT 'x' s) WHERE FALSE AND s LIKE 'a_' ESCAPE '_'", ""},
            {"SELECT 'ab' LIKE 'a__' ESCAPE '_'", "false"},
            {"SELECT 'ab' LIKE 'a\\\\\\\\' ESCAPE '\\\\'", "false"},
            {"SELECT 'a\\\\' LIKE 'a\\\\\\\\' ESCAPE '\\\\'", "true"},
            {"SELECT 'ab' LIKE 'a\\\\'", "false"},
            {"SELECT 'a\\\\' LIKE 'a\\\\'", "true"},
            {"SELECT 'a%' LIKE 'a%%' ESCAPE '%'", "true"},
            {"SELECT 'ab' LIKE 'ab!' ESCAPE '!'", "false"},
            {"SELECT 'abc' LIKE 'a!' ESCAPE '!'", "false"},
            {"SELECT IFF(FALSE, 'ab' LIKE 'a!' ESCAPE '!', TRUE)", "true"},
            {"SELECT 'ab' LIKE 'a' || '!' ESCAPE '!'", "false"},
            {"SELECT 'a!b' LIKE 'a!!b' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE 'a!b' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE 'a!' ESCAPE NULL", "null"},
            {"SELECT CASE WHEN 'ab' LIKE 'a!' ESCAPE '!' THEN 1 END", "null"},
            {"SELECT 'ab' NOT LIKE 'a!' ESCAPE '!'", "true"},
            {"SELECT 'AB' ILIKE 'ab' ESCAPE '!'", "true"},
            {"SELECT LIKE('ab', 'a!', '!')", "false"},
            {"SELECT s LIKE 'a!' ESCAPE '!' FROM (SELECT 'ab' s)", "false"},
            {"SELECT s LIKE p ESCAPE '!' FROM (SELECT 'ab' s, 'a!' p)", "false"},
            {"SELECT s LIKE p ESCAPE '!' FROM (SELECT 'ab' s, 'ab!' p)", "false"},
            {"SELECT 'a!' LIKE 'a!' ESCAPE '!'", "true"},
            {"SELECT 'a' LIKE 'a!' ESCAPE '!'", "false"},
            {"SELECT NULL LIKE 'a%!' ESCAPE '!'", "null"},
            {"SELECT COUNT(*) FROM (SELECT NULL::VARCHAR s) WHERE s LIKE 'a%!' ESCAPE '!'", "0"},
            {"SELECT 'ab' LIKE 'a%!'", "false"},
            {"SELECT 'ab!' LIKE 'ab!' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE '!a' ESCAPE '!'", "false"},
            {"SELECT 'a' LIKE '!a' ESCAPE '!'", "true"},
            {"SELECT 'a%' LIKE 'a!%' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE 'a!%' ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE 'a!_' ESCAPE '!'", "false"},
            {"SELECT 'a_' LIKE 'a!_' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE s ESCAPE '!' FROM (SELECT 'ab' s)", "true"},
            {"SELECT s NOT LIKE p ESCAPE '!' FROM (SELECT 'ab' s, 'a!' p)", "true"},
            {"SELECT 'ab' LIKE 'ab' ESCAPE 'b'", "true"},
            {"SELECT 'ab' LIKE 'abb' ESCAPE 'b'", "true"},
            {"SELECT 'a' LIKE 'ab' ESCAPE 'b'", "false"},
            {"SELECT 'ab' LIKE '%b!' ESCAPE '!'", "false"},
            {"SELECT 'ab!' LIKE '%b!' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE '%!' ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE '%%!' ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE '!%b' ESCAPE '!'", "false"},
            {"SELECT 'a%' LIKE 'a\\\\%'", "false"},
            {"SELECT 'a\\\\x' LIKE 'a\\\\%'", "true"},
            {"SELECT 'a_' LIKE 'a\\\\_'", "false"},
            {"SELECT 'a\\\\b' LIKE 'a\\\\_'", "true"},
            {"SELECT 'ab' LIKE 'a!%!' ESCAPE '!'", "false"},
            {"SELECT 'a%!' LIKE 'a!%!' ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE '!!%' ESCAPE '!'", "false"},
            {"SELECT '!ab' LIKE '!!%' ESCAPE '!'", "true"},
        });
    }

    @Test
    public void theMultiPatternFormsReachADanglingEscapeInTheirOwnOrder() {
        assertAnswers(new String[][] {
            {"SELECT 'ab' LIKE ALL ('x%', 'a!') ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE ANY ('a%', 'a!') ESCAPE '!'", "true"},
            {"SELECT COUNT(*) FROM (SELECT 'ab' s UNION ALL SELECT 'cd') WHERE s LIKE 'a!' ESCAPE '!'", "0"},
            {"SELECT 'ab' LIKE ANY ('a!') ESCAPE NULL", "false"},
            {"SELECT 'ab' LIKE ANY ('ab', 'a!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a!', 'x%') ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE ANY ('a%!', 'a%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a%', 'a%!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a!') ESCAPE '!'", "false"},
            {"SELECT s LIKE ANY (p) ESCAPE '!' FROM (SELECT 'ab' s, 'a!' p)", "false"},
            {"SELECT s LIKE ANY (p, 'ab') ESCAPE '!' FROM (SELECT 'ab' s, 'a!' p)", "true"},
            {"SELECT s LIKE ANY ('ab', p) ESCAPE '!' FROM (SELECT 'ab' s, 'a!' p)", "true"},
            {"SELECT s LIKE ALL ('x%', p) ESCAPE '!' FROM (SELECT 'ab' s, 'a!' p)", "false"},
            {"SELECT 'ab' LIKE ANY ('a%', 'x!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('x!', 'a%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ALL ('x%', 'a%!') ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE ANY ('%b!', 'a%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('%b', 'a%!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a%!', '%b') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a%!', '%a%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a%!', 'ab') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('ab', 'a%!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a%!', 'a%', 'x') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('%b!', 'x%') ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE ANY ('x%', 'a!') ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE ANY ('_%!', 'a%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('%b', 'a%!', 'x') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('%a%', 'a%!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('x%', '%b!') ESCAPE '!'", "false"},
            {"SELECT 'ab' LIKE ANY ('_b!', 'a%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('a%', 'ab!') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('ab!', '%') ESCAPE '!'", "true"},
            {"SELECT 'ab' LIKE ANY ('%', 'a!') ESCAPE '!'", "true"},
        });
    }

    @Test
    public void anEscapeIsOneCharacterAndTheGrammarStillDecides() {
        assertRefused(new String[][] {
            {"SELECT 'ab' LIKE 'a_' ESCAPE ''", "SQL compilation error:\ninvalid value [''] for parameter 'escape'"},
            {"SELECT 'ab' LIKE 'a_' ESCAPE 'xy'", "SQL compilation error:\ninvalid value ['xy'] for parameter 'escape'"},
            {"SELECT 'ab' LIKE 'a!' ESCAPE '!' IS NULL", "SQL compilation error:\nsyntax error line 1 at position 33 unexpected 'IS'."},
            {"SELECT s LIKE p ESCAPE e FROM (SELECT 'ab' s, 'a!' p, '!' e)", "SQL compilation error:\nsyntax error line 1 at position 23 unexpected 'e'."},
            {"SELECT 'ab' LIKE 'a!' ESCAPE e FROM (SELECT '!' e)", "SQL compilation error:\nsyntax error line 1 at position 29 unexpected 'e'."},
        });
    }
}
