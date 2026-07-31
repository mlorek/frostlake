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

package dev.frostlake.functions.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FILE positions Snowflake rejects at COMPILE time: grouping / sorting keys, the ordering
 * aggregates, text concatenation and every cast.
 *
 * <p>Frostlake used to accept all of these and hand back the descriptor JSON — a FILE silently
 * stringified, so {@code 'x' || f} and {@code CAST(f AS VARCHAR)} produced output that looked
 * deliberate. Every expectation below was measured against a live Snowflake account on over
 * a populated {@code FILE} column, together with the ACCEPTED forms that bound the rule: a FILE
 * compares, de-duplicates, joins and aggregates through {@code COUNT} / {@code ANY_VALUE} /
 * {@code ARRAY_AGG} perfectly well, and an OBJECT column groups and sorts and casts to VARCHAR — so
 * none of these rejections may be widened to semi-structured values in general.
 */
public class FileKeyAndCastRejectionTest extends FileFunctionTestSupport {

    @BeforeEach
    public void createFileTable() {
        engine.execute("CREATE TABLE fk (id INTEGER, f FILE, o OBJECT, s VARCHAR)");
        engine.execute("INSERT INTO fk SELECT 1, OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'hello.txt', 'SIZE', 12,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1'),"
            + " OBJECT_CONSTRUCT('a', 1), 'x'");
        engine.execute("INSERT INTO fk SELECT 2, OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'two.txt', 'SIZE', 12,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e2'),"
            + " OBJECT_CONSTRUCT('a', 2), 'y'");
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

    // ── GROUP BY keys ────────────────────────────────────────────────────────

    /** Live: "Expressions of type FILE cannot be used as GROUP BY keys" (SQLSTATE 42804). */
    @Test
    public void groupByAFileColumnIsRejected() {
        assertFails("SELECT COUNT(*) FROM fk GROUP BY f",
            "Expressions of type FILE cannot be used as GROUP BY keys");
    }

    /** The ordinal and the alias reach the same rule: both resolve to the FILE-typed item. */
    @Test
    public void groupByAFileOrdinalOrAliasIsRejected() {
        assertFails("SELECT f, COUNT(*) FROM fk GROUP BY 1",
            "Expressions of type FILE cannot be used as GROUP BY keys");
        assertFails("SELECT f AS x, COUNT(*) FROM fk GROUP BY x",
            "Expressions of type FILE cannot be used as GROUP BY keys");
    }

    /** A FILE anywhere in a multi-key GROUP BY rejects the whole clause. */
    @Test
    public void groupByRejectsAFileAmongOtherKeys() {
        assertFails("SELECT COUNT(*) FROM fk GROUP BY id, f",
            "Expressions of type FILE cannot be used as GROUP BY keys");
    }

    /**
     * Live: {@code GROUP BY IFF(TRUE, f, f)} is rejected too — a conditional over FILE branches IS a
     * FILE there (its result column reports type FILE), so the key rule reads through it.
     */
    @Test
    public void groupByAConditionalYieldingAFileIsRejected() {
        assertFails("SELECT COUNT(*) FROM fk GROUP BY IFF(TRUE, f, f)",
            "Expressions of type FILE cannot be used as GROUP BY keys");
        assertFails("SELECT COUNT(*) FROM fk GROUP BY COALESCE(f, f)",
            "Expressions of type FILE cannot be used as GROUP BY keys");
    }

    // ── ORDER BY keys ────────────────────────────────────────────────────────

    /** Live: "Expressions of type FILE cannot be used as ORDER BY keys" (SQLSTATE 42804). */
    @Test
    public void orderByAFileColumnIsRejected() {
        assertFails("SELECT id FROM fk ORDER BY f",
            "Expressions of type FILE cannot be used as ORDER BY keys");
    }

    @Test
    public void orderByAFileOrdinalOrAliasIsRejected() {
        assertFails("SELECT f FROM fk ORDER BY 1",
            "Expressions of type FILE cannot be used as ORDER BY keys");
        assertFails("SELECT f AS x FROM fk ORDER BY x",
            "Expressions of type FILE cannot be used as ORDER BY keys");
    }

    /** A sort direction / null placement does not exempt the key. */
    @Test
    public void orderByAFileRejectsWithAnExplicitDirection() {
        assertFails("SELECT id FROM fk ORDER BY f DESC NULLS LAST",
            "Expressions of type FILE cannot be used as ORDER BY keys");
    }

    /**
     * The rejection is a COMPILE-time one live, so it must not depend on the result being big enough
     * to sort — a single-row (and an empty) result rejects identically.
     */
    @Test
    public void orderByAFileIsRejectedEvenWithNothingToSort() {
        assertFails("SELECT id FROM fk WHERE id = 1 ORDER BY f",
            "Expressions of type FILE cannot be used as ORDER BY keys");
        assertFails("SELECT id FROM fk WHERE id = 999 ORDER BY f",
            "Expressions of type FILE cannot be used as ORDER BY keys");
    }

    // ── Window keys ──────────────────────────────────────────────────────────

    /** Live: "Expressions of type FILE cannot be used as PARTITION BY keys" — its own message. */
    @Test
    public void windowPartitionByAFileIsRejected() {
        assertFails("SELECT ROW_NUMBER() OVER (PARTITION BY f ORDER BY id) FROM fk",
            "Expressions of type FILE cannot be used as PARTITION BY keys");
    }

    /** A window ORDER BY reports the ORDER BY variant, exactly like the statement-level clause. */
    @Test
    public void windowOrderByAFileIsRejected() {
        assertFails("SELECT ROW_NUMBER() OVER (ORDER BY f) FROM fk",
            "Expressions of type FILE cannot be used as ORDER BY keys");
    }

    // ── Ordering aggregates ──────────────────────────────────────────────────

    /** Live: "SQL compilation error:\nFunction MAX does not support FILE argument type" (22000). */
    @Test
    public void maxOverAFileIsRejected() {
        assertFails("SELECT MAX(f) FROM fk",
            "SQL compilation error:\nFunction MAX does not support FILE argument type");
    }

    @Test
    public void minAndModeOverAFileAreRejected() {
        assertFails("SELECT MIN(f) FROM fk",
            "SQL compilation error:\nFunction MIN does not support FILE argument type");
        assertFails("SELECT MODE(f) FROM fk",
            "SQL compilation error:\nFunction MODE does not support FILE argument type");
    }

    /** DISTINCT does not exempt the argument. */
    @Test
    public void maxDistinctOverAFileIsRejected() {
        assertFails("SELECT MAX(DISTINCT f) FROM fk",
            "SQL compilation error:\nFunction MAX does not support FILE argument type");
    }

    /** The windowed form is rejected identically — live, {@code MAX(f) OVER ()} names MAX. */
    @Test
    public void windowedMaxOverAFileIsRejected() {
        assertFails("SELECT MAX(f) OVER () FROM fk",
            "SQL compilation error:\nFunction MAX does not support FILE argument type");
        assertFails("SELECT MIN(f) OVER (PARTITION BY id) FROM fk",
            "SQL compilation error:\nFunction MIN does not support FILE argument type");
    }

    /** A window aggregate written inline in QUALIFY is checked on the same rule. */
    @Test
    public void inlineQualifyWindowAggregateOverAFileIsRejected() {
        assertFails("SELECT id FROM fk QUALIFY MAX(f) OVER () IS NOT NULL",
            "SQL compilation error:\nFunction MAX does not support FILE argument type");
    }

    // ── Concatenation ────────────────────────────────────────────────────────

    /** Live: "Invalid argument types for function '||': (VARCHAR(1), FILE)" — both operand orders. */
    @Test
    public void concatOperatorRejectsAFileOnEitherSide() {
        assertFails("SELECT 'x' || f FROM fk",
            "Invalid argument types for function '||': (VARCHAR(1), FILE)");
        assertFails("SELECT f || 'x' FROM fk",
            "Invalid argument types for function '||': (FILE, VARCHAR(1))");
        assertFails("SELECT f || f FROM fk",
            "Invalid argument types for function '||': (FILE, FILE)");
    }

    /** CONCAT names itself; CONCAT_WS reports itself as CONCAT, as live does. */
    @Test
    public void concatFunctionsRejectAFileInAnyPosition() {
        assertFails("SELECT CONCAT('x', f) FROM fk",
            "Invalid argument types for function 'CONCAT': (VARCHAR(1), FILE)");
        assertFails("SELECT CONCAT(f, 'x') FROM fk",
            "Invalid argument types for function 'CONCAT': (FILE, VARCHAR(1))");
        assertFails("SELECT CONCAT_WS('-', 'x', f) FROM fk",
            "Invalid argument types for function 'CONCAT': (VARCHAR(1), VARCHAR(1), FILE)");
    }

    /** Live: "Invalid argument types for function 'LISTAGG': (FILE, VARCHAR(1))". */
    @Test
    public void listaggOverAFileIsRejected() {
        assertFails("SELECT LISTAGG(f, ',') FROM fk",
            "Invalid argument types for function 'LISTAGG': (FILE, VARCHAR(1))");
    }

    // ── Casts ────────────────────────────────────────────────────────────────

    /**
     * Live: "invalid type [CAST(FK.F AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'" — the
     * column qualified with its source table and the VARCHAR length expanded, which is exactly what
     * the strict-message printer renders since the alignment.
     */
    @Test
    public void castingAFileToVarcharIsRejected() {
        assertFails("SELECT CAST(f AS VARCHAR) FROM fk",
            "invalid type [CAST(FK.F AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT f::VARCHAR FROM fk",
            "invalid type [CAST(FK.F AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
    }

    /** The parameter names the conversion the TARGET implies, not the target itself. */
    @Test
    public void everyCastTargetNamesItsConversionFunction() {
        assertFails("SELECT CAST(f AS NUMBER) FROM fk",
            "invalid type [CAST(FK.F AS NUMBER(38,0))] for parameter 'TO_NUMBER'");
        assertFails("SELECT CAST(f AS FLOAT) FROM fk",
            "invalid type [CAST(FK.F AS FLOAT)] for parameter 'TO_DOUBLE'");
        assertFails("SELECT CAST(f AS BOOLEAN) FROM fk",
            "invalid type [CAST(FK.F AS BOOLEAN)] for parameter 'TO_BOOLEAN'");
        assertFails("SELECT CAST(f AS DATE) FROM fk",
            "invalid type [CAST(FK.F AS DATE)] for parameter 'TO_DATE'");
        assertFails("SELECT CAST(f AS TIMESTAMP) FROM fk",
            "invalid type [CAST(FK.F AS TIMESTAMP_NTZ(9))] for parameter 'TO_TIMESTAMP_NTZ'");
        assertFails("SELECT CAST(f AS OBJECT) FROM fk",
            "invalid type [CAST(FK.F AS OBJECT)] for parameter 'TO_OBJECT'");
        assertFails("SELECT CAST(f AS VARIANT) FROM fk",
            "invalid type [CAST(FK.F AS VARIANT)] for parameter 'TO_VARIANT'");
        assertFails("SELECT CAST(f AS ARRAY) FROM fk",
            "invalid type [CAST(FK.F AS ARRAY)] for parameter 'TO_ARRAY'");
        assertFails("SELECT CAST(f AS BINARY) FROM fk",
            "invalid type [CAST(FK.F AS BINARY(67108864))] for parameter 'TO_BINARY'");
    }

    /** A TRY_CAST renders WITHOUT its target live, and is rejected all the same. */
    @Test
    public void tryCastFromAFileIsRejected() {
        assertFails("SELECT TRY_CAST(f AS VARCHAR) FROM fk",
            "invalid type [TRY_CAST(FK.F)] for parameter 'TO_VARCHAR'");
    }

    /** The explicit conversion FUNCTIONS carry the same rule, naming the canonical conversion. */
    @Test
    public void conversionFunctionsOverAFileAreRejected() {
        assertFails("SELECT TO_VARCHAR(f) FROM fk",
            "invalid type [TO_VARCHAR(FK.F)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TO_CHAR(f) FROM fk",
            "invalid type [TO_CHAR(FK.F)] for parameter 'TO_CHAR'");
        assertFails("SELECT TO_NUMBER(f) FROM fk",
            "invalid type [TO_NUMBER(FK.F)] for parameter 'TO_NUMBER'");
        assertFails("SELECT TO_VARIANT(f) FROM fk",
            "invalid type [TO_VARIANT(FK.F)] for parameter 'TO_VARIANT'");
        assertFails("SELECT TO_OBJECT(f) FROM fk",
            "invalid type [TO_OBJECT(FK.F)] for parameter 'TO_OBJECT'");
    }

    /** {@code TRY_TO_NUMBER} renders its own name but reports the CANONICAL parameter, as live does. */
    @Test
    public void tryConversionFunctionsNameTheCanonicalParameter() {
        assertFails("SELECT TRY_TO_NUMBER(f) FROM fk",
            "invalid type [TRY_TO_NUMBER(FK.F)] for parameter 'TO_NUMBER'");
    }

    // ── The ACCEPTED forms these rejections must never swallow ───────────────

    /**
     * Live-accepted over a FILE: equality (both directions of ordering comparison too), IS NULL,
     * DISTINCT, COUNT and COUNT(DISTINCT). A tightening that broke any of these would be a regression,
     * so each is pinned here rather than left to the rejection tests to imply.
     */
    @Test
    public void comparisonsDistinctAndCountStillWork() {
        assertAccepted("SELECT id FROM fk WHERE f = f", 2);
        assertAccepted("SELECT id FROM fk WHERE f <> f", 0);
        assertAccepted("SELECT id FROM fk WHERE f > f", 0);
        assertAccepted("SELECT id FROM fk WHERE f IS NULL", 0);
        assertAccepted("SELECT id FROM fk WHERE f IS NOT NULL", 2);
        assertAccepted("SELECT DISTINCT f FROM fk", 2);
        assertEquals("2", scalar("SELECT COUNT(f) FROM fk"));
        assertEquals("2", scalar("SELECT COUNT(DISTINCT f) FROM fk"));
    }

    /** Live-accepted: the aggregates that do NOT order their input, and the value-returning windows. */
    @Test
    public void nonOrderingAggregatesAndWindowsStillWork() {
        assertAccepted("SELECT ANY_VALUE(f) FROM fk", 1);
        assertAccepted("SELECT ARRAY_AGG(f) FROM fk", 1);
        assertAccepted("SELECT ARRAY_UNIQUE_AGG(f) FROM fk", 1);
        assertAccepted("SELECT MAX_BY(id, f) FROM fk", 1);
        assertAccepted("SELECT COUNT(f) OVER () FROM fk", 2);
        assertAccepted("SELECT FIRST_VALUE(f) OVER (ORDER BY id) FROM fk", 2);
        assertAccepted("SELECT LAG(f) OVER (ORDER BY id) FROM fk", 2);
    }

    /** Live-accepted: a FILE is a perfectly good join key, set-operation column and IN operand. */
    @Test
    public void joinsSetOperationsAndInStillWork() {
        assertAccepted("SELECT a.id FROM fk a JOIN fk b ON a.f = b.f", 2);
        assertAccepted("SELECT f FROM fk UNION SELECT f FROM fk", 2);
        assertAccepted("SELECT f FROM fk UNION ALL SELECT f FROM fk", 4);
        assertAccepted("SELECT id FROM fk WHERE f IN (SELECT f FROM fk)", 2);
    }

    /** Live-accepted: the conditionals return the FILE itself, and containers hold it. */
    @Test
    public void conditionalsAndContainersStillAcceptAFile() {
        assertAccepted("SELECT IFF(TRUE, f, f) FROM fk", 2);
        assertAccepted("SELECT COALESCE(f, f) FROM fk", 2);
        assertAccepted("SELECT GREATEST(f, f) FROM fk", 2);
        assertAccepted("SELECT CASE WHEN id = 1 THEN f ELSE f END FROM fk", 2);
        assertAccepted("SELECT ARRAY_CONSTRUCT(f) FROM fk", 2);
        assertAccepted("SELECT OBJECT_CONSTRUCT('k', f) FROM fk", 2);
        assertAccepted("SELECT TO_FILE(f) FROM fk", 2);
    }

    /** Live-accepted: an ACCESSOR result is an ordinary VARCHAR, so it keys and aggregates normally. */
    @Test
    public void accessorResultsAreOrdinaryKeys() {
        assertAccepted("SELECT id FROM fk ORDER BY FL_GET_RELATIVE_PATH(f)", 2);
        assertAccepted("SELECT COUNT(*) FROM fk GROUP BY FL_GET_RELATIVE_PATH(f)", 2);
        assertEquals("two.txt", scalar("SELECT MAX(FL_GET_RELATIVE_PATH(f)) FROM fk"));
    }

    /**
     * The critical NON-generalisation: an OBJECT is not a FILE. Live, {@code GROUP BY o},
     * {@code ORDER BY o} and {@code CAST(o AS VARCHAR)} all work, so the KEY rules and the CAST rule
     * may not key off "semi-structured" instead of the FILE type itself.
     *
     * <p>The ordering-aggregate rule is the one exception, and it is a genuine overlap rather than a
     * widening: live, {@code MAX(o)} fails "Function MAX does not support OBJECT argument
     * type" just as {@code MAX(f)} names FILE. That case is owned by
     * {@code SemiStructuredOrderingAggregateTest}; the four assertions here are the ones that must
     * stay accepted whatever else is tightened.
     */
    @Test
    public void anObjectColumnIsUnaffectedByEveryFileRule() {
        assertAccepted("SELECT COUNT(*) FROM fk GROUP BY o", 2);
        assertAccepted("SELECT id FROM fk ORDER BY o", 2);
        assertAccepted("SELECT CAST(o AS VARCHAR) FROM fk", 2);
        assertAccepted("SELECT ROW_NUMBER() OVER (PARTITION BY o ORDER BY id) FROM fk", 2);
    }

    /** And a VARCHAR column keeps every one of these operations, unchanged. */
    @Test
    public void aVarcharColumnIsUnaffectedByEveryFileRule() {
        assertAccepted("SELECT COUNT(*) FROM fk GROUP BY s", 2);
        assertAccepted("SELECT id FROM fk ORDER BY s", 2);
        assertAccepted("SELECT CAST(s AS VARCHAR) FROM fk", 2);
        assertAccepted("SELECT 'x' || s FROM fk", 2);
        assertEquals("y", scalar("SELECT MAX(s) FROM fk"));
    }
}
