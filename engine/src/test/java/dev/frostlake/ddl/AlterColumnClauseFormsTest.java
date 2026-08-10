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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The optional keywords of ALTER TABLE's column clauses, each live-verified:
 *
 * <ul>
 *   <li>{@code DROP} takes its COLUMN keyword or leaves it out — in the single, comma-list and
 *       IF EXISTS forms alike;</li>
 *   <li>{@code NOT NULL} needs no SET, in the ALTER, MODIFY and COLUMN-less spellings;</li>
 *   <li>a MASKING POLICY attaches in the column DEFINITION, with WITH optional, on CREATE TABLE and
 *       on ALTER TABLE ADD COLUMN;</li>
 *   <li>{@code FORCE} replaces an attached policy where the plain form refuses.</li>
 * </ul>
 *
 * <p>The masking attachment is asserted BEHAVIOURALLY — a second policy without FORCE is refused —
 * because DESCRIBE TABLE's {@code policy name} cell is still hardcoded null in the engine even
 * though live populates it; that read surface is its own defect, and these tests move to reading the
 * cell once it lands.
 */
public class AlterColumnClauseFormsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE MASKING POLICY mp1 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE MASKING POLICY mp2 AS (v VARCHAR) RETURNS VARCHAR -> '###'");
    }

    /** The columns a table still has, by name, read through DESCRIBE. */
    private int columnCount(final String table, final String column) {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE " + table);
        return rowsWhere(described, "name", column).size();
    }

    // ── DROP takes COLUMN or leaves it out ────────────────────────────────────────────────────────

    @Test
    public void dropTakesTheColumnKeywordOrNot() {
        engine.execute("CREATE TABLE d1 (keep INT, gone INT, also_gone INT)");
        engine.execute("ALTER TABLE d1 DROP gone");
        assertEquals(0, columnCount("d1", "GONE"));
        engine.execute("ALTER TABLE d1 DROP COLUMN also_gone");
        assertEquals(0, columnCount("d1", "ALSO_GONE"));
        assertEquals(1, columnCount("d1", "KEEP"));
    }

    @Test
    public void dropWithoutTheKeywordTakesAList() {
        engine.execute("CREATE TABLE d2 (keep INT, a INT, b INT)");
        engine.execute("ALTER TABLE d2 DROP a, b");
        assertEquals(0, columnCount("d2", "A"));
        assertEquals(0, columnCount("d2", "B"));
        assertEquals(1, columnCount("d2", "KEEP"));
    }

    @Test
    public void dropIfExistsWorksWithoutTheKeyword() {
        engine.execute("CREATE TABLE d3 (keep INT)");
        engine.execute("ALTER TABLE d3 DROP IF EXISTS never_existed");
        assertEquals(1, columnCount("d3", "KEEP"));
    }

    /** The specific DROP forms keep their meaning — CLUSTERING is a legal column name too. */
    @Test
    public void theOtherDropFormsStillParse() {
        engine.execute("CREATE TABLE d4 (n INT PRIMARY KEY) CLUSTER BY (n)");
        engine.execute("ALTER TABLE d4 DROP CLUSTERING KEY");
        engine.execute("ALTER TABLE d4 DROP PRIMARY KEY");
        assertEquals(1, columnCount("d4", "N"));
    }

    // ── NOT NULL without SET ──────────────────────────────────────────────────────────────────────

    @Test
    public void notNullNeedsNoSet() {
        engine.execute("CREATE TABLE n1 (a INT, b INT, c INT, d INT)");
        engine.execute("ALTER TABLE n1 ALTER COLUMN a NOT NULL");
        engine.execute("ALTER TABLE n1 MODIFY COLUMN b NOT NULL");
        engine.execute("ALTER TABLE n1 ALTER c NOT NULL");
        engine.execute("ALTER TABLE n1 ALTER COLUMN d SET NOT NULL");
        for (final String column : new String[] {"A", "B", "C", "D"}) {
            assertEquals("N", describeCell("n1", column, "null?"), column);
        }
    }

    @Test
    public void dropNotNullStillWorks() {
        engine.execute("CREATE TABLE n2 (a INT NOT NULL)");
        engine.execute("ALTER TABLE n2 ALTER COLUMN a DROP NOT NULL");
        assertEquals("Y", describeCell("n2", "A", "null?"));
    }

    // ── a masking policy in the column definition ─────────────────────────────────────────────────

    /** Attached means attached: a DIFFERENT policy on the same column is refused without FORCE. */
    private void assertPolicyAttached(final String table, final String column) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE " + table + " ALTER COLUMN " + column
                    + " SET MASKING POLICY mp2");
            }
        }, table + "." + column);
        assertTrue(String.valueOf(ex.getMessage()).contains("already attached"),
            "expected the already-attached refusal, got: " + ex.getMessage());
    }

    @Test
    public void aColumnDefinitionMayCarryAMaskingPolicy() {
        engine.execute("CREATE TABLE m1 (s VARCHAR WITH MASKING POLICY mp1)");
        assertPolicyAttached("m1", "s");
    }

    @Test
    public void theWithKeywordIsOptionalOnTheDefinition() {
        engine.execute("CREATE TABLE m2 (s VARCHAR MASKING POLICY mp1)");
        assertPolicyAttached("m2", "s");
    }

    @Test
    public void addColumnMayCarryAMaskingPolicy() {
        engine.execute("CREATE TABLE m3 (n INT)");
        engine.execute("ALTER TABLE m3 ADD COLUMN s VARCHAR WITH MASKING POLICY mp1");
        assertPolicyAttached("m3", "s");
    }

    @Test
    public void addColumnTakesItWithoutWithToo() {
        engine.execute("CREATE TABLE m4 (n INT)");
        engine.execute("ALTER TABLE m4 ADD COLUMN s VARCHAR MASKING POLICY mp1");
        assertPolicyAttached("m4", "s");
    }

    // ── FORCE ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void forceReplacesAnAttachedPolicy() {
        engine.execute("CREATE TABLE f1 (s VARCHAR)");
        engine.execute("ALTER TABLE f1 ALTER COLUMN s SET MASKING POLICY mp1");
        engine.execute("ALTER TABLE f1 ALTER COLUMN s SET MASKING POLICY mp2 FORCE");
        // mp2 is on it now, so mp1 without FORCE is the one that is refused.
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE f1 ALTER COLUMN s SET MASKING POLICY mp1");
            }
        });
        assertTrue(String.valueOf(ex.getMessage()).contains("already attached"), ex.getMessage());
    }

    @Test
    public void withoutForceASecondPolicyIsRefused() {
        engine.execute("CREATE TABLE f2 (s VARCHAR)");
        engine.execute("ALTER TABLE f2 ALTER COLUMN s SET MASKING POLICY mp1");
        assertPolicyAttached("f2", "s");
    }

    /** Re-attaching the SAME policy is a no-op rather than an error, with or without FORCE. */
    @Test
    public void reattachingTheSamePolicyIsAccepted() {
        engine.execute("CREATE TABLE f3 (s VARCHAR)");
        engine.execute("ALTER TABLE f3 ALTER COLUMN s SET MASKING POLICY mp1");
        engine.execute("ALTER TABLE f3 ALTER COLUMN s SET MASKING POLICY mp1");
        engine.execute("ALTER TABLE f3 ALTER COLUMN s SET MASKING POLICY mp1 FORCE");
    }
}
