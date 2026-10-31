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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A session variable is a DDL property's value: the COMMENT of every kind, a table's, a schema's and a
 * database's parameters, a task's options and SCHEDULE, a file format's options and a stage's format and copy
 * options each read it as the literal of what it holds, judged by the property: a COMMENT takes a text and leaves
 * no comment for a NULL or a Boolean, a switch takes none, a parameter a value of the type it is listed with,
 * and a file format's or a stage's option a text alone. A variable is no column's comment, no COMMENT ON's and no
 * sequence's START or INCREMENT, and a file format given a COMMENT alone is refused for its TYPE (all
 * live-verified).
 */
public class SessionVariablePropertyTest extends MeasuredCellsTestSupport {

    @Test
    public void commentsOfTables() {
        engine.execute("CREATE OR REPLACE TABLE cb (a INT)");
        engine.execute("SET x = 'zz'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '$z'.",
            "ALTER TABLE cb SET COMMENT = $x$z$x$");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = $x");
        assertAnswer("zz",
            "SELECT comment FROM test_db.information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name "
                + "= 'CB'");
        assertAnswer("Table CB2 successfully created.", "CREATE OR REPLACE TABLE cb2 (a INT) COMMENT = $x");
        assertAnswer("zz",
            "SELECT comment FROM test_db.information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name "
                + "= 'CB2'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | Session variable '$Y' does not "
                + "exist",
            "ALTER TABLE cb SET COMMENT = $y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '$x'.",
            "COMMENT ON TABLE cb IS $x");
    }

    @Test
    public void commentsOfTheKinds() {
        engine.execute("CREATE OR REPLACE TABLE cb (a INT)");
        engine.execute("SET x = 'zz'");
        engine.execute("CREATE OR REPLACE VIEW cv AS SELECT 1 AS a");
        assertAnswer("View CV2 successfully created.", "CREATE OR REPLACE VIEW cv2 COMMENT = $x AS SELECT 1 AS a");
        assertAnswer("Statement executed successfully.", "ALTER VIEW cv SET COMMENT = $x");
        assertAnswer("Schema CS successfully created.", "CREATE OR REPLACE SCHEMA cs COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER SCHEMA cs SET COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '$x'.",
            "ALTER TABLE cb ALTER COLUMN a COMMENT $x");
        assertAnswer("Sequence CQ successfully created.", "CREATE OR REPLACE SEQUENCE cq COMMENT = $x");
        assertAnswer("Task CT successfully created.", "CREATE OR REPLACE TASK ct COMMENT = $x AS SELECT 1");
        assertAnswer("Function CF successfully created.",
            "CREATE OR REPLACE FUNCTION cf() RETURNS INT COMMENT = $x AS '1'");
        assertAnswer("Stage area CG successfully created.", "CREATE OR REPLACE STAGE cg COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): type",
            "CREATE OR REPLACE FILE FORMAT cff COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '||'.",
            "ALTER TABLE cb SET COMMENT = $x || 'y'");
        assertAnswer("REFUSED SQL compilation error: | Table 'TEST_DB.CS.CB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.CS.CB.",
            "ALTER TABLE cb SET COMMENT = $X");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '$x'.",
            "ALTER TABLE cb SET COMMENT = 'a' $x");
        assertAnswer("Statement executed successfully.", "SET n = 5");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER TABLE cb SET COMMENT = $n");
        assertAnswer("null",
            "SELECT comment FROM test_db.information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name "
                + "= 'CB'");
        assertAnswer("REFUSED SQL compilation error: | Table 'TEST_DB.CS.CB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.CS.CB.",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = $n");
        assertAnswer("Table CB5 successfully created.",
            "CREATE OR REPLACE TABLE cb5 (a INT) DATA_RETENTION_TIME_IN_DAYS = $n");
    }

    @Test
    public void commentsOfAlteredKinds() {
        engine.execute("CREATE OR REPLACE TABLE cb (a INT)");
        engine.execute("SET x = 'zz'");
        engine.execute("SET n = 5");
        engine.execute("CREATE OR REPLACE TASK ct AS SELECT 1");
        engine.execute("CREATE OR REPLACE SEQUENCE cq");
        engine.execute("CREATE OR REPLACE STAGE cg");
        engine.execute("CREATE OR REPLACE FUNCTION cf() RETURNS INT AS '1'");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct SET COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER SEQUENCE cq SET COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER STAGE cg SET COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER FUNCTION cf() SET COMMENT = $x");
        assertAnswer("Stream CS successfully created.", "CREATE OR REPLACE STREAM cs ON TABLE cb COMMENT = $x");
        assertAnswer("Function CP successfully created.",
            "CREATE OR REPLACE PROCEDURE cp() RETURNS INT LANGUAGE SQL COMMENT = $x AS 'BEGIN RETURN 1; END'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | Session variable '$NOSUCH' does "
                + "not exist",
            "ALTER TABLE cb SET COMMENT = $nosuch");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE TABLE cb6 (a INT) COMMENT = $n");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37 | Session variable '$NOSUCH' does "
                + "not exist",
            "CREATE OR REPLACE VIEW cv3 COMMENT = $nosuch AS SELECT 1 AS a");
        assertAnswer("Table CB7 successfully created.", "CREATE OR REPLACE TABLE cb7 COMMENT = $x AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | duplicate property 'COMMENT';",
            "CREATE OR REPLACE TABLE cb8 (a INT) COMMENT = $x COMMENT = 'w'");
        assertAnswer("zz ;; zz",
            "SELECT comment FROM test_db.information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND table_name "
                + "IN ('CB', 'CB7') ORDER BY table_name");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE cb SET COMMENT = $x, DATA_RETENTION_TIME_IN_DAYS = $n");
        assertAnswer("Table CB9 successfully created.", "CREATE OR REPLACE TABLE cb9 (a INT) COMMENT = $X");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '$z'.",
            "ALTER TABLE cb SET COMMENT = $x$z$x$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '$'.",
            "ALTER TABLE cb SET COMMENT = $x$");
    }

    @Test
    public void placesAndValues() {
        engine.execute("SET x = 'zz'");
        engine.execute("SET n = 5");
        engine.execute("SET b = TRUE");
        engine.execute("SET f = 1.5");
        engine.execute("SET t = 'JSON'");
        engine.execute("SET s = '5 MINUTE'");
        engine.execute("SET y = NULL");
        engine.execute("CREATE OR REPLACE TABLE cb (a INT)");
        engine.execute("CREATE OR REPLACE VIEW cv AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'CHANGE_TRACKING'",
            "ALTER TABLE cb SET CHANGE_TRACKING = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid value [1.5] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = $f");
        assertAnswer("REFUSED SQL compilation error: | invalid value [zz] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE cb2 (a INT) CHANGE_TRACKING = $b");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = $y");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$f' for 'COMMENT'",
            "ALTER TABLE cb SET COMMENT = $f");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = $b");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): type",
            "CREATE OR REPLACE FILE FORMAT cff COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): type",
            "CREATE OR REPLACE FILE FORMAT cff COMMENT = $x");
        assertAnswer("File format CFF successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV COMMENT = $x");
        assertAnswer("File format CFF successfully created.", "CREATE OR REPLACE FILE FORMAT cff TYPE = $t");
        assertAnswer("Stage area ST successfully created.", "CREATE OR REPLACE STAGE st FILE_FORMAT = (TYPE = $t)");
        assertAnswer("Stream CS successfully created.", "CREATE OR REPLACE STREAM cs ON TABLE cb");
        assertAnswer("Statement executed successfully.", "ALTER STREAM cs SET COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER VIEW cv SET COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'CHANGE_TRACKING'",
            "CREATE OR REPLACE VIEW cv4 CHANGE_TRACKING = $b AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '$n'.",
            "CREATE OR REPLACE SEQUENCE cq START = $n");
        assertAnswer("Sequence CQ2 successfully created.", "CREATE OR REPLACE SEQUENCE cq2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '$n'.",
            "ALTER SEQUENCE cq2 SET INCREMENT = $n");
        assertAnswer("Task CT successfully created.", "CREATE OR REPLACE TASK ct SCHEDULE = $s AS SELECT 1");
        assertAnswer("Table CB3 successfully created.",
            "CREATE OR REPLACE TABLE cb3 (a INT) COMMENT = $x DATA_RETENTION_TIME_IN_DAYS = $n");
        assertAnswer("zz|5", "SELECT comment, retention_time FROM information_schema.tables WHERE table_name = 'CB3'");
        assertAnswer("Statement executed successfully.", "ALTER STAGE st SET COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'COMMENT'.",
            "CREATE OR REPLACE FUNCTION cf2() RETURNS INT AS '1' COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'CHANGE_TRACKING'",
            "ALTER TABLE cb SET COMMENT = $x, CHANGE_TRACKING = $b");
        assertAnswer("Schema CS2 successfully created.",
            "CREATE OR REPLACE SCHEMA cs2 DATA_RETENTION_TIME_IN_DAYS = $n");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
    }

    @Test
    public void valueTypesOfEachFamily() {
        engine.execute("SET x = 'zz'");
        engine.execute("SET n = 5");
        engine.execute("SET n1 = 1");
        engine.execute("SET n2 = 60000");
        engine.execute("SET b = TRUE");
        engine.execute("SET xt = 'TRUE'");
        engine.execute("SET y = NULL");
        engine.execute("SET t = 'JSON'");
        engine.execute("SET s = '5 MINUTE'");
        engine.execute("SET s2 = '10 MINUTE'");
        engine.execute("SET d = '|'");
        engine.execute("SET c = 'en-ci'");
        engine.execute("SET fn = 'cff'");
        engine.execute("SET oe = 'CONTINUE'");
        engine.execute("SET tz = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE cb (a INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['zz'] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = 'zz'");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$xt' for 'CHANGE_TRACKING'",
            "ALTER TABLE cb SET CHANGE_TRACKING = $xt");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n1' for 'CHANGE_TRACKING'",
            "ALTER TABLE cb SET CHANGE_TRACKING = $n1");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['TRUE'] for parameter 'CHANGE_TRACKING'",
            "ALTER TABLE cb SET CHANGE_TRACKING = 'TRUE'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 37 | Session variable '$NOSUCH' does "
                + "not exist",
            "ALTER TABLE cb SET CHANGE_TRACKING = $nosuch");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = 'w'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = $y");
        assertAnswer("true|null",
            "SELECT comment IS NULL, comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = $b");
        assertAnswer("null", "SELECT comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer("File format CFF successfully created.", "CREATE OR REPLACE FILE FORMAT cff");
        assertAnswer("File format CFF successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff FIELD_DELIMITER = ','");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): type",
            "CREATE FILE FORMAT IF NOT EXISTS cff2 COMMENT = 'x'");
        assertAnswer("File format CFF successfully created.", "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV");
        assertAnswer("Statement executed successfully.", "ALTER FILE FORMAT cff SET COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER FILE FORMAT cff SET FIELD_DELIMITER = $d");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$n] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE FILE FORMAT cff3 TYPE = CSV SKIP_HEADER = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$n] for parameter 'TYPE'",
            "CREATE OR REPLACE FILE FORMAT cff4 TYPE = $n");
        assertAnswer("File format CFF5 successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff5 TYPE = $t COMMENT = $x");
        engine.execute("SHOW FILE FORMATS LIKE 'CFF%'");
        assertAnswer("CFF|CSV|zz ;; CFF5|JSON|zz",
            "SELECT \"name\", \"type\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("Stage area ST successfully created.", "CREATE OR REPLACE STAGE st FILE_FORMAT = (TYPE = $t)");
        engine.execute("DESCRIBE STAGE st");
        assertAnswer("JSON",
            "SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"parent_property\" = "
                + "'STAGE_FILE_FORMAT' AND \"property\" = 'TYPE'");
        assertAnswer("Stage area ST2 successfully created.",
            "CREATE OR REPLACE STAGE st2 FILE_FORMAT = (FORMAT_NAME = $fn)");
        assertAnswer("Stage area ST3 successfully created.",
            "CREATE OR REPLACE STAGE st3 FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = $d)");
        assertAnswer("Stage area ST4 successfully created.",
            "CREATE OR REPLACE STAGE st4 COPY_OPTIONS = (ON_ERROR = $oe)");
        assertAnswer("Stage area ST5 successfully created.", "CREATE OR REPLACE STAGE st5 FILE_FORMAT = $fn");
        assertAnswer("Statement executed successfully.", "ALTER STAGE st SET FILE_FORMAT = (TYPE = $t)");
        assertAnswer("Task CT successfully created.", "CREATE OR REPLACE TASK ct SCHEDULE = $s AS SELECT 1");
        engine.execute("DESCRIBE TASK ct");
        assertAnswer("5 MINUTE", "SELECT \"schedule\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct SET SCHEDULE = $s2");
        engine.execute("DESCRIBE TASK ct");
        assertAnswer("10 MINUTE", "SELECT \"schedule\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'SCHEDULE'",
            "CREATE OR REPLACE TASK ct2 SCHEDULE = $n AS SELECT 1");
        assertAnswer("Task CT3 successfully created.",
            "CREATE OR REPLACE TASK ct3 SCHEDULE = '5 MINUTE' USER_TASK_TIMEOUT_MS = $n2 AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'ALLOW_OVERLAPPING_EXECUTION'",
            "CREATE OR REPLACE TASK ct4 SCHEDULE = '5 MINUTE' ALLOW_OVERLAPPING_EXECUTION = $b AS SELECT 1");
        assertAnswer("Task CT5 successfully created.",
            "CREATE OR REPLACE TASK ct5 SCHEDULE = '5 MINUTE' TIMEZONE = $tz AS SELECT 1");
        assertAnswer("Table CB4 successfully created.",
            "CREATE OR REPLACE TABLE cb4 (a INT) MAX_DATA_EXTENSION_TIME_IN_DAYS = $n");
        assertAnswer("Table CB5 successfully created.",
            "CREATE OR REPLACE TABLE cb5 (a VARCHAR) DEFAULT_DDL_COLLATION = $c");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET DEFAULT_DDL_COLLATION = $c");
        assertAnswer("Tag TG successfully created.", "CREATE OR REPLACE TAG tg COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER TAG tg SET COMMENT = $x");
        assertAnswer("Masking policy MP successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp AS (v STRING) RETURNS STRING -> v COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER MASKING POLICY mp SET COMMENT = $x");
        assertAnswer("Row access policy 'RP' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY rp AS (v INT) RETURNS BOOLEAN -> TRUE COMMENT = $x");
        assertAnswer("Table ET successfully created.", "CREATE OR REPLACE EVENT TABLE et COMMENT = $x");
        assertAnswer("Schema CS2 successfully created.",
            "CREATE OR REPLACE SCHEMA cs2 DATA_RETENTION_TIME_IN_DAYS = $n");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
        engine.execute("SHOW SCHEMAS LIKE 'CS2' IN DATABASE test_db");
        assertAnswer("5", "SELECT \"retention_time\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Statement executed successfully.",
            "ALTER SCHEMA test_db.cs2 SET DATA_RETENTION_TIME_IN_DAYS = $n1");
        assertAnswer("Schema CS3 successfully created.",
            "CREATE OR REPLACE SCHEMA cs3 MAX_DATA_EXTENSION_TIME_IN_DAYS = $n");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
        assertAnswer("Schema CS4 successfully created.", "CREATE OR REPLACE SCHEMA cs4 DEFAULT_DDL_COLLATION = $c");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
    }

    @Test
    public void nullsSwitchesAndParameters() {
        engine.execute("SET x = 'zz'");
        engine.execute("SET n = 5");
        engine.execute("SET n1 = 1");
        engine.execute("SET n2 = 60000");
        engine.execute("SET b = TRUE");
        engine.execute("SET xt = 'TRUE'");
        engine.execute("SET y = NULL");
        engine.execute("SET nn = '5'");
        engine.execute("SET oe = 'CONTINUE'");
        engine.execute("SET cfg = '{\"a\":1}'");
        engine.execute("CREATE OR REPLACE TABLE cb (a INT) COMMENT = 'w'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb SET COMMENT = ''");
        assertAnswer("false|",
            "SELECT comment IS NULL, comment FROM information_schema.tables WHERE table_name = 'CB'");
        assertAnswer("Table CB2 successfully created.", "CREATE OR REPLACE TABLE cb2 (a INT) COMMENT = $y");
        assertAnswer("true", "SELECT comment IS NULL FROM information_schema.tables WHERE table_name = 'CB2'");
        assertAnswer("Table CB3 successfully created.", "CREATE OR REPLACE TABLE cb3 (a INT) COMMENT = $b");
        assertAnswer("true", "SELECT comment IS NULL FROM information_schema.tables WHERE table_name = 'CB3'");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$xt' for 'ENABLE_SCHEMA_EVOLUTION'",
            "ALTER TABLE cb SET ENABLE_SCHEMA_EVOLUTION = $xt");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$xt' for 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE cb4 (a INT) CHANGE_TRACKING = $xt");
        assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = $b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type NULL for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = $y");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE cb SET DATA_RETENTION_TIME_IN_DAYS = $nn");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'DEFAULT_DDL_COLLATION'",
            "ALTER TABLE cb SET DEFAULT_DDL_COLLATION = $n");
        assertAnswer("Statement executed successfully.", "ALTER DATABASE test_db SET COMMENT = $x");
        assertAnswer("Statement executed successfully.",
            "ALTER DATABASE test_db SET DATA_RETENTION_TIME_IN_DAYS = $n1");
        assertAnswer("Statement executed successfully.", "ALTER DATABASE test_db UNSET COMMENT");
        assertAnswer("Statement executed successfully.", "ALTER DATABASE test_db UNSET DATA_RETENTION_TIME_IN_DAYS");
        assertAnswer("Task CT successfully created.",
            "CREATE OR REPLACE TASK ct SCHEDULE = '5 MINUTE' COMMENT = $b AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value [zz] for parameter 'USER_TASK_TIMEOUT_MS'",
            "CREATE OR REPLACE TASK ct2 SCHEDULE = '5 MINUTE' USER_TASK_TIMEOUT_MS = $x AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$xt' for "
                + "'ALLOW_OVERLAPPING_EXECUTION'",
            "CREATE OR REPLACE TASK ct3 SCHEDULE = '5 MINUTE' ALLOW_OVERLAPPING_EXECUTION = $xt AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n1' for "
                + "'ALLOW_OVERLAPPING_EXECUTION'",
            "CREATE OR REPLACE TASK ct4 SCHEDULE = '5 MINUTE' ALLOW_OVERLAPPING_EXECUTION = $n1 AS SELECT 1");
        assertAnswer("Task CT5 successfully created.",
            "CREATE OR REPLACE TASK ct5 SCHEDULE = '5 MINUTE' SUSPEND_TASK_AFTER_NUM_FAILURES = $n AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'TIMEZONE'",
            "CREATE OR REPLACE TASK ct6 SCHEDULE = '5 MINUTE' TIMEZONE = $n AS SELECT 1");
        assertAnswer("Task CT7 successfully created.",
            "CREATE OR REPLACE TASK ct7 SCHEDULE = '5 MINUTE' CONFIG = $cfg AS SELECT 1");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct SET COMMENT = $x");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct SET USER_TASK_TIMEOUT_MS = $n2");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'null' for property 'ALLOW_OVERLAPPING_EXECUTION'",
            "ALTER TASK ct SET ALLOW_OVERLAPPING_EXECUTION = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'SCHEDULE'",
            "ALTER TASK ct SET SCHEDULE = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$nn] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV SKIP_HEADER = $nn");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$b] for parameter "
                + "'ERROR_ON_COLUMN_COUNT_MISMATCH'",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV ERROR_ON_COLUMN_COUNT_MISMATCH = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$xt] for parameter "
                + "'ERROR_ON_COLUMN_COUNT_MISMATCH'",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV ERROR_ON_COLUMN_COUNT_MISMATCH = $xt");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$x' for property 'NULL_IF'",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV NULL_IF = ($x)");
        assertAnswer("File format CFF successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV COMMENT = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV COMMENT = $n");
        assertAnswer("File format CFF6 successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff6 COMMENT = 'x' FIELD_DELIMITER = ','");
        assertAnswer("File format CFF7 successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff7 FIELD_DELIMITER = ',' COMMENT = 'x'");
        assertAnswer("File format CFF8 successfully created.",
            "CREATE OR REPLACE FILE FORMAT cff8 COMMENT = $x TYPE = CSV");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$y] for parameter 'FIELD_DELIMITER'",
            "CREATE OR REPLACE FILE FORMAT cff9 TYPE = CSV FIELD_DELIMITER = $y");
        assertAnswer("File format CFF successfully created.", "CREATE OR REPLACE FILE FORMAT cff TYPE = CSV");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$n] for parameter 'SKIP_HEADER'",
            "ALTER FILE FORMAT cff SET SKIP_HEADER = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER FILE FORMAT cff SET COMMENT = $n");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$x] for parameter 'TYPE'",
            "ALTER FILE FORMAT cff SET TYPE = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$n] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE STAGE st FILE_FORMAT = (TYPE = CSV SKIP_HEADER = $n)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$n] for parameter 'SIZE_LIMIT'",
            "CREATE OR REPLACE STAGE st2 COPY_OPTIONS = (SIZE_LIMIT = $n)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$b] for parameter 'PURGE'",
            "CREATE OR REPLACE STAGE st3 COPY_OPTIONS = (PURGE = $b)");
        assertAnswer("Stage area ST4 successfully created.", "CREATE OR REPLACE STAGE st4 COMMENT = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "CREATE OR REPLACE STAGE st5 COMMENT = $n");
        assertAnswer("REFUSED SQL compilation error: | File format '\"5\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"5\".",
            "CREATE OR REPLACE STAGE st6 FILE_FORMAT = $n");
        assertAnswer("Stage area ST7 successfully created.", "CREATE OR REPLACE STAGE st7");
        assertAnswer("Statement executed successfully.", "ALTER STAGE st7 SET COPY_OPTIONS = (ON_ERROR = $oe)");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER STAGE st7 SET COMMENT = $n");
        assertAnswer("Sequence SQ successfully created.", "CREATE OR REPLACE SEQUENCE sq COMMENT = $b");
        assertAnswer("View CV successfully created.", "CREATE OR REPLACE VIEW cv COMMENT = $y AS SELECT 1 AS a");
        assertAnswer("Statement executed successfully.", "ALTER VIEW cv SET COMMENT = $b");
        assertAnswer("Masking policy MP successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp AS (v STRING) RETURNS STRING -> v");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$n' for 'COMMENT'",
            "ALTER MASKING POLICY mp SET COMMENT = $n");
        assertAnswer("Statement executed successfully.", "ALTER MASKING POLICY mp SET COMMENT = $y");
        assertAnswer("Schema CS5 successfully created.", "CREATE OR REPLACE SCHEMA cs5 COMMENT = $b");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
    }

    /**
     * A Boolean variable for ENABLE_SCHEMA_EVOLUTION fails on the account with an internal error of its own, which is
     * not mirrored: the engine refuses it as CHANGE_TRACKING refuses one. Engine-only, so as to raise no incident on
     * the account.
     */
    @Test
    public void aSchemaEvolutionSwitchTakesNoBooleanVariable() {
        assumeFalse(isLiveSnowflake());
        engine.execute("SET b = TRUE");
        engine.execute("CREATE OR REPLACE TABLE cb (a INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'ENABLE_SCHEMA_EVOLUTION'",
            "ALTER TABLE cb SET ENABLE_SCHEMA_EVOLUTION = $b");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'ENABLE_SCHEMA_EVOLUTION'",
            "CREATE OR REPLACE TABLE cb4 (a INT) ENABLE_SCHEMA_EVOLUTION = $b");
    }
}
