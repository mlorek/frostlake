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
 * The declared column type of a set operation combines its branches' types the way Snowflake does —
 * live-verified on a real account. Measured NUMBERs fold to the numeric supertype: the
 * widest INTEGER PART meets the widest SCALE, so {@code 208 ∪ 0.3} declares NUMBER(4,1) — not the
 * max-precision/max-scale (3,1), which could not even hold 208 — and the same rule holds for UNION,
 * UNION ALL and EXCEPT, and through a CTE or view.
 *
 * <p>This is a VALUE-correctness rule, not a metadata nicety: treating a (3,0) branch and a (4,1)
 * branch as "agreeing" used to keep the leading branch's scale-0 type, and a CTAS through a CTE then
 * stored 183 for the 182.5 the other branch produced. That exact shape — an expected-data fixture
 * built as {@code CREATE TABLE … AS WITH q (…) AS (SELECT … UNION ALL SELECT …) SELECT * FROM q} —
 * is how a downstream integration suite seeds its comparison tables, and the corruption surfaced
 * there as a silent EXCEPT mismatch rather than an error.
 */
public class SetOperationDeclaredTypeTest extends BaseDatabaseTest {

    private String value(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** The declared type of one column as {@code TYPE(precision,scale)}, read back from the catalog. */
    private String declared(final String table, final String column) {
        return value("SELECT data_type || '(' || numeric_precision || ',' || numeric_scale || ')'"
            + " FROM test_db.information_schema.columns WHERE table_name = '" + table.toUpperCase()
            + "' AND column_name = '" + column.toUpperCase() + "'");
    }

    /** The integration-suite fixture shape: a decimal in a later branch must survive the CTE CTAS. */
    @Test
    public void aLaterDecimalBranchKeepsItsValueThroughACteCtas() {
        engine.execute("""
            CREATE TABLE sla AS
              WITH q (a, b, c) AS (
                SELECT 1, 208,   0.3      UNION ALL
                SELECT 2, 30,    0.3      UNION ALL
                SELECT 3, 182.5, 0.333333
              )
              SELECT * FROM q WHERE TRUE""");
        assertEquals("182.5", value("SELECT b FROM sla WHERE a = 3"));
        assertEquals("0.333333", value("SELECT c FROM sla WHERE a = 3"));
        assertEquals("NUMBER(1,0)", declared("sla", "a"));
        assertEquals("NUMBER(4,1)", declared("sla", "b"));
        assertEquals("NUMBER(7,6)", declared("sla", "c"));
    }

    /** 208 is three integer digits, 0.3 is one decimal — the supertype needs all four positions. */
    @Test
    public void theSupertypeIsWidestIntegerPartPlusWidestScale() {
        engine.execute("CREATE TABLE ps AS WITH q (x) AS (SELECT 208 UNION ALL SELECT 0.3)"
            + " SELECT * FROM q");
        assertEquals("NUMBER(4,1)", declared("ps", "x"));
        assertEquals("208.0", value("SELECT x FROM ps WHERE x > 1"));
    }

    @Test
    public void twoFractionsFoldToTheWiderScale() {
        engine.execute("CREATE TABLE sc AS WITH q (x) AS (SELECT 0.3 UNION ALL SELECT 0.33333)"
            + " SELECT * FROM q");
        assertEquals("NUMBER(6,5)", declared("sc", "x"));
    }

    @Test
    public void aNegativeBranchCountsItsDigitsNotItsSign() {
        engine.execute("CREATE TABLE ng AS WITH q (x) AS (SELECT -208 UNION ALL SELECT 1.25)"
            + " SELECT * FROM q");
        assertEquals("NUMBER(5,2)", declared("ng", "x"));
        assertEquals("-208.00", value("SELECT x FROM ng WHERE x < 0"));
    }

    /** EXCEPT combines branch types under the same rule as UNION (live-verified). */
    @Test
    public void exceptCombinesBranchTypesLikeUnion() {
        engine.execute("CREATE TABLE ex AS WITH q (x) AS (SELECT 208 EXCEPT SELECT 0.3)"
            + " SELECT * FROM q");
        assertEquals("NUMBER(4,1)", declared("ex", "x"));
    }

    /** An undetermined branch (here a NULL literal) leaves the column to the value scan — the
     *  decimal value must survive regardless of what the catalog ends up declaring. */
    @Test
    public void anUndeterminedBranchStillKeepsEveryValue() {
        engine.execute("CREATE TABLE un AS WITH q (x) AS"
            + " (SELECT 182.5 UNION ALL SELECT NULL) SELECT * FROM q");
        assertEquals("182.5", value("SELECT x FROM un WHERE x IS NOT NULL"));
    }

    /** The pre-existing semi-structured agreement is unchanged: an all-OBJECT union stays OBJECT. */
    @Test
    public void agreeingSemiStructuredBranchesKeepTheirType() {
        engine.execute("CREATE TABLE ob AS WITH q (x) AS ("
            + "SELECT OBJECT_CONSTRUCT('k', 1) UNION ALL SELECT OBJECT_CONSTRUCT('k', 2))"
            + " SELECT * FROM q");
        assertEquals("OBJECT", value("SELECT data_type FROM test_db.information_schema.columns"
            + " WHERE table_name = 'OB' AND column_name = 'X'"));
    }
}
