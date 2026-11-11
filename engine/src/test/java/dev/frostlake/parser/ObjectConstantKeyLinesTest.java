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
 * An object constant whose key is no text is refused at the key - or at the ':' after a key the account reads as a
 * word - and then at the first token of the value after each ':' from that pair to the constant's end
 * (live-verified): {@code SELECT {1: 'a', 'b': 2}} is '1', ''a'', then '2'.
 */
public class ObjectConstantKeyLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theKeyThenEachValue() {
        assertCells(new String[][] {
            {"SELECT {1: 'a'}::MAP(NUMBER, VARCHAR)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax error "
                + "line 1 at position 11 unexpected ''a''."},
            {"SELECT {1: 'a'}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax error "
                + "line 1 at position 11 unexpected ''a''."},
            {"SELECT {'a': 1, 2: 'b'}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '2'. | syntax "
                + "error line 1 at position 19 unexpected ''b''."},
            {"SELECT {1: 'a'} x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax error "
                + "line 1 at position 11 unexpected ''a''."},
            {"SELECT ({1: 'a'})::OBJECT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'. | syntax error "
                + "line 1 at position 12 unexpected ''a''."},
            {"SELECT {TRUE: 1}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ':'. | syntax "
                + "error line 1 at position 14 unexpected '1'."},
            {"SELECT {a: 1}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ':'. | syntax error "
                + "line 1 at position 11 unexpected '1'."},
            {"SELECT {1.5: 'a'}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1.5'. | syntax "
                + "error line 1 at position 13 unexpected ''a''."},
            {"SELECT {1: 'a'}::MAP(NUMBER, VARCHAR) x, 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax error "
                + "line 1 at position 11 unexpected ''a''."},
            {"SELECT {NULL: 1}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'NULL'. | syntax "
                + "error line 1 at position 14 unexpected '1'."},
            {"SELECT {1: 'a', 'b': 2}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax error "
                + "line 1 at position 11 unexpected ''a''. | syntax error line 1 at position 21 unexpected '2'."},
        });
    }
}
