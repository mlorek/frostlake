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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The VARIANT {@code undefined} element — the third null of the semi-structured layer.
 *
 * <p>Every assertion below was verified against a real Snowflake account on. Throughout,
 * {@code ac} is {@code ARRAY_CONSTRUCT(1, NULL, 2)} (which renders {@code [1,undefined,2]}) and
 * {@code pj} is {@code PARSE_JSON('[1,null,2]')} (which keeps {@code [1,null,2]}). Each behaviour is
 * asserted for BOTH so the two nulls stay distinguishable.
 */
public class VariantUndefinedTest extends BaseDatabaseTest {

    private static final String AC = "ARRAY_CONSTRUCT(1, NULL, 2)";
    private static final String PJ = "PARSE_JSON('[1,null,2]')";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        return String.valueOf(scalar(sql));
    }

    // ---------------------------------------------------------------- producers

    @Test
    public void everySqlNullEnteringAnArrayBecomesUndefined() {
        // Live: ARRAY_CONSTRUCT(1,NULL,2) -> [1,undefined,2]; [1,NULL,2] -> [1,undefined,2];
        // ARRAY_APPEND([1],NULL) -> [1,undefined]; ARRAY_PREPEND([1],NULL) -> [undefined,1];
        // ARRAY_INSERT([1],4,9) -> [1,undefined,undefined,undefined,9]; ARRAY_REPEAT(NULL,3) ->
        // [undefined,undefined,undefined]; TRANSFORM([1,2], x -> NULL) -> [undefined,undefined];
        // [** NULL] -> [undefined]; ARRAY_CONSTRUCT(NULL::VARIANT) -> [undefined].
        assertEquals("[1,undefined,2]", text("SELECT " + AC));
        assertEquals("[1,undefined,2]", text("SELECT [1, NULL, 2]"));
        assertEquals("[1,undefined]", text("SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(1), NULL)"));
        assertEquals("[undefined,1]", text("SELECT ARRAY_PREPEND(ARRAY_CONSTRUCT(1), NULL)"));
        assertEquals("[1,undefined,undefined,undefined,9]",
            text("SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(1), 4, 9)"));
        assertEquals("[undefined,undefined,undefined]", text("SELECT ARRAY_REPEAT(NULL, 3)"));
        assertEquals("[undefined,undefined]",
            text("SELECT TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> NULL)"));
        assertEquals("[undefined]", text("SELECT [** NULL]"));
        assertEquals("[undefined]", text("SELECT ARRAY_CONSTRUCT(NULL::VARIANT)"));
    }

    @Test
    public void parseJsonAcceptsTheBareUndefinedToken() {
        // Live: PARSE_JSON('[undefined]') is [undefined] and equals ARRAY_CONSTRUCT(NULL);
        // TRY_PARSE_JSON agrees and CHECK_JSON reports the text as VALID (SQL NULL = no error).
        assertEquals("[undefined]", text("SELECT PARSE_JSON('[undefined]')"));
        assertEquals("[undefined]", text("SELECT TRY_PARSE_JSON('[undefined]')"));
        assertNull(scalar("SELECT CHECK_JSON('[1,undefined,2]')"));
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAY_CONSTRUCT(NULL) = PARSE_JSON('[undefined]')"));
    }

    @Test
    public void aGenuineUndefinedStringStaysAString() {
        // The token is only rewritten OUTSIDE string values — live, PARSE_JSON('["undefined"]') keeps a
        // one-element array holding the STRING, whose TYPEOF is VARCHAR (not the undefined element).
        assertEquals("[\"undefined\"]", text("SELECT PARSE_JSON('[\"undefined\"]')"));
        assertEquals("VARCHAR", text("SELECT TYPEOF(GET(PARSE_JSON('[\"undefined\"]'), 0))"));
        assertEquals("is undefined here",
            text("SELECT PARSE_JSON('{\"m\":\"is undefined here\"}'):m::VARCHAR"));
    }

    @Test
    public void theseAreNotProducers() {
        // Live: PARSE_JSON('[1,null,2]') keeps JSON nulls; ARRAY_CONSTRUCT(PARSE_JSON('null')) is [null];
        // OBJECT_CONSTRUCT_KEEP_NULL('k',NULL) is {"k":null}; OBJECT_CONSTRUCT('k',NULL) is {};
        // TO_ARRAY(NULL) is SQL NULL; ARRAY_AGG skips SQL NULL rows entirely.
        assertEquals("[1,null,2]", text("SELECT " + PJ));
        assertEquals("[null]", text("SELECT ARRAY_CONSTRUCT(PARSE_JSON('null'))"));
        assertEquals("{\"k\":null}", text("SELECT OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)"));
        assertEquals("{}", text("SELECT OBJECT_CONSTRUCT('k', NULL)"));
        assertNull(scalar("SELECT TO_ARRAY(NULL)"));
        assertEquals("[1,2]",
            text("SELECT ARRAY_AGG(v) FROM (SELECT 1 v UNION ALL SELECT NULL UNION ALL SELECT 2)"));
    }

    @Test
    public void undefinedLivesOnlyInAnArrayNeverInAnObject() {
        // Live: PARSE_JSON('{"a":undefined}') is {"a":null} and TO_JSON of it keeps the JSON null;
        // an object nested in an array keeps its own JSON null next to a sibling undefined; and a
        // WHOLE-VALUE undefined is SQL NULL (PARSE_JSON('undefined') IS NULL is TRUE).
        assertEquals("{\"a\":null}", text("SELECT PARSE_JSON('{\"a\":undefined}')"));
        assertEquals("{\"a\":null,\"b\":1}", text("SELECT TO_JSON(PARSE_JSON('{\"a\":undefined,\"b\":1}'))"));
        assertEquals("[{\"k\":null},undefined]",
            text("SELECT ARRAY_CONSTRUCT(OBJECT_CONSTRUCT_KEEP_NULL('k', NULL), NULL)"));
        assertNull(scalar("SELECT PARSE_JSON('undefined')"));
        assertNull(scalar("SELECT TYPEOF(PARSE_JSON('undefined'))"));
    }

    // ---------------------------------------------------------------- it never escapes as a value

    @Test
    public void extractingAnUndefinedYieldsSqlNullWhileAJsonNullStaysAValue() {
        // Live: TYPEOF(GET(ac,1)) is SQL NULL, GET(ac,1) IS NULL is TRUE, IS_NULL_VALUE(GET(ac,1)) is SQL
        // NULL, TO_JSON(GET(ac,1)) is SQL NULL and COALESCE(GET(ac,1),9) is 9 — whereas over pj the same
        // access reports 'NULL_VALUE', IS NULL is FALSE, IS_NULL_VALUE is TRUE, TO_JSON is 'null' and
        // COALESCE returns the JSON null itself.
        assertNull(scalar("SELECT TYPEOF(GET(" + AC + ", 1))"));
        assertEquals(Boolean.TRUE, scalar("SELECT GET(" + AC + ", 1) IS NULL"));
        assertNull(scalar("SELECT IS_NULL_VALUE(GET(" + AC + ", 1))"));
        assertNull(scalar("SELECT TO_JSON(GET(" + AC + ", 1))"));
        assertEquals("9", text("SELECT COALESCE(GET(" + AC + ", 1), 9)"));

        assertEquals("NULL_VALUE", text("SELECT TYPEOF(GET(" + PJ + ", 1))"));
        assertEquals(Boolean.FALSE, scalar("SELECT GET(" + PJ + ", 1) IS NULL"));
        assertEquals(Boolean.TRUE, scalar("SELECT IS_NULL_VALUE(GET(" + PJ + ", 1))"));
        assertEquals("null", text("SELECT TO_JSON(GET(" + PJ + ", 1))"));
        assertEquals("null", text("SELECT COALESCE(GET(" + PJ + ", 1), 9)"));
    }

    @Test
    public void bracketAndPathAccessAgreeWithGet() {
        // Live: ac[1] IS NULL is TRUE, GET_PATH(ac,'[1]') is SQL NULL, EQUAL_NULL(GET(ac,1),NULL) is TRUE
        // and STRIP_NULL_VALUE(GET(ac,1)) is SQL NULL; over pj, pj[1] IS NULL is FALSE and
        // EQUAL_NULL(GET(pj,1),NULL) is FALSE.
        assertEquals(Boolean.TRUE, scalar("SELECT (" + AC + ")[1] IS NULL"));
        assertNull(scalar("SELECT GET_PATH(" + AC + ", '[1]')"));
        assertEquals(Boolean.TRUE, scalar("SELECT EQUAL_NULL(GET(" + AC + ", 1), NULL)"));
        assertNull(scalar("SELECT STRIP_NULL_VALUE(GET(" + AC + ", 1))"));

        assertEquals(Boolean.FALSE, scalar("SELECT (" + PJ + ")[1] IS NULL"));
        assertEquals(Boolean.FALSE, scalar("SELECT EQUAL_NULL(GET(" + PJ + ", 1), NULL)"));
    }

    @Test
    public void nestedUndefinedExtractsAsSqlNullToo() {
        // Live: ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1,NULL),2) is [[1,undefined],2],
        // OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(NULL)) is {"a":[undefined]} and the inner element still
        // reads as SQL NULL.
        assertEquals("[[1,undefined],2]",
            text("SELECT ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1, NULL), 2)"));
        assertEquals("{\"a\":[undefined]}",
            text("SELECT OBJECT_CONSTRUCT('a', ARRAY_CONSTRUCT(NULL))"));
        assertEquals("[undefined]", text("SELECT PARSE_JSON('{\"k\":[undefined]}'):k"));
        assertEquals(Boolean.TRUE,
            scalar("SELECT GET(GET(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1, NULL), 2), 0), 1) IS NULL"));
    }

    // ---------------------------------------------------------------- rendering, size, equality

    @Test
    public void renderingAndSizeKeepTheElement() {
        // Live: TO_JSON(ac) and ac::VARCHAR are both [1,undefined,2]; ARRAY_SIZE(ac) is 3 (the element is
        // COUNTED); TYPEOF(ac) is ARRAY.
        assertEquals("[1,undefined,2]", text("SELECT TO_JSON(" + AC + ")"));
        assertEquals("[1,undefined,2]", text("SELECT " + AC + "::VARCHAR"));
        assertEquals("[1,undefined,2]", text("SELECT TO_VARCHAR(" + AC + ")"));
        assertEquals(3L, scalar("SELECT ARRAY_SIZE(" + AC + ")"));
        assertEquals(3L, scalar("SELECT ARRAY_SIZE(" + PJ + ")"));
        assertEquals("ARRAY", text("SELECT TYPEOF(" + AC + ")"));
    }

    @Test
    public void undefinedIsNotEqualToAJsonNull() {
        // Live: ac = ac is TRUE, ac = pj is FALSE.
        assertEquals(Boolean.TRUE, scalar("SELECT " + AC + " = " + AC));
        assertEquals(Boolean.FALSE, scalar("SELECT " + AC + " = " + PJ));
    }

    @Test
    public void storedArraysRoundTripAndStayDistinct() {
        // Live: an ARRAY column stores [1,undefined,2] and reads it back unchanged, TYPEOF of the element
        // is still SQL NULL, and the undefined array never collapses onto the JSON-null one (2 groups).
        engine.execute("CREATE TABLE u_round (id INT, a ARRAY, v VARIANT)");
        engine.execute("INSERT INTO u_round SELECT 1, " + AC + ", " + AC);
        engine.execute("INSERT INTO u_round SELECT 2, " + PJ + ", " + PJ);
        engine.execute("INSERT INTO u_round SELECT 3, " + AC + ", " + AC);
        assertEquals("[1,undefined,2]", text("SELECT a FROM u_round WHERE id = 1"));
        assertEquals("[1,null,2]", text("SELECT a FROM u_round WHERE id = 2"));
        assertEquals("[1,undefined,2]", text("SELECT v FROM u_round WHERE id = 1"));
        assertNull(scalar("SELECT TYPEOF(GET(a, 1)) FROM u_round WHERE id = 1"));
        assertEquals("NULL_VALUE", text("SELECT TYPEOF(GET(a, 1)) FROM u_round WHERE id = 2"));
        assertEquals(2L, scalar("SELECT COUNT(*) FROM (SELECT a FROM u_round GROUP BY a)"));
        assertEquals(2L, scalar("SELECT COUNT(*) FROM u_round WHERE a = " + AC));
    }

    // ---------------------------------------------------------------- the array functions

    @Test
    public void arrayCompactRemovesBothNulls() {
        // Live: ARRAY_COMPACT removes the undefined AND the JSON null — ARRAY_COMPACT(ac) and
        // ARRAY_COMPACT(pj) are both [1,2], and over the concatenation it is [1,2,1,2]. Only the top
        // level is compacted: ARRAY_COMPACT([[1,NULL]]) keeps [[1,undefined]].
        assertEquals("[1,2]", text("SELECT ARRAY_COMPACT(" + AC + ")"));
        assertEquals("[1,2]", text("SELECT ARRAY_COMPACT(" + PJ + ")"));
        assertEquals("[1,2,1,2]", text("SELECT ARRAY_COMPACT(ARRAY_CAT(" + AC + ", " + PJ + "))"));
        assertEquals("[]", text("SELECT ARRAY_COMPACT(ARRAY_CONSTRUCT(NULL))"));
        assertEquals("[[1,undefined]]",
            text("SELECT ARRAY_COMPACT(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1, NULL)))"));
    }

    @Test
    public void flattenSkipsUndefinedButKeepsAJsonNull() {
        // Live: FLATTEN over ac yields 2 rows whose INDEX values are the ORIGINAL 0 and 2, while over pj
        // it yields 3 rows with TYPEOF(value) INTEGER / NULL_VALUE / INTEGER.
        assertEquals(2L, scalar("SELECT COUNT(*) FROM TABLE(FLATTEN(" + AC + "))"));
        assertEquals(3L, scalar("SELECT COUNT(*) FROM TABLE(FLATTEN(" + PJ + "))"));
        assertEquals("INTEGER,INTEGER",
            text("SELECT LISTAGG(TYPEOF(value), ',') FROM TABLE(FLATTEN(" + AC + "))"));
        assertEquals("INTEGER,NULL_VALUE,INTEGER",
            text("SELECT LISTAGG(TYPEOF(value), ',') FROM TABLE(FLATTEN(" + PJ + "))"));
        assertEquals("0,2",
            text("SELECT LISTAGG(index::string, ',') FROM TABLE(FLATTEN(" + AC + "))"));
        assertEquals("0,1,2",
            text("SELECT LISTAGG(index::string, ',') FROM TABLE(FLATTEN(" + PJ + "))"));
    }

    @Test
    public void flattenOverAnAllUndefinedArrayBehavesLikeAnEmptyOne() {
        // Live: FLATTEN(ARRAY_CONSTRUCT(NULL)) yields 0 rows, and with outer => TRUE it yields the single
        // all-NULL row — exactly what an empty array does.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(NULL)))"));
        assertEquals(1L,
            scalar("SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(NULL), outer => TRUE))"));
        // Live: a recursive FLATTEN over [[1,NULL],2] yields 3 rows while over PARSE_JSON('[[1,null],2]')
        // it yields 4.
        assertEquals(3L, scalar("""
            SELECT COUNT(*) FROM TABLE(FLATTEN(
                input => ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1, NULL), 2), recursive => TRUE))"""));
        assertEquals(4L, scalar("""
            SELECT COUNT(*) FROM TABLE(FLATTEN(
                input => PARSE_JSON('[[1,null],2]'), recursive => TRUE))"""));
    }

    @Test
    public void arrayToStringRendersUndefinedAsEmpty() {
        // Live: ARRAY_TO_STRING(ac,'|') is '1||2' — the element contributes an empty string but keeps its
        // separators — and ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', NULL), ',') is 'a,'.
        assertEquals("1||2", text("SELECT ARRAY_TO_STRING(" + AC + ", '|')"));
        assertEquals("a,", text("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', NULL), ',')"));
        assertEquals("", text("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(NULL), '|')"));
    }

    @Test
    public void arrayContainsAndPositionMatchOnlyTheirOwnNull() {
        // Live: a SQL NULL needle finds an undefined — ARRAY_CONTAINS(NULL::VARIANT, ac) is TRUE and
        // ARRAY_POSITION(NULL::VARIANT, ac) is 1 — but over pj, and over an array with neither, the
        // result PROPAGATES the NULL rather than returning FALSE. A JSON null needle is the mirror image.
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAY_CONTAINS(NULL::VARIANT, " + AC + ")"));
        assertNull(scalar("SELECT ARRAY_CONTAINS(NULL::VARIANT, " + PJ + ")"));
        assertNull(scalar("SELECT ARRAY_CONTAINS(NULL::VARIANT, ARRAY_CONSTRUCT(1, 2))"));
        assertEquals(Boolean.FALSE, scalar("SELECT ARRAY_CONTAINS(PARSE_JSON('null'), " + AC + ")"));
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAY_CONTAINS(PARSE_JSON('null'), " + PJ + ")"));

        assertEquals(1L, scalar("SELECT ARRAY_POSITION(NULL::VARIANT, " + AC + ")"));
        assertNull(scalar("SELECT ARRAY_POSITION(NULL::VARIANT, " + PJ + ")"));
        assertNull(scalar("SELECT ARRAY_POSITION(NULL::VARIANT, ARRAY_CONSTRUCT(1, 2))"));
        assertNull(scalar("SELECT ARRAY_POSITION(PARSE_JSON('null'), " + AC + ")"));
        assertEquals(1L, scalar("SELECT ARRAY_POSITION(PARSE_JSON('null'), " + PJ + ")"));
    }

    @Test
    public void arrayRemoveWithASqlNullValuePropagates() {
        // Live: ARRAY_REMOVE(ac, NULL) is SQL NULL (it does NOT strip the undefined);
        // ARRAY_REMOVE(ac, PARSE_JSON('null')) leaves [1,undefined,2]; only over pj does the JSON null go.
        assertNull(scalar("SELECT ARRAY_REMOVE(" + AC + ", NULL)"));
        assertEquals("[1,undefined,2]", text("SELECT ARRAY_REMOVE(" + AC + ", PARSE_JSON('null'))"));
        assertEquals("[1,2]", text("SELECT ARRAY_REMOVE(" + PJ + ", PARSE_JSON('null'))"));
        assertEquals("[1,2]", text("SELECT ARRAY_REMOVE_AT(" + AC + ", 1)"));
    }

    @Test
    public void arrayMinMaxIgnoreUndefinedButRankAJsonNullHighest() {
        // Live: ARRAY_MIN(ac) is 1 and ARRAY_MAX(ac) is 2 (the undefined is ignored), while ARRAY_MIN(pj)
        // is 1 and ARRAY_MAX(pj) is the JSON null; over an all-undefined array both are SQL NULL.
        assertEquals("1", text("SELECT ARRAY_MIN(" + AC + ")"));
        assertEquals("2", text("SELECT ARRAY_MAX(" + AC + ")"));
        assertEquals("1", text("SELECT ARRAY_MIN(" + PJ + ")"));
        assertEquals("null", text("SELECT ARRAY_MAX(" + PJ + ")"));
        assertNull(scalar("SELECT ARRAY_MIN(ARRAY_CONSTRUCT(NULL))"));
        assertNull(scalar("SELECT ARRAY_MAX(ARRAY_CONSTRUCT(NULL))"));
    }

    @Test
    public void positionalArrayFunctionsCarryTheElementThrough() {
        // Live: ARRAY_SLICE / ARRAY_CAT / ARRAY_REVERSE / ARRAY_FLATTEN all keep the element in place.
        assertEquals("[1,undefined]", text("SELECT ARRAY_SLICE(" + AC + ", 0, 2)"));
        assertEquals("[undefined,2]", text("SELECT ARRAY_SLICE(" + AC + ", 1, 3)"));
        assertEquals("[null,2]", text("SELECT ARRAY_SLICE(" + PJ + ", 1, 3)"));
        assertEquals("[1,undefined,2,9]", text("SELECT ARRAY_CAT(" + AC + ", ARRAY_CONSTRUCT(9))"));
        assertEquals("[1,null,2,undefined]", text("SELECT ARRAY_CAT(" + PJ + ", ARRAY_CONSTRUCT(NULL))"));
        assertEquals("[2,undefined,1]", text("SELECT ARRAY_REVERSE(" + AC + ")"));
        assertEquals("[1,undefined,2]",
            text("SELECT ARRAY_FLATTEN(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1, NULL), ARRAY_CONSTRUCT(2)))"));
    }

    @Test
    public void arrayDistinctMovesTheUndefinedLastOnlyWhenItDeduplicates() {
        // Live: an array with no duplicates comes back UNCHANGED —
        // ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL,1,2)) is [undefined,1,2] — but as soon as a duplicate is
        // dropped the undefined moves to the END: ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL,1,1)) is
        // [1,undefined] and ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL,2,NULL,1)) is [2,1,undefined]. A JSON
        // null keeps its first-occurrence position: ARRAY_DISTINCT of [null,1,null,2] is [null,1,2].
        assertEquals("[undefined,1,2]", text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL, 1, 2))"));
        assertEquals("[2,undefined,1]", text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(2, NULL, 1))"));
        assertEquals("[1,undefined]", text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL, 1, 1))"));
        assertEquals("[1,undefined]", text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL, NULL, 1))"));
        assertEquals("[2,1,undefined]", text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL, 2, NULL, 1))"));
        assertEquals("[1,2,undefined]", text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(1, NULL, 1, NULL, 2))"));
        assertEquals("[1,null,2]", text("SELECT ARRAY_DISTINCT(PARSE_JSON('[1,null,1,null,2]'))"));
        assertEquals("[null,1,undefined]",
            text("SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL, PARSE_JSON('null'), NULL,"
                + " PARSE_JSON('null'), 1))"));
    }

    @Test
    public void setFunctionsTreatTheTwoNullsAsDifferentElements() {
        // Live: ARRAYS_OVERLAP(ac, [NULL]) is TRUE but ARRAYS_OVERLAP(ac, PARSE_JSON('[null]')) is FALSE;
        // ARRAY_EXCEPT(ac, [NULL]) is [1,2] while ARRAY_EXCEPT(pj, [NULL]) is [1,null,2];
        // ARRAY_INTERSECTION(ac, [NULL]) is [undefined] while over pj it is [].
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAYS_OVERLAP(" + AC + ", ARRAY_CONSTRUCT(NULL))"));
        assertEquals(Boolean.FALSE, scalar("SELECT ARRAYS_OVERLAP(" + AC + ", PARSE_JSON('[null]'))"));
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAYS_OVERLAP(" + PJ + ", PARSE_JSON('[null]'))"));

        assertEquals("[undefined,2]", text("SELECT ARRAY_EXCEPT(" + AC + ", ARRAY_CONSTRUCT(1))"));
        assertEquals("[1,2]", text("SELECT ARRAY_EXCEPT(" + AC + ", ARRAY_CONSTRUCT(NULL))"));
        assertEquals("[1,null,2]", text("SELECT ARRAY_EXCEPT(" + PJ + ", ARRAY_CONSTRUCT(NULL))"));

        assertEquals("[undefined]", text("SELECT ARRAY_INTERSECTION(" + AC + ", ARRAY_CONSTRUCT(NULL))"));
        assertEquals("[]", text("SELECT ARRAY_INTERSECTION(" + PJ + ", ARRAY_CONSTRUCT(NULL))"));
        assertEquals("[1,2]", text("SELECT ARRAY_INTERSECTION(" + AC + ", " + PJ + ")"));
    }

    @Test
    public void setFunctionsAreMultisetOperations() {
        // Live: ARRAY_EXCEPT keeps max(0, N-M) copies — ARRAY_EXCEPT([1,1,2], []) is [1,1,2] and
        // ARRAY_EXCEPT(ARRAY_CONSTRUCT(NULL,1,NULL), ARRAY_CONSTRUCT(1)) is [undefined,undefined] —
        // while ARRAY_INTERSECTION keeps min(N,M), emitting the undefined last:
        // ARRAY_INTERSECTION(ARRAY_CONSTRUCT(NULL,1,NULL), ARRAY_CONSTRUCT(NULL,1)) is [1,undefined].
        assertEquals("[1,1,2]", text("SELECT ARRAY_EXCEPT(ARRAY_CONSTRUCT(1, 1, 2), ARRAY_CONSTRUCT())"));
        assertEquals("[undefined,undefined]",
            text("SELECT ARRAY_EXCEPT(ARRAY_CONSTRUCT(NULL, 1, NULL), ARRAY_CONSTRUCT(1))"));
        assertEquals("[1,undefined]",
            text("SELECT ARRAY_INTERSECTION(ARRAY_CONSTRUCT(NULL, 1, NULL), ARRAY_CONSTRUCT(NULL, 1))"));
    }

    @Test
    public void objectBuildersDowngradeAnUndefinedToAJsonNull() {
        // Live: ARRAYS_ZIP(ARRAY_CONSTRUCT(1,NULL), ARRAY_CONSTRUCT(2,3)) is
        // [{"$1":1,"$2":2},{"$1":null,"$2":3}] and ARRAYS_TO_OBJECT(['a','b'], ARRAY_CONSTRUCT(1,NULL)) is
        // {"a":1,"b":null} — moving an undefined into an OBJECT turns it into a JSON null.
        assertEquals("[{\"$1\":1,\"$2\":2},{\"$1\":null,\"$2\":3}]",
            text("SELECT ARRAYS_ZIP(ARRAY_CONSTRUCT(1, NULL), ARRAY_CONSTRUCT(2, 3))"));
        assertEquals("{\"a\":1,\"b\":null}",
            text("SELECT ARRAYS_TO_OBJECT(ARRAY_CONSTRUCT('a', 'b'), ARRAY_CONSTRUCT(1, NULL))"));
    }

    @Test
    public void arrayConstructCompactAndFilterStillWork() {
        // Live: ARRAY_CONSTRUCT_COMPACT(1,NULL,2) is [1,2] (the SQL NULLs never become elements) while
        // FILTER keeps an undefined that is already there.
        assertEquals("[1,2]", text("SELECT ARRAY_CONSTRUCT_COMPACT(1, NULL, 2)"));
        assertEquals("[1,undefined,2]", text("SELECT FILTER(" + AC + ", x -> TRUE)"));
    }

    @Test
    public void theSentinelNeverLeaksIntoScalarEvaluation() {
        // An `undefined` reads as SQL NULL the moment it leaves the variant layer, so no scalar sees the
        // token. Live: NVL(GET(ac,1),9) is 9, GET(ac,1)::VARCHAR is SQL NULL, TO_ARRAY(GET(ac,1)) is SQL
        // NULL and a REDUCE lambda sees SQL NULL (0+1+10+2 = 13 with COALESCE(x::int,10)).
        assertEquals("9", text("SELECT NVL(GET(" + AC + ", 1), 9)"));
        assertNull(scalar("SELECT GET(" + AC + ", 1)::VARCHAR"));
        assertNull(scalar("SELECT TO_ARRAY(GET(" + AC + ", 1))"));
        assertNull(scalar("SELECT GET(" + AC + ", 1) + 1"));
        assertNull(scalar("SELECT UPPER(GET(" + AC + ", 1))"));
        assertEquals("13", text("""
            SELECT REDUCE(ARRAY_CONSTRUCT(1, NULL, 2), 0, (acc, x) -> acc + COALESCE(x::int, 10))"""));
    }

    @Test
    public void theTokenSurvivesEveryTextRoundTrip() {
        // The canonical text carries Snowflake's bare token, so it must survive being re-parsed. Reading
        // the value back through PARSE_JSON, a VARIANT column and TO_JSON all reproduce it exactly.
        assertEquals("[1,undefined,2]", text("SELECT PARSE_JSON(TO_JSON(" + AC + "))"));
        assertEquals(Boolean.TRUE, scalar("SELECT PARSE_JSON(TO_JSON(" + AC + ")) = " + AC));
        assertNotNull(scalar("SELECT " + AC));
        assertTrue(text("SELECT ARRAY_SLICE(PARSE_JSON('[1,undefined,2]'), 1, 3)").startsWith("[undefined"));
    }
}
