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

package dev.frostlake.functions.file;

import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing to a FILE column — the path task #134 deliberately left failing and {@code TO_FILE} now
 * completes.
 *
 * <p>Live-verified: a FILE column accepts THREE things, and each behaves
 * differently. A stage-path STRING is RESOLVED against the stage on write, so the row ends up holding
 * the full descriptor of the real file and a path that names nothing fails. A metadata OBJECT is
 * validated structurally and stored as given, WITHOUT being resolved. NULL is stored as NULL.
 */
public class FileColumnWriteTest extends StagedFileTestSupport {

    /**
     * Live: {@code INSERT INTO w SELECT 1, '@sse/hello.txt'} stores the resolved descriptor — reading
     * the row back gives the real file's metadata, not the path string that was written.
     */
    @Test
    public void stagePathStringIsResolvedOnWrite() {
        stage("hello.txt", "hello world\nsecond line\n");
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");

        engine.execute("INSERT INTO w SELECT 1, '@st/hello.txt'");

        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(f) FROM w WHERE id = 1"));
        assertEquals("24", scalar("SELECT FL_GET_SIZE(f) FROM w WHERE id = 1"));
        assertEquals("text/plain", scalar("SELECT FL_GET_CONTENT_TYPE(f) FROM w WHERE id = 1"));
        assertEquals("@TEST_DB.TEST_SCHEMA.ST", scalar("SELECT FL_GET_STAGE(f) FROM w WHERE id = 1"));
    }

    /** Live: {@code INSERT … SELECT TO_FILE(…)} stores the same descriptor as the bare path does. */
    @Test
    public void toFileExpressionIsAccepted() {
        stage("image_real.png", "not really a png but the name decides\n");
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");

        engine.execute("INSERT INTO w SELECT 2, TO_FILE('@st/image_real.png')");

        assertEquals("image/png", scalar("SELECT FL_GET_CONTENT_TYPE(f) FROM w WHERE id = 2"));
        assertEquals("image", scalar("SELECT FL_GET_FILE_TYPE(f) FROM w WHERE id = 2"));
    }

    /**
     * Live: {@code UPDATE w SET f = '@sse/plain.csv'} re-resolves against the stage, so the stored
     * content type changes to the new file's.
     */
    @Test
    public void updateReResolvesTheStagePath() {
        stage("hello.txt", "hello world\nsecond line\n");
        stage("plain.csv", "col1,col2\n1,2\n");
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");
        engine.execute("INSERT INTO w SELECT 1, '@st/hello.txt'");

        engine.execute("UPDATE w SET f = '@st/plain.csv' WHERE id = 1");

        assertEquals("text/csv", scalar("SELECT FL_GET_CONTENT_TYPE(f) FROM w WHERE id = 1"));
        assertEquals("plain.csv", scalar("SELECT FL_GET_RELATIVE_PATH(f) FROM w WHERE id = 1"));
    }

    /**
     * Live: the write fails when the path names no file — "DML operation to table W failed on column F
     * with error: Remote file '@sse/nope.txt' was not found. …".
     */
    @Test
    public void writeOfAMissingFileFails() {
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");

        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO w SELECT 4, '@st/nope.txt'");
            }
        });
        assertTrue(error.getMessage().contains("Remote file '@st/nope.txt' was not found."),
            "unexpected message: " + error.getMessage());
    }

    /**
     * Live: a valid metadata OBJECT is accepted and stored AS GIVEN — it is not resolved, so its
     * caller-supplied ETAG survives even though no such file exists on the stage.
     */
    @Test
    public void metadataObjectIsStoredWithoutResolution() {
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");

        engine.execute("INSERT INTO w SELECT 3, OBJECT_CONSTRUCT("
            + "'STAGE', '@PROBE135_DB.S.SSE', 'RELATIVE_PATH', 'hello.txt', 'SIZE', 24,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1')");

        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(f) FROM w WHERE id = 3"));
        assertEquals("e1", scalar("SELECT FL_GET_ETAG(f) FROM w WHERE id = 3"));
        assertEquals("@PROBE135_DB.S.SSE", scalar("SELECT FL_GET_STAGE(f) FROM w WHERE id = 3"));
    }

    /** Live: an object carrying an unknown field is rejected on write, naming the field. */
    @Test
    public void invalidMetadataObjectIsRejectedOnWrite() {
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");

        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO w SELECT 5, OBJECT_CONSTRUCT("
                    + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
                    + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain',"
                    + " 'ETAG', 'e1', 'A', 1)");
            }
        });
        assertTrue(error.getMessage().contains("Invalid file metadata field A."),
            "unexpected message: " + error.getMessage());
    }

    /** Live: NULL still round-trips, and IS NULL still finds it. */
    @Test
    public void nullStillRoundTrips() {
        engine.execute("CREATE TABLE w (id INTEGER, f FILE)");
        engine.execute("INSERT INTO w VALUES (9, NULL)");

        final ResultSet rs = engine.executeQuery("SELECT f FROM w WHERE id = 9");
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals(1, engine.executeQuery("SELECT id FROM w WHERE f IS NULL").getRows().size());
    }
}
