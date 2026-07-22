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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code GET(array_or_object, index_or_key)} semi-structured function is usable in expressions even
 * though {@code GET} is also the stage-download command keyword — the two are disambiguated by context
 * (a statement starting with GET is the stage command; {@code GET(...)} in an expression is the function).
 */
public class GetFunctionTest extends BaseDatabaseTest {

    @Test
    public void getArrayElementByIndex() {
        final ResultSet rs = engine.executeQuery("SELECT GET(PARSE_JSON('[10,20,30]'), 1)");
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void getObjectValueByKey() {
        final ResultSet rs = engine.executeQuery("SELECT GET(PARSE_JSON('{\"a\":5}'), 'a')");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void getOverObjectKeysFromColonPathThenCast() {
        // The reported expression: GET(OBJECT_KEYS(col:field), 0)::STRING — GET nested over OBJECT_KEYS of
        // a colon-path field, then cast. OBJECT_KEYS returns the keys in sorted order, so element 0 is the
        // first key.
        engine.execute("CREATE TABLE t1 (c VARIANT)");
        engine.execute("INSERT INTO t1 SELECT PARSE_JSON('{\"owner\":{\"alice\":1,\"bob\":2}}')");

        final ResultSet rs = engine.executeQuery(
            "SELECT GET(OBJECT_KEYS(a.c:owner), 0)::STRING FROM t1 a");
        assertEquals("alice", rs.getRows().get(0).getValue(0));
    }
}
