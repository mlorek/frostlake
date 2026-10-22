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
 * A whole-number argument is read through the fixed-point slot its function takes, which a FLOAT must fit (all
 * live-verified): a code point, a SUBSTR, LEFT, RIGHT or INSERT position or length, a REGEXP position, occurrence,
 * option or group, a GETBIT place and a RANDSTR length take FIXED[SB8](18,0); a CHARINDEX or POSITION start, a
 * STRTOK or SPLIT_PART part, an ARRAY_SLICE bound, an ARRAY_INSERT or ARRAY_REMOVE_AT position, an XMLGET instance
 * and a padded length FIXED[SB4](9,0). NaN, an infinity and a value rounding past the slot's digits are refused in
 * its words with the argument's nullability, SPLIT_PART's always nullable; an exact number past 64 bits is not
 * recognized and one past 32 out of range where the slot is 32 bits. CHR names a code's low 64 bits,
 * ARRAY_INSERT refuses an array past 2,147,483,647 elements, and BOOLAND, BOOLOR, BOOLXOR and BOOLNOT round each
 * operand to a whole number through the 64-bit slot.
 */
public class WholeNumberSlotTest extends MeasuredCellsTestSupport {

    @Test
    public void aFloatIsReadThroughTheSlotItsFunctionTakes() {
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT CHAR('inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT CHAR('nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT CHAR(1e30::FLOAT)");
        assertAnswer("", "SELECT CHAR(2.5::FLOAT)");
        assertShapedAnswer("REFUSED Invalid character code <ID19> in the CHR input",
            "SELECT CHAR(10000000000000000000000000)");
        assertAnswer("REFUSED Invalid character code 3000000000 in the CHR input", "SELECT CHAR(3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT CHR('inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT CHR('nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT CHR(1e30::FLOAT)");
        assertAnswer("", "SELECT CHR(2.5::FLOAT)");
        assertShapedAnswer("REFUSED Invalid character code <ID19> in the CHR input",
            "SELECT CHR(10000000000000000000000000)");
        assertAnswer("REFUSED Invalid character code 3000000000 in the CHR input", "SELECT CHR(3000000000)");
        assertAnswer("null", "SELECT GET([1,2,3], 'inf'::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 'nan'::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 1e30::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 2.5::FLOAT)");
        assertAnswer("REFUSED Invalid extraction path '10,000,000,000,000,000,000,000,000': array index 10,000,000,"
                + "000,000,000,000,000,000 is off limits; must be between 0 and 2,147,483,647.",
            "SELECT GET([1,2,3], 10000000000000000000000000)");
        assertAnswer("REFUSED Invalid extraction path '3,000,000,000': array index 3,000,000,000 is off limits; must "
                + "be between 0 and 2,147,483,647.",
            "SELECT GET([1,2,3], 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT SUBSTR('abcdef', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT SUBSTR('abcdef', 1e30::FLOAT)");
        assertAnswer("cdef", "SELECT SUBSTR('abcdef', 2.5::FLOAT)");
        assertAnswer("", "SELECT SUBSTR('abcdef', 10000000000000000000000000)");
        assertAnswer("", "SELECT SUBSTR('abcdef', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', 2, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT SUBSTR('abcdef', 2, 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT SUBSTR('abcdef', 2, 1e30::FLOAT)");
        assertAnswer("bcd", "SELECT SUBSTR('abcdef', 2, 2.5::FLOAT)");
        assertAnswer("bcdef", "SELECT SUBSTR('abcdef', 2, 10000000000000000000000000)");
        answer("SELECT SUBSTR('abcdef', 2, 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT LEFT('abcdef', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT LEFT('abcdef', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT LEFT('abcdef', 1e30::FLOAT)");
        assertAnswer("abc", "SELECT LEFT('abcdef', 2.5::FLOAT)");
        assertAnswer("abcdef", "SELECT LEFT('abcdef', 10000000000000000000000000)");
        answer("SELECT LEFT('abcdef', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT RIGHT('abcdef', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT RIGHT('abcdef', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT RIGHT('abcdef', 1e30::FLOAT)");
        assertAnswer("def", "SELECT RIGHT('abcdef', 2.5::FLOAT)");
        assertAnswer("abcdef", "SELECT RIGHT('abcdef', 10000000000000000000000000)");
        answer("SELECT RIGHT('abcdef', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_INSTR('abc', 'b', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT REGEXP_INSTR('abc', 'b', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_INSTR('abc', 'b', 1e30::FLOAT)");
        assertAnswer("0", "SELECT REGEXP_INSTR('abc', 'b', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT REGEXP_INSTR('abc', 'b', 10000000000000000000000000)");
        assertAnswer("0", "SELECT REGEXP_INSTR('abc', 'b', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_INSTR('abc', 'b', 1, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT REGEXP_INSTR('abc', 'b', 1, 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_INSTR('abc', 'b', 1, 1e30::FLOAT)");
        assertAnswer("0", "SELECT REGEXP_INSTR('abc', 'b', 1, 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT REGEXP_INSTR('abc', 'b', 1, 10000000000000000000000000)");
        assertAnswer("0", "SELECT REGEXP_INSTR('abc', 'b', 1, 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_SUBSTR('abc', 'b', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT REGEXP_SUBSTR('abc', 'b', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_SUBSTR('abc', 'b', 1e30::FLOAT)");
        assertAnswer("null", "SELECT REGEXP_SUBSTR('abc', 'b', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT REGEXP_SUBSTR('abc', 'b', 10000000000000000000000000)");
        assertAnswer("null", "SELECT REGEXP_SUBSTR('abc', 'b', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_COUNT('abc', 'b', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT REGEXP_COUNT('abc', 'b', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_COUNT('abc', 'b', 1e30::FLOAT)");
        assertAnswer("0", "SELECT REGEXP_COUNT('abc', 'b', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT REGEXP_COUNT('abc', 'b', 10000000000000000000000000)");
        assertAnswer("0", "SELECT REGEXP_COUNT('abc', 'b', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_REPLACE('abc', 'b', 'x', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT REGEXP_REPLACE('abc', 'b', 'x', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_REPLACE('abc', 'b', 'x', 1e30::FLOAT)");
        assertAnswer("abc", "SELECT REGEXP_REPLACE('abc', 'b', 'x', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT REGEXP_REPLACE('abc', 'b', 'x', 10000000000000000000000000)");
        assertAnswer("abc", "SELECT REGEXP_REPLACE('abc', 'b', 'x', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 1e30::FLOAT)");
        assertAnswer("[]", "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 10000000000000000000000000)");
        assertAnswer("[]", "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT CHARINDEX('b', 'abc', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT CHARINDEX('b', 'abc', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT CHARINDEX('b', 'abc', 1e30::FLOAT)");
        assertAnswer("0", "SELECT CHARINDEX('b', 'abc', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT CHARINDEX('b', 'abc', 10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT CHARINDEX('b', 'abc', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT POSITION('b', 'abc', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT POSITION('b', 'abc', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT POSITION('b', 'abc', 1e30::FLOAT)");
        assertAnswer("0", "SELECT POSITION('b', 'abc', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT POSITION('b', 'abc', 10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT POSITION('b', 'abc', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT INSERT('abc', 'inf'::FLOAT, 1, 'x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT INSERT('abc', 'nan'::FLOAT, 1, 'x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT INSERT('abc', 1e30::FLOAT, 1, 'x')");
        assertAnswer("abx", "SELECT INSERT('abc', 2.5::FLOAT, 1, 'x')");
        assertAnswer("abcx", "SELECT INSERT('abc', 10000000000000000000000000, 1, 'x')");
        answer("SELECT INSERT('abc', 3000000000, 1, 'x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT INSERT('abc', 1, 'inf'::FLOAT, 'x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT INSERT('abc', 1, 'nan'::FLOAT, 'x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT INSERT('abc', 1, 1e30::FLOAT, 'x')");
        assertAnswer("x", "SELECT INSERT('abc', 1, 2.5::FLOAT, 'x')");
        assertAnswer("x", "SELECT INSERT('abc', 1, 10000000000000000000000000, 'x')");
        assertAnswer("x", "SELECT INSERT('abc', 1, 3000000000, 'x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT SPLIT_PART('a,b', ',', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value nan",
            "SELECT SPLIT_PART('a,b', ',', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value 1e+30",
            "SELECT SPLIT_PART('a,b', ',', 1e30::FLOAT)");
        assertAnswer("", "SELECT SPLIT_PART('a,b', ',', 2.5::FLOAT)");
        answer("SELECT SPLIT_PART('a,b', ',', 10000000000000000000000000)");
        answer("SELECT SPLIT_PART('a,b', ',', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT STRTOK('a b', ' ', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT STRTOK('a b', ' ', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT STRTOK('a b', ' ', 1e30::FLOAT)");
        assertAnswer("null", "SELECT STRTOK('a b', ' ', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT STRTOK('a b', ' ', 10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT STRTOK('a b', ' ', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT ARRAY_SLICE([1,2,3], 'inf'::FLOAT, 2)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT ARRAY_SLICE([1,2,3], 'nan'::FLOAT, 2)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT ARRAY_SLICE([1,2,3], 1e30::FLOAT, 2)");
        assertAnswer("[]", "SELECT ARRAY_SLICE([1,2,3], 2.5::FLOAT, 2)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT ARRAY_SLICE([1,2,3], 10000000000000000000000000, 2)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT ARRAY_SLICE([1,2,3], 3000000000, 2)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT ARRAY_SLICE([1,2,3], 0, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT ARRAY_SLICE([1,2,3], 0, 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT ARRAY_SLICE([1,2,3], 0, 1e30::FLOAT)");
        assertAnswer("[1,2,3]", "SELECT ARRAY_SLICE([1,2,3], 0, 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT ARRAY_SLICE([1,2,3], 0, 10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT ARRAY_SLICE([1,2,3], 0, 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT ARRAY_INSERT([1,2], 'inf'::FLOAT, 9)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT ARRAY_INSERT([1,2], 'nan'::FLOAT, 9)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT ARRAY_INSERT([1,2], 1e30::FLOAT, 9)");
        assertAnswer("[1,2,undefined,9]", "SELECT ARRAY_INSERT([1,2], 2.5::FLOAT, 9)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT ARRAY_INSERT([1,2], 10000000000000000000000000, 9)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT ARRAY_INSERT([1,2], 3000000000, 9)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT ARRAY_REMOVE_AT([1,2], 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT ARRAY_REMOVE_AT([1,2], 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT ARRAY_REMOVE_AT([1,2], 1e30::FLOAT)");
        assertAnswer("[1,2]", "SELECT ARRAY_REMOVE_AT([1,2], 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT ARRAY_REMOVE_AT([1,2], 10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT ARRAY_REMOVE_AT([1,2], 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT GETBIT(5, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT GETBIT(5, 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT GETBIT(5, 1e30::FLOAT)");
        assertAnswer("0", "SELECT GETBIT(5, 2.5::FLOAT)");
        assertAnswer("REFUSED Bit index 10000000000000000000000000 out of range; Should be between 0 and 127",
            "SELECT GETBIT(5, 10000000000000000000000000)");
        assertAnswer("REFUSED Bit index 3000000000 out of range; Should be between 0 and 127",
            "SELECT GETBIT(5, 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT REPEAT('a', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT REPEAT('a', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT REPEAT('a', 1e30::FLOAT)");
        assertAnswer("aaa", "SELECT REPEAT('a', 2.5::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](38,0){not null}, value "
                + "10000000000000000000000000",
            "SELECT REPEAT('a', 10000000000000000000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](28,0){not null}, value 3000000000",
            "SELECT REPEAT('a', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT LPAD('a', 'inf'::FLOAT, 'b')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT LPAD('a', 'nan'::FLOAT, 'b')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT LPAD('a', 1e30::FLOAT, 'b')");
        assertAnswer("bba", "SELECT LPAD('a', 2.5::FLOAT, 'b')");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT LPAD('a', 10000000000000000000000000, 'b')");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT LPAD('a', 3000000000, 'b')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT SPACE('inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT SPACE('nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT SPACE(1e30::FLOAT)");
        assertAnswer("   ", "SELECT SPACE(2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT SPACE(10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT SPACE(3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT RANDSTR('inf'::FLOAT, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT RANDSTR('nan'::FLOAT, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT RANDSTR(1e30::FLOAT, 1)");
        answer("SELECT RANDSTR(2.5::FLOAT, 1)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT RANDSTR(10000000000000000000000000, 1)");
        assertAnswer("REFUSED Invalid parameter value: 3000000000. Reason: length must be at most 134217728",
            "SELECT RANDSTR(3000000000, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', 'nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', 1e30::FLOAT)");
        assertAnswer("null", "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', 2.5::FLOAT)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', 10000000000000000000000000)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', 3000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT DATEADD(day, 'inf'::FLOAT, '2024-01-01'::DATE)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan",
            "SELECT DATEADD(day, 'nan'::FLOAT, '2024-01-01'::DATE)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+30",
            "SELECT DATEADD(day, 1e30::FLOAT, '2024-01-01'::DATE)");
        assertAnswer("2024-01-04", "SELECT DATEADD(day, 2.5::FLOAT, '2024-01-01'::DATE)");
        assertAnswer("REFUSED Numeric value '10000000000000000000000000' is not recognized",
            "SELECT DATEADD(day, 10000000000000000000000000, '2024-01-01'::DATE)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT DATEADD(day, 3000000000, '2024-01-01'::DATE)");
    }

    @Test
    public void aNullableColumnSpellsTheSlotNullable() {
        engine.execute("CREATE OR REPLACE TABLE tn (f FLOAT, g FLOAT NOT NULL, n NUMBER(38,0), m NUMBER(10,0), k INT)");
        engine.execute("INSERT INTO tn VALUES ('inf'::FLOAT, 'inf'::FLOAT, 3000000000, 3000000000, -1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', g) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT CHARINDEX('b', 'abc', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT CHAR(f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT GETBIT(5, f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT REGEXP_INSTR('abc', 'b', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT SPLIT_PART('a,b', ',', g) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT LPAD('a', f, 'b') FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT REPEAT('a', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT ARRAY_INSERT([1,2], f, 9) FROM tn");
        assertAnswer("null", "SELECT GET([1,2,3], f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT STRTOK('a b', ' ', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT RANDSTR(f, 1) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT INSERT('abc', f, 1, 'x') FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT LEFT('abc', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT ARRAY_SLICE([1], f, 2) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT ARRAY_REMOVE_AT([1], f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT SPACE(f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT DATEADD(day, f, '2024-01-01'::DATE) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value inf",
            "SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', f) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](38,0){nullable}, value 3000000000",
            "SELECT REPEAT('a', n) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](28,0){nullable}, value 3000000000",
            "SELECT REPEAT('a', m) FROM tn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](38,0){nullable}, value 6000000000",
            "SELECT REPEAT('ab', n) FROM tn");
        assertAnswer("null", "SELECT GET([1,2,3], k) FROM tn");
        assertAnswer("null", "SELECT GET([1,2,3], n) FROM tn");
        assertShapedAnswer("REFUSED Invalid character code <ID19> in the CHR input",
            "SELECT CHAR(n * 10000000000000000) FROM tn");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT ARRAY_INSERT([1,2], n, 9) FROM tn");
        assertAnswer("[1,9,2]", "SELECT ARRAY_INSERT([1,2], k, 9) FROM tn");
    }

    @Test
    public void theSlotsBoundsAndSigns() {
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value -inf",
            "SELECT SUBSTR('abcdef', '-inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value -1e+30",
            "SELECT CHARINDEX('b', 'abc', -1e30::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value inf",
            "SELECT SUBSTR('abcdef', 'inf'::FLOAT::VARIANT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+18",
            "SELECT SUBSTR('abcdef', 1e18::FLOAT)");
        assertAnswer("", "SELECT SUBSTR('abcdef', 9.99e17::FLOAT)");
        assertAnswer("0", "SELECT CHARINDEX('b', 'abc', 999999999.4::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+09",
            "SELECT CHARINDEX('b', 'abc', 999999999.5::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value -1e+09",
            "SELECT CHARINDEX('b', 'abc', -999999999.5::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_SUBSTR('abc', 'b', 1, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_REPLACE('abc', 'b', 'x', 1, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_SUBSTR_ALL('abc', 'b', 1, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT REGEXP_COUNT('abc', 'b', 1e30::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_INSTR('abc', 'b', 1, 1, 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT REGEXP_SUBSTR('abc', '(b)', 1, 1, 'e', 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT RPAD('a', 'inf'::FLOAT, 'b')");
        answer("SELECT SPLIT_PART('a,b', ',', n) FROM (SELECT 3000000000 n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value 1e+30",
            "SELECT SPLIT_PART('a,b', ',', n) FROM (SELECT 1e30::FLOAT n)");
        answer("SELECT STRTOK('a b', ' ', n) FROM (SELECT 'inf'::FLOAT n)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT ARRAY_SLICE([1,2,3], n, 2) FROM (SELECT 3000000000 n)");
        answer("SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n)");
        answer("SELECT CHARINDEX('b', 'abc', n) FROM (SELECT 'inf'::FLOAT n)");
        answer("SELECT INSERT('abc', 1, 1, 'x') || CHAR(n) FROM (SELECT 'inf'::FLOAT n)");
    }

    @Test
    public void chrNamesTheLow64BitsOfACodeItRefuses() {
        assertAnswer("Invalid character code bfjaijhjhidfjebehie in the CHR input",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := CHAR(10000000000000000000000000); RETURN 'ok ' || "
                + "TRANSLATE(x, '0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, "
                + "'0123456789', 'abcdefghij'); END;$$");
        assertAnswer("Invalid character code -bfjaijhjhidfjebehie in the CHR input",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := CHAR(-10000000000000000000000000); RETURN 'ok ' || "
                + "TRANSLATE(x, '0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, "
                + "'0123456789', 'abcdefghij'); END;$$");
        assertAnswer("Invalid character code jccddhcadgifehhfiah in the CHR input",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := CHAR(9223372036854775807); RETURN 'ok ' || TRANSLATE(x, "
                + "'0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, '0123456789', "
                + "'abcdefghij'); END;$$");
        assertAnswer("ok A",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := CHAR(18446744073709551681); RETURN 'ok ' || TRANSLATE(x, "
                + "'0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, '0123456789', "
                + "'abcdefghij'); END;$$");
        assertAnswer("ok A",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := CHAR(9223372036854775873); RETURN 'ok ' || TRANSLATE(x, "
                + "'0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, '0123456789', "
                + "'abcdefghij'); END;$$");
        assertAnswer("ok A",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := CHR(4294967361); RETURN 'ok ' || TRANSLATE(x, '0123456789', "
                + "'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, '0123456789', 'abcdefghij'); "
                + "END;$$");
        assertAnswer("Bit index baaaaaaaaaaaaaaaaaaaaaaaaa out of range; Should be between a and bch",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := TO_VARCHAR(GETBIT(5, 10000000000000000000000000)); RETURN "
                + "'ok ' || TRANSLATE(x, '0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN "
                + "TRANSLATE(SQLERRM, '0123456789', 'abcdefghij'); END;$$");
        assertAnswer("Number out of representable range: type FIXED[SBe](di,a){not null}, value "
                + "baaaaaaaaaaaaaaaaaaaaaaaaa",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := REPEAT('a', 10000000000000000000000000); RETURN 'ok ' || "
                + "TRANSLATE(x, '0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, "
                + "'0123456789', 'abcdefghij'); END;$$");
        assertAnswer("Number out of representable range: type FIXED[SBe](ci,a){not null}, value daaaaaaaaa",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := REPEAT('ab', 1500000000); RETURN 'ok ' || TRANSLATE(x, "
                + "'0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, '0123456789', "
                + "'abcdefghij'); END;$$");
        assertAnswer("Number out of representable range: type FIXED[SBe](ci,a){not null}, value cbeheidgei",
            "EXECUTE IMMEDIATE $$BEGIN LET x VARCHAR := REPEAT('a', 2147483648); RETURN 'ok ' || TRANSLATE(x, "
                + "'0123456789', 'abcdefghij'); EXCEPTION WHEN OTHER THEN RETURN TRANSLATE(SQLERRM, '0123456789', "
                + "'abcdefghij'); END;$$");
    }

    @Test
    public void arrayInsertPadsAndBoundsItsArray() {
        assertAnswer("1000001", "SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 1000000, 9))");
        assertAnswer("REFUSED Result array of (ARRAY_INSERT) is too large",
            "SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 2147483647, 9))");
        assertAnswer("REFUSED Result array of (ARRAY_INSERT) is too large",
            "SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], -2147483648, 9))");
        assertAnswer("[9,undefined,1,2]", "SELECT ARRAY_INSERT([1,2], -3, 9)");
    }

    @Test
    public void theBooleanFunctionsReadEachOperandAsAWholeNumber() {
        engine.execute("CREATE OR REPLACE TABLE bt (f FLOAT, g FLOAT NOT NULL)");
        engine.execute("INSERT INTO bt VALUES ('nan'::FLOAT, 0.4::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT BOOLAND('nan'::FLOAT, 1), BOOLOR('nan'::FLOAT, 0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT BOOLAND('inf'::FLOAT, 1), BOOLOR('inf'::FLOAT, 0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value -inf",
            "SELECT BOOLAND('-inf'::FLOAT, 1), BOOLOR('-inf'::FLOAT, 0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT BOOLAND(1e30::FLOAT, 1), BOOLOR(1e30::FLOAT, 0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+18",
            "SELECT BOOLAND(1e18::FLOAT, 1), BOOLOR(1e18::FLOAT, 0)");
        assertAnswer("true|true", "SELECT BOOLAND(9.99e17::FLOAT, 1), BOOLOR(9.99e17::FLOAT, 0)");
        assertAnswer("false|false", "SELECT BOOLAND(0.4::FLOAT, 1), BOOLOR(0.4::FLOAT, 0)");
        assertAnswer("true|true", "SELECT BOOLAND(0.5::FLOAT, 1), BOOLOR(0.5::FLOAT, 0)");
        assertAnswer("true|true", "SELECT BOOLAND(-0.5::FLOAT, 1), BOOLOR(-0.5::FLOAT, 0)");
        assertAnswer("true|true", "SELECT BOOLAND(2.5::FLOAT, 1), BOOLOR(2.5::FLOAT, 0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT BOOLXOR('nan'::FLOAT, 0), BOOLNOT('nan'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT BOOLXOR('inf'::FLOAT, 0), BOOLNOT('inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value -inf",
            "SELECT BOOLXOR('-inf'::FLOAT, 0), BOOLNOT('-inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30",
            "SELECT BOOLXOR(1e30::FLOAT, 0), BOOLNOT(1e30::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+18",
            "SELECT BOOLXOR(1e18::FLOAT, 0), BOOLNOT(1e18::FLOAT)");
        assertAnswer("true|false", "SELECT BOOLXOR(9.99e17::FLOAT, 0), BOOLNOT(9.99e17::FLOAT)");
        assertAnswer("false|true", "SELECT BOOLXOR(0.4::FLOAT, 0), BOOLNOT(0.4::FLOAT)");
        assertAnswer("true|false", "SELECT BOOLXOR(0.5::FLOAT, 0), BOOLNOT(0.5::FLOAT)");
        assertAnswer("true|false", "SELECT BOOLXOR(-0.5::FLOAT, 0), BOOLNOT(-0.5::FLOAT)");
        assertAnswer("true|false", "SELECT BOOLXOR(2.5::FLOAT, 0), BOOLNOT(2.5::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan",
            "SELECT BOOLAND(1, 'nan'::FLOAT)");
        assertAnswer("false|false|true", "SELECT BOOLAND(0.4, 1), BOOLOR(0.4, 0), BOOLNOT(0.4)");
        assertAnswer("true|false", "SELECT BOOLAND(0.5, 1), BOOLNOT(0.5)");
        assertAnswer("false|true", "SELECT BOOLAND('0.4', 1), BOOLNOT('0.4')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value nan",
            "SELECT BOOLAND(f, 1) FROM bt");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value nan",
            "SELECT BOOLNOT(f) FROM bt");
        assertAnswer("false", "SELECT BOOLAND(g, 1) FROM bt");
        assertAnswer("true", "SELECT BOOLAND(10000000000000000000000000, 1)");
        assertAnswer("false", "SELECT BOOLNOT(10000000000000000000000000)");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value NaN",
            "SELECT BOOLAND(PARSE_JSON('NaN'), 1)");
        assertAnswer("false", "SELECT BOOLAND(0.4::FLOAT, 0.4::FLOAT)");
    }
}
