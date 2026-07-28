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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OBJECT_INSERT null semantics (Snowflake): a SQL NULL value OMITS the key-value pair from the
 * result — an already-present key is removed — while a JSON null (PARSE_JSON('null')) is stored
 * as a real null member. Without the update flag, inserting an existing key errors.
 */
public class ObjectInsertTest extends BaseDatabaseTest {

    private String eval(final String expr) {
        return String.valueOf(engine.executeQuery("SELECT " + expr).getRows().get(0).getValue(0));
    }

    @Test
    public void insertsANewKey() {
        assertEquals("{\"a\":1,\"b\":2}", eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'b', 2)"));
    }

    @Test
    public void sqlNullValueOmitsThePair() {
        assertEquals("{\"a\":1}", eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'b', NULL)"));
        assertEquals("{\"a\":1}", eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'b', NULL, TRUE)"));
    }

    @Test
    public void sqlNullValueRemovesAnExistingKey() {
        assertEquals("{\"a\":1}",
            eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1, 'b', 2), 'b', NULL, TRUE)"));
    }

    @Test
    public void jsonNullValueIsStoredAsANullMember() {
        assertEquals("{\"a\":1,\"b\":null}",
            eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'b', PARSE_JSON('null'))"));
    }

    @Test
    public void tryParseJsonOfNullBehavesLikeSqlNull() {
        // The loader pattern: OBJECT_INSERT(attrs, 'k', TRY_PARSE_JSON(<all-null aggregate>), TRUE)
        // must NOT add the key when the parsed text is SQL NULL.
        assertEquals("{\"a\":1}",
            eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'b', TRY_PARSE_JSON(NULL), TRUE)"));
    }

    @Test
    public void duplicateKeyWithoutUpdateFlagErrors() {
        assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'a', 2)");
            }
        });
    }

    @Test
    public void updateFlagReplacesTheValue() {
        assertEquals("{\"a\":9}", eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), 'a', 9, TRUE)"));
    }

    @Test
    public void sqlNullKeyLeavesTheObjectUnchanged() {
        assertEquals("{\"a\":1}", eval("OBJECT_INSERT(OBJECT_CONSTRUCT('a', 1), NULL, 2)"));
    }
}
