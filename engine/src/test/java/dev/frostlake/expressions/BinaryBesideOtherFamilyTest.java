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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BINARY beside another family is REFUSED, not widened — the direction of fidelity bug that matters
 * most, because Frostlake accepting what the account rejects is a green test suite that proves nothing.
 * Every one of these used to be accepted and typed TEXT.
 *
 * <p>The two constructs refuse in DIFFERENT WORDS, and both are live's:
 *
 * <pre>
 *   IFF(c, b, s)   Can not convert parameter 'QX.S' of type [VARCHAR(10)] into expected type [BINARY(4)]
 *   CONCAT(b, s)   error line 1 at position N Invalid argument types for function 'CONCAT': (BINARY(4), VARCHAR(10))
 * </pre>
 *
 * <p>The conditional names the OPERAND — qualified by the table it came from — and carries no position;
 * CONCAT lists the TYPES and is positioned at the call, exactly as the {@code ||} spelling is. The
 * expected type is whichever family the FIRST branch established, so reversing the operands reverses
 * the sentence.
 */
public class BinaryBesideOtherFamilyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE qx (b BINARY(4), s VARCHAR(10), n NUMBER, d DATE,"
            + " bo BOOLEAN, v VARIANT)");
        engine.execute("INSERT INTO qx SELECT TO_BINARY('4142'), 'abc', 1, '2026-01-01', TRUE,"
            + " TO_VARIANT(1)");
    }

    /** The refusal a statement raises, flattened, or "accepted" when there was none. */
    private String refusalOf(final String expression) {
        try {
            engine.execute("CREATE OR REPLACE VIEW qx_v AS SELECT " + expression + " AS c FROM qx");
        } catch (final RuntimeException refused) {
            return refused.getMessage().replace('\n', ' ');
        }
        return "accepted";
    }

    private void refusesConversion(final String expression, final String parameter,
                                   final String actual, final String expected) {
        final String sentence = "Can not convert parameter '" + parameter + "' of type [" + actual
            + "] into expected type [" + expected + "]";
        final String got = refusalOf(expression);
        assertTrue(got.contains(sentence), expression + "\nexpected: " + sentence + "\nbut got: " + got);
        assertTrue(got.contains("SQL compilation error"),
            expression + " must refuse at COMPILE time, or a view over it is created: " + got);
    }

    /** Every conditional refuses a binary beside another family, naming the operand and both types. */
    @Test
    public void aConditionalRefusesAMixedPair() {
        refusesConversion("IFF(n = 1, b, s)", "QX.S", "VARCHAR(10)", "BINARY(4)");
        refusesConversion("COALESCE(b, s)", "QX.S", "VARCHAR(10)", "BINARY(4)");
        refusesConversion("NVL(b, s)", "QX.S", "VARCHAR(10)", "BINARY(4)");
        refusesConversion("IFNULL(b, s)", "QX.S", "VARCHAR(10)", "BINARY(4)");
        refusesConversion("CASE WHEN n = 1 THEN b ELSE s END", "QX.S", "VARCHAR(10)", "BINARY(4)");
    }

    /** Whichever family sits beside it. */
    @Test
    public void everyOtherFamilyIsRefusedToo() {
        refusesConversion("IFF(n = 1, b, n)", "QX.N", "NUMBER(38,0)", "BINARY(4)");
        refusesConversion("IFF(n = 1, b, d)", "QX.D", "DATE", "BINARY(4)");
        refusesConversion("IFF(n = 1, b, bo)", "QX.BO", "BOOLEAN", "BINARY(4)");
        refusesConversion("IFF(n = 1, b, v)", "QX.V", "VARIANT", "BINARY(4)");
    }

    /**
     * The EXPECTED type is the FIRST branch's, so reversing the operands reverses the sentence — and
     * the operand NAMED is the one that does not fit, which in this order is the SECOND branch rather
     * than the one the fold stops on.
     */
    @Test
    public void theExpectedTypeIsTheFirstBranchs() {
        refusesConversion("IFF(n = 1, s, b)", "QX.B", "BINARY(4)", "VARCHAR(10)");
        refusesConversion("COALESCE(s, b)", "QX.B", "BINARY(4)", "VARCHAR(10)");
        refusesConversion("CASE WHEN n = 1 THEN s ELSE b END", "QX.B", "BINARY(4)", "VARCHAR(10)");
    }

    /** A literal is named by its own text, in doubled quotes, with the length it measures. */
    @Test
    public void aLiteralIsNamedByItsText() {
        refusesConversion("IFF(n = 1, b, 'abc')", "'abc'", "VARCHAR(3)", "BINARY(4)");
        refusesConversion("COALESCE(b, 'abc')", "'abc'", "VARCHAR(3)", "BINARY(4)");
    }

    /** CONCAT refuses in the OPERATOR's words instead, positioned at the call. */
    @Test
    public void concatRefusesWithTheArgumentTypeList() {
        final String got = refusalOf("CONCAT(b, s)");
        assertTrue(got.contains("Invalid argument types for function 'CONCAT': (BINARY(4), VARCHAR(10))"),
            "CONCAT gave: " + got);
        assertTrue(got.contains("error line 1 at position"), "CONCAT must be positioned: " + got);
        assertTrue(got.contains("SQL compilation error"), "CONCAT must refuse at compile time: " + got);
    }

    /** In either order, and for the other families. */
    @Test
    public void concatRefusesWhicheverSideTheBinaryIsOn() {
        assertTrue(refusalOf("CONCAT(s, b)")
            .contains("Invalid argument types for function 'CONCAT': (VARCHAR(10), BINARY(4))"));
        assertTrue(refusalOf("CONCAT(b, n)")
            .contains("Invalid argument types for function 'CONCAT': (BINARY(4), NUMBER(38,0))"));
        assertTrue(refusalOf("CONCAT(b, d)")
            .contains("Invalid argument types for function 'CONCAT': (BINARY(4), DATE)"));
    }

    /** And an all-binary call still works — the refusal must not swallow the legal shape. */
    @Test
    public void anAllBinaryCallIsStillAccepted() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE VIEW bx_v AS SELECT CONCAT(b, s) AS c FROM qx");
            }
        });
        assertTrue(refusalOf("CONCAT(b, b)").equals("accepted"), "CONCAT(b, b) must still be legal");
        assertTrue(refusalOf("IFF(n = 1, b, b)").equals("accepted"), "IFF over two binaries is legal");
    }
}
