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
 * A table procedure called after LATERAL or joined with ON: an argument that reads a relation to its left is an
 * invalid argument where the column stands, after the names and the argument types are judged and ahead of the
 * restriction a lateral table function meets with a join predicate (all live-verified).
 */
public class TableProcedureLateralArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void lateralOrJoined() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('a')");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(c VARCHAR) RETURNS TABLE (x VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET DEFAULT (SELECT :c AS x); BEGIN RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('x', 1)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b INT)");
        engine.execute("INSERT INTO fz VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN TABLE(qa(t1.c)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1, LATERAL qa(t1.c)");
    }

    @Test
    public void everyForm() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('a')");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(c VARCHAR) RETURNS TABLE (x VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET DEFAULT (SELECT :c AS x); BEGIN RETURN TABLE(r); END; $$");
        assertAnswer("a|x", "SELECT * FROM t1 JOIN TABLE(qa('x')) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 36: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 LEFT JOIN TABLE(qa(t1.c)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 33: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN LATERAL qa(t1.c) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN TABLE(qa(t1.c))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for "
                + "function 'QA': (VARCHAR(16777216), NUMBER(1,0))",
            "SELECT * FROM t1 JOIN TABLE(qa(t1.c, 2)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH'",
            "SELECT * FROM t1 JOIN TABLE(qa(nosuch)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN TABLE(qa(c)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1, LATERAL qa(c)");
        assertAnswer("REFUSED SQL compilation error: | Invalid identifier C",
            "SELECT * FROM t1 JOIN TABLE(qa(t1.c)) USING (c)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 CROSS JOIN TABLE(qa(t1.c))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN TABLE(qa(UPPER(t1.c))) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 NATURAL JOIN TABLE(qa(t1.c))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1, LATERAL qa(c => t1.c)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 36: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN TABLE(qa(c => t1.c)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1 JOIN TABLE(qa(t1.c)) ON t1.c = 'a'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29: Invalid argument T1.C for procedure "
                + "in FROM clause",
            "SELECT * FROM t1, LATERAL qa(t1.c) q");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for "
                + "function 'QA': (NUMBER(1,0))",
            "SELECT * FROM t1 JOIN TABLE(qa(1)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | Invalid argument types for "
                + "function 'QA': (VARCHAR(16777216), NUMBER(1,0))",
            "SELECT * FROM t1, LATERAL qa(t1.c, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH'",
            "SELECT * FROM t1, LATERAL qa(nosuch)");
    }
}
