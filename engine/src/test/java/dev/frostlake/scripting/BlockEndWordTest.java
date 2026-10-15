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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * One word after a block's END. The top block of a body runs when the word is IF, LOOP, CASE, DO, UNTIL, a
 * quoted name or a keyword that may be a name — BEGIN RETURN 1; END IF; answers 1, as a statement, as the text
 * EXECUTE IMMEDIATE runs and as a procedure's body — while FOR, END, BEGIN, THEN, ELSE, NULL and a number are
 * refused where they stand and a second word is refused at itself. A block nested in another that ends in a word
 * and declares nothing is read and then neither compiled nor run, whatever the word: a name its statements do
 * not declare is no fault, and the statements after it see nothing it would have done. A nested block with a
 * DECLARE section runs (live-verified).
 */
public class BlockEndWordTest extends MeasuredCellsTestSupport {

    @Test
    public void theTopBlockOfABody() {
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END IF;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END LOOP;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END CASE;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END \"x\";");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'b'.",
            "BEGIN RETURN 1; END a b;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'FOR'.",
            "BEGIN RETURN 1; END FOR;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'END'.",
            "BEGIN RETURN 1; END END;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END IF");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IF'.",
            "BEGIN RETURN 1; END IF IF;");
        assertAnswer(
            "1",
            "DECLARE x INT DEFAULT 1; BEGIN RETURN x; END IF;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 2; END IF;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'.",
            "BEGIN RETURN 1; END 1;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'NULL'.",
            "BEGIN RETURN 1; END NULL;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'BEGIN'.",
            "BEGIN RETURN 1; END BEGIN;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END DO;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'THEN'.",
            "BEGIN RETURN 1; END THEN;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "BEGIN RETURN 1; END x.y;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'.",
            "BEGIN RETURN 1; END \"x\" y;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'LOOP'.",
            "BEGIN RETURN 1; END IF LOOP;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ELSE'.",
            "BEGIN RETURN 1; END ELSE;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END UNTIL;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'CASE'.",
            "BEGIN RETURN 1; END CASE CASE;");
    }

    @Test
    public void aNestedBlockIsNotRun() {
        assertAnswer(
            "null",
            "BEGIN BEGIN RETURN 1; END IF; END;");
        assertAnswer(
            "1",
            "BEGIN LET x := 1; IF (x = 1) THEN BEGIN x := 2; END IF; END IF; RETURN x; END;");
        assertAnswer(
            "2",
            "BEGIN LET x := 1; IF (x = 1) THEN BEGIN x := 2; END; END IF; RETURN x; END;");
        assertAnswer(
            "0",
            "BEGIN LET x := 0; FOR i IN 1 TO 2 DO BEGIN x := x + i; END LOOP; END FOR; RETURN x; END;");
        assertAnswer(
            "null",
            "BEGIN BEGIN RETURN 1; END LOOP; END;");
        assertAnswer(
            "null",
            "BEGIN BEGIN RETURN 1; END \"x\"; END;");
        assertAnswer(
            "1",
            "BEGIN BEGIN RETURN 1; END; END IF;");
        assertAnswer(
            "5",
            "BEGIN BEGIN LET y := 1; END IF; RETURN 5; END;");
        assertAnswer(
            "0",
            "BEGIN LET r := 0; BEGIN r := 1; END IF; RETURN r; END;");
    }

    @Test
    public void inAProceduresBodyAndInATextThatIsRun() {
        try {
            assertAnswer(
                "Function PE successfully created.",
                "CREATE OR REPLACE PROCEDURE pe() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END IF'");
            assertAnswer(
                "1",
                "CALL pe()");
            assertAnswer(
                "Function PE2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pe2() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END LOOP; $$");
            assertAnswer(
                "1",
                "CALL pe2()");
            assertAnswer(
                "1",
                "EXECUTE IMMEDIATE 'BEGIN RETURN 1; END IF;'");
            assertAnswer(
                "1",
                "EXECUTE IMMEDIATE 'BEGIN RETURN 1; END CASE'");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pe()");
            answer(
                "DROP PROCEDURE IF EXISTS pe2()");
        }
    }

    @Test
    public void aNestedBlockIsNotCompiled() {
        assertAnswer(
            "5",
            "BEGIN BEGIN nosuch_var_zq7 := 1; END IF; RETURN 5; END;");
        assertAnswer(
            "5",
            "BEGIN BEGIN SELECT * FROM nosuch_tab_zq7; END IF; RETURN 5; END;");
        assertAnswer(
            "5",
            "BEGIN BEGIN LET z := 1 / 0; END IF; RETURN 5; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN RETURN 1; END lbl; RETURN 2; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN RETURN 1; END CASE; RETURN 2; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN RETURN 1; END DO; RETURN 2; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN RETURN 1; END UNTIL; RETURN 2; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN RETURN 1; END \"x\"; RETURN 2; END;");
        assertAnswer(
            "null",
            "BEGIN LET x := 1 / 0; EXCEPTION WHEN OTHER THEN BEGIN RETURN 7; END IF; END;");
        assertAnswer(
            "7",
            "BEGIN LET x := 1 / 0; EXCEPTION WHEN OTHER THEN BEGIN RETURN 7; END; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN BEGIN RETURN 1; END; END IF; RETURN 2; END;");
        assertAnswer(
            "2",
            "BEGIN BEGIN RETURN 1; END IF; RETURN 2; END LOOP;");
        assertAnswer(
            "5",
            "BEGIN BEGIN OPEN nosuch_cur_zq7; END IF; RETURN 5; END;");
        assertAnswer(
            "5",
            "BEGIN BEGIN LET a := 1; LET a := 2; END IF; RETURN 5; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IF'. | syntax error "
                + "line 1 at position 33 unexpected 'END'.",
            "BEGIN BEGIN RETURN 1; END IF IF; END;");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE 'BEGIN BEGIN RETURN 1; END IF; RETURN 2; END;'");
        assertAnswer(
            "6",
            "BEGIN LET s := 0; FOR i IN 1 TO 3 DO s := s + i; END FOR; RETURN s; END LOOP;");
        assertAnswer(
            "2",
            "BEGIN IF (TRUE) THEN BEGIN RETURN 1; END IF; END IF; RETURN 2; END;");
        assertAnswer(
            "5",
            "BEGIN BEGIN LET z := 1 / 0; EXCEPTION WHEN OTHER THEN RETURN 3; END IF; RETURN 5; END;");
    }

    @Test
    public void aNestedBlockThatDeclaresRuns() {
        assertAnswer(
            "1",
            "DECLARE r INT DEFAULT 0; BEGIN DECLARE y INT DEFAULT 1; BEGIN r := y; END IF; RETURN r; END;");
        assertAnswer(
            "1",
            "DECLARE r INT DEFAULT 0; BEGIN DECLARE y INT DEFAULT 1; BEGIN r := y; END; RETURN r; END;");
    }

    @Test
    public void nestedInAProceduresBody() {
        try {
            assertAnswer(
                "Function PE1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pe1() RETURNS INT LANGUAGE SQL AS BEGIN RETURN 1; END IF;");
            assertAnswer(
                "1",
                "CALL pe1()");
            assertAnswer(
                "Function PE2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pe2() RETURNS INT LANGUAGE SQL AS $$ BEGIN BEGIN RETURN 1; END IF; "
                    + "RETURN 2; END; $$");
            assertAnswer(
                "2",
                "CALL pe2()");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pe1()");
            answer(
                "DROP PROCEDURE IF EXISTS pe2()");
        }
    }

    @Test
    public void inBranchesLoopsAndMoreWords() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "BEGIN BEGIN SELEC 1; END IF; RETURN 5; END;");
        assertAnswer(
            "3",
            "BEGIN BEGIN RETURN 1; END IF; BEGIN RETURN 2; END LOOP; RETURN 3; END;");
        assertAnswer(
            "10",
            "BEGIN LET n := 0; BEGIN n := n + 1; END CASE; BEGIN n := n + 10; END; RETURN n; END;");
        assertAnswer(
            "2",
            "BEGIN CASE WHEN TRUE THEN BEGIN RETURN 1; END CASE; END CASE; RETURN 2; END;");
        assertAnswer(
            "2",
            "BEGIN LOOP BEGIN RETURN 1; END LOOP; BREAK; END LOOP; RETURN 2; END;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END comment;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '.'.",
            "BEGIN RETURN 1; END x.y;");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END IF;;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '\"LOOP\"'.",
            "BEGIN RETURN 1; END \"IF\" \"LOOP\";");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; END IDENTIFIER;");
    }
}
