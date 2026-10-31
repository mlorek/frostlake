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
 * A Java routine's unknown package is refused after its missing IMPORTS file and after an invalid
 * RUNTIME_VERSION, and on its own as not supported (all live-verified).
 */
public class JavaPackageRefusalOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void refusalOrder() {
        assertAnswer("REFUSED Remote file 'nosuch_l24.jar' was not found. If you are running a copy command, please "
                + "make sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option.",
            "CREATE OR REPLACE FUNCTION pk_j1() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.example:nosuch:1.0') IMPORTS = ('@~/nosuch_l24.jar') HANDLER = 'H.f' AS 'class H { public "
                + "static int f() { return 1; } }'");
        assertAnswer("REFUSED SQL compilation error: | invalid value '7' for property 'RUNTIME_VERSION'",
            "CREATE OR REPLACE FUNCTION pk_j2() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '7' PACKAGES = "
                + "('com.example:nosuch:1.0') HANDLER = 'H.f' AS 'class H { public static int f() { return 1; } }'");
        assertAnswer("REFUSED SQL compilation error: Package 'com.example:nosuch:1.0' is not supported.",
            "CREATE OR REPLACE FUNCTION pk_j3() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.example:nosuch:1.0') HANDLER = 'H.f' AS 'class H { public static int f() { return 1; } }'");
    }
}
