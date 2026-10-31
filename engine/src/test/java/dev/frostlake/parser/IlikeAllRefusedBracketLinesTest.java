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
 * The syntax-error lines of ILIKE ALL, negated or not, whose bracket opens with a bracket: the inner bracket is
 * refused and opens nothing, so a comma inside it carries the select list on and the parenthesis that closes it
 * is refused (all live-verified).
 */
public class IlikeAllRefusedBracketLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void negated() {
        engine.execute("CREATE OR REPLACE TABLE pq3 (n INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ALL'. | syntax "
                + "error line 1 at position 26 unexpected '('. | syntax error line 1 at position 35 unexpected ')'.",
            "SELECT 'a' NOT ILIKE ALL (('a', 'b'))");
    }

    @Test
    public void nestedBrackets() {
        engine.execute("CREATE OR REPLACE TABLE pq3 (n INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'ALL'. | syntax "
                + "error line 1 at position 22 unexpected '('. | syntax error line 1 at position 31 unexpected ')'.",
            "SELECT 'a' ILIKE ALL (('a', 'b'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'ALL'. | syntax "
                + "error line 1 at position 22 unexpected '('. | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT 'a' ILIKE ALL (('a'), 'b')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ALL'. | syntax "
                + "error line 1 at position 26 unexpected '('.",
            "SELECT 'a' NOT ILIKE ALL ((('a')))");
    }
}
