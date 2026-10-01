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
 * A star's RENAME or REPLACE where its grammar has no place for one — after a star argument, inside an object star's
 * braces — is refused at the keyword, and live stacks exactly the token after it: nothing when that token closes the
 * star's call or braces, which reads on as if the keyword were not written, and the closing token itself when a comma
 * carries the list on (live-verified).
 */
public class StarModifierLinesTest extends BaseDatabaseTest {

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

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aStarArgumentStacksTheTokenAfterTheKeyword() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(26, "REPLACE"), at(34, "(")),
            refusal("SELECT OBJECT_CONSTRUCT(* REPLACE (1 AS id)) FROM fz"));
        assertEquals(lines(at(26, "RENAME"), at(33, "(")), refusal("SELECT OBJECT_CONSTRUCT(* RENAME (id AS k)) FROM fz"));
        assertEquals(lines(at(25, "REPLACE"), at(33, "(")), refusal("SELECT ARRAY_CONSTRUCT(* REPLACE (1 AS id)) FROM fz"));
        assertEquals(lines(at(14, "REPLACE"), at(22, "(")), refusal("SELECT HASH(* REPLACE (1 AS id)) FROM fz"));
        assertEquals(lines(at(26, "REPLACE"), at(34, "x")), refusal("SELECT OBJECT_CONSTRUCT(* REPLACE x) y z FROM fz"));
        assertEquals(lines(at(39, "RENAME"), at(46, "id")),
            refusal("SELECT OBJECT_CONSTRUCT(fz.* EXCLUDE b RENAME id AS k) FROM fz"));
        assertEquals(lines(at(18, "REPLACE"), at(26, "(")), refusal("SELECT id, HASH(* REPLACE (1 AS id)) FROM fz"));
        assertEquals(lines(at(29, "REPLACE"), at(37, "(")),
            refusal("INSERT INTO fz SELECT HASH(* REPLACE (1 AS id)), TRUE FROM fz"));
        assertEquals(lines(at(38, "REPLACE"), at(46, "(")),
            refusal("SELECT id FROM fz UNION SELECT HASH(* REPLACE (1 AS id)) FROM fz"));
    }

    @Test
    public void theClosingTokenReadsOnAndACommaRefusesTheCallsParenthesis() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(26, "REPLACE")), refusal("SELECT OBJECT_CONSTRUCT(* REPLACE) FROM fz"));
        assertEquals(lines(at(14, "REPLACE"), at(25, "y")), refusal("SELECT HASH(* REPLACE) x y FROM fz"));
        assertEquals(lines(at(14, "REPLACE"), at(24, ")")), refusal("SELECT HASH(* REPLACE, 1) x y FROM fz"));
        assertEquals(lines(at(14, "RENAME"), at(24, ")")), refusal("SELECT HASH(* RENAME, id) FROM fz"));
        assertEquals(lines(at(14, "REPLACE"), at(31, "REPLACE"), at(39, "(")),
            refusal("SELECT HASH(* REPLACE), HASH(* REPLACE (1 AS id)) FROM fz"));
    }

    @Test
    public void anObjectStarsBracesReadTheSameWay() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(10, "REPLACE")), refusal("SELECT {* REPLACE} FROM fz"));
        assertEquals(lines(at(10, "RENAME")), refusal("SELECT {* RENAME} FROM fz"));
        assertEquals(lines(at(13, "REPLACE")), refusal("SELECT {fz.* REPLACE} FROM fz"));
        assertEquals(lines(at(21, "REPLACE")), refusal("SELECT {* EXCLUDE id REPLACE} FROM fz"));
        assertEquals(lines(at(10, "REPLACE"), at(18, "x")), refusal("SELECT {* REPLACE x} x y FROM fz"));
        assertEquals(lines(at(10, "REPLACE"), at(20, "}")), refusal("SELECT {* REPLACE, 1} FROM fz"));
        assertEquals(lines(at(10, "REPLACE"), at(35, "y")), refusal("SELECT {* REPLACE} FROM fz WHERE x y"));
        assertEquals(lines(at(10, "REPLACE"), at(23, "RENAME")), refusal("SELECT {* REPLACE}, {* RENAME} FROM fz"));
    }

    @Test
    public void aFaultRightAfterTheClosingTokenEndsTheReport() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(10, "REPLACE")), refusal("SELECT {* REPLACE}} FROM fz"));
        assertEquals(lines(at(10, "REPLACE")), refusal("SELECT {* REPLACE}} FROM fz WHERE x y"));
        assertEquals(lines(at(14, "REPLACE")), refusal("SELECT HASH(* REPLACE)) FROM fz"));
    }
}
