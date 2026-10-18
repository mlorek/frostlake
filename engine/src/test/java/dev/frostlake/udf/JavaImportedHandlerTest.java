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
 * A Java routine whose code its IMPORTS hold has its handler looked for in them as it is created: a stage directory
 * that holds nothing holds no class, {@code java.lang.ClassNotFoundException: JS} with the loader's frames, for a
 * function and a procedure alike, and a handler that names no method is {@code Invalid function name JS in function
 * FIMP9 with handler JS}; a stage name alone and a file that is not there keep their own sentences (live-verified).
 */
public class JavaImportedHandlerTest extends MeasuredCellsTestSupport {

    @Test
    public void theHandlerIsLookedForInTheImports() {
        engine.execute("CREATE OR REPLACE STAGE jst");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fimp(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/dir/') "
                + "HANDLER = 'JS.f'",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: JS | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FIMP with handler "
                + "JS.f"},
            {"CREATE OR REPLACE FUNCTION fimp2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/') "
                + "HANDLER = 'JS.f'",
                "REFUSED SQL compilation error: IMPORT must specify the path of a file on a stage, but only the "
                + "stage name 'jst' was provided."},
            {"CREATE OR REPLACE FUNCTION fimp3(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/dir/') "
                + "HANDLER = 'p.JS.f'",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: p.JS | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FIMP3 with handler "
                + "p.JS.f"},
            {"CREATE OR REPLACE FUNCTION fimp4(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "IMPORTS = ('@jst/dir/') HANDLER = 'JS.f'",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: JS | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:592) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525) |  in function FIMP4 with handler "
                + "JS.f"},
            {"CREATE OR REPLACE PROCEDURE pimp() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                + "= ('com.snowflake:snowpark:latest') IMPORTS = ('@jst/dir/') HANDLER = 'JS.run'",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: JS | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function PIMP with handler "
                + "JS.run"},
            {"CREATE OR REPLACE FUNCTION fimp6(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = "
                + "('@jst/nosuch.jar') HANDLER = 'JS.f'",
                "REFUSED Remote file 'nosuch.jar' was not found. If you are running a copy command, please make "
                + "sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION fimp7(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/dir/', "
                + "'@jst/other/') HANDLER = 'JS.f'",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.ClassNotFoundException: JS | \tat "
                + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) |  in function FIMP7 with handler "
                + "JS.f"},
            {"CREATE OR REPLACE FUNCTION fimp8(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/dir') "
                + "HANDLER = 'JS.f'",
                "REFUSED Remote file 'dir' was not found. If you are running a copy command, please make sure "
                + "files are not deleted when they are being loaded or files are not being loaded into two different "
                + "tables concurrently with auto purge option."},
            {"CREATE OR REPLACE FUNCTION fimp9(x NUMBER) RETURNS NUMBER LANGUAGE JAVA IMPORTS = ('@jst/dir/') "
                + "HANDLER = 'JS'",
                "REFUSED Invalid function name JS in function FIMP9 with handler JS"},
        });
    }
}
