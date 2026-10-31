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
 * A DECLARE section in a block's statement position, whose items take any word as their type (all live-verified).
 */
public class NestedDeclareSectionTest extends MeasuredCellsTestSupport {

    @Test
    public void nestedDeclarations() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q r; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "BEGIN RETURN 1; DECLARE q r; BEGIN RETURN 2; END; END");
        assertAnswer("1", "BEGIN RETURN 1; DECLARE q INT; BEGIN RETURN 2; END; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "BEGIN DECLARE q r; BEGIN RETURN 2; END; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'END'.",
            "BEGIN DECLARE q r; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 's'.",
            "BEGIN RETURN 1; DECLARE q r s; BEGIN RETURN 2; END; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "BEGIN RETURN 1; DECLARE q r; BEGIN RETURN 2; END; RETURN 3; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.", "DECLARE q r; BEGIN RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'S'.",
            "BEGIN RETURN 1; DECLARE q INT; r s; BEGIN RETURN 2; END; END");
        assertAnswer("4", "BEGIN DECLARE q INT DEFAULT 4; BEGIN RETURN q; END; END");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
