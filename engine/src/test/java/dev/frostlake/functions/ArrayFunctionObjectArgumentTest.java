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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An OBJECT-shaped value where an ARRAY function reads an ARRAY is refused by the argument types at compile time,
 * where the functions used to answer FALSE, NULL or a run-time error. The positions typed ARRAY refuse a plain
 * OBJECT, a structured one and a MAP; ARRAY_SIZE, ARRAY_CAT and ARRAY_COMPACT, which read a VARIANT, refuse only a
 * structured OBJECT and a MAP and take a plain OBJECT. A VARIANT is always accepted, whatever it holds. Every cell is
 * live-verified unless it says otherwise.
 */
public class ArrayFunctionObjectArgumentTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String scalarText(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void arrayContainsAndArraySizeRefuseAnObjectAsTheAccountDoes() {
        // Live-verified, sentence and position alike.
        assertTrue(refusal("SELECT ARRAY_CONTAINS(1, {'a': 1}::OBJECT)").contains("error line 1 at position 7\n"
            + "Invalid argument types for function 'ARRAY_CONTAINS': (NUMBER(1,0), OBJECT)"));
        assertTrue(refusal("SELECT ARRAY_CONTAINS(1::VARIANT, {'a': 1}::OBJECT(a NUMBER))").contains(
            "error line 1 at position 7\nInvalid argument types for function 'ARRAY_CONTAINS': (VARIANT,"
                + " OBJECT(a NUMBER(38,0)))"));
        assertTrue(refusal("SELECT ARRAY_CONTAINS(1, {'a': 'x'}::OBJECT(a VARCHAR))").contains(
            "Invalid argument types for function 'ARRAY_CONTAINS': (NUMBER(1,0), OBJECT(a VARCHAR(134217728)))"));
        assertTrue(refusal("SELECT ARRAY_SIZE({'a': 1}::OBJECT(a NUMBER))").contains("error line 1 at position 7\n"
            + "Invalid argument types for function 'ARRAY_SIZE': (OBJECT(a NUMBER(38,0)))"));
    }

    @Test
    public void aMapAndAColumnAreRefusedToo() {
        assertTrue(refusal("SELECT ARRAY_CONTAINS(1, {'a': 1}::MAP(VARCHAR, NUMBER))").contains(
            "Invalid argument types for function 'ARRAY_CONTAINS': (NUMBER(1,0), MAP(VARCHAR(134217728),"
                + " NUMBER(38,0)))"));
        assertTrue(refusal("SELECT ARRAY_SIZE({'a': 1}::MAP(VARCHAR, NUMBER))").contains(
            "Invalid argument types for function 'ARRAY_SIZE': (MAP(VARCHAR(134217728), NUMBER(38,0)))"));
        engine.execute("CREATE TABLE ot (o OBJECT, so OBJECT(a NUMBER))");
        assertTrue(refusal("SELECT ARRAY_CONTAINS(1, o) FROM ot").contains(
            "Invalid argument types for function 'ARRAY_CONTAINS': (NUMBER(1,0), OBJECT)"));
        assertTrue(refusal("SELECT ARRAY_SIZE(so) FROM ot").contains(
            "Invalid argument types for function 'ARRAY_SIZE': (OBJECT(a NUMBER(38,0)))"));
        // A compile-time refusal: the view is refused, not created over an empty table.
        assertTrue(refusal("CREATE VIEW ov AS SELECT ARRAY_SIZE(so) s FROM ot").contains(
            "Invalid argument types for function 'ARRAY_SIZE'"));
    }

    @Test
    public void theOtherArrayTypedPositionsRefuseAStructuredObjectAndAMap() {
        assertTrue(refusal("SELECT ARRAY_APPEND({'a': 1}::OBJECT(a NUMBER), 1)").contains(
            "Invalid argument types for function 'ARRAY_APPEND': (OBJECT(a NUMBER(38,0)), NUMBER(1,0))"));
        assertTrue(refusal("SELECT ARRAY_SLICE({'a': 1}::OBJECT(a NUMBER), 0, 1)").contains(
            "Invalid argument types for function 'ARRAY_SLICE': (OBJECT(a NUMBER(38,0)), NUMBER(1,0), NUMBER(1,0))"));
        assertTrue(refusal("SELECT ARRAYS_OVERLAP([1], {'a': 1}::MAP(VARCHAR, NUMBER))").contains(
            "Invalid argument types for function 'ARRAYS_OVERLAP': (ARRAY, MAP(VARCHAR(134217728), NUMBER(38,0)))"));
        assertTrue(refusal("SELECT ARRAY_POSITION(1, {'a': 1}::OBJECT(a NUMBER))").contains(
            "Invalid argument types for function 'ARRAY_POSITION': (NUMBER(1,0), OBJECT(a NUMBER(38,0)))"));
    }

    @Test
    public void everyArrayTypedPositionRefusesAPlainObject() {
        assertTrue(refusal("SELECT ARRAY_POSITION(1, {'a': 1}::OBJECT)").contains(
            "Invalid argument types for function 'ARRAY_POSITION': (NUMBER(1,0), OBJECT)"));
        assertTrue(refusal("SELECT ARRAY_POSITION(1::VARIANT, {'a': 1}::OBJECT)").contains(
            "Invalid argument types for function 'ARRAY_POSITION': (VARIANT, OBJECT)"));
        assertTrue(refusal("SELECT ARRAY_APPEND({'a': 1}::OBJECT, 1)").contains(
            "Invalid argument types for function 'ARRAY_APPEND': (OBJECT, NUMBER(1,0))"));
        assertTrue(refusal("SELECT ARRAY_DISTINCT({'a': 1}::OBJECT)").contains(
            "Invalid argument types for function 'ARRAY_DISTINCT': (OBJECT)"));
        for (final String call : new String[] {
            "ARRAY_PREPEND({'a': 1}::OBJECT, 1)", "ARRAY_INSERT({'a': 1}::OBJECT, 0, 1)",
            "ARRAY_SLICE({'a': 1}::OBJECT, 0, 1)", "ARRAYS_OVERLAP({'a': 1}::OBJECT, [1])",
            "ARRAY_FLATTEN({'a': 1}::OBJECT)", "ARRAY_MAX({'a': 1}::OBJECT)", "ARRAY_MIN({'a': 1}::OBJECT)",
            "ARRAY_REMOVE({'a': 1}::OBJECT, 1)", "ARRAY_REMOVE_AT({'a': 1}::OBJECT, 0)", "ARRAY_SORT({'a': 1}::OBJECT)",
            "ARRAY_INTERSECTION({'a': 1}::OBJECT, [1])", "ARRAY_EXCEPT({'a': 1}::OBJECT, [1])",
            "ARRAYS_TO_OBJECT(['k'], {'a': 1}::OBJECT)", "ARRAYS_ZIP({'a': 1}::OBJECT, [1])",
            "ARRAY_REVERSE({'a': 1}::OBJECT)"}) {
            final String function = call.substring(0, call.indexOf('('));
            final String refused = refusal("SELECT " + call);
            assertTrue(refused.contains("Invalid argument types for function '" + function + "': (")
                && refused.contains("OBJECT"), call + " read: " + refused);
        }
    }

    @Test
    public void theVariantReadingPositionsRefuseAStructuredObjectAndAMap() {
        assertTrue(refusal("SELECT ARRAY_CAT({'a': 1}::OBJECT(a NUMBER), [1])").contains(
            "Invalid argument types for function 'ARRAY_CAT': (OBJECT(a NUMBER(38,0)), ARRAY)"));
        assertTrue(refusal("SELECT ARRAY_CAT({'a': 1}::MAP(VARCHAR, NUMBER), [1])").contains(
            "Invalid argument types for function 'ARRAY_CAT': (MAP(VARCHAR(134217728), NUMBER(38,0)), ARRAY)"));
        assertTrue(refusal("SELECT ARRAY_COMPACT({'a': 1}::OBJECT(a NUMBER))").contains(
            "Invalid argument types for function 'ARRAY_COMPACT': (OBJECT(a NUMBER(38,0)))"));
        // ARRAY_CAT's right-hand argument follows its left one's rule; not checked live.
        assertTrue(refusal("SELECT ARRAY_CAT([1], {'a': 1}::MAP(VARCHAR, NUMBER))").contains(
            "Invalid argument types for function 'ARRAY_CAT': (ARRAY, MAP(VARCHAR(134217728), NUMBER(38,0)))"));
    }

    @Test
    public void theVariantReadingPositionsTakeAPlainObjectAndJudgeItWhenTheRowIsRead() {
        assertEquals("null", scalarText("SELECT ARRAY_SIZE({'a': 1}::OBJECT)"));
        assertTrue(refusal("SELECT ARRAY_CAT({'a': 1}::OBJECT, [1])").contains(
            "Left argument of ARRAY_CAT is not an array"));
        assertTrue(refusal("SELECT ARRAY_TO_STRING({'a': 1}::OBJECT, ',')").contains(
            "Left argument of string is not an array"));
        assertTrue(refusal("SELECT ARRAY_COMPACT({'a': 1}::OBJECT)").contains(
            "First argument of ARRAY_COMPACT is not an array"));
        assertEquals("null", scalarText("SELECT ARRAY_SIZE({'a': 1}::MAP(VARCHAR, NUMBER)::VARIANT)"));
    }

    @Test
    public void aVariantAnArrayAndAPlainObjectWhereTheAccountTakesOneAreAccepted() {
        assertEquals("null", scalarText("SELECT ARRAY_SIZE(PARSE_JSON('{\"a\":1}'))"));
        assertEquals("false", scalarText("SELECT ARRAY_CONTAINS(1, PARSE_JSON('{\"a\":1}'))").toLowerCase(Locale.ROOT));
        assertEquals("true", scalarText("SELECT ARRAY_CONTAINS(1, [1,2]::ARRAY(NUMBER))").toLowerCase(Locale.ROOT));
        assertEquals("2", scalarText("SELECT ARRAY_SIZE([1,2]::ARRAY(NUMBER))"));
        // Live-verified: ARRAY_SIZE reads a plain OBJECT as a VARIANT and answers NULL.
        assertEquals("null", scalarText("SELECT ARRAY_SIZE(OBJECT_CONSTRUCT())"));
    }
}
