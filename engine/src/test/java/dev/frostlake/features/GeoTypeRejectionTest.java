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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GEOGRAPHY / GEOMETRY positions Snowflake rejects at COMPILE time: grouping / sorting keys,
 * every conversion, the reading functions, the operators and comparison.
 *
 * <p>Every expectation was measured against a live Snowflake account on over a table
 * carrying both a populated {@code GEOGRAPHY} and a populated {@code GEOMETRY} column, alongside an
 * OBJECT column that bounds each rule. Frostlake used to accept all of them and work from the GeoJSON
 * text, so {@code GROUP BY g} grouped by the JSON, {@code CAST(g AS VARCHAR)} handed it back and
 * {@code g = g} compared it.
 *
 * <p>The tests run in the ENGINE module, WITHOUT the optional {@code frostlake-geo} module on the
 * classpath — which is the point: a GEOGRAPHY column can be declared with the module absent (see
 * {@link GeoModuleAbsenceTest}), so the rejections must not depend on it. Every one of them is a
 * PLAN-TIME check that reads the column's DECLARED type, so an empty table rejects exactly as a
 * populated one does — live-verified with {@code WHERE 1 = 0}. The rows that a geo value would need
 * are therefore not required here; {@code GeoTypeRejectionTest} in the geo module covers the same
 * surface over real geo values, plus the {@code ST_} functions that must keep working.
 */
public class GeoTypeRejectionTest extends BaseDatabaseTest {

    @BeforeEach
    public void createGeoTable() {
        engine.execute("CREATE TABLE gt (id INTEGER, g GEOGRAPHY, gm GEOMETRY, s VARCHAR, n NUMBER)");
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

    /** A form live ACCEPTS: it must still run. These are the guard against an over-broad rule. */
    private void assertAccepted(final String sql) {
        engine.executeQuery(sql);
    }

    // ── GROUP BY keys (live SQLSTATE 42804, vendor 92102) ────────────────────

    @Test
    public void groupByAGeoColumnIsRejected() {
        assertFails("SELECT COUNT(*) FROM gt GROUP BY g",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("SELECT COUNT(*) FROM gt GROUP BY gm",
            "Expressions of type GEOMETRY cannot be used as GROUP BY keys");
    }

    /** The ordinal and the alias reach the same rule: both resolve to the geo-typed item. */
    @Test
    public void groupByAGeoOrdinalOrAliasIsRejected() {
        assertFails("SELECT g, COUNT(*) FROM gt GROUP BY 1",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("SELECT g AS x, COUNT(*) FROM gt GROUP BY x",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
    }

    /** A geo key anywhere in a multi-key clause, or inside ROLLUP / CUBE, rejects the whole clause. */
    @Test
    public void groupByRejectsAGeoKeyAmongOthersAndInSuperGroups() {
        assertFails("SELECT COUNT(*) FROM gt GROUP BY n, g",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("SELECT COUNT(*) FROM gt GROUP BY ROLLUP(g)",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("SELECT COUNT(*) FROM gt GROUP BY CUBE(gm)",
            "Expressions of type GEOMETRY cannot be used as GROUP BY keys");
    }

    /**
     * Live: a NULL-choosing conditional over geo branches IS a geo value (its result column reports
     * GEOGRAPHY), so the key rule reads through it.
     */
    @Test
    public void groupByAConditionalYieldingGeoIsRejected() {
        assertFails("SELECT COUNT(*) FROM gt GROUP BY IFF(TRUE, g, g)",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("SELECT COUNT(*) FROM gt GROUP BY COALESCE(g, g)",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
    }

    /** The rejection is a PLAN-time one: an empty input and a derived relation reject alike. */
    @Test
    public void groupByAGeoKeyIsRejectedOverEmptyInputAndThroughACte() {
        assertFails("SELECT COUNT(*) FROM gt WHERE 1 = 0 GROUP BY g",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
        assertFails("WITH q AS (SELECT g AS x FROM gt) SELECT COUNT(*) FROM q GROUP BY x",
            "Expressions of type GEOGRAPHY cannot be used as GROUP BY keys");
    }

    // ── ORDER BY keys (live SQLSTATE 42804, vendor 92103) ────────────────────

    @Test
    public void orderByAGeoColumnIsRejected() {
        assertFails("SELECT id FROM gt ORDER BY g",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
        assertFails("SELECT id FROM gt ORDER BY gm",
            "Expressions of type GEOMETRY cannot be used as ORDER BY keys");
    }

    @Test
    public void orderByAGeoOrdinalAliasOrDirectionIsRejected() {
        assertFails("SELECT g FROM gt ORDER BY 1",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
        assertFails("SELECT g AS x FROM gt ORDER BY x",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
        assertFails("SELECT id FROM gt ORDER BY g DESC NULLS LAST",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
        assertFails("SELECT id FROM gt ORDER BY n, g",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
    }

    // ── PARTITION BY keys (live SQLSTATE 42804, vendor 92104) ────────────────

    @Test
    public void windowPartitionByAGeoColumnIsRejected() {
        assertFails("SELECT ROW_NUMBER() OVER (PARTITION BY g ORDER BY id) FROM gt",
            "Expressions of type GEOGRAPHY cannot be used as PARTITION BY keys");
        assertFails("SELECT ROW_NUMBER() OVER (PARTITION BY gm ORDER BY id) FROM gt",
            "Expressions of type GEOMETRY cannot be used as PARTITION BY keys");
    }

    /** The ORDER BY inside an OVER spec reports the ORDER BY variant, not the PARTITION BY one. */
    @Test
    public void windowOrderByAGeoColumnReportsTheOrderByVariant() {
        assertFails("SELECT ROW_NUMBER() OVER (ORDER BY g) FROM gt",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
        assertFails("SELECT FIRST_VALUE(id) OVER (PARTITION BY n ORDER BY g) FROM gt",
            "Expressions of type GEOGRAPHY cannot be used as ORDER BY keys");
    }

    // ── the reading functions ────────────────────────────────────────────────

    /** The text family: live names the function and lists EVERY argument as written. */
    @Test
    public void textFunctionsRejectAGeoArgument() {
        assertFails("SELECT UPPER(g) FROM gt",
            "Invalid argument types for function 'UPPER': (GEOGRAPHY)");
        assertFails("SELECT UPPER(gm) FROM gt",
            "Invalid argument types for function 'UPPER': (GEOMETRY)");
        assertFails("SELECT LENGTH(g) FROM gt",
            "Invalid argument types for function 'LENGTH': (GEOGRAPHY)");
        assertFails("SELECT SUBSTR(g, 1, 3) FROM gt",
            "Invalid argument types for function 'SUBSTR': (GEOGRAPHY, NUMBER(1,0), NUMBER(1,0))");
        assertFails("SELECT SPLIT_PART(s, g, 1) FROM gt",
            "Invalid argument types for function 'SPLIT_PART': (VARCHAR(16777216), GEOGRAPHY, NUMBER(1,0))");
    }

    /** The numeric family refuses it with the same shape. */
    @Test
    public void numericFunctionsRejectAGeoArgument() {
        assertFails("SELECT ABS(g) FROM gt", "Invalid argument types for function 'ABS': (GEOGRAPHY)");
        assertFails("SELECT ABS(gm) FROM gt", "Invalid argument types for function 'ABS': (GEOMETRY)");
        assertFails("SELECT ROUND(g) FROM gt", "Invalid argument types for function 'ROUND': (GEOGRAPHY)");
        assertFails("SELECT POWER(g, 2) FROM gt",
            "Invalid argument types for function 'POWER': (GEOGRAPHY, NUMBER(1,0))");
    }

    /**
     * The VARIANT-reading surface refuses it too, even the parts that take a plain OBJECT quite
     * happily — {@code GET(o, 'k')} and {@code OBJECT_KEYS(o)} both return a value live.
     */
    @Test
    public void variantReadingFunctionsRejectAGeoArgument() {
        assertFails("SELECT TYPEOF(g) FROM gt",
            "Invalid argument types for function 'TYPEOF': (GEOGRAPHY)");
        assertFails("SELECT TO_JSON(g) FROM gt",
            "Invalid argument types for function 'TO_JSON': (GEOGRAPHY)");
        assertFails("SELECT IS_OBJECT(g) FROM gt",
            "Invalid argument types for function 'IS_OBJECT': (GEOGRAPHY)");
        assertFails("SELECT AS_VARCHAR(g) FROM gt",
            "Invalid argument types for function 'AS_VARCHAR': (GEOGRAPHY)");
        assertFails("SELECT OBJECT_KEYS(g) FROM gt",
            "Invalid argument types for function 'OBJECT_KEYS': (GEOGRAPHY)");
        assertFails("SELECT GET(g, 'type') FROM gt",
            "Invalid argument types for function 'GET': (GEOGRAPHY, VARCHAR(4))");
        assertFails("SELECT ARRAY_SIZE(g) FROM gt",
            "Invalid argument types for function 'ARRAY_SIZE': (GEOGRAPHY)");
        assertFails("SELECT OBJECT_INSERT(g, 'a', 1) FROM gt",
            "Invalid argument types for function 'OBJECT_INSERT': (GEOGRAPHY, VARCHAR(1), NUMBER(1,0))");
    }

    /** The colon path operator is GET sugar and reports it as such. */
    @Test
    public void colonPathAccessOnAGeoColumnIsRejected() {
        assertFails("SELECT g:type FROM gt",
            "Invalid argument types for function 'GET': (GEOGRAPHY, VARCHAR(4))");
    }

    /** The constructors use the "does not support" sentence, with a different tail for a KEY. */
    @Test
    public void semiStructuredConstructorsRejectAGeoArgument() {
        assertFails("SELECT ARRAY_CONSTRUCT(g) FROM gt",
            "Function ARRAY_CONSTRUCT does not support GEOGRAPHY argument type");
        assertFails("SELECT ARRAY_CONSTRUCT(1, g) FROM gt",
            "Function ARRAY_CONSTRUCT does not support GEOGRAPHY argument type");
        assertFails("SELECT OBJECT_CONSTRUCT('a', g) FROM gt",
            "Function OBJECT_CONSTRUCT does not support GEOGRAPHY argument type");
        assertFails("SELECT OBJECT_CONSTRUCT(g, 1) FROM gt",
            "Function OBJECT_CONSTRUCT does not support GEOGRAPHY argument type for keys");
    }

    // ── the aggregates ───────────────────────────────────────────────────────

    /** MAX / MIN / MODE order their input, and say so with their own sentence (SQLSTATE 22000). */
    @Test
    public void orderingAggregatesRejectAGeoArgument() {
        assertFails("SELECT MAX(g) FROM gt", "Function MAX does not support GEOGRAPHY argument type");
        assertFails("SELECT MAX(gm) FROM gt", "Function MAX does not support GEOMETRY argument type");
        assertFails("SELECT MIN(g) FROM gt", "Function MIN does not support GEOGRAPHY argument type");
        assertFails("SELECT MODE(g) FROM gt", "Function MODE does not support GEOGRAPHY argument type");
        assertFails("SELECT MAX(g) OVER () FROM gt",
            "Function MAX does not support GEOGRAPHY argument type");
    }

    /** The numeric aggregates report the argument-type list. */
    @Test
    public void numericAggregatesRejectAGeoArgument() {
        assertFails("SELECT SUM(g) FROM gt", "Invalid argument types for function 'SUM': (GEOGRAPHY)");
        assertFails("SELECT SUM(gm) FROM gt", "Invalid argument types for function 'SUM': (GEOMETRY)");
        assertFails("SELECT LISTAGG(g) FROM gt",
            "Invalid argument types for function 'LISTAGG': (GEOGRAPHY)");
    }

    /** MEDIAN and the percentiles report the incompatible-types pair (SQLSTATE 42846). */
    @Test
    public void orderedValueAggregatesRejectAGeoArgument() {
        assertFails("SELECT MEDIAN(g) FROM gt", "incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]");
        assertFails("SELECT MEDIAN(gm) FROM gt", "incompatible types: [GEOMETRY] and [NUMBER(9,0)]");
        assertFails("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY g) FROM gt",
            "incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]");
        assertFails("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY g) FROM gt",
            "incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]");
    }

    /** The moment aggregates reach their sum of squares first and report the multiplication. */
    @Test
    public void momentAggregatesReportTheInternalMultiplication() {
        assertFails("SELECT STDDEV(g) FROM gt",
            "Invalid argument types for function '*': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT VARIANCE(gm) FROM gt",
            "Invalid argument types for function '*': (GEOMETRY, GEOMETRY)");
    }

    /**
     * The aggregates that CARRY a value rather than order it split from the OBJECT case: live returns
     * an answer for {@code ARRAY_AGG(o)}, {@code HASH_AGG(o)}, {@code MAX_BY(o, n)} and
     * {@code APPROX_COUNT_DISTINCT(o)} and refuses every geo spelling.
     */
    @Test
    public void carryingAggregatesRejectAGeoArgumentEvenThoughTheyTakeAnObject() {
        assertFails("SELECT ARRAY_AGG(g) FROM gt",
            "Invalid argument types for function 'ARRAY_AGG': (GEOGRAPHY)");
        assertFails("SELECT ARRAY_UNIQUE_AGG(g) FROM gt",
            "Invalid argument types for function 'ARRAY_UNIQUE_AGG': (GEOGRAPHY)");
        assertFails("SELECT HASH_AGG(g) FROM gt",
            "Invalid argument types for function 'HASH_AGG': (GEOGRAPHY)");
        assertFails("SELECT MAX_BY(g, n) FROM gt",
            "Invalid argument types for function 'MAX_BY': (GEOGRAPHY, NUMBER(38,0))");
        assertFails("SELECT MIN_BY(n, g) FROM gt",
            "Invalid argument types for function 'MIN_BY': (NUMBER(38,0), GEOGRAPHY)");
        assertFails("SELECT OBJECT_AGG(s, g) FROM gt",
            "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(16777216), GEOGRAPHY)");
    }

    /**
     * Live reports the internal 'HLL_ACCUMULATE' for the approximate counter; Frostlake reports the
     * name WRITTEN, the same choice it makes where live reports 'SUM' for {@code AVG}. The rejection
     * itself is the measured behaviour.
     */
    @Test
    public void theApproximateCounterRejectsAGeoArgument() {
        assertFails("SELECT APPROX_COUNT_DISTINCT(g) FROM gt",
            "Invalid argument types for function 'HLL_ACCUMULATE': (GEOGRAPHY)");
    }

    // ── the window value functions ───────────────────────────────────────────

    @Test
    public void offsetWindowFunctionsRejectAGeoArgument() {
        assertFails("SELECT LAG(g) OVER (ORDER BY id) FROM gt",
            "Invalid argument types for function 'LAG': (GEOGRAPHY)");
        assertFails("SELECT LEAD(gm) OVER (ORDER BY id) FROM gt",
            "Invalid argument types for function 'LEAD': (GEOMETRY)");
        assertFails("SELECT NTH_VALUE(g, 1) OVER (ORDER BY id) FROM gt",
            "Invalid argument types for function 'NTH_VALUE': (GEOGRAPHY, NUMBER(1,0))");
    }

    // ── the operators ────────────────────────────────────────────────────────

    @Test
    public void concatenationRejectsAGeoOperand() {
        assertFails("SELECT 'x' || g FROM gt",
            "Invalid argument types for function '||': (VARCHAR(1), GEOGRAPHY)");
        assertFails("SELECT 'x' || gm FROM gt",
            "Invalid argument types for function '||': (VARCHAR(1), GEOMETRY)");
    }

    @Test
    public void arithmeticRejectsAGeoOperand() {
        assertFails("SELECT g + 1 FROM gt",
            "Invalid argument types for function '+': (GEOGRAPHY, NUMBER(1,0))");
        assertFails("SELECT g * g FROM gt",
            "Invalid argument types for function '*': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT -g FROM gt", "Invalid argument types for function 'NEGATE': (GEOGRAPHY)");
    }

    /**
     * A geo value does not COMPARE, where an OBJECT and a FILE both do. Live refuses the projected
     * comparison, the WHERE spelling and {@code IN} alike.
     */
    @Test
    public void comparisonRejectsAGeoOperand() {
        assertFails("SELECT g = g FROM gt",
            "Invalid argument types for function '=': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT gm = gm FROM gt",
            "Invalid argument types for function '=': (GEOMETRY, GEOMETRY)");
        assertFails("SELECT g < g FROM gt",
            "Invalid argument types for function '<': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT id FROM gt WHERE g = g",
            "Invalid argument types for function '=': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT id FROM gt WHERE g > g",
            "Invalid argument types for function '>': (GEOGRAPHY, GEOGRAPHY)");
    }

    @Test
    public void inListRejectsAGeoOperand() {
        assertFails("SELECT id FROM gt WHERE g IN (g)",
            "Invalid argument types for function 'IN': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT id FROM gt WHERE gm NOT IN (gm)",
            "Invalid argument types for function 'IN': (GEOMETRY, GEOMETRY)");
    }

    /** EQUAL_NULL and NULLIF compare too, where both take an OBJECT live. */
    @Test
    public void comparingFunctionsRejectAGeoArgument() {
        assertFails("SELECT EQUAL_NULL(g, g) FROM gt",
            "Invalid argument types for function 'EQUAL_NULL': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT NULLIF(g, g) FROM gt",
            "Invalid argument types for function 'NULLIF': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT GREATEST(g, g) FROM gt",
            "Invalid argument types for function 'GREATEST': (GEOGRAPHY, GEOGRAPHY)");
        assertFails("SELECT LEAST(g, g) FROM gt",
            "Invalid argument types for function 'LEAST': (GEOGRAPHY, GEOGRAPHY)");
    }

    // ── the conversions ──────────────────────────────────────────────────────

    /**
     * EVERY cast target is refused, including the identity one — the sharpest difference from a plain
     * OBJECT, which casts to VARCHAR and returns its JSON. Live renders the cast from its analysed
     * plan (qualified, upper-cased, default length); Frostlake renders it as WRITTEN, the same choice
     * the FILE rule already makes, so only the parameter name is asserted in full here.
     */
    @Test
    public void everyCastFromAGeoValueIsRejected() {
        assertFails("SELECT CAST(g AS VARCHAR) FROM gt",
            "invalid type [CAST(GT.G AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT CAST(gm AS VARCHAR) FROM gt",
            "invalid type [CAST(GT.GM AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT g::VARCHAR FROM gt",
            "invalid type [CAST(GT.G AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT CAST(g AS VARCHAR(20)) FROM gt",
            "invalid type [CAST(GT.G AS VARCHAR(20))] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TRY_CAST(g AS VARCHAR) FROM gt",
            "invalid type [TRY_CAST(GT.G)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT CAST(g AS VARIANT) FROM gt",
            "invalid type [CAST(GT.G AS VARIANT)] for parameter 'TO_VARIANT'");
        assertFails("SELECT CAST(g AS OBJECT) FROM gt",
            "invalid type [CAST(GT.G AS OBJECT)] for parameter 'TO_OBJECT'");
        assertFails("SELECT CAST(g AS NUMBER) FROM gt",
            "invalid type [CAST(GT.G AS NUMBER(38,0))] for parameter 'TO_NUMBER'");
        assertFails("SELECT CAST(g AS GEOGRAPHY) FROM gt",
            "invalid type [CAST(GT.G AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'");
        assertFails("SELECT CAST(gm AS GEOGRAPHY) FROM gt",
            "invalid type [CAST(GT.GM AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'");
    }

    /** The conversion FUNCTIONS report the CANONICAL conversion, with TO_CHAR reporting itself. */
    @Test
    public void everyConversionFunctionOverAGeoValueIsRejected() {
        assertFails("SELECT TO_VARCHAR(g) FROM gt",
            "invalid type [TO_VARCHAR(GT.G)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TO_VARCHAR(gm) FROM gt",
            "invalid type [TO_VARCHAR(GT.GM)] for parameter 'TO_VARCHAR'");
        assertFails("SELECT TO_CHAR(g) FROM gt",
            "invalid type [TO_CHAR(GT.G)] for parameter 'TO_CHAR'");
        assertFails("SELECT TO_NUMBER(g) FROM gt",
            "invalid type [TO_NUMBER(GT.G)] for parameter 'TO_NUMBER'");
        assertFails("SELECT TRY_TO_NUMBER(g) FROM gt",
            "invalid type [TRY_TO_NUMBER(GT.G)] for parameter 'TO_NUMBER'");
        assertFails("SELECT TO_VARIANT(g) FROM gt",
            "invalid type [TO_VARIANT(GT.G)] for parameter 'TO_VARIANT'");
        assertFails("SELECT TO_OBJECT(g) FROM gt",
            "invalid type [TO_OBJECT(GT.G)] for parameter 'TO_OBJECT'");
        assertFails("SELECT TO_ARRAY(g) FROM gt",
            "invalid type [TO_ARRAY(GT.G)] for parameter 'TO_ARRAY'");
        assertFails("SELECT TO_BOOLEAN(g) FROM gt",
            "invalid type [TO_BOOLEAN(GT.G)] for parameter 'TO_BOOLEAN'");
    }

    /** The format argument is rendered too, matching live's "invalid type [TO_CHAR(GT.G, 'x')]". */
    @Test
    public void theConversionMessageRendersEveryArgument() {
        assertFails("SELECT TO_CHAR(g, 'x') FROM gt",
            "invalid type [TO_CHAR(GT.G, 'x')] for parameter 'TO_CHAR'");
    }

    // ── the ACCEPTED forms: the guard against an over-broad rule ─────────────

    /**
     * A geo value is still a DISTINCT value, is still counted, and is still carried by the
     * NULL-choosing conditionals — every one of these returns a result live, and none may be caught by
     * the rules above.
     */
    @Test
    public void distinctCountAndCarryingFormsAreAccepted() {
        assertAccepted("SELECT DISTINCT g FROM gt");
        assertAccepted("SELECT DISTINCT gm FROM gt");
        assertAccepted("SELECT DISTINCT g, gm FROM gt");
        assertAccepted("SELECT DISTINCT g, n FROM gt");
        assertAccepted("SELECT COUNT(g) FROM gt");
        assertAccepted("SELECT COUNT(DISTINCT g) FROM gt");
        assertAccepted("SELECT COUNT(g) OVER () FROM gt");
        assertAccepted("SELECT ANY_VALUE(g) FROM gt");
        assertAccepted("SELECT ANY_VALUE(gm) FROM gt");
        assertAccepted("SELECT HASH(g) FROM gt");
    }

    @Test
    public void nullChoosingConditionalsOverGeoAreAccepted() {
        assertAccepted("SELECT IFF(TRUE, g, g) FROM gt");
        assertAccepted("SELECT COALESCE(g, g) FROM gt");
        assertAccepted("SELECT NVL(g, g) FROM gt");
        assertAccepted("SELECT IFNULL(g, g) FROM gt");
        assertAccepted("SELECT CASE WHEN TRUE THEN g ELSE g END FROM gt");
        assertAccepted("SELECT COALESCE(g, NULL) FROM gt");
    }

    /** FIRST_VALUE / LAST_VALUE carry a geo value where LAG / LEAD refuse it — measured, not inferred. */
    @Test
    public void firstAndLastValueOverGeoAreAccepted() {
        assertAccepted("SELECT FIRST_VALUE(g) OVER (ORDER BY id) FROM gt");
        assertAccepted("SELECT LAST_VALUE(g) OVER (ORDER BY id) FROM gt");
    }

    @Test
    public void setOperatorsAndNullTestsOverGeoAreAccepted() {
        assertAccepted("SELECT g FROM gt UNION ALL SELECT g FROM gt");
        assertAccepted("SELECT g FROM gt UNION SELECT g FROM gt");
        assertAccepted("SELECT g FROM gt INTERSECT SELECT g FROM gt");
        assertAccepted("SELECT g FROM gt EXCEPT SELECT g FROM gt");
        assertAccepted("SELECT id FROM gt WHERE g IS NULL");
        assertAccepted("SELECT id FROM gt WHERE gm IS NOT NULL");
    }

    /** None of this may leak onto the ordinary columns beside the geo ones. */
    @Test
    public void theRulesDoNotTouchTheNonGeoColumns() {
        assertAccepted("SELECT COUNT(*) FROM gt GROUP BY n");
        assertAccepted("SELECT id FROM gt ORDER BY n");
        assertAccepted("SELECT ROW_NUMBER() OVER (PARTITION BY s ORDER BY id) FROM gt");
        assertAccepted("SELECT UPPER(s) FROM gt");
        assertAccepted("SELECT SUM(n) FROM gt");
        assertAccepted("SELECT CAST(n AS VARCHAR) FROM gt");
        assertAccepted("SELECT 's' || s FROM gt");
        assertAccepted("SELECT id FROM gt WHERE s = 'x'");
        assertEquals(0, engine.executeQuery("SELECT id FROM gt WHERE n IN (1, 2)").getRows().size());
    }

    /**
     * A geo COLUMN in a projection is untouched: only reading, ordering and comparing it are refused,
     * and live returns the value for every one of these.
     */
    @Test
    public void projectingAndInsertingAGeoColumnIsAccepted() {
        assertAccepted("SELECT g FROM gt");
        assertAccepted("SELECT g, gm FROM gt");
        assertAccepted("SELECT * FROM gt");
        assertAccepted("SELECT * FROM (SELECT g FROM gt) WHERE 1 = 1");
    }
}
