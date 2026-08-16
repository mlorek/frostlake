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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHANGE_TRACKING, live-measured: a fresh table is OFF; the CREATE TABLE option, ALTER … SET, or
 * the creation of a stream turns it ON (and it survives the stream); the CHANGES clause requires
 * it — an untracked table refuses with the single-line wording — and a CHANGES point is bounded
 * by the table's creation (the retention wording) and must be a CONSTANT, unlike a plain
 * time-travel AT which accepts any expression.
 */
public class ChangeTrackingTest extends BaseDatabaseTest {

    private String tracking(final String table) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        assertEquals(1, rs.getRowCount(), table);
        return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("change_tracking")));
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    @Test
    public void aFreshTableIsOff() {
        engine.execute("CREATE TABLE ct_fresh (id INTEGER)");
        assertEquals("OFF", tracking("ct_fresh"));
    }

    @Test
    public void creatingAStreamTurnsItOnAndItSurvivesTheStream() {
        engine.execute("CREATE TABLE ct_streamed (id INTEGER)");
        assertEquals("OFF", tracking("ct_streamed"));
        engine.execute("CREATE STREAM ct_s ON TABLE ct_streamed");
        assertEquals("ON", tracking("ct_streamed"));
        engine.execute("DROP STREAM ct_s");
        assertEquals("ON", tracking("ct_streamed"));
    }

    @Test
    public void alterSetTogglesIt() {
        engine.execute("CREATE TABLE ct_alter (id INTEGER)");
        engine.execute("ALTER TABLE ct_alter SET CHANGE_TRACKING = TRUE");
        assertEquals("ON", tracking("ct_alter"));
        engine.execute("ALTER TABLE ct_alter SET CHANGE_TRACKING = FALSE");
        assertEquals("OFF", tracking("ct_alter"));
    }

    @Test
    public void theCreateOptionTurnsItOn() {
        engine.execute("CREATE TABLE ct_opt (id INTEGER) CHANGE_TRACKING = TRUE");
        assertEquals("ON", tracking("ct_opt"));
    }

    @Test
    public void changesOverAnUntrackedTableRefuses() {
        engine.execute("CREATE TABLE ct_plain (id INTEGER)");
        assertEquals("SQL compilation error: Change tracking is not enabled or has been missing"
                + " for the time range requested on table 'TEST_DB.TEST_SCHEMA.CT_PLAIN'.",
            refusal("SELECT * FROM ct_plain CHANGES (INFORMATION => DEFAULT) AT (OFFSET => 0)")
                .getMessage());
    }

    @Test
    public void changesAfterDisablingRefusesTheSameWay() {
        engine.execute("CREATE TABLE ct_toggled (id INTEGER)");
        engine.execute("ALTER TABLE ct_toggled SET CHANGE_TRACKING = TRUE");
        engine.execute("ALTER TABLE ct_toggled SET CHANGE_TRACKING = FALSE");
        assertTrue(refusal(
            "SELECT * FROM ct_toggled CHANGES (INFORMATION => DEFAULT) AT (OFFSET => 0)")
            .getMessage().contains("Change tracking is not enabled"));
    }

    @Test
    public void changesOverATrackedTableAnswersAnEmptyWindow() {
        engine.execute("CREATE TABLE ct_ok (id INTEGER)");
        engine.execute("ALTER TABLE ct_ok SET CHANGE_TRACKING = TRUE");
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM ct_ok CHANGES (INFORMATION => DEFAULT) AT (OFFSET => 0)").getRowCount());
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM ct_ok CHANGES (INFORMATION => APPEND_ONLY) AT (OFFSET => 0)").getRowCount());
    }

    @Test
    public void aChangesPointBeforeCreationRefusesWithTheRetentionWording() {
        engine.execute("CREATE TABLE ct_bound (id INTEGER) CHANGE_TRACKING = TRUE");
        assertEquals("Time travel data is not available for table CT_BOUND. The requested time is"
                + " either beyond the allowed time travel period or before the object creation time.",
            refusal("SELECT * FROM ct_bound CHANGES (INFORMATION => DEFAULT) AT (OFFSET => -86400)")
                .getMessage());
    }

    @Test
    public void aChangesPointMustBeConstant() {
        engine.execute("CREATE TABLE ct_const (id INTEGER) CHANGE_TRACKING = TRUE");
        assertTrue(refusal("SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT)"
            + " AT (TIMESTAMP => DATEADD(minute, -30, CURRENT_TIMESTAMP()))")
            .getMessage().contains("argument TIMESTAMP to function AT needs to be constant"),
            "expected the constant-argument refusal");
    }
}
