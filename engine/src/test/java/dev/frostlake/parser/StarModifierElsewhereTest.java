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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A star call's misplaced RENAME or REPLACE, or a word that is no modifier, where no select item or clause of the
 * statement's own query holds it: a join's condition, a subquery that is a select item, a CAST and a MERGE's
 * condition.
 */
public class StarModifierElsewhereTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("CREATE TABLE fy (id INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aJoinsConditionIsRefusedAtItsOn() {
        assertEquals(lines(at(29, "ON")), refusal("SELECT 1 FROM fz a JOIN fz b ON HASH(* REPLACE (1 AS id)) = 1"));
        assertEquals(lines(at(29, "ON")), refusal("SELECT 1 FROM fz a JOIN fz b ON HASH(* REPLACE x) = 1"));
        assertEquals(lines(at(29, "ON")), refusal("SELECT 1 FROM fz a JOIN fz b ON HASH(* x) = 1"));
        assertEquals(lines(at(29, "ON")),
            refusal("SELECT 1 FROM fz a JOIN fz b ON HASH(* REPLACE (1 AS id)) = 1 WHERE x y"));
        assertEquals(lines(at(34, "ON")), refusal("SELECT 1 FROM fz a LEFT JOIN fz b ON HASH(* REPLACE (1 AS id)) = 1"));
        assertEquals(lines(at(29, "ON")),
            refusal("SELECT 1 FROM fz a JOIN fz b ON a.id = b.id AND HASH(* REPLACE (1 AS id)) = 1"));
        // Any other fault of a join's condition is refused where it stands.
        assertEquals(lines(at(34, "y")), refusal("SELECT 1 FROM fz a JOIN fz b ON x y"));
    }

    @Test
    public void aWordThatIsNoModifierIsRefusedAlone() {
        assertEquals(lines(at(30, "x")), refusal("SELECT 1 FROM fz WHERE HASH(* x) = 1"));
        assertEquals(lines(at(30, "x")), refusal("SELECT 1 FROM fz WHERE HASH(* x y) = 1"));
        assertEquals(lines(at(30, "1")), refusal("SELECT 1 FROM fz WHERE HASH(* 1) = 1"));
        assertEquals(lines(at(30, "x"), at(43, "b")), refusal("SELECT 1 FROM fz WHERE HASH(* x) = 1 AND a b"));
        assertEquals(lines(at(30, "REPLACE"), at(39, "1"), at(64, "x")),
            refusal("SELECT 1 FROM fz WHERE HASH(* REPLACE (1 AS id)) = 1 AND HASH(* x) = 1"));
        assertEquals(lines(at(33, "x")), refusal("SELECT 1 FROM fz ORDER BY HASH(* x)"));
        assertEquals(lines(at(33, "x")), refusal("SELECT 1 FROM fz GROUP BY HASH(* x)"));
        assertEquals(lines(at(31, "x")), refusal("SELECT 1 FROM fz HAVING HASH(* x) = 1"));
        assertEquals(lines(at(14, "x")), refusal("SELECT HASH(* x) FROM fz"));
        assertEquals(lines(at(14, "x")), refusal("SELECT HASH(* x) = 1 FROM fz"));
        assertEquals(lines(at(20, "x")), refusal("SELECT UPPER(HASH(* x)) FROM fz"));
        assertEquals(lines(at(34, "x")), refusal("UPDATE fz SET id = 1 WHERE HASH(* x) = 1"));
        assertEquals(lines(at(28, "x")), refusal("DELETE FROM fz WHERE HASH(* x) = 1"));
    }

    @Test
    public void aSubqueryThatIsASelectItemIsRefusedAtItsSelect() {
        assertEquals(lines(at(8, "SELECT"), at(31, "(")),
            refusal("SELECT (SELECT OBJECT_CONSTRUCT(* REPLACE (1 AS id)) FROM fz)"));
        assertEquals(lines(at(8, "SELECT"), at(19, "(")), refusal("SELECT (SELECT HASH(* REPLACE x) FROM fz)"));
        assertEquals(lines(at(12, "SELECT"), at(23, "(")),
            refusal("SELECT 1 + (SELECT HASH(* REPLACE (1 AS id)) FROM fz) FROM fz"));
    }

    @Test
    public void underACastTheReadingIsACallsAndInAMergesConditionAClauses() {
        assertEquals(lines(at(19, "REPLACE"), at(28, "1"), at(35, ")")),
            refusal("SELECT CAST(HASH(* REPLACE (1 AS id)) AS INT) FROM fz"));
        assertEquals(lines(at(23, "REPLACE"), at(32, "1"), at(39, ")")),
            refusal("SELECT TRY_CAST(HASH(* REPLACE (1 AS id)) AS INT) FROM fz"));
        assertEquals(lines(at(19, "REPLACE"), at(28, "id"), at(30, ")")),
            refusal("SELECT CAST(HASH(* REPLACE (id)) AS INT) FROM fz"));
        assertEquals(lines(at(19, "REPLACE")), refusal("SELECT CAST(HASH(* REPLACE) AS INT) FROM fz"));
        assertEquals(lines(at(33, "REPLACE"), at(42, "1")),
            refusal("MERGE INTO fz USING fy ON HASH(* REPLACE (1 AS id)) = 1 WHEN MATCHED THEN DELETE"));
        assertEquals(lines(at(33, "REPLACE"), at(42, "id")),
            refusal("MERGE INTO fz USING fy ON HASH(* REPLACE (id)) = 1 WHEN MATCHED THEN DELETE"));
        assertEquals(lines(at(33, "REPLACE")),
            refusal("MERGE INTO fz USING fy ON HASH(* REPLACE x) = 1 WHEN MATCHED THEN DELETE"));
        assertEquals(lines(at(33, "REPLACE")),
            refusal("MERGE INTO fz USING fy ON HASH(* REPLACE) = 1 WHEN MATCHED THEN DELETE"));
    }
}
