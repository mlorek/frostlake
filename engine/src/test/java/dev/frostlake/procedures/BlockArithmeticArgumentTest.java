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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block hands a call arithmetic over its NUMBER variables as SQL, each variable written as its value's conversion
 * to its declared type, so the call reads the type SQL gives the arithmetic (live-verified): TO_VARCHAR(x / 4) over
 * an INT holding 10 is 2.500000, and TO_VARCHAR(x + 1) over a NUMBER(10,2) holding 1.50 is 2.50, in a RETURN and in a
 * LET alike.
 */
public class BlockArithmeticArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void arithmeticOverVariablesIsTypedAsSqlTypesIt() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET x INT := 10; RETURN TO_VARCHAR(x / 4); END; $$", "2.500000"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x NUMBER(10,2) := 1.50; RETURN TO_VARCHAR(x + 1); END; $$", "2.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x NUMBER(10,2) := 1.50; LET y VARCHAR := TO_VARCHAR(x + 1); RETURN "
                + "y; END; $$",
                "2.50"},
        });
    }
}
