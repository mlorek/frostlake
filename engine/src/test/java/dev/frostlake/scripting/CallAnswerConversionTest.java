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
 * x := (CALL p()) holds the procedure's answer unconverted (live-verified): a bare RETURN x hands it back as the
 * CALL did and SYSTEM$TYPEOF(x) answers the declared type, an expression that reads x converts it there and fails
 * as an EXPRESSION_ERROR at that expression, a statement's bind refuses it as an invalid bind value, and a cursor's
 * or an EXECUTE IMMEDIATE's USING binds it as it is.
 */
public class CallAnswerConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void readsConvertWhereTheyStand() {
        engine.execute("CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END; "
            + "$$");
        assertCells(new String[][] {
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN (SELECT :x); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('2024-01-02')); RETURN (SELECT :x); END;", "2024-01-02"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN (SELECT :x); END;", "1.24"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN (SELECT SYSTEM$TYPEOF(:x)); END;",
                "NUMBER(5,2)[SB1]"},
            {"DECLARE x DATE; res RESULTSET; BEGIN x := (CALL pv('abc')); res := (SELECT :x AS v); RETURN TABLE(res); "
                + "END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 67 : SQL compilation "
                + "error: | Invalid bind value (abc) for type (DATE)."},
            {"DECLARE x DATE; v VARCHAR; c CURSOR FOR SELECT ?::VARCHAR AS v; BEGIN x := (CALL pv('abc')); OPEN c "
                + "USING (x); FETCH c INTO v; CLOSE c; RETURN v; END;",
                "abc"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN TO_VARCHAR(x); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); IF (x IS NULL) THEN RETURN 'n'; END IF; RETURN 'nn'; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); LET y VARCHAR := x; RETURN y; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); EXECUTE IMMEDIATE 'SELECT ?' USING (x); RETURN 'ok'; END;",
                "ok"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN OBJECT_CONSTRUCT('a', x); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN x * 1; END;", "1.24"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); x := (CALL pv('2024-05-05')); RETURN x; END;", "2024-05-05"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); CREATE OR REPLACE TEMPORARY TABLE tx (v VARCHAR); INSERT "
                + "INTO tx VALUES (:x); RETURN (SELECT v FROM tx); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 95 : SQL compilation "
                + "error: | Invalid bind value (abc) for type (DATE)."},
            {"DECLARE x VARCHAR(2); BEGIN x := (CALL pv('abcdef')); RETURN LENGTH(x); END;", "6"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN x; EXCEPTION WHEN OTHER THEN RETURN 'caught ' || "
                + "SQLCODE; END;",
                "abc"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); CASE WHEN x IS NULL THEN RETURN 1; ELSE RETURN 2; END "
                + "CASE; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN (SELECT :x + 0); END;", "1.24"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); LET y FLOAT := x; RETURN y; END;", "1.24"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN x::VARCHAR; END;", "1.24"},
            {"DECLARE x NUMBER; BEGIN x := (CALL pd('2024-01-02')); RETURN (SELECT :x); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Numeric value "
                + "'2024-01-02' is not recognized"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN SYSTEM$TYPEOF(x); END;", "DATE[SB4]"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); FOR i IN 1 TO 1 DO RETURN x; END FOR; END;", "abc"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN :x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Date 'abc' is not "
                + "recognized"},
        });
    }

    @Test
    public void theAnswerIsHeldUntilRead() {
        engine.execute("CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END; "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + "
            + "1; END; $$");
        assertCells(new String[][] {
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN x; END;", "abc"},
            {"DECLARE x DATE; BEGIN IF (TRUE) THEN x := (CALL pv('abc')); END IF; RETURN x; END;", "abc"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN x; END;", "1.239"},
            {"DECLARE x NUMBER; BEGIN x := (CALL pv('abc')); RETURN x; END;", "abc"},
            {"DECLARE x BOOLEAN; BEGIN x := (CALL pv('abc')); RETURN x; END;", "abc"},
            {"DECLARE x VARCHAR(2); BEGIN x := (CALL pv('abcdef')); RETURN x; END;", "abcdef"},
            {"DECLARE x DATE; y DATE; BEGIN x := (CALL pv('abc')); y := x; RETURN y; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN x || '!'; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x NUMBER; BEGIN x := (CALL pd('2024-01-02')); RETURN x; END;", "2024-01-02"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('abc')); RETURN YEAR(x); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN SYSTEM$TYPEOF(x); END;", "NUMBER(5,2)[SB4]"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); x := x; RETURN x; END;", "1.24"},
            {"DECLARE x NUMBER(5,2); BEGIN x := (CALL pf(1.239)); RETURN x + 1; END;", "2.24"},
            {"DECLARE x DATE; BEGIN x := (CALL pv('2024-01-02')); RETURN DATEADD(day, 1, x); END;", "2024-01-03"},
            {"DECLARE x DATE; BEGIN CALL pv('abc') INTO :x; RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Date 'abc' is not "
                + "recognized"},
            {"DECLARE x ARRAY; BEGIN x := (CALL pv('abc')); RETURN x; END;", "abc"},
            {"DECLARE x VARIANT; BEGIN x := (CALL pv('abc')); RETURN x; END;", "abc"},
        });
    }
}
