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
 * A view named where a DML statement writes is expanded before it is refused: one whose table is gone answers
 * Failure during expansion of view 'V' to a DELETE, an UPDATE, a TRUNCATE, an INSERT and a MERGE, where a
 * whole view is told that the statement's target must be a table. A view is a MERGE's source as it is a FROM
 * clause's: named bare, by a path or under an alias, and expanded the same way (live-verified).
 */
public class ViewAsDmlTargetAndSourceTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewWhoseTableIsGone() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t1");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub() RETURNS TABLE (x INT) AS 'SELECT x FROM ub'");
        answer(
            "CREATE OR REPLACE FUNCTION f_outer() RETURNS NUMBER AS 'f_ue() + 1'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ok() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t1'");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "DELETE FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "UPDATE vv SET x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "TRUNCATE TABLE vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "INSERT INTO vv VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "MERGE INTO vv USING t1 ON vv.x = t1.a WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "DELETE FROM vv WHERE x = 1");
    }

    @Test
    public void aWholeView() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t1");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub() RETURNS TABLE (x INT) AS 'SELECT x FROM ub'");
        answer(
            "CREATE OR REPLACE FUNCTION f_outer() RETURNS NUMBER AS 'f_ue() + 1'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ok() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t1'");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE statement's target must be a table",
            "DELETE FROM vw");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE vw SET a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "TRUNCATE TABLE vw");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO vw VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE statement's target must be a table",
            "MERGE INTO vw USING t1 ON vw.a = t1.a WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE statement's target must be a table",
            "DELETE FROM vw WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "TRUNCATE vw");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "TRUNCATE TABLE IF EXISTS vw");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE statement's target must be a table",
            "DELETE FROM vw USING t1 WHERE vw.a = t1.a");
    }

    @Test
    public void nestedViewsAndAViewAsASource() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1)");
        answer(
            "CREATE OR REPLACE TABLE ue (x INT)");
        answer(
            "CREATE OR REPLACE TABLE ub (x INT)");
        answer(
            "CREATE OR REPLACE TABLE vb (x INT)");
        answer(
            "CREATE OR REPLACE VIEW vv AS SELECT x FROM vb");
        answer(
            "CREATE OR REPLACE VIEW vv2 AS SELECT x FROM vv");
        answer(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue'");
        answer(
            "CREATE OR REPLACE FUNCTION f_ub(p INT) RETURNS TABLE (x INT) AS 'SELECT x FROM ub WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_p(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p INT) RETURNS NUMBER AS 'SELECT COUNT(*) FROM ue WHERE x = p'");
        answer(
            "CREATE OR REPLACE FUNCTION f_o(p VARCHAR) RETURNS NUMBER AS 'LENGTH(p)'");
        answer(
            "CREATE OR REPLACE FUNCTION f_v() RETURNS NUMBER AS 'SELECT COUNT(*) FROM vv'");
        answer(
            "CREATE OR REPLACE FUNCTION f_sub() RETURNS NUMBER AS '(SELECT COUNT(*) FROM ue) + 1'");
        answer(
            "CREATE OR REPLACE VIEW vfn AS SELECT f_ue() AS c FROM t");
        answer(
            "DROP TABLE ue");
        answer(
            "DROP TABLE ub");
        answer(
            "DROP TABLE vb");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV2': SQL compilation error: | "
                + "Failure during expansion of view 'VV': SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.VB' "
                + "does not exist or not authorized. Your primary role <ROLE> must have at least one privilege "
                + "granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "DELETE FROM vv2");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "UPDATE vv SET nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "UPDATE vv SET x = nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "INSERT INTO vv SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "INSERT INTO vv (x) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "TRUNCATE TABLE IF EXISTS vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "TRUNCATE vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "MERGE INTO t USING vv ON t.a = vv.x WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "INSERT INTO t SELECT x FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "UPDATE t SET a = 1 FROM vv WHERE t.a = vv.x");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "DELETE FROM t USING vv WHERE t.a = vv.x");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "INSERT ALL INTO vv VALUES (1) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV2': SQL compilation error: | "
                + "Failure during expansion of view 'VV': SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.VB' "
                + "does not exist or not authorized. Your primary role <ROLE> must have at least one privilege "
                + "granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM vv2");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vv");
    }

    @Test
    public void aViewAsAMergesSource() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "INSERT INTO t VALUES (1), (2)");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "INSERT INTO t1 VALUES (1), (3)");
        answer(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t1");
        assertAnswer(
            "1",
            "MERGE INTO t USING vw ON t.a = vw.a WHEN MATCHED THEN DELETE");
        assertAnswer(
            "2",
            "SELECT a FROM t ORDER BY a");
        assertAnswer(
            "2",
            "MERGE INTO t USING vw s ON t.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a)");
        assertAnswer(
            "1 ;; 2 ;; 3",
            "SELECT a FROM t ORDER BY a");
        assertAnswer(
            "2",
            "MERGE INTO t USING test_schema.vw s ON t.a = s.a WHEN MATCHED THEN UPDATE SET a = s.a + 10");
        assertAnswer(
            "2 ;; 11 ;; 13",
            "SELECT a FROM t ORDER BY a");
        assertAnswer(
            "0",
            "MERGE INTO t USING (SELECT a FROM vw) s ON t.a = s.a WHEN MATCHED THEN DELETE");
        assertAnswer(
            "2 ;; 11 ;; 13",
            "SELECT a FROM t ORDER BY a");
        assertAnswer(
            "VW successfully dropped.",
            "DROP VIEW IF EXISTS vw");
    }
}
