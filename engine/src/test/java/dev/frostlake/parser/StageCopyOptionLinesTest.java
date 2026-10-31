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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The syntax-error lines of a stage's COPY_OPTIONS list: a list of bare keys refuses its = and its parenthesis, a
 * word after a bare key is named twice before the parenthesis, a % in a value twice; a table's STAGE_COPY_OPTIONS
 * with a value is taken (all live-verified).
 */
public class StageCopyOptionLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void stageCopyOptions() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected '='. | syntax "
                + "error line 1 at position 66 unexpected ')'.",
            "CREATE OR REPLACE STAGE sc26 COPY_OPTIONS = (PURGE, SIZE_LIMIT = 5)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'NOPE'. | syntax "
                + "error line 1 at position 51 unexpected 'NOPE'. | syntax error line 1 at position 55 unexpected ')'.",
            "CREATE OR REPLACE STAGE sc26 COPY_OPTIONS = (PURGE NOPE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected '%'. | syntax "
                + "error line 1 at position 68 unexpected '%'.",
            "CREATE OR REPLACE STAGE sc26 COPY_OPTIONS = (ON_ERROR = SKIP_FILE_10%)");
        assertAnswer("Table T26C successfully created.",
            "CREATE OR REPLACE TABLE t26c (a INT) STAGE_COPY_OPTIONS = (PURGE = TRUE)");
    }
}
