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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exact arithmetic lives inside a SIGNED 128-BIT CARRIER, and the ceiling is the carrier's window —
 * never a digit count (live-verified throughout).
 *
 * <p>★ THE WINDOW IS [-2^127, 2^127-1], INCLUSIVE AND ASYMMETRIC. With {@code a} a NUMBER(38,0) of 38
 * nines, {@code a + 1} answers all 39 digits of 1e38, the sum reaching exactly 2^127-1 answers, the sum
 * reaching 2^127 refuses — and -2^127 itself, unreachable by any stored value, is still answered when
 * arithmetic produces it.
 *
 * <p>★ THE CHECK IS ON THE RAW SCALED INTEGER, NOT THE VALUE. {@code a * 0.15} is a comfortable 1.5e37,
 * and live still refuses it: the raw product — the two unscaled integers multiplied — is 1.5e39. The
 * raw-result refusal always reports the carrier view {@code (38,0)&#123;not null&#125;} and prints the
 * RAW as a six-significant-digit double, whatever the operands' scales ({@code e + e} over a scale-1
 * column prints 2e+38, the raw, where the value is 2e+37).
 *
 * <p>★ ADDITION CAN FAIL BEFORE ADDING. Aligning {@code a + 0.5} to the common scale must represent
 * {@code a} at scale 1 — raw 1e39 — so the REFUSED NUMBER IS THE OPERAND: the sentence carries the
 * aligned type, the operand's own digits printed plain, and the operand's declared nullability. MOD
 * shares this rescale step.
 *
 * <p>★ DIVISION REPORTS ITS DERIVED TYPE and prints the QUOTIENT'S VALUE — always in the double form,
 * even when the digits would fit the carrier — with the operands' nullability.
 *
 * <p>★ A LONG PRODUCT PAST 2^63 WIDENS instead of wrapping: live's only ceiling is the window, so
 * {@code d * 10} over eighteen nines answers 9999999999999999990, not a wrapped negative.
 *
 * <p>★ NEGATION AND ABS FOLLOW THE WINDOW TOO. Arithmetic can produce -2^127, and negating it — or
 * taking its ABS, subtracting it from zero, multiplying it by -1 — is the one exact step out of the
 * window, refused in the raw-result form; a 39-digit value still inside it ({@code -(a + 1)},
 * {@code ABS(-a - 1)}, {@code ABS} of 2^127 - 1) answers, so ABS is NOT held to its declared
 * NUMBER(38,0) the way the rounding functions are.
 *
 * <p>★ ONLY THE LOWER-SCALE OPERAND EVER RESCALES, and it is the one named whichever side it sits:
 * {@code e + a} names {@code a} exactly as {@code a + e} does. The functions share the operators'
 * steps — DIV0 and DIV0NULL refuse a quotient past the window in the division's form, MOD refuses
 * the rescale in the remainder's.
 *
 * <p>★ SUM AND AVG REFUSE AT THE CARRIER with a sentence of their own, "Value overflow in a SUM
 * aggregate", no prefix, at the row whose RAW running total leaves the window: a total reaching 1e38
 * or 2^127 - 1 answers, 2^127 does not, a scaled column is judged by its raw, a total that would come
 * back inside on a later row is refused all the same, and the window and DISTINCT spellings and AVG
 * say the same words. AVG's own division is the operator's: a sum of 1e38 over two rows is refused
 * as the (38,6) quotient 5e+37.
 */
public class ArithmeticCarrierOverflowTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ar (a NUMBER(38,0), d NUMBER(18,0), c NUMBER(20,0))");
        engine.execute("INSERT INTO ar VALUES (99999999999999999999999999999999999999, "
            + "999999999999999999, 99999999999999999999)");
        engine.execute("CREATE OR REPLACE TABLE nn (b NUMBER(38,0) NOT NULL)");
        engine.execute("INSERT INTO nn VALUES (99999999999999999999999999999999999999)");
        engine.execute("CREATE OR REPLACE TABLE ar3 (e NUMBER(38,1))");
        engine.execute("INSERT INTO ar3 VALUES (9999999999999999999999999999999999999.9)");
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void rawResultPastTheWindowRefuses() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 3e+38",
            refusal("SELECT a * 3 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38",
            refusal("SELECT a + a FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38",
            refusal("SELECT a - (0 - a) FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value -2e+38",
            refusal("SELECT -a - a FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+40",
            refusal("SELECT c * c FROM ar"));
    }

    @Test
    public void theWindowIsInclusiveAndAsymmetric() {
        assertEquals("100000000000000000000000000000000000000", answer("SELECT a + 1 FROM ar"));
        assertEquals("170141183460469231731687303715884105727",
            answer("SELECT a + 70141183460469231731687303715884105728 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 1.70141e+38",
            refusal("SELECT a + 70141183460469231731687303715884105729 FROM ar"));
        assertEquals("-170141183460469231731687303715884105728",
            answer("SELECT -a - 70141183460469231731687303715884105729 FROM ar"));
    }

    @Test
    public void theRawIsCheckedEvenWhenTheValueFits() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 1.5e+39",
            refusal("SELECT a * 1.5 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 1.5e+39",
            refusal("SELECT a * 0.15 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+39",
            refusal("SELECT a * 2.5 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38",
            refusal("SELECT e + e FROM ar3"));
        assertEquals("99999999999999999999999999999999999.999", answer("SELECT a * 0.001 FROM ar"));
    }

    @Test
    public void rescalingAnOperandCanRefuseBeforeTheArithmetic() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,1){nullable},"
            + " value 99999999999999999999999999999999999999",
            refusal("SELECT a + 0.5 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,2){nullable},"
            + " value 9999999999999999999999999999999999999.9",
            refusal("SELECT e - 0.05 FROM ar3"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,1){nullable},"
            + " value 99999999999999999999999999999999999999",
            refusal("SELECT a % 0.3 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,23){nullable},"
            + " value 999999999999999999",
            refusal("SELECT d + 0.00000000000000000000005 FROM ar"));
    }

    @Test
    public void negationAndAbsFollowTheWindow() {
        final String pastTheWindow =
            "Number out of representable range: type FIXED[SB16](38,0){not null}, value 1.70141e+38";
        assertEquals("-170141183460469231731687303715884105728",
            answer("SELECT -a - 70141183460469231731687303715884105729 FROM ar"));
        assertEquals(pastTheWindow, refusal("SELECT -(-a - 70141183460469231731687303715884105729) FROM ar"));
        assertEquals(pastTheWindow, refusal("SELECT ABS(-a - 70141183460469231731687303715884105729) FROM ar"));
        assertEquals(pastTheWindow, refusal("SELECT 0 - (-a - 70141183460469231731687303715884105729) FROM ar"));
        assertEquals(pastTheWindow, refusal("SELECT (-a - 70141183460469231731687303715884105729) * -1 FROM ar"));
        // Inside the window a 39-digit value answers, ABS included.
        assertEquals("-100000000000000000000000000000000000000", answer("SELECT -(a + 1) FROM ar"));
        assertEquals("100000000000000000000000000000000000000", answer("SELECT -(-a - 1) FROM ar"));
        assertEquals("100000000000000000000000000000000000000", answer("SELECT ABS(-a - 1) FROM ar"));
        assertEquals("170141183460469231731687303715884105727",
            answer("SELECT ABS(-a - 70141183460469231731687303715884105728) FROM ar"));
        assertEquals("-170141183460469231731687303715884105727",
            answer("SELECT -(a + 70141183460469231731687303715884105728) FROM ar"));
    }

    @Test
    public void theFunctionsShareTheOperatorsSteps() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){nullable}, value 1e+39",
            refusal("SELECT DIV0(a, 0.1) FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){nullable}, value 1e+39",
            refusal("SELECT DIV0NULL(a, 0.1) FROM ar"));
        assertEquals("99999999999999999999000.000000", answer("SELECT DIV0(c, 0.001) FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,1){nullable},"
            + " value 99999999999999999999999999999999999999", refusal("SELECT MOD(a, 0.5) FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,1){nullable},"
            + " value 99999999999999999999999999999999999999", refusal("SELECT MOD(a, e) FROM ar, ar3"));
        assertEquals("0", answer("SELECT TO_VARCHAR(MOD(a, 0.5::FLOAT)) FROM ar"));
        // The lower-scale operand is the one named, whichever side it sits.
        assertEquals("Number out of representable range: type FIXED[SB16](38,1){nullable},"
            + " value 99999999999999999999999999999999999999", refusal("SELECT e + a FROM ar, ar3"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,1){nullable},"
            + " value 99999999999999999999999999999999999999", refusal("SELECT e % a FROM ar, ar3"));
        // A rounding function keeps its declared range, and a minus keeps the column's nullability.
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){nullable},"
            + " value -100000000000000000000000000000000000000", refusal("SELECT ROUND(-a, -1) FROM ar"));
    }

    @Test
    public void sumAndAverageRefuseAtTheCarrier() {
        final String overflow = "Value overflow in a SUM aggregate";
        engine.execute("CREATE OR REPLACE TABLE s1 (a NUMBER(38,0))");
        engine.execute("INSERT INTO s1 VALUES (99999999999999999999999999999999999999), (1)");
        engine.execute("CREATE OR REPLACE TABLE s2 (a NUMBER(38,0))");
        engine.execute("INSERT INTO s2 VALUES (99999999999999999999999999999999999999),"
            + " (70141183460469231731687303715884105728)");
        engine.execute("CREATE OR REPLACE TABLE s3 (a NUMBER(38,0))");
        engine.execute("INSERT INTO s3 VALUES (99999999999999999999999999999999999999),"
            + " (70141183460469231731687303715884105729)");
        engine.execute("CREATE OR REPLACE TABLE s4 (a NUMBER(38,0))");
        engine.execute("INSERT INTO s4 VALUES (-99999999999999999999999999999999999999),"
            + " (-99999999999999999999999999999999999999)");
        engine.execute("CREATE OR REPLACE TABLE s5 (f NUMBER(38,2))");
        engine.execute("INSERT INTO s5 VALUES (999999999999999999999999999999999999.99),"
            + " (999999999999999999999999999999999999.99)");
        engine.execute("CREATE OR REPLACE TABLE s6 (a NUMBER(38,0))");
        engine.execute("INSERT INTO s6 VALUES (99999999999999999999999999999999999999),"
            + " (99999999999999999999999999999999999999), (-99999999999999999999999999999999999999)");
        assertEquals("100000000000000000000000000000000000000", answer("SELECT SUM(a) FROM s1"));
        assertEquals("170141183460469231731687303715884105727", answer("SELECT SUM(a) FROM s2"));
        assertEquals(overflow, refusal("SELECT SUM(a) FROM s3"));
        assertEquals(overflow, refusal("SELECT SUM(a) FROM s4"));
        assertEquals(overflow, refusal("SELECT SUM(f) FROM s5"));
        assertEquals(overflow, refusal("SELECT SUM(a) FROM s6"));
        assertEquals(overflow, refusal("SELECT SUM(a) OVER () FROM s3"));
        assertEquals(overflow, refusal("SELECT SUM(DISTINCT a) FROM s3"));
        assertEquals(overflow, refusal("SELECT AVG(a) FROM s3"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){nullable}, value 5e+37",
            refusal("SELECT AVG(a) FROM s1"));
        // A FLOAT sum has no window to leave.
        assertEquals("1.70141183460469e+38", answer("SELECT TO_VARCHAR(SUM(a::FLOAT)) FROM s3"));
    }

    @Test
    public void divisionReportsItsDerivedTypeAndTheQuotient() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){nullable}, value 2e+38",
            refusal("SELECT a / 0.5 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){nullable}, value 3.33333e+37",
            refusal("SELECT a / 3 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){nullable}, value 1.11111e+38",
            refusal("SELECT a / 0.9 FROM ar"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,6){not null}, value 2e+38",
            refusal("SELECT b / 0.5 FROM nn"));
        assertEquals("99999999999999999999000.000000", answer("SELECT c / 0.001 FROM ar"));
    }

    @Test
    public void aNotNullColumnAndLiteralsSpellNotNull() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38",
            refusal("SELECT b + b FROM nn"));
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38",
            refusal("SELECT 99999999999999999999999999999999999999"
                + " + 99999999999999999999999999999999999999"));
    }

    @Test
    public void intermediatesAreChecked() {
        assertEquals("Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38",
            refusal("SELECT a + a - a FROM ar"));
    }

    @Test
    public void longArithmeticWidensInsteadOfWrapping() {
        assertEquals("9999999999999999990", answer("SELECT d * 10 FROM ar"));
        assertEquals("98999999999999999901", answer("SELECT d * 99 FROM ar"));
    }

    @Test
    public void approximateArithmeticIsExempt() {
        // A FLOAT operand leaves the exact carrier entirely — no window applies.
        assertEquals(1, engine.executeQuery("SELECT a::FLOAT * 3 FROM ar").getRows().size());
    }
}
