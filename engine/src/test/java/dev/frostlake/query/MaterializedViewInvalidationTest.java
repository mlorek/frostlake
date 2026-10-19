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
 * A materialized view is marked invalid when its base table's shape is altered under it: a column renamed or
 * dropped, a column's type or nullability set — whether the view reads the column or not, and whether the
 * statement changes anything or not. An invalid view refuses every read under its own name, with the reason,
 * which SHOW MATERIALIZED VIEWS lists too, and each view that reads it adds its own name in front. A dropped or
 * replaced base table invalidates as well, and only UNDROP brings the view back; a renamed one is simply
 * missing until it has its name again. An added column, a comment, a clustering key, a constraint, a retention
 * time, search optimization and every write leave the view valid, and it goes on answering the columns it was
 * created with. SUSPEND marks a view invalid by hand and RESUME lifts that mark alone. Every answer is
 * live-verified.
 */
public class MaterializedViewInvalidationTest extends MeasuredCellsTestSupport {

    @Test
    public void anAddedColumnLeavesTheViewAsItWasCreated() {
        engine.execute("CREATE OR REPLACE TABLE m1 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m1 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m1_mv AS SELECT a, c FROM m1");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m1_star AS SELECT * FROM m1");
        assertAnswer(
            "1|x",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m1 ADD COLUMN d INT");
        assertAnswer(
            "1|x",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "1|2|x",
            "SELECT * FROM m1_star");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM m1_mv");
        assertAnswer(
            "",
            "SELECT * FROM m1_mv WHERE 1 = 0");
        assertAnswer(
            "1",
            "SELECT a FROM m1_mv");
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'M1_MV'");
        assertAnswer(
            "false|null",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m1_mv RESUME");
        assertAnswer(
            "1|x",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "Materialized view M1_MV successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW m1_mv AS SELECT a, c FROM m1");
        assertAnswer(
            "1|x",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "B|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|VARCHAR(10)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE MATERIALIZED VIEW m1_star");
        assertAnswer(
            "View M1_OVER successfully created.",
            "CREATE OR REPLACE VIEW m1_over AS SELECT a FROM m1_star");
        assertAnswer(
            "1",
            "SELECT * FROM m1_over");
    }

    @Test
    public void aColumnRenamedDroppedOrRetypedInvalidates() {
        engine.execute("CREATE OR REPLACE TABLE m2 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m2 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m2_mv AS SELECT a, c FROM m2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m2 RENAME COLUMN b TO bb");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M2_MV': SQL compilation "
                + "error: Materialized View M2_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M2'. Marked Materialized View as invalid.",
            "SELECT * FROM m2_mv");
        engine.execute("CREATE OR REPLACE TABLE m3 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m3 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m3_mv AS SELECT a, c FROM m3");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m3 DROP COLUMN b");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M3_MV': SQL compilation "
                + "error: Materialized View M3_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M3'. Marked Materialized View as invalid.",
            "SELECT * FROM m3_mv");
        engine.execute("CREATE OR REPLACE TABLE m4 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m4 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m4_mv AS SELECT a, c FROM m4");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m4 DROP COLUMN c");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M4_MV': SQL compilation "
                + "error: Materialized View M4_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M4'. Marked Materialized View as invalid.",
            "SELECT * FROM m4_mv");
        engine.execute("CREATE OR REPLACE TABLE m5 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m5 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m5_mv AS SELECT a, c FROM m5");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m5 ALTER COLUMN c SET DATA TYPE VARCHAR(20)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M5_MV': SQL compilation "
                + "error: Materialized View M5_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M5'. Marked Materialized View as invalid.",
            "SELECT * FROM m5_mv");
        engine.execute("CREATE OR REPLACE TABLE m8 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m8 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m8_mv AS SELECT a, c FROM m8");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m8 SET COMMENT = 'c'");
        assertAnswer(
            "1|x",
            "SELECT * FROM m8_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m8 ALTER COLUMN a SET NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M8_MV': SQL compilation "
                + "error: Materialized View M8_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M8'. Marked Materialized View as invalid.",
            "SELECT * FROM m8_mv");
    }

    @Test
    public void aStatementThatChangesNothingInvalidatesToo() {
        engine.execute("CREATE OR REPLACE TABLE m5 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m5 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m5_mv AS SELECT a, c FROM m5");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m5 ALTER COLUMN b SET NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M5_MV': SQL compilation "
                + "error: Materialized View M5_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M5'. Marked Materialized View as invalid.",
            "SELECT * FROM m5_mv");
        engine.execute("CREATE OR REPLACE TABLE m7 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m7 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m7_mv AS SELECT a, c FROM m7");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m7 ADD COLUMN d INT");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m7 DROP COLUMN d");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M7_MV': SQL compilation "
                + "error: Materialized View M7_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M7'. Marked Materialized View as invalid.",
            "SELECT * FROM m7_mv");
        engine.execute("CREATE OR REPLACE TABLE m10 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m10 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m10_mv AS SELECT a, c FROM m10");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m10 ALTER COLUMN c SET DATA TYPE VARCHAR(10)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M10_MV': SQL compilation "
                + "error: Materialized View M10_MV is invalid. Invalidation reason: DDL Statement was executed "
                + "on the base table 'M10'. Marked Materialized View as invalid.",
            "SELECT * FROM m10_mv");
        engine.execute("CREATE OR REPLACE TABLE m11 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m11 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m11_mv AS SELECT a, c FROM m11");
        engine.execute("ALTER TABLE m11 ALTER COLUMN b DROP NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M11_MV': SQL compilation "
                + "error: Materialized View M11_MV is invalid. Invalidation reason: DDL Statement was executed "
                + "on the base table 'M11'. Marked Materialized View as invalid.",
            "SELECT * FROM m11_mv");
    }

    @Test
    public void anInvalidViewRefusesEveryRead() {
        engine.execute("CREATE OR REPLACE TABLE m1 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m1 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m1_mv AS SELECT a, c FROM m1");
        engine.execute("CREATE OR REPLACE VIEW m1_over AS SELECT a FROM m1_mv");
        engine.execute("CREATE OR REPLACE VIEW m1_top AS SELECT a FROM m1_over");
        engine.execute("ALTER TABLE m1 RENAME COLUMN b TO bb");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT COUNT(*) FROM m1_mv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT * FROM m1_mv WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT * FROM m1_mv LIMIT 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT 1 FROM m1_mv");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_OVER': SQL compilation "
                + "error: | Failure during expansion of view 'M1_MV': SQL compilation error: Materialized View "
                + "M1_MV is invalid. Invalidation reason: DDL Statement was executed on the base table 'M1'. "
                + "Marked Materialized View as invalid.",
            "SELECT * FROM m1_over");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_TOP': SQL compilation "
                + "error: | Failure during expansion of view 'M1_OVER': SQL compilation error: | Failure during "
                + "expansion of view 'M1_MV': SQL compilation error: Materialized View M1_MV is invalid. "
                + "Invalidation reason: DDL Statement was executed on the base table 'M1'. Marked Materialized "
                + "View as invalid.",
            "SELECT * FROM m1_top");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT (SELECT MAX(a) FROM m1_mv)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT t.a FROM m1 t JOIN m1_mv v ON v.a = t.a");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C|VARCHAR(10)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE MATERIALIZED VIEW m1_mv");
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'M1_MV'");
        assertAnswer(
            "true|DDL Statement was executed on the base table 'M1'. Marked Materialized View as invalid.|false|1",
            "SELECT \"invalid\", \"invalid_reason\", \"is_secure\", \"rows\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "CREATE OR REPLACE VIEW m1_new AS SELECT a FROM m1_mv");
        assertAnswer(
            "1",
            "INSERT INTO m1 VALUES (9, 9, 'z')");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m1 DROP COLUMN bb");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m1_mv SUSPEND");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': SQL compilation "
                + "error: Materialized View M1_MV is invalid. Invalidation reason: Marked Materialized View as "
                + "invalid manually.",
            "SELECT * FROM m1_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m1_mv RESUME");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M1_MV': DDL Statement was "
                + "executed on the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT * FROM m1_mv ORDER BY a");
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'M1_MV'");
        assertAnswer(
            "true|DDL Statement was executed on the base table 'M1'. Marked Materialized View as invalid.",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void aViewWhoseBodyNoLongerCompilesCannotResume() {
        engine.execute("CREATE OR REPLACE TABLE m2 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m2 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m2_mv AS SELECT a, c FROM m2");
        engine.execute("ALTER TABLE m2 DROP COLUMN c");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M2_MV': SQL compilation "
                + "error: Materialized View M2_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M2'. Marked Materialized View as invalid.",
            "SELECT * FROM m2_mv");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'C'",
            "ALTER MATERIALIZED VIEW m2_mv RESUME");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M2_MV': SQL compilation "
                + "error: Materialized View M2_MV is invalid. Invalidation reason: DDL Statement was executed on "
                + "the base table 'M2'. Marked Materialized View as invalid.",
            "SELECT * FROM m2_mv");
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'M2_MV'");
        assertAnswer(
            "true|DDL Statement was executed on the base table 'M2'. Marked Materialized View as invalid.",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Materialized view M2_MV successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW m2_mv AS SELECT a FROM m2");
        assertAnswer(
            "1",
            "SELECT * FROM m2_mv");
    }

    @Test
    public void aDroppedOrReplacedBaseTable() {
        engine.execute("CREATE OR REPLACE TABLE m6 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m6 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m6_mv AS SELECT a, c FROM m6");
        assertAnswer(
            "Table M6 successfully created.",
            "CREATE OR REPLACE TABLE m6 (a INT, b INT, c VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M6_MV': SQL compilation "
                + "error: Materialized View M6_MV is invalid. Invalidation reason: Base table 'M6' dropped. "
                + "Marked Materialized View as invalid.",
            "SELECT * FROM m6_mv");
        engine.execute("CREATE OR REPLACE TABLE m12 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m12 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m12_mv AS SELECT a, c FROM m12");
        assertAnswer(
            "M12 successfully dropped.",
            "DROP TABLE m12");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M12_MV': SQL compilation "
                + "error: Materialized View M12_MV is invalid. Invalidation reason: Base table 'M12' dropped. "
                + "Marked Materialized View as invalid.",
            "SELECT * FROM m12_mv");
        assertAnswer(
            "Table M12 successfully restored.",
            "UNDROP TABLE m12");
        assertAnswer(
            "1|x",
            "SELECT * FROM m12_mv");
        engine.execute("CREATE OR REPLACE TABLE m13 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m13 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m13_mv AS SELECT a, c FROM m13");
        assertAnswer(
            "M13 successfully dropped.",
            "DROP TABLE m13");
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'M13_MV'");
        assertAnswer(
            "true|Base table 'M13' dropped. Marked Materialized View as invalid.",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M13_MV': SQL compilation "
                + "error: | Object 'TEST_DB.TEST_SCHEMA.M13' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.M13.",
            "ALTER MATERIALIZED VIEW m13_mv RESUME");
        assertAnswer(
            "Table M13 successfully created.",
            "CREATE TABLE m13 (a INT, b INT, c VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M13_MV': SQL compilation "
                + "error: Materialized View M13_MV is invalid. Invalidation reason: Base table 'M13' dropped. "
                + "Marked Materialized View as invalid.",
            "SELECT * FROM m13_mv");
        assertAnswer(
            "M13_MV successfully dropped.",
            "DROP MATERIALIZED VIEW m13_mv");
    }

    @Test
    public void aRenamedBaseTableIsMissingUntilItHasItsNameAgain() {
        engine.execute("CREATE OR REPLACE TABLE m7 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m7 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m7_mv AS SELECT a, c FROM m7");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m7 RENAME TO m7_new");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M7_MV': SQL compilation "
                + "error: | Object 'TEST_DB.TEST_SCHEMA.M7' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.M7.",
            "SELECT * FROM m7_mv");
        engine.execute("CREATE OR REPLACE TABLE m8 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m8 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m8_mv AS SELECT a, c FROM m8");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m8 RENAME TO m8_new");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m8_new RENAME TO m8");
        assertAnswer(
            "1|x",
            "SELECT * FROM m8_mv");
    }

    @Test
    public void whatLeavesTheViewValid() {
        engine.execute("CREATE OR REPLACE TABLE m9 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m9 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m9_mv AS SELECT a, c FROM m9");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m9 CLUSTER BY (a)");
        assertAnswer(
            "1|x",
            "SELECT * FROM m9_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m9 ALTER COLUMN a COMMENT 'k'");
        assertAnswer(
            "1|x",
            "SELECT * FROM m9_mv");
        engine.execute("CREATE OR REPLACE TABLE m11 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m11 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m11_mv AS SELECT a, c FROM m11");
        assertAnswer(
            "1",
            "INSERT INTO m11 VALUES (5, 6, 'y')");
        assertAnswer(
            "1|x ;; 5|y",
            "SELECT * FROM m11_mv ORDER BY a");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE m11");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM m11_mv");
        engine.execute("CREATE OR REPLACE TABLE m4 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m4 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m4_mv AS SELECT a, c FROM m4");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m4 ADD CONSTRAINT m4_pk PRIMARY KEY (a)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m4 DROP CONSTRAINT m4_pk");
        assertAnswer(
            "1|x",
            "SELECT * FROM m4_mv");
        engine.execute("CREATE OR REPLACE TABLE m6 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m6 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m6_mv AS SELECT a, c FROM m6");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m6 UNSET COMMENT");
        assertAnswer(
            "1|x",
            "SELECT * FROM m6_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m6 SET DATA_RETENTION_TIME_IN_DAYS = 0");
        assertAnswer(
            "1|x",
            "SELECT * FROM m6_mv");
        engine.execute("CREATE OR REPLACE TABLE m12 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m12 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m12_mv AS SELECT a, c FROM m12");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m12_mv SET COMMENT = 'c'");
        assertAnswer(
            "1|x",
            "SELECT * FROM m12_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m12_mv RENAME TO m12_mv2");
        assertAnswer(
            "1|x",
            "SELECT * FROM m12_mv2");
        engine.execute("CREATE OR REPLACE TABLE m14 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m14 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m14_mv AS SELECT a, c FROM m14");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m14 ADD SEARCH OPTIMIZATION");
        assertAnswer(
            "1|x",
            "SELECT * FROM m14_mv");
    }

    @Test
    public void suspendedByHandAndResumed() {
        engine.execute("CREATE OR REPLACE TABLE m10 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m10 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m10_mv AS SELECT a, c FROM m10");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE m10 ADD CONSTRAINT m10_pk PRIMARY KEY (a)");
        assertAnswer(
            "1|x",
            "SELECT * FROM m10_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m10_mv SUSPEND");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M10_MV': SQL compilation "
                + "error: Materialized View M10_MV is invalid. Invalidation reason: Marked Materialized View as "
                + "invalid manually.",
            "SELECT * FROM m10_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m10_mv RESUME");
        assertAnswer(
            "1|x",
            "SELECT * FROM m10_mv");
        engine.execute("CREATE OR REPLACE TABLE m3 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m3 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m3_mv AS SELECT a, c FROM m3");
        engine.execute("ALTER MATERIALIZED VIEW m3_mv SUSPEND");
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'M3_MV'");
        assertAnswer(
            "true|Marked Materialized View as invalid manually.",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "1",
            "INSERT INTO m3 VALUES (5, 6, 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'M3_MV': SQL compilation "
                + "error: Materialized View M3_MV is invalid. Invalidation reason: Marked Materialized View as "
                + "invalid manually.",
            "SELECT * FROM m3_mv");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m3_mv SUSPEND");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m3_mv RESUME");
        assertAnswer(
            "1|x ;; 5|y",
            "SELECT * FROM m3_mv ORDER BY a");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW m3_mv RESUME");
    }
}
