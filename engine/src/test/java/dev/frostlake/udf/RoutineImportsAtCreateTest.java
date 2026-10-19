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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A routine's IMPORTS are judged as the routine is created, in the order written: an import that names a stage
 * and no file below it is refused before its stage is looked for, a table's stage is there while its table
 * is, and a file the stage does not hold is refused by its path below the stage as written - for a named, a
 * user's and a table's stage, for a function and a procedure of any language, ahead of the runtime version
 * and the packages (live-verified).
 */
public class RoutineImportsAtCreateTest extends MeasuredCellsTestSupport {

    @Test
    public void aMissingFileIsRefused() {
        try {
            assertAnswer(
                "Stage area JST successfully created.",
                "CREATE OR REPLACE STAGE jst");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/nosuch.jar') HANDLER = 'H.f' AS $$ class H { public static int f(int x) { return x; } "
                    + "} $$");
            assertAnswer(
                "REFUSED Remote file 'dir/nosuch.jar' was not found. If you are running a copy command, please "
                    + "make sure files are not deleted when they are being loaded or files are not being loaded into "
                    + "two different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/dir/nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'a.jar' was not found. If you are running a copy command, please make sure "
                    + "files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/a.jar', "
                    + "'@jst/b.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCHSTAGE' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCHSTAGE.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@nosuchstage/x.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch_fl.jar' was not found. If you are running a copy command, please "
                    + "make sure files are not deleted when they are being loaded or files are not being loaded into "
                    + "two different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@~/nosuch_fl.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@%ct/nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'NoSuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/NoSuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@JST/nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@test_db.test_schema.jst/nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name 'jst' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/') "
                    + "HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name 'jst' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst') "
                    + "HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/nosuch.jar') HANDLER = 'JS.f' TARGET_PATH = '@jst/out.jar' AS $$ class JS { public "
                    + "static int f(int x) { return x; } } $$");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE PROCEDURE pimp() RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') IMPORTS = ('@jst/nosuch.jar') HANDLER = 'JS.run'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.py' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION pyimp() RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                    + "IMPORTS = ('@jst/nosuch.py') HANDLER = 'f' AS $$\ndef f():\n    return 1\n$$");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION scimp(x NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = "
                    + "'2.12' IMPORTS = ('@jst/nosuch.jar') HANDLER = 'S.f'");
            assertAnswer(
                "",
                "SHOW USER FUNCTIONS LIKE 'FIMP'");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fimp(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pimp()");
            answer(
                "DROP FUNCTION IF EXISTS pyimp()");
            answer(
                "DROP FUNCTION IF EXISTS scimp(NUMBER)");
            answer(
                "DROP TABLE IF EXISTS ct");
            answer(
                "DROP STAGE IF EXISTS jst");
        }
    }

    @Test
    public void aStageWithoutAFileAndOtherPaths() {
        try {
            assertAnswer(
                "Stage area JST successfully created.",
                "CREATE OR REPLACE STAGE jst");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name 'JST' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@JST') "
                    + "HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name 'nosuchstage' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@nosuchstage') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name 'nosuchstage' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@nosuchstage/') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name '~' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@~') HANDLER "
                    + "= 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name '~' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@~/') HANDLER "
                    + "= 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name '%ct' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@%ct') "
                    + "HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                    + "stage name '%ct' was provided.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@%ct/') "
                    + "HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file '/nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst//nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.\"%NOSUCHTABLE\"' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.\"%NOSUCHTABLE\".",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@%nosuchtable/x.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file './x.jar' was not found. If you are running a copy command, please make sure "
                    + "files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/./x.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/nosuch.jar') HANDLER = 'H.g' AS $$ class H { public static int f(int x) { return x; } "
                    + "} $$");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/nosuch.jar') HANDLER = 'H.f' AS $$ class H { public static int f(int x) { return y; } "
                    + "} $$");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '8' "
                    + "IMPORTS = ('@jst/nosuch.jar') HANDLER = 'JS.f'");
            assertAnswer(
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                    + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                    + "different tables concurrently with auto purge option.",
                "CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                    + "('@jst/nosuch.jar') PACKAGES = ('com.snowflake:nosuchpkg:latest') HANDLER = 'JS.f'");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fimp(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pimp()");
            answer(
                "DROP FUNCTION IF EXISTS pyimp()");
            answer(
                "DROP FUNCTION IF EXISTS scimp(NUMBER)");
            answer(
                "DROP TABLE IF EXISTS ct");
            answer(
                "DROP STAGE IF EXISTS jst");
        }
    }
}
