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

/**
 * A session variable as a DDL property's value, in the places and of the types a property judges: a column's
 * COMMENT refused at what follows it, a table's STAGE_FILE_FORMAT and STAGE_COPY_OPTIONS, a stage's encryption
 * TYPE, DIRECTORY and FILE_FORMAT, a task's SCHEDULE that a NULL or a Boolean leaves unset, a parameter refusing a
 * date, a time, a timestamp, a binary value or a DECFLOAT, a missing parameter's variable placed nowhere, and a
 * password or a session policy's COMMENT and other properties (all live-verified).
 */
public class PropertyVariablePlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void variablePlaces() {
        engine.execute("SET x = 'hello'");
        engine.execute("SET b = TRUE");
        engine.execute("SET nul = NULL");
        engine.execute("SET d = TO_DATE('2024-01-01')");
        engine.execute("SET f = 'CSV'");
        engine.execute("SET five = 5");
        engine.execute("CREATE OR REPLACE TABLE cb677 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '$x'.",
            "CREATE OR REPLACE TABLE cb1 (a INT COMMENT $x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '='.",
            "CREATE OR REPLACE TABLE cb2 (a INT COMMENT = $x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '='.",
            "CREATE OR REPLACE TABLE cb3 (a INT COMMENT = 'c')");
        assertAnswer("Table CB4 successfully created.", "CREATE OR REPLACE TABLE cb4 (a INT COMMENT 'c')");
        assertAnswer("Table CB5 successfully created.",
            "CREATE OR REPLACE TABLE cb5 (a INT) STAGE_FILE_FORMAT = (TYPE = $f)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$x] for parameter 'ON_ERROR'",
            "CREATE OR REPLACE TABLE cb6 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = $x)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type DATE for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cb7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $d");
        assertAnswer("REFUSED SQL compilation error: | invalid value '$b' for property 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE cb8 (a INT) CHANGE_TRACKING = $b");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type NULL for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cb9 (a INT) DATA_RETENTION_TIME_IN_DAYS = $nul");
        assertAnswer("Table CB10 successfully created.",
            "CREATE OR REPLACE TABLE cb10 (a INT) DATA_RETENTION_TIME_IN_DAYS = $five");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$b] for parameter 'ENABLE'",
            "CREATE OR REPLACE STAGE st677a DIRECTORY = (ENABLE = $b)");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter '$nul'",
            "CREATE OR REPLACE STAGE st677b FILE_FORMAT = $nul");
        assertAnswer("REFUSED SQL compilation error: | invalid value [hello] for parameter 'TYPE'",
            "CREATE OR REPLACE STAGE st677c ENCRYPTION = (TYPE = $x)");
        assertAnswer("Task TK677A successfully created.", "CREATE OR REPLACE TASK tk677a SCHEDULE = $nul AS SELECT 1");
        assertAnswer("Task TK677B successfully created.", "CREATE OR REPLACE TASK tk677b SCHEDULE = $b AS SELECT 1");
        assertAnswer("1", "EXECUTE IMMEDIATE $$ BEGIN ALTER TABLE cb677 SET COMMENT = $x; RETURN 1; END $$");
        assertAnswer("hello", "SELECT COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'CB677'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb677 SET DEFAULT_DDL_COLLATION = $x");
    }

    @Test
    public void stageAndTableOptions() {
        engine.execute("SET x = 'hello'");
        engine.execute("SET b = TRUE");
        engine.execute("SET nul = NULL");
        engine.execute("SET d = TO_DATE('2024-01-01')");
        engine.execute("SET f = 'CSV'");
        engine.execute("SET five = 5");
        engine.execute("SET oe = 'CONTINUE'");
        engine.execute("SET sse = 'SNOWFLAKE_SSE'");
        engine.execute("SET t = TO_TIME('10:00:00')");
        engine.execute("SET ts = TO_TIMESTAMP_NTZ('2024-01-01 10:00:00')");
        engine.execute("SET tl = TO_TIMESTAMP_LTZ('2024-01-01 10:00:00')");
        engine.execute("SET df = 5::DECFLOAT");
        engine.execute("CREATE OR REPLACE TABLE cb678 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st678z");
        engine.execute("CREATE OR REPLACE TASK tk678c SCHEDULE = '5 MINUTE' AS SELECT 1");
        assertAnswer("Table CC1 successfully created.",
            "CREATE OR REPLACE TABLE cc1 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = $oe)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$five] for parameter 'ON_ERROR'",
            "CREATE OR REPLACE TABLE cc2 (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = $five)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$b] for parameter 'PURGE'",
            "CREATE OR REPLACE TABLE cc3 (a INT) STAGE_COPY_OPTIONS = (PURGE = $b)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$x] for parameter 'TYPE'",
            "CREATE OR REPLACE TABLE cc4 (a INT) STAGE_FILE_FORMAT = (TYPE = $x)");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$five] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE TABLE cc5 (a INT) STAGE_FILE_FORMAT = (SKIP_HEADER = $five)");
        assertAnswer("Table CC6 successfully created.",
            "CREATE OR REPLACE TABLE cc6 (a INT) STAGE_FILE_FORMAT = (TYPE = $f FIELD_DELIMITER = $x) "
                + "STAGE_COPY_OPTIONS = (ON_ERROR = $oe)");
        assertAnswer("STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|RECORD_DELIMITER|String|\\n|\\n ;; "
                + "STAGE_FILE_FORMAT|FIELD_DELIMITER|String|hello|, ;; STAGE_FILE_FORMAT|FILE_EXTENSION|String|| ;; "
                + "STAGE_FILE_FORMAT|SKIP_HEADER|Integer|0|0 ;; STAGE_FILE_FORMAT|PARSE_HEADER|Boolean|false|false ;; "
                + "STAGE_FILE_FORMAT|DATE_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|TIME_FORMAT|String|AUTO|AUTO "
                + ";; STAGE_FILE_FORMAT|TIMESTAMP_FORMAT|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|BINARY_FORMAT|String|"
                + "HEX|HEX ;; STAGE_FILE_FORMAT|ESCAPE|String|NONE|NONE ;; STAGE_FILE_FORMAT|ESCAPE_UNENCLOSED_FIELD|"
                + "String|\\\\|\\\\ ;; STAGE_FILE_FORMAT|TRIM_SPACE|Boolean|false|false ;; STAGE_FILE_FORMAT|"
                + "FIELD_OPTIONALLY_ENCLOSED_BY|String|NONE|NONE ;; STAGE_FILE_FORMAT|NULL_IF|List|[\\\\N]|[\\\\N] ;; "
                + "STAGE_FILE_FORMAT|COMPRESSION|String|AUTO|AUTO ;; STAGE_FILE_FORMAT|ERROR_ON_COLUMN_COUNT_MISMATCH|"
                + "Boolean|true|true ;; STAGE_FILE_FORMAT|VALIDATE_UTF8|Boolean|true|true ;; STAGE_FILE_FORMAT|"
                + "SKIP_BLANK_LINES|Boolean|false|false ;; STAGE_FILE_FORMAT|REPLACE_INVALID_CHARACTERS|Boolean|false|"
                + "false ;; STAGE_FILE_FORMAT|EMPTY_FIELD_AS_NULL|Boolean|true|true ;; STAGE_FILE_FORMAT|"
                + "SKIP_BYTE_ORDER_MARK|Boolean|true|true ;; STAGE_FILE_FORMAT|ENCODING|String|UTF8|UTF8 ;; "
                + "STAGE_FILE_FORMAT|MULTI_LINE|Boolean|true|true ;; STAGE_COPY_OPTIONS|ON_ERROR|String|CONTINUE|"
                + "ABORT_STATEMENT ;; STAGE_COPY_OPTIONS|SIZE_LIMIT|Long|| ;; STAGE_COPY_OPTIONS|PURGE|Boolean|false|"
                + "false ;; STAGE_COPY_OPTIONS|RETURN_FAILED_ONLY|Boolean|false|false ;; STAGE_COPY_OPTIONS|"
                + "ENFORCE_LENGTH|Boolean|true|true ;; STAGE_COPY_OPTIONS|TRUNCATECOLUMNS|Boolean|false|false ;; "
                + "STAGE_COPY_OPTIONS|FORCE|Boolean|false|false ;; STAGE_LOCATION|URL|String||",
            "DESC TABLE cc6 TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS|ON_ERROR|CONTINUE ;; STAGE_FILE_FORMAT|FIELD_DELIMITER|hello ;; "
                + "STAGE_FILE_FORMAT|TYPE|CSV",
            "SELECT \"parent_property\", \"property\", \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "WHERE \"property\" IN ('TYPE', 'FIELD_DELIMITER', 'ON_ERROR') ORDER BY 1, 2");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb678 SET STAGE_COPY_OPTIONS = (ON_ERROR = $oe)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE cb678 SET STAGE_FILE_FORMAT = (TYPE = $f)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$x] for parameter 'ON_ERROR'",
            "ALTER TABLE cb678 SET STAGE_COPY_OPTIONS = (ON_ERROR = $x)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$x] for parameter 'ON_ERROR'",
            "CREATE OR REPLACE STAGE st678a COPY_OPTIONS = (ON_ERROR = $x)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$x] for parameter 'TYPE'",
            "CREATE OR REPLACE STAGE st678b FILE_FORMAT = (TYPE = $x)");
        assertAnswer("Stage area ST678C successfully created.",
            "CREATE OR REPLACE STAGE st678c ENCRYPTION = (TYPE = $sse)");
        assertAnswer("REFUSED SQL compilation error: | File format 'TRUE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT TRUE.",
            "CREATE OR REPLACE STAGE st678f FILE_FORMAT = $b");
        assertAnswer("REFUSED SQL compilation error: | File format '\"5\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"5\".",
            "CREATE OR REPLACE STAGE st678g FILE_FORMAT = $five");
        assertAnswer("REFUSED SQL compilation error: | File format 'HELLO' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT HELLO.",
            "CREATE OR REPLACE STAGE st678h FILE_FORMAT = (FORMAT_NAME = $x)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$b] for parameter 'ENABLE'",
            "ALTER STAGE st678z SET DIRECTORY = (ENABLE = $b)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cc7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_NTZ(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cc7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $ts");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_LTZ(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cc7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $tl");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "CREATE OR REPLACE TABLE cc7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $df");
        assertAnswer("REFUSED Unsupported feature 'assignment from non-constant source expression'.",
            "SET v = PARSE_JSON('5')");
        assertAnswer("REFUSED SQL compilation error: error line -1 at position -1 | Session variable '$V' does not "
                + "exist",
            "CREATE OR REPLACE TABLE cc7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $v");
        assertAnswer("REFUSED SQL compilation error: | invalid value [hello] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'",
            "CREATE OR REPLACE TABLE cc7 (a INT) DATA_RETENTION_TIME_IN_DAYS = $x");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type DATE for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "ALTER TABLE cb678 SET DATA_RETENTION_TIME_IN_DAYS = $d");
        assertAnswer("", "SELECT COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'CC8'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [$d] for parameter 'CHANGE_TRACKING'",
            "CREATE OR REPLACE TABLE cc9 (a INT) CHANGE_TRACKING = $d");
        assertAnswer("REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE TASK tk678a SCHEDULE = $d AS SELECT 1");
        assertAnswer("REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "CREATE OR REPLACE TASK tk678b SCHEDULE = $x AS SELECT 1");
        assertAnswer("Statement executed successfully.", "ALTER TASK tk678c SET SCHEDULE = $nul");
        assertAnswer("Statement executed successfully.", "ALTER TASK tk678c SET SCHEDULE = $b");
        assertAnswer("REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule.",
            "ALTER TASK tk678c SET SCHEDULE = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$d] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE FILE FORMAT ff678a TYPE = CSV SKIP_HEADER = $d");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$d] for parameter 'FIELD_DELIMITER'",
            "CREATE OR REPLACE FILE FORMAT ff678b TYPE = CSV FIELD_DELIMITER = $d");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$five' for 'PASSWORD_MIN_LENGTH'",
            "CREATE OR REPLACE PASSWORD POLICY pp678a PASSWORD_MIN_LENGTH = $five");
        assertAnswer("Password policy 'PP678B' created successfully",
            "CREATE OR REPLACE PASSWORD POLICY pp678b COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$five' for "
                + "'SESSION_IDLE_TIMEOUT_MINS'",
            "CREATE OR REPLACE SESSION POLICY sp678 SESSION_IDLE_TIMEOUT_MINS = $five");
    }

    @Test
    public void typesAndPolicies() {
        engine.execute("SET x = 'hello'");
        engine.execute("SET b = TRUE");
        engine.execute("SET nul = NULL");
        engine.execute("SET five = 5");
        engine.execute("SET d = TO_DATE('2024-01-01')");
        engine.execute("SET tz = TO_TIMESTAMP_TZ('2024-01-01 10:00:00 +02:00')");
        engine.execute("SET t3 = '10:00:00'::TIME(3)");
        engine.execute("SET bin = TO_BINARY('41', 'HEX')");
        engine.execute("SET df = 5::DECFLOAT");
        engine.execute("CREATE OR REPLACE TABLE cb679 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [hello] for parameter 'TYPE'",
            "CREATE OR REPLACE STAGE st679a ENCRYPTION = (TYPE = 'hello')");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'TYPE'",
            "CREATE OR REPLACE STAGE st679a ENCRYPTION = (TYPE = $five)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_TZ(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cc679 (a INT) DATA_RETENTION_TIME_IN_DAYS = $tz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type BINARY(67108864) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE cc679 (a INT) DATA_RETENTION_TIME_IN_DAYS = $bin");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type DATE for parameter DEFAULT_DDL_COLLATION "
                + "specified with session variable",
            "ALTER TABLE cb679 SET DEFAULT_DDL_COLLATION = $d");
        assertAnswer("REFUSED SQL compilation error: | File format '\"2024-01-01\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"2024-01-01\".",
            "CREATE OR REPLACE STAGE st679b FILE_FORMAT = $d");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$df' for 'COMMENT'",
            "CREATE OR REPLACE TABLE cc679 (a INT) COMMENT = $df");
        assertAnswer("REFUSED SQL compilation error: | invalid type [$df] for parameter 'SKIP_HEADER'",
            "CREATE OR REPLACE FILE FORMAT ff679 TYPE = CSV SKIP_HEADER = $df");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 48 | Session variable '$MISSING' does "
                + "not exist",
            "CREATE OR REPLACE TABLE cc679 (a INT) COMMENT = $missing");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Session variable '$MISSING' does "
                + "not exist",
            "ALTER TABLE cb679 SET COMMENT = $missing");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 58 | Session variable '$MISSING' does "
                + "not exist",
            "CREATE OR REPLACE STAGE st679c COPY_OPTIONS = (ON_ERROR = $missing)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 40 | Session variable '$MISSING' does "
                + "not exist",
            "CREATE OR REPLACE TASK tk679 SCHEDULE = $missing AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: error line -1 at position -1 | Session variable '$MISSING' does "
                + "not exist",
            "CREATE OR REPLACE TABLE cc679 (a INT) DATA_RETENTION_TIME_IN_DAYS = $missing");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$x' for 'PASSWORD_MIN_LENGTH'",
            "CREATE OR REPLACE PASSWORD POLICY pp679 PASSWORD_MIN_LENGTH = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$five' for 'COMMENT'",
            "CREATE OR REPLACE PASSWORD POLICY pp679 COMMENT = $five");
        assertAnswer("Password policy 'PP679' created successfully",
            "CREATE OR REPLACE PASSWORD POLICY pp679 COMMENT = $b");
        assertAnswer("Password policy 'PP679' created successfully",
            "CREATE OR REPLACE PASSWORD POLICY pp679 COMMENT = $x");
        answer("DESC PASSWORD POLICY pp679");
        assertAnswer("COMMENT|hello",
            "SELECT \"property\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"property\" = 'COMMENT'");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$five' for 'COMMENT'",
            "ALTER PASSWORD POLICY pp679 SET COMMENT = $five");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$five' for 'PASSWORD_MIN_LENGTH'",
            "ALTER PASSWORD POLICY pp679 SET PASSWORD_MIN_LENGTH = $five");
        assertAnswer("Statement executed successfully.", "ALTER PASSWORD POLICY pp679 SET COMMENT = $nul");
        assertAnswer("Session policy 'SP679' created successfully.",
            "CREATE OR REPLACE SESSION POLICY sp679 COMMENT = $x");
        assertAnswer("REFUSED SQL compilation error: | invalid type of property '$x' for "
                + "'SESSION_UI_IDLE_TIMEOUT_MINS'",
            "CREATE OR REPLACE SESSION POLICY sp679 SESSION_UI_IDLE_TIMEOUT_MINS = $x");
    }
}
