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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake multi-pattern matching: {@code x [NOT] LIKE/ILIKE ANY (p1, p2, …)} is TRUE when x matches at
 * least one pattern, {@code LIKE ALL} when it matches every pattern, with OR/AND three-valued NULL
 * semantics; an optional ESCAPE applies to every pattern.
 */
public class LikeAnyAllTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE words (w VARCHAR)");
        engine.execute("INSERT INTO words VALUES ('alpha'), ('beta'), ('gamma'), (NULL)");
    }

    private List<String> matches(final String predicate) {
        final ResultSet result = engine.executeQuery(
            "SELECT w FROM words WHERE " + predicate + " ORDER BY w");
        final List<String> out = new ArrayList<>();
        for (final Row row : result.getRows()) {
            out.add((String) row.getValue(0));
        }
        return out;
    }

    @Test
    public void likeAnyMatchesAtLeastOnePattern() {
        assertEquals(List.of("alpha", "beta"), matches("w LIKE ANY ('al%', '%eta')"));
    }

    @Test
    public void ilikeAnyIsCaseInsensitive() {
        assertEquals(List.of("alpha", "gamma"), matches("w ILIKE ANY ('AL%', 'GA%')"));
    }

    @Test
    public void likeAllRequiresEveryPattern() {
        assertEquals(List.of("alpha"), matches("w LIKE ALL ('a%', '%a', '%lph%')"));
    }

    @Test
    public void notLikeAnyExcludesEveryMatch() {
        assertEquals(List.of("gamma"), matches("w NOT LIKE ANY ('al%', '%eta')"));
    }

    @Test
    public void singleElementListBehavesLikePlainLike() {
        assertEquals(List.of("beta"), matches("w LIKE ANY ('bet_')"));
    }

    @Test
    public void nullSubjectYieldsNullNotFalse() {
        final ResultSet result = engine.executeQuery(
            "SELECT NULL LIKE ANY ('a%', 'b%') AS r");
        assertNull(result.getRows().get(0).getValue(0));
    }

    @Test
    public void nullPatternKeepsTrueWhenAnotherPatternMatches() {
        final ResultSet result = engine.executeQuery(
            "SELECT 'alpha' LIKE ANY (NULL, 'al%') AS r");
        assertEquals(Boolean.TRUE, result.getRows().get(0).getValue(0));
    }

    @Test
    public void escapeAppliesToEveryPattern() {
        engine.execute("CREATE TABLE pct (v VARCHAR)");
        engine.execute("INSERT INTO pct VALUES ('50%'), ('50x')");
        final ResultSet result = engine.executeQuery(
            "SELECT v FROM pct WHERE v LIKE ANY ('50!%', '60!%') ESCAPE '!' ORDER BY v");
        assertEquals(1, result.getRows().size());
        assertEquals("50%", result.getRows().get(0).getValue(0));
    }

    @Test
    public void worksInsideASqlUdfBody() {
        engine.execute(
            """
            CREATE FUNCTION kind_of(w VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS
            $$ SELECT CASE WHEN UPPER(w) ILIKE ANY ('AL%', 'BE%') THEN 'ab' ELSE 'other' END $$
            """);
        final ResultSet result = engine.executeQuery("SELECT kind_of('beta') AS r, kind_of('gamma') AS r2");
        assertEquals("ab", result.getRows().get(0).getValue(0));
        assertEquals("other", result.getRows().get(0).getValue(1));
    }
}
