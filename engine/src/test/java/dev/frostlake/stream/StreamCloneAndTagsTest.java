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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CREATE STREAM … CLONE, CREATE STREAM … COPY GRANTS and ALTER STREAM … SET | UNSET TAG. */
public class StreamCloneAndTagsTest extends BaseDatabaseTest {

    private long count(final String stream) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + stream).getRows().get(0).getValue(0))
            .longValue();
    }

    @Test
    public void aCloneInheritsTheSourcesOffset() {
        engine.execute("CREATE TABLE src_t (a INT)");
        // No COPY GRANTS here: a plain create has no source to copy from, and the account refuses it.
        engine.execute("CREATE STREAM src_s ON TABLE src_t APPEND_ONLY = TRUE COMMENT = 'orig'");
        engine.execute("INSERT INTO src_t VALUES (1), (2)");
        engine.execute("CREATE STREAM clone_s CLONE src_s");
        assertEquals(2L, count("clone_s"), "the clone holds the source's pending changes");
        engine.execute("CREATE TABLE sink (a INT)");
        engine.execute("INSERT INTO sink SELECT a FROM src_s");
        assertEquals(0L, count("src_s"));
        assertEquals(2L, count("clone_s"), "consuming the source leaves the clone's offset alone");
        engine.execute("INSERT INTO src_t VALUES (3)");
        assertEquals(3L, count("clone_s"), "the clone records later changes too");
        final ResultSet shown = engine.executeQuery("SHOW STREAMS LIKE 'CLONE_S'");
        assertEquals("APPEND_ONLY", shown.getRows().get(0).getValue(shown.getColumnIndex("mode")));
        assertEquals("orig", shown.getRows().get(0).getValue(shown.getColumnIndex("comment")));
    }

    @Test
    public void cloneModesAndRefusals() {
        engine.execute("CREATE TABLE modes_t (a INT)");
        engine.execute("CREATE STREAM modes_s ON TABLE modes_t");
        engine.execute("CREATE STREAM modes_c CLONE modes_s COPY GRANTS");
        final RuntimeException exists = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM modes_c CLONE modes_s");
            }
        });
        assertTrue(exists.getMessage().contains("already exists"), exists.getMessage());
        engine.execute("CREATE STREAM IF NOT EXISTS modes_c CLONE modes_s");
        engine.execute("CREATE OR REPLACE STREAM modes_c CLONE modes_s");
        final RuntimeException missing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM other_c CLONE no_such_stream");
            }
        });
        assertTrue(missing.getMessage().contains("does not exist"), missing.getMessage());
    }

    @Test
    public void aStreamCarriesTags() {
        engine.execute("CREATE TAG owner_tag");
        engine.execute("CREATE TABLE tag_t (a INT)");
        engine.execute("CREATE STREAM tag_s ON TABLE tag_t");
        engine.execute("ALTER STREAM tag_s SET TAG owner_tag = 'team'");
        assertEquals("team", engine.executeQuery("SELECT SYSTEM$GET_TAG('owner_tag', 'tag_s', 'STREAM')")
            .getRows().get(0).getValue(0));
        engine.execute("ALTER STREAM tag_s UNSET TAG owner_tag");
        assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES("
            + "'tag_s', 'STREAM'))").getRowCount());
    }
}
