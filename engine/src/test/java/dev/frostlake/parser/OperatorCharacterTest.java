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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The characters live reads as operators: the bar refused as an unsupported feature before the caret, the
 * ampersand and a leading tilde calls of functions no account has, the bang's lines by what stands before it
 * (all live-verified).
 */
public class OperatorCharacterTest extends MeasuredCellsTestSupport {

    @Test
    public void operatorCharacters() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1 |");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '|'.",
            "SELECT | 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '|'.", "SELECT |1");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT a | b FROM t1");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 FROM t1 WHERE a | 2");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 FROM t1 WHERE a | 2 = 0");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2 FROM nosuch_t_zq7");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT nosuch_c | 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'.",
            "SELECT 1 | 2 x y");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT ABS(1 | 2)");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2, 3 | 4");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2 | 3");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 + 2 | 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1 | FROM t1");
        assertAnswer("12", "SELECT 1 || 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '|'.", "SELECT |");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 'a' | 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "SELECT 1 | 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1 ^");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '^'.",
            "SELECT ^ 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '^'.", "SELECT ^1");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT a ^ b FROM t1");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 FROM t1 WHERE a ^ 2");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 FROM t1 WHERE a ^ 2 = 0");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ 2 FROM nosuch_t_zq7");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT nosuch_c ^ 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'.",
            "SELECT 1 ^ 2 x y");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT ABS(1 ^ 2)");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ 2, 3 ^ 4");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ 2 ^ 3");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 + 2 ^ 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1 ^ FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '^'.",
            "SELECT 1 ^^ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '^'.", "SELECT ^");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 'a' ^ 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "SELECT 1 ^ 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 & 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1 &");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '&'.",
            "SELECT & 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '&'.", "SELECT &1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT a & b FROM t1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 FROM t1 WHERE a & 2");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 FROM t1 WHERE a & 2 = 0");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_ZQ7.",
            "SELECT 1 & 2 FROM nosuch_t_zq7");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH_C'",
            "SELECT nosuch_c & 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'.",
            "SELECT 1 & 2 x y");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT ABS(1 & 2)");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"&\", \"&\".", "SELECT 1 & 2, 3 & 4");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"&\", \"&\".", "SELECT 1 & 2 & 3");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 + 2 & 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1 & FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '&'.",
            "SELECT 1 && 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '&'.", "SELECT &");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 'a' & 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "SELECT 1 & 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'.",
            "SELECT 1 ! 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "SELECT 1 !");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"!\".", "SELECT ! 1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"!\".", "SELECT !1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'.",
            "SELECT a ! b FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'.",
            "SELECT 1 FROM t1 WHERE a ! 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'.",
            "SELECT 1 FROM t1 WHERE a ! 2 = 0");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'.",
            "SELECT 1 ! 2 FROM nosuch_t_zq7");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax "
                + "error line 1 at position 16 unexpected '!'.",
            "SELECT nosuch_c ! 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT 1 ! 2 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '2'.",
            "SELECT ABS(1 ! 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 18 unexpected '4'.",
            "SELECT 1 ! 2, 3 ! 4");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '3'.",
            "SELECT 1 + 2 ! 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT 1 ! FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '!'.",
            "SELECT 1 !! 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "SELECT !");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ''b''.",
            "SELECT 'a' ! 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 29 unexpected 'b'.",
            "SELECT 1 ! 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$'.",
            "SELECT $ 1");
        assertAnswer("null", "SELECT $1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT a $ b FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '$'.",
            "SELECT 1 FROM t1 WHERE a $ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '$'.",
            "SELECT 1 FROM t1 WHERE a $ 2 = 0");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $ 2 FROM nosuch_t_zq7");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '$'.",
            "SELECT nosuch_c $ 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '$'.",
            "SELECT ABS(1 $ 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 16 unexpected '$'.",
            "SELECT 1 $ 2, 3 $ 4");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $ 2 $ 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '$'.",
            "SELECT 1 + 2 $ 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $ FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$'.", "SELECT $");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '$'.",
            "SELECT 'a' $ 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 29 unexpected 'b'.",
            "SELECT 1 $ 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"!\".", "SELECT !TRUE");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"!\".", "SELECT ! TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '='.",
            "SELECT 1 ! = 2");
        assertAnswer("true", "SELECT 1 != 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ','.",
            "SELECT 1 !, 2");
        assertAnswer("1", "SELECT $1 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$a'.",
            "SELECT 1 $a");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$$a$$'.",
            "SELECT 1 $$a$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '~'.",
            "SELECT 1 ~ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '?'.",
            "SELECT 1 ? 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '@'.",
            "SELECT 1 @ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\\ '.",
            "SELECT 1 \\ 2");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2 & 3 ^ 4");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 FROM t1 WHERE a = 1 | 2");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 FROM t1 ORDER BY a | 2");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 FROM t1 GROUP BY a & 2");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2 FROM t1 WHERE nosuch_c = 1");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT CASE WHEN 1 | 2 THEN 1 END");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 FROM t1 WHERE a IN (1 | 2)");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2 AS x");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ 2 AS x FROM t1 ORDER BY x");
        assertAnswer("REFUSED Unsupported feature '|'.", "BEGIN RETURN 1 | 2; END");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unknown function \"&\".",
            "BEGIN RETURN 1 & 2; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '2'.",
            "BEGIN RETURN 1 ! 2; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '$'.",
            "BEGIN RETURN 1 $ 2; END");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions NOSUCH_F, \"&\".", "SELECT 1 & nosuch_f(2)");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions NOSUCH_F, \"&\".", "SELECT nosuch_f(1) & 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "SELECT 1 & 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ ABS(1, 2)");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT ABS(1, 2) | 1");
    }

    @Test
    public void operatorPrecedenceAndPlaces() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 ^ 2 | 3");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 & 2 | 3");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 1 | 2 ^ 3");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 ^ 2 & 3");
        assertAnswer("REFUSED Unsupported feature '^'.", "SELECT 1 & 2 ^ 3");
        assertAnswer("REFUSED Unsupported feature '|'.", "SELECT 2 * 3 | 4");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions NOSUCH_F, NOSUCH_G, \"&\".",
            "SELECT nosuch_f(1) & nosuch_g(2)");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"&\", NOSUCH_F, \"&\".",
            "SELECT 1 & 2 & nosuch_f(3)");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"&\", NOSUCH_F.", "SELECT nosuch_f(1 & 2)");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"&\", NOSUCH_F.",
            "SELECT 1 & 2, nosuch_f(3)");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"~\", \"&\".", "SELECT ~1 & 2");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions NOSUCH_F, \"~\".", "SELECT ~nosuch_f(1)");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_ZQ7.",
            "SELECT 1 & 2 FROM nosuch_t_zq7 WHERE nosuch_c = 1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~ 1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~'a'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT 1 + ~2");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_T_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_ZQ7.",
            "SELECT ~1 FROM nosuch_t_zq7");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~a FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH_C'",
            "SELECT ~nosuch_c FROM t1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT 1 FROM t1 WHERE ~a = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'y'.",
            "SELECT ~1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "SELECT ~");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions \"~\", \"~\".", "SELECT ~1, ~2");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unknown function \"~\".",
            "BEGIN RETURN ~1; END");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~(1)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~1::INT");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~1 * 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '~'.",
            "SELECT 1 ~");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 9 unexpected '!'.",
            "SELECT a ! 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 9 unexpected '!'.",
            "SELECT a ! 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 9 unexpected '!'.",
            "SELECT a ! 2 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'.",
            "SELECT 1 FROM t1 WHERE a ! 2 AND b = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax "
                + "error line 1 at position 9 unexpected '.'.",
            "SELECT t1.a ! 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'.",
            "SELECT a ! b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax "
                + "error line 1 at position 16 unexpected '!'.",
            "SELECT nosuch_c ! 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax "
                + "error line 1 at position 16 unexpected '!'.",
            "SELECT nosuch_c ! 2 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 9 unexpected '!'.",
            "SELECT a ! 2, b FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'.",
            "SELECT 1 FROM t1 WHERE a ! 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'.",
            "SELECT 1 FROM t1 ORDER BY a ! 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''x''. | syntax "
                + "error line 1 at position 9 unexpected '!'.",
            "SELECT a ! 'x' FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '2'. | syntax "
                + "error line 1 at position 10 unexpected '!'.",
            "SELECT (a ! 2) FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '2'.",
            "SELECT ABS(a ! 2) FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'. | syntax "
                + "error line 1 at position 9 unexpected '!'.",
            "SELECT a ! 2 FROM t1 WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'.",
            "SELECT 1 ! 2 FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '2'.",
            "SELECT (1 ! 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $ 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "SELECT a $ b FROM t1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'.",
            "SELECT 1 | 2 | 3 FROM t1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 | 2 &");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT (1 | 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT 1 | (2");
    }
}
