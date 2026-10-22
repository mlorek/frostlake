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
 * GET's array index (all live-verified): a FLOAT index names no element, so GET([1,2,3], 1::FLOAT) is NULL, unless
 * it is written out, 1e0 reading the second element; an index written out as a number is cut to a whole number
 * while the statement compiles and refused below 0 or past 2,147,483,647, while a computed one - 0 - 1, a cast, a
 * column - naming no element answers NULL, as does a written one rounding past either end.
 */
public class GetIndexTest extends MeasuredCellsTestSupport {

    @Test
    public void aFloatIndexNamesNoElement() {
        assertAnswer("null", "SELECT GET([1,2,3], 1::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 0::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 1.4::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 1.5::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], -1::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 3e9::FLOAT)");
        answer("SELECT GET(PARSE_JSON('[1,2,3]'), 1::FLOAT)");
        assertAnswer("null", "SELECT [1,2,3][1::FLOAT]");
        assertAnswer("null", "SELECT [1,2,3][1.5::FLOAT]");
        assertAnswer("REFUSED Invalid extraction path '-1': array index -1 is off limits; must be between 0 and 2,147,"
                + "483,647.",
            "SELECT GET([1,2,3], -1)");
        assertAnswer("REFUSED Invalid extraction path '-3,000,000,000': array index -3,000,000,000 is off limits; "
                + "must be between 0 and 2,147,483,647.",
            "SELECT GET([1,2,3], -3000000000)");
        assertAnswer("null", "SELECT GET([1,2,3], 2147483647)");
        assertAnswer("REFUSED Invalid extraction path '2,147,483,648': array index 2,147,483,648 is off limits; must "
                + "be between 0 and 2,147,483,647.",
            "SELECT GET([1,2,3], 2147483648)");
        assertAnswer("3", "SELECT GET([1,2,3], 1.5)");
        assertAnswer("null", "SELECT GET([1,2,3], 2147483647.5)");
        assertAnswer("REFUSED Invalid extraction path '-2,147,483,648': array index -2,147,483,648 is off limits; "
                + "must be between 0 and 2,147,483,647.",
            "SELECT GET([1,2,3], -2147483648)");
        assertAnswer("REFUSED Invalid extraction path '-2,147,483,649': array index -2,147,483,649 is off limits; "
                + "must be between 0 and 2,147,483,647.",
            "SELECT GET([1,2,3], -2147483649)");
        assertAnswer("null", "SELECT GET([1,2,3], n) FROM (SELECT 3000000000 n)");
    }

    @Test
    public void aWrittenIndexIsJudgedWhileTheStatementCompiles() {
        assertAnswer("null", "SELECT GET([1,2,3], 0 - 1)");
        assertAnswer("null", "SELECT GET([1,2,3], -1::NUMBER)");
        assertAnswer("null", "SELECT GET([1,2,3], CAST(-1 AS INT))");
        assertAnswer("REFUSED Invalid extraction path '-1': array index -1 is off limits; must be between 0 and 2,147,"
                + "483,647.",
            "SELECT [1,2,3][-1]");
        assertAnswer("REFUSED Invalid extraction path '3,000,000,000': array index 3,000,000,000 is off limits; must "
                + "be between 0 and 2,147,483,647.",
            "SELECT [1,2,3][3000000000]");
        assertAnswer("2", "SELECT GET([1,2,3], ABS(-1))");
        assertAnswer("REFUSED Invalid extraction path '-1': array index -1 is off limits; must be between 0 and 2,147,"
                + "483,647.",
            "SELECT GET(ARRAY_CONSTRUCT(1,2,3), -1)");
        assertAnswer("REFUSED Invalid extraction path '-1': array index -1 is off limits; must be between 0 and 2,147,"
                + "483,647.",
            "SELECT GET(PARSE_JSON('[1,2,3]'), -1)");
        assertAnswer("REFUSED Invalid extraction path 'Extract path index cannot be null': invalid number at position "
                + "{1}.",
            "SELECT GET([1,2,3], NULL)");
        assertAnswer("null", "SELECT GET([1,2,3], 2147483647.4)");
        assertAnswer("1", "SELECT GET([1,2,3], -0.4)");
        assertAnswer("null", "SELECT GET([1,2,3], -0.5)");
        assertAnswer("2", "SELECT GET([1,2,3], 1e0)");
        assertAnswer("2", "SELECT GET([1,2,3], 1::NUMBER(10,2))");
    }
}
