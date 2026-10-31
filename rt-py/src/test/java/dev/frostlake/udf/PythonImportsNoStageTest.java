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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Python routine's IMPORTS judged as it is created (live-verified): a file a stage does not hold is refused for a
 * function, a table function and a procedure alike, and an import that names no stage with the account's invalid URL
 * prefix found in: '...'.
 */
public class PythonImportsNoStageTest extends MeasuredCellsTestSupport {

    /** A Python function's, table function's and procedure's imports. */
    @Test
    public void importsOfPythonRoutines() {
        // The default surface: an import that names no stage is refused unless the engine opts in to local files,
        // which the suite's engine does for its stages.
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "false");
        engine.execute("CREATE OR REPLACE TABLE ti801 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st801");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION pf801(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' IMPORTS = "
                + "('@~/nosuch_p7.py') HANDLER = 'f' AS $$\ndef f(x):\n    return x\n$$",
                "REFUSED Remote file 'nosuch_p7.py' was not found. If you are running a copy command, please make sure "
                + "files are not deleted when they are being loaded or files are not being loaded into two different "
                + "tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION ptf801() RETURNS TABLE (a INT) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
                + "IMPORTS = ('@st801/nosuch_p7.py') HANDLER = 'H' AS $$\nclass H:\n    def process(self):\n        "
                + "yield (1,)\n$$",
                "REFUSED Remote file 'nosuch_p7.py' was not found. If you are running a copy command, please make sure "
                + "files are not deleted when they are being loaded or files are not being loaded into two different "
                + "tables concurrently with auto purge option."},
            {"CREATE OR REPLACE PROCEDURE pp801() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.11' PACKAGES = "
                + "('snowflake-snowpark-python') IMPORTS = ('@st801/nosuch_p7.py') HANDLER = 'f' AS $$\ndef "
                + "f(session):\n    return 'x'\n$$",
                "REFUSED Remote file 'nosuch_p7.py' was not found. If you are running a copy command, please make sure "
                + "files are not deleted when they are being loaded or files are not being loaded into two different "
                + "tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION ps803(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' IMPORTS = "
                + "('s3://b-p7/x.py') HANDLER = 'f' AS $$\ndef f(x):\n    return x\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 's3://b-p7/x.py'"},
        });
    }
}
