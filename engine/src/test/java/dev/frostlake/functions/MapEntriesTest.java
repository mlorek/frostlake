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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAP_ENTRIES(map) — the entries as an array of {@code {"key": …, "value": …}} objects. Every
 * expectation here was measured live.
 */
public class MapEntriesTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void entriesCarryLowerCaseKeyAndValueMembers() {
        assertEquals("[{\"key\":\"a\",\"value\":1},{\"key\":\"b\",\"value\":2}]", String.valueOf(scalar(
            "SELECT MAP_ENTRIES(OBJECT_CONSTRUCT('a',1,'b',2)::MAP(VARCHAR,INT))")));
    }

    @Test
    public void entriesAreInKeyOrderNotInsertionOrder() {
        assertEquals("[{\"key\":\"a\",\"value\":2},{\"key\":\"b\",\"value\":1}]", String.valueOf(scalar(
            "SELECT MAP_ENTRIES(OBJECT_CONSTRUCT('b',1,'a',2)::MAP(VARCHAR,INT))")));
    }

    @Test
    public void emptyMapHasNoEntries() {
        assertEquals("[]", String.valueOf(scalar(
            "SELECT MAP_ENTRIES(OBJECT_CONSTRUCT()::MAP(VARCHAR,INT))")));
    }

    @Test
    public void nullMapAnswersNull() {
        assertNull(scalar("SELECT MAP_ENTRIES(NULL::MAP(VARCHAR,INT))"));
    }

    @Test
    public void anEntryIsNavigableLikeAnyObject() {
        // Live: MAP_ENTRIES(m)[0]:key is 'a' and [0]:value is 1.
        assertEquals("a", String.valueOf(scalar(
            "SELECT MAP_ENTRIES(OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT))[0]:key")));
        assertEquals("1", String.valueOf(scalar(
            "SELECT MAP_ENTRIES(OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT))[0]:value")));
    }

    @Test
    public void readsAMapColumn() {
        engine.execute("CREATE TABLE me (m MAP(VARCHAR,INT))");
        engine.execute("INSERT INTO me SELECT OBJECT_CONSTRUCT('k1',1,'k2',2)::MAP(VARCHAR,INT)");
        assertEquals("[{\"key\":\"k1\",\"value\":1},{\"key\":\"k2\",\"value\":2}]",
            String.valueOf(scalar("SELECT MAP_ENTRIES(m) FROM me")));
    }

    @Test
    public void aPlainObjectIsRefused() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_ENTRIES(OBJECT_CONSTRUCT('a',1))");
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_ENTRIES'"),
            "expected Snowflake's argument-type error, got: " + error.getMessage());
    }
}
