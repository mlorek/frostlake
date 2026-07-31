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

/** MAP_SIZE(map) — the number of entries. Every expectation here was measured live. */
public class MapSizeTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void countsTheEntries() {
        assertEquals("2", String.valueOf(scalar(
            "SELECT MAP_SIZE(OBJECT_CONSTRUCT('a',1,'b',2)::MAP(VARCHAR,INT))")));
        assertEquals("3", String.valueOf(scalar("SELECT MAP_SIZE(MAP_CONSTRUCT('x',1,'y',2,'z',3))")));
    }

    @Test
    public void emptyMapIsZero() {
        assertEquals("0", String.valueOf(scalar(
            "SELECT MAP_SIZE(OBJECT_CONSTRUCT()::MAP(VARCHAR,INT))")));
    }

    @Test
    public void nullMapAnswersNull() {
        assertNull(scalar("SELECT MAP_SIZE(NULL::MAP(VARCHAR,INT))"));
    }

    @Test
    public void anEntryWhoseValueIsNullStillCounts() {
        // Live: MAP_SIZE(MAP_INSERT(<{'a':1}>, 'b', NULL)) is 2 — the pair is kept as a JSON null.
        assertEquals("2", String.valueOf(scalar(
            "SELECT MAP_SIZE(MAP_INSERT(OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT), 'b', NULL))")));
    }

    @Test
    public void readsAMapColumn() {
        engine.execute("CREATE TABLE ms (m MAP(VARCHAR,INT))");
        engine.execute("INSERT INTO ms SELECT OBJECT_CONSTRUCT('z',1,'a',2,'M',3)::MAP(VARCHAR,INT)");
        assertEquals("3", String.valueOf(scalar("SELECT MAP_SIZE(m) FROM ms")));
    }

    @Test
    public void aPlainObjectAndAKeyArrayAreBothRefused() {
        // Live: "Invalid argument types for function 'MAP_SIZE': (OBJECT)" — and the ARRAY that
        // MAP_KEYS hands back is refused too, "(ARRAY(VARCHAR(16777216) NOT NULL))".
        assertMapSizeRejects("SELECT MAP_SIZE(OBJECT_CONSTRUCT('a',1))");
        assertMapSizeRejects(
            "SELECT MAP_SIZE(MAP_KEYS(OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT)))");
    }

    private void assertMapSizeRejects(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_SIZE'"),
            "expected Snowflake's argument-type error for [" + sql + "], got: " + error.getMessage());
    }
}
