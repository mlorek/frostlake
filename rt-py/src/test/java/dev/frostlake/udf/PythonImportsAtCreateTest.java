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
 * A Python function's IMPORTS file that is not on its stage is refused as the function is created
 * (live-verified).
 */
public class PythonImportsAtCreateTest extends MeasuredCellsTestSupport {

    @Test
    public void missingImport() {
        assertAnswer("REFUSED Remote file 'nosuch_l24.py' was not found. If you are running a copy command, please "
                + "make sure files are not deleted when they are being loaded or files are not being loaded into two "
                + "different tables concurrently with auto purge option.",
            "CREATE OR REPLACE FUNCTION pyimp() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' IMPORTS = "
                + "('@~/nosuch_l24.py') HANDLER = 'nosuch_l24.f'");
    }
}
