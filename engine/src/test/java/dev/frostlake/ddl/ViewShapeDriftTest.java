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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A view carries the shape it was created with, and a base table that changes underneath it can put the
 * two out of step. A {@code SELECT *} view whose table lost or gained a column is refused rather than
 * answered at the new width, and a body that no longer compiles reports its place in the CREATE statement
 * that declared the view — not in the body alone, and not in the statement doing the reading.
 */
public class ViewShapeDriftTest extends BaseDatabaseTest {

    /** The message of the refusal a statement raises, newlines flattened. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " | ");
    }

    /** The first row of a result, columns joined by '|'. */
    private String row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        final StringBuilder text = new StringBuilder();
        for (int c = 0; c < rs.getColumnCount(); c++) {
            text.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
        }
        return text.toString();
    }

    /** A star view over a table that lost a column is refused, naming both widths. */
    @Test
    public void aNarrowedStarViewIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE d1 (a INT, b INT, c INT)");
        engine.execute("INSERT INTO d1 VALUES (1, 2, 3)");
        engine.execute("CREATE OR REPLACE VIEW d1_star AS SELECT * FROM d1");
        engine.execute("ALTER TABLE d1 DROP COLUMN b");
        assertTrue(refusal("SELECT * FROM d1_star")
            .contains("declared 3 column(s), but view query produces 2 column(s)."));
    }

    /** The same when the table GAINED one. */
    @Test
    public void aWidenedStarViewIsRefusedToo() {
        engine.execute("CREATE OR REPLACE TABLE d2 (a INT, b INT)");
        engine.execute("INSERT INTO d2 VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE VIEW d2_star AS SELECT * FROM d2");
        engine.execute("ALTER TABLE d2 ADD COLUMN c INT");
        assertTrue(refusal("SELECT * FROM d2_star")
            .contains("declared 2 column(s), but view query produces 3 column(s)."));
    }

    /** A view with a written column list keeps working: its shape did not move. */
    @Test
    public void anExplicitListSurvivesTheDrop() {
        engine.execute("CREATE OR REPLACE TABLE d3 (a INT, b INT, c INT)");
        engine.execute("INSERT INTO d3 VALUES (1, 2, 3)");
        engine.execute("CREATE OR REPLACE VIEW d3_list AS SELECT a, c FROM d3");
        engine.execute("ALTER TABLE d3 DROP COLUMN b");
        assertEquals("1|3", row("SELECT * FROM d3_list"));
    }

    /** A body that no longer compiles is positioned in the CREATE statement that declared the view. */
    @Test
    public void theBodysRefusalIsPlacedInItsCreateStatement() {
        engine.execute("CREATE OR REPLACE TABLE d4 (a INT, b INT, c INT)");
        engine.execute("CREATE OR REPLACE VIEW d4_list AS SELECT a, c FROM d4");
        engine.execute("ALTER TABLE d4 DROP COLUMN c");
        final String message = refusal("SELECT * FROM d4_list");
        assertTrue(message.contains("error line 1 at position 44"), message);
        assertTrue(message.contains("invalid identifier 'C'"), message);
    }

    /** A CREATE written over two lines anchors on its own line and column. */
    @Test
    public void aMultiLineCreateAnchorsWhereItsBodyStands() {
        engine.execute("CREATE OR REPLACE TABLE d5 (a INT, b INT, c INT)");
        engine.execute("CREATE OR REPLACE VIEW d5_multi AS\n  SELECT a, c FROM d5");
        engine.execute("ALTER TABLE d5 DROP COLUMN c");
        final String message = refusal("SELECT * FROM d5_multi");
        assertTrue(message.contains("error line 2 at position 12"), message);
    }
}
