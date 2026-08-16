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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How SHOW COLUMNS spells a cell that has no value. Most unset text cells are the EMPTY STRING —
 * default, expression, comment and autoincrement — but NOT all of them: schema_evolution_record and
 * write_default are genuinely NULL, on every column shape and on a derived column too. That is why
 * this is a per-cell rule rather than a sweep over the row.
 *
 * <p>The autoincrement pair is the other surprise. An IDENTITY column's generator is spelled in the
 * AUTOINCREMENT cell, in full — {@code IDENTITY START 1 INCREMENT 1 NOORDER} — and its DEFAULT cell is
 * left empty. Frostlake had the two the other way round, with a bare {@code Y} for the flag.
 */
public class ShowColumnsUnsetCellsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE uc_t (
                plain NUMBER,
                withdefault NUMBER DEFAULT 7,
                commented NUMBER COMMENT 'a comment',
                autocol NUMBER AUTOINCREMENT)""");
        engine.execute("CREATE VIEW uc_v AS SELECT plain + 1 AS derived FROM uc_t");
    }

    private Row columnRow(final String relation, final String columnName) {
        final ResultSet rs = engine.executeQuery(
            "SHOW COLUMNS IN TABLE test_db.test_schema." + relation);
        final Row found = soleRowWhere(rs, "column_name", columnName);
        assertNotNull(found, columnName + " not listed");
        return found;
    }

    /** Every unset TEXT cell on an ordinary column is the empty string, never NULL. */
    @Test
    public void unsetTextCellsAreEmptyNotNull() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.uc_t");
        final Row plain = soleRowWhere(rs, "column_name", "PLAIN");
        assertEquals("", cell(rs, plain, "default"));
        assertEquals("", cell(rs, plain, "expression"));
        assertEquals("", cell(rs, plain, "comment"));
        assertEquals("", cell(rs, plain, "autoincrement"));
    }

    /** A cell that IS set still carries its value. */
    @Test
    public void setCellsKeepTheirValue() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.uc_t");
        assertEquals("7", cell(rs, soleRowWhere(rs, "column_name", "WITHDEFAULT"), "default"));
        assertEquals("a comment", cell(rs, soleRowWhere(rs, "column_name", "COMMENTED"), "comment"));
    }

    /**
     * An IDENTITY column spells its generator in the AUTOINCREMENT cell and leaves DEFAULT empty —
     * not the other way round, and not a bare flag.
     */
    @Test
    public void anIdentityColumnSpellsItsGeneratorInTheAutoincrementCell() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.uc_t");
        final Row auto = soleRowWhere(rs, "column_name", "AUTOCOL");
        assertEquals("IDENTITY START 1 INCREMENT 1 NOORDER", cell(rs, auto, "autoincrement"));
        assertEquals("", cell(rs, auto, "default"));
    }

    /**
     * The two cells that are NOT empty-string: they are genuinely NULL, which is exactly why this rule
     * had to be measured per cell instead of applied to the whole row.
     */
    @Test
    public void theTrailingPairIsGenuinelyNull() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.uc_t");
        final Row plain = soleRowWhere(rs, "column_name", "PLAIN");
        assertNull(cell(rs, plain, "schema_evolution_record"));
        assertNull(cell(rs, plain, "write_default"));
    }

    /** A DERIVED column follows the same rules, through its own row builder. */
    @Test
    public void aDerivedColumnFollowsTheSameRules() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.uc_v");
        final Row derived = soleRowWhere(rs, "column_name", "DERIVED");
        assertEquals("", cell(rs, derived, "default"));
        assertEquals("", cell(rs, derived, "expression"));
        assertEquals("", cell(rs, derived, "comment"));
        assertEquals("", cell(rs, derived, "autoincrement"));
        assertNull(cell(rs, derived, "schema_evolution_record"));
        assertNull(cell(rs, derived, "write_default"));
    }
}
