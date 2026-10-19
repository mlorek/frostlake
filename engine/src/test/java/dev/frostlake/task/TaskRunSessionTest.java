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
package dev.frostlake.task;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.TaskRunWait;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The session a task's run is (live-verified): it starts from the account's defaults with the task's own session
 * parameters set, whatever the session that started it has set; inside it CURRENT_USER() is the system's, or the
 * user the task is declared to execute as; and the starting session's settings are its own again afterwards.
 */
public class TaskRunSessionTest extends BaseDatabaseTest {

    private List<String> logged() {
        final ResultSet rows = engine.executeQuery("SELECT v FROM test_db.test_schema.trs_log");
        final List<String> values = new ArrayList<>();
        for (final Row row : rows.getRows()) {
            values.add(String.valueOf(row.getValue(0)));
        }
        Collections.sort(values);
        return values;
    }

    @Test
    public void aRunTakesItsTasksParametersAndNotTheStartingSessions() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String plain = "TRS_PLAIN_" + suffix;
        final String own = "TRS_OWN_" + suffix;
        engine.execute("CREATE TABLE trs_log (v VARCHAR)");
        engine.execute("CREATE TASK " + plain + " AS INSERT INTO test_db.test_schema.trs_log SELECT 'plain|' ||"
            + " '2020-03-04'::DATE::VARCHAR || '|' || CURRENT_USER()");
        engine.execute("CREATE TASK " + own + " DATE_OUTPUT_FORMAT = 'DD/MM/YYYY', TIMEZONE = 'Asia/Tokyo' AS INSERT"
            + " INTO test_db.test_schema.trs_log SELECT 'own|' || '2020-03-04'::DATE::VARCHAR || '|' ||"
            + " TO_CHAR(CURRENT_TIMESTAMP(), 'TZH')");
        engine.execute("ALTER SESSION SET DATE_OUTPUT_FORMAT = 'YYYY.MM.DD'");
        try {
            engine.execute("EXECUTE TASK " + plain);
            engine.execute("EXECUTE TASK " + own);
            TaskRunWait.untilRows(engine, "SELECT v FROM test_db.test_schema.trs_log", 2);
            final List<String> told = logged();
            assertEquals("own|04/03/2020|+09", told.get(0), "the task's own format and zone");
            assertEquals("plain|2020-03-04|SYSTEM", told.get(1), "the default format, and the system's user");
            assertEquals("2020.03.04", String.valueOf(engine.executeQuery("SELECT '2020-03-04'::DATE::VARCHAR")
                .getRows().get(0).getValue(0)), "the starting session keeps its own setting");
        } finally {
            engine.execute("ALTER SESSION UNSET DATE_OUTPUT_FORMAT");
            engine.execute("DROP TASK IF EXISTS " + plain);
            engine.execute("DROP TASK IF EXISTS " + own);
        }
    }

    @Test
    public void aRunIsTheUsersTheTaskExecutesAs() {
        final String task = "TRS_USER_" + Long.toString(System.nanoTime() % 100000000L);
        final String me = String.valueOf(engine.executeQuery("SELECT CURRENT_USER()").getRows().get(0).getValue(0));
        engine.execute("CREATE TABLE trs_log (v VARCHAR)");
        engine.execute("CREATE TASK " + task + " EXECUTE AS USER \"" + me + "\" AS INSERT INTO"
            + " test_db.test_schema.trs_log SELECT IFF(CURRENT_USER() = '" + me + "', 'the user', CURRENT_USER())");
        try {
            engine.execute("EXECUTE TASK " + task);
            TaskRunWait.untilRows(engine, "SELECT v FROM test_db.test_schema.trs_log", 1);
            assertEquals("the user", logged().get(0));
        } finally {
            engine.execute("DROP TASK IF EXISTS " + task);
        }
    }
}
