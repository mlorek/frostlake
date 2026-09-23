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
 * A column keeps the ORDINAL_POSITION it was given. Dropping one leaves a gap, and the next column added
 * takes one past the highest position the table has ever handed out — even when the dropped column was the
 * last. Only INFORMATION_SCHEMA reports these; {@code SELECT *} order follows the column list itself.
 */
public class ColumnOrdinalPositionTest extends BaseDatabaseTest {

    /** COLUMN_NAME|ORDINAL_POSITION for one table, in position order, rows joined by ';'. */
    private String ordinals(final String table) {
        final ResultSet rs = engine.executeQuery(
            "SELECT column_name, ordinal_position FROM information_schema.columns "
            + "WHERE table_name = '" + table + "' ORDER BY ordinal_position");
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(rs.getValue(0)).append('|').append(rs.getValue(1));
        }
        return text.toString();
    }

    /** A dropped column takes its position with it, and the next one added does not reuse it. */
    @Test
    public void aDroppedColumnLeavesItsPositionBehind() {
        engine.execute("CREATE OR REPLACE TABLE oz (a INT, b INT, c INT)");
        assertEquals("A|1;B|2;C|3", ordinals("OZ"));
        engine.execute("ALTER TABLE oz DROP COLUMN b");
        assertEquals("A|1;C|3", ordinals("OZ"));
        engine.execute("ALTER TABLE oz ADD COLUMN d INT");
        assertEquals("A|1;C|3;D|4", ordinals("OZ"));
    }

    /** Dropping the LAST column spends its number too: the next add goes past it. */
    @Test
    public void theLastColumnsPositionIsNotReusedEither() {
        engine.execute("CREATE OR REPLACE TABLE oy (a INT, b INT, c INT)");
        engine.execute("ALTER TABLE oy DROP COLUMN c");
        engine.execute("ALTER TABLE oy ADD COLUMN d INT");
        assertEquals("A|1;B|2;D|4", ordinals("OY"));
        engine.execute("ALTER TABLE oy DROP COLUMN a");
        engine.execute("ALTER TABLE oy ADD COLUMN e INT");
        assertEquals("B|2;D|4;E|5", ordinals("OY"));
    }

    /** A column re-added under a name just dropped is a NEW column, and takes a new position. */
    @Test
    public void aReaddedNameTakesANewPosition() {
        engine.execute("CREATE OR REPLACE TABLE ox (a INT, b INT)");
        engine.execute("ALTER TABLE ox DROP COLUMN b");
        engine.execute("ALTER TABLE ox ADD COLUMN b INT");
        assertEquals("A|1;B|3", ordinals("OX"));
    }

    /** A rename moves the name, not the position. */
    @Test
    public void aRenameKeepsThePosition() {
        engine.execute("CREATE OR REPLACE TABLE ow (a INT, b INT, c INT)");
        engine.execute("ALTER TABLE ow DROP COLUMN b");
        engine.execute("ALTER TABLE ow RENAME COLUMN c TO k");
        assertEquals("A|1;K|3", ordinals("OW"));
    }

    /** A CLONE keeps the source's positions, gaps and all, and its spent high-water mark. */
    @Test
    public void aCloneKeepsTheSourcesPositions() {
        engine.execute("CREATE OR REPLACE TABLE ou (a INT, b INT, c INT)");
        engine.execute("ALTER TABLE ou DROP COLUMN b");
        engine.execute("CREATE OR REPLACE TABLE ou2 CLONE ou");
        assertEquals("A|1;C|3", ordinals("OU2"));
        engine.execute("ALTER TABLE ou2 ADD COLUMN d INT");
        assertEquals("A|1;C|3;D|4", ordinals("OU2"));
    }

    /** SELECT * still projects the columns in their own order, gap or no gap. */
    @Test
    public void theProjectionOrderIsUnaffected() {
        engine.execute("CREATE OR REPLACE TABLE ov (a INT, b INT, c INT)");
        engine.execute("INSERT INTO ov VALUES (1, 2, 3)");
        engine.execute("ALTER TABLE ov DROP COLUMN b");
        final ResultSet rs = engine.executeQuery("SELECT * FROM ov");
        rs.next();
        assertEquals("1|3", String.valueOf(rs.getValue(0)) + "|" + String.valueOf(rs.getValue(1)));
    }
}
