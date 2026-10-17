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
 * A structured OBJECT takes only string literals naming its fields as the keys OBJECT_PICK and OBJECT_DELETE name
 * (live-verified): any other key argument, an ARRAY, a VARIANT, an expression or a NULL, is refused by its argument
 * number, and a literal naming none of its fields, spelled exactly, refuses the statement whatever rows it reads.
 */
public class StructuredObjectKeyArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void aStructuredObjectTakesStringLiteralKeys() {
        assertCells(new String[][] {
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c', ['a'])",
                "REFUSED SQL compilation error: | Argument number 2 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c' || '')",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c', 'a' || '')",
                "REFUSED SQL compilation error: | Argument number 2 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), ['a'], 'c')",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), ['c'])",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_DELETE' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c' || '')",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_DELETE' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'b', 'c' || '')",
                "REFUSED SQL compilation error: | Argument number 2 for function 'OBJECT_DELETE' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), PARSE_JSON('\"c\"'))",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
        });
    }

    @Test
    public void aNullOrAnExpressionIsNoStringLiteral() {
        assertCells(new String[][] {
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), NULL)",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), NULL)",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_DELETE' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'a', NULL)",
                "REFUSED SQL compilation error: | Argument number 2 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), $$c$$)", "{\"c\":2}"},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c'::VARCHAR)",
                "REFUSED SQL compilation error: | Argument number 1 for function 'OBJECT_PICK' needs to be a "
                + "string literal."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'zz')",
                "REFUSED Function OBJECT_PICK: expected structured object to contain field zz but it did not."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), ('c'))", "{\"c\":2}"},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'b', 'zz')",
                "REFUSED Function OBJECT_DELETE: expected structured object to contain field zz but it did not."},
            {"SELECT OBJECT_PICK(o, 'c', 'a') FROM (SELECT {'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c "
                + "INT) AS o)",
                "{\"c\":2,\"a\":1}"},
        });
    }

    @Test
    public void aKeyNamesAFieldSpelledExactly() {
        assertCells(new String[][] {
            {"SELECT OBJECT_PICK(o, 'zz') FROM (SELECT {'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT) "
                + "AS o, 1 AS n) WHERE n > 5",
                "REFUSED Function OBJECT_PICK: expected structured object to contain field zz but it did not."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'A')",
                "REFUSED Function OBJECT_PICK: expected structured object to contain field A but it did not."},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'A')",
                "REFUSED Function OBJECT_DELETE: expected structured object to contain field A but it did not."},
            {"SELECT OBJECT_PICK(o, 'zz') FROM (SELECT {'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT) "
                + "AS o) LIMIT 0",
                "REFUSED Function OBJECT_PICK: expected structured object to contain field zz but it did not."},
            {"SELECT OBJECT_PICK({'A':1,'b':2}::OBJECT(\"A\" INT, b INT), 'A')", "{\"A\":1}"},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c', 'zz', 'a')",
                "REFUSED Function OBJECT_PICK: expected structured object to contain field zz but it did not."},
            {"SELECT OBJECT_PICK({'a':1,'b':'x','c':2}::OBJECT(a INT, b VARCHAR, c INT), 'c', 'c')", "{\"c\":2}"},
            {"SELECT OBJECT_PICK(NULL::OBJECT(a INT), 'zz')",
                "REFUSED Function OBJECT_PICK: expected structured object to contain field zz but it did not."},
        });
    }
}
