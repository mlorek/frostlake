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
import static org.junit.jupiter.api.Assertions.assertNull;

/** STRTOK_TO_ARRAY(string [, delimiters]) — tokenizes into a VARIANT ARRAY of non-empty tokens. */
public class StrtokToArrayTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void eachDelimiterCharacterSeparates() {
        // Documented Snowflake example: both '.' and '@' are delimiters.
        assertEquals("[\"user\",\"snowflake\",\"com\"]",
            scalar("SELECT STRTOK_TO_ARRAY('user@snowflake.com', '.@')"));
    }

    @Test
    public void defaultDelimiterIsSpace() {
        assertEquals("[\"hello\",\"world\",\"test\"]", scalar("SELECT STRTOK_TO_ARRAY('hello world test')"));
    }

    @Test
    public void consecutiveDelimitersProduceNoEmptyTokens() {
        assertEquals("[\"a\",\"b\"]", scalar("SELECT STRTOK_TO_ARRAY('a,,b', ',')"));
    }

    @Test
    public void nullStringYieldsNull() {
        assertNull(scalar("SELECT STRTOK_TO_ARRAY(NULL)"));
    }

    @Test
    public void nullDelimiterYieldsNull() {
        assertNull(scalar("SELECT STRTOK_TO_ARRAY('a b', NULL)"));
    }
}
