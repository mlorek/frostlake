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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIKE ANY, LIKE ALL and ILIKE ANY take one pattern without parentheses: {@code c LIKE ANY 'a%'}. The pattern is
 * a value as tight as a concatenation or a sum, and an ESCAPE follows it as it follows a list. A query written as
 * LIKE ANY's pattern gives a pattern per row it answers, where LIKE ALL reads it as one value. NOT LIKE ANY,
 * ILIKE ALL, LIKE SOME and RLIKE ANY stay the syntax errors they are. Every row is live-verified.
 */
public class LikeAnySinglePatternTest extends BaseDatabaseTest {

    /** A statement, and its answer or the words it is refused in. */
    private static final String[][] CASES = {
            {"SELECT 1 LIKE ANY 1",
                "true"},
            {"SELECT 'a' LIKE ANY 'a'",
                "true"},
            {"SELECT 'a' LIKE ALL 'a%'",
                "true"},
            {"SELECT 'a' ILIKE ANY 'A'",
                "true"},
            {"SELECT 'a' LIKE ANY 'b'",
                "false"},
            {"SELECT 'a' LIKE ANY x FROM (SELECT 'a' x)",
                "true"},
            {"SELECT 'a' LIKE ANY 'a' ESCAPE '!'",
                "true"},
            {"SELECT 'a' LIKE ANY 'a', 'b'",
                "true|b"},
            {"SELECT 'a' LIKE ANY 'a' || 'b'",
                "false"},
            {"SELECT 'a' LIKE ANY NULL",
                "null"},
            {"SELECT 'a' NOT LIKE ANY 'a'",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 20 unexpected 'ANY'."},
            {"SELECT 'a' ILIKE ALL 'A'",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 17 unexpected 'ALL'."},
            {"SELECT 'a' LIKE SOME 'a'",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 16 unexpected 'SOME'."},
            {"SELECT 'a' LIKE ANY ('a')",
                "true"},
            {"SELECT 'a' LIKE ANY (SELECT 'a')",
                "true"},
            {"SELECT 'a' LIKE ANY 1 + 1",
                "false"},
            {"SELECT 'a' LIKE ANY 'a' AND TRUE",
                "true"},
            {"SELECT 'a' RLIKE ANY 'a'",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 17 unexpected 'ANY'."},
            {"SELECT 'a' LIKE ANY",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 19 unexpected '<EOF>'."},
            {"SELECT 'ab' LIKE ALL 'a%' 'x'",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 26 unexpected ''x''."},
            {"SELECT 'a' LIKE ANY (SELECT column1 FROM VALUES ('b'), ('a'))",
                "true"},
            {"SELECT 'a' LIKE ALL (SELECT column1 FROM VALUES ('b'), ('a'))",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT 'a' LIKE ANY (SELECT column1 FROM VALUES ('b'), ('c'))",
                "false"},
            {"SELECT 'ab' LIKE ANY ('a') || 'b'",
                "true"},
            {"SELECT 'a' LIKE ANY -1",
                "false"},
            {"SELECT 'a' LIKE ANY pq.x FROM pq",
                "false"},
            {"SELECT 'a' LIKE ANY 'a' IS NULL",
                "REFUSED SQL compilation error: error line 1 at position 11\nInvalid argument types for function 'LIKE_ANY': (VARCHAR(1), NULL, BOOLEAN)"},
            {"SELECT 'a' LIKE ANY 'a' = TRUE",
                "true"},
            {"SELECT 'a' LIKE ANY 'a' LIKE ANY 'b'",
                "REFUSED SQL compilation error: error line 1 at position 24\nInvalid argument types for function 'LIKE_ANY': (BOOLEAN, NULL, VARCHAR(1))"},
            {"SELECT 'a' ILIKE ANY 'A' ESCAPE '!'",
                "true"},
            {"SELECT x LIKE ANY y FROM (SELECT 'a' x, 'a' y)",
                "true"},
            {"SELECT 'a%' LIKE ANY 'a!%' ESCAPE '!'",
                "true"},
            {"SELECT 'a' LIKE ALL 'a' ESCAPE '!'",
                "true"},
            {"SELECT 'a' LIKE ANY 'a', 'a' LIKE ALL 'b'",
                "true|false"},
            {"SELECT * FROM (SELECT 'a' x) WHERE x LIKE ANY 'a'",
                "a"},
            {"SELECT 'a' LIKE ANY ('a', 'b') ESCAPE '!'",
                "true"},
            {"SELECT 'a' LIKE ANY 'a' ESCAPE",
                "true"},
            {"SELECT 'a' LIKE ANY ()",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 21 unexpected ')'."},
            {"SELECT 'a' LIKE ANY (('a'))",
                "true"},
            {"SELECT 'a' LIKE ANY (SELECT 'x' WHERE FALSE)",
                "false"},
            {"SELECT 'a' LIKE ANY (SELECT NULL)",
                "null"},
            {"SELECT 'a' LIKE ANY (SELECT column1 FROM VALUES ('b'), (NULL))",
                "false"},
            {"SELECT 'a' ILIKE ANY (SELECT 'A')",
                "true"},
            {"SELECT 'a' LIKE ANY (SELECT 'a') ESCAPE '!'",
                "true"},
            {"SELECT 'a' LIKE ALL (SELECT 'a')",
                "true"},
            {"SELECT x FROM pg WHERE 'ab' LIKE ANY (SELECT column1 FROM VALUES ('b%'), ('a%'))",
                "1"},
            {"SELECT x FROM pg WHERE x::VARCHAR LIKE ANY (SELECT p2.x::VARCHAR FROM pg p2 WHERE p2.y = pg.y)",
                "1"},
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
    public void onePatternNeedsNoParentheses() {
        engine.execute("CREATE OR REPLACE TABLE pq (x INT, a ARRAY, v VARIANT, b BOOLEAN)");
        engine.execute("INSERT INTO pq SELECT 1, ARRAY_CONSTRUCT(1, 2), PARSE_JSON('{\"a\": 1}'), TRUE");
        engine.execute("CREATE OR REPLACE TABLE po (x INT, a ARRAY)");
        engine.execute("INSERT INTO po SELECT 1, ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE pg (x INT, y INT, b BOOLEAN)");
        engine.execute("INSERT INTO pg VALUES (1, 2, TRUE)");
        assertAnswers(CASES);
    }
}
