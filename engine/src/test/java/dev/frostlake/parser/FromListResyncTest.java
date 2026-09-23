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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * After a first fault in a query's FROM list live's recovery skips to the first token that may follow the broken table
 * reference and reads on: a comma after the list's first reference, a clause, a join after a table read whole, a FOR
 * whose next token it refuses, or the next statement (live-verified).
 */
public class FromListResyncTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aCommaAfterTheFirstReferenceCarriesTheListOn() {
        assertEquals(lines(at(1, 14, "2"), at(1, 17, "1")), refusal("SELECT 1 FROM 2, 1"));
        assertEquals(lines(at(1, 14, "2"), at(1, 20, "1")), refusal("SELECT 1 FROM 2, t, 1"));
        assertEquals(lines(at(1, 14, "2"), at(1, 17, "1")), refusal("SELECT 1 FROM 2, 1, 2"));
        assertEquals(lines(at(1, 16, "+"), at(1, 21, "1")), refusal("SELECT 1 FROM d + 1, 1"));
        assertEquals(lines(at(1, 14, "2"), at(1, 19, "+")), refusal("SELECT 1 FROM 2, d + 1"));
        assertEquals(lines(at(1, 15, "1"), at(1, 19, "1")), refusal("SELECT 1 FROM (1), 1"));
        assertEquals(lines(at(1, 14, "2")), refusal("SELECT 1 FROM 2,"));
        assertEquals(lines(at(1, 29, "2"), at(1, 32, "1")), refusal("SELECT 1 FROM (SELECT 1 FROM 2, 1)"));
    }

    @Test
    public void aLaterReferencesFaultIsTheBrokenTokenAndTheListEnds() {
        assertEquals(lines(at(1, 17, "2")), refusal("SELECT 1 FROM t, 2, 1"));
        assertEquals(lines(at(1, 20, "+")), refusal("SELECT 1 FROM t, t2 + 1"));
        assertEquals(lines(at(1, 21, "+")), refusal("SELECT 1 FROM t x, d + 1"));
    }

    @Test
    public void aClauseReadsOnAndAJoinOnlyAfterATable() {
        assertEquals(lines(at(1, 14, "2"), at(1, 24, "y")), refusal("SELECT 1 FROM 2 WHERE x y"));
        assertEquals(lines(at(1, 14, "2"), at(1, 21, "<EOF>")), refusal("SELECT 1 FROM 2 WHERE"));
        assertEquals(lines(at(1, 14, "2"), at(1, 21, "<EOF>")), refusal("SELECT 1 FROM 2 LIMIT"));
        assertEquals(lines(at(1, 14, "2"), at(1, 27, "y")), refusal("SELECT 1 FROM 2 ORDER BY x y"));
        assertEquals(lines(at(1, 14, "2")), refusal("SELECT 1 FROM 2 JOIN t ON x y"));
        assertEquals(lines(at(1, 14, "2")), refusal("SELECT 1 FROM 2 LIMIT 1"));
        assertEquals(lines(at(1, 16, "+"), at(1, 32, "y")), refusal("SELECT 1 FROM d + 1 JOIN t ON x y"));
        assertEquals(lines(at(1, 14, "2"), at(1, 29, "1")), refusal("SELECT 1 FROM 2 JOIN t ON 1, 1"));
    }

    @Test
    public void aForRefusesTheTokenAfterIt() {
        assertEquals(lines(at(1, 14, "2"), at(1, 20, "1")), refusal("SELECT 1 FROM 2 FOR 1) FROM t"));
        assertEquals(lines(at(1, 14, "2"), at(1, 20, "x")), refusal("SELECT 1 FROM 2 FOR x"));
        assertEquals(lines(at(1, 14, "2"), at(1, 19, "<EOF>")), refusal("SELECT 1 FROM 2 FOR"));
        assertEquals(lines(at(1, 17, "2"), at(1, 23, "1")), refusal("SELECT 1 FROM t, 2 FOR 1"));
        assertEquals(lines(at(1, 14, "2"), at(1, 17, "1"), at(1, 23, "3")), refusal("SELECT 1 FROM 2, 1 FOR 3"));
    }

    @Test
    public void anAsThatNoAliasFollowsIsNotTheFault() {
        assertEquals(lines(at(1, 19, "1")), refusal("SELECT 1 FROM d AS 1"));
        assertEquals(lines(at(1, 18, "<EOF>")), refusal("SELECT 1 FROM d AS"));
        assertEquals(lines(at(1, 19, "'x'")), refusal("SELECT 1 FROM d AS 'x'"));
        assertEquals(lines(at(1, 19, "1"), at(1, 22, "1")), refusal("SELECT 1 FROM d AS 1, 1"));
        assertEquals(lines(at(1, 19, "1"), at(1, 29, "y")), refusal("SELECT 1 FROM d AS 1 WHERE x y"));
    }

    @Test
    public void theNextStatementIsReadOnItsOwn() {
        assertEquals(lines(at(1, 14, "2"), at(2, 11, "y")), refusal("SELECT 1 FROM 2 x;\nSELECT 1 x y"));
        assertEquals(lines(at(1, 14, "2"), at(1, 17, "1"), at(2, 11, "y")), refusal("SELECT 1 FROM 2, 1;\nSELECT 1 x y"));
        assertEquals(lines(at(1, 14, "2")), refusal("SELECT 1 FROM 2; ;"));
    }

    @Test
    public void theAnsiFromFormReadsItsFromClauseTheSameWay() {
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 27, "1")), refusal("SELECT SUBSTRING(b FROM 2, 1) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 28, "+"), at(1, 33, "1")),
            refusal("SELECT EXTRACT('wks' FROM d + 1, 1) FROM t"));
    }
}
