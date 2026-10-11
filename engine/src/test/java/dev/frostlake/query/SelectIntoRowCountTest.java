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
 * A SELECT ... INTO of a block answers for the rows its query returns: more than one is the statement's
 * failure in the account's sentence, with a code and a state of its own for a handler, and none leaves each
 * target a NULL of no type, which a RETURN of the bare name reports as VARCHAR(134217728) until the name is
 * assigned again. A select list that writes another number of values than the statement has targets is
 * refused while the block compiles, and a value its target does not take is the statement's expression
 * failing. Every answer is live-verified.
 */
public class SelectIntoRowCountTest extends MeasuredCellsTestSupport {

    @Test
    public void theRowsASelectIntoTakes() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pf(x FLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN TO_VARCHAR(x); "
                + "END'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pnan() RETURNS FLOAT LANGUAGE SQL AS 'BEGIN RETURN ''NaN''::FLOAT; END'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pinf() RETURNS FLOAT LANGUAGE SQL AS 'BEGIN LET x FLOAT := "
                + "''inf''::FLOAT; RETURN x; END'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz ORDER BY id; RETURN r; END;");
        assertShape(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz ORDER BY id; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 28 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; s INT; BEGIN SELECT id, id INTO :r, :s FROM fz; RETURN r; END;");
        assertShape(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 28 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; s INT; BEGIN SELECT id, id INTO :r, :s FROM fz; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(SUM(x)) INTO :r FROM (SELECT n + 1 x FROM t); RETURN "
                + "r; END;");
        assertShape(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(SUM(x)) INTO :r FROM (SELECT n + 1 x FROM t); RETURN "
                + "r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT DEFAULT 3; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT DEFAULT 3; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE id = 5; RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE id = 5; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; r := 5; RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; r := 5; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r + 1; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r + 1; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; s INT; BEGIN SELECT id, id INTO :r, :s FROM fz WHERE FALSE; RETURN s; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT; s INT; BEGIN SELECT id, id INTO :r, :s FROM fz WHERE FALSE; RETURN s; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT; BEGIN SELECT id INTO r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz ORDER BY id LIMIT 1; RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz ORDER BY id LIMIT 1; RETURN r; END;");
        assertAnswer(
            "true",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r IS NULL; END;");
        assertShape(
            "anonymous block:BOOLEAN",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r IS NULL; END;");
        assertAnswer(
            "9",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN COALESCE(r, 9); END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN COALESCE(r, 9); END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; LET s := r; RETURN s; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; LET s := r; RETURN s; END;");
        assertAnswer(
            "null",
            "DECLARE r VARCHAR DEFAULT 'x'; BEGIN SELECT id::VARCHAR INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r VARCHAR DEFAULT 'x'; BEGIN SELECT id::VARCHAR INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE id = 5; SELECT id INTO :r FROM fz WHERE FALSE; "
                + "RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE id = 5; SELECT id INTO :r FROM fz WHERE FALSE; "
                + "RETURN r; END;");
        assertAnswer(
            "5",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; SELECT id INTO :r FROM fz WHERE id = 5; "
                + "RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; SELECT id INTO :r FROM fz WHERE id = 5; "
                + "RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r FLOAT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r FLOAT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r BOOLEAN; BEGIN SELECT b INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r BOOLEAN; BEGIN SELECT b INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT MAX(id) INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT MAX(id) INTO :r FROM fz WHERE FALSE; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r::INT; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN r::INT; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN :r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN :r; END;");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN SYSTEM$TYPEOF(r); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE FALSE; RETURN SYSTEM$TYPEOF(r); END;");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE id = 5; RETURN SYSTEM$TYPEOF(r); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz WHERE id = 5; RETURN SYSTEM$TYPEOF(r); END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN r := NULL; RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN r := NULL; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT DEFAULT 3; BEGIN r := NULL; RETURN r; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT DEFAULT 3; BEGIN r := NULL; RETURN r; END;");
        assertAnswer(
            "null",
            "DECLARE r INT; BEGIN LET q INT := NULL; RETURN q; END;");
        assertShape(
            "anonymous block:NUMBER(38,0)",
            "DECLARE r INT; BEGIN LET q INT := NULL; RETURN q; END;");
        assertAnswer(
            "statement 92219 22000 A SELECT INTO statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz ORDER BY id; RETURN r; EXCEPTION WHEN STATEMENT_ERROR "
                + "THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "statement 2028 42601 SQL compilation error: | ambiguous column name 'ID'",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz, fz f2; RETURN r; EXCEPTION WHEN STATEMENT_ERROR THEN "
                + "RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN OTHER THEN RETURN "
                + "'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 |  Number of SELECT values (1) does not "
                + "match the number of INTO variables (2).",
            "DECLARE r INT; s INT; BEGIN SELECT id INTO :r, :s FROM fz WHERE id = 5; RETURN r; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 |  Number of SELECT values (2) does not "
                + "match the number of INTO variables (1).",
            "DECLARE r INT; BEGIN SELECT id, b INTO :r FROM fz WHERE id = 5; RETURN r; EXCEPTION WHEN "
                + "STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; WHEN "
                + "OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz ORDER BY id; RETURN r; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH'",
            "BEGIN SELECT id INTO :nosuch FROM fz WHERE id = 5; RETURN 1; END;");
        assertAnswer(
            "1",
            "DECLARE r INT; BEGIN SELECT b INTO :r FROM fz WHERE id = 5; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 21 : Numeric value 'x' "
                + "is not recognized",
            "DECLARE r INT; BEGIN SELECT 'x' INTO :r; RETURN r; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 INTO clause is not allowed in this context",
            "DECLARE r INT; BEGIN SELECT id INTO :r FROM fz UNION ALL SELECT id FROM fz; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : A SELECT INTO "
                + "statement expects exactly 1 returned row, but got 2.",
            "DECLARE r INT; BEGIN SELECT * INTO :r FROM (SELECT id FROM fz); RETURN r; END;");
    }
}
