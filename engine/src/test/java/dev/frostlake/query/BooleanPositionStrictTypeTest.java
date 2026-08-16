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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The BOOLEAN POSITIONS of a statement — a searched CASE's WHEN condition, IFF's condition, and the
 * WHERE / HAVING / QUALIFY predicate — are judged on their STATIC type while the statement compiles,
 * each family with a sentence of its own (live-verified):
 *
 * <pre>
 *   CASE WHEN g THEN 1 END          Can not convert parameter 'RT.G' of type [VARCHAR(10)] into expected type [BOOLEAN]
 *   IFF(g, 1, 2)                    error line 1 at position 7  Invalid argument types for function 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))
 *   WHERE g / HAVING g / QUALIFY g  Invalid data type [VARCHAR(10)] for predicate [RT.G]
 * </pre>
 *
 * <p>★ EVERY NON-BOOLEAN FAMILY REFUSES in CASE and IFF — VARCHAR, NUMBER, FLOAT, DATE, BINARY, ARRAY,
 * OBJECT, and VARIANT too, a column or a PARSE_JSON / colon-path / TO_VARIANT expression alike. Only
 * a BOOLEAN and an untyped NULL compile; TO_BOOLEAN(g) and TRY_TO_BOOLEAN(g) pass as BOOLEAN calls.
 * The predicate rule keeps its narrower set (VARCHAR and NUMBER).
 *
 * <p>★ THE TYPE IS STATIC: an empty table, a view body, a predicate position and a FROM-less select
 * list all refuse the same; a CASE nested in a branch is walked; the first offending WHEN in written
 * order is the one named; and IFF's refusal wins over a branch mismatch in the same call.
 *
 * <p>★ THE OPERAND IS SPELLED FROM THE PLAN: a column qualified by its FROM-clause key (the alias
 * where one was written — R.G, A.N, D.X for a derived table, C.X for a CTE — else the table name), a
 * SELECT alias bare (GG), a string literal as its text, and the plan's own spellings for a call —
 * IFNULL for COALESCE, SUBSTR for LEFT, GET for a colon path, CAST(x AS T) for a ::, NEGATE for a
 * minus, the division's widening cast, a niladic call with its parentheses.
 *
 * <p>★ NVL2 AND DECODE TAKE ANY FIRST ARGUMENT, and AND / NOT convert at ROW time instead — a CASE
 * over {@code g AND TRUE} compiles and refuses the text per row.
 *
 * <p>★ A CASE WITHOUT ELSE FOLDS AN IMPLICIT NULL BRANCH: a text CASE declares VARCHAR(134217728)
 * whatever its branches' widths, while a NUMBER, DATE, BOOLEAN or VARIANT one keeps its own type.
 *
 * <p>NOT COVERED HERE, deliberately: a scalar subquery as the condition (untyped here), live's
 * internal CASE_FLATTENED spelling of a CASE operand, an unaliased derived table's invented name, a
 * BOOL*_AGG over unrecognised text, a ::BOOLEAN cast of a JSON object, and a SELECT alias in WHERE —
 * each recorded on its own.
 */
public class BooleanPositionStrictTypeTest extends BaseDatabaseTest {

    private static final String VARCHAR10 = "VARCHAR(10)";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (g VARCHAR(10), n NUMBER(5,0), b BOOLEAN, v VARIANT,"
            + " f FLOAT, d DATE, bi BINARY(4), a ARRAY, o OBJECT)");
        engine.execute("INSERT INTO rt SELECT 'x', 1, TRUE, PARSE_JSON('{\"a\":true}'), 1.5,"
            + " '2020-01-01', TO_BINARY('0A0B'), ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('k', 1)");
        engine.execute("CREATE OR REPLACE TABLE re (g VARCHAR(10))");
        engine.execute("CREATE OR REPLACE TABLE rt2 (m NUMBER(5,0), h VARCHAR(10))");
        engine.execute("INSERT INTO rt2 SELECT 1, 'y'");
    }

    /** Every row's first column, or the refusal with its lines joined by '|'. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED");
            while (rs.next()) {
                all.append(' ').append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The conversion refusal naming {@code operand} of {@code type}. */
    private static String cannotConvert(final String operand, final String type) {
        return "SQL compilation error:|Can not convert parameter '" + operand + "' of type [" + type
            + "] into expected type [BOOLEAN]";
    }

    /** IFF's argument-type refusal at the call's own offset. */
    private static String iffRefusal(final int position, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + "|Invalid argument types for function 'IFF': (" + types + ")";
    }

    /** The predicate-position refusal. */
    private static String predicate(final String type, final String printed) {
        return "SQL compilation error:|Invalid data type [" + type + "] for predicate [" + printed + "]";
    }

    @Test
    void aSearchedCaseRefusesEveryNonBooleanFamily() {
        assertEquals(cannotConvert("RT.G", VARCHAR10), outcome("SELECT CASE WHEN g THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.N", "NUMBER(5,0)"), outcome("SELECT CASE WHEN n THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.F", "FLOAT"), outcome("SELECT CASE WHEN f THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.D", "DATE"), outcome("SELECT CASE WHEN d THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.V", "VARIANT"), outcome("SELECT CASE WHEN v THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.BI", "BINARY(4)"), outcome("SELECT CASE WHEN bi THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.A", "ARRAY"), outcome("SELECT CASE WHEN a THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.O", "OBJECT"), outcome("SELECT CASE WHEN o THEN 1 ELSE 2 END FROM rt"));
        // A BOOLEAN, an untyped NULL and a BOOLEAN-typed call compile.
        assertEquals("ACCEPTED 1", outcome("SELECT CASE WHEN b THEN 1 ELSE 2 END FROM rt"));
        assertEquals("ACCEPTED 2", outcome("SELECT CASE WHEN NULL THEN 1 ELSE 2 END"));
        assertEquals("ACCEPTED 2", outcome("SELECT CASE WHEN TRY_TO_BOOLEAN(g) THEN 1 ELSE 2 END FROM rt"));
        assertEquals("ACCEPTED", outcome("SELECT CASE WHEN TO_BOOLEAN(g) THEN 1 ELSE 2 END FROM rt WHERE FALSE"));
    }

    @Test
    void theTypeIsStaticSoEveryPositionRefusesTheSame() {
        assertEquals(cannotConvert("RE.G", VARCHAR10), outcome("SELECT CASE WHEN g THEN 1 ELSE 2 END FROM re"));
        assertEquals(cannotConvert("RT.G", VARCHAR10),
            outcome("CREATE VIEW vv AS SELECT CASE WHEN g THEN 1 ELSE 2 END AS c FROM rt"));
        assertEquals(cannotConvert("RT.G", VARCHAR10), outcome("SELECT 1 FROM rt WHERE CASE WHEN g THEN TRUE END"));
        assertEquals(cannotConvert("RT.G", VARCHAR10),
            outcome("SELECT g FROM rt GROUP BY g HAVING CASE WHEN g THEN TRUE END"));
        assertEquals(cannotConvert("GG", VARCHAR10), outcome("SELECT n, g AS gg, ROW_NUMBER() OVER (ORDER BY n) AS r"
            + " FROM rt QUALIFY CASE WHEN gg THEN TRUE END"));
        // A FROM-less select list: a literal and a VARIANT expression are typed all the same.
        assertEquals(cannotConvert("'x'", "VARCHAR(1)"), outcome("SELECT CASE WHEN 'x' THEN 1 ELSE 2 END"));
        assertEquals(cannotConvert("'true'", "VARCHAR(4)"), outcome("SELECT CASE WHEN 'true' THEN 1 ELSE 2 END"));
        assertEquals(cannotConvert("1", "NUMBER(1,0)"), outcome("SELECT CASE WHEN 1 THEN 1 ELSE 2 END"));
        assertEquals(cannotConvert("1.5", "NUMBER(2,1)"), outcome("SELECT CASE WHEN 1.5 THEN 1 ELSE 2 END"));
        assertEquals(cannotConvert("PARSE_JSON('true')", "VARIANT"),
            outcome("SELECT CASE WHEN PARSE_JSON('true') THEN 1 ELSE 2 END"));
        assertEquals(cannotConvert("CAST(TRUE AS VARIANT)", "VARIANT"),
            outcome("SELECT CASE WHEN TO_VARIANT(TRUE) THEN 1 ELSE 2 END"));
        assertEquals(cannotConvert("CURRENT_DATE()", "DATE"), outcome("SELECT CASE WHEN CURRENT_DATE THEN 1 ELSE 2 END"));
    }

    @Test
    void theFirstOffendingConditionIsNamedAndNestedCasesAreWalked() {
        assertEquals(cannotConvert("RT.G", VARCHAR10), outcome("SELECT CASE WHEN g THEN 1 WHEN n THEN 2 END FROM rt"));
        assertEquals(cannotConvert("RT.N", "NUMBER(5,0)"), outcome("SELECT CASE WHEN b THEN 1 WHEN n THEN 2 END FROM rt"));
        assertEquals(cannotConvert("RT.G", VARCHAR10),
            outcome("SELECT CASE WHEN b THEN CASE WHEN g THEN 1 END END FROM rt"));
        assertEquals(cannotConvert("RT.G", VARCHAR10), outcome("SELECT CASE WHEN (g) THEN 1 ELSE 2 END FROM rt"));
    }

    @Test
    void theOperandIsSpelledFromThePlan() {
        assertEquals(cannotConvert("RT.N + 1", "NUMBER(6,0)"), outcome("SELECT CASE WHEN n + 1 THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.N * 2", "NUMBER(6,0)"), outcome("SELECT CASE WHEN n * 2 THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("(CAST(RT.N AS NUMBER(11,6))) / 2", "NUMBER(11,6)"),
            outcome("SELECT CASE WHEN n / 2 THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("NEGATE(RT.N)", "NUMBER(5,0)"), outcome("SELECT CASE WHEN -n THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("RT.G || 'a'", "VARCHAR(11)"),
            outcome("SELECT CASE WHEN g || 'a' THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("UPPER(RT.G)", "VARCHAR(30)"), outcome("SELECT CASE WHEN UPPER(g) THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("LENGTH(RT.G)", "NUMBER(18,0)"),
            outcome("SELECT CASE WHEN LENGTH(g) THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("SUM(RT.N)", "NUMBER(17,0)"), outcome("SELECT CASE WHEN SUM(n) THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("IFNULL(RT.G, 'a')", VARCHAR10),
            outcome("SELECT CASE WHEN COALESCE(g, 'a') THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("IFNULL(RT.G, IFNULL('a', 'b'))", VARCHAR10),
            outcome("SELECT CASE WHEN COALESCE(g, 'a', 'b') THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("NULLIF(RT.G, 'a')", VARCHAR10),
            outcome("SELECT CASE WHEN NULLIF(g, 'a') THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("SUBSTR(RT.G, 1, 2)", VARCHAR10),
            outcome("SELECT CASE WHEN LEFT(g, 2) THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("CAST(RT.N AS VARCHAR(134217728))", "VARCHAR(134217728)"),
            outcome("SELECT CASE WHEN n::VARCHAR THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("CAST(RT.N AS VARCHAR(5))", "VARCHAR(5)"),
            outcome("SELECT CASE WHEN CAST(n AS VARCHAR(5)) THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("GET(RT.V, 'a')", "VARIANT"), outcome("SELECT CASE WHEN v:a THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("GET(RT.V, 'a')", "VARIANT"), outcome("SELECT CASE WHEN v['a'] THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("GET(GET(RT.V, 'a'), 'b')", "VARIANT"),
            outcome("SELECT CASE WHEN v:a.b THEN 1 ELSE 2 END FROM rt"));
        assertEquals(cannotConvert("CURRENT_TIMESTAMP()", "TIMESTAMP_LTZ(9)"),
            outcome("SELECT CASE WHEN CURRENT_TIMESTAMP THEN 1 ELSE 2 END"));
    }

    @Test
    void theQualifierIsTheFromClauseKey() {
        assertEquals(cannotConvert("R.G", VARCHAR10), outcome("SELECT CASE WHEN r.g THEN 1 ELSE 2 END FROM rt r"));
        assertEquals(cannotConvert("A.N", "NUMBER(5,0)"),
            outcome("SELECT CASE WHEN n THEN 1 ELSE 2 END FROM rt a JOIN rt2 b ON a.n = b.m"));
        assertEquals(cannotConvert("A.N", "NUMBER(5,0)"),
            outcome("SELECT CASE WHEN a.n THEN 1 ELSE 2 END FROM rt a JOIN rt2 b ON a.n = b.m"));
        assertEquals(cannotConvert("B.H", VARCHAR10),
            outcome("SELECT CASE WHEN h THEN 1 ELSE 2 END FROM rt a JOIN rt2 b ON a.n = b.m"));
        assertEquals(cannotConvert("RT.G", VARCHAR10),
            outcome("SELECT CASE WHEN g THEN 1 ELSE 2 END FROM rt JOIN rt2 ON rt.n = rt2.m"));
        assertEquals(cannotConvert("D.X", VARCHAR10),
            outcome("SELECT CASE WHEN x THEN 1 ELSE 2 END FROM (SELECT g AS x FROM rt) d"));
        assertEquals(cannotConvert("D.X", VARCHAR10),
            outcome("SELECT CASE WHEN d.x THEN 1 ELSE 2 END FROM (SELECT g AS x FROM rt) d"));
        assertEquals(cannotConvert("C.X", VARCHAR10),
            outcome("WITH c AS (SELECT g AS x FROM rt) SELECT CASE WHEN x THEN 1 ELSE 2 END FROM c"));
    }

    @Test
    void iffRefusesTheSameFamiliesPositionedAtTheCall() {
        assertEquals(iffRefusal(7, "VARCHAR(10), NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(g, 1, 2) FROM rt"));
        assertEquals(iffRefusal(7, "NUMBER(5,0), NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(n, 1, 2) FROM rt"));
        assertEquals(iffRefusal(7, "DATE, NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(d, 1, 2) FROM rt"));
        assertEquals(iffRefusal(7, "VARIANT, NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(v, 1, 2) FROM rt"));
        assertEquals(iffRefusal(7, "VARIANT, NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(v:a, 1, 2) FROM rt"));
        assertEquals(iffRefusal(7, "VARIANT, NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(PARSE_JSON('true'), 1, 2)"));
        assertEquals(iffRefusal(7, "VARCHAR(10), NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT IFF(g, 1, 2) FROM re"));
        // Every argument's static type is listed, NULL for an untyped one, and the call's refusal
        // wins over a branch mismatch.
        assertEquals(iffRefusal(7, "VARCHAR(10), NULL, NUMBER(1,0)"), outcome("SELECT IFF(g, NULL, 1) FROM rt"));
        assertEquals(iffRefusal(7, "VARCHAR(10), NUMBER(1,0), VARCHAR(1)"), outcome("SELECT IFF(g, 1, 'x') FROM rt"));
        assertEquals(iffRefusal(7, "VARCHAR(10), NUMBER(5,0), FLOAT"), outcome("SELECT IFF(g, n, f) FROM rt"));
        // The position is the call's own, wherever it sits.
        assertEquals(iffRefusal(10, "VARCHAR(10), NUMBER(1,0), NUMBER(1,0)"), outcome("SELECT 1, IFF(g, 1, 2) FROM rt"));
        assertEquals(iffRefusal(23, "VARCHAR(10), BOOLEAN, BOOLEAN"), outcome("SELECT 1 FROM rt WHERE IFF(g, TRUE, FALSE)"));
        assertEquals(iffRefusal(25, "VARCHAR(10), NUMBER(1,0), NUMBER(1,0)"),
            outcome("CREATE VIEW vi AS SELECT IFF(g, 1, 2) AS c FROM rt"));
        assertEquals(iffRefusal(7, "NUMBER(5,0), NUMBER(1,0), NUMBER(1,0)"),
            outcome("SELECT IFF(n, 1, 2) FROM rt a JOIN rt2 b ON a.n = b.m"));
        // What compiles: NULL, a BOOLEAN-typed call, a BOOLEAN.
        assertEquals("ACCEPTED 2", outcome("SELECT IFF(NULL, 1, 2)"));
        assertEquals("ACCEPTED 2", outcome("SELECT IFF(TRY_TO_BOOLEAN(g), 1, 2) FROM rt"));
        assertEquals("ACCEPTED 1", outcome("SELECT IFF(b, 1, 2) FROM rt"));
    }

    @Test
    void nvl2AndDecodeTakeAnythingAndTheOperatorsConvertPerRow() {
        assertEquals("ACCEPTED 1", outcome("SELECT NVL2(g, 1, 2) FROM rt"));
        assertEquals("ACCEPTED 2", outcome("SELECT DECODE(g, TRUE, 1, 2) FROM rt"));
        // AND and NOT are their own rule: the text converts at ROW time, refused per row, no prefix.
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT CASE WHEN g AND TRUE THEN 1 ELSE 2 END FROM rt"));
        assertEquals("Boolean value 'x' is not recognized", outcome("SELECT IFF(NOT g, 1, 2) FROM rt"));
    }

    @Test
    void havingAndQualifyAreHeldToThePredicateRuleWithWhere() {
        assertEquals(predicate(VARCHAR10, "RT.G"), outcome("SELECT 1 FROM rt GROUP BY g HAVING g"));
        assertEquals(predicate(VARCHAR10, "RT.G"), outcome("SELECT g FROM rt GROUP BY g HAVING (g)"));
        assertEquals(predicate(VARCHAR10, "MAX(RT.G)"), outcome("SELECT g FROM rt GROUP BY g HAVING MAX(g)"));
        assertEquals(predicate("NUMBER(18,0)", "COUNT(*)"), outcome("SELECT g FROM rt GROUP BY g HAVING COUNT(*)"));
        assertEquals(predicate("NUMBER(6,0)", "RT.N + 1"), outcome("SELECT n FROM rt GROUP BY n HAVING n + 1"));
        assertEquals(predicate(VARCHAR10, "GG"), outcome("SELECT g AS gg FROM rt GROUP BY g HAVING gg"));
        assertEquals(predicate(VARCHAR10, "RT.G"), outcome("SELECT ROW_NUMBER() OVER (ORDER BY n) FROM rt QUALIFY g"));
        // The predicate's type is judged before the clause is asked for a window at all.
        assertEquals(predicate(VARCHAR10, "RT.G"), outcome("SELECT 1 FROM rt QUALIFY g"));
        assertEquals(predicate("NUMBER(18,0)", "ROW_NUMBER() OVER (ORDER BY RT.N ASC NULLS LAST)"),
            outcome("SELECT n FROM rt QUALIFY ROW_NUMBER() OVER (ORDER BY n)"));
        assertEquals(predicate("NUMBER(6,0)", "RT.N + 1"),
            outcome("SELECT n, ROW_NUMBER() OVER (ORDER BY n) AS r FROM rt QUALIFY n + 1"));
        assertEquals(predicate(VARCHAR10, "GG"), outcome("SELECT n, g AS gg FROM rt QUALIFY gg"));
        assertEquals(predicate("VARCHAR(11)", "GG || 'a'"),
            outcome("SELECT n, g AS gg, ROW_NUMBER() OVER (ORDER BY n) AS r FROM rt QUALIFY gg || 'a'"));
        // WHERE spells its predicate the same way.
        assertEquals(predicate("NUMBER(6,0)", "RT.N + 1"), outcome("SELECT 1 FROM rt WHERE n + 1"));
        assertEquals(predicate("NUMBER(6,0)", "RT.N + 1"), outcome("SELECT 1 FROM rt WHERE (n + 1)"));
        assertEquals(predicate("VARCHAR(4)", "'true'"), outcome("SELECT 1 FROM rt WHERE 'true'"));
        assertEquals(predicate("NUMBER(1,0)", "1"), outcome("SELECT 1 FROM rt WHERE 1"));
        assertEquals(predicate(VARCHAR10, "B.H"), outcome("SELECT 1 FROM rt a JOIN rt2 b ON a.n = b.m WHERE h"));
        assertEquals("ACCEPTED 1", outcome("SELECT 1 FROM rt WHERE CASE WHEN b THEN b END"));
    }

    @Test
    void aCaseWithoutElseFoldsAnImplicitNullBranch() {
        assertEquals("ACCEPTED VARCHAR(134217728)[LOB]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN g END) FROM rt"));
        assertEquals("ACCEPTED VARCHAR(134217728)[LOB]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN 'x' END) FROM rt"));
        assertEquals("ACCEPTED VARCHAR(134217728)[LOB]",
            outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN 'x' WHEN n = 1 THEN 'yy' END) FROM rt"));
        assertEquals("ACCEPTED VARCHAR(134217728)[LOB]",
            outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN 'x' ELSE NULL END) FROM rt"));
        assertEquals("ACCEPTED VARCHAR(10)[LOB]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN g ELSE 'yy' END) FROM rt"));
        assertEquals("ACCEPTED VARCHAR(2)[LOB]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN 'x' ELSE 'yy' END) FROM rt"));
        assertEquals("ACCEPTED NUMBER(1,0)[SB1]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN 1 END) FROM rt"));
        assertEquals("ACCEPTED NUMBER(2,1)[SB1]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN 1 WHEN n = 1 THEN 2.5 END) FROM rt"));
        assertEquals("ACCEPTED DATE[SB4]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN d END) FROM rt"));
        assertEquals("ACCEPTED VARIANT[LOB]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN v END) FROM rt"));
        assertEquals("ACCEPTED BOOLEAN[SB1]", outcome("SELECT SYSTEM$TYPEOF(CASE WHEN b THEN b END) FROM rt"));
    }
}
