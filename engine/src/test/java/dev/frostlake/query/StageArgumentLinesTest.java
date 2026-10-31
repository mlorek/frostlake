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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A staged-file query's argument list that does not parse, refused with the lines the account reports (live-verified):
 * the list is the query's from its opening parenthesis on; a token after a whole argument and a value's empty
 * parentheses are refused alone; a fault inside a value in parentheses is followed by the list's own ')'; an empty
 * first argument is refused at its comma, and a '=>' with no name before it at itself and then where the name's own
 * '=>' was due.
 */
public class StageArgumentLinesTest extends MeasuredCellsTestSupport {

    /** Each staged-file query and the lines it is refused with. */
    @Test
    public void eachArgumentListAndItsLines() {
        engine.execute("CREATE OR REPLACE STAGE ust");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV");
        assertCells(new String[][] {
            {"SELECT $1 FROM @ust (FILE_FORMAT => (FORMAT_NAME => 'ff'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '=>'. | syntax "
                + "error line 1 at position 57 unexpected ')'."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => ())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => ff PATTERN => '.*')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'PATTERN'."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => (TYPE = CSV) PATTERN => '.*')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'PATTERN'."},
            {"SELECT $1, $2 FROM @ust (,) ORDER BY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','."},
            {"SELECT $1, $2 FROM @ust (=> ff) ORDER BY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '=>'. | syntax "
                + "error line 1 at position 30 unexpected ')'."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => ff,)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => ff, ,)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'."},
            {"SELECT $1 FROM @ust (PATTERN => '.*' FILE_FORMAT => ff)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'FILE_FORMAT'."},
            {"SELECT $1 FROM @ust (FILE_FORMAT ff)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'ff'."},
            {"SELECT $1 FROM @ust (ff =>)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'."},
            {"SELECT $1 FROM @ust ()",
                ""},
        });
    }
}
