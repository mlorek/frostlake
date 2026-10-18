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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A table procedure's arguments in a FROM clause compile with the statement (live-verified): a name no relation
 * answers is an invalid identifier where it stands, ahead of a miscounted call among the arguments, which is
 * refused where it stands too; a NOT, an AND or an OR is an argument as a value is; too many arguments beside a
 * parameter with a default are the CALL's to refuse; and a column of a relation written before the call is an
 * invalid argument once the types fit, its bare name spelled with its relation.
 */
public class TableProcedureArgumentScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void theArgumentsCompileWithTheStatement() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c VARCHAR, n NUMBER(5,2))");
        engine.execute("INSERT INTO t1 VALUES ('a', 1.5)");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(a VARCHAR) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qn(n NUMBER) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qb(b BOOLEAN) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qe(x NUMBER DEFAULT 1) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS "
            + "$$ DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE q2(a VARCHAR, b NUMBER) RETURNS TABLE (r VARCHAR) LANGUAGE SQL "
            + "AS $$ DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(qa($1))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier '$1'"},
            {"SELECT * FROM TABLE(qa(nosuchcol))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCHCOL'"},
            {"SELECT * FROM TABLE(qa(nosuchcol || 'x'))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCHCOL'"},
            {"SELECT * FROM TABLE(qa(UPPER(nosuchcol)))",
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCHCOL'"},
            {"SELECT * FROM TABLE(qa(t1.c))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'T1.C'"},
            {"SELECT * FROM TABLE(qa(nosuch_t.c))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH_T.C'"},
            {"SELECT * FROM TABLE(qa(ABS(1, 2)))",
                "REFUSED SQL compilation error: error line 1 at position 23 | too many arguments for function [ABS(1,"
                + " 2)] expected 1, got 2"},
            {"SELECT * FROM TABLE(qn(1 + ABS(1, 2)))",
                "REFUSED SQL compilation error: error line 1 at position 27 | too many arguments for function [ABS(1,"
                + " 2)] expected 1, got 2"},
            {"SELECT * FROM TABLE(qa(UPPER(1, 2)))",
                "REFUSED SQL compilation error: error line 1 at position 23 | too many arguments for function "
                + "[UPPER(1, 2)] expected 1, got 2"},
            {"SELECT * FROM t1, TABLE(qa(t1.c))",
                "REFUSED SQL compilation error: error line 1 at position 27: Invalid argument T1.C for procedure in "
                + "FROM clause"},
            {"SELECT * FROM t1, TABLE(qa(c))",
                "REFUSED SQL compilation error: error line 1 at position 27: Invalid argument T1.C for procedure in "
                + "FROM clause"},
            {"SELECT * FROM t1, TABLE(qn(t1.n))",
                "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'QN': (NUMBER(5,2))"},
            {"SELECT * FROM t1, TABLE(qa(t1.n))",
                "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'QA': (NUMBER(5,2))"},
            {"SELECT * FROM t1, TABLE(qa(t1.c || 'x'))",
                "REFUSED SQL compilation error: error line 1 at position 27: Invalid argument T1.C for procedure in "
                + "FROM clause"},
            {"SELECT * FROM TABLE(qb(NOT TRUE))", "x"},
            {"SELECT * FROM TABLE(qb(TRUE AND FALSE))", "x"},
            {"SELECT * FROM TABLE(qb(1 = 1))", "x"},
            {"SELECT * FROM TABLE(qb(NOT (1 = 2)))", "x"},
            {"SELECT * FROM TABLE(qn(-1))", "x"},
            {"SELECT * FROM TABLE(qe(1, 2))",
                "REFUSED Stored procedure execution error on line 1 position 20: SQL compilation error: error line 0 "
                + "at position -1 | too many arguments for function [QE(1, 2)] expected 1, got 2"},
            {"SELECT * FROM TABLE(qe())", "x"},
            {"SELECT * FROM TABLE(qe(1))", "x"},
            {"SELECT * FROM TABLE(qe(x => 2))", "x"},
            {"SELECT * FROM TABLE(qe(1, 2, 3))",
                "REFUSED Stored procedure execution error on line 1 position 20: SQL compilation error: error line 0 "
                + "at position -1 | too many arguments for function [QE(1, 2, 3)] expected 1, got 3"},
            {"SELECT * FROM TABLE(q2('a'))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (VARCHAR(1))"},
            {"SELECT * FROM TABLE(q2('a', 1, 2))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'Q2': (VARCHAR(1), NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT * FROM TABLE(qa(a => nosuchcol))",
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCHCOL'"},
            {"SELECT * FROM TABLE(qa((SELECT 'y')))", "x"},
            {"SELECT * FROM TABLE(qa(NULL))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (NULL)"},
            {"SELECT * FROM TABLE(qa(COUNT(*)))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (NUMBER(18,0))"},
            {"SELECT * FROM TABLE(qa(nosuchcol, 1))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCHCOL'"},
            {"SELECT * FROM TABLE(qe(nosuchcol, 2))",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCHCOL'"},
            {"SELECT * FROM TABLE(qb(NOT nosuchcol))",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCHCOL'"},
        });
    }
}
