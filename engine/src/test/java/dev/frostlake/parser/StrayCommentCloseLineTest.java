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
 * A comment close no comment opened, at the text's end, is a star and a slash: the slash is refused and then the
 * text's end, {@code SELECT 1 *}{@code /} at 10 and 11; a binary literal whose quotes are doubled after its opening
 * one takes everything to the end, {@code SELECT x'''} the lexer's line and then the end (live-verified).
 */
public class StrayCommentCloseLineTest extends MeasuredCellsTestSupport {

    @Test
    public void theEndIsNamedAfterTheFault() {
        assertCells(new String[][] {
            {"SELECT 1 /* a */ */",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '/'. | syntax "
                + "error line 1 at position 19 unexpected '<EOF>'."},
            {"SELECT x'''",
                "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax error "
                + "line 1 at position 11 unexpected '<EOF>'."},
            {"SELECT 1 */",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '/'. | syntax "
                + "error line 1 at position 11 unexpected '<EOF>'."},
            {"SELECT 1 */ 2", "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '/'."},
            {"SELECT x'''''",
                "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error "
                + "line 1 at position 13 unexpected '<EOF>'."},
            {"SELECT 1 /* a */ */ 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '/'."},
        });
    }
}
