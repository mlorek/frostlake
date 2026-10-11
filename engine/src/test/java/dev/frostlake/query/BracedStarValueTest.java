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
 * The braced star where a value stands: the row as an OBJECT, OBJECT_CONSTRUCT over the star's columns. It is
 * an argument, an operand and a path's root, named in the column as written; it takes one EXCLUDE or one ILIKE,
 * stands in the select list and in GROUP BY only, and two columns of one name under it are a duplicate key. Every
 * answer is live-verified.
 */
public class BracedStarValueTest extends MeasuredCellsTestSupport {

    @Test
    public void asAnArgumentAndAnOperand() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({*}) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON({* EXCLUDE b}) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({FZ.*}) FROM FZ ORDER BY 1");
        assertAnswer(
            "[{\"B\":true,\"ID\":1}] ;; [{\"B\":false,\"ID\":2}]",
            "SELECT ARRAY_CONSTRUCT({*}) FROM FZ ORDER BY id");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"B\":false,\"ID\":2}",
            "SELECT {*} FROM FZ ORDER BY id");
        assertAnswer(
            "{\"B\":true,\"ID\":1}|1 ;; {\"B\":false,\"ID\":2}|2",
            "SELECT TO_JSON({*}), id FROM FZ ORDER BY id");
        assertAnswer(
            "[\"B\",\"ID\"] ;; [\"B\",\"ID\"]",
            "SELECT OBJECT_KEYS({*}) FROM FZ ORDER BY id");
        assertAnswer(
            "1 ;; 2",
            "SELECT {*}:ID FROM FZ ORDER BY id");
        assertAnswer(
            "true ;; true",
            "SELECT {*} = {*} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT id FROM FZ WHERE {*}:ID = 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"B\":false,\"ID\":2}",
            "SELECT ({*}) FROM FZ ORDER BY id");
        assertAnswer(
            "{\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON({* ILIKE 'i%'}) FROM FZ ORDER BY 1");
        assertAnswer(
            "2",
            "SELECT COUNT({*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT id FROM FZ ORDER BY {*}:ID DESC");
        assertAnswer(
            "{\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON({FZ.* EXCLUDE (b)}) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({ * }) x FROM FZ ORDER BY 1");
        assertAnswer(
            "REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON({*}) FROM FZ f JOIN FY y ON f.id = y.id");
        assertAnswer(
            "{\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({f.*}) FROM FZ f JOIN FY y ON f.id = y.id");
        assertAnswer(
            "{}",
            "SELECT TO_JSON({*})");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'ZZ' does not exist or not authorized.",
            "SELECT TO_JSON({zz.*}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'UPPER': (OBJECT)",
            "SELECT UPPER({*}) FROM FZ");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"B\":false,\"ID\":2}",
            "SELECT {*}::VARCHAR FROM FZ ORDER BY id");
        assertAnswer(
            "{\"B\":true,\"ID\":1,\"k\":1} ;; {\"B\":false,\"ID\":2,\"k\":1}",
            "SELECT OBJECT_INSERT({*}, 'k', 1) FROM FZ ORDER BY id");
        assertAnswer(
            "[{\"B\":true,\"ID\":1},{\"B\":false,\"ID\":2}]",
            "SELECT ARRAY_AGG({*}) WITHIN GROUP (ORDER BY id) FROM FZ");
        assertAnswer(
            "{\"B\":false,\"ID\":2}x ;; {\"B\":true,\"ID\":1}x",
            "SELECT TO_JSON({*} ) || 'x' FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; null",
            "SELECT CASE WHEN id = 1 THEN {*} END FROM FZ ORDER BY id");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; null",
            "SELECT IFF(b, {*}, NULL) FROM FZ ORDER BY id");
        assertAnswer(
            "{\"B\":true,\"ID\":1}|{\"B\":true,\"ID\":1} ;; {\"B\":false,\"ID\":2}|{\"B\":false,\"ID\":2}",
            "SELECT {*} AS o, TO_JSON({*}) AS j FROM FZ ORDER BY id");
        assertAnswer(
            "{} ;; {}",
            "SELECT TO_JSON({* EXCLUDE (id, b)}) FROM FZ");
    }

    @Test
    public void theColumnItNames() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertShape(
            "TO_JSON({*}):VARCHAR(134217728)",
            "SELECT TO_JSON({*}) FROM FZ");
        assertShape(
            "TO_JSON({* EXCLUDE B}):VARCHAR(134217728)",
            "SELECT TO_JSON({* EXCLUDE b}) FROM FZ");
        assertShape(
            "TO_JSON({FZ.*}):VARCHAR(134217728)",
            "SELECT TO_JSON({FZ.*}) FROM FZ");
        assertShape(
            "ARRAY_CONSTRUCT({*}):ARRAY",
            "SELECT ARRAY_CONSTRUCT({*}) FROM FZ");
        assertShape(
            "{*}:ID:VARIANT",
            "SELECT {*}:ID FROM FZ");
        assertShape(
            "({*}):OBJECT",
            "SELECT ({*}) FROM FZ");
        assertShape(
            "TO_JSON({ * }):VARCHAR(134217728)",
            "SELECT TO_JSON({ * }) FROM FZ");
        assertShape(
            "TO_JSON({FZ.* EXCLUDE (B)}):VARCHAR(134217728)",
            "SELECT TO_JSON({fz.* exclude (b)}) FROM FZ");
        assertShape(
            "OBJECT_KEYS({*}):ARRAY",
            "SELECT OBJECT_KEYS({*}) FROM FZ");
        assertShape(
            "{*}::VARCHAR:VARCHAR(134217728)",
            "SELECT {*}::VARCHAR FROM FZ");
    }

    @Test
    public void besideTheStarCallItStandsFor() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON({*}) FROM FN ORDER BY 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(*)) FROM FN ORDER BY 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"ID\":2}",
            "SELECT {*} FROM FN ORDER BY id");
        assertAnswer(
            "{\"B\":true,\"ID\":1} ;; {\"ID\":2}",
            "SELECT OBJECT_CONSTRUCT(*) FROM FN ORDER BY id");
        assertAnswer(
            "REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON({*}) FROM FZ f JOIN FY y ON f.id = y.id");
        assertAnswer(
            "REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(*)) FROM FZ f JOIN FY y ON f.id = y.id");
        assertAnswer(
            "1 ;; 2",
            "SELECT id FROM FZ GROUP BY id, {*} ORDER BY 1");
        assertAnswer(
            "1 ;; 2",
            "SELECT id FROM FZ GROUP BY id, OBJECT_CONSTRUCT(*) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT id FROM FZ GROUP BY id HAVING {*} IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT id FROM FZ GROUP BY id HAVING OBJECT_CONSTRUCT(*) IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT id FROM FZ QUALIFY {*} IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT id FROM FZ QUALIFY OBJECT_CONSTRUCT(*) IS NOT NULL");
        assertAnswer(
            "2",
            "SELECT COUNT({*}) FROM FN");
        assertAnswer(
            "2",
            "SELECT COUNT(OBJECT_CONSTRUCT(*)) FROM FN");
        assertAnswer(
            "{} ;; {}",
            "SELECT (SELECT TO_JSON({*})) FROM FZ ORDER BY id");
        assertAnswer(
            "{} ;; {}",
            "SELECT (SELECT TO_JSON(OBJECT_CONSTRUCT(*))) FROM FZ ORDER BY id");
        assertAnswer(
            "{\"A\":1,\"B\":2}",
            "SELECT TO_JSON({*}) FROM (SELECT 1 a, 2 b)");
        assertAnswer(
            "{\"A\":1,\"B\":2}",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(*)) FROM (SELECT 1 a, 2 b)");
        assertAnswer(
            "2 ;; 3",
            "SELECT {*}:ID::INT + 1 FROM FZ ORDER BY 1");
        assertAnswer(
            "2 ;; 3",
            "SELECT OBJECT_CONSTRUCT(*):ID::INT + 1 FROM FZ ORDER BY 1");
        assertAnswer(
            "1 ;; 2",
            "SELECT {*}['ID'] FROM FZ ORDER BY 1");
        assertAnswer(
            "1 ;; 2",
            "SELECT OBJECT_CONSTRUCT(*)['ID'] FROM FZ ORDER BY 1");
        assertAnswer(
            "false ;; false",
            "SELECT {*} IS NULL FROM FZ");
        assertAnswer(
            "false ;; false",
            "SELECT OBJECT_CONSTRUCT(*) IS NULL FROM FZ");
        assertAnswer(
            "REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON({*}) FROM FZ, FY");
        assertAnswer(
            "REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(*)) FROM FZ, FY");
        assertAnswer(
            "{\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({*}) FROM FZ f JOIN FY y USING (id)");
        assertAnswer(
            "{\"B\":true,\"ID\":1}",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(*)) FROM FZ f JOIN FY y USING (id)");
        assertAnswer(
            "B ;; B",
            "SELECT OBJECT_KEYS({*})[0]::VARCHAR FROM FZ ORDER BY id");
        assertAnswer(
            "B ;; B",
            "SELECT OBJECT_KEYS(OBJECT_CONSTRUCT(*))[0]::VARCHAR FROM FZ ORDER BY id");
        assertAnswer(
            "1 ;; 3",
            "SELECT SUM(id) OVER (ORDER BY {*}:ID) FROM FZ ORDER BY 1");
        assertAnswer(
            "1 ;; 3",
            "SELECT SUM(id) OVER (ORDER BY OBJECT_CONSTRUCT(*):ID) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1}",
            "SELECT MAX(TO_JSON({*})) FROM FZ");
        assertAnswer(
            "{\"B\":true,\"ID\":1}",
            "SELECT MAX(TO_JSON(OBJECT_CONSTRUCT(*))) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | 'FZ.B' in select clause is neither an "
                + "aggregate nor in the group by clause.",
            "SELECT TO_JSON({*}) x, COUNT(*) FROM FZ GROUP BY id ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | 'FZ.B' in select clause is neither an "
                + "aggregate nor in the group by clause.",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(*)) x, COUNT(*) FROM FZ GROUP BY id ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'EXCLUDE'. | syntax "
                + "error line 1 at position 37 unexpected 'b'.",
            "SELECT TO_JSON({* ILIKE 'i%' EXCLUDE b}) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | column 'NOSUCH' does not exist",
            "SELECT TO_JSON({* EXCLUDE nosuch}) FROM FZ");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({\"FZ\".*}) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({test_schema.FZ.*}) FROM FZ ORDER BY 1");
        assertAnswer(
            "{} ;; {}",
            "SELECT {} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '.'.",
            "SELECT {*}.id FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEGATE': (OBJECT)",
            "SELECT -{*} FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'||': (OBJECT, VARCHAR(1))",
            "SELECT {*} || 'x' FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1}|{\"B\":true,\"ID\":1} ;; {\"B\":false,\"ID\":2}|{\"B\":false,\"ID\":2}",
            "SELECT {*} x, {*} FROM FZ ORDER BY id");
        assertAnswer(
            "[{\"B\":true,\"ID\":1},{\"ID\":1}] ;; [{\"B\":false,\"ID\":2},{\"ID\":2}]",
            "SELECT ARRAY_CONSTRUCT({*}, {* EXCLUDE b}) FROM FZ ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT TO_JSON({*}) FROM FZ ORDER BY {*}:ID");
        assertAnswer(
            "REFUSED SQL compilation error: | Use of * as a function argument is only allowed in the SELECT "
                + "clause.",
            "SELECT 1 FROM FZ WHERE TO_JSON({*}) IS NOT NULL");
        assertAnswer(
            "0",
            "INSERT INTO FY SELECT {*}:ID FROM FZ WHERE id = 99");
        assertAnswer(
            "{\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON({* EXCLUDE (b) }) AS j FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON( { * } ) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"a\":{\"B\":false,\"ID\":2}} ;; {\"a\":{\"B\":true,\"ID\":1}}",
            "SELECT TO_JSON({'a': {*}}) FROM FZ ORDER BY 1");
        assertAnswer(
            "[{\"B\":true,\"ID\":1}] ;; [{\"B\":false,\"ID\":2}]",
            "SELECT [{*}] FROM FZ ORDER BY id");
        assertAnswer(
            "[1,2]",
            "SELECT ARRAY_AGG(DISTINCT {*}:ID) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES ({*})");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '{'.",
            "SELECT TO_JSON({*}) FROM FZ LIMIT 1 OFFSET {*}:ID");
    }

    @Test
    public void theColumnsOfTheSecondMeasurement() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertShape(
            "TO_JSON({* ILIKE 'I%'}):VARCHAR(134217728)",
            "SELECT TO_JSON({* ILIKE 'i%'}) FROM FZ");
        assertShape(
            "{*}:ID::INT + 1:NUMBER(38,0)",
            "SELECT {*}:ID::INT + 1 FROM FZ");
        assertShape(
            "{*}['ID']:VARIANT",
            "SELECT {*}['ID'] FROM FZ");
        assertShape(
            "{*} IS NULL:BOOLEAN",
            "SELECT {*} IS NULL FROM FZ");
        assertShape(
            "TO_JSON({\"FZ\".*}):VARCHAR(134217728)",
            "SELECT TO_JSON({\"FZ\".*}) FROM FZ");
        assertShape(
            "TO_JSON({* EXCLUDE (ID, B)}):VARCHAR(134217728)",
            "SELECT TO_JSON({* EXCLUDE (id, b)}) FROM FZ");
        assertShape(
            "ARRAY_CONSTRUCT({*}, {* EXCLUDE B}):ARRAY",
            "SELECT ARRAY_CONSTRUCT({*}, {* EXCLUDE b}) FROM FZ");
        assertShape(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'||': (OBJECT, VARCHAR(1))",
            "SELECT {*} || 'x' FROM FZ");
        assertShape(
            "X:VARCHAR(134217728)",
            "SELECT TO_JSON({*}) x FROM FZ");
        assertShape(
            "(SELECT TO_JSON({*})):VARCHAR(134217728)",
            "SELECT (SELECT TO_JSON({*})) FROM FZ");
        assertShape(
            "CASE WHEN ID = 1 THEN {*} END:OBJECT",
            "SELECT CASE WHEN id = 1 THEN {*} END FROM FZ");
        assertShape(
            "TO_JSON({*  EXCLUDE  B}):VARCHAR(134217728)",
            "SELECT TO_JSON({*  exclude  b}) FROM FZ");
        assertShape(
            "TO_JSON({F.*}):VARCHAR(134217728)",
            "SELECT TO_JSON({f.*}) FROM FZ f");
    }

    @Test
    public void filtersAndJoins() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "{\"ID\":1} ;; {\"ID\":2}",
            "SELECT TO_JSON(OBJECT_CONSTRUCT(* ILIKE 'i%')) FROM FZ ORDER BY 1");
        assertAnswer(
            "{\"B\":false} ;; {\"B\":true}",
            "SELECT TO_JSON({* ILIKE 'B'}) FROM FZ ORDER BY 1");
        assertAnswer(
            "",
            "SELECT TO_JSON({*}) FROM FZ f JOIN FY y ON f.id = y.id WHERE 1 = 0");
        assertAnswer(
            "{\"B\":false,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({f.*}) FROM FZ f JOIN FZ g ON f.id = g.id ORDER BY 1");
        assertAnswer(
            "{\"B\":true,\"ID\":1}",
            "SELECT TO_JSON({*}) FROM FZ f NATURAL JOIN FY y ORDER BY 1");
        assertAnswer(
            "{\"B\":null,\"ID\":2} ;; {\"B\":true,\"ID\":1}",
            "SELECT TO_JSON(OBJECT_CONSTRUCT_KEEP_NULL(*)) FROM FN ORDER BY 1");
        assertAnswer(
            "REFUSED Duplicate field key 'A'",
            "SELECT TO_JSON({*}) FROM (SELECT 1 a, 2 a)");
    }
}
