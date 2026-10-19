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
 * A number written straight before letters is a number and an alias — SELECT 1x, 1a, 0b11, 1.5a, 1e5x, 2abc, .5a,
 * 12ab34, 3d and 1f each answer the number — but for a hexadecimal prefix, 0x1F and 0X1F, one token no rule reads,
 * and a number with two points, 1.5.6, refused whole; an exponent or a prefix the input ends on, 1e and 0x, is told
 * as a number that stops and an end nothing may stand at (all live-verified).
 */
public class NumberRunningIntoLettersTest extends MeasuredCellsTestSupport {

    @Test
    public void aNumberBeforeLettersIsANumberAndAnAlias() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1F'.",
            "SELECT 0x1F");
        assertAnswer("1", "SELECT 1x");
        assertAnswer("1", "SELECT 1a");
        assertAnswer("0", "SELECT 0b11");
        assertAnswer("1.5", "SELECT 1.5a");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '<EOF>'. | syntax error "
                + "line 1 at position 9 unexpected '<EOF>'.",
            "SELECT 1e");
        assertAnswer("1", "SELECT 1_000");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '<EOF>'. | syntax error "
                + "line 1 at position 9 unexpected '<EOF>'.",
            "SELECT 0x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x1F'.",
            "SELECT 0x1F + 1");
        assertAnswer("1", "SELECT 1 a");
        assertAnswer("100000", "SELECT 1e5x");
        assertAnswer("1500", "SELECT 1.5e3");
        assertAnswer("2", "SELECT 2abc FROM (SELECT 1)");
        assertAnswer("1F", "SELECT x'1F'");
        assertAnswer("0.5", "SELECT .5a");
        assertAnswer("12", "SELECT 12ab34");
        assertAnswer("100000", "SELECT 1e5 AS e");
        assertAnswer("3", "SELECT 3d");
        assertAnswer("1", "SELECT 1f");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '$'.", "SELECT 7$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1.5.6'.",
            "SELECT 1.5.6");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0X1F'.",
            "SELECT 0X1F");
    }
}
