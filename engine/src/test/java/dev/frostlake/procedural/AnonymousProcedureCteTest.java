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

package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A CALL with an anonymous procedure takes common table expressions after the procedure's definition, comma before
 * each, and its own procedure hides its name from a schema's: written under a schema it names no routine at all
 * (live-verified). The CALL runs where a CALL does, inside a block's RESULTSET too.
 */
public class AnonymousProcedureCteTest extends MeasuredCellsTestSupport {

    @Test
    public void anAnonymousProcedureWithCommonTableExpressions() {
        engine.execute("CREATE OR REPLACE PROCEDURE np912() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 100; END'");
        assertCells(new String[][] {
            {"WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()", "7"},
            {"WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END', c AS (SELECT 1 AS one) "
                + "CALL p()",
                "7"},
            {"WITH p AS PROCEDURE (x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN x + 1; END', c AS (SELECT 1 "
                + "AS one) CALL p(2)",
                "3"},
            {"WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' COMMENT = 'x' CALL p()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'COMMENT'."},
            {"WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL COMMENT = 'x' AS 'BEGIN RETURN 7; END' CALL p()", "7"},
            {"WITH np912 AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END' CALL np912()", "1"},
            {"WITH np912 AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END' CALL test_schema.np912()",
                "REFUSED SQL compilation error: | Unknown user-defined function TEST_SCHEMA.NP912."},
            {"WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN nosuch_912; END' CALL p()",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH_912'"},
            {"EXECUTE IMMEDIATE $$BEGIN LET rs RESULTSET := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS "
                + "'BEGIN RETURN 7; END' CALL p()); RETURN TABLE(rs); END$$",
                "7"},
        });
    }
}
