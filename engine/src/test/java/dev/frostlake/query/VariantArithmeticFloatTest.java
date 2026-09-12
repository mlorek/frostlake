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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Arithmetic that touches a VARIANT produces a FLOAT, whatever the member holds. A DECIMAL member
 * already did, because it is still a VariantValue when the operator sees it; an INTEGER member arrives
 * EXTRACTED, as a plain Long, so the {@code instanceof Number} fast path computed it exactly and
 * answered 14 where live answers 14.0.
 *
 * <p>The value alone cannot tell the two apart — a Long from a VARIANT and a Long from an INT column
 * are the same object — so the VARIANT-ness is read from the STATIC type, the same channel that
 * restores a variant for a comparison.
 *
 * <p><b>The controls are half the point.</b> An ordinary INT or NUMBER column must keep its exact
 * arithmetic, and this change is precisely the kind that could float it by accident: {@code i + 2} is
 * still 9 and {@code i / 2} still 3.500000. Seven such cells are asserted below, and the vendor suite
 * was run on its own before the gate for the same reason — arithmetic on a VARIANT member is what the
 * loaders do all day.
 *
 * <p>Two neighbouring cells are deliberately NOT asserted, both tracked separately: a division's
 * DOUBLE renders at full precision here and to ten decimals on live, and {@code -src:score} is refused
 * because the unary minus binds tighter than the colon path and negates the whole object.
 */
public class VariantArithmeticFloatTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vc (src VARIANT, i INT, n NUMBER(10,2))");
        engine.execute("INSERT INTO vc SELECT PARSE_JSON('{\"score\": 7.5, \"n\": 7}'), 7, 2.50");
    }

    /** The expression's single value, printed. */
    private String value(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr + " AS c FROM vc");
        return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
    }

    /** An INTEGER member floats, which is the defect this fixes. */
    @Test
    public void anIntegerMemberFloats() {
        assertEquals("9.0", value("src:n + 2"));
        assertEquals("9.0", value("2 + src:n"));
        assertEquals("5.0", value("src:n - 2"));
        assertEquals("-5.0", value("2 - src:n"));
        assertEquals("14.0", value("src:n * 2"));
        assertEquals("14.0", value("2 * src:n"));
        assertEquals("1.0", value("src:n % 2"));
        assertEquals("2.0", value("2 % src:n"));
    }

    /** A DECIMAL member already floated, and must keep doing so. */
    @Test
    public void aDecimalMemberIsUnchanged() {
        assertEquals("9.5", value("src:score + 2"));
        assertEquals("5.5", value("src:score - 2"));
        assertEquals("15.0", value("src:score * 2"));
        assertEquals("18.75", value("src:score * 2.5"));
        assertEquals("1.5", value("src:score % 2"));
        assertEquals("2.0", value("2 % src:score"));
    }

    /** Two members against each other float too, in every combination. */
    @Test
    public void twoMembersFloatTogether() {
        assertEquals("49.0", value("src:n * src:n"));
        assertEquals("52.5", value("src:score * src:n"));
        assertEquals("56.25", value("src:score * src:score"));
        assertEquals("14.5", value("src:score + src:n"));
    }

    /**
     * The CONTROLS: an ordinary column's arithmetic must not float. These are what stop the fix from
     * being "make all arithmetic double".
     */
    @Test
    public void anOrdinaryColumnKeepsItsExactArithmetic() {
        assertEquals("9", value("i + 2"));
        assertEquals("14", value("i * 2"));
        assertEquals("5", value("i - 2"));
        assertEquals("1", value("i % 2"));
        assertEquals("4.50", value("n + 2"));
        assertEquals("9.50", value("i + n"));
        assertEquals("14", value("7 * 2"), "two literals are not variants either");
    }

    /** The reads that never went through arithmetic keep their own answers. */
    @Test
    public void theNonArithmeticReadsAreUnchanged() {
        assertEquals("DECIMAL", value("TYPEOF(src:score)"));
        assertEquals("INTEGER", value("TYPEOF(src:n)"));
        assertEquals("7.5", value("SUM(src:score)"));
        assertEquals("7.50", value("src:score::NUMBER(10,2)"));
    }
}
