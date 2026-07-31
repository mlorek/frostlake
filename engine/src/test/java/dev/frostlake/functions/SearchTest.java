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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SEARCH(data, query [, ANALYZER =&gt; ...] [, SEARCH_MODE =&gt; 'OR'|'AND']) — token-based full-text
 * search: case-insensitive whole-token matching, OR semantics by default, AND requiring every query
 * token, and a tuple first argument searching across several columns at once.
 */
public class SearchTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE lines (play VARCHAR, line VARCHAR)");
        engine.execute("INSERT INTO lines VALUES "
            + "('Hamlet', 'To sleep, perchance to dream'), "
            + "('Macbeth', 'The king is dead')");
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    private long count(final String sql) {
        return ((Number) scalar(sql)).longValue();
    }

    @Test
    public void orModeMatchesAnyToken() {
        assertEquals(Boolean.TRUE, scalar("SELECT SEARCH('the king is dead', 'king')"));
        assertEquals(Boolean.TRUE, scalar("SELECT SEARCH('the king is dead', 'king queen')"));
        assertEquals(Boolean.FALSE, scalar("SELECT SEARCH('the king is dead', 'queen')"));
        assertEquals(Boolean.FALSE, scalar("SELECT SEARCH('kingdom', 'king')"),
            "whole-token matching, not substring");
        assertEquals(Boolean.TRUE, scalar("SELECT SEARCH('The KING is dead', 'king')"),
            "case-insensitive");
    }

    @Test
    public void andModeRequiresEveryToken() {
        assertEquals(Boolean.TRUE, scalar("SELECT SEARCH('the king and queen', 'king queen', SEARCH_MODE => 'AND')"));
        assertEquals(Boolean.FALSE, scalar("SELECT SEARCH('the king is dead', 'king queen', SEARCH_MODE => 'AND')"));
    }

    @Test
    public void analyzerNamedArgumentIsAccepted() {
        assertEquals(Boolean.TRUE, scalar("SELECT SEARCH('the king', 'king', ANALYZER => 'UNICODE_ANALYZER')"));
        assertEquals(Boolean.TRUE,
            scalar("SELECT SEARCH('the king', 'king', ANALYZER => 'UNICODE_ANALYZER', SEARCH_MODE => 'OR')"));
        assertEquals(Boolean.FALSE,
            scalar("SELECT SEARCH('the king', 'king queen', SEARCH_MODE => 'AND', ANALYZER => 'PATTERN_ANALYZER')"));
    }

    @Test
    public void tupleFirstArgumentSearchesAcrossColumns() {
        assertEquals(1, count("SELECT COUNT(*) FROM lines WHERE SEARCH((play, line), 'dream')"));
        assertEquals(1, count("SELECT COUNT(*) FROM lines WHERE SEARCH((play, line), 'macbeth')"),
            "the tuple form matches in EITHER column");
        assertEquals(0, count("SELECT COUNT(*) FROM lines WHERE SEARCH((play, line), 'queen')"));
    }

    @Test
    public void nullDataOrQueryYieldsNull() {
        assertNull(scalar("SELECT SEARCH(NULL, 'king')"));
        assertNull(scalar("SELECT SEARCH('king', NULL)"));
    }
}
