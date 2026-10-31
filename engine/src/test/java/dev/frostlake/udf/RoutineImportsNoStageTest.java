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
 * A routine's IMPORTS judged as it is created (live-verified): an import that names no stage - a URL of any scheme, a
 * path, a stage's name without its @, an empty text - is refused, SQL compilation error: invalid URL prefix found in:
 * '...', in the order the imports are written; a file a user's, a table's or a named stage does not hold is refused for
 * a function, a table function and a procedure alike.
 */
public class RoutineImportsNoStageTest extends MeasuredCellsTestSupport {

    /** A Java routine's imports on each kind of stage and outside one. */
    @Test
    public void importsOfJavaRoutines() {
        // The default surface: an import that names no stage is refused unless the engine opts in to local files,
        // which the suite's engine does for its stages.
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "false");
        engine.execute("CREATE OR REPLACE TABLE ti801 (a INT)");
        engine.execute("CREATE OR REPLACE STAGE st801");
        engine.execute("CREATE OR REPLACE STAGE st803");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION ju801(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@~/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } "
                + "}\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION jt801(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@%ti801/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } "
                + "}\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION js801(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('s3://nosuch-bucket-p7/x.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { "
                + "return x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 's3://nosuch-bucket-p7/x.jar'"},
            {"CREATE OR REPLACE FUNCTION jn801(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st801/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } "
                + "}\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION jq801(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@\"ST801\"/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return "
                + "x; } }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION jw801(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st801/') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } }\n$$",
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the stage "
                + "name 'st801' was provided."},
            {"CREATE OR REPLACE FUNCTION jtf801(x INT) RETURNS TABLE (a INT) LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "IMPORTS = ('@st801/nosuch_p7.jar') HANDLER = 'H' AS $$\nimport java.util.stream.Stream;\nclass "
                + "OutputRow { public int a; public OutputRow(int a) { this.a = a; } }\nclass H { public static Class "
                + "getOutputClass() { return OutputRow.class; }\n  public Stream<OutputRow> process(int x) { return "
                + "Stream.of(new OutputRow(x)); } }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE PROCEDURE jp801() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') IMPORTS = ('@st801/nosuch_p7.jar') HANDLER = 'H.run' AS "
                + "$$\nimport com.snowflake.snowpark_java.*;\nclass H { public static String run(Session s) { return "
                + "\"x\"; } }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION jb803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'nosuch_p7.jar'"},
            {"CREATE OR REPLACE FUNCTION jf803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('file:///tmp/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return "
                + "x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'file:///tmp/nosuch_p7.jar'"},
            {"CREATE OR REPLACE FUNCTION jg803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('gcs://b-p7/x.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } "
                + "}\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'gcs://b-p7/x.jar'"},
            {"CREATE OR REPLACE FUNCTION ja803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('azure://a.blob.core.windows.net/c/x.jar') HANDLER = 'H.f' AS $$\nclass H { public static int "
                + "f(int x) { return x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: "
                + "'azure://a.blob.core.windows.net/c/x.jar'"},
            {"CREATE OR REPLACE FUNCTION jh803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('https://example.com/x.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return "
                + "x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'https://example.com/x.jar'"},
            {"CREATE OR REPLACE FUNCTION ju803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('S3://B-P7/X.JAR') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'S3://B-P7/X.JAR'"},
            {"CREATE OR REPLACE FUNCTION jo803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st803/nosuch_p7.jar', 's3://b-p7/x.jar') HANDLER = 'H.f' AS $$\nclass H { public static int "
                + "f(int x) { return x; } }\n$$",
                "REFUSED Remote file 'nosuch_p7.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION jr803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('s3://b-p7/x.jar', '@st803/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int "
                + "f(int x) { return x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 's3://b-p7/x.jar'"},
            {"CREATE OR REPLACE FUNCTION jd803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('/tmp/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } "
                + "}\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: '/tmp/nosuch_p7.jar'"},
            {"CREATE OR REPLACE FUNCTION jc803(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('st803/nosuch_p7.jar') HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } "
                + "}\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: 'st803/nosuch_p7.jar'"},
            {"CREATE OR REPLACE FUNCTION je804(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = ('') "
                + "HANDLER = 'H.f' AS $$\nclass H { public static int f(int x) { return x; } }\n$$",
                "REFUSED SQL compilation error: | invalid URL prefix found in: ''"},
        });
    }
}
