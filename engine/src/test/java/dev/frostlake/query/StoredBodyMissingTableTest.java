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
 * A view, a SQL table function and a SQL function whose table was dropped. The missing table is named by its
 * full path in the schema of the object that reads it, however its body wrote it; a view wraps the refusal
 * in its own sentence, once for each view the reading passes through, and leaves a missing column's refusal
 * as it is. The view is refused wherever a statement reads it - a join, a subquery, a CTE, an INSERT's or a
 * CREATE's query, an EXPLAIN - and is described and scripted without being read. Every answer is
 * live-verified.
 */
public class StoredBodyMissingTableTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyOverADroppedTableNamesItInFull() {
        engine.execute(
            "CREATE OR REPLACE TABLE vb (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vv AS SELECT a FROM vb");
        engine.execute(
            "DROP TABLE vb");
        engine.execute(
            "CREATE OR REPLACE TABLE vc (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vc1 AS SELECT a FROM vc");
        engine.execute(
            "CREATE OR REPLACE VIEW vc2 AS SELECT a FROM vc1");
        engine.execute(
            "DROP TABLE vc");
        engine.execute(
            "CREATE OR REPLACE TABLE vd (a INT, b INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vd1 AS SELECT b FROM vd");
        engine.execute(
            "ALTER TABLE vd DROP COLUMN b");
        engine.execute(
            "CREATE OR REPLACE TABLE ub (a INT)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f_ub() RETURNS TABLE (a INT) AS 'SELECT a FROM ub'");
        engine.execute(
            "DROP TABLE ub");
        engine.execute(
            "CREATE OR REPLACE TABLE ue (a INT)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f_ue() RETURNS INT AS 'SELECT COUNT(*) FROM ue'");
        engine.execute(
            "DROP TABLE ue");
        engine.execute(
            "CREATE OR REPLACE TABLE vqt (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vq AS SELECT a FROM test_schema.vqt");
        engine.execute(
            "CREATE OR REPLACE VIEW vq3 AS SELECT a FROM test_db.test_schema.vqt");
        engine.execute(
            "CREATE OR REPLACE VIEW \"v low\" AS SELECT a FROM vqt");
        engine.execute(
            "DROP TABLE vqt");
        engine.execute(
            "CREATE OR REPLACE TABLE vj (a INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE vk (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vjk AS SELECT vj.a FROM vj JOIN vk ON vj.a = vk.a");
        engine.execute(
            "DROP TABLE vk");
        engine.execute(
            "CREATE OR REPLACE TABLE \"t low\" (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vlow AS SELECT a FROM \"t low\"");
        engine.execute(
            "DROP TABLE \"t low\"");
        engine.execute(
            "CREATE OR REPLACE TABLE ug (a INT)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f_ug(x INT) RETURNS INT AS '(SELECT MAX(a) FROM ug) + x'");
        engine.execute(
            "DROP TABLE ug");
        engine.execute(
            "CREATE OR REPLACE TABLE vs (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vs1 AS SELECT a FROM vs");
        engine.execute(
            "DROP TABLE vs");
        engine.execute(
            "CREATE OR REPLACE VIEW vs AS SELECT 1 AS a");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT COUNT(*) FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VC2': SQL compilation error: | "
                + "Failure during expansion of view 'VC1': SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.VC' "
                + "does not exist or not authorized. Your primary role <ROLE> must have at least one privilege "
                + "granted on TABLE TEST_DB.TEST_SCHEMA.VC.",
            "SELECT * FROM vc2");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VC1': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VC' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VC.",
            "SELECT * FROM vc1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'B'",
            "SELECT * FROM vd1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM TABLE(f_ub())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UE.",
            "SELECT f_ue()");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT a FROM vv WHERE a > 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT 1 FROM vv v JOIN vv w ON v.a = w.a");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM (SELECT * FROM vv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "WITH c AS (SELECT * FROM vv) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT (SELECT COUNT(*) FROM vv)");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vv");
        assertAnswer(
            "create or replace view VV( | \tA | ) as SELECT a FROM vb;",
            "SELECT GET_DDL('VIEW', 'vv')");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "CREATE OR REPLACE VIEW vx AS SELECT * FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM test_schema.vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM test_db.test_schema.vv");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | Failure during expansion of view 'VV': SQL compilation error: | Object "
                + "'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "BEGIN SELECT * FROM vv; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "EXPLAIN SELECT * FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VQ': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VQT' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VQT.",
            "SELECT * FROM vq");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VQ3': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VQT' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VQT.",
            "SELECT * FROM vq3");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'v low': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VQT' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VQT.",
            "SELECT * FROM \"v low\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VJK': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VK' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VK.",
            "SELECT * FROM vjk");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VLOW': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.\"t low\"' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.\"t low\".",
            "SELECT * FROM vlow");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UG' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UG.",
            "SELECT f_ug(1)");
        assertAnswer(
            "1",
            "SELECT * FROM vs1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM t, vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM vv UNION ALL SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "SELECT * FROM t WHERE EXISTS (SELECT 1 FROM vv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "INSERT INTO t SELECT * FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VV': SQL compilation error: | "
                + "Object 'TEST_DB.TEST_SCHEMA.VB' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.VB.",
            "CREATE OR REPLACE TABLE t9 AS SELECT * FROM vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.UB.",
            "SELECT * FROM TABLE(f_ub()) WHERE a > 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for VV",
            "SHOW COLUMNS IN VIEW vv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'VC2': SQL compilation error: | "
                + "Failure during expansion of view 'VC1': SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.VC' "
                + "does not exist or not authorized. Your primary role <ROLE> must have at least one privilege "
                + "granted on TABLE TEST_DB.TEST_SCHEMA.VC.",
            "SELECT COUNT(*) FROM vc2");
    }
}
