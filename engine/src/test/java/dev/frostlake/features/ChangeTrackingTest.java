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
    public void aChangesPointReadsNoFunctionWhoseValueMoves() {
        engine.execute("CREATE TABLE ct_const (id INTEGER) CHANGE_TRACKING = TRUE");
        final String head = "SELECT * FROM ct_const CHANGES (INFORMATION => DEFAULT) ";
        final String moving = "', but change tracking is not supported on queries with non-deterministic functions in"
            + " versions. (inside a view).";
        assertEquals("SQL compilation error: line 1 at position 94:\nQuery contains time travel to a version containing"
            + " function 'CURRENT_TIMESTAMP" + moving,
            refusal(head + "AT (TIMESTAMP => DATEADD(minute, -30, CURRENT_TIMESTAMP()))").getMessage());
        assertEquals("SQL compilation error: line 1 at position 73:\nQuery contains time travel to a version containing"
            + " function 'CURRENT_DATE" + moving,
            refusal(head + "AT (TIMESTAMP => CURRENT_DATE()::TIMESTAMP_LTZ)").getMessage());
        assertEquals("SQL compilation error: line 1 at position 75:\nQuery contains time travel to a version containing"
            + " function 'RANDOM" + moving, refusal(head + "AT (OFFSET => -ABS(RANDOM()) % 2)").getMessage());
        assertEquals("SQL compilation error: line 1 at position 111:\nQuery contains time travel to a version"
            + " containing function 'CURRENT_TIMESTAMP" + moving, refusal(head
            + "AT (OFFSET => 0) END (TIMESTAMP => DATEADD(minute, 30, CURRENT_TIMESTAMP()))").getMessage(),
            "the END point is judged as the start is");
        assertEquals("SQL compilation error: line 1 at position 56:\nQuery contains time travel to a version containing"
            + " function 'RANDOM', but change tracking is not supported on queries with non-deterministic functions in"
            + " versions.", refusal("CREATE STREAM ct_s ON TABLE ct_const AT (OFFSET => -ABS(RANDOM()) % 2)")
            .getMessage(), "a stream's point likewise, its sentence ending there");
    }

    @Test
    public void aChangesPointMayBeComputedFromWhatDoesNotMove() {
        engine.execute("CREATE TABLE ct_fold (id INTEGER) CHANGE_TRACKING = TRUE");
        final String head = "SELECT * FROM ct_fold CHANGES (INFORMATION => DEFAULT) ";
        assertEquals(0, engine.executeQuery(head + "AT (OFFSET => -60 + 60)").getRowCount());
        assertEquals(0, engine.executeQuery(head + "AT (OFFSET => (SELECT 0))").getRowCount());
        final String unavailable = "Time travel data is not available for table CT_FOLD. The requested time is either"
            + " beyond the allowed time travel period or before the object creation time.";
        assertEquals(unavailable, refusal(head + "AT (TIMESTAMP => TO_TIMESTAMP_LTZ('2020-01-01 00:00:00'))")
            .getMessage());
        assertEquals(unavailable, refusal(head + "AT (TIMESTAMP => '2020-01-01 00:00:00')").getMessage());
        assertEquals(unavailable, refusal(head + "AT (TIMESTAMP => DATEADD(day, 1, '2020-01-01 00:00:00'"
            + "::TIMESTAMP_LTZ))").getMessage());
        assertEquals(unavailable, refusal(head + "AT (OFFSET => -1 * 60 * 60 * 24)").getMessage());
        assertTrue(refusal(head + "AT (OFFSET => '0')").getMessage().contains(
            "Invalid data type ['0'] in AT(OFFSET => '0')"));
        assertEquals("Statement xy not found", refusal(head + "AT (STATEMENT => 'x' || 'y')").getMessage());
    }
}
