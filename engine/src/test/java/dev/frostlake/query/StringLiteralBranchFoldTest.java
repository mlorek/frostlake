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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conditional's branch fold, where a STRING LITERAL stands beside a number. It used to contribute a
 * flat NUMBER(18,5) whatever it said; it now contributes exactly what the same number written WITHOUT
 * quotes contributes, which is what live folds it as:
 *
 * <pre>
 *   COALESCE(n2, '5.12345')        NUMBER(13,5)      was NUMBER(18,5)
 *   COALESCE(n2,  5.12345 )        NUMBER(13,5)      the unquoted twin — identical, and always was
 *   COALESCE(n2, '123456789012')   NUMBER(14,2)      was NUMBER(18,5)
 * </pre>
 *
 * <p>A string COLUMN keeps the flat NUMBER(18,5): it has no literal text to measure, so live cannot
 * narrow it either. That column-versus-literal split is why the fold needed the branch EXPRESSION and
 * not just its type.
 *
 * <p><b>The old answer was right for exactly one shape</b>, and that is what hid it: with a one-digit
 * literal like {@code '5'}, folding NUMBER(18,5) against NUMBER(10,2) lands on the same NUMBER(10,2)
 * the correct rule gives. Measuring only that cell leaves the rule underdetermined, which is why the
 * cells below all carry more decimals than the column does.
 *
 * <p>The unquoted twin of each literal cell is asserted beside it. They agreed before this change and
 * must keep agreeing — they are what says the rule is "a string literal is a number literal" rather
 * than some separate string rule.
 */
public class StringLiteralBranchFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Scales 1, 2 and 4 — never 0, which renders identically folded or not and hides the error.
        engine.execute("CREATE OR REPLACE TABLE fv (n1 NUMBER(3,1), n2 NUMBER(10,2),"
            + " n4 NUMBER(12,4), i INT, vn VARCHAR(20), flag BOOLEAN)");
        engine.execute("INSERT INTO fv VALUES (0.7, 123.45, 1.2345, 7, '123', TRUE)");
    }

    /** The declared type of the expression's result column. */
    private String typeOf(final String expr) {
        final DataType t = engine.executeQuery("SELECT " + expr + " AS c FROM fv")
            .getColumns().get(0).getDataType();
        if (t == null) {
            return "null";
        }
        if (t instanceof NumericType && !"FLOAT".equals(t.getName())) {
            return t.getName() + "(" + ((NumericType) t).getPrecision() + ","
                + ((NumericType) t).getScale() + ")";
        }
        if (t instanceof StringType) {
            return t.getName() + "(" + ((StringType) t).getMaxLength() + ")";
        }
        return t.getName();
    }

    /** A string literal folds as its own measured width, in every conditional spelling. */
    @Test
    public void aStringLiteralFoldsAsItsOwnWidth() {
        assertEquals("NUMBER(13,5)", typeOf("COALESCE(n2, '5.12345')"));
        assertEquals("NUMBER(13,5)", typeOf("GREATEST(n2, '5.12345')"));
        assertEquals("NUMBER(13,5)", typeOf("IFF(flag, n2, '5.12345')"));
        assertEquals("NUMBER(14,2)", typeOf("COALESCE(n2, '123456789012')"));
        assertEquals("NUMBER(7,5)", typeOf("COALESCE(n1, '5.12345')"));
    }

    /** The UNQUOTED twin of each, which agreed before and pins the rule's shape. */
    @Test
    public void theUnquotedTwinIsIdentical() {
        assertEquals(typeOf("COALESCE(n2, 5.12345)"), typeOf("COALESCE(n2, '5.12345')"));
        assertEquals(typeOf("COALESCE(n2, 123456789012)"), typeOf("COALESCE(n2, '123456789012')"));
        assertEquals(typeOf("COALESCE(n1, 5.12345)"), typeOf("COALESCE(n1, '5.12345')"));
    }

    /** A string COLUMN has no text to measure and keeps the flat width. */
    @Test
    public void aStringColumnKeepsTheFlatWidth() {
        assertEquals("NUMBER(18,5)", typeOf("COALESCE(vn, n2)"));
        assertEquals("NUMBER(18,5)", typeOf("COALESCE(n2, vn)"));
    }

    /**
     * The shape the vendor suite's loaders actually write — a scaled column beside a scale-0 integer
     * literal. It agreed before and must keep agreeing; this is the fold those loaders depend on, and
     * it is why the literal rule above had to be narrowed to literals that MEASURE as something other
     * than what the column already carries.
     */
    @Test
    public void theLoaderShapeIsUnchanged() {
        assertEquals("NUMBER(3,1)", typeOf("NVL(n1, 0)"));
        assertEquals("NUMBER(10,2)", typeOf("NVL(n2, 0)"));
        assertEquals("NUMBER(12,4)", typeOf("NVL(n4, 0)"));
        assertEquals("NUMBER(10,2)", typeOf("COALESCE(n2, 0)"));
        assertEquals("NUMBER(10,2)", typeOf("IFF(flag, n2, 0)"));
        assertEquals("NUMBER(10,2)", typeOf("CASE WHEN flag THEN n2 ELSE 0 END"));
        assertEquals("NUMBER(10,2)", typeOf("NVL(COALESCE(n2, 0), 0)"));
        assertEquals("NUMBER(22,2)", typeOf("SUM(NVL(n2, 0))"));
    }

    /** A one-digit literal still lands where it always did — the cell that hid the error. */
    @Test
    public void theOneDigitLiteralIsUnchanged() {
        assertEquals("NUMBER(10,2)", typeOf("GREATEST(n2, '5')"));
        assertEquals("NUMBER(10,2)", typeOf("COALESCE(n2, '5')"));
        assertEquals("NUMBER(10,2)", typeOf("COALESCE(n2, '5.1')"));
    }

    /** Column-against-column folds are untouched by the literal rule. */
    @Test
    public void theColumnFoldsAreUnchanged() {
        assertEquals("NUMBER(12,4)", typeOf("COALESCE(n2, n4)"));
        assertEquals("NUMBER(12,4)", typeOf("COALESCE(n4, n2)"));
        assertEquals("NUMBER(38,2)", typeOf("COALESCE(n2, i)"));
        assertEquals("NUMBER(38,2)", typeOf("COALESCE(i, n2)"));
    }
}
