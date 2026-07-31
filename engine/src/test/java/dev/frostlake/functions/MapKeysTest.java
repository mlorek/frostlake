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

/** MAP_KEYS(map) — the map's keys as an array. Every expectation here was measured live. */
public class MapKeysTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void keysComeBackSortedNotInInsertionOrder() {
        assertEquals("[\"a\",\"b\",\"c\"]", String.valueOf(scalar(
            "SELECT MAP_KEYS(OBJECT_CONSTRUCT('b',1,'a',2,'c',3)::MAP(VARCHAR,INT))")));
    }

    @Test
    public void sortingIsByCodepointSoUpperCaseKeysComeFirst() {
        // Live: MAP_CONSTRUCT('Z','x','a','y','B','z') has keys ["B","Z","a"].
        assertEquals("[\"B\",\"Z\",\"a\"]", String.valueOf(scalar(
            "SELECT MAP_KEYS(MAP_CONSTRUCT('Z','x','a','y','B','z'))")));
    }

    @Test
    public void emptyMapHasNoKeys() {
        assertEquals("[]", String.valueOf(scalar(
            "SELECT MAP_KEYS(OBJECT_CONSTRUCT()::MAP(VARCHAR,INT))")));
    }

    @Test
    public void nullMapAnswersNull() {
        assertNull(scalar("SELECT MAP_KEYS(NULL::MAP(VARCHAR,INT))"));
    }

    @Test
    public void readsAMapColumn() {
        engine.execute("CREATE TABLE mk (m MAP(VARCHAR,INT))");
        engine.execute("INSERT INTO mk SELECT OBJECT_CONSTRUCT('z',1,'a',2,'M',3)::MAP(VARCHAR,INT)");
        assertEquals("[\"M\",\"a\",\"z\"]", String.valueOf(scalar("SELECT MAP_KEYS(m) FROM mk")));
    }

    @Test
    public void aPlainObjectIsRefused() {
        // A MAP position takes a MAP and nothing else — live: "Invalid argument types for function
        // 'MAP_KEYS': (OBJECT)". The VARIANT, ARRAY, VARCHAR, NUMBER and untyped-NULL spellings each
        // give the same sentence naming their own type.
        assertMapKeysRejects("SELECT MAP_KEYS(OBJECT_CONSTRUCT('a',1))");
        assertMapKeysRejects("SELECT MAP_KEYS(PARSE_JSON('{\"a\":1}'))");
        assertMapKeysRejects("SELECT MAP_KEYS(ARRAY_CONSTRUCT(1,2))");
        assertMapKeysRejects("SELECT MAP_KEYS('abc')");
        assertMapKeysRejects("SELECT MAP_KEYS(1)");
        assertMapKeysRejects("SELECT MAP_KEYS(NULL)");
    }

    private void assertMapKeysRejects(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_KEYS'"),
            "expected Snowflake's argument-type error for [" + sql + "], got: " + error.getMessage());
    }
}
