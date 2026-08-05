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
 * Where a STRUCTURED type may not go although the plain semi-structured one may. Snowflake treats
 * {@code OBJECT} and {@code OBJECT(x VARCHAR)} as different types and they genuinely DIVERGE: over one
 * table carrying both, {@code CAST(o AS VARCHAR)} returns {@code {"k":"v1"}} while
 * {@code CAST(so AS VARCHAR)} is a compile error, and {@code ARRAY_AGG(o)} collects its objects while
 * {@code ARRAY_AGG(so)} is refused. Frostlake accepted every one of these and handed back the JSON
 * text.
 *
 * <p>Every expectation below was measured against a live Snowflake account on over
 * populated {@code OBJECT(x VARCHAR)}, {@code ARRAY(INT)} and {@code MAP(VARCHAR, INT)} columns
 * sitting beside plain {@code OBJECT}, {@code ARRAY} and {@code VARIANT} ones — all three structured
 * kinds, one statement at a time, because they were NOT assumed to agree.
 *
 * <p>The PAIRED guards are the point of this class rather than a courtesy: each rejection is asserted
 * next to the plain-typed statement that must keep working. A rule keyed on "is this semi-structured?"
 * would satisfy half of these and break the other half, and that is the mistake this file exists to
 * catch.
 *
 * <p>The widest guard of all is the KEY positions. It would be natural to assume a structured value is
 * refused wherever a value must be ordered or compared; live says otherwise, and it was measured for
 * each of the three kinds: {@code GROUP BY}, {@code ORDER BY}, {@code DISTINCT}, {@code PARTITION BY},
 * the set operators, joins, {@code IN} and {@code COUNT(DISTINCT …)} all take a structured value.
 */
public class StructuredTypeArgumentRejectionTest extends BaseDatabaseTest {

    @BeforeEach
    public void createStructuredTable() {
        engine.execute("CREATE TABLE stt (id INTEGER, so OBJECT(x VARCHAR), sa ARRAY(INT),"
            + " sm MAP(VARCHAR, INT), o OBJECT, a ARRAY, v VARIANT, s VARCHAR, n NUMBER)");
        engine.execute("INSERT INTO stt SELECT 1, OBJECT_CONSTRUCT('x', 'a')::OBJECT(x VARCHAR),"
            + " [1, 2]::ARRAY(INT), OBJECT_CONSTRUCT('k', 1)::MAP(VARCHAR, INT),"
            + " OBJECT_CONSTRUCT('k', 'v1'), ARRAY_CONSTRUCT(1, 2), PARSE_JSON('{\"x\":1}'), 'aa', 10");
        engine.execute("INSERT INTO stt SELECT 2, OBJECT_CONSTRUCT('x', 'b')::OBJECT(x VARCHAR),"
            + " [3, 4]::ARRAY(INT), OBJECT_CONSTRUCT('k', 2)::MAP(VARCHAR, INT),"
            + " OBJECT_CONSTRUCT('k', 'v2'), ARRAY_CONSTRUCT(3, 4), PARSE_JSON('{\"x\":2}'), 'bb', 20");
    }

    /** The full type names live spells in these messages, for the three columns above. */
    private static final String OBJECT_TYPE = "OBJECT(x VARCHAR(16777216))";
    private static final String ARRAY_TYPE = "ARRAY(NUMBER(38,0))";
    private static final String MAP_TYPE = "MAP(VARCHAR(16777216), NUMBER(38,0))";

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

    private void assertValue(final String sql, final String expected) {
        assertEquals(expected, String.valueOf(
            engine.executeQuery(sql).getRows().get(0).getValues().get(0)), "for [" + sql + "]");
    }

    // ── The cast to text ─────────────────────────────────────────────────────

    /**
     * Live: "invalid type [CAST(ST.SO AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'" (SQLSTATE
     * 22023, vendor code 1007) — a fourth message shape, quoting the CALL back rather than listing
     * argument types. Frostlake renders the cast as WRITTEN where live renders it from its analysed
     * plan, so the column comes out unqualified; the sentence, the bracket contents' shape and the
     * parameter name are the live ones.
     */
    @Test
    public void castingAStructuredObjectToVarcharIsRejected() {
        assertFails("SELECT CAST(so AS VARCHAR) FROM stt",
            "SQL compilation error:\ninvalid type [CAST(STT.SO AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT so::VARCHAR FROM stt",
            "SQL compilation error:\ninvalid type [CAST(STT.SO AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
    }

    /** A structured ARRAY and a MAP refuse the same cast; all three kinds were measured separately. */
    @Test
    public void castingAStructuredArrayOrMapToVarcharIsRejected() {
        assertFails("SELECT CAST(sa AS VARCHAR) FROM stt",
            "invalid type [CAST(STT.SA AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT CAST(sm AS VARCHAR) FROM stt",
            "invalid type [CAST(STT.SM AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT sm::VARCHAR FROM stt",
            "invalid type [CAST(STT.SM AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
    }

    /**
     * Every spelling of a text target routes through the same conversion, and live renders them all as
     * VARCHAR keeping only the length: {@code CHAR(5)} is reported as {@code VARCHAR(5)} and a bare
     * {@code NVARCHAR} as {@code VARCHAR(134217728)}.
     */
    @Test
    public void everyTextTargetSpellingIsRejected() {
        assertFails("SELECT CAST(so AS TEXT) FROM stt", "CAST(STT.SO AS VARCHAR(134217728))");
        assertFails("SELECT CAST(so AS STRING) FROM stt", "CAST(STT.SO AS VARCHAR(134217728))");
        assertFails("SELECT CAST(so AS NVARCHAR) FROM stt", "CAST(STT.SO AS VARCHAR(134217728))");
        assertFails("SELECT CAST(so AS VARCHAR(20)) FROM stt", "CAST(STT.SO AS VARCHAR(20))");
        assertFails("SELECT CAST(so AS CHAR(5)) FROM stt", "CAST(STT.SO AS VARCHAR(5))");
        assertFails("SELECT CAST(so AS CHARACTER(3)) FROM stt", "CAST(STT.SO AS VARCHAR(3))");
    }

    /** A TRY_CAST renders WITHOUT its target live, and is refused with the conversion sentence too. */
    @Test
    public void tryCastToVarcharOverStructuredIsRejected() {
        assertFails("SELECT TRY_CAST(so AS VARCHAR) FROM stt",
            "SQL compilation error:\ninvalid type [TRY_CAST(STT.SO)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TRY_CAST(sm AS VARCHAR) FROM stt",
            "SQL compilation error:\ninvalid type [TRY_CAST(STT.SM)] for parameter 'TO_VARCHAR'");
    }

    /** The FUNCTION spelling of the same conversion, which reports the name as WRITTEN. */
    @Test
    public void toVarcharAndToCharOverStructuredAreRejected() {
        assertFails("SELECT TO_VARCHAR(so) FROM stt",
            "SQL compilation error:\ninvalid type [TO_VARCHAR(STT.SO)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TO_CHAR(so) FROM stt",
            "SQL compilation error:\ninvalid type [TO_CHAR(STT.SO)] for parameter 'TO_CHAR'");
        assertFails("SELECT TO_VARCHAR(sa) FROM stt", "invalid type [TO_VARCHAR(STT.SA)]");
        assertFails("SELECT TO_CHAR(sm) FROM stt", "invalid type [TO_CHAR(STT.SM)]");
    }

    /**
     * The guard that matters most for the cast rule: the PLAIN types still render to their JSON text,
     * exactly as live does. If this ever starts failing, the structured rule has bled into the
     * unstructured path.
     */
    @Test
    public void plainSemiStructuredStillCastsToText() {
        assertValue("SELECT CAST(o AS VARCHAR) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT o::VARCHAR FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT TO_VARCHAR(o) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT TO_CHAR(a) FROM stt WHERE id = 1", "[1,2]");
        assertValue("SELECT CAST(a AS VARCHAR) FROM stt WHERE id = 1", "[1,2]");
        assertValue("SELECT TO_VARCHAR(v) FROM stt WHERE id = 1", "{\"x\":1}");
    }

    /**
     * TEXT targets only. A cast to VARIANT, to a plain OBJECT / ARRAY or to a structured type is legal
     * live and returns the value — {@code CAST(so AS VARIANT)}, {@code CAST(sm AS OBJECT)} and
     * {@code CAST(sa AS ARRAY(INT))} were each run — and that is exactly how a caller reaches the text.
     */
    @Test
    public void castsToSemiStructuredAndStructuredTargetsStayLegal() {
        assertAccepted("SELECT CAST(so AS VARIANT) FROM stt", 2);
        assertAccepted("SELECT CAST(so AS OBJECT) FROM stt", 2);
        assertAccepted("SELECT CAST(sa AS ARRAY) FROM stt", 2);
        assertAccepted("SELECT CAST(sm AS OBJECT) FROM stt", 2);
        assertAccepted("SELECT CAST(so AS OBJECT(x VARCHAR)) FROM stt", 2);
        assertAccepted("SELECT CAST(sa AS ARRAY(INT)) FROM stt", 2);
        assertAccepted("SELECT CAST(sm AS MAP(VARCHAR, INT)) FROM stt", 2);
        assertValue("SELECT CAST(so::VARIANT AS VARCHAR) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
        assertValue("SELECT TO_VARCHAR(TO_VARIANT(so)) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
    }

    // ── TRY_CAST into a PLAIN semi-structured target ─────────────────────────

    /**
     * A VARIANT target takes any semi-structured source, and each of the six shapes was run against a
     * live account on rather than inferred from its neighbours: plain OBJECT, plain ARRAY,
     * VARIANT, structured OBJECT, structured ARRAY and MAP all come back with the value they were
     * given. Frostlake used to refuse every one of them — the TRY_CAST source rule was generalized
     * from SCALAR targets alone, where rejecting a semi-structured source is right.
     */
    @Test
    public void tryCastToVariantTakesEverySemiStructuredSource() {
        assertValue("SELECT TRY_CAST(o AS VARIANT) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT TRY_CAST(a AS VARIANT) FROM stt WHERE id = 1", "[1,2]");
        assertValue("SELECT TRY_CAST(v AS VARIANT) FROM stt WHERE id = 1", "{\"x\":1}");
        assertValue("SELECT TRY_CAST(so AS VARIANT) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
        assertValue("SELECT TRY_CAST(sa AS VARIANT) FROM stt WHERE id = 1", "[1,2]");
        assertValue("SELECT TRY_CAST(sm AS VARIANT) FROM stt WHERE id = 1", "{\"k\":1}");
    }

    /**
     * A plain OBJECT target takes the OBJECT FAMILY only — plain OBJECT, structured OBJECT and MAP.
     * The three refusals beside them are what stops this from being read as "any semi-structured
     * source": live gives {@code TRY_CAST(a AS OBJECT)} "invalid type [TRY_CAST(ST.A)] for parameter
     * 'TO_OBJECT'", {@code (sa …)} "Unsupported data type 'STRUCTURED_ARRAY'." and {@code (v …)} the
     * TRY_CAST-arguments sentence Frostlake keeps.
     */
    @Test
    public void tryCastToPlainObjectTakesTheObjectFamilyOnly() {
        assertValue("SELECT TRY_CAST(o AS OBJECT) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT TRY_CAST(so AS OBJECT) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
        assertValue("SELECT TRY_CAST(sm AS OBJECT) FROM stt WHERE id = 1", "{\"k\":1}");
        assertFails("SELECT TRY_CAST(a AS OBJECT) FROM stt",
            "invalid type [TRY_CAST(STT.A)] for parameter 'TO_OBJECT'");
        assertFails("SELECT TRY_CAST(sa AS OBJECT) FROM stt",
            "Unsupported data type 'STRUCTURED_ARRAY'.");
        assertFails("SELECT TRY_CAST(v AS OBJECT) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types VARIANT and OBJECT");
    }

    /**
     * A plain ARRAY target takes the ARRAY FAMILY only. The OBJECT-side sources are refused live —
     * {@code TRY_CAST(o AS ARRAY)} and {@code (v …)} give the TRY_CAST-arguments sentence and
     * {@code (so …)} "Unsupported data type 'STRUCTURED_OBJECT'." — even though the very same
     * {@code CAST} succeeds, which is the whole reason this rule cannot be shared with CAST.
     */
    @Test
    public void tryCastToPlainArrayTakesTheArrayFamilyOnly() {
        assertValue("SELECT TRY_CAST(a AS ARRAY) FROM stt WHERE id = 1", "[1,2]");
        assertValue("SELECT TRY_CAST(sa AS ARRAY) FROM stt WHERE id = 1", "[1,2]");
        assertFails("SELECT TRY_CAST(o AS ARRAY) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types OBJECT and ARRAY");
        assertFails("SELECT TRY_CAST(v AS ARRAY) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types VARIANT and ARRAY");
        assertFails("SELECT TRY_CAST(so AS ARRAY) FROM stt",
            "Unsupported data type 'STRUCTURED_OBJECT'.");
    }

    /**
     * The VARIANT source is where the family rule bites hardest, so it is asserted on its own: a
     * VARIANT reaches only a VARIANT target. Measured twice live, on the column and on a
     * {@code PARSE_JSON} expression, so the reading is about the TYPE and not about how the value
     * arrived.
     */
    @Test
    public void aVariantSourceReachesOnlyAVariantTarget() {
        assertValue("SELECT TRY_CAST(PARSE_JSON('{\"k\":1}') AS VARIANT) AS r", "{\"k\":1}");
        assertFails("SELECT TRY_CAST(PARSE_JSON('{\"k\":1}') AS OBJECT) AS r",
            "Function TRY_CAST cannot be used with arguments of types VARIANT and OBJECT");
        assertValue("SELECT TRY_CAST(TRY_CAST(o AS VARIANT) AS VARIANT) FROM stt WHERE id = 1",
            "{\"k\":\"v1\"}");
    }

    /**
     * What the accepted casts hand back: the value UNCHANGED, not a converted or re-wrapped one.
     * {@code TRY_CAST(o AS VARIANT) = o} is TRUE live and TYPEOF still reports what went in.
     */
    @Test
    public void anAcceptedSemiStructuredTryCastReturnsTheValueUnchanged() {
        assertValue("SELECT TYPEOF(TRY_CAST(o AS VARIANT)) FROM stt WHERE id = 1", "OBJECT");
        assertValue("SELECT TYPEOF(TRY_CAST(a AS VARIANT)) FROM stt WHERE id = 1", "ARRAY");
        assertValue("SELECT TRY_CAST(o AS VARIANT) = o FROM stt WHERE id = 1", "true");
        assertValue("SELECT ARRAY_SIZE(TRY_CAST(a AS ARRAY)) FROM stt WHERE id = 1", "2");
        assertValue("SELECT GET(TRY_CAST(o AS OBJECT), 'k') FROM stt WHERE id = 1", "v1");
    }

    /**
     * TRY_CAST still yields NULL rather than erroring when the conversion cannot be made — the point
     * of the spelling, and it has to survive a target the rule now lets through. Live returns NULL for
     * the object whose field does not fit and the object itself when it does.
     */
    @Test
    public void tryCastStillYieldsNullWhenTheStructuredConversionFails() {
        assertValue("SELECT TRY_CAST(OBJECT_CONSTRUCT('x', 'notanumber') AS OBJECT(x INT)) AS r", "null");
        assertValue("SELECT TRY_CAST(OBJECT_CONSTRUCT('x', '5') AS OBJECT(x INT)) AS r", "{\"x\":5}");
        assertValue("SELECT TRY_CAST(NULL AS OBJECT) AS r", "null");
        assertValue("SELECT TRY_CAST(NULL AS ARRAY) AS r", "null");
        assertValue("SELECT TRY_CAST(NULL AS VARIANT) AS r", "null");
    }

    /**
     * The half of the rule that must KEEP rejecting: a scalar target still refuses a semi-structured
     * source, and a scalar source still refuses a semi-structured target. Without these the narrowing
     * above could swing the other way and quietly delete the rule.
     */
    @Test
    public void scalarTargetsAndScalarSourcesStayRejected() {
        assertFails("SELECT TRY_CAST(o AS NUMBER) FROM stt",
            "invalid type [TRY_CAST(STT.O)] for parameter 'TO_NUMBER'");
        assertFails("SELECT TRY_CAST(v AS NUMBER) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types VARIANT and NUMBER(38,0)");
        assertFails("SELECT TRY_CAST(a AS VARCHAR) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types ARRAY and VARCHAR(134217728)");
        assertFails("SELECT TRY_CAST(n AS VARIANT) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types NUMBER(38,0) and VARIANT");
        assertFails("SELECT TRY_CAST(n AS OBJECT) FROM stt",
            "invalid type [TRY_CAST(STT.N)] for parameter 'TO_OBJECT'");
        assertFails("SELECT TRY_CAST(n AS ARRAY) FROM stt",
            "Function TRY_CAST cannot be used with arguments of types NUMBER(38,0) and ARRAY");
    }

    /**
     * The structured-source TEXT rule still wins over the TRY_CAST source rule for a text target — the
     * ordering the two share. {@code TRY_CAST(so AS VARCHAR)} must keep the 'TO_VARCHAR' conversion
     * sentence and not fall through to TRY_CAST's own, which is what live gives.
     */
    @Test
    public void theStructuredTextSentenceStillWinsOverTheTryCastOne() {
        assertFails("SELECT TRY_CAST(so AS VARCHAR) FROM stt",
            "SQL compilation error:\ninvalid type [TRY_CAST(STT.SO)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TRY_CAST(sa AS VARCHAR) FROM stt",
            "SQL compilation error:\ninvalid type [TRY_CAST(STT.SA)] for parameter 'TO_VARCHAR'");
    }

    /**
     * CAST and {@code ::} are LOOSER than TRY_CAST over the very same pairs, so the rule is
     * TRY_CAST-only. Live: {@code CAST(o AS ARRAY)} wraps the object into a one-element
     * array and {@code CAST(v AS OBJECT)} succeeds, while both TRY_CAST spellings error. CAST and
     * {@code ::} agreed with each other on all eighteen pairs probed; TRY_CAST did not join them.
     */
    @Test
    public void plainCastIsLooserThanTryCastOverTheSamePairs() {
        assertValue("SELECT CAST(o AS ARRAY) FROM stt WHERE id = 1", "[{\"k\":\"v1\"}]");
        assertValue("SELECT o::ARRAY FROM stt WHERE id = 1", "[{\"k\":\"v1\"}]");
        assertValue("SELECT CAST(v AS OBJECT) FROM stt WHERE id = 1", "{\"x\":1}");
        assertFails("SELECT TRY_CAST(o AS ARRAY) FROM stt", "cannot be used with arguments of types");
        assertFails("SELECT TRY_CAST(v AS OBJECT) FROM stt", "cannot be used with arguments of types");
    }

    /**
     * What the narrowing is FOR: a TRY_CAST out of a structured type into the plain one launders the
     * value past the rules the structured type is barred from. Live,
     * {@code ARRAY_AGG(so)} and {@code TO_VARCHAR(so)} are both refused while
     * {@code ARRAY_AGG(TRY_CAST(so AS OBJECT))} and {@code TO_VARCHAR(TRY_CAST(so AS OBJECT))} return
     * the value — the declared target is what the neighbouring rules read.
     */
    @Test
    public void aTryCastOutOfAStructuredTypeLaundersItIntoThePlainOne() {
        assertAccepted("SELECT ARRAY_AGG(TRY_CAST(so AS OBJECT)) FROM stt", 1);
        assertAccepted("SELECT ARRAY_AGG(TRY_CAST(so AS VARIANT)) FROM stt", 1);
        assertValue("SELECT TO_VARCHAR(TRY_CAST(so AS OBJECT)) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
        assertValue("SELECT TO_VARCHAR(TRY_CAST(so AS VARIANT)) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
        assertValue("SELECT TYPEOF(TRY_CAST(so AS OBJECT)) FROM stt WHERE id = 1", "OBJECT");
    }

    /** An EXPRESSION source, not just a column, and the target spelled in lower case. */
    @Test
    public void expressionSourcesAndLowerCaseTargetsBehaveTheSame() {
        assertValue("SELECT TRY_CAST(OBJECT_CONSTRUCT('k', 1) AS OBJECT) AS r", "{\"k\":1}");
        assertValue("SELECT TRY_CAST(OBJECT_CONSTRUCT('k', 1) AS VARIANT) AS r", "{\"k\":1}");
        assertValue("SELECT TRY_CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY) AS r", "[1,2]");
        assertValue("SELECT TRY_CAST(ARRAY_CONSTRUCT(1, 2) AS VARIANT) AS r", "[1,2]");
        assertValue("SELECT try_cast(o as object) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT TRY_CAST(o AS Object) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
    }

    // ── ARRAY_AGG and the other collecting aggregates ────────────────────────

    /**
     * Live: "Invalid argument types for function 'ARRAY_AGG': (OBJECT(x VARCHAR(16777216)))" (SQLSTATE
     * 42P13, vendor code 1044), for all three structured kinds.
     */
    @Test
    public void arrayAggOverStructuredIsRejected() {
        assertFails("SELECT ARRAY_AGG(so) FROM stt",
            "Invalid argument types for function 'ARRAY_AGG': (" + OBJECT_TYPE + ")");
        assertFails("SELECT ARRAY_AGG(sa) FROM stt",
            "Invalid argument types for function 'ARRAY_AGG': (" + ARRAY_TYPE + ")");
        assertFails("SELECT ARRAY_AGG(sm) FROM stt",
            "Invalid argument types for function 'ARRAY_AGG': (" + MAP_TYPE + ")");
    }

    /**
     * Each written form of the same aggregate, all measured live: DISTINCT, WITHIN GROUP, the windowed
     * form, under GROUP BY, and on an EMPTY input — the rejection is a compile-time one, so no row is
     * needed. Only the aggregated VALUE is constrained: sorting BY a structured key is fine.
     */
    @Test
    public void everyWrittenFormOfArrayAggIsRejected() {
        final String expected = "Invalid argument types for function 'ARRAY_AGG': (" + OBJECT_TYPE + ")";
        assertFails("SELECT ARRAY_AGG(DISTINCT so) FROM stt", expected);
        assertFails("SELECT ARRAY_AGG(so) WITHIN GROUP (ORDER BY id) FROM stt", expected);
        assertFails("SELECT ARRAY_AGG(so) OVER () FROM stt", expected);
        assertFails("SELECT ARRAY_AGG(so) FROM stt GROUP BY id", expected);
        assertFails("SELECT ARRAY_AGG(so) FROM stt WHERE 1 = 0", expected);
        assertAccepted("SELECT ARRAY_AGG(n) WITHIN GROUP (ORDER BY so) FROM stt", 1);
    }

    /** ARRAY_UNIQUE_AGG and OBJECT_AGG's VALUE position split the same way. */
    @Test
    public void arrayUniqueAggAndObjectAggValueOverStructuredAreRejected() {
        assertFails("SELECT ARRAY_UNIQUE_AGG(so) FROM stt",
            "Invalid argument types for function 'ARRAY_UNIQUE_AGG': (" + OBJECT_TYPE + ")");
        assertFails("SELECT ARRAY_UNIQUE_AGG(sm) FROM stt",
            "Invalid argument types for function 'ARRAY_UNIQUE_AGG': (" + MAP_TYPE + ")");
        assertFails("SELECT OBJECT_AGG(s, so) FROM stt",
            "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(16777216), " + OBJECT_TYPE + ")");
        assertFails("SELECT OBJECT_AGG(s, sa) FROM stt",
            "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(16777216), " + ARRAY_TYPE + ")");
    }

    /**
     * The KEY position of OBJECT_AGG is NOT a divergence: live refuses a plain OBJECT there too, so
     * this half of the rule applies to both kinds and is declared as a semi-structured rejection.
     */
    @Test
    public void objectAggRefusesASemiStructuredKeyOfEitherKind() {
        assertFails("SELECT OBJECT_AGG(o, n) FROM stt",
            "Invalid argument types for function 'OBJECT_AGG': (OBJECT, NUMBER(38,0))");
        assertFails("SELECT OBJECT_AGG(so, n) FROM stt",
            "Invalid argument types for function 'OBJECT_AGG': (" + OBJECT_TYPE + ", NUMBER(38,0))");
        assertFails("SELECT OBJECT_AGG(sa, n) FROM stt",
            "Invalid argument types for function 'OBJECT_AGG': (" + ARRAY_TYPE + ", NUMBER(38,0))");
    }

    /** The paired guard: the plain types still collect, exactly as {@code #143} pinned them. */
    @Test
    public void plainSemiStructuredStillCollects() {
        assertValue("SELECT ARRAY_AGG(o) FROM stt", "[{\"k\":\"v1\"},{\"k\":\"v2\"}]");
        assertValue("SELECT ARRAY_AGG(a) FROM stt", "[[1,2],[3,4]]");
        assertAccepted("SELECT ARRAY_AGG(v) FROM stt", 1);
        assertAccepted("SELECT ARRAY_UNIQUE_AGG(o) FROM stt", 1);
        assertAccepted("SELECT OBJECT_AGG(s, o) FROM stt", 1);
    }

    // ── The VARIANT-reading scalar surface ───────────────────────────────────

    /**
     * The functions that read their argument AS a VARIANT refuse a structured one, since Snowflake will
     * not implicitly convert it. Each name here was run live over a structured column and over the
     * plain column beside it.
     */
    @Test
    public void theVariantReadingScalarsRejectStructured() {
        assertFails("SELECT TO_JSON(so) FROM stt",
            "Invalid argument types for function 'TO_JSON': (" + OBJECT_TYPE + ")");
        assertFails("SELECT TO_XML(sa) FROM stt",
            "Invalid argument types for function 'TO_XML': (" + ARRAY_TYPE + ")");
        assertFails("SELECT TYPEOF(sm) FROM stt",
            "Invalid argument types for function 'TYPEOF': (" + MAP_TYPE + ")");
        assertFails("SELECT IS_OBJECT(so) FROM stt",
            "Invalid argument types for function 'IS_OBJECT': (" + OBJECT_TYPE + ")");
        assertFails("SELECT IS_ARRAY(sa) FROM stt",
            "Invalid argument types for function 'IS_ARRAY': (" + ARRAY_TYPE + ")");
        assertFails("SELECT IS_NULL_VALUE(so) FROM stt",
            "Invalid argument types for function 'IS_NULL_VALUE': (" + OBJECT_TYPE + ")");
        assertFails("SELECT AS_OBJECT(so) FROM stt",
            "Invalid argument types for function 'AS_OBJECT': (" + OBJECT_TYPE + ")");
        assertFails("SELECT AS_ARRAY(sa) FROM stt",
            "Invalid argument types for function 'AS_ARRAY': (" + ARRAY_TYPE + ")");
        assertFails("SELECT AS_VARCHAR(so) FROM stt",
            "Invalid argument types for function 'AS_VARCHAR': (" + OBJECT_TYPE + ")");
    }

    /**
     * An ALIAS is covered for free because the declaration sits on the registered object rather than in
     * a name table: {@code IS_CHAR} is the {@code IS_VARCHAR} instance, {@code IS_REAL} the
     * {@code IS_DOUBLE} one and {@code AS_CHAR} the {@code AS_VARCHAR} one — and live rejects each
     * alias exactly as it rejects the name it aliases.
     */
    @Test
    public void theAliasesOfThoseScalarsRejectStructuredToo() {
        assertFails("SELECT IS_CHAR(so) FROM stt",
            "Invalid argument types for function 'IS_CHAR': (" + OBJECT_TYPE + ")");
        assertFails("SELECT IS_REAL(so) FROM stt",
            "Invalid argument types for function 'IS_REAL': (" + OBJECT_TYPE + ")");
        assertFails("SELECT IS_DATE_VALUE(so) FROM stt",
            "Invalid argument types for function 'IS_DATE_VALUE': (" + OBJECT_TYPE + ")");
        assertFails("SELECT AS_CHAR(so) FROM stt",
            "Invalid argument types for function 'AS_CHAR': (" + OBJECT_TYPE + ")");
    }

    /** ARRAY_TO_STRING refuses a structured value in the ARRAY position, where a plain ARRAY joins. */
    @Test
    public void arrayToStringRefusesAStructuredArray() {
        assertFails("SELECT ARRAY_TO_STRING(sa, ',') FROM stt",
            "Invalid argument types for function 'ARRAY_TO_STRING': (" + ARRAY_TYPE + ", VARCHAR(1))");
        assertFails("SELECT ARRAY_TO_STRING(sm, ',') FROM stt",
            "Invalid argument types for function 'ARRAY_TO_STRING': (" + MAP_TYPE + ", VARCHAR(1))");
        assertValue("SELECT ARRAY_TO_STRING(a, ',') FROM stt WHERE id = 1", "1,2");
    }

    /** The paired guard for the whole scalar surface above. */
    @Test
    public void theSameScalarsStillReadPlainSemiStructured() {
        assertValue("SELECT TO_JSON(o) FROM stt WHERE id = 1", "{\"k\":\"v1\"}");
        assertValue("SELECT TYPEOF(o) FROM stt WHERE id = 1", "OBJECT");
        assertValue("SELECT TYPEOF(a) FROM stt WHERE id = 1", "ARRAY");
        assertAccepted("SELECT TO_XML(o) FROM stt", 2);
        assertAccepted("SELECT IS_OBJECT(o) FROM stt", 2);
        assertAccepted("SELECT IS_ARRAY(a) FROM stt", 2);
        assertAccepted("SELECT AS_OBJECT(o) FROM stt", 2);
        assertAccepted("SELECT AS_VARCHAR(o) FROM stt", 2);
        assertAccepted("SELECT IS_NULL_VALUE(o) FROM stt", 2);
    }

    // ── The constructors ─────────────────────────────────────────────────────

    /**
     * The constructors refuse a structured value with the ORDERING-aggregate sentence instead of an
     * argument-type list — live, "Function ARRAY_CONSTRUCT does not support OBJECT(x
     * VARCHAR(16777216)) argument type" (SQLSTATE 22000) — in EVERY position.
     */
    @Test
    public void theConstructorsRejectStructuredInAnyPosition() {
        assertFails("SELECT ARRAY_CONSTRUCT(so) FROM stt",
            "SQL compilation error:\nFunction ARRAY_CONSTRUCT does not support " + OBJECT_TYPE
                + " argument type");
        assertFails("SELECT ARRAY_CONSTRUCT(1, so) FROM stt",
            "Function ARRAY_CONSTRUCT does not support " + OBJECT_TYPE + " argument type");
        assertFails("SELECT ARRAY_CONSTRUCT(so, 1) FROM stt",
            "Function ARRAY_CONSTRUCT does not support " + OBJECT_TYPE + " argument type");
        assertFails("SELECT ARRAY_CONSTRUCT_COMPACT(sa) FROM stt",
            "Function ARRAY_CONSTRUCT_COMPACT does not support " + ARRAY_TYPE + " argument type");
        assertFails("SELECT OBJECT_CONSTRUCT('a', so) FROM stt",
            "Function OBJECT_CONSTRUCT does not support " + OBJECT_TYPE + " argument type");
        assertFails("SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', sm) FROM stt",
            "Function OBJECT_CONSTRUCT_KEEP_NULL does not support " + MAP_TYPE + " argument type");
    }

    /**
     * A structured value used as an OBJECT_CONSTRUCT KEY gets its own tail — live appends " for keys"
     * and carries vendor code 2270 rather than 2016.
     */
    @Test
    public void aStructuredObjectConstructKeyNamesTheKeyHalf() {
        assertFails("SELECT OBJECT_CONSTRUCT(so, 1) FROM stt",
            "Function OBJECT_CONSTRUCT does not support " + OBJECT_TYPE + " argument type for keys");
        assertFails("SELECT OBJECT_CONSTRUCT('a', 1, sa, 2) FROM stt",
            "Function OBJECT_CONSTRUCT does not support " + ARRAY_TYPE + " argument type for keys");
    }

    /** The paired guard: a plain OBJECT or ARRAY nests inside a constructor live. */
    @Test
    public void theConstructorsStillNestPlainSemiStructured() {
        assertValue("SELECT ARRAY_CONSTRUCT(1, o) FROM stt WHERE id = 1", "[1,{\"k\":\"v1\"}]");
        assertAccepted("SELECT ARRAY_CONSTRUCT_COMPACT(o) FROM stt", 2);
        assertValue("SELECT OBJECT_CONSTRUCT('a', o) FROM stt WHERE id = 1",
            "{\"a\":{\"k\":\"v1\"}}");
        assertAccepted("SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', a) FROM stt", 2);
    }

    // ── The boundary: what a structured value may still do ───────────────────

    /**
     * The KEY positions, measured for each of the three kinds rather than inherited from the plain
     * behaviour. This is the assumption most likely to be made and it is WRONG in Snowflake: a
     * structured value groups, sorts, partitions, de-duplicates, unions, joins and matches {@code IN}
     * just as a plain one does.
     */
    @Test
    public void theKeyPositionsStillAcceptStructured() {
        assertAccepted("SELECT COUNT(*) FROM stt GROUP BY so", 2);
        assertAccepted("SELECT COUNT(*) FROM stt GROUP BY sa", 2);
        assertAccepted("SELECT COUNT(*) FROM stt GROUP BY sm", 2);
        assertAccepted("SELECT id FROM stt ORDER BY so", 2);
        assertAccepted("SELECT id FROM stt ORDER BY sa", 2);
        assertAccepted("SELECT id FROM stt ORDER BY sm", 2);
        assertAccepted("SELECT DISTINCT so FROM stt", 2);
        assertAccepted("SELECT DISTINCT sm FROM stt", 2);
        assertAccepted("SELECT ROW_NUMBER() OVER (PARTITION BY sa ORDER BY id) FROM stt", 2);
        assertAccepted("SELECT ROW_NUMBER() OVER (ORDER BY so) FROM stt", 2);
        assertAccepted("SELECT so FROM stt UNION SELECT so FROM stt", 2);
        assertAccepted("SELECT COUNT(*) FROM stt t1 JOIN stt t2 ON t1.sa = t2.sa", 1);
        assertAccepted("SELECT id FROM stt WHERE sm IN (SELECT sm FROM stt)", 2);
        assertAccepted("SELECT COUNT(DISTINCT so) FROM stt", 1);
    }

    /**
     * The aggregate family SPLITS: these were all accepted live over a structured column, so the
     * rejection may never be widened to "every aggregate". {@code MAX} / {@code MIN} / {@code MODE} do
     * reject, but for the ordering reason {@code #143} landed, over plain and structured alike.
     */
    @Test
    public void theNonCollectingAggregatesStillAcceptStructured() {
        assertAccepted("SELECT ANY_VALUE(so) FROM stt", 1);
        assertAccepted("SELECT HASH_AGG(sa) FROM stt", 1);
        assertAccepted("SELECT MAX_BY(sm, n) FROM stt", 1);
        assertAccepted("SELECT MIN_BY(so, n) FROM stt", 1);
        assertAccepted("SELECT COUNT(so) FROM stt", 1);
        assertAccepted("SELECT APPROX_COUNT_DISTINCT(sa) FROM stt", 1);
        assertAccepted("SELECT FIRST_VALUE(so) OVER (ORDER BY id) FROM stt", 2);
        assertAccepted("SELECT LAG(so) OVER (ORDER BY id) FROM stt", 2);
        assertAccepted("SELECT COUNT(so) OVER () FROM stt", 2);
    }

    /**
     * The ACCESSORS read a structured value quite happily — the surface that makes the type usable at
     * all. Every one of these was run live over the structured columns.
     */
    @Test
    public void theAccessorsStillReadStructured() {
        assertValue("SELECT GET(so, 'x') FROM stt WHERE id = 1", "a");
        assertValue("SELECT so:x FROM stt WHERE id = 1", "a");
        assertValue("SELECT sa[0] FROM stt WHERE id = 1", "1");
        assertValue("SELECT OBJECT_KEYS(so) FROM stt WHERE id = 1", "[\"x\"]");
        assertValue("SELECT ARRAY_SIZE(sa) FROM stt WHERE id = 1", "2");
        assertValue("SELECT OBJECT_INSERT(so, 'y', 1) FROM stt WHERE id = 1",
            "{\"x\":\"a\",\"y\":1}");
    }

    /**
     * An explicit conversion is how a caller opts back in, and it must keep working: live,
     * {@code ARRAY_AGG(so::VARIANT)}, {@code TO_JSON(so::OBJECT)} and {@code ARRAY_AGG(TO_VARIANT(so))}
     * all return their values. The rule reads the DECLARED type, so the conversion really does change
     * the answer rather than merely dressing it up.
     */
    @Test
    public void anExplicitConversionEscapesTheRule() {
        assertValue("SELECT ARRAY_AGG(so::VARIANT) FROM stt", "[{\"x\":\"a\"},{\"x\":\"b\"}]");
        assertValue("SELECT ARRAY_AGG(so::OBJECT) FROM stt", "[{\"x\":\"a\"},{\"x\":\"b\"}]");
        assertAccepted("SELECT ARRAY_AGG(TO_VARIANT(so)) FROM stt", 1);
        assertValue("SELECT TO_JSON(so::OBJECT) FROM stt WHERE id = 1", "{\"x\":\"a\"}");
        assertAccepted("SELECT ARRAY_AGG(sa::ARRAY) FROM stt", 1);
        assertAccepted("SELECT ARRAY_AGG(sm::OBJECT) FROM stt", 1);
    }

    // ── The type is a static property, read through anything that carries it ──

    /**
     * A derived table, a CTE, a view and the conditionals all carry the structured type, so the rule
     * fires through each of them — live-verified for every wrapper here.
     */
    @Test
    public void theRuleReadsThroughDerivedTablesViewsAndConditionals() {
        final String expected = "Invalid argument types for function 'ARRAY_AGG': (" + OBJECT_TYPE + ")";
        assertFails("SELECT ARRAY_AGG(so) FROM (SELECT so FROM stt) q", expected);
        assertFails("WITH c AS (SELECT so FROM stt) SELECT ARRAY_AGG(so) FROM c", expected);
        assertFails("SELECT ARRAY_AGG(IFF(TRUE, so, so)) FROM stt", expected);
        assertFails("SELECT ARRAY_AGG(COALESCE(so, so)) FROM stt", expected);
        assertFails("SELECT ARRAY_AGG(CASE WHEN id = 1 THEN so ELSE so END) FROM stt", expected);
        engine.execute("CREATE VIEW sov AS SELECT so, sa, sm FROM stt");
        assertFails("SELECT ARRAY_AGG(so) FROM sov", expected);
        assertFails("SELECT CAST(so AS VARCHAR) FROM sov", "for parameter 'TO_VARCHAR'");
    }

    /** A structured value produced by a CAST rather than read from a column is refused the same way. */
    @Test
    public void aCastProducedStructuredValueIsRejectedToo() {
        // A CAST target's bare VARCHAR field carries the CONVERSION default, not the column one —
        // live reports "(OBJECT(x VARCHAR(134217728)))" here while the same
        // type declared on a column reports VARCHAR(16777216).
        assertFails("SELECT ARRAY_AGG(CAST(OBJECT_CONSTRUCT('x', 'a') AS OBJECT(x VARCHAR))) FROM stt",
            "Invalid argument types for function 'ARRAY_AGG': (OBJECT(x VARCHAR(134217728)))");
        assertFails("SELECT TO_JSON(CAST([1, 2] AS ARRAY(INT))) FROM stt",
            "Invalid argument types for function 'TO_JSON': (" + ARRAY_TYPE + ")");
    }

    /**
     * Every SHAPE of structured type names itself in full, and the shapes were measured one at a time
     * rather than assumed to render alike: the zero-field {@code OBJECT()} that
     * {@code DataTypeParser} keeps distinct from a plain OBJECT, a multi-field object, a nested
     * {@code ARRAY(OBJECT(x INT))}, a MAP whose value is itself structured, and a {@code NOT NULL}
     * field — which live carries into the message.
     */
    @Test
    public void everyStructuredShapeNamesItselfInFull() {
        engine.execute("CREATE TABLE shapes (z OBJECT(), n2 OBJECT(x INT, y VARCHAR),"
            + " na ARRAY(OBJECT(x INT)), mm MAP(VARCHAR, ARRAY(INT)),"
            + " nn OBJECT(x VARCHAR NOT NULL) NOT NULL)");
        assertFails("SELECT ARRAY_AGG(z) FROM shapes",
            "Invalid argument types for function 'ARRAY_AGG': (OBJECT())");
        assertFails("SELECT ARRAY_AGG(n2) FROM shapes", "Invalid argument types for function"
            + " 'ARRAY_AGG': (OBJECT(x NUMBER(38,0), y VARCHAR(16777216)))");
        assertFails("SELECT ARRAY_AGG(na) FROM shapes", "Invalid argument types for function"
            + " 'ARRAY_AGG': (ARRAY(OBJECT(x NUMBER(38,0))))");
        assertFails("SELECT ARRAY_AGG(mm) FROM shapes", "Invalid argument types for function"
            + " 'ARRAY_AGG': (MAP(VARCHAR(16777216), ARRAY(NUMBER(38,0))))");
        assertFails("SELECT ARRAY_AGG(nn) FROM shapes", "Invalid argument types for function"
            + " 'ARRAY_AGG': (OBJECT(x VARCHAR(16777216) NOT NULL))");
        assertFails("SELECT CAST(na AS VARCHAR) FROM shapes",
            "invalid type [CAST(SHAPES.NA AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        // The zero-field object is a KEY like any other structured value.
        assertAccepted("SELECT COUNT(*) FROM shapes GROUP BY z", 0);
    }
}
