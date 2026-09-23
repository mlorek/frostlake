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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code SHOW VARIABLES [LIKE '<pattern>']} filters the session's variables by NAME, matched
 * case-insensitively and with {@code %} and {@code _} as every other listing's pattern matches
 * (live-verified). A pattern that names nothing answers no row, and there is no IN scope — live refuses
 * {@code IN ACCOUNT} as a syntax error, with or without a pattern.
 */
public class ShowVariablesLikeTest extends BaseDatabaseTest {

    @BeforeEach
    public void setVariables() {
        engine.execute("SET my_var = 5");
        engine.execute("SET other_var = 'x'");
    }

    /** The names the listing answers, in order, joined by a comma. */
    private String names(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final int name = rs.getColumnIndex("name");
        final StringBuilder all = new StringBuilder();
        for (final Row row : rs.getRows()) {
            all.append(all.length() > 0 ? "," : "").append(String.valueOf(row.getValue(name)));
        }
        return all.toString();
    }

    @Test
    public void thePatternFiltersByName() {
        assertEquals("MY_VAR,OTHER_VAR", names("SHOW VARIABLES"));
        assertEquals("MY_VAR", names("SHOW VARIABLES LIKE 'MY_VAR'"));
        assertEquals("MY_VAR", names("SHOW VARIABLES LIKE 'my_var'"), "the pattern matches whatever its case");
        assertEquals("MY_VAR", names("SHOW VARIABLES LIKE 'MY%'"), "and takes the wildcards");
        assertEquals("MY_VAR,OTHER_VAR", names("SHOW VARIABLES LIKE '%VAR'"));
        assertEquals("", names("SHOW VARIABLES LIKE 'NOPE'"), "a pattern naming nothing answers no row");
    }
}
