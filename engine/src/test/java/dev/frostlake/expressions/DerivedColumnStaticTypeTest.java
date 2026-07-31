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
 * A column projected through a derived table, a CTE or a view keeps its type. Frostlake used to declare
 * every such column VARCHAR regardless of the value's real type, which gave every compile-time type
 * rejection a trivial bypass: {@code SELECT COUNT(*) FROM t GROUP BY f} was refused while
 * {@code SELECT COUNT(*) FROM (SELECT f FROM t) GROUP BY f} — the same query, one level down — was not.
 *
 * <p>Measured against a live Snowflake account on reading the derived column's own type
 * with {@code SYSTEM$TYPEOF} rather than inferring it from whether a rejection fired. Live reports the
 * SOURCE type unchanged through every wrapper: a FILE column stays FILE, an OBJECT stays OBJECT, a
 * {@code VECTOR(FLOAT, 3)} stays {@code VECTOR(FLOAT, 3)} and a {@code VECTOR_TRUNC(vec, 2)} becomes
 * {@code VECTOR(FLOAT, 2)}, through a plain subquery, a named CTE, two levels of nesting, a column
 * alias, an ANSI derived-column list, a UNION ALL of agreeing branches, a recursive CTE's anchor, a
 * LATERAL, a join and a view. The rejections that follow carry the identical message and SQLSTATE they
 * do over the base table (42804 / 92102 for a FILE key, 22000 / 2016 for an ordering aggregate).
 *
 * <p>The ACCEPTED forms below matter at least as much: making a type KNOWN must not make it WRONG.
 * Live keeps accepting a VARIANT-producing projection ({@code PARSE_JSON}, {@code TO_VARIANT}, a
 * colon path, a table function's {@code VALUE}) and every ordinary query over a derived column, so an
 * over-broad inference would show up here first.
 */
public class DerivedColumnStaticTypeTest extends BaseDatabaseTest {

    /** A live-account LAST_MODIFIED, in the RFC-1123 shape Snowflake requires of a FILE descriptor. */
    private static final String LAST_MODIFIED = "Mon, 03 Aug 2026 11:24:13 GMT";

    @BeforeEach
    public void createTypedTable() {
        engine.execute("CREATE TABLE dt (id INTEGER, f FILE, o OBJECT, a ARRAY, v VARIANT,"
            + " s VARCHAR, n NUMBER(10,2), vec VECTOR(FLOAT,3))");
        engine.execute("INSERT INTO dt SELECT 1, " + fileOf("hello.txt", "e1")
            + ", OBJECT_CONSTRUCT('k', 1), ARRAY_CONSTRUCT(1, 2), TO_VARIANT(1), 'x', 1.5,"
            + " [1,2,3]::VECTOR(FLOAT,3)");
        engine.execute("INSERT INTO dt SELECT 2, " + fileOf("two.txt", "e2")
            + ", OBJECT_CONSTRUCT('k', 2), ARRAY_CONSTRUCT(3, 4), TO_VARIANT(2), 'y', 2.5,"
            + " [4,5,6]::VECTOR(FLOAT,3)");
    }

    /** A FILE descriptor built from metadata, so it needs no staged file on either backend. */
    private static String fileOf(final String path, final String etag) {
        return "OBJECT_CONSTRUCT('STAGE', '@D.S.ST', 'RELATIVE_PATH', '" + path + "', 'SIZE', 12,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain',"
            + " 'ETAG', '" + etag + "')";
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

    private static final String FILE_KEY = "Expressions of type FILE cannot be used as GROUP BY keys";
    private static final String MAX_OBJECT =
        "SQL compilation error:\nFunction MAX does not support OBJECT argument type";
    private static final String MAX_ARRAY =
        "SQL compilation error:\nFunction MAX does not support ARRAY argument type";

    // ── The bypass itself: the base-table rejection, then the same query one level down ──────────

    /** The pair that names the defect: identical queries, one wrapped, and live refuses both. */
    @Test
    public void theSameRejectionFiresDirectlyAndThroughADerivedTable() {
        assertFails("SELECT COUNT(*) FROM dt GROUP BY f", FILE_KEY);
        assertFails("SELECT COUNT(*) FROM (SELECT id, f FROM dt) GROUP BY f", FILE_KEY);
        assertFails("SELECT MAX(o) FROM dt", MAX_OBJECT);
        assertFails("SELECT MAX(o) FROM (SELECT id, o FROM dt)", MAX_OBJECT);
    }

    // ── Case 1: a plain derived table ────────────────────────────────────────────────────────────

    @Test
    public void aPlainDerivedTableCarriesItsColumnTypes() {
        assertFails("SELECT COUNT(*) FROM (SELECT id, f FROM dt) GROUP BY f", FILE_KEY);
        assertFails("SELECT MAX(o) FROM (SELECT id, o FROM dt)", MAX_OBJECT);
        assertFails("SELECT MAX(a) FROM (SELECT id, a FROM dt)", MAX_ARRAY);
    }

    /** A star projection carries the source column's type, not a placeholder. */
    @Test
    public void aStarProjectionCarriesItsColumnTypes() {
        assertFails("SELECT COUNT(*) FROM (SELECT * FROM dt) GROUP BY f", FILE_KEY);
        assertFails("SELECT MAX(o) FROM (SELECT * FROM dt)", MAX_OBJECT);
    }

    /** A table alias, and the ANSI derived-column list that renames the columns positionally. */
    @Test
    public void anAliasedDerivedTableCarriesItsColumnTypes() {
        assertFails("SELECT COUNT(*) FROM (SELECT id, f FROM dt) d GROUP BY d.f", FILE_KEY);
        assertFails("SELECT COUNT(*) FROM (SELECT id, f FROM dt) d (c1, c2) GROUP BY d.c2", FILE_KEY);
        assertFails("SELECT MAX(d.c2) FROM (SELECT id, o FROM dt) d (c1, c2)", MAX_OBJECT);
    }

    // ── Case 2: a CTE ────────────────────────────────────────────────────────────────────────────

    @Test
    public void aCteCarriesItsColumnTypes() {
        assertFails("WITH c AS (SELECT id, f FROM dt) SELECT COUNT(*) FROM c GROUP BY f", FILE_KEY);
        assertFails("WITH c AS (SELECT id, o FROM dt) SELECT MAX(o) FROM c", MAX_OBJECT);
        assertFails("WITH c AS (SELECT id, a FROM dt) SELECT MAX(a) FROM c", MAX_ARRAY);
    }

    /** {@code WITH c (k, ff) AS ...} renames the columns; the renamed column keeps the type. */
    @Test
    public void aCteColumnListKeepsTheRenamedColumnsType() {
        assertFails("WITH c (k, ff) AS (SELECT id, f FROM dt) SELECT COUNT(*) FROM c GROUP BY ff",
            FILE_KEY);
    }

    // ── Case 3: nesting, two and three levels deep ───────────────────────────────────────────────

    @Test
    public void nestedDerivedTablesCarryTheTypeAllTheWayOut() {
        assertFails("SELECT COUNT(*) FROM (SELECT * FROM (SELECT id, f FROM dt)) GROUP BY f", FILE_KEY);
        assertFails("SELECT COUNT(*) FROM (SELECT * FROM (SELECT * FROM (SELECT id, f FROM dt)))"
            + " GROUP BY f", FILE_KEY);
        assertFails("SELECT MAX(o) FROM (SELECT * FROM (SELECT id, o FROM dt))", MAX_OBJECT);
    }

    @Test
    public void aCteOverACteCarriesTheType() {
        assertFails("WITH c1 AS (SELECT id, o FROM dt), c2 AS (SELECT * FROM c1) SELECT MAX(o) FROM c2",
            MAX_OBJECT);
        assertFails("WITH c1 AS (SELECT id, f FROM dt), c2 AS (SELECT * FROM c1)"
            + " SELECT COUNT(*) FROM c2 GROUP BY f", FILE_KEY);
    }

    // ── Case 4: a column aliased inside ──────────────────────────────────────────────────────────

    @Test
    public void anAliasInsideCarriesTheAliasedExpressionsType() {
        assertFails("SELECT COUNT(*) FROM (SELECT f AS g FROM dt) GROUP BY g", FILE_KEY);
        assertFails("SELECT MAX(p) FROM (SELECT o AS p FROM dt)", MAX_OBJECT);
        assertFails("WITH c AS (SELECT o AS p FROM dt) SELECT MAX(p) FROM c", MAX_OBJECT);
        assertFails("SELECT COUNT(*) FROM (SELECT * FROM (SELECT f AS g FROM dt)) GROUP BY g",
            FILE_KEY);
    }

    // ── Case 5: the inner projection is an expression ────────────────────────────────────────────

    /** Live {@code SYSTEM$TYPEOF} over these derived columns: OBJECT, ARRAY, OBJECT, ARRAY. */
    @Test
    public void anExpressionProjectionCarriesTheExpressionsType() {
        assertFails("SELECT MAX(x) FROM (SELECT OBJECT_CONSTRUCT('k', 1) AS x FROM dt)", MAX_OBJECT);
        assertFails("SELECT MAX(x) FROM (SELECT ARRAY_CONSTRUCT(1, 2) AS x FROM dt)", MAX_ARRAY);
        assertFails("SELECT MAX(x) FROM (SELECT CASE WHEN TRUE THEN o ELSE o END AS x FROM dt)",
            MAX_OBJECT);
        assertFails("SELECT MAX(x) FROM (SELECT ARRAY_AGG(n) AS x FROM dt)", MAX_ARRAY);
    }

    /** A conditional over FILE branches is a FILE — live rejects it as a GROUP BY key. */
    @Test
    public void aConditionalOverFileBranchesIsStillAFile() {
        assertFails("SELECT COUNT(*) FROM (SELECT IFF(TRUE, f, f) AS x FROM dt) GROUP BY x", FILE_KEY);
    }

    /** Live: {@code VECTOR_TRUNC(vec, 2)} projected out is {@code VECTOR(FLOAT, 2)}, so the outer
     *  dimension check sees a 2-vector and refuses to compare it with a 3-vector. */
    @Test
    public void aVectorExpressionCarriesItsDimensionOut() {
        assertFails("SELECT VECTOR_L2_DISTANCE(x, [1,2,3]::VECTOR(FLOAT,3))"
                + " FROM (SELECT VECTOR_TRUNC(vec, 2) AS x FROM dt)",
            "Invalid argument types for function 'VECTOR_L2_DISTANCE': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 3))");
        assertAccepted("SELECT VECTOR_L2_DISTANCE(x, [1,2]::VECTOR(FLOAT,2))"
            + " FROM (SELECT VECTOR_TRUNC(vec, 2) AS x FROM dt)", 2);
    }

    // ── Case 6: set-operation branches ───────────────────────────────────────────────────────────

    /** Branches that AGREE keep the type they share — live reports OBJECT for an all-OBJECT union. */
    @Test
    public void agreeingSetOperationBranchesKeepTheirType() {
        assertFails("SELECT COUNT(*) FROM (SELECT f AS c FROM dt UNION ALL SELECT f FROM dt)"
            + " GROUP BY c", FILE_KEY);
        assertFails("SELECT MAX(c) FROM (SELECT o AS c FROM dt UNION ALL SELECT o FROM dt)",
            MAX_OBJECT);
        assertFails("SELECT MAX(c) FROM (SELECT a AS c FROM dt UNION ALL SELECT a FROM dt)",
            MAX_ARRAY);
        assertFails("WITH u AS (SELECT o AS c FROM dt UNION ALL SELECT o FROM dt) SELECT MAX(c) FROM u",
            MAX_OBJECT);
    }

    /**
     * Branches that DISAGREE unify to the non-string side — live declares {@code SELECT s ... UNION
     * ALL SELECT n ...} as {@code NUMBER(18,5)}, neither branch's own type — and the string branch's
     * VALUES must convert to it, so this exact shape fails at the non-numeric 'x' with the same
     * sentence live produces.
     */
    @Test
    public void disagreeingSetOperationBranchesCoerceTheStringSide() {
        assertFails("SELECT COUNT(*) FROM (SELECT s AS c FROM dt UNION ALL SELECT n FROM dt)"
            + " GROUP BY c", "Numeric value 'x' is not recognized");
    }

    // ── Case 7: a recursive CTE ──────────────────────────────────────────────────────────────────

    /** Live: the recursive CTE's column type is the ANCHOR branch's — an OBJECT anchor makes it
     *  OBJECT for the whole recursion, and a FILE anchor makes it a FILE. */
    @Test
    public void aRecursiveCteTakesItsAnchorBranchesType() {
        assertFails("WITH RECURSIVE r (i, x) AS (SELECT 1, o FROM dt WHERE id = 1"
            + " UNION ALL SELECT i + 1, x FROM r WHERE i < 3) SELECT MAX(x) FROM r", MAX_OBJECT);
        assertFails("WITH RECURSIVE r (i, x) AS (SELECT 1, f FROM dt WHERE id = 1"
            + " UNION ALL SELECT i + 1, x FROM r WHERE i < 3) SELECT COUNT(*) FROM r GROUP BY x",
            FILE_KEY);
    }

    // ── Case 8: LATERAL, correlated derived tables and joins ─────────────────────────────────────

    @Test
    public void aLateralDerivedTableCarriesItsColumnTypes() {
        assertFails("SELECT COUNT(*) FROM dt, LATERAL (SELECT t2.f AS g FROM dt t2"
            + " WHERE t2.id = dt.id) l GROUP BY l.g", FILE_KEY);
        assertFails("SELECT MAX(l.p) FROM dt, LATERAL (SELECT t2.o AS p FROM dt t2"
            + " WHERE t2.id = dt.id) l", MAX_OBJECT);
    }

    @Test
    public void aJoinedDerivedTableCarriesItsColumnTypes() {
        assertFails("SELECT MAX(j.o) FROM dt LEFT JOIN (SELECT id, o FROM dt) j ON j.id = dt.id",
            MAX_OBJECT);
        assertFails("SELECT COUNT(*) FROM dt LEFT JOIN (SELECT id, f FROM dt) j ON j.id = dt.id"
            + " GROUP BY j.f", FILE_KEY);
    }

    // ── Case 9: table functions stay undetermined ────────────────────────────────────────────────

    /**
     * A table function's columns are not given a static type: live reports VARIANT for
     * {@code FLATTEN}'s {@code VALUE} and VARCHAR for {@code SPLIT_TO_TABLE}'s, and neither is a type
     * this engine declares on those functions today. Leaving them undetermined keeps every one of these
     * accepted, exactly as live accepts them.
     */
    @Test
    public void tableFunctionColumnsStayUndeterminedAndAccepted() {
        assertAccepted("SELECT MAX(value) FROM"
            + " (SELECT value FROM TABLE(FLATTEN(input => PARSE_JSON('[1,2]'))))", 1);
        assertAccepted("SELECT COUNT(*) FROM"
            + " (SELECT value AS vv FROM TABLE(SPLIT_TO_TABLE('a,b', ','))) GROUP BY vv", 2);
        assertAccepted("SELECT MAX(fv) FROM"
            + " (SELECT fl.value AS fv FROM dt, LATERAL FLATTEN(input => dt.a) fl)", 1);
    }

    // ── A view resolves the same way ─────────────────────────────────────────────────────────────

    @Test
    public void aViewCarriesItsColumnTypes() {
        engine.execute("CREATE VIEW vdt AS SELECT id, f, o, a, v, s, n FROM dt");
        assertFails("SELECT COUNT(*) FROM vdt GROUP BY f", FILE_KEY);
        assertFails("SELECT MAX(o) FROM vdt", MAX_OBJECT);
        assertFails("SELECT COUNT(*) FROM (SELECT id, f FROM vdt) GROUP BY f", FILE_KEY);
    }

    // ── The bound: a KNOWN type must not become a WRONG one ──────────────────────────────────────

    /**
     * A VARIANT stays a VARIANT through the wrapper. Live accepts {@code MAX} over one even when it
     * HOLDS an object, and reports VARIANT for {@code PARSE_JSON}, {@code TO_VARIANT} and a colon path
     * — so none of them may be promoted to OBJECT by the inference.
     */
    @Test
    public void aVariantDerivedColumnIsStillAcceptedByTheOrderingAggregates() {
        assertAccepted("SELECT MAX(v) FROM (SELECT id, v FROM dt)", 1);
        assertAccepted("SELECT MAX(x) FROM (SELECT PARSE_JSON('{\"k\":1}') AS x FROM dt)", 1);
        assertAccepted("SELECT MAX(x) FROM (SELECT TO_VARIANT(o) AS x FROM dt)", 1);
        assertAccepted("SELECT MAX(x) FROM (SELECT o:k AS x FROM dt)", 1);
        assertAccepted("WITH c AS (SELECT id, v FROM dt) SELECT MAX(v) FROM c", 1);
    }

    /** The ordinary types keep working in the positions their type allows. */
    @Test
    public void ordinaryDerivedColumnsStillGroupSortAndAggregate() {
        assertAccepted("SELECT COUNT(*) FROM (SELECT id, s FROM dt) GROUP BY s", 2);
        assertAccepted("SELECT COUNT(*) FROM (SELECT id, n FROM dt) GROUP BY n", 2);
        assertAccepted("SELECT id, s FROM (SELECT id, s FROM dt) ORDER BY id", 2);
        assertAccepted("SELECT SUM(n) FROM (SELECT id, n FROM dt)", 1);
        assertAccepted("SELECT UPPER(s) FROM (SELECT s FROM dt) ORDER BY 1", 2);
        assertAccepted("SELECT GET(o, 'k') FROM (SELECT o FROM dt) ORDER BY 1", 2);
    }

    /** A FILE column is only refused in the positions that refuse it — it still counts and projects. */
    @Test
    public void aFileDerivedColumnIsStillUsableWhereFilesAreAllowed() {
        assertAccepted("SELECT COUNT(*) FROM (SELECT id, f FROM dt)", 1);
        assertAccepted("SELECT COUNT(f) FROM (SELECT id, f FROM dt)", 1);
    }

    /**
     * Making the type KNOWN must make it RIGHT, not merely non-null. A derived DATE really is a DATE,
     * so the temporal functions take it — where a VARCHAR of the same text is refused. A derived
     * BOOLEAN really is a BOOLEAN, so it may be a condition, and a derived NUMBER may not.
     */
    @Test
    public void aDerivedColumnsKnownTypeIsTheRightType() {
        assertAccepted("SELECT DAYOFMONTH(d) FROM (SELECT '2024-04-08'::DATE AS d FROM dt)", 2);
        assertFails("SELECT DAYOFMONTH(d) FROM (SELECT '2024-04-08'::VARCHAR AS d FROM dt)",
            "Function EXTRACT does not support VARCHAR");
        assertAccepted("SELECT IFF(b, 1, 2) FROM (SELECT s = 'x' AS b FROM dt)", 2);
        assertFails("SELECT IFF(c, 1, 2) FROM (SELECT n AS c FROM dt)",
            "Invalid argument types for function 'IFF'");
        assertAccepted("SELECT TYPEOF(x) FROM (SELECT TO_VARIANT(n) AS x FROM dt)", 2);
    }
}
