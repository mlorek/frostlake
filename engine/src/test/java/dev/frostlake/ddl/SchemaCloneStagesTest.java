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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A schema clone copies a named external stage always, a named internal one only under INCLUDE INTERNAL STAGES, and
 * a temporary stage never; the content options are taken in either order, the retention option only after a point of
 * Time Travel, and their words stay plain names (live-verified).
 */
public class SchemaCloneStagesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE st_int");
        engine.execute("CREATE OR REPLACE STAGE st_dir DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE OR REPLACE STAGE st_ext URL = 's3://frostlake-clone-source/x/'");
        engine.execute("CREATE OR REPLACE TEMPORARY STAGE st_tmp");
        engine.execute("CREATE OR REPLACE TABLE t1 (i INT)");
    }

    /** The stages a schema lists, by name. */
    private List<String> stages(final String schema) {
        final ResultSet rs = engine.executeQuery("SHOW STAGES IN SCHEMA " + schema);
        final List<String> names = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            names.add(cell(rs, row, "name"));
        }
        return names;
    }

    @Test
    public void aCloneCopiesExternalStagesAlwaysAndInternalOnesWhenAsked() {
        engine.execute("CREATE SCHEMA sc1 CLONE test_schema");
        assertEquals(Arrays.asList("ST_EXT"), stages("sc1"));
        assertEquals(1, engine.executeQuery("SHOW TABLES IN SCHEMA sc1").getRowCount());
        engine.execute("CREATE SCHEMA sc2 CLONE test_schema INCLUDE INTERNAL STAGES");
        assertEquals(Arrays.asList("ST_DIR", "ST_EXT", "ST_INT"), stages("sc2"));
        assertEquals(0, engine.executeQuery("LIST @sc2.st_int").getRowCount());
        assertEquals(0, engine.executeQuery("LIST @sc2.st_dir").getRowCount());
        assertEquals("0", String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM DIRECTORY(@sc2.st_dir)").getRows()
            .get(0).getValue(0)));
        assertEquals(Arrays.asList("ST_DIR", "ST_EXT", "ST_INT", "ST_TMP"), stages("test_schema"));
    }

    @Test
    public void theContentOptionsAreTakenInEitherOrder() {
        engine.execute("CREATE SCHEMA sc3 CLONE test_schema IGNORE HYBRID TABLES INCLUDE INTERNAL STAGES");
        assertEquals(Arrays.asList("ST_DIR", "ST_EXT", "ST_INT"), stages("sc3"));
        engine.execute("CREATE SCHEMA sc4 CLONE test_schema INCLUDE INTERNAL STAGES IGNORE HYBRID TABLES");
        assertEquals(Arrays.asList("ST_DIR", "ST_EXT", "ST_INT"), stages("sc4"));
        refusedAt("CREATE SCHEMA sc5 CLONE test_schema IGNORE TABLES WITH INSUFFICIENT DATA RETENTION",
            "syntax error line 1 at position 43 unexpected 'TABLES'.");
        refusedAt("CREATE SCHEMA sc10 CLONE test_schema IGNORE TABLES",
            "syntax error line 1 at position 44 unexpected 'TABLES'.");
        refusedAt("CREATE OR REPLACE SCHEMA sc1 CLONE test_schema INCLUDE INTERNAL STAGE",
            "syntax error line 1 at position 64 unexpected 'STAGE'.");
        refusedAt("CREATE OR REPLACE SCHEMA sc1 CLONE test_schema INCLUDE STAGES",
            "syntax error line 1 at position 55 unexpected 'STAGES'.");
    }

    /** A statement refused with a syntax error whose first line is the one given. */
    private void refusedAt(final String sql, final String firstLine) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(refused.getMessage().contains(firstLine), refused.getMessage());
    }

    @Test
    public void theOptionsWordsStayNames() {
        engine.execute("CREATE OR REPLACE TABLE kw (internal INT, retention INT, insufficient INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2, 3)");
        final ResultSet rs = engine.executeQuery("SELECT internal + retention + insufficient AS s FROM kw");
        assertEquals("6", String.valueOf(rs.getRows().get(0).getValue(0)));
    }
}
