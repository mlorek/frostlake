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
 * A QUOTED function name resolves case-INSENSITIVELY: {@code "SUM"(a)}, {@code "sum"(a)} and
 * {@code sum(a)} all find SUM. Frostlake refused the quoted spellings of every AGGREGATE — "Unknown
 * function SUM." — while the quoted spellings of a SCALAR worked, which is what gave the bug away.
 *
 * <p>The cause was the call's TEXT being used as the lookup key: {@code "SUM"} carries its quotes, so
 * upper-casing it whole asked the registry for a name with quotes in it, which matches nothing. The
 * scalar path canonicalised first and so was unaffected. Four sites shared the mistake — the
 * aggregate DETECTION and three dispatch points — and a half-fix is visible: with detection alone
 * repaired the call was recognised and then accumulated NULL.
 *
 * <p>The fixture matters as much as the assertions. An earlier one held a single row of 1, where
 * SUM(a), ABS(a) and a are all the same number and every wrong answer looks right. These rows are
 * chosen so the aggregate, the scalar and the column all differ.
 */
public class QuotedFunctionNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE qf (a INT)");
        engine.execute("INSERT INTO qf VALUES (-3), (5), (10)");
    }

    /** The one-column answer, rows joined, so an aggregate and a per-row value cannot be confused. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            all.append(String.valueOf(rs.getValue(0)));
        }
        return all.toString();
    }

    /** The message of the refusal a statement raises. */
    private String refusalOf(final String sql) {
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
        return "accepted";
    }

    /** SUM over these rows is 12, which no single row holds. */
    @Test
    public void aQuotedAggregateResolvesWhateverItsCase() {
        assertEquals("12", answer("SELECT SUM(a) AS x FROM qf"));
        assertEquals("12", answer("SELECT \"SUM\"(a) AS x FROM qf"));
        assertEquals("12", answer("SELECT \"sum\"(a) AS x FROM qf"));
        assertEquals("12", answer("SELECT SuM(a) AS x FROM qf"), "and a mixed bare spelling");
    }

    /** The rest of the family behaves the same, so this is the lookup and not one name. */
    @Test
    public void theOtherAggregatesAgree() {
        assertEquals("3", answer("SELECT \"count\"(a) AS x FROM qf"));
        assertEquals("10", answer("SELECT \"max\"(a) AS x FROM qf"));
    }

    /**
     * An UNKNOWN name is echoed the way the call spelled it — quotes and case intact. Resolution
     * folds the name; the echo does not, and the two are independent.
     *
     * <p>The fold here was a DOUBLE canonicalisation: the AST already holds the canonical name, with a
     * quoted spelling's quotes removed and its case kept, and the sentence canonicalised it a SECOND
     * time — which saw an unquoted {@code No Such} and folded it to {@code NO SUCH}. It now spells the
     * AST's own name PARTS, which also avoids splitting on a '.' that a quoted name may contain.
     */
    @Test
    public void anUnknownNameIsEchoedAsWritten() {
        assertEquals("SQL compilation error: Unknown function \"No Such\".",
            refusalOf("SELECT \"No Such\"(a) AS x FROM qf"));
        assertEquals("SQL compilation error: Unknown function \"nosuchfn\".",
            refusalOf("SELECT \"nosuchfn\"(a) AS x FROM qf"));
        assertEquals("SQL compilation error: Unknown function NOSUCHFN.",
            refusalOf("SELECT nosuchfn(a) AS x FROM qf"), "an unquoted name still folds and prints bare");
    }

    /** Every clause reports it the same way, not only the select list. */
    @Test
    public void everyClauseEchoesItAlike() {
        assertEquals("SQL compilation error: Unknown function \"No Such\".",
            refusalOf("SELECT a FROM qf WHERE \"No Such\"(a) = 1"));
        assertEquals("SQL compilation error: Unknown function \"No Such\".",
            refusalOf("SELECT a FROM qf ORDER BY \"No Such\"(a)"));
        assertEquals("SQL compilation error: Unknown function \"No Such\".",
            refusalOf("SELECT ABS(\"No Such\"(a)) AS x FROM qf"));
    }

    /** A quoted SCALAR already worked, and must keep working — ABS(-3) is 3, not -3. */
    @Test
    public void aQuotedScalarIsUnchanged() {
        assertEquals("3", answer("SELECT ABS(a) AS x FROM qf WHERE a < 0"));
        assertEquals("3", answer("SELECT \"ABS\"(a) AS x FROM qf WHERE a < 0"));
        assertEquals("3", answer("SELECT \"abs\"(a) AS x FROM qf WHERE a < 0"));
    }
}
