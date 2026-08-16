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

/**
 * {@code ALTER TABLE … SET} carries as MANY properties as you give it, separated by a space or a
 * comma, and each one lands — live-verified by reading them back from SHOW TABLES rather than by
 * the statement merely being accepted.
 *
 * <p>Three measured details shape this:
 * <ul>
 *   <li>a property REPEATED in one SET is accepted here, unlike CREATE TABLE where the same repeat
 *       is refused as a duplicate property;</li>
 *   <li>{@code ERROR_LOGGING} takes a BAREWORD — {@code = DEFAULT} runs and {@code = 'DEFAULT'} is
 *       refused with the bracketed invalid-value shape;</li>
 *   <li>{@code ENABLE_SCHEMA_EVOLUTION} is recorded, not merely tolerated: SHOW TABLES answers Y.</li>
 * </ul>
 */
public class AlterTablePropertySetTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT)");
    }

    /** One SHOW TABLES cell for a table in this test's schema. */
    private String tableCell(final String table, final String column) {
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        return cell(tables, soleRowWhere(tables, "name", table.toUpperCase()), column);
    }

    // ── several properties in one SET ─────────────────────────────────────────────────────────────

    @Test
    public void oneSetCarriesSeveralPropertiesSeparatedBySpaces() {
        engine.execute("ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = 1 COMMENT = 'multi'");
        assertEquals("1", tableCell("t", "retention_time"));
        assertEquals("multi", tableCell("t", "comment"));
    }

    @Test
    public void commasSeparateThemToo() {
        engine.execute("ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = 1, COMMENT = 'commas'");
        assertEquals("1", tableCell("t", "retention_time"));
        assertEquals("commas", tableCell("t", "comment"));
    }

    @Test
    public void threePropertiesAtOnce() {
        engine.execute("ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = 1 CHANGE_TRACKING = TRUE"
            + " COMMENT = 'three'");
        assertEquals("1", tableCell("t", "retention_time"));
        assertEquals("ON", tableCell("t", "change_tracking"));
        assertEquals("three", tableCell("t", "comment"));
    }

    /** A repeat is legal on ALTER — the CREATE-side duplicate refusal does not reach here. */
    @Test
    public void aRepeatedPropertyIsAcceptedOnAlter() {
        engine.execute("ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = 1 DATA_RETENTION_TIME_IN_DAYS = 0");
    }

    @Test
    public void theSinglePropertyFormsStillWork() {
        engine.execute("ALTER TABLE t SET CHANGE_TRACKING = TRUE");
        assertEquals("ON", tableCell("t", "change_tracking"));
        engine.execute("ALTER TABLE t SET COMMENT = 'single'");
        assertEquals("single", tableCell("t", "comment"));
    }

    @Test
    public void anUnknownPropertyIsStillRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE t SET NO_SUCH_PROP = 1");
            }
        });
        assertEquals("SQL compilation error:\ninvalid property 'NO_SUCH_PROP' for 'TABLE'",
            ex.getMessage());
    }

    // ── ENABLE_SCHEMA_EVOLUTION ───────────────────────────────────────────────────────────────────

    @Test
    public void schemaEvolutionIsRecordedNotJustAccepted() {
        assertEquals("N", tableCell("t", "enable_schema_evolution"));
        engine.execute("ALTER TABLE t SET ENABLE_SCHEMA_EVOLUTION = TRUE");
        assertEquals("Y", tableCell("t", "enable_schema_evolution"));
        engine.execute("ALTER TABLE t UNSET ENABLE_SCHEMA_EVOLUTION");
        assertEquals("N", tableCell("t", "enable_schema_evolution"));
    }

    @Test
    public void schemaEvolutionIsRecordedAtCreateToo() {
        engine.execute("CREATE TABLE se (n INT) ENABLE_SCHEMA_EVOLUTION = TRUE");
        assertEquals("Y", tableCell("se", "enable_schema_evolution"));
    }

    // ── ERROR_LOGGING takes a bareword ────────────────────────────────────────────────────────────

    @Test
    public void errorLoggingTakesABareword() {
        engine.execute("ALTER TABLE t SET ERROR_LOGGING = DEFAULT");
        assertEquals("OFF", tableCell("t", "error_logging"));
        engine.execute("ALTER TABLE t UNSET ERROR_LOGGING");
    }

    @Test
    public void aQuotedErrorLoggingValueIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE t SET ERROR_LOGGING = 'DEFAULT'");
            }
        });
        assertEquals("SQL compilation error:\ninvalid value ['DEFAULT'] for parameter 'ERROR_LOGGING'",
            ex.getMessage());
    }
}
