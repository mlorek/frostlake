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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A column's IDENTITY(start, step) must survive the presence of a table-level PRIMARY KEY — the column
 * rebuild that stamps the PK flag must not reset the identity sequence to (1, 1).
 */
public class IdentityWithPrimaryKeyTest extends BaseDatabaseTest {

    private long id(final ResultSet rs, final int row) {
        return ((Number) rs.getRows().get(row).getValue(0)).longValue();
    }

    @Test
    public void identityStartAndStepSurviveATableLevelPrimaryKey() {
        Assumptions.assumeFalse(isLiveSnowflake(), "an AUTOINCREMENT column defaults to NOORDER on Snowflake, which allocates values in "
            + "BATCHES per statement — a real account answers 100 then 600 (and 1 then 101 for step 1) "
            + "for two single-row INSERTs, and the jump is not deterministic; only an explicit ORDER "
            + "sequence is gapless. Frostlake models the gapless allocation");
        engine.execute("CREATE TABLE ip (id NUMBER IDENTITY(100,5), name STRING, PRIMARY KEY (name))");
        engine.execute("INSERT INTO ip (name) VALUES ('x')");
        engine.execute("INSERT INTO ip (name) VALUES ('y')");
        final ResultSet rs = engine.executeQuery("SELECT id FROM ip ORDER BY id");
        assertEquals(100L, id(rs, 0));
        assertEquals(105L, id(rs, 1));
    }

    @Test
    public void identityStartAndStepWithoutAPrimaryKey() {
        engine.execute("CREATE TABLE ip2 (id NUMBER IDENTITY(100,5), name STRING)");
        engine.execute("INSERT INTO ip2 (name) VALUES ('x')");
        final ResultSet rs = engine.executeQuery("SELECT id FROM ip2");
        assertEquals(100L, id(rs, 0));
    }
}
