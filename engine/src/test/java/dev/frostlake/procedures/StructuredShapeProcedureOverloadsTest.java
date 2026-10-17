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
 * A procedure's structured parameter is a signature of its shape as a function's is (live-verified): another field
 * name is an overload, another width the same signature, a field's NOT NULL alone a twin refused naming the new
 * signature, and OR REPLACE replaces that twin. A CALL runs the overload its argument's shape binds, a number's leaf
 * binding any number's; a shape no overload binds, and a plain OBJECT, are refused at the call.
 */
public class StructuredShapeProcedureOverloadsTest extends MeasuredCellsTestSupport {

    @Test
    public void aProcedureShapeIsAnOverloadOfItsOwn() {
        engine.execute("CREATE OR REPLACE PROCEDURE p1(x OBJECT(a INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'pa'; END $$");
        assertCells(new String[][] {
            {"CREATE PROCEDURE p1(x OBJECT(b INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'pb'; END $$",
                "Function P1 successfully created."},
            {"CREATE PROCEDURE p1(x OBJECT(a NUMBER(10,2))) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'pw'; END $$",
                "REFUSED SQL compilation error: | Object 'P1' already exists."},
            {"CREATE PROCEDURE p1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'pn'; END $$",
                "REFUSED SQL compilation error: | Object 'P1(X OBJECT(a NUMBER NOT NULL)):VARCHAR' already exists."},
            {"CALL p1({'a': 1}::OBJECT(a INT))", "pa"},
            {"CALL p1({'b': 1}::OBJECT(b INT))", "pb"},
            {"CALL p1(OBJECT_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'P1': (OBJECT)"},
            {"CREATE OR REPLACE PROCEDURE p1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN 'pr'; END $$",
                "Function P1 successfully created."},
        });
        engine.execute("SHOW USER PROCEDURES LIKE 'P1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "P1(OBJECT(a NUMBER NOT NULL)) RETURN VARCHAR ;; P1(OBJECT(b NUMBER)) RETURN VARCHAR"},
            {"DROP PROCEDURE p1(OBJECT(b INT))", "P1 successfully dropped."},
        });
        engine.execute("SHOW USER PROCEDURES LIKE 'P1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "P1(OBJECT(a NUMBER NOT NULL)) RETURN VARCHAR"},
        });
    }

    @Test
    public void aCallRunsTheProcedureItsShapeBinds() {
        engine.execute("CREATE OR REPLACE PROCEDURE q1(x OBJECT(a INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'qa'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE q1(x OBJECT(b INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'qb'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE q2(x OBJECT(a INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'single'; END $$");
        assertCells(new String[][] {
            {"CALL q1({'a': 1}::OBJECT(a INT))", "qa"},
            {"CALL q1({'b': 1}::OBJECT(b INT))", "qb"},
            {"CALL q1({'c': 1}::OBJECT(c INT))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'Q1': (OBJECT(c NUMBER(38,0)))"},
            {"CALL q1({'a': 1.5}::OBJECT(a FLOAT))", "qa"},
            {"CALL q1(NULL)", "qa"},
            {"CALL q2({'a': 1.5}::OBJECT(a FLOAT))", "single"},
            {"CALL q2(OBJECT_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'Q2': (OBJECT)"},
            {"CALL q2({'b': 1}::OBJECT(b INT))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'Q2': (OBJECT(b NUMBER(38,0)))"},
        });
    }
}
