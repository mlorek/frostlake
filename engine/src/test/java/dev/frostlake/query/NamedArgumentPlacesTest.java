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

import org.junit.jupiter.api.Test;

/**
 * A positional argument written after a named one is refused at the argument's own place (live-verified): an
 * operator at the operator, a typed literal at its string, a COLLATE at the word, a NOT IN list at the NOT and an
 * IS DISTINCT FROM at the IS. A star among the arguments is read too, a qualified one placed at its qualifier
 * and a bare one at line 0, position 0.
 */
public class NamedArgumentPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentStandsWhereItsOperatorDoes() {
        engine.execute("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, s VARCHAR, c INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a', 1)");
        assertCells(new String[][] {
            {"SELECT n1(a => 1, *) FROM t1",
                "REFUSED SQL compilation error: error line 0 at position 0 | positional argument cannot follow named "
                + "argument in function call 'N1'"},
            {"SELECT n1(a => 1, t1.*) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c, *) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, DATE '2020-01-01') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, TIMESTAMP '2020-01-01 00:00:00') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 28 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s COLLATE 'en') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s COLLATE 'en' || 'x') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 33 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c NOT IN (1)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c IN (1)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c NOT IN (SELECT 1)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c IN (SELECT 1)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c + 1) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c * 2 + 1) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c BETWEEN 1 AND 2) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c NOT BETWEEN 1 AND 2) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s LIKE 'a') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s NOT LIKE 'a') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s ILIKE 'a') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s RLIKE 'a') FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, s LIKE ANY ('a')) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c IS NULL) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c IS NOT NULL) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c IS DISTINCT FROM 1) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, NOT c) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, -c) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c::INT) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, CAST(c AS INT)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, (c)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c = 1) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, c AND TRUE) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, ABS(c)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, t1.c) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, EXISTS (SELECT 1)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, CASE WHEN c = 1 THEN 1 END) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, (SELECT 1)) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, [1]) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, {'a': 1}) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, PARSE_JSON('1'):a) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 33 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(a => 1, 1) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT n1(*, a => 1) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N1(T1.X, T1.S, T1.C, 1)] expected 2, got 4"},
            {"SELECT n1(b => 1, a => 2, *) FROM t1",
                "REFUSED SQL compilation error: error line 0 at position 0 | positional argument cannot follow named "
                + "argument in function call 'N1'"},
        });
    }
}
