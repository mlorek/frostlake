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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UPDATE/DELETE with an aliased target must bind the alias for correlated subqueries in WHERE:
 * {@code UPDATE tgt t … WHERE EXISTS (SELECT 1 FROM src s WHERE t.k = s.k)} correlates per target row.
 * Without the alias binding, the alias-qualified outer reference fell back to the INNER table's
 * same-named column, turning the correlation into a tautology — EXISTS matched every row and
 * NOT EXISTS none (the disappeared-row-marking idiom then updated nothing).
 */
public class UpdateDeleteCorrelatedWhereTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tracked (k VARCHAR, sid VARCHAR, st VARCHAR)");
        engine.execute("CREATE TABLE feed (k VARCHAR, sid VARCHAR)");
        engine.execute("INSERT INTO tracked VALUES ('a','s1','CURRENT'), ('a','s2','CURRENT'), ('b','s9','CURRENT')");
        engine.execute("INSERT INTO feed VALUES ('a','s1')");
    }

    private Map<String, String> states() {
        final ResultSet result = engine.executeQuery("SELECT k || '/' || sid AS id, st FROM tracked");
        final Map<String, String> out = new HashMap<>();
        for (final Row row : result.getRows()) {
            out.put((String) row.getValue(0), (String) row.getValue(1));
        }
        return out;
    }

    @Test
    public void updateMarksOnlyRowsMissingFromTheFeed() {
        engine.execute(
            """
            UPDATE tracked t SET t.st = 'DELETED'
            WHERE t.st != 'DELETED'
              AND NOT EXISTS (SELECT 1 FROM feed s WHERE t.k = s.k AND t.sid = s.sid)
            """);
        final Map<String, String> got = states();
        assertEquals("CURRENT", got.get("a/s1"));
        assertEquals("DELETED", got.get("a/s2"));
        assertEquals("DELETED", got.get("b/s9"));
    }

    @Test
    public void updateWithExistsTouchesOnlyCorrelatedRows() {
        engine.execute(
            "UPDATE tracked t SET t.st = 'SEEN' WHERE EXISTS (SELECT 1 FROM feed s WHERE t.k = s.k)");
        final Map<String, String> got = states();
        assertEquals("SEEN", got.get("a/s1"));
        assertEquals("SEEN", got.get("a/s2"));
        assertEquals("CURRENT", got.get("b/s9"));
    }

    @Test
    public void combinedExistsAndNotExistsMatchesTheDisappearedRowOnly() {
        engine.execute("CREATE TABLE scope (k VARCHAR, kind VARCHAR)");
        engine.execute("INSERT INTO scope VALUES ('a', 'plain')");
        engine.execute(
            """
            UPDATE tracked t SET t.st = 'DELETED'
            WHERE t.st != 'DELETED'
              AND EXISTS (SELECT 1 FROM scope s WHERE t.k = s.k AND s.kind != 'excluded')
              AND NOT EXISTS (SELECT 1 FROM feed s WHERE t.k = s.k AND t.sid = s.sid)
            """);
        final Map<String, String> got = states();
        assertEquals("CURRENT", got.get("a/s1"));
        assertEquals("DELETED", got.get("a/s2"));
        assertEquals("CURRENT", got.get("b/s9"));
    }

    @Test
    public void deleteWithCorrelatedNotExistsRemovesOnlyMissingRows() {
        engine.execute(
            "DELETE FROM tracked t WHERE NOT EXISTS (SELECT 1 FROM feed s WHERE t.k = s.k AND t.sid = s.sid)");
        final ResultSet result = engine.executeQuery("SELECT k, sid FROM tracked");
        assertEquals(1, result.getRows().size());
        assertEquals("a", result.getRows().get(0).getValue(0));
        assertEquals("s1", result.getRows().get(0).getValue(1));
    }
}
