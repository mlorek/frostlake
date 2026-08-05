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
 * MAP_PICK(map, key …) and MAP_PICK(map, keyArray) — the map narrowed to the named keys. Both spellings
 * are real overloads live; every expectation here was measured.
 */
public class MapPickTest extends BaseDatabaseTest {

    private static final String M3 = "OBJECT_CONSTRUCT('a',1,'b',2,'c',3)::MAP(VARCHAR,INT)";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void picksTheNamedKeys() {
        assertEquals("{\"a\":1,\"c\":3}", String.valueOf(scalar(
            "SELECT MAP_PICK(" + M3 + ", 'a', 'c')")));
    }

    @Test
    public void picksFromAKeyArray() {
        assertEquals("{\"a\":1,\"b\":2}", String.valueOf(scalar(
            "SELECT MAP_PICK(" + M3 + ", ARRAY_CONSTRUCT('a','b'))")));
    }

    @Test
    public void aKeyTheMapDoesNotHoldIsSkippedNotAddedAsNull() {
        assertEquals("{}", String.valueOf(scalar("SELECT MAP_PICK(" + M3 + ", 'zzz')")));
        assertEquals("{\"a\":1}", String.valueOf(scalar(
            "SELECT MAP_PICK(" + M3 + ", ARRAY_CONSTRUCT('a','zz'))")));
    }

    @Test
    public void aRepeatedKeyIsHarmless() {
        assertEquals("{\"a\":1}", String.valueOf(scalar("SELECT MAP_PICK(" + M3 + ", 'a', 'a')")));
    }

    @Test
    public void aNullKeyPicksNothingSoTheResultIsTheEmptyMapNotNull() {
        assertEquals("{}", String.valueOf(scalar("SELECT MAP_PICK(" + M3 + ", NULL)")));
        assertEquals("{}", String.valueOf(scalar("SELECT MAP_PICK(" + M3 + ", NULL::ARRAY)")));
    }

    @Test
    public void nullMapAnswersNull() {
        assertNull(scalar("SELECT MAP_PICK(NULL::MAP(VARCHAR,INT), 'a')"));
        assertNull(scalar("SELECT MAP_PICK(NULL::MAP(VARCHAR,INT), ARRAY_CONSTRUCT('a'))"));
    }

    @Test
    public void readsAMapColumn() {
        engine.execute("CREATE TABLE mp (m MAP(VARCHAR,INT))");
        engine.execute("INSERT INTO mp SELECT OBJECT_CONSTRUCT('z',1,'a',2,'M',3)::MAP(VARCHAR,INT)");
        assertEquals("{\"a\":2,\"z\":1}", String.valueOf(scalar("SELECT MAP_PICK(m, 'z', 'a') FROM mp")));
    }

    @Test
    public void aPlainObjectIsRefused() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_PICK(OBJECT_CONSTRUCT('a',1,'b',2), 'a')");
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_PICK'"),
            "expected Snowflake's argument-type error, got: " + error.getMessage());
    }
}
