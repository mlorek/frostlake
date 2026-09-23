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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pipes named with their database and schema from another database — CREATE, ALTER … REFRESH | SET TAG,
 * DROP — the COMMENT option written before AS, and DESCRIBE STAGE of a qualified name.
 */
public class PipeAndStageQualifiedNamesTest extends BaseDatabaseTest {

    /** The file is staged in a LOCAL directory, which a cloud stage has no equivalent for. */
    private static final String NO_LOCAL_STAGE = "the pipe loads a file staged in a local"
        + " directory; the account's stages are cloud storage";

    @Test
    public void aQualifiedPipeLivesInItsOwnSchemaAndLoadsThere() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_LOCAL_STAGE);
        engine.execute("CREATE DATABASE pq_db");
        engine.execute("CREATE SCHEMA pq_db.s");
        engine.execute("CREATE TABLE pq_db.s.t (a INT, b VARCHAR)");
        engine.execute("CREATE STAGE pq_db.s.st");
        stageLocalFile("pq_db.s.st", "rows.csv", "1,a\n2,b\n");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE PIPE pq_db.s.p COMMENT = 'before as' AS COPY INTO t FROM @st FILE_FORMAT = (TYPE = CSV)");
        final ResultSet pipes = engine.executeQuery("SHOW PIPES IN SCHEMA pq_db.s");
        assertEquals(1, pipes.getRowCount());
        assertEquals("P", pipes.getRows().get(0).getValue(pipes.getColumnIndex("name")));
        assertEquals("before as", pipes.getRows().get(0).getValue(pipes.getColumnIndex("comment")));
        assertEquals(0, engine.executeQuery("SHOW PIPES IN SCHEMA test_db.test_schema").getRowCount());

        engine.execute("ALTER PIPE pq_db.s.p REFRESH");
        assertEquals(2L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM pq_db.s.t").getRows().get(0)
            .getValue(0)).longValue(), "the pipe's COPY resolves in the pipe's schema");

        engine.execute("CREATE TAG pq_db.s.cc");
        engine.execute("ALTER PIPE pq_db.s.p SET TAG pq_db.s.cc = 'x'");
        assertEquals(1, engine.executeQuery("SELECT * FROM TABLE(pq_db.INFORMATION_SCHEMA.TAG_REFERENCES("
            + "'pq_db.s.p', 'PIPE'))").getRowCount());
        engine.execute("ALTER PIPE pq_db.s.p UNSET TAG pq_db.s.cc");

        engine.execute("DROP PIPE pq_db.s.p");
        assertEquals(0, engine.executeQuery("SHOW PIPES IN SCHEMA pq_db.s").getRowCount());
        engine.execute("DROP PIPE IF EXISTS pq_db.s.p");
    }

    @Test
    public void describeStageTakesAQualifiedName() {
        engine.execute("CREATE DATABASE ds_db");
        engine.execute("CREATE SCHEMA ds_db.s");
        engine.execute("CREATE STAGE ds_db.s.st DIRECTORY = (ENABLE = TRUE)");
        engine.execute("USE DATABASE test_db");
        final ResultSet described = engine.executeQuery("DESCRIBE STAGE ds_db.s.st");
        String enabled = null;
        for (int i = 0; i < described.getRowCount(); i++) {
            if ("DIRECTORY".equals(described.getRows().get(i).getValue(0))
                    && "ENABLE".equals(described.getRows().get(i).getValue(1))) {
                enabled = String.valueOf(described.getRows().get(i).getValue(3));
            }
        }
        assertEquals("true", enabled);
    }
}
