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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WHEN a name that resolves to no function is refused, and WHAT the sentence says when several of them
 * do. Frostlake found an unknown name only while TYPING the result columns — after every clause had
 * been validated and after the rows had been produced — so whatever was raised on the way there spoke
 * first, and a whole family of clause complaints did:
 *
 * <pre>
 *   … GROUP BY a ORDER BY b       was [GW.B] is not a valid order by expression
 *   … , b FROM gw GROUP BY a      was 'GW.B' … is neither an aggregate nor in the group by clause.
 *   … QUALIFY nosuchfn(a) &gt; 1     was found QUALIFY clause but no window function.
 *   … WHERE SUM(b) &gt; 1            was Invalid aggregate function in where clause [SUM(GW.B)]
 * </pre>
 *
 * <p>Live answers every one of them by naming the function.
 *
 * <p>PRECEDENCE IS BY KIND, NOT BY POSITION — measured with the two problems written in both orders.
 * Ahead of the unknown name: a syntax error, a missing relation, an invalid identifier ANYWHERE, a
 * window that needs an ORDER BY, an out-of-range ORDER BY ordinal. Behind it: every clause rule above,
 * and every argument-type complaint.
 *
 * <p>ONE SENTENCE, EVERY NAME. Live pluralises the noun and lists them all, in the order it resolves
 * them: written order across clauses, and ARGUMENTS BEFORE THEIR CALL within an expression. It does not
 * de-duplicate. The bare and qualified families are reported separately, and a statement carrying one
 * of each names only the bare list.
 */
public class UnknownFunctionPrecedenceTest extends BaseDatabaseTest {

    private static final String FN = "nosuchfn(a)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 30), (2, 10), (3, 50)");
        engine.execute("CREATE OR REPLACE TABLE ew (a INT, b INT)");
    }

    /** The refusal, newlines flattened. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void namesTheFunction(final String sql) {
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.", refusal(sql), sql);
    }

    /** The five clause complaints that used to speak first. */
    @Test
    public void theUnknownNameOutranksEveryClauseRule() {
        namesTheFunction("SELECT " + FN + " FROM gw GROUP BY a ORDER BY b");
        namesTheFunction("SELECT " + FN + ", ROW_NUMBER() OVER (ORDER BY SUM(b)) FROM gw"
            + " GROUP BY a ORDER BY b");
        namesTheFunction("SELECT " + FN + ", b FROM gw GROUP BY a");
        namesTheFunction("SELECT a FROM gw GROUP BY " + FN);
        namesTheFunction("SELECT a FROM gw GROUP BY a QUALIFY " + FN + " > 1");
        namesTheFunction("SELECT " + FN + " FROM gw WHERE SUM(b) > 1");
        namesTheFunction("SELECT " + FN + " FROM gw GROUP BY a"
            + " ORDER BY ROW_NUMBER() OVER (ORDER BY a)");
        namesTheFunction("SELECT DISTINCT " + FN + " FROM gw ORDER BY b");
        namesTheFunction("SELECT " + FN + " FROM gw GROUP BY ROLLUP(a) ORDER BY b");
        namesTheFunction("SELECT " + FN + ", BASE64_ENCODE(a, -1) FROM gw",
            "and an argument-type complaint, which #327 had already put behind it");
    }

    /** Overload carrying a note. */
    private void namesTheFunction(final String sql, final String note) {
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.", refusal(sql), note);
    }

    /** An EMPTY table answers identically — the check never needed a row. */
    @Test
    public void anEmptyTableIsRefusedTheSameWay() {
        namesTheFunction("SELECT " + FN + " FROM ew");
        namesTheFunction("SELECT " + FN + " FROM ew GROUP BY a ORDER BY b");
        namesTheFunction("SELECT " + FN + ", b FROM ew GROUP BY a");
    }

    /** What still outranks it, each measured with the unknown name written FIRST. */
    @Test
    public void whatStillOutranksTheUnknownName() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 32 unexpected '<EOF>'.",
            refusal("SELECT " + FN + " FROM gw WHERE"));
        assertEquals(hinted("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized."),
            refusal("SELECT " + FN + " FROM nosuchtable"));
        assertEquals("SQL compilation error:|Window function type [ROW_NUMBER] requires ORDER BY"
            + " in window specification.",
            refusal("SELECT " + FN + ", ROW_NUMBER() OVER () FROM gw"));
    }

    /** An invalid IDENTIFIER outranks it from any clause — six of them, all the same way. */
    @Test
    public void anInvalidIdentifierOutranksItFromEveryClause() {
        assertEquals("SQL compilation error: error line 1 at position 16"
            + "|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchfn(nosuchcol) FROM gw"), "the call's own argument");
        assertEquals("SQL compilation error: error line 1 at position 33"
            + "|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT " + FN + " FROM gw WHERE nosuchcol = 1"));
        assertEquals("SQL compilation error: error line 1 at position 20"
            + "|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT " + FN + ", nosuchcol FROM gw"), "a LATER select item");
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT nosuchcol, alsonosuch(a) FROM gw"), "and an EARLIER one");
        assertEquals("SQL compilation error: error line 1 at position 48"
            + "|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT " + FN + ", ROW_NUMBER() OVER (ORDER BY nosuchcol) FROM gw"));
        assertEquals("SQL compilation error: error line 1 at position 45"
            + "|invalid identifier 'NOSUCHCOL'",
            refusal("SELECT " + FN + " FROM gw GROUP BY a HAVING nosuchcol > 1"));
    }

    /** Every name in ONE sentence, pluralised, undeduplicated, in resolution order. */
    @Test
    public void oneSentenceNamesEveryUnresolvableCall() {
        assertEquals("SQL compilation error:|Unknown functions FNC, FNA, FNB.",
            refusal("SELECT fnc(a), fna(a), fnb(a) FROM gw"), "written order, not sorted");
        assertEquals("SQL compilation error:|Unknown functions NOSUCHFN, NOSUCHFN.",
            refusal("SELECT nosuchfn(a), nosuchfn(b) FROM gw"),
            "the same name twice is listed twice — live does not de-duplicate");
        assertEquals("SQL compilation error:|Unknown functions ALSONOSUCH, NOSUCHFN.",
            refusal("SELECT nosuchfn(alsonosuch(a)) FROM gw"),
            "NESTED names the INNER call first, which is the order they resolve in");
    }

    /** The list spans clauses, in written order. */
    @Test
    public void theListSpansEveryClause() {
        assertEquals("SQL compilation error:|Unknown functions NOSUCHFN, ALSONOSUCH.",
            refusal("SELECT " + FN + " FROM gw WHERE alsonosuch(a) = 1"));
        assertEquals("SQL compilation error:|Unknown functions NOSUCHFN, ALSONOSUCH.",
            refusal("SELECT " + FN + " FROM gw ORDER BY alsonosuch(a)"));
        assertEquals("SQL compilation error:|Unknown functions NOSUCHFN, ALSONOSUCH.",
            refusal("SELECT " + FN + " FROM gw GROUP BY a HAVING alsonosuch(a) > 1"));
        assertEquals("SQL compilation error:|Unknown functions NOSUCHFN, ALSONOSUCH.",
            refusal("SELECT a FROM gw GROUP BY nosuchfn(a), alsonosuch(a)"));
    }

    /** The QUALIFIED family is a sentence of its own, and the BARE list wins when both appear. */
    @Test
    public void theQualifiedFamilyIsReportedSeparately() {
        assertEquals("SQL compilation error:|Unknown user-defined function"
            + " TEST_DB.TEST_SCHEMA.NOSUCHFN.",
            refusal("SELECT test_db.test_schema.nosuchfn(a) FROM gw"));
        assertEquals("SQL compilation error:|Unknown user-defined functions"
            + " TEST_DB.TEST_SCHEMA.FNQ, TEST_DB.TEST_SCHEMA.FNR.",
            refusal("SELECT test_db.test_schema.fnq(a), test_db.test_schema.fnr(a) FROM gw"));
        assertEquals("SQL compilation error:|Unknown function FNP.",
            refusal("SELECT test_db.test_schema.fnq(a), fnp(a) FROM gw"),
            "one of each names only the BARE one — the qualified call is not mentioned at all");
    }

    /** Where else the name is found, and where the refusal is left to an inner query. */
    @Test
    public void everyOtherPlaceTheNameIsFound() {
        namesTheFunction("SELECT a FROM gw ORDER BY " + FN);
        namesTheFunction("SELECT a FROM gw WHERE nosuchfn(a) = 1");
        namesTheFunction("SELECT ABS(nosuchfn(a)) FROM gw");
        namesTheFunction("SELECT SUM(nosuchfn(a)) FROM gw GROUP BY a");
        namesTheFunction("SELECT nosuchfn()");
        namesTheFunction("SELECT nosuchfn(1)");
        namesTheFunction("SELECT * FROM (SELECT nosuchfn(a) x FROM gw) s");
        namesTheFunction("WITH c AS (SELECT nosuchfn(a) x FROM gw) SELECT * FROM c");
        namesTheFunction("SELECT " + FN + " FROM gw UNION ALL SELECT a, b FROM gw");
        namesTheFunction("SELECT " + FN + " FROM gw HAVING a > 1");
    }

    /**
     * A FROM-clause call is NOT judged by this scan. A table function written qualified
     * ({@code INFORMATION_SCHEMA.QUERY_HISTORY}) resolves through the relation machinery, and to the
     * expression registry it looks exactly like a user routine that does not exist — judging it here
     * refused 41 working queries. These read, and that is the whole assertion.
     */
    @Test
    public void aTableFunctionInFromIsLeftAlone() {
        assertEquals("accepted",
            refusal("SELECT COUNT(*) FROM TABLE(information_schema.query_history())"));
        assertEquals("accepted", refusal("SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3))"));
    }

    /** A view and a CTAS refuse at CREATE time, with the query's own sentence. */
    @Test
    public void aViewAndACtasRefuseWithTheSameSentence() {
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.", refusal(
            "CREATE OR REPLACE VIEW v390 AS SELECT nosuchfn(a) x FROM gw"));
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.", refusal(
            "CREATE OR REPLACE TABLE t390 AS SELECT nosuchfn(a) x FROM gw"));
    }
}
