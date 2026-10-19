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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A structured parameter's shape is a signature of its own (live-verified): beside {@code f1(x OBJECT(a INT))} another
 * field name, another member family, another field order, an ARRAY or a MAP of another element are each an overload,
 * while the widths inside a shape are not. A field's NOT NULL makes no overload either, and is no name for the one it
 * differs from: a plain CREATE of such a twin is refused naming the new signature, OR REPLACE replaces it, IF NOT
 * EXISTS leaves it, a temporary one stands beside a permanent one, and only an overload of its very shape competes
 * with a new one or hides a temporary one. Each shape is dropped by its own written signature, and a call reaches the
 * overload its argument's shape binds: by field names and order, a number's leaf binding any number's, every other
 * leaf only its own family, a plain OBJECT or ARRAY only a plain overload, and an untyped NULL the plain one first.
 */
public class StructuredShapeOverloadsTest extends MeasuredCellsTestSupport {

    @Test
    public void aStructuredShapeIsAnOverloadOfItsOwn() {
        engine.execute("CREATE OR REPLACE FUNCTION f1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 's' $$");
        assertCells(new String[][] {
            {"CREATE FUNCTION f1(x OBJECT(b INT)) RETURNS VARCHAR AS $$ 't' $$", "Function F1 successfully created."},
            {"CREATE FUNCTION f1(x OBJECT(a VARCHAR)) RETURNS VARCHAR AS $$ 'u' $$",
                "Function F1 successfully created."},
            {"CREATE FUNCTION f1(x OBJECT(a NUMBER(10,2))) RETURNS VARCHAR AS $$ 'w' $$",
                "REFUSED SQL compilation error: | Object 'F1' already exists."},
            {"CREATE FUNCTION f1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR AS $$ 'n' $$",
                "REFUSED SQL compilation error: | Object 'F1(X OBJECT(a NUMBER NOT NULL)):VARCHAR' already exists."},
        });
        engine.execute("SHOW USER FUNCTIONS LIKE 'F1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "F1(OBJECT(a NUMBER)) RETURN VARCHAR ;; F1(OBJECT(a VARCHAR)) RETURN VARCHAR ;; F1(OBJECT(b "
                + "NUMBER)) RETURN VARCHAR"},
            {"SELECT f1({'a': 1}::OBJECT(a INT)), f1({'b': 1}::OBJECT(b INT)), f1({'a': 'z'}::OBJECT(a VARCHAR))",
                "s|t|u"},
            {"DROP FUNCTION f1(OBJECT(b INT))", "F1 successfully dropped."},
            {"DROP FUNCTION f1(OBJECT(a VARCHAR(5)))", "F1 successfully dropped."},
            {"DROP FUNCTION f1(OBJECT)",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F1."},
        });
        engine.execute("SHOW USER FUNCTIONS LIKE 'F1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "F1(OBJECT(a NUMBER)) RETURN VARCHAR"},
            {"CREATE FUNCTION f1(x ARRAY(INT)) RETURNS VARCHAR AS $$ 'a' $$", "Function F1 successfully created."},
            {"CREATE FUNCTION f1(x ARRAY(VARCHAR)) RETURNS VARCHAR AS $$ 'v' $$", "Function F1 successfully created."},
            {"CREATE FUNCTION f1(x ARRAY(NUMBER(10,2))) RETURNS VARCHAR AS $$ 'n' $$",
                "REFUSED SQL compilation error: | Object 'F1' already exists."},
            {"CREATE FUNCTION f1(x OBJECT(a INT, b INT)) RETURNS VARCHAR AS $$ 'ab' $$",
                "Function F1 successfully created."},
            {"CREATE FUNCTION f1(x OBJECT(b INT, a INT)) RETURNS VARCHAR AS $$ 'ba' $$",
                "Function F1 successfully created."},
            {"CREATE FUNCTION f1(y OBJECT(a INT NOT NULL), z INT) RETURNS VARCHAR AS $$ 'm' $$",
                "Function F1 successfully created."},
            {"CREATE FUNCTION f1(y OBJECT(a INT), z INT) RETURNS VARCHAR AS $$ 'm2' $$",
                "REFUSED SQL compilation error: | Object 'F1(Y OBJECT(a NUMBER), Z NUMBER):VARCHAR' already exists."},
            {"CREATE OR REPLACE FUNCTION f1(x OBJECT(b INT)) RETURNS VARCHAR AS $$ 't2' $$",
                "Function F1 successfully created."},
        });
        engine.execute("SHOW USER FUNCTIONS LIKE 'F1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "F1(ARRAY(NUMBER)) RETURN VARCHAR ;; F1(ARRAY(VARCHAR)) RETURN VARCHAR ;; F1(OBJECT(a NUMBER NOT "
                + "NULL), NUMBER) RETURN VARCHAR ;; F1(OBJECT(a NUMBER)) RETURN VARCHAR ;; F1(OBJECT(a NUMBER, b "
                + "NUMBER)) RETURN VARCHAR ;; F1(OBJECT(b NUMBER)) RETURN VARCHAR ;; F1(OBJECT(b NUMBER, a NUMBER)) "
                + "RETURN VARCHAR"},
        });
    }

    @Test
    public void aCallReachesTheOverloadItsShapeBinds() {
        engine.execute("CREATE OR REPLACE FUNCTION g1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 'ai' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g1(x OBJECT(b INT)) RETURNS VARCHAR AS $$ 'bi' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g1(x OBJECT(a VARCHAR)) RETURNS VARCHAR AS $$ 'av' $$");
        assertCells(new String[][] {
            {"SELECT g1({'a': 1}::OBJECT(a INT))", "ai"},
            {"SELECT g1({'b': 1}::OBJECT(b INT))", "bi"},
            {"SELECT g1({'a': 'z'}::OBJECT(a VARCHAR))", "av"},
            {"SELECT g1({'a': 1}::OBJECT(a NUMBER(10,2)))", "ai"},
            {"SELECT g1({'a': 1}::OBJECT(a INT NOT NULL))", "ai"},
            {"SELECT g1(OBJECT_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G1': (OBJECT)"},
            {"SELECT g1({'c': 1}::OBJECT(c INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G1': (OBJECT(c NUMBER(38,0)))"},
            {"SELECT g1({'a': 1.5}::OBJECT(a FLOAT))", "ai"},
            {"SELECT g1({'a': 1, 'b': 2}::OBJECT(a INT, b INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G1': (OBJECT(a NUMBER(38,0), b NUMBER(38,0)))"},
            {"SELECT g1(NULL)", "ai"},
            {"SELECT g1({'a': '1'}::OBJECT(a VARCHAR(3)))", "av"},
            {"CREATE FUNCTION g1(x OBJECT) RETURNS VARCHAR AS $$ 'plain' $$", "Function G1 successfully created."},
            {"SELECT g1(OBJECT_CONSTRUCT('a', 1))", "plain"},
            {"SELECT g1({'c': 1}::OBJECT(c INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G1': (OBJECT(c NUMBER(38,0)))"},
            {"SELECT g1({'a': 1}::OBJECT(a INT))", "ai"},
            {"SELECT g1(NULL)", "plain"},
            {"CREATE FUNCTION g1(x OBJECT(a INT), y INT DEFAULT 1) RETURNS VARCHAR AS $$ 'd' $$",
                "REFUSED SQL compilation error: Cannot overload FUNCTION 'G1' as it would cause ambiguous FUNCTION "
                + "overloading."},
            {"CREATE FUNCTION g1(x OBJECT(c INT), y INT DEFAULT 1) RETURNS VARCHAR AS $$ 'cd' $$",
                "Function G1 successfully created."},
            {"CREATE FUNCTION g1(x OBJECT(a INT NOT NULL), y INT DEFAULT 1) RETURNS VARCHAR AS $$ 'nd' $$",
                "Function G1 successfully created."},
        });
    }

    @Test
    public void aFieldsNotNullAloneMakesNoOverload() {
        engine.execute("CREATE OR REPLACE FUNCTION h1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 's' $$");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION h1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR AS $$ 'r' $$",
                "Function H1 successfully created."},
        });
        engine.execute("SHOW USER FUNCTIONS LIKE 'H1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "H1(OBJECT(a NUMBER NOT NULL)) RETURN VARCHAR"},
            {"SELECT h1({'a': 1}::OBJECT(a INT))", "r"},
            {"CREATE FUNCTION IF NOT EXISTS h1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 'i' $$",
                "H1 already exists, statement succeeded."},
            {"CREATE FUNCTION IF NOT EXISTS h1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR AS $$ 'i2' $$",
                "H1 already exists, statement succeeded."},
        });
        engine.execute("SHOW USER FUNCTIONS LIKE 'H1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "H1(OBJECT(a NUMBER NOT NULL)) RETURN VARCHAR"},
            {"CREATE FUNCTION h1(x OBJECT(a INT)) RETURNS NUMBER(10,2) AS $$ 1 $$",
                "REFUSED SQL compilation error: | Object 'H1(X OBJECT(a NUMBER)):NUMBER(10,2)' already exists."},
            {"CREATE FUNCTION h1(x OBJECT(a INT)) RETURNS TABLE(c INT) AS $$ SELECT 1 $$",
                "REFUSED SQL compilation error: | Object 'H1(X OBJECT(a NUMBER)):TABLE: (C NUMBER)' already exists."},
            {"CREATE TEMPORARY FUNCTION h1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 't' $$",
                "Function H1 successfully created."},
            {"CREATE FUNCTION h1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR AS $$ 'same' $$",
                "REFUSED SQL compilation error: | Object 'H1' already exists."},
            {"CREATE PROCEDURE h1(x OBJECT(a INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'p'; END $$",
                "REFUSED SQL compilation error: | Object 'H1' already exists as FUNCTION"},
            {"CREATE PROCEDURE h1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "'p'; END $$",
                "REFUSED SQL compilation error: | Object 'H1' already exists as FUNCTION"},
            {"CREATE PROCEDURE h1(x OBJECT(b INT)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'p'; END $$",
                "Function H1 successfully created."},
            {"DESCRIBE FUNCTION h1(OBJECT(a INT))",
                "signature|(X OBJECT(a NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 't' "},
            {"DROP FUNCTION h1(OBJECT(a INT))", "H1 successfully dropped."},
            {"DROP FUNCTION h1(OBJECT(a INT NOT NULL))", "H1 successfully dropped."},
        });
    }

    @Test
    public void mapAndArrayShapesAreOverloadsToo() {
        engine.execute("CREATE OR REPLACE FUNCTION m1(x MAP(VARCHAR, INT)) RETURNS VARCHAR AS $$ 'mi' $$");
        assertCells(new String[][] {
            {"CREATE FUNCTION m1(x MAP(VARCHAR, VARCHAR)) RETURNS VARCHAR AS $$ 'mv' $$",
                "Function M1 successfully created."},
            {"CREATE FUNCTION m1(x MAP(NUMBER, INT)) RETURNS VARCHAR AS $$ 'ni' $$",
                "Function M1 successfully created."},
            {"CREATE FUNCTION m1(x MAP(VARCHAR(5), NUMBER(10,2))) RETURNS VARCHAR AS $$ 'w' $$",
                "REFUSED SQL compilation error: | Object 'M1' already exists."},
            {"SELECT m1({'a': 1}::MAP(VARCHAR, INT)), m1({'a': 'z'}::MAP(VARCHAR, VARCHAR))", "mi|mv"},
            {"CREATE FUNCTION m1(x ARRAY(INT)) RETURNS VARCHAR AS $$ 'ai' $$", "Function M1 successfully created."},
            {"CREATE FUNCTION m1(x ARRAY(VARCHAR)) RETURNS VARCHAR AS $$ 'av' $$", "Function M1 successfully created."},
            {"SELECT m1([1]::ARRAY(INT)), m1(['z']::ARRAY(VARCHAR))", "ai|av"},
            {"SELECT m1([1.5]::ARRAY(FLOAT))", "ai"},
            {"SELECT m1(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'M1': (ARRAY)"},
            {"DROP FUNCTION m1(MAP(VARCHAR, VARCHAR))", "M1 successfully dropped."},
            {"DROP FUNCTION m1(ARRAY(VARCHAR(3)))", "M1 successfully dropped."},
        });
        engine.execute("SHOW USER FUNCTIONS LIKE 'M1'");
        assertCells(new String[][] {
            {"SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "M1(ARRAY(NUMBER)) RETURN VARCHAR ;; M1(MAP(NUMBER, NUMBER)) RETURN VARCHAR ;; M1(MAP(VARCHAR, "
                + "NUMBER)) RETURN VARCHAR"},
        });
    }

    @Test
    public void aLeafBindsItsOwnFamilyAndAnyNumberAnyNumber() {
        engine.execute("CREATE OR REPLACE FUNCTION fi(o OBJECT(x INT)) RETURNS VARCHAR AS $$ 'int-param' $$");
        engine.execute("CREATE OR REPLACE FUNCTION fd(o OBJECT(x DATE)) RETURNS VARCHAR AS $$ 'date-param' $$");
        engine.execute("CREATE OR REPLACE FUNCTION fv(o OBJECT(x VARCHAR)) RETURNS VARCHAR AS $$ 'text-param' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ff(o OBJECT(x FLOAT)) RETURNS VARCHAR AS $$ 'float-param' $$");
        engine.execute("CREATE OR REPLACE FUNCTION fn(o OBJECT(x TIMESTAMP_NTZ)) RETURNS VARCHAR AS $$ 'ntz-param' $$");
        engine.execute("CREATE OR REPLACE FUNCTION fa(v ARRAY(INT)) RETURNS VARCHAR AS $$ 'int-array' $$");
        assertCells(new String[][] {
            {"SELECT fi({'x': 1.5}::OBJECT(x FLOAT))", "int-param"},
            {"SELECT fi({'x': 'a'}::OBJECT(x VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FI': (OBJECT(x VARCHAR(134217728)))"},
            {"SELECT fi({'x': TRUE}::OBJECT(x BOOLEAN))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FI': (OBJECT(x BOOLEAN))"},
            {"SELECT fd({'x': '10:00:00'}::OBJECT(x TIME))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FD': (OBJECT(x TIME(9)))"},
            {"SELECT fd({'x': '2024-01-01 10:00:00'}::OBJECT(x TIMESTAMP_NTZ))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FD': (OBJECT(x TIMESTAMP_NTZ(9)))"},
            {"SELECT fv({'x': 5}::OBJECT(x INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FV': (OBJECT(x NUMBER(38,0)))"},
            {"SELECT ff({'x': 5}::OBJECT(x INT))", "float-param"},
            {"SELECT fn({'x': '2024-01-01 10:00:00'}::OBJECT(x TIMESTAMP_LTZ))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FN': (OBJECT(x TIMESTAMP_LTZ(9)))"},
            {"SELECT fa([1.5]::ARRAY(FLOAT))", "int-array"},
            {"SELECT fa(['a']::ARRAY(VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FA': (ARRAY(VARCHAR(134217728)))"},
            {"SELECT fi({'x': 1.5}::OBJECT(x NUMBER(10,2)))", "int-param"},
        });
    }

    @Test
    public void ofOverloadsByLeafTheOneTheLeafBindsRuns() {
        engine.execute("CREATE OR REPLACE FUNCTION g2(o OBJECT(a INT)) RETURNS VARCHAR AS $$ 'ai' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g2(o OBJECT(a VARCHAR)) RETURNS VARCHAR AS $$ 'av' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g3(o OBJECT(a VARCHAR)) RETURNS VARCHAR AS $$ 'av' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g3(o OBJECT(b INT)) RETURNS VARCHAR AS $$ 'bi' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g4(o OBJECT(a INT)) RETURNS VARCHAR AS $$ 'ai' $$");
        engine.execute("CREATE OR REPLACE FUNCTION g4(o OBJECT(a DATE)) RETURNS VARCHAR AS $$ 'ad' $$");
        assertCells(new String[][] {
            {"SELECT g2({'a': 1.5}::OBJECT(a FLOAT))", "ai"},
            {"SELECT g2({'a': TRUE}::OBJECT(a BOOLEAN))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G2': (OBJECT(a BOOLEAN))"},
            {"SELECT g2({'a': '2024-01-01'}::OBJECT(a DATE))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G2': (OBJECT(a DATE))"},
            {"SELECT g3({'a': 1}::OBJECT(a INT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G3': (OBJECT(a NUMBER(38,0)))"},
            {"SELECT g3({'a': 1.5}::OBJECT(a FLOAT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G3': (OBJECT(a FLOAT))"},
            {"SELECT g4({'a': 'x'}::OBJECT(a VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G4': (OBJECT(a VARCHAR(134217728)))"},
            {"SELECT g4({'a': '2024-01-01 10:00:00'}::OBJECT(a TIMESTAMP_NTZ))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'G4': (OBJECT(a TIMESTAMP_NTZ(9)))"},
            {"SELECT g2(NULL)", "ai"},
            {"SELECT g2(NULL::OBJECT(a INT))", "ai"},
            {"SELECT g3(NULL)", "av"},
        });
    }
}
