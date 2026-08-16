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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How far a source column's NOT NULL travels into a view's reported columns. The rule is SYNTACTIC:
 * a projected COLUMN REFERENCE carries it, and nothing else does — not a cast of that same column,
 * not COALESCE with a non-null default, not COUNT(*), none of which can actually produce a NULL.
 *
 * <p>Every nesting level is transparent (a derived table, a CTE, a view over a view), and so are the
 * clauses that do not touch the column list (WHERE, GROUP BY, ORDER BY, DISTINCT, LIMIT). What CLEARS
 * it is an outer join null-extending a side, and any set operation — INTERSECT and UNION ALL included,
 * even when every branch is NOT NULL.
 *
 * <p>All live-measured. KNOWN DIVERGENCE, deliberately not asserted here: live carries the NOT NULL
 * through a PARENTHESIZED reference {@code SELECT (v)} as well; Frostlake resolves the column type
 * only for the unparenthesized form, so it reports that one nullable.
 */
public class ViewColumnNullabilityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nv_l (k NUMBER NOT NULL, v VARCHAR(4) NOT NULL, w VARCHAR(4))");
        engine.execute("CREATE TABLE nv_r (k NUMBER NOT NULL, r VARCHAR(4) NOT NULL)");
        engine.execute("CREATE VIEW nv_inner AS SELECT v FROM nv_l");
    }

    /** DESCRIBE's null? cell for one column of a view over {@code definition}. */
    private String nullabilityOf(final String definition, final String column) {
        engine.execute("CREATE OR REPLACE VIEW nv_v AS " + definition);
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW nv_v");
        return cell(rs, soleRowWhere(rs, "name", column), "null?");
    }

    private void assertNotNullable(final String definition, final String column) {
        assertEquals("N", nullabilityOf(definition, column), definition);
    }

    private void assertNullable(final String definition, final String column) {
        assertEquals("Y", nullabilityOf(definition, column), definition);
    }

    /** A column reference carries the NOT NULL; a nullable source stays nullable beside it. */
    @Test
    public void aColumnReferenceCarriesTheNotNull() {
        assertNotNullable("SELECT v FROM nv_l", "V");
        assertNullable("SELECT w FROM nv_l", "W");
        assertNotNullable("SELECT v AS renamed FROM nv_l", "RENAMED");
        assertNotNullable("SELECT l.v AS qualified FROM nv_l l", "QUALIFIED");
    }

    /** SELECT * carries each column's own answer. */
    @Test
    public void starCarriesEachColumnsOwnAnswer() {
        assertNotNullable("SELECT * FROM nv_l", "K");
        assertNotNullable("SELECT * FROM nv_l", "V");
        assertNullable("SELECT * FROM nv_l", "W");
    }

    /** The clauses that do not touch the column list are transparent. */
    @Test
    public void rowShapingClausesAreTransparent() {
        assertNotNullable("SELECT v FROM nv_l WHERE k > 0", "V");
        assertNotNullable("SELECT DISTINCT v FROM nv_l", "V");
        assertNotNullable("SELECT v FROM nv_l GROUP BY v", "V");
        assertNotNullable("SELECT v FROM nv_l ORDER BY v", "V");
        assertNotNullable("SELECT v FROM nv_l LIMIT 1", "V");
    }

    /** And so is every nesting level. */
    @Test
    public void nestingIsTransparent() {
        assertNotNullable("SELECT v FROM (SELECT v FROM nv_l)", "V");
        assertNotNullable("WITH c AS (SELECT v FROM nv_l) SELECT v FROM c", "V");
        assertNotNullable("SELECT v FROM nv_inner", "V");
    }

    /** An expression over the same column accepts NULL — even one that cannot produce one. */
    @Test
    public void anExpressionOverTheColumnAcceptsNull() {
        assertNullable("SELECT COALESCE(v, 'x') AS cx FROM nv_l", "CX");
        assertNullable("SELECT v::VARCHAR(4) AS cv FROM nv_l", "CV");
        assertNullable("SELECT COUNT(*) AS n FROM nv_l", "N");
        assertNullable("SELECT MAX(v) AS mv FROM nv_l", "MV");
        assertNullable("SELECT 1 AS one FROM nv_l", "ONE");
    }

    /** An inner join keeps both sides; an outer join clears the side it null-extends. */
    @Test
    public void anOuterJoinClearsTheNullExtendedSide() {
        final String inner = "SELECT l.v AS lv, r.r AS rr FROM nv_l l JOIN nv_r r ON l.k = r.k";
        assertNotNullable(inner, "LV");
        assertNotNullable(inner, "RR");

        final String left = "SELECT l.v AS lv, r.r AS rr FROM nv_l l LEFT JOIN nv_r r ON l.k = r.k";
        assertNotNullable(left, "LV");
        assertNullable(left, "RR");

        final String right = "SELECT l.v AS lv, r.r AS rr FROM nv_l l RIGHT JOIN nv_r r ON l.k = r.k";
        assertNullable(right, "LV");
        assertNotNullable(right, "RR");

        final String full = "SELECT l.v AS lv, r.r AS rr FROM nv_l l FULL OUTER JOIN nv_r r ON l.k = r.k";
        assertNullable(full, "LV");
        assertNullable(full, "RR");
    }

    /** A set operation clears it even when EVERY branch is NOT NULL. */
    @Test
    public void aSetOperationAlwaysClearsIt() {
        assertNullable("SELECT v AS u FROM nv_l UNION ALL SELECT v FROM nv_l", "U");
        assertNullable("SELECT v AS u FROM nv_l UNION ALL SELECT w FROM nv_l", "U");
        assertNullable("SELECT v FROM nv_l INTERSECT SELECT v FROM nv_l", "V");
    }

    /** SHOW COLUMNS says the same thing inside its descriptor, and in its own null? spelling. */
    @Test
    public void showColumnsAgreesInBothOfItsCells() {
        engine.execute("CREATE OR REPLACE VIEW nv_v AS SELECT v, w FROM nv_l");
        final ResultSet rs = engine.executeQuery(
            "SHOW COLUMNS IN VIEW test_db.test_schema.nv_v");
        assertEquals("""
            {"type":"TEXT","length":4,"byteLength":16,"nullable":false,"fixed":false}""",
            cell(rs, soleRowWhere(rs, "column_name", "V"), "data_type"));
        assertEquals("NOT_NULL", cell(rs, soleRowWhere(rs, "column_name", "V"), "null?"));
        assertEquals("""
            {"type":"TEXT","length":4,"byteLength":16,"nullable":true,"fixed":false}""",
            cell(rs, soleRowWhere(rs, "column_name", "W"), "data_type"));
        assertEquals("true", cell(rs, soleRowWhere(rs, "column_name", "W"), "null?"));
    }
}
