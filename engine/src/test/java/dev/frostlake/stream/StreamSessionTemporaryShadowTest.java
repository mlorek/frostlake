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

package dev.frostlake.stream;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A session's own temporary table that takes the name of a stream's table neither drops nor replaces that table: the
 * session reads the stream's changes, consumes them and asks SYSTEM$STREAM_HAS_DATA of the stream as any other session
 * does. A view stream read in that session is refused while the temporary table tracks no changes, as the view reads
 * the temporary table there, and lists the temporary table's changes since its offset once it tracks them
 * (live-verified); a table stream is read as it is anywhere, where the account fails the read with an internal error.
 */
public class StreamSessionTemporaryShadowTest {

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext session;

    @BeforeEach
    public void openSession() {
        concurrent = new ConcurrentDatabaseEngine();
        session = concurrent.createSession();
        say("CREATE DATABASE sts");
        say("USE SCHEMA sts.public");
    }

    @AfterEach
    public void closeSession() {
        concurrent.shutdown();
    }

    /** Runs a statement in the session: its rows as {@code a|b ;; c|d}, or {@code REFUSED} and the refusal. */
    private String say(final String sql) {
        try {
            final ResultSet rows = concurrent.executeQuery(sql, session);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < rows.getColumnCount(); i++) {
                    out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void aTableStreamIsReadBesideTheSessionsTemporaryTable() {
        say("CREATE TABLE t (id INT, v STRING)");
        say("CREATE STREAM st ON TABLE t");
        say("INSERT INTO t VALUES (2, 'perm2')");
        say("CREATE TEMPORARY TABLE t (id INT, v STRING)");
        say("INSERT INTO t VALUES (3, 'temp3')");
        assertEquals("2|INSERT", say("SELECT id, METADATA$ACTION FROM st"));
        assertEquals("true", say("SELECT SYSTEM$STREAM_HAS_DATA('st')"));
        say("CREATE TABLE u (id INT)");
        assertEquals("1", say("INSERT INTO u SELECT id FROM st"));
        assertEquals("0", say("SELECT COUNT(*) FROM st"));
        say("DROP TABLE t");
        say("INSERT INTO t VALUES (4, 'perm4')");
        assertEquals("4|INSERT", say("SELECT id, METADATA$ACTION FROM st"));
        assertEquals("2", say("SELECT id FROM u"));
    }

    @Test
    public void aViewStreamReadThroughTheSessionsUntrackedTemporaryTableIsRefused() {
        say("CREATE TABLE tv (id INT, v STRING)");
        say("CREATE VIEW vtv AS SELECT id, v FROM tv");
        say("CREATE STREAM svt ON VIEW vtv");
        say("INSERT INTO tv VALUES (2, 'perm2')");
        say("CREATE TEMPORARY TABLE tv (id INT, v STRING)");
        assertEquals("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time"
            + " range requested on table 'STS.PUBLIC.TV'.", say("SELECT id, METADATA$ACTION FROM svt"));
        assertEquals("true", say("SELECT SYSTEM$STREAM_HAS_DATA('svt')"));
        say("DROP TABLE tv");
        assertEquals("2|INSERT", say("SELECT id, METADATA$ACTION FROM svt"));
    }

    @Test
    public void aViewStreamReadsTheChangesOfTheSessionsTrackedTemporaryTable() {
        say("CREATE TABLE tw (id INT)");
        say("CREATE VIEW vtw AS SELECT id FROM tw");
        say("CREATE STREAM svw ON VIEW vtw");
        say("INSERT INTO tw VALUES (1)");
        say("CREATE TABLE tw2 (id INT)");
        say("CREATE TEMPORARY TABLE tw (id INT)");
        say("ALTER TABLE tw SET CHANGE_TRACKING = TRUE");
        say("INSERT INTO tw VALUES (2)");
        assertEquals("2|INSERT", say("SELECT id, METADATA$ACTION FROM svw ORDER BY 1"));
        assertEquals("true", say("SELECT SYSTEM$STREAM_HAS_DATA('svw')"));
        assertEquals("1", say("INSERT INTO tw2 SELECT id FROM svw"));
        say("DROP TABLE tw");
        // The consuming read moved the offset past the permanent table's change too.
        assertEquals("", say("SELECT id, METADATA$ACTION FROM svw ORDER BY 1"));
        assertEquals("2", say("SELECT id FROM tw2 ORDER BY 1"));
    }

    @Test
    public void aJoinViewStreamReadsTheChangesOfTheSessionsTrackedTemporaryTable() {
        say("CREATE TABLE tjw (id INT)");
        say("CREATE TABLE tjv (id INT, v STRING)");
        say("INSERT INTO tjv VALUES (1, 'one'), (2, 'two')");
        say("CREATE VIEW vjw AS SELECT tjw.id, tjv.v FROM tjw JOIN tjv ON tjw.id = tjv.id");
        say("CREATE STREAM sjw ON VIEW vjw");
        // The permanent table took no change: the temporary table's are the stream's all the same.
        say("CREATE TEMPORARY TABLE tjw (id INT)");
        say("ALTER TABLE tjw SET CHANGE_TRACKING = TRUE");
        say("INSERT INTO tjw VALUES (2)");
        assertEquals("2|two|INSERT", say("SELECT id, v, METADATA$ACTION FROM sjw ORDER BY 1"));
        say("CREATE TABLE tjo (id INT)");
        assertEquals("1", say("INSERT INTO tjo SELECT id FROM sjw"));
        assertEquals("", say("SELECT id, v, METADATA$ACTION FROM sjw ORDER BY 1"));
    }
}
