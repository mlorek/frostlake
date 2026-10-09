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
 * OVER written after what is no call of a function. The window is read, and what stands before it refused while
 * the statement compiles: a value that can be no function - a column, a literal, a bind, a call that already has
 * its window - as {@code invalid function '<value>'}, before a table or a function of the statement is looked for,
 * and an operation the account names as a function - a cast, a CASE, an array, a path, an operator in parentheses -
 * as {@code Invalid function type [<name>] for window function.} OVER binds tighter than a sign and every operator
 * beside it. Every row is live-verified.
 */
public class WindowAfterValueRefusalTest extends BaseDatabaseTest {

    /** A statement, and its answer or the words it is refused in. */
    private static final String[][] CASES = {
            {"SELECT x OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT 1 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT x OVER (ORDER BY x) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT (x) OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT po.x OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'PO.X'"},
            {"SELECT 'a' OVER ()",
                "REFUSED SQL compilation error:\ninvalid function ''a''"},
            {"SELECT x + 1 OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT NULL OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'null'"},
            {"SELECT CURRENT_DATE OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CURRENT_DATE] for window function."},
            {"SELECT x OVER (PARTITION BY x) y FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT \"x\" OVER () FROM (SELECT 1 \"x\")",
                "REFUSED SQL compilation error:\ninvalid function '\"x\"'"},
            {"SELECT x::INT OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT TRUE OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'TRUE'"},
            {"SELECT x OVER () OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT SUM(x) OVER () OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'SUM(X) OVER ()'"},
            {"SELECT nosuch OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'NOSUCH'"},
            {"SELECT nosuch(1) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [NOSUCH] for window function."},
            {"SELECT ABS(x) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [ABS] for window function."},
            {"SELECT x OVER (ORDER BY nosuch) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT 1.5 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '1.5'"},
            {"SELECT -x OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER (), 1 FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT * FROM po WHERE x OVER () = 1",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT $1 OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function '$1'"},
            {"SELECT x OVER ( FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 16 unexpected 'FROM'."},
            {"SELECT x OVER () AS y FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT CASE WHEN TRUE THEN 1 END OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CASE] for window function."},
            {"SELECT [1] OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [ARRAY_CONSTRUCT] for window function."},
            {"SELECT x:a OVER () FROM (SELECT PARSE_JSON('{}') x)",
                "REFUSED SQL compilation error:\nInvalid function type [GET] for window function."},
            {"SELECT x OVER (ORDER BY x) OVER (ORDER BY x) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT t.x OVER () FROM po t",
                "REFUSED SQL compilation error:\ninvalid function 'T.X'"},
            {"SELECT a.b.c OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'A.B.C'"},
            {"SELECT x y OVER () FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 11 unexpected 'OVER'."},
            {"SELECT x AS y OVER () FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 14 unexpected 'OVER'."},
            {"SELECT DISTINCT x OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT ROW_NUMBER OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'ROW_NUMBER'"},
            {"SELECT current_timestamp OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CURRENT_TIMESTAMP] for window function."},
            {"SELECT x OVER () FROM nosuch_table",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT (x + 1) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [+] for window function."},
            {"SELECT (-x) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [NEGATE] for window function."},
            {"SELECT (x = 1) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [=] for window function."},
            {"SELECT (x > 0 AND TRUE) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [AND] for window function."},
            {"SELECT (x IS NULL) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [IS NULL] for window function."},
            {"SELECT (x || 'a') OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [||] for window function."},
            {"SELECT (x * 2) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [*] for window function."},
            {"SELECT DATE '2020-01-01' OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [TO_DATE] for window function."},
            {"SELECT :v OVER ()",
                "REFUSED SQL compilation error:\ninvalid function ':v'"},
            {"SELECT ? OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '?'"},
            {"SELECT {} OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [OBJECT_CONSTRUCT] for window function."},
            {"SELECT a[0] OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [GET] for window function."},
            {"SELECT Po.X OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'PO.X'"},
            {"SELECT \"A\".\"b\" OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'A.\"b\"'"},
            {"SELECT 1e5 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '100000'"},
            {"SELECT -1 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT FALSE OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'FALSE'"},
            {"SELECT null OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'null'"},
            {"SELECT true OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'TRUE'"},
            {"SELECT (NULL) OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'null'"},
            {"SELECT CAST(x AS INT) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT x OVER () OVER () OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT EXISTS (SELECT 1) OVER ()",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 30 unexpected '('."},
            {"SELECT NOT x OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER (PARTITION BY nosuch) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT COALESCE(x, 1) OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [COALESCE] for window function."},
            {"SELECT IFF(TRUE, 1, 2) OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [IFF] for window function."},
            {"SELECT x IS NULL OVER () FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 22 unexpected '('."},
            {"SELECT x = 1 OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT 'a' || 'b' OVER ()",
                "REFUSED SQL compilation error:\ninvalid function ''b''"},
            {"SELECT x::INT::VARCHAR OVER () FROM po",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT TRY_CAST('1' AS INT) OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [TRY_CAST] for window function."},
            {"SELECT INTERVAL '1 day' OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [INTERVAL_LITERAL] for window function."},
            {"SELECT CURRENT_USER OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CURRENT_USER] for window function."},
            {"SELECT SYSDATE() OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [SYSDATE] for window function."},
            {"SELECT x OVER (ORDER BY 1) + 1 FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT 'it''s' OVER ()",
                "REFUSED SQL compilation error:\ninvalid function ''it''s''"},
            {"SELECT 1.50 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '1.5'"},
            {"SELECT 001 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT .5 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '0.5'"},
            {"SELECT 12345678901234567890 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '12345678901234567890'"},
            {"SELECT $$a$$ OVER ()",
                "REFUSED SQL compilation error:\ninvalid function ''a''"},
            {"SELECT x.y.z.w OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X.Y.Z.W'"},
            {"SELECT \"x y\" OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function '\"x y\"'"},
            {"SELECT x OVER (), y OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT 2 OVER (), 1 OVER ()",
                "REFUSED SQL compilation error:\ninvalid function '2'"},
            {"SELECT x OVER (), nosuchfn(1) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT nosuchfn(1), x OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER () FROM po WHERE nosuchfn(1) = 1",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT ABS(x) OVER (), y OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'Y'"},
            {"SELECT y OVER (), ABS(x) OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'Y'"},
            {"SELECT x OVER () FROM po ORDER BY",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 33 unexpected '<EOF>'."},
            {"SELECT (x OVER ()) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT ABS(x OVER ()) FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER () IS NULL FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT x OVER ()::INT FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT * FROM po QUALIFY x OVER () = 1",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT * FROM po ORDER BY x OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT COUNT(*) FROM po GROUP BY x OVER ()",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT COUNT(x) OVER () OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'COUNT(X) OVER ()'"},
            {"SELECT MAX(x) OVER (PARTITION BY x) OVER () FROM po",
                "REFUSED SQL compilation error:\ninvalid function 'MAX(X) OVER (PARTITION BY X)'"},
            {"SELECT x OVER (ORDER BY x) y OVER () FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 29 unexpected 'OVER'."},
            {"SELECT 1 OVER (ORDER BY nosuch)",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT x OVER (ORDER BY) FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 23 unexpected ')'."},
            {"SELECT x OVER (ORDER BY x ROWS) FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 30 unexpected ')'."},
            {"SELECT x OVER () () FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 18 unexpected ')'."},
            {"SELECT x OVER ()) FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 16 unexpected ')'."},
            {"SELECT x OVER (ORDER BY x, ) FROM po",
                "REFUSED SQL compilation error:\nsyntax error line 1 at position 27 unexpected ')'."},
            {"SELECT TIMESTAMP '2020-01-01 00:00:00' OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [TO_TIMESTAMP] for window function."},
            {"SELECT TIME '00:00:00' OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [TO_TIME] for window function."},
            {"SELECT (x <> 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [<>] for window function."},
            {"SELECT (x < 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [<] for window function."},
            {"SELECT (x - 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [-] for window function."},
            {"SELECT (x / 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [/] for window function."},
            {"SELECT (x % 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [%] for window function."},
            {"SELECT (b OR TRUE) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [OR] for window function."},
            {"SELECT (NOT b) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [NOT] for window function."},
            {"SELECT (x IS NOT NULL) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [IS NOT NULL] for window function."},
            {"SELECT (x IN (1)) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [IN] for window function."},
            {"SELECT (x LIKE 'a') OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [LIKE] for window function."},
            {"SELECT (x BETWEEN 1 AND 2) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [BETWEEN] for window function."},
            {"SELECT (x IS DISTINCT FROM 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [NOT] for window function."},
            {"SELECT (+x) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [UNARY PLUS] for window function."},
            {"SELECT ('a' COLLATE 'de') OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [COLLATE] for window function."},
            {"SELECT CURRENT_TIMESTAMP() OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CURRENT_TIMESTAMP] for window function."},
            {"SELECT LOCALTIMESTAMP OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [LOCALTIMESTAMP] for window function."},
            {"SELECT CURRENT_TIME OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CURRENT_TIME] for window function."},
            {"SELECT x::INT OVER (), ABS(x) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT x::INT OVER (), nosuchfn(1) FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT nosuchfn(1), x::INT OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT (1) OVER (ORDER BY 1)",
                "REFUSED SQL compilation error:\ninvalid function '1'"},
            {"SELECT (x >= 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [>=] for window function."},
            {"SELECT (x <= 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [<=] for window function."},
            {"SELECT (x > 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [>] for window function."},
            {"SELECT (x != 1) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [!=] for window function."},
            {"SELECT (x NOT IN (1)) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [NOT] for window function."},
            {"SELECT (x NOT LIKE 'a') OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [NOT] for window function."},
            {"SELECT (x ILIKE 'a') OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [ILIKE] for window function."},
            {"SELECT (x RLIKE 'a') OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [RLIKE] for window function."},
            {"SELECT (x::INT) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [CAST] for window function."},
            {"SELECT (ABS(x)) OVER () FROM pq",
                "REFUSED SQL compilation error:\nInvalid function type [ABS] for window function."},
            {"SELECT (CASE WHEN TRUE THEN 1 END) OVER ()",
                "REFUSED SQL compilation error:\nInvalid function type [CASE] for window function."},
            {"SELECT ((x)) OVER () FROM pq",
                "REFUSED SQL compilation error:\ninvalid function 'X'"},
            {"SELECT COUNT(*) OVER () FROM pg",
                "1"},
            {"SELECT COUNT(DISTINCT x) OVER () FROM pg",
                "1"},
            {"SELECT COUNT(pg.*) OVER () FROM pg",
                "1"},
            {"SELECT COUNT(* EXCLUDE y) OVER () FROM pg",
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
    public void aWindowAfterAValueIsRefusedByTheValue() {
        engine.execute("CREATE OR REPLACE TABLE pq (x INT, a ARRAY, v VARIANT, b BOOLEAN)");
        engine.execute("INSERT INTO pq SELECT 1, ARRAY_CONSTRUCT(1, 2), PARSE_JSON('{\"a\": 1}'), TRUE");
        engine.execute("CREATE OR REPLACE TABLE po (x INT, a ARRAY)");
        engine.execute("INSERT INTO po SELECT 1, ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE pg (x INT, y INT, b BOOLEAN)");
        engine.execute("INSERT INTO pg VALUES (1, 2, TRUE)");
        assertAnswers(CASES);
    }
}
