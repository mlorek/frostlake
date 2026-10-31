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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The value an out-of-range FLOAT or VARIANT double is refused with, as the account prints it: a FLOAT in C's %g
 * six significant digits (1e+39, 1.23457e+39, 123457), classed by the target's width, a VARIANT's double as the
 * VARIANT prints it (1.2345678e+39), and a VARIANT's whole number in full - through a cast, TO_NUMBER and
 * TO_DECIMAL, a column's cast and an INSERT (all live-verified).
 */
public class OverflowValueTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aDoubleIsPrintedAsTheAccountPrintsIt() {
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1.23457e+39",
            "SELECT 1.2345678e39::FLOAT::NUMBER");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](10,2){not null}, value 1e+39",
            "SELECT 1e39::FLOAT::NUMBER(10,2)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+39",
            "SELECT 1e39::FLOAT::INT");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value 1.2345678e+39",
            "SELECT PARSE_JSON('1.2345678e39')::NUMBER");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value "
                + "123456789012345678901234567890123456789",
            "SELECT PARSE_JSON('123456789012345678901234567890123456789')::NUMBER");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value 1e+39",
            "SELECT PARSE_JSON('1e39')::INT");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+39",
            "SELECT TO_NUMBER(1e39::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](10,2){not null}, value 1e+39",
            "SELECT TO_DECIMAL(1e39::FLOAT, 10, 2)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](5,0){not null}, value 123457",
            "SELECT 123456.7::FLOAT::NUMBER(5,0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](10,0){not null}, value 1e+20",
            "SELECT 1e20::FLOAT::NUMBER(10,0)");
        assertAnswer("Table FF successfully created.", "CREATE TABLE ff (f FLOAT)");
        assertAnswer("1", "INSERT INTO ff VALUES (1e39)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value 1e+39",
            "SELECT f::NUMBER FROM ff");
        assertAnswer("Table FN successfully created.", "CREATE TABLE fn (n NUMBER(38,0))");
        assertAnswer("REFUSED DML operation to table FN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){nullable}, value 1e+39",
            "INSERT INTO fn SELECT f FROM ff");
    }
}
