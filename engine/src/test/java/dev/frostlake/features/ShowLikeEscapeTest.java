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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SHOW's LIKE pattern takes a backslash before an underscore or a percent sign as that character itself. The
 * engine read the backslash as a character to match, so an escaped pattern matched nothing.
 *
 * <p>On the account {@code _} matches any one character and {@code %} any run, a backslash makes either literal,
 * and the pattern is matched whatever its case. A pattern written in a string literal doubles the backslash. Every
 * cell is live-verified.
 */
public class ShowLikeEscapeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE lk_int");
        engine.execute("CREATE STAGE lkxint");
        engine.execute("CREATE TABLE lk_t (a INT)");
        engine.execute("CREATE TABLE lkxt (a INT)");
    }

    /** The names a SHOW lists, in order. */
    private List<String> shown(final String show) {
        final ResultSet rs = engine.executeQuery(show);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(cell(rs, row, "name"));
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> names(final String... names) {
        final List<String> out = new ArrayList<>();
        Collections.addAll(out, names);
        return out;
    }

    @Test
    public void anUnescapedUnderscoreMatchesAnyCharacter() {
        assertEquals(names("LKXINT", "LK_INT"), shown("SHOW STAGES LIKE 'LK_INT' IN SCHEMA test_db.test_schema"));
        assertEquals(names("LKXINT", "LK_INT"), shown("SHOW STAGES LIKE 'LK%' IN SCHEMA test_db.test_schema"));
    }

    @Test
    public void anEscapedUnderscoreMatchesItself() {
        assertEquals(names("LK_INT"), shown("SHOW STAGES LIKE 'LK\\\\_INT' IN SCHEMA test_db.test_schema"));
        assertEquals(names("LK_INT"), shown("SHOW STAGES LIKE 'lk\\\\_int' IN SCHEMA test_db.test_schema"));
        assertEquals(names("LK_INT"), shown("SHOW STAGES LIKE 'LK\\\\_%' IN SCHEMA test_db.test_schema"));
        assertEquals(names("LK_INT"), shown("SHOW STAGES LIKE '%\\\\_INT' IN SCHEMA test_db.test_schema"));
        assertEquals(names("LK_T"), shown("SHOW TABLES LIKE 'LK\\\\_T' IN SCHEMA test_db.test_schema"));
        assertEquals(names("LKXT", "LK_T"), shown("SHOW TABLES LIKE 'LK_T' IN SCHEMA test_db.test_schema"));
    }
}
