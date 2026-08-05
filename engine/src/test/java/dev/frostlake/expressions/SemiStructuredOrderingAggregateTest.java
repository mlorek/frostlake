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
 * The ordering aggregates do not order a semi-structured value: {@code MAX}, {@code MIN} and
 * {@code MODE} reject an OBJECT or an ARRAY, and Frostlake used to accept all of them and hand back
 * the JSON text — {@code MAX(o)} answered {@code {"k":"v2"}}, a silent stringification that looked
 * deliberate.
 *
 * <p>Every expectation below was measured against a live Snowflake account on over a
 * populated OBJECT column and ARRAY column, together with the ACCEPTED forms that BOUND the rule. The
 * bound matters more than the rejection here: OBJECT and ARRAY run through everything, so a rule even
 * slightly too broad would break real queries. Live, a semi-structured value still groups, sorts,
 * partitions, de-duplicates, joins, unions and casts to VARCHAR perfectly well, and
 * {@code ANY_VALUE} / {@code ARRAY_AGG} / {@code OBJECT_AGG} / {@code COUNT} / {@code HASH_AGG} /
 * {@code MAX_BY} all take it happily — so the rejection may never be widened past these three
 * functions.
 *
 * <p>Those accepted forms are the PLAIN types' alone. A STRUCTURED value diverges in two of them —
 * {@code CAST(so AS VARCHAR)} and {@code ARRAY_AGG(so)} are both refused live while their plain twins
 * are not — which is why {@code StructuredTypeArgumentRejectionTest} exists beside this file. This
 * rule is the one place they agree: {@code MAX} / {@code MIN} / {@code MODE} reject either kind, and
 * name whatever type they were handed.
 *
 * <p>The rule reads the DECLARED type and never the runtime value. That was measured rather than
 * assumed, because it is the whole shape of the thing: {@code MAX(v)} over a VARIANT is accepted even
 * when the VARIANT HOLDS an object, while the same value cast — {@code MAX(vo::OBJECT)} — is
 * rejected, and {@code MAX(o::VARIANT)} is accepted.
 */
public class SemiStructuredOrderingAggregateTest extends BaseDatabaseTest {

    @BeforeEach
    public void createSemiStructuredTable() {
        engine.execute("CREATE TABLE sst (id INTEGER, o OBJECT, a ARRAY, v VARIANT, vo VARIANT,"
            + " s VARCHAR, n NUMBER)");
        engine.execute("INSERT INTO sst SELECT 1, OBJECT_CONSTRUCT('k', 'v1'), ARRAY_CONSTRUCT(1, 2),"
            + " TO_VARIANT(1), TO_VARIANT(OBJECT_CONSTRUCT('x', 1)), 'x', 10");
        engine.execute("INSERT INTO sst SELECT 2, OBJECT_CONSTRUCT('k', 'v2'), ARRAY_CONSTRUCT(3, 4),"
            + " TO_VARIANT(2), TO_VARIANT(OBJECT_CONSTRUCT('x', 2)), 'y', 20");
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

    // ── The rejection ────────────────────────────────────────────────────────

    /** Live: "Function MAX does not support OBJECT argument type" (SQLSTATE 22000, code 2016). */
    @Test
    public void maxOverAnObjectColumnIsRejected() {
        assertFails("SELECT MAX(o) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
    }

    /** The ARRAY variant names ARRAY: the message carries the argument's own type. */
    @Test
    public void maxOverAnArrayColumnIsRejected() {
        assertFails("SELECT MAX(a) FROM sst",
            "SQL compilation error:\nFunction MAX does not support ARRAY argument type");
    }

    /**
     * A structured MAP is refused here too, and names its whole parameterised type. It was missed
     * until because {@code MapType} is deliberately not an {@code ObjectType} subclass, so
     * {@code MAX(sm)} came back as the JSON text {@code {"k":1}} — a silent stringification of exactly
     * the kind this rule exists to stop. Live: "Function MAX does not support MAP(VARCHAR(16777216),
     * NUMBER(38,0)) argument type".
     */
    @Test
    public void maxOverAStructuredMapColumnIsRejected() {
        engine.execute("CREATE TABLE sstm (id INTEGER, so OBJECT(x VARCHAR), sm MAP(VARCHAR, INT))");
        engine.execute("INSERT INTO sstm SELECT 1, OBJECT_CONSTRUCT('x', 'a')::OBJECT(x VARCHAR),"
            + " OBJECT_CONSTRUCT('k', 1)::MAP(VARCHAR, INT)");
        assertFails("SELECT MAX(sm) FROM sstm", "SQL compilation error:\nFunction MAX does not support"
            + " MAP(VARCHAR(16777216), NUMBER(38,0)) argument type");
        assertFails("SELECT MIN(sm) FROM sstm", "SQL compilation error:\nFunction MIN does not support"
            + " MAP(VARCHAR(16777216), NUMBER(38,0)) argument type");
        assertFails("SELECT MAX(so) FROM sstm", "SQL compilation error:\nFunction MAX does not support"
            + " OBJECT(x VARCHAR(16777216)) argument type");
        // The key positions stay open for a MAP as they do for a plain OBJECT.
        assertAccepted("SELECT COUNT(*) FROM sstm GROUP BY sm", 1);
        assertAccepted("SELECT id FROM sstm ORDER BY sm", 1);
    }

    /** MIN and MODE share the rule and each name themselves, exactly as live does. */
    @Test
    public void minAndModeOverSemiStructuredAreRejected() {
        assertFails("SELECT MIN(o) FROM sst",
            "SQL compilation error:\nFunction MIN does not support OBJECT argument type");
        assertFails("SELECT MIN(a) FROM sst",
            "SQL compilation error:\nFunction MIN does not support ARRAY argument type");
        assertFails("SELECT MODE(o) FROM sst",
            "SQL compilation error:\nFunction MODE does not support OBJECT argument type");
        assertFails("SELECT MODE(a) FROM sst",
            "SQL compilation error:\nFunction MODE does not support ARRAY argument type");
    }

    /** Live: {@code MAX(DISTINCT o)} and the windowed {@code MAX(o) OVER ()} reject identically. */
    @Test
    public void distinctAndWindowedFormsAreRejected() {
        assertFails("SELECT MAX(DISTINCT o) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(o) OVER () FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
    }

    /**
     * The rejection is a COMPILE error live, so it fires with nothing to aggregate: an empty input
     * must reject exactly like a populated one rather than quietly returning NULL.
     */
    @Test
    public void anEmptyInputStillRejects() {
        assertFails("SELECT MAX(o) FROM sst WHERE 1 = 0",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
    }

    /**
     * The DECLARED type decides, not the value. Live, a VARIANT cast to OBJECT is rejected even though
     * the identical uncast VARIANT is accepted, and a VARIANT column holding a plain number cast to
     * ARRAY is rejected too — the cast is where the expression takes on the type.
     */
    @Test
    public void aCastToObjectOrArrayIsRejected() {
        assertFails("SELECT MAX(vo::OBJECT) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(v::OBJECT) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(v::ARRAY) FROM sst",
            "SQL compilation error:\nFunction MAX does not support ARRAY argument type");
        assertFails("SELECT MAX(CAST(o AS OBJECT)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
    }

    /**
     * Live, a conditional over semi-structured branches IS semi-structured — {@code SYSTEM$TYPEOF}
     * reports OBJECT for every one of these — so the rule reads through it just as the FILE rule does.
     */
    @Test
    public void conditionalsOverSemiStructuredBranchesAreReadThrough() {
        assertFails("SELECT MAX(IFF(TRUE, o, o)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(COALESCE(o, o)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(GREATEST(o, o)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(CASE WHEN TRUE THEN o ELSE o END) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MIN(IFF(TRUE, a, a)) FROM sst",
            "SQL compilation error:\nFunction MIN does not support ARRAY argument type");
    }

    /**
     * The constructor family is statically OBJECT / ARRAY rather than VARIANT — read off
     * {@code SYSTEM$TYPEOF} live, one name at a time — so aggregating their results rejects even
     * though no OBJECT column is mentioned.
     */
    @Test
    public void semiStructuredProducerFunctionsAreRejected() {
        assertFails("SELECT MAX(OBJECT_CONSTRUCT('k', id)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(OBJECT_INSERT(OBJECT_CONSTRUCT('k', id), 'j', 2)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(ARRAY_CONSTRUCT(id)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support ARRAY argument type");
        assertFails("SELECT MAX(SPLIT(s, ',')) FROM sst",
            "SQL compilation error:\nFunction MAX does not support ARRAY argument type");
        assertFails("SELECT MAX(OBJECT_KEYS(o)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support ARRAY argument type");
        assertFails("SELECT MAX(TO_ARRAY(id)) FROM sst",
            "SQL compilation error:\nFunction MAX does not support ARRAY argument type");
    }

    // ── The ACCEPTED forms this rejection must never swallow ─────────────────

    /**
     * The single most important guard: a VARIANT is NEVER rejected, even when it holds an object or an
     * array. Live, {@code MAX(vo)} returns {@code {"x": 2}} and {@code MAX(va)} returns {@code [3,4]}.
     * If this ever starts failing, the rule has begun reading the runtime value instead of the
     * declared type.
     */
    @Test
    public void aVariantIsAcceptedEvenWhenItHoldsAnObject() {
        assertAccepted("SELECT MAX(v) FROM sst", 1);
        assertAccepted("SELECT MIN(v) FROM sst", 1);
        assertAccepted("SELECT MODE(v) FROM sst", 1);
        assertAccepted("SELECT MAX(vo) FROM sst", 1);
        assertAccepted("SELECT MIN(vo) FROM sst", 1);
        assertEquals("{\"x\":2}", String.valueOf(
            engine.executeQuery("SELECT MAX(vo) FROM sst").getRows().get(0).getValues().get(0)));
    }

    /**
     * Casting the other way is the mirror image and must stay accepted: an OBJECT column explicitly
     * widened to VARIANT aggregates fine live, returning the object itself.
     */
    @Test
    public void anObjectCastToVariantIsAccepted() {
        assertAccepted("SELECT MAX(o::VARIANT) FROM sst", 1);
        assertAccepted("SELECT MAX(TO_VARIANT(o)) FROM sst", 1);
    }

    /**
     * The TRY_CAST spelling of the same widening reads the same way, which is the guard that a
     * TRY_CAST into a semi-structured target still declares the type it names. Live:
     * {@code MAX(TRY_CAST(o AS VARIANT))} returns the object while {@code MAX(TRY_CAST(o AS OBJECT))}
     * is "Function MAX does not support OBJECT argument type" — the rule reads the DECLARED target,
     * exactly as it does for {@code ::}, and is not fooled by the value underneath.
     */
    @Test
    public void theTryCastSpellingOfTheWideningReadsTheSameWay() {
        assertAccepted("SELECT MAX(TRY_CAST(o AS VARIANT)) FROM sst", 1);
        assertAccepted("SELECT MAX(TRY_CAST(v AS VARIANT)) FROM sst", 1);
        assertFails("SELECT MAX(TRY_CAST(o AS OBJECT)) FROM sst",
            "Function MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(TRY_CAST(a AS ARRAY)) FROM sst",
            "Function MAX does not support ARRAY argument type");
    }

    /** PARSE_JSON and TO_VARIANT report VARIANT live, so they are outside the rule entirely. */
    @Test
    public void variantProducingFunctionsStayAccepted() {
        assertAccepted("SELECT MAX(PARSE_JSON('{\"a\":1}')) FROM sst", 1);
        assertAccepted("SELECT MAX(TO_VARIANT(id)) FROM sst", 1);
        assertAccepted("SELECT MAX(o:k) FROM sst", 1);
        assertAccepted("SELECT MAX(a[0]) FROM sst", 1);
    }

    /**
     * The key positions are the sharpest boundary of all, and they were verified live specifically
     * because the FILE rule DOES reject them: a semi-structured value groups, sorts and partitions
     * perfectly well in Snowflake, so this rejection must never reach {@code validateKeyExpression}.
     */
    @Test
    public void groupingAndSortingKeysStillAcceptSemiStructured() {
        assertAccepted("SELECT COUNT(*) FROM sst GROUP BY o", 2);
        assertAccepted("SELECT COUNT(*) FROM sst GROUP BY a", 2);
        assertAccepted("SELECT id FROM sst ORDER BY o", 2);
        assertAccepted("SELECT id FROM sst ORDER BY a", 2);
        assertAccepted("SELECT ROW_NUMBER() OVER (PARTITION BY o ORDER BY id) FROM sst", 2);
        assertAccepted("SELECT ROW_NUMBER() OVER (ORDER BY o) FROM sst", 2);
    }

    /** DISTINCT, COUNT and comparison all work over semi-structured values live. */
    @Test
    public void distinctCountAndComparisonsStillWork() {
        assertAccepted("SELECT DISTINCT o FROM sst", 2);
        assertAccepted("SELECT DISTINCT a FROM sst", 2);
        assertAccepted("SELECT COUNT(o) FROM sst", 1);
        assertAccepted("SELECT COUNT(DISTINCT o) FROM sst", 1);
        assertAccepted("SELECT id FROM sst WHERE o = o", 2);
    }

    /**
     * The aggregate family SPLITS rather than dividing on "semi-structured": every one of these was
     * accepted live over both an OBJECT and an ARRAY column, so only MAX / MIN / MODE may reject.
     */
    @Test
    public void theNonOrderingAggregatesStillAcceptSemiStructured() {
        assertAccepted("SELECT ANY_VALUE(o) FROM sst", 1);
        assertAccepted("SELECT ARRAY_AGG(o) FROM sst", 1);
        assertAccepted("SELECT ARRAY_UNIQUE_AGG(a) FROM sst", 1);
        assertAccepted("SELECT OBJECT_AGG(s, o) FROM sst", 1);
        assertAccepted("SELECT HASH_AGG(o) FROM sst", 1);
        assertAccepted("SELECT APPROX_COUNT_DISTINCT(o) FROM sst", 1);
        assertAccepted("SELECT MAX_BY(o, id) FROM sst", 1);
        assertAccepted("SELECT MIN_BY(o, id) FROM sst", 1);
    }

    /** Casting a semi-structured value to VARCHAR is legal live and returns its JSON text. */
    @Test
    public void castingSemiStructuredToVarcharStillWorks() {
        assertAccepted("SELECT CAST(o AS VARCHAR) FROM sst", 2);
        assertAccepted("SELECT TO_VARCHAR(a) FROM sst", 2);
    }

    /**
     * The two places where the PLAIN and STRUCTURED types part company, pinned here as a pair so a
     * later widening of either rule cannot quietly swap them. Live over one table carrying
     * both: {@code CAST(o AS VARCHAR)} and {@code ARRAY_AGG(o)} return values while
     * {@code CAST(so AS VARCHAR)} and {@code ARRAY_AGG(so)} are compile errors. This rejection —
     * MAX / MIN / MODE — is unaffected by that split and covers both kinds; the divergence itself is
     * exercised in {@code StructuredTypeArgumentRejectionTest}.
     */
    @Test
    public void theCastAndArrayAggAcceptancesAreThePlainTypesAlone() {
        engine.execute("CREATE TABLE sstd (id INTEGER, so OBJECT(x VARCHAR), o OBJECT)");
        engine.execute("INSERT INTO sstd SELECT 1, OBJECT_CONSTRUCT('x', 'a')::OBJECT(x VARCHAR),"
            + " OBJECT_CONSTRUCT('k', 'v1')");
        assertAccepted("SELECT CAST(o AS VARCHAR) FROM sstd", 1);
        assertAccepted("SELECT ARRAY_AGG(o) FROM sstd", 1);
        assertFails("SELECT CAST(so AS VARCHAR) FROM sstd", "for parameter 'TO_VARCHAR'");
        assertFails("SELECT ARRAY_AGG(so) FROM sstd",
            "Invalid argument types for function 'ARRAY_AGG': (OBJECT(x VARCHAR(16777216)))");
        // …while MAX rejects both, naming each one's own type.
        assertFails("SELECT MAX(o) FROM sstd",
            "SQL compilation error:\nFunction MAX does not support OBJECT argument type");
        assertFails("SELECT MAX(so) FROM sstd", "SQL compilation error:\nFunction MAX does not support"
            + " OBJECT(x VARCHAR(16777216)) argument type");
    }

    /** Joins, set operations and IN all key off semi-structured values happily live. */
    @Test
    public void joinsSetOperationsAndInStillWork() {
        assertAccepted("SELECT COUNT(*) FROM sst t1 JOIN sst t2 ON t1.o = t2.o", 1);
        assertAccepted("SELECT o FROM sst UNION SELECT o FROM sst", 2);
        assertAccepted("SELECT o FROM sst INTERSECT SELECT o FROM sst", 2);
        assertAccepted("SELECT id FROM sst WHERE o IN (SELECT o FROM sst)", 2);
    }

    /** The ordinary scalar types are untouched: nothing here may narrow MAX over VARCHAR or NUMBER. */
    @Test
    public void ordinaryScalarTypesAreUnaffected() {
        assertAccepted("SELECT MAX(s) FROM sst", 1);
        assertAccepted("SELECT MIN(s) FROM sst", 1);
        assertAccepted("SELECT MAX(n) FROM sst", 1);
        assertAccepted("SELECT MODE(s) FROM sst", 1);
    }
}
