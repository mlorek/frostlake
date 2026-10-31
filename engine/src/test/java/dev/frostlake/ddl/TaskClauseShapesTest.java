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
 * The shapes live's task clauses parse, and what they mean (live-verified). EXECUTE TASK ... USING takes a list of
 * properties, CONFIG the one a run knows: a name given twice is a duplicate property, then the first name in the
 * account's order that no task carries is an invalid property, named as written, then CONFIG refuses a number, a switch
 * and a list of constants. SET CONTACT takes a parenthesized purpose, refused once the contact is found. A
 * parenthesized list of properties as a value reads as the word TOK_PROPERTY_LIST, and a list of constants holding one
 * as TOK_LIST, a task's or a session's; a list of constants is a session's TOK_CONSTANT_LIST. A task runs as a user
 * named as written, and a missing one is refused with no hint.
 */
public class TaskClauseShapesTest extends MeasuredCellsTestSupport {

    /** The properties are judged before the task is looked up, and nothing runs. */
    @Test
    public void executeTaskUsingTakesAPropertyList() {
        assertCells(new String[][] {
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}'",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}' FOO = 'x'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch788 USING FOO = 'x'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}' CONFIG = '{}'",
                "REFUSED SQL compilation error: | duplicate property 'CONFIG';"},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}', FOO = 'x'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch788 USING CONFIG = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'CONFIG'"},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}' RETRY = 'x'",
                "REFUSED SQL compilation error: | invalid property 'RETRY' for 'TASK'"},
            {"EXECUTE TASK nosuch788 USING CONFIG '{}'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ''{}''."},
            {"EXECUTE TASK nosuch788 USING CONFIG = $$x$$",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"EXECUTE TASK nosuch788 RETRY LAST",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}' FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}' FOO = BAR",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch788 USING CONFIG = '{}' FOO = 'x' BAR = 'y'",
                "REFUSED SQL compilation error: | invalid property 'BAR' for 'TASK'"},
            {"EXECUTE TASK nosuch789 USING CONFIG = '{}' FOO = 'x' CONFIG = '{}'",
                "REFUSED SQL compilation error: | duplicate property 'CONFIG';"},
            {"EXECUTE TASK nosuch789 USING CONFIG = '{}' CONFIG = 1",
                "REFUSED SQL compilation error: | duplicate property 'CONFIG';"},
            {"EXECUTE TASK nosuch789 USING CONFIG = 1 FOO = 'x'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch789 USING FOO = 'x' BAR = 'y' BAZ = 'z'",
                "REFUSED SQL compilation error: | invalid property 'BAR' for 'TASK'"},
            {"EXECUTE TASK nosuch789 USING config = '{}'",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH789' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH789."},
            {"EXECUTE TASK nosuch789 USING CONFIG = '{}' RETRY LAST",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'LAST'."},
            {"EXECUTE TASK nosuch789 USING CONFIG = NULL",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH789' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH789."},
            {"EXECUTE TASK nosuch789 USING CONFIG = TRUE",
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'CONFIG'"},
            {"EXECUTE TASK nosuch789 USING CONFIG = ('x')",
                "REFUSED Invalid value specified for property 'CONFIG'"},
            {"EXECUTE TASK nosuch789 USING CONFIG = x",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH789' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH789."},
            {"EXECUTE TASK nosuch789 USING CONFIG = 'not json'",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH789' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH789."},
            {"EXECUTE TASK nosuch790 USING CONFIG = '{}', CONFIG = '{}'",
                "REFUSED SQL compilation error: | duplicate property 'CONFIG';"},
            {"EXECUTE TASK nosuch790 USING CONFIG = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'CONFIG'"},
            {"EXECUTE TASK nosuch790 USING CONFIG = -1",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'CONFIG'"},
            {"EXECUTE TASK nosuch790 USING CONFIG = (a = 'b')",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH790' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH790."},
            {"EXECUTE TASK nosuch790 USING CONFIG = ()",
                "REFUSED Invalid value specified for property 'CONFIG'"},
            {"EXECUTE TASK nosuch790 USING foo = 'x'",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TASK'"},
            {"EXECUTE TASK nosuch790 USING \"Foo\" = 'x'",
                "REFUSED SQL compilation error: | invalid property '\"Foo\"' for 'TASK'"},
            {"EXECUTE TASK nosuch790 USING AAA = 'x' ZZZ = 'y'",
                "REFUSED SQL compilation error: | invalid property 'AAA' for 'TASK'"},
            {"EXECUTE TASK nosuch790 USING ZZZ = 'x' AAA = 'y'",
                "REFUSED SQL compilation error: | invalid property 'AAA' for 'TASK'"},
            {"EXECUTE TASK nosuch790 USING CONFIG = '{}' foo = 'x' bar = 'y'",
                "REFUSED SQL compilation error: | invalid property 'bar' for 'TASK'"},
            {"EXECUTE TASK nosuch790 USING CONFIG = FALSE",
                "REFUSED SQL compilation error: | invalid value [FALSE] for parameter 'CONFIG'"},
            {"EXECUTE TASK nosuch790 USING CONFIG = a.b",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH790' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH790."},
            {"EXECUTE TASK nosuch790 USING CONFIG = '{}' ;",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH790' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH790."},
            {"EXECUTE TASK nosuch790 USING CONFIG = 'x' RETRY LAST",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'LAST'."},
            {"EXECUTE TASK nosuch790 RETRY LAST USING CONFIG = '{}'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'USING'."},
            {"EXECUTE TASK nosuch790 USING CONFIG = '{}' CONFIG = '{}' FOO = 'x'",
                "REFUSED SQL compilation error: | duplicate property 'CONFIG';"},
            {"EXECUTE TASK nosuch790 USING CONFIG = 1 CONFIG = 2",
                "REFUSED SQL compilation error: | duplicate property 'CONFIG';"},
            {"EXECUTE TASK nosuch790 USING FOO = 1 CONFIG = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
            {"EXECUTE TASK nosuch790 USING CONFIG = 1 FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TASK'"},
        });
    }

    /** A purpose written as a list is refused after the contact it names is looked up. */
    @Test
    public void aContactPurposeIsJudgedOnceTheContactIsFound() {
        engine.execute("CREATE OR REPLACE TABLE ct788 (i INT)");
        engine.execute("CREATE OR REPLACE TASK tk788 SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertCells(new String[][] {
            {"ALTER TASK nosuch788 SET CONTACT (SUPPORT = c) = x",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"ALTER TASK nosuch788 SET CONTACT (SUPPORT = c) = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''."},
            {"ALTER TABLE nosuch788 SET CONTACT (SUPPORT = c) = x",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"ALTER TASK tk788 SET CONTACT (SUPPORT = c) = x",
                "REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.X' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.X."},
            {"ALTER TABLE ct788 SET CONTACT (SUPPORT = c) = x",
                "REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.X' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.X."},
        });
    }

    /** A purpose written as a list of properties or of constants is refused by the parser's word for it. */
    @Test
    public void aContactPurposeListIsNoPurpose() {
        engine.execute("CREATE OR REPLACE TABLE ct789 (i INT)");
        engine.execute("CREATE OR REPLACE TASK tk789 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE CONTACT cx789 URL = 'https://example.com/789'");
        assertCells(new String[][] {
            {"ALTER TASK tk789 SET CONTACT (SUPPORT = c) = cx789",
                "REFUSED SQL compilation error: Purpose type TOK_PROPERTY_LIST is not valid, please use "
                + "access_approval, steward and support."},
            {"ALTER TASK tk789 SET CONTACT (SUPPORT = cx789) = cx789",
                "REFUSED SQL compilation error: Purpose type TOK_PROPERTY_LIST is not valid, please use "
                + "access_approval, steward and support."},
            {"ALTER TASK tk789 SET CONTACT (STEWARD = cx789) = cx789",
                "REFUSED SQL compilation error: Purpose type TOK_PROPERTY_LIST is not valid, please use "
                + "access_approval, steward and support."},
            {"ALTER TASK tk789 SET CONTACT () = cx789",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'purposeValue'"},
            {"ALTER TASK tk789 SET CONTACT (a) = cx789",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'purposeValue'"},
            {"ALTER TASK tk789 SET CONTACT (a b) = cx789",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'b'."},
            {"ALTER TASK tk789 SET CONTACT SUPPORT = cx789",
                "Statement executed successfully."},
            {"ALTER TASK tk789 SET CONTACT FOO = cx789",
                "REFUSED SQL compilation error: Purpose type FOO is not valid, please use access_approval, steward and "
                + "support."},
            {"ALTER TABLE ct789 SET CONTACT (SUPPORT = c) = cx789",
                "REFUSED SQL compilation error: Purpose type TOK_PROPERTY_LIST is not valid, please use "
                + "access_approval, steward and support."},
            {"ALTER TABLE ct789 SET CONTACT (STEWARD = x) = cx789",
                "REFUSED SQL compilation error: Purpose type TOK_PROPERTY_LIST is not valid, please use "
                + "access_approval, steward and support."},
        });
    }

    /** A list of constants or of properties as a value of a task or a session. */
    @Test
    public void aListAsATaskOrSessionValue() {
        engine.execute("CREATE OR REPLACE TABLE ct789 (i INT)");
        engine.execute("CREATE OR REPLACE TASK tk789 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE CONTACT cx789 URL = 'https://example.com/789'");
        assertCells(new String[][] {
            {"ALTER TASK nosuch788 SET QUERY_TAG = ('a', 'b')",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"ALTER TASK nosuch788 SET QUERY_TAG = ((a = 'b'))",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"ALTER TASK nosuch788 SET QUERY_TAG = (a = 'b')",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"ALTER TASK nosuch788 SET QUERY_TAG = ()",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH788' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH788."},
            {"ALTER TASK tk789 SET QUERY_TAG = (a = 'b')",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk789 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_PROPERTY_LIST|TASK"},
            {"ALTER TASK tk789 SET QUERY_TAG = ()",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk789 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_CONSTANT_LIST|TASK"},
            {"ALTER TASK tk789 SET QUERY_TAG = ('a')",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk789 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_CONSTANT_LIST|TASK"},
            {"ALTER TASK tk789 SET QUERY_TAG = (1)",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk789 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_CONSTANT_LIST|TASK"},
            {"ALTER TASK tk789 SET TIMEZONE = ('UTC')",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'TIMEZONE'"},
            {"ALTER TASK tk789 SET JSON_INDENT = (2)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'JSON_INDENT'"},
            {"ALTER TASK tk789 SET AUTOCOMMIT = (TRUE)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'AUTOCOMMIT'"},
            {"ALTER TASK tk789 SET USER_TASK_TIMEOUT_MS = (1000)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'USER_TASK_TIMEOUT_MS'"},
            {"ALTER TASK tk789 SET SUSPEND_TASK_AFTER_NUM_FAILURES = (3)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'SUSPEND_TASK_AFTER_NUM_FAILURES'"},
            {"ALTER TASK tk789 SET ALLOW_OVERLAPPING_EXECUTION = (TRUE)",
                "REFUSED Invalid value specified for property 'ALLOW_OVERLAPPING_EXECUTION'"},
            {"ALTER TASK tk789 SET WAREHOUSE = (x)",
                "REFUSED Invalid value specified for property 'WAREHOUSE'"},
            {"ALTER TASK tk789 SET USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = ('XSMALL')",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE'"},
            {"ALTER TASK tk789 SET CONFIG = ('x')",
                "REFUSED Invalid value specified for property 'CONFIG'"},
            {"ALTER TASK tk789 SET TASK_AUTO_RETRY_ATTEMPTS = (1)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'TASK_AUTO_RETRY_ATTEMPTS'"},
            {"ALTER TASK tk789 SET COMMENT = (a = 'b')",
                "Statement executed successfully."},
            {"ALTER TASK tk789 SET COMMENT = ()",
                "REFUSED SQL compilation error: | invalid value [[]] for parameter 'comment'"},
            {"ALTER TASK tk789 SET COMMENT = ('a')",
                "REFUSED SQL compilation error: | invalid value [[a]] for parameter 'comment'"},
            {"ALTER TASK tk789 SET SCHEDULE = ()",
                "REFUSED Invalid value specified for property 'SCHEDULE'"},
            {"ALTER TASK tk789 SET LOG_LEVEL = ('INFO')",
                "REFUSED Invalid value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF."},
            {"ALTER TASK tk789 SET ERROR_INTEGRATION = (x)",
                "REFUSED Invalid value specified for property 'ERROR_INTEGRATION'"},
            {"ALTER TASK tk789 SET USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS = (60)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS'"},
            {"CREATE OR REPLACE TASK tk789b SCHEDULE = '60 MINUTE' QUERY_TAG = ('a', 'b') AS SELECT 1",
                "Task TK789B successfully created."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk789b ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_CONSTANT_LIST|TASK"},
            {"CREATE OR REPLACE TASK tk789b SCHEDULE = '60 MINUTE' COMMENT = ('a', 'b') AS SELECT 1",
                "REFUSED SQL compilation error: | invalid value [[a, b]] for parameter 'comment'"},
            {"CREATE OR REPLACE TASK tk789b SCHEDULE = ('60 MINUTE') AS SELECT 1",
                "REFUSED Invalid value specified for property 'SCHEDULE'"},
            {"ALTER SESSION SET QUERY_TAG = ('a', 'b')",
                "Statement executed successfully."},
            {"ALTER TASK tk789 UNSET QUERY_TAG",
                "Statement executed successfully."},
        });
    }

    /** A property list reads as TOK_PROPERTY_LIST wherever a word does, and a nested one as TOK_LIST. */
    @Test
    public void aPropertyListReadsAsTheParsersWord() {
        engine.execute("CREATE OR REPLACE TABLE ct790 (i INT)");
        engine.execute("CREATE OR REPLACE TASK tk790 SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertCells(new String[][] {
            {"ALTER SESSION SET QUERY_TAG = ('a', 'b')",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_CONSTANT_LIST|SESSION"},
            {"ALTER SESSION SET QUERY_TAG = (a = 'b')",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_PROPERTY_LIST|SESSION"},
            {"ALTER SESSION SET QUERY_TAG = ()",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_CONSTANT_LIST|SESSION"},
            {"ALTER SESSION SET QUERY_TAG = ((a = 'b'))",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_LIST|SESSION"},
            {"ALTER SESSION SET TIMEZONE = ('UTC')",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'TIMEZONE'"},
            {"ALTER SESSION SET TIMEZONE = (a = 'b')",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'TIMEZONE'"},
            {"ALTER SESSION SET JSON_INDENT = (2)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'JSON_INDENT'"},
            {"ALTER SESSION SET JSON_INDENT = (a = 2)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'JSON_INDENT'"},
            {"ALTER SESSION SET LOCK_TIMEOUT = ()",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter 'LOCK_TIMEOUT'"},
            {"ALTER SESSION UNSET QUERY_TAG",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION ->> SELECT \"value\", \"level\" FROM $1",
                "|"},
            {"ALTER TASK tk790 SET COMMENT = (a = 'b')",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TASK', 'tk790')",
                "create or replace task TK790 | \tschedule='60 MINUTE' | \tCOMMENT='TOK_PROPERTY_LIST' | \tas SELECT "
                + "1;"},
            {"ALTER TASK tk790 SET COMMENT = (a = 'b', c = 'd')",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TASK', 'tk790')",
                "create or replace task TK790 | \tschedule='60 MINUTE' | \tCOMMENT='TOK_PROPERTY_LIST' | \tas SELECT "
                + "1;"},
            {"ALTER TASK tk790 SET QUERY_TAG = ((a = 'b'))",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk790 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_LIST|TASK"},
            {"ALTER TASK tk790 SET QUERY_TAG = (a = 'b', c = 'd')",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk790 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_PROPERTY_LIST|TASK"},
            {"ALTER TASK tk790 SET QUERY_TAG = (a = 'b' c = 'd')",
                "Statement executed successfully."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk790 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_PROPERTY_LIST|TASK"},
            {"ALTER TASK tk790 SET TIMEZONE = (a = 'b')",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter 'TIMEZONE'"},
            {"ALTER TASK tk790 SET SCHEDULE = (a = 'b')",
                "REFUSED Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                + "schedule."},
            {"ALTER TASK tk790 SET USER_TASK_TIMEOUT_MS = (a = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'USER_TASK_TIMEOUT_MS'"},
            {"ALTER TASK tk790 SET ALLOW_OVERLAPPING_EXECUTION = (a = TRUE)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'ALLOW_OVERLAPPING_EXECUTION'"},
            {"ALTER TASK tk790 SET CONFIG = (a = 'b')",
                "REFUSED Invalid config. Must be a string representation of a valid JSON Object."},
            {"ALTER TASK tk790 SET WAREHOUSE = (a = b)",
                "REFUSED Nonexistent warehouse TOK_PROPERTY_LIST was specified."},
            {"ALTER TASK tk790 SET LOG_LEVEL = (a = 'b')",
                "REFUSED Invalid value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF."},
            {"ALTER TASK tk790 SET ERROR_INTEGRATION = (a = b)",
                "REFUSED SQL compilation error: | Integration 'TOK_PROPERTY_LIST' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on INTEGRATION "
                + "TOK_PROPERTY_LIST."},
            {"CREATE OR REPLACE TASK tk790 SCHEDULE = '60 MINUTE' QUERY_TAG = (a = 'b') COMMENT = (c = 'd') AS SELECT "
                + "1",
                "Task TK790 successfully created."},
            {"SHOW PARAMETERS LIKE 'QUERY_TAG' IN TASK tk790 ->> SELECT \"value\", \"level\" FROM $1",
                "TOK_PROPERTY_LIST|TASK"},
            {"SELECT GET_DDL('TASK', 'tk790')",
                "create or replace task TK790 | \tschedule='60 MINUTE' | \tQUERY_TAG='TOK_PROPERTY_LIST' | "
                + "\tCOMMENT='TOK_PROPERTY_LIST' | \tas SELECT 1;"},
        });
    }

    /** EXECUTE AS USER names a user as written, and a missing one is refused with no hint. */
    @Test
    public void aTaskRunsAsAUserNamedAsWritten() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK nosuch788 EXECUTE AS USER IDENTIFIER AS SELECT 1",
                "REFUSED SQL compilation error: | User 'IDENTIFIER' does not exist or not authorized."},
            {"CREATE OR REPLACE TASK nosuch788 EXECUTE AS USER CURRENT_USER AS SELECT 1",
                "REFUSED SQL compilation error: | User 'CURRENT_USER' does not exist or not authorized."},
            {"CREATE OR REPLACE TASK nosuch788 EXECUTE AS USER \"nosuch_user_788\" AS SELECT 1",
                "REFUSED SQL compilation error: | User '\"nosuch_user_788\"' does not exist or not authorized."},
            {"CREATE OR REPLACE TASK nosuch788 EXECUTE AS USER nosuch_user_788 AS SELECT 1",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_788' does not exist or not authorized."},
            {"CREATE OR REPLACE TASK nosuch788 EXECUTE AS USER SELECT AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'SELECT'."},
            {"CREATE OR REPLACE TASK nosuch788 EXECUTE AS USER TABLE AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'TABLE'."},
        });
    }
}
