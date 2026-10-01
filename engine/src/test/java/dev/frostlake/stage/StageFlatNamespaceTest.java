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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.functions.scalar.file.PresignedUrls;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stage is a flat set of names, so one name may run on past another: {@code dir} and {@code dir/g} are two files
 * side by side, written by an unload or a PUT in either order, and every statement that reads the stage sees both
 * under the names they were written with.
 */
public class StageFlatNamespaceTest extends BaseDatabaseTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    /** One column of a query's rows, as text. */
    private List<String> column(final String sql, final int index) {
        final List<String> values = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            values.add(String.valueOf(row.getValue(index)));
        }
        return values;
    }

    /** A query's rows, each as its cells joined with '|'. */
    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        final ResultSet result = engine.executeQuery(sql);
        for (final Row row : result.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < result.getColumns().size(); i++) {
                line.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
            out.add(line.toString());
        }
        return out;
    }

    @Test
    public void aSingleUnloadWritesANameOtherNamesContinue() {
        engine.execute("CREATE STAGE sfn");
        engine.execute("COPY INTO @sfn/dir/g FROM (SELECT 5)" + CSV);
        engine.execute("COPY INTO @sfn/dir FROM (SELECT 50)" + CSV);
        engine.execute("COPY INTO @sfn/dir/ FROM (SELECT 51)" + CSV);
        final String taken = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO @sfn/dir FROM (SELECT 52)" + CSV);
            }
        }).getMessage();
        assertTrue(taken.contains("Files already existing at the unload destination: @sfn/dir. Use overwrite option"
            + " to force unloading."), taken);
        assertEquals(List.of("sfn/dir", "sfn/dir/data", "sfn/dir/g"), column("LIST @sfn", 0));
        assertEquals(List.of("sfn/dir", "sfn/dir/data", "sfn/dir/g"), column("LIST @sfn/dir", 0));
        assertEquals(List.of("sfn/dir/data", "sfn/dir/g"), column("LIST @sfn/dir/", 0));
        assertEquals(List.of("50|dir", "51|dir/data", "5|dir/g"),
            rows("SELECT $1, METADATA$FILENAME FROM @sfn ORDER BY 2"));
        assertEquals(List.of("51|dir/data", "5|dir/g"), rows("SELECT $1, METADATA$FILENAME FROM @sfn/dir/ ORDER BY 2"));
        assertEquals(List.of("5|dir/g"), rows("SELECT $1, METADATA$FILENAME FROM @sfn/dir/g ORDER BY 2"));

        engine.execute("COPY INTO @sfn/dir FROM (SELECT 53)" + CSV + " OVERWRITE = TRUE");
        assertEquals(List.of("53|dir", "51|dir/data", "5|dir/g"),
            rows("SELECT $1, METADATA$FILENAME FROM @sfn/dir ORDER BY 2"));
        assertEquals("dir", column("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@sfn/dir'))", 0).get(0));
    }

    @Test
    public void aPutLandsBesideAFileOfItsPathsName() {
        engine.execute("CREATE STAGE sfp");
        stageLocalFile("sfp", "p", "top-p\n");
        stageLocalFile("sfp/p", "x", "x-in-p\n");
        assertEquals(List.of("sfp/p", "sfp/p/x"), column("LIST @sfp/p", 0));
        assertEquals(List.of("top-p|p", "x-in-p|p/x"), rows("SELECT $1, METADATA$FILENAME FROM @sfp/p ORDER BY 2"));
        engine.execute("COPY INTO @sfp/p/x/deeper FROM (SELECT 9)" + CSV);
        assertEquals(List.of("top-p|p", "x-in-p|p/x", "9|p/x/deeper"),
            rows("SELECT $1, METADATA$FILENAME FROM @sfp/p ORDER BY 2"));
        assertEquals("p", column("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@sfp', 'p'))", 0).get(0));
    }

    @Test
    public void removeReachesEachName() {
        engine.execute("CREATE STAGE sfr");
        stageLocalFile("sfr", "p", "top-p\n");
        stageLocalFile("sfr/p", "x", "x-in-p\n");
        engine.execute("COPY INTO @sfr/p/x/deeper FROM (SELECT 9)" + CSV);
        final List<String> removed = column("REMOVE @sfr/p/x", 0);
        Collections.sort(removed);
        assertEquals(List.of("sfr/p/x", "sfr/p/x/deeper"), removed);
        assertEquals(List.of("top-p|p"), rows("SELECT $1, METADATA$FILENAME FROM @sfr/p ORDER BY 2"));
        assertEquals(List.of("sfr/p"), column("REMOVE @sfr/p", 0));
        assertEquals(List.of(), column("LIST @sfr", 0));
    }

    @Test
    public void getDownloadsEachNameByItsOwnLastPart() throws Exception {
        engine.execute("CREATE STAGE sfg");
        engine.execute("COPY INTO @sfg/dir/g FROM (SELECT 5)" + CSV);
        engine.execute("COPY INTO @sfg/dir FROM (SELECT 50)" + CSV);
        final Path local = Files.createTempDirectory("sfg_get");
        final List<String> fetched = column("GET @sfg/dir 'file://" + local.toAbsolutePath() + "/'", 0);
        Collections.sort(fetched);
        assertEquals(List.of("dir", "dir/g"), fetched);
        assertEquals("50\n", Files.readString(local.resolve("dir")));
        assertEquals("5\n", Files.readString(local.resolve("g")));
    }

    /** A loaded file stays loaded when a later name continues it, and FILES names it exactly. */
    @Test
    public void aLoadedFileStaysLoadedWhenANameContinuesIt() {
        engine.execute("CREATE STAGE sfl");
        engine.execute("CREATE TABLE sfl_t (a INT)");
        engine.execute("COPY INTO @sfl/dir FROM (SELECT 50)" + CSV);
        engine.execute("COPY INTO sfl_t FROM @sfl/dir FILE_FORMAT = (TYPE = CSV)");
        engine.execute("COPY INTO @sfl/dir/g FROM (SELECT 5)" + CSV);
        engine.execute("COPY INTO sfl_t FROM @sfl/dir FILE_FORMAT = (TYPE = CSV)");
        assertEquals(List.of("5", "50"), column("SELECT a FROM sfl_t ORDER BY a", 0));
        engine.execute("COPY INTO sfl_t FROM @sfl FILES = ('dir') FILE_FORMAT = (TYPE = CSV) FORCE = TRUE");
        assertEquals(List.of("5", "50", "50"), column("SELECT a FROM sfl_t ORDER BY a", 0));
    }

    /** The file that other names continue is kept inside the directory of its name; its neighbours keep their paths. */
    @Test
    public void theContinuedNameIsKeptInsideItsDirectory() throws Exception {
        Assumptions.assumeFalse(isLiveSnowflake(), "the on-disk layout is the engine's own");
        engine.execute("CREATE STAGE sfd");
        engine.execute("COPY INTO @sfd/plain FROM (SELECT 1)" + CSV);
        engine.execute("COPY INTO @sfd/dir FROM (SELECT 50)" + CSV);
        engine.execute("COPY INTO @sfd/dir/g FROM (SELECT 5)" + CSV);
        final Path root = engine.getExecutor().resolveCopyBaseDir("@sfd");
        assertTrue(Files.isRegularFile(root.resolve("plain")));
        assertTrue(Files.isRegularFile(root.resolve("dir").resolve(StagePathSegments.OWN_FILE)));
        assertTrue(Files.isRegularFile(root.resolve("dir").resolve("g")));
        assertEquals("dir", StagePathSegments.written("dir/" + StagePathSegments.OWN_FILE));
        assertEquals("dir", StagePathSegments.diskName(root.resolve("dir").resolve(StagePathSegments.OWN_FILE)));
        final String url = String.valueOf(engine.executeQuery("SELECT GET_PRESIGNED_URL(@sfd, 'dir')").getRows()
            .get(0).getValue(0));
        assertTrue(url.endsWith("/dir"), url);
        final String token = url.substring(url.indexOf(PresignedUrls.CONTEXT) + PresignedUrls.CONTEXT.length(),
            url.lastIndexOf('/'));
        // The token signs the path the name spells, which reaches the file inside the directory of that name.
        final Path signed = PresignedUrls.verify(token, new long[1]);
        assertEquals("dir", signed.getFileName().toString());
        assertEquals("50\n", Files.readString(StagePathSegments.ownFileOf(signed)));
    }

    /** A name that only the names continuing it hold is no file: a script run from it is missing. */
    @Test
    public void aScriptNamedOnlyByTheNamesContinuingItIsMissing() {
        engine.execute("CREATE STAGE sfx");
        engine.execute("COPY INTO @sfx/only/x FROM (SELECT 1)" + CSV);
        assertEquals("File '/only' not found in stage 'TEST_DB.TEST_SCHEMA.SFX'.",
            refusal("EXECUTE IMMEDIATE FROM @sfx/only"));
        assertEquals("File '/only/' not found in stage 'TEST_DB.TEST_SCHEMA.SFX'.",
            refusal("EXECUTE IMMEDIATE FROM @sfx/only/"));
        assertEquals("File '/nosuch' not found in stage 'TEST_DB.TEST_SCHEMA.SFX'.",
            refusal("EXECUTE IMMEDIATE FROM '@sfx/nosuch'"));
        engine.execute("COPY INTO @sfx/scr FROM (SELECT 'SELECT 42 AS v;')" + CSV);
        engine.execute("COPY INTO @sfx/scr/x FROM (SELECT 1)" + CSV);
        assertEquals(List.of("42"), column("EXECUTE IMMEDIATE FROM @sfx/scr", 0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }
}
