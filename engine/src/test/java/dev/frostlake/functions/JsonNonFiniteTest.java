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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NaN and the infinities — the numbers JSON has no literal for and Snowflake reads anyway.
 *
 * <p>★ ONE RULE BEHIND ALL OF IT: a JSON keyword is matched CASE-INSENSITIVELY. That covers
 * true/false/null/undefined as well as nan/inf/infinity, so {@code True}, {@code NAN} and {@code Inf} are
 * all read. A word that is not a keyword keeps the {@code unknown keyword} refusal.
 *
 * <p>★ THE SIGN IS ASYMMETRIC, and measured rather than reasoned: {@code +NaN} is read and {@code -NaN}
 * is REFUSED, while both signs work on the infinities.
 *
 * <p>★ NaN IS NOT IEEE HERE. It EQUALS ITSELF and sorts ABOVE everything, so ORDER BY puts it last —
 * which is exactly Java's Double.compare total order, while {@code ==} on a primitive double is not.
 *
 * <p>★ 1e999 WAS A WRITER PROBLEM, NOT A READER ONE. The overflow already produced a DOUBLE holding
 * infinity; it came back as the STRING "Infinity" because a standard JSON writer QUOTES a non-finite
 * number. Snowflake writes it bare, and the reader knows the bare word, so the text round-trips.
 */
public class JsonNonFiniteTest extends BaseDatabaseTest {

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private String doc(final String text) {
        return one("SELECT PARSE_JSON('" + text + "')");
    }

    private String yesNo(final String predicate) {
        return one("SELECT IFF(" + predicate + ", 'yes', 'no')");
    }

    private void refuses(final String sql, final String fragment) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(e.getMessage()).contains(fragment),
            "expected a refusal naming '" + fragment + "', got: " + e.getMessage());
    }

    @Test
    void everyCasingOfNanIsRead() {
        assertEquals("NaN", doc("NaN"));
        assertEquals("NaN", doc("nan"));
        assertEquals("NaN", doc("NAN"));
        assertEquals("NaN", doc("Nan"));
        assertEquals("NaN", doc("nAn"));
    }

    @Test
    void everyCasingOfInfinityIsRead() {
        assertEquals("Infinity", doc("Infinity"));
        assertEquals("Infinity", doc("infinity"));
        assertEquals("Infinity", doc("INFINITY"));
        assertEquals("Infinity", doc("inf"));
        assertEquals("Infinity", doc("INF"));
        assertEquals("Infinity", doc("Inf"));
    }

    @Test
    void theSignIsAsymmetric() {
        assertEquals("NaN", doc("+NaN"));
        assertEquals("Infinity", doc("+inf"));
        assertEquals("Infinity", doc("+Infinity"));
        assertEquals("-Infinity", doc("-inf"));
        assertEquals("-Infinity", doc("-Infinity"));
        refuses("SELECT PARSE_JSON('-NaN')", "unknown keyword");
    }

    @Test
    void aWordThatIsNotAKeywordIsStillRefused() {
        refuses("SELECT PARSE_JSON('infin')", "unknown keyword");
        refuses("SELECT PARSE_JSON('Infinit')", "unknown keyword");
        refuses("SELECT PARSE_JSON('None')", "unknown keyword");
    }

    @Test
    void theBooleansAndNullAreCaseInsensitiveToo() {
        assertEquals("true", doc("True"));
        assertEquals("true", doc("TRUE"));
        assertEquals("true", doc("tRuE"));
        assertEquals("false", doc("False"));
        assertEquals("false", doc("FALSE"));
        assertEquals("null", doc("NULL"));
        assertEquals("BOOLEAN", one("SELECT TYPEOF(PARSE_JSON('True'))"));
        assertEquals("NULL_VALUE", one("SELECT TYPEOF(PARSE_JSON('NULL'))"));
    }

    @Test
    void undefinedIsCaseInsensitiveAndStillSqlNull() {
        assertEquals("yes", yesNo("PARSE_JSON('Undefined') IS NULL"));
        assertEquals("yes", yesNo("PARSE_JSON('UNDEFINED') IS NULL"));
        assertEquals("[1,undefined,2]", one("SELECT TO_JSON(PARSE_JSON('[1,UNDEFINED,2]'))"));
    }

    @Test
    void aNonFiniteIsADouble() {
        assertEquals("DOUBLE", one("SELECT TYPEOF(PARSE_JSON('NaN'))"));
        assertEquals("DOUBLE", one("SELECT TYPEOF(PARSE_JSON('Infinity'))"));
        assertEquals("DOUBLE", one("SELECT TYPEOF(PARSE_JSON('-Infinity'))"));
        assertEquals("no", yesNo("PARSE_JSON('NaN') IS NULL"));
        assertEquals("no", yesNo("IS_NULL_VALUE(PARSE_JSON('NaN'))"));
    }

    @Test
    void itRendersBareThroughToJsonAndToVarchar() {
        assertEquals("NaN", one("SELECT TO_JSON(PARSE_JSON('NaN'))"));
        assertEquals("Infinity", one("SELECT TO_JSON(PARSE_JSON('inf'))"));
        assertEquals("-Infinity", one("SELECT TO_JSON(PARSE_JSON('-inf'))"));
        assertEquals("NaN", one("SELECT TO_VARCHAR(PARSE_JSON('NaN'))"));
    }

    @Test
    void arithmeticIsIeee() {
        assertEquals("NaN", one("SELECT PARSE_JSON('NaN') + 1"));
        assertEquals("Infinity", one("SELECT PARSE_JSON('Infinity') + 1"));
        assertEquals("NaN", one("SELECT PARSE_JSON('Infinity') - PARSE_JSON('Infinity')"));
    }

    @Test
    void nanEqualsItselfAndOutranksEverything() {
        assertEquals("yes", yesNo("PARSE_JSON('NaN') = PARSE_JSON('NaN')"));
        assertEquals("no", yesNo("PARSE_JSON('NaN') = PARSE_JSON('Infinity')"));
        assertEquals("yes", yesNo("PARSE_JSON('NaN') > 1"));
        assertEquals("no", yesNo("PARSE_JSON('NaN') < 1"));
        assertEquals("yes", yesNo("PARSE_JSON('Infinity') > 1"));
        assertEquals("yes", yesNo("PARSE_JSON('-Infinity') < 1"));
    }

    @Test
    void theSortOrderPutsNanLast() {
        final ResultSet rs = engine.executeQuery(
            "SELECT v FROM (SELECT PARSE_JSON('NaN') v UNION ALL SELECT PARSE_JSON('Infinity')"
            + " UNION ALL SELECT PARSE_JSON('-Infinity') UNION ALL SELECT PARSE_JSON('1')) ORDER BY v");
        final StringBuilder order = new StringBuilder();
        while (rs.next()) {
            if (order.length() > 0) {
                order.append(',');
            }
            order.append(rs.getValue(0));
        }
        assertEquals("-Infinity,1,Infinity,NaN", order.toString());
    }

    @Test
    void containersHoldThem() {
        assertEquals("[NaN,Infinity,-Infinity,1]", doc("[NaN,Infinity,-Infinity,1]"));
        assertEquals("[NaN,Infinity,-Infinity,1]",
            one("SELECT TO_JSON(PARSE_JSON('[NaN,Infinity,-Infinity,1]'))"));
        assertEquals("{\"a\":{\"b\":NaN}}", doc("{\"a\":{\"b\":NaN}}"));
        assertEquals("2", one("SELECT ARRAY_SIZE(PARSE_JSON('[Infinity,1]'))"));
    }

    @Test
    void anOverflowingExponentIsTheNumberInfinity() {
        assertEquals("Infinity", doc("1e999"));
        assertEquals("-Infinity", doc("-1e999"));
        assertEquals("Infinity", doc("1e309"));
        assertEquals("[Infinity]", doc("[1e999]"));
        assertEquals("DOUBLE", one("SELECT TYPEOF(PARSE_JSON('1e999'))"));
    }

    @Test
    void theCastReadsTheSameSpellings() {
        assertEquals("NaN", one("SELECT 'NaN'::FLOAT"));
        assertEquals("NaN", one("SELECT 'nan'::FLOAT"));
        assertEquals("NaN", one("SELECT 'NAN'::FLOAT"));
        assertEquals("Infinity", one("SELECT 'inf'::FLOAT"));
        assertEquals("Infinity", one("SELECT 'INF'::FLOAT"));
        assertEquals("Infinity", one("SELECT '+inf'::FLOAT"));
        assertEquals("Infinity", one("SELECT 'infinity'::FLOAT"));
        assertEquals("Infinity", one("SELECT 'Infinity'::FLOAT"));
        assertEquals("-Infinity", one("SELECT '-inf'::FLOAT"));
        assertEquals("-Infinity", one("SELECT '-Infinity'::FLOAT"));
        assertEquals("Infinity", one("SELECT '1e999'::FLOAT"));
    }

    @Test
    void aFloatNanAlsoEqualsItself() {
        assertEquals("yes", yesNo("'NaN'::FLOAT = 'NaN'::FLOAT"));
        assertEquals("yes", yesNo("'NaN'::FLOAT > 1"));
    }

    @Test
    void aNonFiniteKeepsItsFloatCast() {
        assertEquals("Infinity", one("SELECT PARSE_JSON('Infinity')::FLOAT"));
        assertEquals("NaN", one("SELECT PARSE_JSON('NaN')::FLOAT"));
    }

    @Test
    void itSurvivesBeingStored() {
        engine.execute("CREATE OR REPLACE TABLE nf_round (v VARIANT, f FLOAT)");
        engine.execute("INSERT INTO nf_round SELECT PARSE_JSON('NaN'), 'NaN'::FLOAT");
        engine.execute("INSERT INTO nf_round SELECT PARSE_JSON('Infinity'), 'inf'::FLOAT");
        final ResultSet rs = engine.executeQuery(
            "SELECT TO_JSON(v), TYPEOF(v), f FROM nf_round ORDER BY f");
        final StringBuilder seen = new StringBuilder();
        while (rs.next()) {
            seen.append(rs.getValue(0)).append('|').append(rs.getValue(1))
                .append('|').append(rs.getValue(2)).append(' ');
        }
        assertEquals("Infinity|DOUBLE|Infinity NaN|DOUBLE|NaN", seen.toString().trim());
    }
}
