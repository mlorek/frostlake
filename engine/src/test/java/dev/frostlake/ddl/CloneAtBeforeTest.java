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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLONE … AT | BEFORE copies the data as of the point, and refuses the points a SELECT refuses. The grammar accepted
 * the clause and the clone ignored it, so a "restore from before the bad UPDATE" clone silently copied the damaged
 * data, and a point years in the future cloned the present. A schema or database cloned as of a point leaves out the
 * children that did not exist yet then (documented).
 */
public class CloneAtBeforeTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws InterruptedException {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE tt_db");
        engine.execute("CREATE SCHEMA tt_db.s1");
        engine.execute("USE SCHEMA tt_db.s1");
        engine.execute("CREATE TABLE x (a INT)");
        engine.execute("INSERT INTO x VALUES (0)");
        Thread.sleep(25);
        engine.execute("SET t = CURRENT_TIMESTAMP()");
        Thread.sleep(25);
        engine.execute("INSERT INTO x VALUES (1)");
        engine.execute("CREATE TABLE later (a INT)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String tables(final String in) {
        final ResultSet listed = engine.executeQuery("SHOW TABLES IN " + in);
        final StringBuilder out = new StringBuilder();
        while (listed.next()) {
            out.append(out.length() > 0 ? "," : "").append(listed.getValue("name"));
        }
        return out.toString();
    }

    @Test
    public void aTableCloneCopiesTheDataAsOfThePoint() {
        engine.execute("CREATE TABLE xa CLONE x AT(TIMESTAMP => $t)");
        engine.execute("CREATE TABLE xb CLONE x BEFORE(TIMESTAMP => $t)");
        engine.execute("CREATE TABLE xn CLONE x");
        assertEquals(1L, count("xa"));
        assertEquals(1L, count("xb"));
        assertEquals(2L, count("xn"));
        assertEquals("Future data is not yet available for table X.",
            refusal("CREATE TABLE xf CLONE x AT(TIMESTAMP => DATEADD(year, 5, CURRENT_TIMESTAMP()))"));
        assertTrue(refusal("CREATE TABLE xch CLONE x CHANGES(INFORMATION => DEFAULT)")
            .contains("unexpected 'CHANGES'"));
    }

    @Test
    public void aSchemaOrDatabaseCloneLeavesOutWhatCameLater() {
        engine.execute("CREATE SCHEMA tt_db.s2 CLONE tt_db.s1 AT(TIMESTAMP => $t)");
        assertEquals(1L, count("tt_db.s2.x"));
        assertEquals("X", tables("SCHEMA tt_db.s2"), "LATER did not exist yet");
        engine.execute("CREATE SCHEMA tt_db.newer");
        engine.execute("CREATE DATABASE tt_copy CLONE tt_db AT(TIMESTAMP => $t)");
        assertEquals(1L, count("tt_copy.s1.x"));
        assertEquals("X", tables("SCHEMA tt_copy.s1"));
        assertTrue(refusal("SELECT 1 FROM tt_copy.newer.x").contains("does not exist"), "nor did schema NEWER");
    }

    @Test
    public void aSourceThatDidNotExistYetIsRefusedBeforeAnythingChanges() {
        engine.execute("CREATE SCHEMA tt_db.young");
        engine.execute("CREATE TABLE tt_db.young.y (a INT)");
        engine.execute("CREATE SCHEMA tt_db.keep");
        engine.execute("CREATE TABLE tt_db.keep.k (a INT)");
        assertEquals("Time travel data is not available for schema YOUNG. The requested time is either beyond the"
                + " allowed time travel period or before the object creation time.",
            refusal("CREATE OR REPLACE SCHEMA tt_db.keep CLONE tt_db.young AT(TIMESTAMP => $t)"));
        assertEquals(0L, count("tt_db.keep.k"), "the schema it would have replaced is still there");
    }
}
