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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Script recovery past a first fault: a later SHOW's keyword scope, statement keywords run into later statements, a
 * run-in DROP read past and a MERGE not resumed, the WHILE of END WHILE, and IF NOT's semicolon (all live-verified).
 */
public class ScriptRecoveryReadOnTest extends MeasuredCellsTestSupport {

    @Test
    public void scriptRecovery() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 29 unexpected 'x'.",
            "SELECT 1 +; SHOW TABLES IN t x; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 28 unexpected 'DROP'. | syntax error line 1 at position 53 unexpected 'y'.",
            "SELECT 1 +; SELECT 1 FROM t DROP TABLE t; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'. | syntax "
                + "error line 1 at position 57 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DROP TABLE t x y; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'MERGE'.",
            "SELECT 1 FROM t WHERE a = 1 MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN DELETE; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'.",
            "BEGIN WHILE (FALSE) DO SELECT 'foo' RETURN 1; END WHILE; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                + "error line 1 at position 32 unexpected 'y'. | syntax error line 1 at position 33 unexpected "
                + "'<EOF>'.",
            "CREATE TABLE IF NOT; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 +; SHOW TABLES IN t; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 24 unexpected 'x'. | syntax error line 1 at position 38 unexpected 'y'.",
            "SELECT 1 +; SHOW TABLES x; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "SELECT 1; SHOW TABLES IN t x; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 28 unexpected 'UPDATE'. | syntax error line 1 at position 59 unexpected "
                + "'y'.",
            "SELECT 1 +; SELECT 1 FROM t UPDATE t SET a = 1; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 28 unexpected 'CREATE'. | syntax error line 1 at position 63 unexpected "
                + "'y'.",
            "SELECT 1 +; SELECT 1 FROM t CREATE TABLE u (a INT); SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'. | syntax "
                + "error line 1 at position 53 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DROP TABLE t; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'. | syntax "
                + "error line 1 at position 55 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DROP TABLE t x; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'MERGE'.",
            "SELECT 1 FROM t WHERE a = 1 MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN DELETE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 47 unexpected 'x'.",
            "SELECT 1 FROM t WHERE a = 1 UPDATE t SET a = 1 x y; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 35 unexpected 'TABLE'. | syntax error line 1 at position 43 "
                + "unexpected '('.",
            "BEGIN LET a := 1 CREATE OR REPLACE TABLE u (a INT); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'SELECT'.",
            "BEGIN WHILE (FALSE) DO LET a := 1 SELECT 1; END WHILE; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'.",
            "BEGIN WHILE (FALSE) DO SELECT 'foo' RETURN 1; END WHILE; RETURN 2; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'.",
            "BEGIN LOOP SELECT 'foo' RETURN 1; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE TABLE IF NOT; SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "CREATE TABLE IF NOT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "CREATE TABLE IF; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'.",
            "CREATE TABLE IF NOT EXISTS; SELECT 1 x y");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
