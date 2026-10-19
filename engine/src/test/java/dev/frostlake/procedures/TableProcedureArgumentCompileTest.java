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
 * A table procedure called in a FROM clause compiles its arguments with the statement: a function the argument calls
 * that is not there is the statement's own refusal, not the procedure's execution error (live-verified). An argument
 * of a type the parameter does not take, an aggregate's among them, is refused by the types, and a default fills an
 * argument the call leaves out.
 */
public class TableProcedureArgumentCompileTest extends MeasuredCellsTestSupport {

    @Test
    public void aFromFormArgumentIsCompiledWithTheStatement() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c VARCHAR, n NUMBER(5,2))");
        engine.execute("INSERT INTO t1 VALUES ('a', 1.5)");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(a VARCHAR) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qn(n NUMBER) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qb(b BOOLEAN) RETURNS TABLE (r VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE qe(x NUMBER DEFAULT 1) RETURNS TABLE (r VARCHAR) LANGUAGE SQL "
            + "AS $$ DECLARE res RESULTSET; BEGIN res := (SELECT 'x' AS r); RETURN TABLE(res); END; $$");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(qa(nosuchfn(1)))", "REFUSED SQL compilation error: | Unknown function NOSUCHFN."},
            {"SELECT * FROM TABLE(qa(COUNT(*)))",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'QA': (NUMBER(18,0))"},
            {"SELECT * FROM TABLE(qa('a'))", "x"},
            {"SELECT * FROM TABLE(qn(1))", "x"},
            {"SELECT * FROM TABLE(qe())", "x"},
        });
    }
}
