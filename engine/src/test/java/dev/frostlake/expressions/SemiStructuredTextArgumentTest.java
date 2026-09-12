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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statically OBJECT- or ARRAY-typed value never coerces to the text a string function reads.
 * Frostlake used to accept every one of these and build its answer out of the JSON text, so
 * {@code 'x' || o} returned {@code x{"k":"v1"}} and — worse — {@code UPPER(o)} returned
 * {@code {"K":"V1"}}, an uppercased set of KEYS that still looked like an object.
 *
 * <p>Every expectation below was measured against a live Snowflake account on over a
 * populated OBJECT column and ARRAY column. The ACCEPTED forms matter more than the rejections:
 * string functions are shared machinery, so a rule even slightly too broad breaks ordinary queries.
 * Three bounds in particular were measured before the rule was written and are pinned here — a
 * VARIANT is never rejected even when it HOLDS an object, an EXPLICIT conversion
 * ({@code CAST(o AS VARCHAR)}, {@code TO_VARCHAR(o)}, {@code TO_CHAR(o)}) is legal where implicit
 * coercion is not, and {@code SEARCH} takes semi-structured data on purpose.
 */
public class SemiStructuredTextArgumentTest extends BaseDatabaseTest {

    /** Live: "Invalid argument types for function 'UPPER': (OBJECT)" (SQLSTATE 42P13, code 1044). */
    private static final String UPPER_OBJECT =
        "Invalid argument types for function 'UPPER': (OBJECT)";

    private static final String UPPER_ARRAY =
        "Invalid argument types for function 'UPPER': (ARRAY)";

    @BeforeEach
    public void createSemiStructuredTable() {
        engine.execute("CREATE TABLE stt (id INTEGER, o OBJECT, a ARRAY, v VARIANT, vo VARIANT,"
            + " s VARCHAR, n NUMBER)");
        engine.execute("INSERT INTO stt SELECT 1, OBJECT_CONSTRUCT('k', 'v1'), ARRAY_CONSTRUCT(1, 2),"
            + " TO_VARIANT(1), TO_VARIANT(OBJECT_CONSTRUCT('x', 1)), 'xy', 10");
        engine.execute("INSERT INTO stt SELECT 2, OBJECT_CONSTRUCT('k', 'v2'), ARRAY_CONSTRUCT(3, 4),"
            + " TO_VARIANT(2), TO_VARIANT(OBJECT_CONSTRUCT('x', 2)), 'zw', 20");
    }

    /** The exact message live Snowflake produces, asserted in full — not merely that it threw. */
    private void assertFails(final String sql, final String expectedMessage) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(error.getMessage().contains(expectedMessage),
            "expected [" + expectedMessage + "] for [" + sql + "] but got: " + error.getMessage());
    }

    private void assertAccepted(final String sql, final int expectedRows) {
        assertEquals(expectedRows, engine.executeQuery(sql).getRows().size(),
            "expected " + expectedRows + " row(s) from [" + sql + "]");
    }

    private void assertFirstValue(final String sql, final String expected) {
        assertEquals(expected, String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0)),
            "unexpected value from [" + sql + "]");
    }

    // ── The || operator ──────────────────────────────────────────────────────

    /** Live: "Invalid argument types for function '||': (VARCHAR(1), OBJECT)". */
    @Test
    public void concatOperatorRejectsSemiStructuredOnEitherSide() {
        assertFails("SELECT 'x' || o FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
        assertFails("SELECT o || 'x' FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (OBJECT, VARCHAR(1))");
        assertFails("SELECT o || o FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (OBJECT, OBJECT)");
        assertFails("SELECT 'x' || a FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (VARCHAR(1), ARRAY)");
        assertFails("SELECT a || 'x' FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (ARRAY, VARCHAR(1))");
        assertFails("SELECT o || a FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (OBJECT, ARRAY)");
    }

    /** Live rejects on an empty input too — the rule is a compile-time one. */
    @Test
    public void concatOperatorRejectsOverAnEmptyInput() {
        assertFails("SELECT 'x' || o FROM stt WHERE 1 = 0",
            "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
    }

    // ── CONCAT / CONCAT_WS / LISTAGG ─────────────────────────────────────────

    /** Live: "Invalid argument types for function 'CONCAT': (VARCHAR(1), OBJECT)". */
    @Test
    public void concatFunctionsRejectSemiStructuredInAnyPosition() {
        assertFails("SELECT CONCAT('x', o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONCAT': (VARCHAR(1), OBJECT)");
        assertFails("SELECT CONCAT('x', a) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONCAT': (VARCHAR(1), ARRAY)");
        assertFails("SELECT CONCAT(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONCAT': (OBJECT)");
        assertFails("SELECT CONCAT(s, s, o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONCAT': (VARCHAR(16777216), VARCHAR(16777216), OBJECT)");
    }

    /** Live reports CONCAT_WS as 'CONCAT' — the name it desugars to. */
    @Test
    public void concatWsReportsItselfAsConcat() {
        assertFails("SELECT CONCAT_WS(',', s, o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONCAT':");
        assertFails("SELECT CONCAT_WS(o, s, s) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONCAT':");
    }

    /** Live: "Invalid argument types for function 'LISTAGG': (OBJECT)", both positions. */
    @Test
    public void listaggRejectsSemiStructuredInBothPositions() {
        assertFails("SELECT LISTAGG(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LISTAGG': (OBJECT)");
        assertFails("SELECT LISTAGG(a) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LISTAGG': (ARRAY)");
        assertFails("SELECT LISTAGG(o, ',') FROM stt ORDER BY 1",
            "Invalid argument types for function 'LISTAGG': (OBJECT, VARCHAR(1))");
        assertFails("SELECT LISTAGG(s, o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LISTAGG': (VARCHAR(16777216), OBJECT)");
    }

    /** The DISTINCT and WITHIN GROUP forms reject identically on live. */
    @Test
    public void listaggRejectsItsDistinctAndWithinGroupForms() {
        assertFails("SELECT LISTAGG(DISTINCT o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LISTAGG': (OBJECT)");
        assertFails("SELECT LISTAGG(o, ',') WITHIN GROUP (ORDER BY id) FROM stt",
            "Invalid argument types for function 'LISTAGG': (OBJECT, VARCHAR(1))");
    }

    /**
     * A structured MAP is refused by the text family too, naming its whole parameterised type. It was
     * missed until because {@code MapType} is deliberately not an {@code ObjectType}
     * subclass, so {@code 'x' || sm} returned {@code x{"k":1}} and {@code UPPER(sm)} returned
     * {@code {"K":1}} — the same silent stringification the plain OBJECT case already stopped.
     */
    @Test
    public void aStructuredMapIsRefusedByTheTextFamilyToo() {
        engine.execute("CREATE TABLE sttm (id INTEGER, sm MAP(VARCHAR, INT))");
        engine.execute("INSERT INTO sttm SELECT 1, OBJECT_CONSTRUCT('k', 1)::MAP(VARCHAR, INT)");
        assertFails("SELECT 'x' || sm FROM sttm", "Invalid argument types for function '||':"
            + " (VARCHAR(1), MAP(VARCHAR(16777216), NUMBER(38,0)))");
        assertFails("SELECT UPPER(sm) FROM sttm", "Invalid argument types for function 'UPPER':"
            + " (MAP(VARCHAR(16777216), NUMBER(38,0)))");
        assertFails("SELECT LISTAGG(sm) FROM sttm", "Invalid argument types for function 'LISTAGG':"
            + " (MAP(VARCHAR(16777216), NUMBER(38,0)))");
    }

    // ── The bound: a VARIANT is never rejected ───────────────────────────────

    /**
     * Live accepts every one of these. {@code 'x' || vo} returned {@code x{"x":1}} and
     * {@code LISTAGG(vo)} returned {@code {"x":1}{"x":2}} — the rule is keyed on the DECLARED type,
     * so a VARIANT passes even when it holds an object.
     */
    @Test
    public void aVariantIsAcceptedEvenWhenItHoldsAnObject() {
        assertAccepted("SELECT 'x' || v FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT v || 'x' FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT 'x' || vo FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT CONCAT('x', vo) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT LISTAGG(v) FROM stt ORDER BY 1", 1);
        assertAccepted("SELECT LISTAGG(vo) FROM stt ORDER BY 1", 1);
    }

    /** An OBJECT cast to VARIANT is accepted; the same value cast to OBJECT is not. */
    @Test
    public void castingToVariantMakesItAcceptableAgain() {
        assertAccepted("SELECT 'x' || o::VARIANT FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT 'x' || TO_VARIANT(o) FROM stt ORDER BY 1", 2);
        assertFails("SELECT 'x' || v::OBJECT FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
    }

    /**
     * The TRY_CAST spelling of that widening behaves identically, which guards the boundary from the
     * other side: TRY_CAST now reaches a semi-structured target, and what it produces must still carry
     * the DECLARED type into these rules rather than slipping past them. Live,
     * {@code UPPER(TRY_CAST(o AS VARIANT))} returns {@code {"K":"V1"}} and
     * {@code 'x' || TRY_CAST(o AS VARIANT)} concatenates, while the OBJECT target of each is refused
     * with the same sentence the {@code ::} spelling gets.
     */
    @Test
    public void theTryCastSpellingOfTheWideningIsAcceptedToo() {
        assertAccepted("SELECT 'x' || TRY_CAST(o AS VARIANT) FROM stt ORDER BY 1", 2);
        assertFirstValue("SELECT UPPER(TRY_CAST(o AS VARIANT)) FROM stt ORDER BY 1", "{\"K\":\"V1\"}");
        assertFirstValue("SELECT 'x' || TRY_CAST(o AS VARIANT) FROM stt ORDER BY 1", "x{\"k\":\"v1\"}");
        assertFails("SELECT UPPER(TRY_CAST(o AS OBJECT)) FROM stt ORDER BY 1", UPPER_OBJECT);
        assertFails("SELECT 'x' || TRY_CAST(o AS OBJECT) FROM stt ORDER BY 1",
            "Invalid argument types for function '||': (VARCHAR(1), OBJECT)");
    }

    // ── The bound: an EXPLICIT conversion is legal ───────────────────────────

    /**
     * The trap this rule must not fall into. Live ACCEPTS all three explicit conversions of an OBJECT
     * to text — a rule phrased as "an OBJECT cannot become a string" would break every one.
     */
    @Test
    public void explicitConversionsToTextStayAccepted() {
        assertFirstValue("SELECT CAST(o AS VARCHAR) FROM stt ORDER BY 1", "{\"k\":\"v1\"}");
        assertFirstValue("SELECT TO_VARCHAR(o) FROM stt ORDER BY 1", "{\"k\":\"v1\"}");
        assertFirstValue("SELECT TO_CHAR(o) FROM stt ORDER BY 1", "{\"k\":\"v1\"}");
        assertFirstValue("SELECT o::VARCHAR FROM stt ORDER BY 1", "{\"k\":\"v1\"}");
        assertFirstValue("SELECT TO_VARCHAR(a) FROM stt ORDER BY 1", "[1,2]");
    }

    /**
     * The escape hatch above belongs to the PLAIN types alone. A STRUCTURED value has no text
     * conversion at all — live, {@code CAST(so AS VARCHAR)} and {@code TO_VARCHAR(so)} are
     * "invalid type […] for parameter 'TO_VARCHAR'" — so its way out is a conversion to VARIANT or to
     * a plain OBJECT first. Pinned here, beside the acceptance it diverges from, so the two can never
     * be conflated; the rule itself is exercised in {@code StructuredTypeArgumentRejectionTest}.
     */
    @Test
    public void aStructuredValueHasNoSuchTextConversion() {
        engine.execute("CREATE TABLE sttc (so OBJECT(x VARCHAR))");
        engine.execute("INSERT INTO sttc SELECT OBJECT_CONSTRUCT('x', 'a')::OBJECT(x VARCHAR)");
        assertFails("SELECT CAST(so AS VARCHAR) FROM sttc",
            "invalid type [CAST(STTC.SO AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TO_VARCHAR(so) FROM sttc",
            "invalid type [TO_VARCHAR(STTC.SO)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT UPPER(CAST(so AS VARCHAR)) FROM sttc", "for parameter 'TO_VARCHAR'");
        assertFirstValue("SELECT UPPER(so::VARIANT::VARCHAR) FROM sttc", "{\"X\":\"A\"}");
    }

    /** And the converted value flows on into the text functions that refused the original. */
    @Test
    public void aConvertedValueFlowsOnIntoTheTextFunctions() {
        assertAccepted("SELECT TO_VARCHAR(o) || 'x' FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT 'x' || CAST(o AS VARCHAR) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT CONCAT('x', TO_JSON(o)) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT LISTAGG(TO_VARCHAR(o)) FROM stt ORDER BY 1", 1);
    }

    // ── The bound: ordinary types still concatenate ──────────────────────────

    /** Live joins NUMBER, BOOLEAN, DATE and an untyped NULL to text without complaint. */
    @Test
    public void ordinaryTypesStillConcatenate() {
        assertAccepted("SELECT 'x' || n FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT 'x' || s FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT 'x' || NULL FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT CONCAT(s, n) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT LISTAGG(s, ',') FROM stt ORDER BY 1", 1);
        assertAccepted("SELECT LISTAGG(n) FROM stt ORDER BY 1", 1);
    }

    /** A path or index read yields VARIANT, so it joins and uppercases fine — live-verified. */
    @Test
    public void readingIntoTheValueYieldsAVariantThatIsAccepted() {
        assertAccepted("SELECT 'x' || o:k FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT 'x' || a[0] FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT CONCAT('x', GET(o, 'k')) FROM stt ORDER BY 1", 2);
    }

    // ── The scalar string family ─────────────────────────────────────────────

    /**
     * {@code UPPER(o)} is the case that made this worth fixing: Frostlake did not merely stringify,
     * it uppercased the JSON KEYS and returned {@code {"K":"V1"}} — a corrupted structure that still
     * read as an object.
     */
    @Test
    public void upperAndLowerRejectSemiStructured() {
        assertFails("SELECT UPPER(o) FROM stt ORDER BY 1", UPPER_OBJECT);
        assertFails("SELECT UPPER(a) FROM stt ORDER BY 1", UPPER_ARRAY);
        assertFails("SELECT LOWER(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LOWER': (OBJECT)");
        assertFails("SELECT LOWER(a) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LOWER': (ARRAY)");
    }

    /** The measuring, trimming and padding functions, each live-verified over both columns. */
    @Test
    public void theMeasuringAndTrimmingFunctionsRejectSemiStructured() {
        assertFails("SELECT LENGTH(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LENGTH': (OBJECT)");
        assertFails("SELECT LENGTH(a) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LENGTH': (ARRAY)");
        assertFails("SELECT LEN(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LEN': (OBJECT)");
        assertFails("SELECT OCTET_LENGTH(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'OCTET_LENGTH': (OBJECT)");
        assertFails("SELECT TRIM(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'TRIM': (OBJECT)");
        assertFails("SELECT LTRIM(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'LTRIM': (OBJECT)");
        assertFails("SELECT RTRIM(a) FROM stt ORDER BY 1",
            "Invalid argument types for function 'RTRIM': (ARRAY)");
        assertFails("SELECT RPAD(o, 20, '.') FROM stt ORDER BY 1",
            "Invalid argument types for function 'RPAD': (OBJECT, NUMBER(2,0), VARCHAR(1))");
        assertFails("SELECT LPAD(o, 20, '.') FROM stt ORDER BY 1",
            "Invalid argument types for function 'LPAD': (OBJECT, NUMBER(2,0), VARCHAR(1))");
    }

    /** Substring, replacement and splitting, plus the regular-expression family. */
    @Test
    public void theSubstringAndRegexFunctionsRejectSemiStructured() {
        assertFails("SELECT SUBSTR(o, 1, 1) FROM stt ORDER BY 1",
            "Invalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(1,0))");
        assertFails("SELECT REPLACE(o, 'a', 'b') FROM stt ORDER BY 1",
            "Invalid argument types for function 'REPLACE': (OBJECT, VARCHAR(1), VARCHAR(1))");
        assertFails("SELECT SPLIT_PART(o, ',', 1) FROM stt ORDER BY 1",
            "Invalid argument types for function 'SPLIT_PART': (OBJECT, VARCHAR(1), NUMBER(1,0))");
        assertFails("SELECT SPLIT(o, ',') FROM stt ORDER BY 1",
            "Invalid argument types for function 'SPLIT': (OBJECT, VARCHAR(1))");
        assertFails("SELECT REGEXP_COUNT(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'REGEXP_COUNT': (OBJECT, VARCHAR(1))");
        assertFails("SELECT REGEXP_REPLACE(o, 'k', 'z') FROM stt ORDER BY 1",
            "Invalid argument types for function 'REGEXP_REPLACE': (OBJECT, VARCHAR(1), VARCHAR(1))");
        assertFails("SELECT REGEXP_SUBSTR(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'REGEXP_SUBSTR': (OBJECT, VARCHAR(1))");
        assertFails("SELECT INITCAP(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'INITCAP': (OBJECT)");
        assertFails("SELECT REVERSE(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'REVERSE': (OBJECT)");
        assertFails("SELECT SOUNDEX(o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'SOUNDEX': (OBJECT)");
    }

    /** The searching and comparing functions, whose subject is text on both sides. */
    @Test
    public void theSearchingFunctionsRejectSemiStructured() {
        assertFails("SELECT CONTAINS(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONTAINS': (OBJECT, VARCHAR(1))");
        assertFails("SELECT STARTSWITH(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'STARTSWITH': (OBJECT, VARCHAR(1))");
        assertFails("SELECT ENDSWITH(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'ENDSWITH': (OBJECT, VARCHAR(1))");
        assertFails("SELECT CHARINDEX(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'CHARINDEX': (OBJECT, VARCHAR(1))");
        assertFails("SELECT POSITION('a', o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'POSITION': (VARCHAR(1), OBJECT)");
        assertFails("SELECT EDITDISTANCE(o, 'k') FROM stt ORDER BY 1",
            "Invalid argument types for function 'EDITDISTANCE': (OBJECT, VARCHAR(1))");
    }

    /**
     * Every argument position refuses one, not merely the string-shaped ones — measured live, since a
     * semi-structured value coerces to the NUMBER positions no better than to the VARCHAR ones.
     */
    @Test
    public void everyArgumentPositionRefusesSemiStructured() {
        assertFails("SELECT SPLIT_PART(s, o, 1) FROM stt ORDER BY 1",
            "Invalid argument types for function 'SPLIT_PART': (VARCHAR(16777216), OBJECT, NUMBER(1,0))");
        assertFails("SELECT SPLIT_PART(s, ',', o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'SPLIT_PART': (VARCHAR(16777216), VARCHAR(1), OBJECT)");
        assertFails("SELECT RPAD(s, 20, o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'RPAD': (VARCHAR(16777216), NUMBER(2,0), OBJECT)");
        assertFails("SELECT REGEXP_REPLACE(s, o, 'z') FROM stt ORDER BY 1",
            "Invalid argument types for function 'REGEXP_REPLACE':"
                + " (VARCHAR(16777216), OBJECT, VARCHAR(1))");
        assertFails("SELECT REGEXP_REPLACE(s, 'x', o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'REGEXP_REPLACE':"
                + " (VARCHAR(16777216), VARCHAR(1), OBJECT)");
        assertFails("SELECT REPLACE(s, o, 'z') FROM stt ORDER BY 1",
            "Invalid argument types for function 'REPLACE': (VARCHAR(16777216), OBJECT, VARCHAR(1))");
        assertFails("SELECT CONTAINS(s, o) FROM stt ORDER BY 1",
            "Invalid argument types for function 'CONTAINS': (VARCHAR(16777216), OBJECT)");
        assertFails("SELECT TRANSLATE(s, o, 'cd') FROM stt ORDER BY 1",
            "Invalid argument types for function 'TRANSLATE': (VARCHAR(16777216), OBJECT, VARCHAR(2))");
    }

    // ── The bound: the declared type is read through wrappers ────────────────

    /**
     * A conditional over OBJECT branches IS an OBJECT, and so is a derived or CTE column — live
     * rejects {@code UPPER(IFF(TRUE, o, o))} and {@code UPPER(x)} over {@code (SELECT o AS x FROM t)}
     * with the same message as the bare column.
     */
    @Test
    public void theDeclaredTypeIsReadThroughConditionalsAndDerivedColumns() {
        assertFails("SELECT UPPER(IFF(TRUE, o, o)) FROM stt ORDER BY 1", UPPER_OBJECT);
        assertFails("SELECT UPPER(COALESCE(o, o)) FROM stt ORDER BY 1", UPPER_OBJECT);
        assertFails("SELECT UPPER(CASE WHEN TRUE THEN o ELSE o END) FROM stt ORDER BY 1", UPPER_OBJECT);
        assertFails("SELECT UPPER(x) FROM (SELECT o AS x FROM stt)", UPPER_OBJECT);
        assertFails("WITH c AS (SELECT o AS x FROM stt) SELECT UPPER(x) FROM c", UPPER_OBJECT);
        assertFails("SELECT UPPER(o) FROM stt WHERE 1 = 0", UPPER_OBJECT);
    }

    /** The producing functions declare their own type, so live rejects those results too. */
    @Test
    public void theSemiStructuredProducersAreRejectedAsArguments() {
        assertFails("SELECT UPPER(OBJECT_CONSTRUCT('k', 1)) FROM stt ORDER BY 1", UPPER_OBJECT);
        assertFails("SELECT UPPER(ARRAY_CONSTRUCT(1)) FROM stt ORDER BY 1", UPPER_ARRAY);
        assertFails("SELECT UPPER(SPLIT('a,b', ',')) FROM stt ORDER BY 1", UPPER_ARRAY);
        assertFails("SELECT UPPER(OBJECT_KEYS(o)) FROM stt ORDER BY 1", UPPER_ARRAY);
        assertFails("SELECT UPPER(ARRAY_AGG(v)) FROM stt ORDER BY 1", UPPER_ARRAY);
        assertFails("SELECT UPPER(OBJECT_AGG(s, v)) FROM stt ORDER BY 1", UPPER_OBJECT);
    }

    // ── The bound: the rest of the string surface is untouched ───────────────

    /** Live accepts a VARIANT everywhere a text function takes text, holding an object or not. */
    @Test
    public void theStringFunctionsStillTakeAVariant() {
        assertAccepted("SELECT UPPER(v) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT UPPER(vo) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT LENGTH(vo) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT SPLIT_PART(vo, ',', 1) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT REPLACE(vo, 'x', 'y') FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT UPPER(o::VARIANT) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT UPPER(TO_VARIANT(o)) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT UPPER(PARSE_JSON('{\"a\":1}')) FROM stt ORDER BY 1", 2);
    }

    /** And every ordinary type, which live coerces to text without complaint. */
    @Test
    public void theStringFunctionsStillTakeOrdinaryTypes() {
        assertAccepted("SELECT UPPER(s) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT UPPER(n) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT LENGTH(s) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT SPLIT_PART(n, ',', 1) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT SUBSTR(s, 1, 1) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT UPPER(NULL) FROM stt ORDER BY 1", 2);
    }

    /** An explicit conversion re-opens the whole family — live-verified on all three forms. */
    @Test
    public void anExplicitConversionReopensTheStringFunctions() {
        assertFirstValue("SELECT UPPER(o::VARCHAR) FROM stt ORDER BY 1", "{\"K\":\"V1\"}");
        assertFirstValue("SELECT UPPER(TO_VARCHAR(o)) FROM stt ORDER BY 1", "{\"K\":\"V1\"}");
        assertFirstValue("SELECT UPPER(TO_JSON(o)) FROM stt ORDER BY 1", "{\"K\":\"V1\"}");
        assertAccepted("SELECT LENGTH(CAST(o AS VARCHAR)) FROM stt ORDER BY 1", 2);
    }

    /**
     * {@code SEARCH} takes semi-structured data on purpose — live returned TRUE for
     * {@code SEARCH(o, 'k')} — so it is deliberately not in the family. The semi-structured surface
     * proper is untouched as well.
     */
    @Test
    public void theSemiStructuredAwareFunctionsAreUntouched() {
        assertAccepted("SELECT SEARCH(o, 'k') FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT ARRAY_TO_STRING(a, ',') FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT OBJECT_KEYS(o) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT TYPEOF(o) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT ARRAY_SIZE(a) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT OBJECT_INSERT(o, 'j', 2) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT COALESCE(o, o) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT GREATEST(o, o) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT HASH(o) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT ARRAY_CONSTRUCT(o) FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT OBJECT_CONSTRUCT('k', o) FROM stt ORDER BY 1", 2);
    }

    /** Grouping, sorting, DISTINCT and comparison keys still take a semi-structured value. */
    @Test
    public void keyPositionsStillAcceptSemiStructured() {
        assertAccepted("SELECT COUNT(*) FROM stt GROUP BY o", 2);
        assertAccepted("SELECT id FROM stt ORDER BY o", 2);
        assertAccepted("SELECT DISTINCT o FROM stt ORDER BY 1", 2);
        assertAccepted("SELECT COUNT(o) FROM stt ORDER BY 1", 1);
        assertAccepted("SELECT id FROM stt WHERE o = o", 2);
    }
}
