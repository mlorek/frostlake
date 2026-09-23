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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * DESCRIBE TABLE, VIEW, MATERIALIZED VIEW and DYNAMIC TABLE all describe whatever relation the name
 * reaches — the four kinds describe each other freely, with the columns or, under {@code TYPE = STAGE},
 * the stage properties — and the keyword is spoken only when the name reaches nothing: a missing dynamic
 * table is named in full, the other three bare. Frostlake kept the dynamic tables apart in both
 * directions (live-verified).
 */
public class DescribeRelationKindTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        engine.execute("CREATE VIEW v1 AS SELECT a FROM t1");
        engine.execute("CREATE MATERIALIZED VIEW mv1 AS SELECT a FROM t1");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM t1");
    }

    /** The first column of each row, rows joined by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "").append(result.getRows().get(r).getValue(0));
        }
        return text.toString();
    }

    /** The result's column names, joined by commas. */
    private String names(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int c = 0; c < result.getColumnCount(); c++) {
            text.append(c > 0 ? ", " : "").append(result.getColumns().get(c).getName());
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void everyRelationKeywordDescribesTheRelationTheNameReaches() {
        assertEquals("A", rows("DESCRIBE DYNAMIC TABLE t1"));
        assertEquals("A", rows("DESCRIBE DYNAMIC TABLE v1"));
        assertEquals("A", rows("DESCRIBE DYNAMIC TABLE mv1"));
        assertEquals("A", rows("DESCRIBE DYNAMIC TABLE dt"));
        assertEquals("A", rows("DESCRIBE TABLE dt"));
        assertEquals("A", rows("DESCRIBE VIEW dt"));
        assertEquals("A", rows("DESCRIBE MATERIALIZED VIEW dt"));
        assertEquals("A", rows("DESC DYNAMIC TABLE t1"));
        // The shape is DESCRIBE's own, whichever keyword named it.
        assertEquals("name, type, kind, null?, default, primary key, unique key, check, expression, comment,"
            + " policy name, privacy domain, write default", names("DESCRIBE DYNAMIC TABLE t1"));
        assertEquals("A", rows("DESCRIBE TABLE dt TYPE = COLUMNS"));
        assertEquals("A", rows("DESCRIBE DYNAMIC TABLE t1 TYPE = COLUMNS"));
    }

    @Test
    public void theStagePropertiesAreTheRelationsWhateverKindItIs() {
        assertTrue(rows("DESCRIBE DYNAMIC TABLE t1 TYPE = STAGE").startsWith("STAGE_FILE_FORMAT"));
        assertTrue(rows("DESCRIBE DYNAMIC TABLE dt TYPE = STAGE").startsWith("STAGE_FILE_FORMAT"));
        assertTrue(rows("DESCRIBE TABLE dt TYPE = STAGE").startsWith("STAGE_FILE_FORMAT"));
        assertTrue(rows("DESCRIBE VIEW t1 TYPE = STAGE").startsWith("STAGE_FILE_FORMAT"));
        assertEquals("parent_property, property, property_type, property_value, property_default",
            names("DESCRIBE DYNAMIC TABLE dt TYPE = STAGE"));
    }

    @Test
    public void theKeywordIsSpokenOnlyWhenTheNameReachesNothing() {
        assertEquals(hinted("SQL compilation error:\nTable 'NOSUCH' does not exist or not authorized."),
            refusal("DESCRIBE TABLE nosuch"));
        assertEquals(hinted("SQL compilation error:\nView 'NOSUCH' does not exist or not authorized."),
            refusal("DESCRIBE VIEW nosuch"));
        assertEquals(hinted("SQL compilation error:\nMaterialized view 'NOSUCH' does not exist or not authorized."),
            refusal("DESCRIBE MATERIALIZED VIEW nosuch"));
        // A missing dynamic table is named in full, where the other three are named bare.
        assertEquals(hinted("SQL compilation error:\n"
            + "Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            refusal("DESCRIBE DYNAMIC TABLE nosuch"));
        assertEquals(hinted("SQL compilation error:\n"
            + "Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            refusal("DESCRIBE DYNAMIC TABLE nosuch TYPE = STAGE"));
    }
}
