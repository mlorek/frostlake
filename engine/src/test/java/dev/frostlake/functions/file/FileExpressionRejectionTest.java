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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the semi-structured surface may and may not do with a FILE value.
 *
 * <p>A FILE value IS an object of file metadata, but live Snowflake does not let the VARIANT surface
 * reach into it. Measured over a populated FILE column: {@code TYPEOF(f)},
 * {@code OBJECT_KEYS(f)} and {@code f:RELATIVE_PATH} are all compile errors naming the FILE type, while
 * equality, {@code IS NULL}, {@code COUNT} and {@code SELECT DISTINCT} all work normally. The
 * accessors are the only supported way in.
 */
public class FileExpressionRejectionTest extends FileFunctionTestSupport {

    private void assertRejectsFile(final String sql, final String function) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(error.getMessage().contains("Invalid argument types for function '" + function + "'")
                && error.getMessage().contains("FILE"),
            "unexpected message for [" + sql + "]: " + error.getMessage());
    }

    private void createPopulatedFileColumn() {
        engine.execute("CREATE TABLE fx (id INTEGER, f FILE)");
        engine.execute("INSERT INTO fx SELECT 1, OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'hello.txt', 'SIZE', 24,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1')");
    }

    /** Live: {@code TYPEOF(f)} fails "Invalid argument types for function 'TYPEOF': (FILE)". */
    @Test
    public void typeofRejectsAFileColumn() {
        createPopulatedFileColumn();
        assertRejectsFile("SELECT TYPEOF(f) FROM fx", "TYPEOF");
    }

    /** Live: {@code OBJECT_KEYS(f)} fails "Invalid argument types for function 'OBJECT_KEYS': (FILE)". */
    @Test
    public void objectKeysRejectsAFileColumn() {
        createPopulatedFileColumn();
        assertRejectsFile("SELECT OBJECT_KEYS(f) FROM fx", "OBJECT_KEYS");
    }

    /**
     * Live: path access into a FILE fails "Invalid argument types for function 'GET': (FILE,
     * VARCHAR(13))" — the {@code :} operator reports itself as GET.
     */
    @Test
    public void pathAccessIntoAFileIsRejected() {
        createPopulatedFileColumn();
        assertRejectsFile("SELECT f:RELATIVE_PATH FROM fx", "GET");
    }

    /**
     * Live: the operations that DO work must keep working — {@code f = f} is TRUE, {@code IS NULL}
     * answers, and {@code COUNT(f)} counts the non-NULL rows.
     */
    @Test
    public void equalityNullTestAndCountStillWork() {
        createPopulatedFileColumn();
        engine.execute("INSERT INTO fx VALUES (2, NULL)");

        assertEquals("TRUE", scalar("SELECT f = f FROM fx WHERE id = 1").toUpperCase());
        assertEquals("FALSE", scalar("SELECT f IS NULL FROM fx WHERE id = 1").toUpperCase());
        assertEquals("1", scalar("SELECT COUNT(f) FROM fx"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM fx"));
    }

    /**
     * Live: a bare stage path is NOT a FILE — {@code FL_GET_RELATIVE_PATH('@sse/hello.txt')} is the
     * compile error "Invalid argument types for function 'FL_GET_RELATIVE_PATH': (VARCHAR(14))", so the
     * path must be wrapped in {@code TO_FILE}. A NUMBER instead fails at run time, "Unsupported cast to
     * FILE." — measured for {@code FL_GET_SIZE(42)}.
     */
    @Test
    public void accessorsRejectABareStagePathString() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT FL_GET_RELATIVE_PATH('@sse/hello.txt')");
            }
        });
        assertTrue(error.getMessage().contains(
                "Invalid argument types for function 'FL_GET_RELATIVE_PATH': (VARCHAR(14))"),
            "unexpected message: " + error.getMessage());
    }

    /** Live: {@code SELECT DISTINCT f} is allowed (unlike GROUP BY / ORDER BY over a FILE). */
    @Test
    public void distinctOverAFileColumnIsAllowed() {
        createPopulatedFileColumn();
        engine.execute("INSERT INTO fx SELECT 2, OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'hello.txt', 'SIZE', 24,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1')");

        assertEquals(1, engine.executeQuery("SELECT DISTINCT f FROM fx").getRows().size());
    }
}
