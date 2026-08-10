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
 * A BINARY concatenates only with another BINARY. Mixed with a string — or anything else — the
 * expression is refused rather than converted, and both DECLARED types are named in the order written:
 * {@code Invalid argument types for function '||': (BINARY(4), VARCHAR(4))}.
 *
 * <p>Live-verified. The refusal is reached from the STATIC types, which is what makes the declared
 * widths available: the runtime value in a BINARY(4) column carries its own length, so a value-based
 * check could only ever report BINARY(1) for a one-byte value.
 */
public class BinaryConcatArgumentTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE bc_s (s VARCHAR(4), bn BINARY(4), bn2 BINARY(9), n NUMBER(5,1))");
    }

    private String refusalOf(final String expression) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT " + expression + " AS c FROM bc_s");
            }
        }, expression);
        return ex.getMessage();
    }

    /** A binary beside a string is refused, both declared types named in written order. */
    @Test
    public void aBinaryBesideAStringIsRefused() {
        assertTrue(refusalOf("bn || s").contains(
            "Invalid argument types for function '||': (BINARY(4), VARCHAR(4))"),
            refusalOf("bn || s"));
        assertTrue(refusalOf("s || bn").contains(
            "Invalid argument types for function '||': (VARCHAR(4), BINARY(4))"),
            refusalOf("s || bn"));
    }

    /** The refusal is a COMPILATION error, so it lands even though the table has no rows. */
    @Test
    public void theRefusalIsAtCompileTime() {
        assertTrue(refusalOf("bn || s").startsWith("SQL compilation error:"), refusalOf("bn || s"));
    }

    /** A binary literal is judged the same way. */
    @Test
    public void aBinaryLiteralIsJudgedToo() {
        assertTrue(refusalOf("bn || 'a'").contains(
            "Invalid argument types for function '||': (BINARY(4), VARCHAR(1))"),
            refusalOf("bn || 'a'"));
    }

    /** Two binaries concatenate legally. */
    @Test
    public void twoBinariesConcatenate() {
        engine.executeQuery("SELECT bn || bn2 AS c FROM bc_s");
    }

    /** And a string beside a non-binary still converts rather than refusing. */
    @Test
    public void aNonBinaryOperandStillConverts() {
        engine.executeQuery("SELECT n || s AS c FROM bc_s");
        engine.executeQuery("SELECT s || n AS c FROM bc_s");
    }
}
