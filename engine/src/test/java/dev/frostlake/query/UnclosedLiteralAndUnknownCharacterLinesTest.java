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
 * The lines of a text the lexer gives up on. A string left unclosed loses its quote and its words are read on;
 * a quoted name, a comment and a binary literal left unclosed take everything to the end; the lexer's own line,
 * told at the end of the text, stands among the parser's by the place of the fault. A character no token opens
 * with is a token of two characters refused where it stands: after a select list's value the reading goes on
 * at the next alias, comma or clause, in a list of keys at the next comma, elsewhere the statement is given up,
 * and what is refused straight after the token is not told (live-verified).
 */
public class UnclosedLiteralAndUnknownCharacterLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aCharacterNoTokenOpensWith() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '# '.",
            "BEGIN SELECT 1 # 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '# '.",
            "BEGIN SELECT 1 # 2; SELECT 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '# '.",
            "BEGIN SELECT 1 # 2; RETURN 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '# '.",
            "BEGIN SELECT 1; SELECT 1 # 2; SELECT 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 41 unexpected '# '.",
            "BEGIN SHOW TABLES IN DATASET y; SELECT 1 # 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 41 unexpected '# '. | syntax error line 1 at position 56 unexpected 'END'.",
            "BEGIN SHOW TABLES IN DATASET y; SELECT 1 # 2; SELECT 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax error "
                + "line 1 at position 37 unexpected '# '.",
            "BEGIN SELECT 1 FROM t1 x y; SELECT 1 # 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '#'.",
            "SELECT 1 #");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '#'.",
            "SELECT #");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '# '.",
            "SELECT # 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '#2'.",
            "SELECT 1 #2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '##'.",
            "SELECT 1 ## 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # # 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '#2'.",
            "SELECT 1#2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '# '.",
            "SELECT 1 FROM t1 WHERE a # 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\\ '.",
            "SELECT 1 \\ 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\\'.",
            "SELECT 1 \\");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '\u00e9'.",
            "SELECT \u00e9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\u00e9 '.",
            "SELECT 1 \u00e9 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '\u00e9 '.",
            "SELECT a\u00e9 FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '# '.",
            "SELECT 1 FROM t1 # x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '# '.",
            "SELECT 1 FROM # t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '# '.",
            "SELECT # FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '#)'.",
            "CREATE TABLE tq (a INT #)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '#)'.",
            "INSERT INTO t1 VALUES (1 #)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '#)'.",
            "INSERT INTO t1 VALUES (#)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '# '.",
            "UPDATE t1 SET a = 1 # 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '#'.",
            "DELETE FROM t1 WHERE a = 1 #");
    }

    @Test
    public void unclosedLiteralsInABlockAndInAScript() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | parse error "
                + "line 1 at position 49 near '<EOF>'. | syntax error line 1 at position 45 unexpected 'END'.",
            "BEGIN SHOW TABLES IN DATASET y; SELECT 'abc; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | parse error "
                + "line 1 at position 49 near '<EOF>'. | syntax error line 1 at position 49 unexpected '<EOF>'.",
            "BEGIN SHOW TABLES IN DATASET y; SELECT \"abc; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | parse error "
                + "line 1 at position 59 near '<EOF>'. | syntax error line 1 at position 45 unexpected 'RETURN'.",
            "BEGIN SHOW TABLES IN DATASET y; SELECT 'abc; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax error "
                + "line 1 at position 28 unexpected 'y'. | parse error line 1 at position 48 near '<EOF>'.",
            "BEGIN SHOW TABLES IN USER x y; SELECT 'abc; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | parse error "
                + "line 1 at position 45 near '<EOF>'. | syntax error line 1 at position 41 unexpected 'END'.",
            "BEGIN SELECT 1 FROM t1 x y; SELECT 'abc; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | parse error "
                + "line 1 at position 33 near '<EOF>'.",
            "SELECT 1 FROM t1 x y; SELECT 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'. | syntax error line "
                + "1 at position 23 unexpected '<EOF>'.",
            "BEGIN SELECT \"abc; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'.",
            "SELECT 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax error line "
                + "1 at position 11 unexpected '<EOF>'.",
            "SELECT \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'.",
            "SELECT 1, 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'.",
            "SELECT 'abc FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'. | syntax error line "
                + "1 at position 19 unexpected '<EOF>'.",
            "SELECT \"abc FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'.",
            "SELECT 1; SELECT 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'.",
            "SELECT $$abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error line "
                + "1 at position 13 unexpected '<EOF>'.",
            "SELECT /* abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax error line "
                + "1 at position 11 unexpected '<EOF>'.",
            "SELECT X'AB");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'.",
            "SELECT 'a''b");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 29 near '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 'x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "SELECT 1 x y # 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 15 unexpected 'y'.",
            "SELECT 1 # 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "SELECT 1 x y; SELECT 2 # 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 25 unexpected 'y'.",
            "SELECT 1 # 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '# '.",
            "SELECT (1 # 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '# '.",
            "SELECT ABS(1 # 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '# '.",
            "SELECT 1 FROM (SELECT 1 # 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '# '.",
            "SHOW TABLES # x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 18 unexpected 'abc'.",
            "SHOW TABLES LIKE 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '# '.",
            "DESCRIBE TABLE t1 # x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '# '.",
            "BEGIN RETURN 1 # 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '# '.",
            "BEGIN LET x := 1 # 2; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'.",
            "BEGIN RETURN 'abc; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 35 near '<EOF>'.",
            "BEGIN LET x := 'abc; RETURN x; END;");
    }

    @Test
    public void theOperatorCharactersThatAreTokens() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '~'.",
            "SELECT 1 ~ 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '?'.",
            "SELECT 1 ? 2");
    }

    @Test
    public void whatIsReadAfterAnUnclosedLiteral() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'.",
            "SELECT 1 \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'.",
            "SELECT 1, \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'.",
            "SELECT 1 FROM t1 \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'.",
            "SELECT 1 /* abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'.",
            "SELECT 1 X'AB");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | syntax error line "
                + "1 at position 31 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 21 unexpected '<EOF>'.",
            "SELECT \"abc; SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 21 unexpected '<EOF>'.",
            "SELECT 1; SELECT \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'. | parse error "
                + "line 1 at position 17 near '<EOF>'.",
            "SELECT 1 x y \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'. | syntax error line "
                + "1 at position 15 unexpected '<EOF>'.",
            "SELECT \"abc x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | parse error "
                + "line 1 at position 33 near '<EOF>'. | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 x y; SELECT \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 18 near '<EOF>'. | syntax error line "
                + "1 at position 18 unexpected '<EOF>'.",
            "SELECT 1 FROM \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'.",
            "SELECT * FROM t1 /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 22 near '<EOF>'.",
            "SELECT 1 FROM t1; /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 30 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = X'A");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'.",
            "SELECT 1 FROM t1 LIMIT 1e");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'.",
            "SELECT 'abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 18 near '<EOF>'.",
            "SELECT 'abc', 'def");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 27 near '<EOF>'.",
            "SELECT 1 FROM t1 WHERE 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error line "
                + "1 at position 12 unexpected '1'.",
            "SELECT 'abc 1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 16 near '<EOF>'. | syntax error line "
                + "1 at position 16 unexpected '<EOF>'.",
            "SELECT 'abc FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'. | syntax error line "
                + "1 at position 16 unexpected 'ghi'.",
            "SELECT 'abc def ghi");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'.",
            "SELECT 1 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 25 near '<EOF>'. | syntax error line "
                + "1 at position 24 unexpected 'y'.",
            "SELECT 'abc; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 28 near '<EOF>'.",
            "INSERT INTO t1 VALUES ('abc)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 16 near '<EOF>'.",
            "SELECT 'a' || 'b");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 16 near '<EOF>'.",
            "SELECT $$abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '''.",
            "SELECT 'abc'def'");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error line "
                + "1 at position 13 unexpected 'c'.",
            "SELECT 'a''b c");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'.",
            "SELECT 'a''");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '''.",
            "SELECT '");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '''.",
            "SELECT 1 '");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 17 near '<EOF>'. | syntax error line "
                + "1 at position 14 unexpected 'y'.",
            "SELECT 'abc x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 20 near '<EOF>'. | syntax error line "
                + "1 at position 11 unexpected ')'.",
            "SELECT 'abc) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'. | syntax error line "
                + "1 at position 12 unexpected '<EOF>'.",
            "SELECT ('abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | parse error "
                + "line 1 at position 25 near '<EOF>'.",
            "SELECT 1 FROM t1 x y 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 27 near '<EOF>'.",
            "BEGIN SELECT 'abc def; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 37 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected 'y'. | syntax error line 1 at position 33 unexpected 'END'.",
            "BEGIN SELECT 1; SELECT 'abc x y; END;");
    }

    @Test
    public void whereTheReadingGoesOnAfterTheCharacter() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # 2 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 21 unexpected 'y'.",
            "SELECT 1 # FROM t1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '# '.",
            "SELECT # 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '# '.",
            "SELECT 1 FROM t1 # x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # 2 # 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 23 unexpected '# '.",
            "SELECT 1 # 2; SELECT 3 # 4");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '\u00e9 '. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT 1 \u00e9 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT 1 # 2 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 18 unexpected 'y'.",
            "SELECT 1 # 2, 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '#'.",
            "SELECT 1 #");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 24 unexpected 'y'.",
            "SELECT 1 # ; SELECT 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '# '.",
            "SELECT 1 FROM t1 WHERE a # 2 AND b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '# '.",
            "SELECT 1 FROM t1 WHERE a # 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '# '. | syntax error "
                + "line 1 at position 21 unexpected 'y'. | syntax error line 1 at position 24 unexpected 'END'.",
            "BEGIN SELECT 1 # 2 x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected '# '.",
            "BEGIN SELECT 1 x y # 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | parse error "
                + "line 1 at position 15 near '<EOF>'.",
            "SELECT 1 # 'abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | parse error "
                + "line 1 at position 15 near '<EOF>'.",
            "SELECT 1 # \"abc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'. | syntax error line "
                + "1 at position 12 unexpected '# '.",
            "SELECT 'abc # 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 23 unexpected 'y'.",
            "SELECT 1 # 2 FROM t1 x y z");
    }

    @Test
    public void keyListsOperatorsAndMoreLiterals() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '# '.",
            "SELECT 1 FROM t1 GROUP BY a # 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '# '. | syntax error "
                + "line 1 at position 35 unexpected 'x'.",
            "SELECT 1 FROM t1 ORDER BY a # 2, 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '# '. | syntax error "
                + "line 1 at position 20 unexpected 'y'.",
            "SELECT ABS(1 # 2) x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '# '. | syntax error "
                + "line 1 at position 18 unexpected 'y'.",
            "SELECT 1, 2 # 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 29 unexpected '# '.",
            "SELECT 1 # 2 FROM t1 WHERE a # 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '# '.",
            "INSERT INTO t1 VALUES (1) # 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '. | syntax error "
                + "line 1 at position 21 unexpected 'y'.",
            "SELECT a # FROM t1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '# '.",
            "SELECT 1 AS b # 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '# '.",
            "SELECT * # 2 x y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '# '. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT 1 + # 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 40 near '<EOF>'.",
            "CREATE TABLE tq (a VARCHAR DEFAULT 'abc)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 39 near '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a = 'x AND b = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 20 near '<EOF>'. | syntax error line "
                + "1 at position 17 unexpected 'y'.",
            "SELECT 'abc AS x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 39 near '<EOF>'. | syntax error line "
                + "1 at position 24 unexpected 'y'.",
            "SELECT 1; SELECT 'abc x y; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 16 near '<EOF>'. | syntax error line "
                + "1 at position 16 unexpected '<EOF>'.",
            "SELECT \"abc AS x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 27 near '<EOF>'. | syntax error line "
                + "1 at position 18 unexpected 'x'. | syntax error line 1 at position 20 unexpected 'y'.",
            "BEGIN RETURN 'abc x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 37 near '<EOF>'. | syntax error line "
                + "1 at position 30 unexpected 'x'. | syntax error line 1 at position 33 unexpected 'END'.",
            "BEGIN LET s := 'abc; RETURN s x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'. | parse error "
                + "line 1 at position 33 near '<EOF>'.",
            "SELECT 1 x y; SELECT 'abc def ghi");
    }
}
