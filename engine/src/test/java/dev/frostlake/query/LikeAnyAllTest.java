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
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Snowflake multi-pattern matching, live-verified: {@code x LIKE ANY (p1, p2, …)} is TRUE when x
 * matches at least one pattern, {@code LIKE ALL} when it matches every pattern, and {@code ILIKE
 * ANY} is the case-insensitive form. NULL patterns are SKIPPED (not three-valued): {@code 'a' LIKE
 * ALL ('a', NULL)} is TRUE and {@code 'a' LIKE ANY ('b', NULL)} is FALSE; only a NULL subject or an
 * all-NULL pattern list yields NULL. {@code NOT LIKE ANY/ALL} and {@code ILIKE ALL} do not exist in
 * Snowflake (compile errors) and are rejected here too. An optional ESCAPE applies to every pattern.
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

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
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
    public void singleElementListBehavesLikePlainLike() {
        assertEquals(List.of("beta"), matches("w LIKE ANY ('bet_')"));
    }

    @Test
    public void nullSubjectYieldsNullNotFalse() {
        assertNull(scalar("SELECT NULL LIKE ANY ('a%', 'b%') AS r"));
    }

    @Test
    public void nullPatternsAreSkippedNotThreeValued() {
        // Live-verified matrix: a NULL pattern is ignored — it neither matches nor poisons the
        // result the way three-valued OR/AND would.
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY ('a', NULL) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a' LIKE ANY ('b', NULL) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ALL ('a', NULL) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a' LIKE ALL ('b', NULL) AS r"));
    }

    @Test
    public void allNullPatternListYieldsNull() {
        assertNull(scalar("SELECT 'a' LIKE ANY (NULL) AS r"));
        assertNull(scalar("SELECT 'a' LIKE ALL (NULL, NULL) AS r"));
    }

    @Test
    public void numericSubjectCoercesToText() {
        assertEquals(Boolean.TRUE, scalar("SELECT 1 LIKE ANY ('1') AS r"));
    }

    @Test
    public void notLikeAnyAndIlikeAllAreRejectedLikeSnowflake() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'a' NOT LIKE ANY ('a', 'b')");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'A' ILIKE ALL ('a%')");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'a' LIKE SOME ('a', 'b')");
            }
        });
    }

    @Test
    public void escapeAppliesToEveryPattern() {
        engine.execute("CREATE TABLE pct (v VARCHAR)");
        engine.execute("INSERT INTO pct VALUES ('50%'), ('50x')");
        final ResultSet result = engine.executeQuery(
            "SELECT v FROM pct WHERE v LIKE ANY ('50!%', '60!%') ESCAPE '!' ORDER BY v");
        assertEquals(1, result.getRows().size());
        assertEquals("50%", result.getRows().get(0).getValue(0));
        assertEquals(Boolean.FALSE, scalar("SELECT 'axb' LIKE ANY ('a!_b') ESCAPE '!' AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a_b' LIKE ANY ('a!_b') ESCAPE '!' AS r"));
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
