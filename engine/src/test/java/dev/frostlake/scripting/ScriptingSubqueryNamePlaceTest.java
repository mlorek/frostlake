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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a name a block cannot resolve is reported, and when. An untyped LET or DECLARE compiles the FROM-less
 * subqueries it infers its type from while the block compiles, so their unknown names are compilation errors
 * placed in the block, even on a branch that never runs. A typed declaration, an assignment, a RETURN or a
 * condition meets the name when it runs, and the error inside the uncaught EXPRESSION_ERROR is placed in the
 * expression after a fixed lead-in on its first line. A failing SQL statement's error is placed in the
 * statement's own text. Every cell is live-verified.
 */
public class ScriptingSubqueryNamePlaceTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE TABLE t (x NUMBER)");
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            if ("setup".equals(cell[0])) {
                engine.execute(cell[1]);
            } else if ("refused".equals(cell[0])) {
                final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                    @Override
                    public void execute() {
                        engine.executeQuery(cell[1]);
                    }
                }, cell[1]);
                assertTrue(String.valueOf(refused.getMessage()).contains(cell[2]),
                    cell[1] + " should be refused with [" + cell[2] + "] but read: " + refused.getMessage());
            } else {
                final Row row = engine.executeQuery(cell[1]).getRows().get(0);
                assertEquals(cell[2], String.valueOf(row.getValue(0)).toLowerCase(), cell[1]);
            }
        }
    }

    @Test
    public void anUntypedDeclarationCompilesItsSubqueryWithTheBlock() {
        assertCells(new String[][] {
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT missing); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET a := (SELECT missing); END IF; RETURN 5; END;$$", "SQL compilation error: error line 1 at position 39\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT missing); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 6\n variable 'A' cannot have its type inferred from initializer"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT 1 + missing); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 27\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT 1 + missing); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 27\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$DECLARE a NUMBER DEFAULT (SELECT missing); BEGIN RETURN a; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN RETURN (SELECT missing); END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation error: error line 1 at position 15\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := 1; a := (SELECT missing); RETURN a; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN SELECT missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT missing2 FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING2'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET x NUMBER := 1; LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET x NUMBER := 1; LET a := (SELECT missing); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 42\ninvalid identifier 'MISSING'"},
            {"answer", "EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b)); END IF; RETURN 5; END;$$", "5"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT b, missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 26\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b) WHERE b = 1); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT UPPER(missing)); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 29\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a VARCHAR := (SELECT missing); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT 1); RETURN (SELECT missing FROM (SELECT 1 AS b)); END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation error: error line 1 at position 15\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT t.missing FROM (SELECT 1 AS b) t); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'T.MISSING'"},
            {"setup", "CREATE OR REPLACE PROCEDURE p645a() RETURNS NUMBER LANGUAGE SQL AS $$BEGIN LET a := (SELECT missing); RETURN 5; END;$$", ""},
            {"refused", "CALL p645a()", "SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"setup", "CREATE OR REPLACE PROCEDURE p645b() RETURNS NUMBER LANGUAGE SQL AS $$BEGIN LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", ""},
            {"refused", "CALL p645b()", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"setup", "CREATE OR REPLACE PROCEDURE p645c() RETURNS NUMBER LANGUAGE SQL AS $$BEGIN IF (FALSE) THEN LET a := (SELECT missing); END IF; RETURN 5; END;$$", ""},
            {"refused", "CALL p645c()", "SQL compilation error: error line 1 at position 39\ninvalid identifier 'MISSING'"},
        });
    }

    @Test
    public void aRunningExpressionPlacesItsErrorInTheExpression() {
        assertCells(new String[][] {
            {"refused", "EXECUTE IMMEDIATE $$BEGIN IF ((SELECT missing FROM (SELECT 1 AS b)) = 1) THEN RETURN 1; END IF; RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL compilation error: error line 1 at position 15\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN WHILE ((SELECT missing FROM (SELECT 1 AS b)) = 1) DO RETURN 1; END WHILE; RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation error: error line 1 at position 15\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := 1 + (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 27\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN CASE (SELECT missing FROM (SELECT 1 AS b)) WHEN 1 THEN RETURN 1; END CASE; RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 11 : SQL compilation error: error line 1 at position 15\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN INSERT INTO t SELECT missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation error: error line 1 at position 21\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT   missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 25\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN RETURN 1 + (SELECT missing FROM (SELECT 1 AS b)); END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation error: error line 1 at position 19\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := ( SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 24\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := 1; a := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN    SELECT missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 9 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := (SELECT missing); RETURN a; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := 2 * (1 + (SELECT missing)); RETURN a; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation error: error line 1 at position 32\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := 1 + (SELECT missing); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 27\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT 1); LET b := (SELECT missing); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 44\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT missing, 2); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT missing FROM t); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 6\n variable 'A' cannot have its type inferred from initializer"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := (SELECT x FROM t WHERE missing = 1); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 6\n variable 'A' cannot have its type inferred from initializer"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN RETURN (SELECT missing); END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation error: error line 1 at position 15\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := missing; RETURN 5; END;$$", "SQL compilation error: error line 1 at position 6\n variable 'A' cannot have its type inferred from initializer"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := missing; RETURN 5; END;$$", "SQL compilation error: error line 1 at position 22\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$DECLARE a DEFAULT (SELECT missing); BEGIN RETURN 5; END;$$", "SQL compilation error: error line 1 at position 26\ninvalid identifier 'MISSING'"},
        });
    }

    @Test
    public void multiLineBlocksAndScriptingVariables() {
        assertCells(new String[][] {
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  SELECT missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b));\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 18 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET a := (SELECT missing);\n  RETURN 5;\nEND;$$", "SQL compilation error: error line 2 at position 19\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET a NUMBER := (SELECT\n    missing FROM (SELECT 1 AS b));\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 18 : SQL compilation error: error line 2 at position 4\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET a := (SELECT\n    missing);\n  RETURN 5;\nEND;$$", "SQL compilation error: error line 3 at position 4\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  SELECT\n    missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation error: error line 2 at position 4\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  RETURN (SELECT\n    missing FROM (SELECT 1 AS b));\nEND;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 9 : SQL compilation error: error line 2 at position 4\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  INSERT INTO t\n  SELECT missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation error: error line 2 at position 9\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$\nBEGIN\n  LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b));\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 3 at position 18 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  IF (TRUE) THEN\n    LET a NUMBER := (SELECT missing FROM (SELECT 1 AS b));\n  END IF;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 3 at position 20 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := 1; LET b := (SELECT a); RETURN b; END;$$", "SQL compilation error: error line 1 at position 35\ninvalid identifier 'A'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a := 1; LET b NUMBER := (SELECT a); RETURN b; END;$$", "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation error: error line 1 at position 23\ninvalid identifier 'A'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET a NUMBER := 1; LET b := (SELECT a + 1); RETURN b; END;$$", "SQL compilation error: error line 1 at position 42\ninvalid identifier 'A'"},
            {"refused", "EXECUTE IMMEDIATE $$DECLARE a NUMBER DEFAULT 1; BEGIN LET b := (SELECT a); RETURN b; END;$$", "SQL compilation error: error line 1 at position 51\ninvalid identifier 'A'"},
            {"answer", "EXECUTE IMMEDIATE $$BEGIN LET b := (SELECT CURRENT_DATE()); RETURN 5; END;$$", "5"},
            {"answer", "EXECUTE IMMEDIATE $$BEGIN LET b := (SELECT 1 AS missing); RETURN b; END;$$", "1"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET b := (SELECT missing()); RETURN 5; END;$$", "SQL compilation error:\nUnknown function MISSING."},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET b := (SELECT $missing); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 23\nSession variable '$MISSING' does not exist"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET b := (SELECT \"missing\"); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 23\ninvalid identifier '\"missing\"'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET b := (SELECT missing) + (SELECT missing2); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 23\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET b := IFF(TRUE, 1, (SELECT missing)); RETURN 5; END;$$", "SQL compilation error: error line 1 at position 36\ninvalid identifier 'MISSING'"},
            {"refused", "SELECT missing FROM (SELECT 1 AS b) WHERE missing2 = 1", "SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
        });
    }

    @Test
    public void aFailingStatementPlacesItsErrorInItsOwnText() {
        assertCells(new String[][] {
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; SELECT missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  SELECT missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; SELECT missing FROM t; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  SELECT missing FROM t;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; SELECT x FROM t WHERE missing = 1; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 22\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  SELECT x FROM t WHERE missing = 1;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 22\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; INSERT INTO t SELECT missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 21\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  INSERT INTO t SELECT missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 21\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; INSERT INTO t VALUES (missing); RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 22\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  INSERT INTO t VALUES (missing);\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 22\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; UPDATE t SET x = missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 17\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  UPDATE t SET x = missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 17\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; DELETE FROM t WHERE missing = 1; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 20\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  DELETE FROM t WHERE missing = 1;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 20\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; CREATE OR REPLACE TABLE t2 AS SELECT missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 37\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  CREATE OR REPLACE TABLE t2 AS SELECT missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 37\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; SELECT 1 + missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 11\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  SELECT 1 + missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 11\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; LET c CURSOR FOR SELECT missing; OPEN c; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  LET c CURSOR FOR SELECT missing; OPEN c;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 35 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$DECLARE v NUMBER; BEGIN LET z := 1; SELECT missing INTO :v; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 36 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$DECLARE v NUMBER; BEGIN\n  LET z := 1;\n  SELECT missing INTO :v;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; MERGE INTO t USING (SELECT 1 AS y) s ON t.x = s.y WHEN MATCHED THEN UPDATE SET x = missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 83\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  MERGE INTO t USING (SELECT 1 AS y) s ON t.x = s.y WHEN MATCHED THEN UPDATE SET x = missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 83\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; SELECT COUNT(*) FROM t GROUP BY missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 32\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  SELECT COUNT(*) FROM t GROUP BY missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 32\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; SELECT x FROM t ORDER BY missing; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 25\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  SELECT x FROM t ORDER BY missing;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 25\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN LET z := 1; WITH w AS (SELECT missing) SELECT * FROM w; RETURN 5; END;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 at position 18\ninvalid identifier 'MISSING'"},
            {"refused", "EXECUTE IMMEDIATE $$BEGIN\n  LET z := 1;\n  WITH w AS (SELECT missing) SELECT * FROM w;\n  RETURN 5;\nEND;$$", "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: error line 1 at position 18\ninvalid identifier 'MISSING'"},
        });
    }
}
