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
 * A word run into a literal in a bracket inside two calls: the word, then the outermost call's ')', as without the
 * bracket; inside one call the word alone (all live-verified).
 */
public class NestedBracketArgumentLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBracketInsideCalls() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "SELECT ABS(ABS((1 x y)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax "
                + "error line 1 at position 21 unexpected ')'.",
            "SELECT ABS(ABS(1 x y))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "SELECT ABS((1 x y))");
    }
}
