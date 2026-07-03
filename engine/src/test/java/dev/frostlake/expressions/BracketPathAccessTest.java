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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bracket subscripts on a VARIANT: a numeric subscript indexes an array; a string subscript {@code x['key']}
 * is object member access (Snowflake bracket notation, equivalent to {@code x:key}).
 */
public class BracketPathAccessTest extends BaseDatabaseTest {

    private long asLong(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void stringKeySubscriptReadsObjectMember() {
        assertEquals(1L, asLong("SELECT PARSE_JSON('{\"a\":1}')['a']"));
    }

    @Test
    public void nestedStringKeySubscripts() {
        assertEquals(2L, asLong("SELECT PARSE_JSON('{\"a\":{\"b\":2}}')['a']['b']"));
    }

    @Test
    public void numericSubscriptStillIndexesArray() {
        assertEquals(20L, asLong("SELECT PARSE_JSON('[10,20,30]')[1]"));
    }

    @Test
    public void colonPathStillWorks() {
        assertEquals(1L, asLong("SELECT PARSE_JSON('{\"a\":1}'):a"));
    }
}
