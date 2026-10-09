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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code !} the account reads. Leading a condition it stands where NOT stands: before EXISTS it negates, and
 * before anything else it is a call of a function named {@code "!"}, which no account has, named with every other
 * unknown function of the statement. Between a name and a method it calls a class instance's method,
 * {@code model!predict(…)}, which names nothing here: {@code Unknown function MODEL!PREDICT.} It is no value
 * operator, so {@code x = !y} is a syntax error at the {@code !}, and after a value that is no name the token
 * after it is refused. Every row is live-verified.
 */
public class BangOperatorTest extends BaseDatabaseTest {

    /** A statement, and its answer or the words it is refused in. */
    private static final String[][] CASES = {
            {"SELECT !1",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT ! TRUE",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT 1 ! 2",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected '2'."},
            {"SELECT x!y FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected 'FROM'."},
            {"SELECT x!y() FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!y(1) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT 1 !",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 10 unexpected '<EOF>'."},
            {"SELECT !x FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT 1 !< 2",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 10 unexpected '<'."},
            {"SELECT 1 !> 2",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 10 unexpected '>'."},
            {"SELECT 1 ! = 2",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected '='."},
            {"SELECT NOT !1",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT m!predict(1)",
                "REFUSED SQL compilation error:\nUnknown function M!PREDICT."},
            {"SELECT pg!x FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 12 unexpected 'FROM'."},
            {"SELECT !",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 8 unexpected '<EOF>'."},
            {"SELECT !!1",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", \"!\"."},
            {"SELECT ! FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 9 unexpected 'FROM'."},
            {"SELECT !(1)",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !(x) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT 1 FROM pg WHERE !x",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT 1 FROM pg WHERE x ! y",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 28 unexpected '<EOF>'."},
            {"SELECT 'a'!b",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected 'b'."},
            {"SELECT x!\"y\"() FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!\"y\"."},
            {"SELECT x ! y () FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT !x!y FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 12 unexpected 'FROM'."},
            {"SELECT -!1",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 8 unexpected '!'."},
            {"SELECT 1 + !1",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected '!'."},
            {"SELECT !NULL",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !'a'",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT ! x, 1 FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT nosuch!y() FROM pg",
                "REFUSED SQL compilation error:\nUnknown function NOSUCH!Y."},
            {"SELECT pg.x!y() FROM pg",
                "REFUSED SQL compilation error:\nUnknown user-defined function PG.X!Y."},
            {"SELECT x!y(), a!b() FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions X!Y, A!B."},
            {"SELECT !x, !y FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", \"!\"."},
            {"SELECT ABS(!x) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT (!x) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT CASE WHEN !b THEN 1 END FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT b AND !b FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT b OR !b FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT x = !y FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected '!'."},
            {"SELECT !x = y FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT ! b AND b FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x IS NULL FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT COUNT(*) FROM pg HAVING !b",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"CALL x!y()",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"CALL x!y(1)",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT db.sch.x!y()",
                "REFUSED SQL compilation error:\nUnknown user-defined function DB.SCH.X!Y."},
            {"SELECT \"x\"!y()",
                "REFUSED SQL compilation error:\nUnknown function \"x\"!Y."},
            {"SELECT x!y(1, 2) z FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!y()::INT FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!y().z FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 12 unexpected '.'."},
            {"SELECT x!y() + 1 FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT -x!y() FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!y() OVER () FROM pg",
                "REFUSED SQL compilation error:\nInvalid function type [X!Y] for window function."},
            {"SELECT * FROM x!y()",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 15 unexpected '!'."},
            {"SELECT nosuchfn(1), x!y() FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions NOSUCHFN, X!Y."},
            {"SELECT x!y(), nosuchfn(1) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions X!Y, NOSUCHFN."},
            {"SELECT nosuchfn(1), !x FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions NOSUCHFN, \"!\"."},
            {"SELECT !x, nosuchfn(1) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", NOSUCHFN."},
            {"SELECT !x!y() FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions X!Y, \"!\"."},
            {"SELECT x!y(!b) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", X!Y."},
            {"SELECT x!y(nosuchfn(1)) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions NOSUCHFN, X!Y."},
            {"SELECT nosuchfn(x!y()) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions X!Y, NOSUCHFN."},
            {"SELECT NOT NOT !b FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !NOT b FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT ! ! b FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", \"!\"."},
            {"SELECT (!(!b)) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", \"!\"."},
            {"SELECT 1 WHERE !TRUE",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT 1 FROM pg WHERE !b AND !b",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", \"!\"."},
            {"SELECT 1 FROM pg WHERE b AND ! b",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT IFF(!b, 1, 2) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT X!Y()",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!Y()",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT \"x\"!\"y\"()",
                "REFUSED SQL compilation error:\nUnknown function \"x\"!\"y\"."},
            {"SELECT sch.x!y()",
                "REFUSED SQL compilation error:\nUnknown user-defined function SCH.X!Y."},
            {"SELECT a.b.c.d!y()",
                "REFUSED SQL compilation error:\nUnknown user-defined function A.B.C.D!Y."},
            {"SELECT x!y(*) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!y(a => 1)",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT x!y",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 10 unexpected '<EOF>'."},
            {"SELECT x ! y FROM pg",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 13 unexpected 'FROM'."},
            {"SELECT !x y FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x AS y FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !(SELECT TRUE)",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !EXISTS (SELECT 1)",
                "false"},
            {"SELECT ![1]",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !-1",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !+1",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT ! :v",
                "REFUSED SQL compilation error: error line 1 at position 9\nBind variable :v not set."},
            {"SELECT !x::INT FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !b::INT FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x + 1 FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x || 'a' FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x IN (1) FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x BETWEEN 1 AND 2 FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !x LIKE 'a' FROM pg",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !b OR nosuchfn(1) FROM pg",
                "REFUSED SQL compilation error:\nUnknown functions \"!\", NOSUCHFN."},
            {"SELECT 1 = ANY 1, !b FROM pg",
                "REFUSED SQL compilation error:\nInvalid query block: 1."},
            {"SELECT !b, 1 = ANY 1 FROM pg",
                "REFUSED SQL compilation error:\nInvalid query block: 1."},
            {"SELECT !b, x OVER () FROM pg",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER (), !b FROM pg",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x!y(), x OVER () FROM pg",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT 1 = ANY x!y() FROM pq",
                "REFUSED SQL compilation error:\nInvalid query block: x."},
            {"SELECT 1 = ANY !b FROM pq",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 15 unexpected '!'."},
            {"SELECT 1 = ANY (!b) FROM pq",
                "REFUSED SQL compilation error:\nInvalid query block: !."},
            {"SELECT !EXISTS (SELECT 1 WHERE FALSE)",
                "true"},
            {"SELECT 1 WHERE !EXISTS (SELECT 1 WHERE FALSE)",
                "1"},
            {"SELECT !(EXISTS (SELECT 1))",
                "false"},
            {"SELECT ! NOT EXISTS (SELECT 1)",
                "true"},
            {"SELECT !!EXISTS (SELECT 1)",
                "true"},
            {"SELECT !EXISTS (SELECT 1) AND TRUE",
                "false"},
            {"SELECT !x IN (SELECT 1) FROM pq",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !TRUE = FALSE",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT NOT EXISTS (SELECT 1), !EXISTS (SELECT 1)",
                "false|false"},
            {"SELECT IFF(!EXISTS (SELECT 1), 'n', 'y')",
                "y"},
            {"SELECT ! EXISTS (SELECT 1 FROM pq WHERE x = 2)",
                "true"},
            {"SELECT x FROM pq WHERE !EXISTS (SELECT 1 FROM pq p2 WHERE p2.x = pq.x + 1)",
                "1"},
            {"SELECT !b IS NULL FROM pq",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT !b IS NOT NULL FROM pq",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT x!y() FROM pg ORDER BY 1",
                "REFUSED SQL compilation error:\nUnknown function X!Y."},
            {"SELECT 1 FROM pg QUALIFY !b",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT 1 FROM pg HAVING !b",
                "REFUSED SQL compilation error:\nUnknown function \"!\"."},
            {"SELECT x FROM pg WHERE x IN (SELECT 1 WHERE !EXISTS (SELECT 1))",
                ""},
    };

    /** A statement's answer as one text: a bar between cells, " ;; " between rows; a refusal as REFUSED and its words. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            boolean firstRow = true;
            while (rs.next()) {
                out.append(firstRow ? "" : " ;; ");
                firstRow = false;
                for (int c = 0; c < rs.getColumnCount(); c++) {
                    out.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** Each statement answers the given text, or is refused in the given words. */
    private void assertAnswers(final String[][] cases) {
        for (final String[] one : cases) {
            final String answered = answer(one[0]);
            if (one[1].startsWith("REFUSED ")) {
                assertTrue(answered.startsWith("REFUSED ") && answered.contains(one[1].substring("REFUSED ".length())),
                    one[0] + " -> " + answered);
            } else {
                assertEquals(one[1], answered, one[0]);
            }
        }
    }

    @Test
    public void aBangNegatesAnExistsAndIsOtherwiseAnUnknownFunction() {
        engine.execute("CREATE OR REPLACE TABLE pq (x INT, a ARRAY, v VARIANT, b BOOLEAN)");
        engine.execute("INSERT INTO pq SELECT 1, ARRAY_CONSTRUCT(1, 2), PARSE_JSON('{\"a\": 1}'), TRUE");
        engine.execute("CREATE OR REPLACE TABLE po (x INT, a ARRAY)");
        engine.execute("INSERT INTO po SELECT 1, ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE pg (x INT, y INT, b BOOLEAN)");
        engine.execute("INSERT INTO pg VALUES (1, 2, TRUE)");
        assertAnswers(CASES);
    }
}
