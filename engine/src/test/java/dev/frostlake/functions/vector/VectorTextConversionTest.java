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
package dev.frostlake.functions.vector;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VECTOR has NO CONVERSION TO TEXT, though it renders perfectly well.
 *
 * <p>★ THE TWO FACTS ARE NOT IN TENSION, and telling them apart is the whole task: selecting a vector
 * prints its elements on both engines, but every statement that asks for that text AS A STRING is
 * refused live — TO_VARCHAR, TO_CHAR, every cast to a string type, and both spellings of
 * concatenation. Frostlake handed back the display text for all of them, so a vector silently became
 * a string anywhere one was wanted.
 *
 * <p>★ TWO SENTENCE FAMILIES, chosen by WHICH construct asked:
 * <ul>
 *   <li>the CONVERSIONS quote the whole call back — {@code invalid type [TO_VARCHAR(VT.V)] for
 *       parameter 'TO_VARCHAR'} — and carry NO position. The parameter named is the conversion the
 *       written spelling routes through, so TO_CHAR reports itself while a CAST, whatever string type
 *       it names, reports TO_VARCHAR;</li>
 *   <li>the CONCATENATIONS list argument types and DO carry a position — anchored on the operator's
 *       own offset — and spell the vector's full declared type, element type and dimension included.</li>
 * </ul>
 *
 * <p>★ THE CALL IS ECHOED FROM THE RESOLVED PLAN, not the source text: a column comes back qualified
 * and upper-cased, and a cast prints the WIDTH it resolved to — the 128MB unknown length when the
 * spelling carried none. Sibling of the rule the structured types already follow.
 */
public class VectorTextConversionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vt (v VECTOR(FLOAT, 3), i VECTOR(INT, 2), s VARCHAR)");
        engine.execute("INSERT INTO vt SELECT [1.0, 0.1234567, 3.5]::VECTOR(FLOAT, 3),"
            + " [7, 8]::VECTOR(INT, 2), 'x'");
    }

    /** One scalar, as text, or the refusal. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The display path is untouched — the cell that keeps the refusals from being a blanket ban. */
    @Test
    public void avectorStillRenders() {
        assertEquals("[1.000000,0.123457,3.500000]", answer("SELECT v FROM vt"),
            "selecting a vector prints its elements; only asking for that text as a STRING refuses");
        assertEquals("VECTOR(FLOAT, 3)[LOB]", answer("SELECT SYSTEM$TYPEOF(v) FROM vt"));
    }

    /** ★ The CONVERSION family: the whole call quoted, no position, the routed parameter named. */
    @Test
    public void theconversionsQuoteTheWholeCall() {
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VT.V)] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v) FROM vt"),
            "the echo is the RESOLVED plan — the column comes back qualified and upper-cased");
        assertEquals("SQL compilation error:|invalid type [TO_CHAR(VT.V)] for parameter 'TO_CHAR'",
            answer("SELECT TO_CHAR(v) FROM vt"),
            "★ TO_CHAR names ITSELF, so the parameter follows the written spelling");
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VT.V, 'x')] for parameter"
            + " 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(v, 'x') FROM vt"),
            "★ the ARGUMENT beats the ARITY: the format is echoed rather than complained about");
        assertEquals("SQL compilation error:|invalid type [TO_VARCHAR(VT.I)] for parameter 'TO_VARCHAR'",
            answer("SELECT TO_VARCHAR(i) FROM vt"),
            "an INT vector is refused the same way, and the echo names no element type");
    }

    /** ★ A CAST reports TO_VARCHAR whatever string type it was written as, and echoes its WIDTH. */
    @Test
    public void thecastsRouteThroughToVarchar() {
        assertEquals("SQL compilation error:|invalid type [CAST(VT.V AS VARCHAR(134217728))] for"
            + " parameter 'TO_VARCHAR'",
            answer("SELECT v::VARCHAR FROM vt"),
            "an unsized cast resolves to the 128MB unknown length, which the echo prints");
        assertEquals("SQL compilation error:|invalid type [CAST(VT.V AS VARCHAR(50))] for parameter"
            + " 'TO_VARCHAR'",
            answer("SELECT v::VARCHAR(50) FROM vt"),
            "★ and a sized one keeps its own width — the echo is resolved, not invented");
    }

    /** ★ The CONCATENATION family: argument types listed, positioned on the operator itself. */
    @Test
    public void theconcatenationsListTypesAndCarryAPosition() {
        assertEquals("SQL compilation error: error line 1 at position 11|Invalid argument types for"
            + " function '||': (VARCHAR(1), VECTOR(FLOAT, 3))",
            answer("SELECT 'x' || v FROM vt"),
            "the full declared type is spelled out, dimension included");
        assertEquals("SQL compilation error: error line 1 at position 9|Invalid argument types for"
            + " function '||': (VECTOR(FLOAT, 3), VARCHAR(1))",
            answer("SELECT v || 'x' FROM vt"),
            "★ the position follows the OPERATOR, so the other order moves it");
        assertEquals("SQL compilation error: error line 1 at position 9|Invalid argument types for"
            + " function '||': (VECTOR(FLOAT, 3), VECTOR(FLOAT, 3))",
            answer("SELECT v || v FROM vt"),
            "two vectors do not join each other either");
        assertEquals("SQL compilation error: error line 1 at position 20|Invalid argument types for"
            + " function '||': (VARCHAR(1), VECTOR(FLOAT, 3))",
            answer("SELECT s, s, s, 'x' || v FROM vt"),
            "★ and moving the operator along the line moves the position with it");
        assertEquals("SQL compilation error: error line 1 at position 11|Invalid argument types for"
            + " function '||': (VARCHAR(1), VECTOR(INT, 2))",
            answer("SELECT 'x' || i FROM vt"),
            "an INT vector spells its own element type and dimension");
    }

    /** ★ CONCAT is the same refusal under its own name, anchored on the function instead. */
    @Test
    public void thefunctionSpellingNamesItself() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'CONCAT': (VECTOR(FLOAT, 3), VARCHAR(1))",
            answer("SELECT CONCAT(v, 'x') FROM vt"),
            "the operator and the function are one operation reported under two names");
    }
}
