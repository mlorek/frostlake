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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code LS} is the account's abbreviation of {@code LIST}, as {@code RM} is of {@code REMOVE}: it lists a
 * stage's files, takes the same PATTERN, and reads in either case. The word stays usable as a NAME, so a
 * column called {@code ls} still resolves (live-verified).
 */
public class ListAbbreviationTest extends BaseDatabaseTest {

    /** The listing's columns, joined — or the refusal on one line. */
    private String shape(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            for (int c = 0; c < rs.getColumns().size(); c++) {
                all.append(c > 0 ? "," : "").append(rs.getColumns().get(c).getName());
            }
            return all + " = " + rs.getRowCount() + " rows";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void lsListsAStageAsListDoes() {
        engine.execute("CREATE OR REPLACE STAGE st");
        final String listed = shape("LIST @st");
        assertEquals(listed, shape("LS @st"), "LS is LIST");
        assertEquals(listed, shape("ls @st"), "in either case");
        assertEquals(listed, shape("LS @st PATTERN = '.*'"), "and with the same PATTERN");
    }

    @Test
    public void theWordIsStillAName() {
        assertEquals("LS = 1 rows", shape("SELECT 1 AS ls"));
        assertEquals("LS = 1 rows", shape("SELECT ls FROM (SELECT 1 AS ls)"));
    }
}
