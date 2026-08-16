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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@code TO_FILE} over the two IMPLICIT stages — the user stage {@code @~} and a table stage
 * {@code @%table} — which render their {@code STAGE} field quite differently from a named stage.
 *
 * <p>Live-verified, after PUTting the same file to each:
 * {@code TO_FILE('@~/p135/hello.txt')} reports {@code STAGE} {@code @"~"} with {@code RELATIVE_PATH}
 * {@code p135/hello.txt} (the sub-path is kept), while {@code TO_FILE('@%tstg/hello.txt')} reports
 * {@code STAGE} {@code @TSTG} — the BARE upper-cased table name, with no database or schema, unlike a
 * named stage which always renders fully qualified.
 *
 * <p>This suite drives its own engine so the implicit-stage root is a temp directory rather than the
 * developer's home, following {@code dml/ImplicitStageTest}. It is engine-only: reaching these stages
 * on a live account needs a PUT, which the shared test harness does not perform.
 */
public class ImplicitStageFileTest extends BaseDatabaseTest {

    private DatabaseEngine ownEngine;
    private Path internalRoot;
    private Path localDir;

    @BeforeEach
    public void setUpImplicitStages() throws IOException {
        if (isLiveSnowflake()) {
            return;
        }
        internalRoot = Files.createTempDirectory("frostlake_file_implicit_");
        localDir = Files.createTempDirectory("frostlake_file_local_");
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        ownEngine = new DatabaseEngine(config);
        ownEngine.execute("CREATE DATABASE d");
        ownEngine.execute("USE DATABASE d");
        ownEngine.execute("CREATE SCHEMA s");
        ownEngine.execute("USE SCHEMA s");
        ownEngine.execute("CREATE TABLE tstg (id INTEGER)");
        Files.writeString(localDir.resolve("hello.txt"), "hello world\nsecond line\n");
    }

    @AfterEach
    public void tearDownImplicitStages() {
        if (ownEngine != null) {
            ownEngine.shutdown();
            ownEngine = null;
        }
        deleteRecursively(internalRoot);
        deleteRecursively(localDir);
    }

    private String scalarOnOwnEngine(final String sql) {
        final Object value = ownEngine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** Live: the user stage renders {@code @"~"} and keeps the sub-path in RELATIVE_PATH. */
    @Test
    public void userStageRendersQuotedTilde() {
        assumeFalse(isLiveSnowflake(), "reaching @~ on a live account needs a PUT");
        ownEngine.executeQuery("PUT 'file://" + localDir.resolve("hello.txt") + "' @~/p135 AUTO_COMPRESS=FALSE");

        assertEquals("@\"~\"", scalarOnOwnEngine("SELECT FL_GET_STAGE(TO_FILE('@~/p135/hello.txt'))"));
        assertEquals("p135/hello.txt",
            scalarOnOwnEngine("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@~/p135/hello.txt'))"));
        assertEquals("24", scalarOnOwnEngine("SELECT FL_GET_SIZE(TO_FILE('@~/p135/hello.txt'))"));
        assertEquals("text/plain",
            scalarOnOwnEngine("SELECT FL_GET_CONTENT_TYPE(TO_FILE('@~/p135/hello.txt'))"));
    }

    /** Live: a table stage renders the BARE upper-cased table name — {@code @TSTG}, not @D.S.TSTG. */
    @Test
    public void tableStageRendersTheBareTableName() {
        assumeFalse(isLiveSnowflake(), "reaching @%table on a live account needs a PUT");
        ownEngine.executeQuery("PUT 'file://" + localDir.resolve("hello.txt") + "' @%tstg AUTO_COMPRESS=FALSE");

        assertEquals("@TSTG", scalarOnOwnEngine("SELECT FL_GET_STAGE(TO_FILE('@%tstg/hello.txt'))"));
        assertEquals("hello.txt",
            scalarOnOwnEngine("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@%tstg/hello.txt'))"));
    }

    /** Live: a missing file on an implicit stage raises the same "was not found" error. */
    @Test
    public void missingFileOnAnImplicitStageIsNotFound() {
        assumeFalse(isLiveSnowflake(), "reaching @~ on a live account needs a PUT");

        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ownEngine.executeQuery("SELECT TO_FILE('@~/nothing.txt')");
            }
        });
        assertTrue(error.getMessage().contains("Remote file '@~/nothing.txt' was not found."),
            "unexpected message: " + error.getMessage());
    }

    private static void deleteRecursively(final Path path) {
        if (path == null) {
            return;
        }
        deleteRecursively(path.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
