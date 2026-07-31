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
 * MAP_INSERT(map, key, value [, updateFlag]) — add or replace one entry. Its NULL handling differs from
 * OBJECT_INSERT's on two branches, so each was measured live rather than carried over.
 */
public class MapInsertTest extends BaseDatabaseTest {

    private static final String M1 = "OBJECT_CONSTRUCT('a',1)::MAP(VARCHAR,INT)";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void addsANewEntry() {
        assertEquals("{\"a\":1,\"b\":2}", String.valueOf(scalar(
            "SELECT MAP_INSERT(" + M1 + ", 'b', 2)")));
    }

    @Test
    public void aDuplicateKeyIsAnErrorUnlessTheUpdateFlagIsTrue() {
        // Live: "Duplicate field key 'a'" (SQLSTATE 22000) for an absent flag, an explicit FALSE and an
        // explicit NULL alike; only TRUE replaces the value.
        assertDuplicateKey("SELECT MAP_INSERT(" + M1 + ", 'a', 9)");
        assertDuplicateKey("SELECT MAP_INSERT(" + M1 + ", 'a', 9, FALSE)");
        assertDuplicateKey("SELECT MAP_INSERT(" + M1 + ", 'a', 9, NULL)");
        assertEquals("{\"a\":9}", String.valueOf(scalar("SELECT MAP_INSERT(" + M1 + ", 'a', 9, TRUE)")));
    }

    @Test
    public void aNullValueIsStoredAsAJsonNullMember() {
        // The sharp divergence from OBJECT_INSERT, which OMITS the pair for a SQL NULL value.
        assertEquals("{\"a\":1,\"b\":null}", String.valueOf(scalar(
            "SELECT MAP_INSERT(" + M1 + ", 'b', NULL)")));
    }

    @Test
    public void aNullKeyAnswersNull() {
        // The other divergence: OBJECT_INSERT returns the map unchanged for a NULL key.
        assertNull(scalar("SELECT MAP_INSERT(" + M1 + ", NULL, 2)"));
    }

    @Test
    public void nullMapAnswersNull() {
        assertNull(scalar("SELECT MAP_INSERT(NULL::MAP(VARCHAR,INT), 'b', 2)"));
    }

    @Test
    public void readsAMapColumn() {
        engine.execute("CREATE TABLE mi (m MAP(VARCHAR,INT))");
        engine.execute("INSERT INTO mi SELECT OBJECT_CONSTRUCT('z',1,'a',2)::MAP(VARCHAR,INT)");
        assertEquals("{\"a\":2,\"z\":9}",
            String.valueOf(scalar("SELECT MAP_INSERT(m, 'z', 9, TRUE) FROM mi")));
    }

    @Test
    public void aPlainObjectIsRefused() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_INSERT(OBJECT_CONSTRUCT('a',1), 'b', 2)");
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Invalid argument types for function 'MAP_INSERT'"),
            "expected Snowflake's argument-type error, got: " + error.getMessage());
    }

    private void assertDuplicateKey(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("Duplicate field key 'a'"),
            "expected Snowflake's duplicate-key error for [" + sql + "], got: " + error.getMessage());
    }
}
