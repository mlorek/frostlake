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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How INFORMATION_SCHEMA and SHOW VIEWS describe a view (live-verified): TABLES leaves a view's
 * transience and retention empty — INFORMATION_SCHEMA's own views too — while a materialized view keeps a
 * retention; LAST_DDL is a TIMESTAMP_LTZ(3) like CREATED; VIEW_DEFINITION holds the CREATE statement
 * without the view's COMMENT clause, and SHOW VIEWS' text re-prints that clause as {@code comment = '…'}.
 */
public class InformationSchemaViewRowsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE T (x INT)");
        engine.execute("INSERT INTO T VALUES (1), (2)");
        engine.execute("CREATE VIEW UV COMMENT = 'uv' AS SELECT 1 AS x");
        engine.execute("CREATE VIEW UV2 AS SELECT x FROM T");
        engine.execute("CREATE SECURE VIEW SV COMMENT='sv' AS SELECT x FROM T");
    }

    private String row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String listedText(final String view) {
        engine.executeQuery("SHOW VIEWS LIKE '" + view + "' IN SCHEMA test_db.test_schema");
        return row("SELECT \"text\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void aViewHasNoTransienceAndNoRetention() {
        assertEquals("SV, VIEW, null, null | T, BASE TABLE, NO, 1 | UV, VIEW, null, null | UV2, VIEW, null, null",
            row("SELECT TABLE_NAME, TABLE_TYPE, IS_TRANSIENT, RETENTION_TIME FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' ORDER BY TABLE_NAME"));
        assertEquals("TABLES, VIEW, null, null | VIEWS, VIEW, null, null",
            row("SELECT TABLE_NAME, TABLE_TYPE, IS_TRANSIENT, RETENTION_TIME FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME IN ('TABLES', 'VIEWS') ORDER BY TABLE_NAME"));
    }

    @Test
    public void aMaterializedViewKeepsARetention() {
        engine.execute("CREATE MATERIALIZED VIEW MV AS SELECT x FROM T");
        assertEquals("MATERIALIZED VIEW, null, 1",
            row("SELECT TABLE_TYPE, IS_TRANSIENT, RETENTION_TIME FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' AND TABLE_NAME = 'MV'"));
    }

    @Test
    public void lastDdlIsATimestamp() {
        assertEquals("TIMESTAMP_LTZ(3)[SB8], TIMESTAMP_LTZ(3)[SB8]",
            row("SELECT SYSTEM$TYPEOF(LAST_DDL), SYSTEM$TYPEOF(CREATED) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' AND TABLE_NAME = 'UV'"));
        assertEquals("TIMESTAMP_LTZ(3)[SB8], TIMESTAMP_LTZ(3)[SB8]",
            row("SELECT SYSTEM$TYPEOF(LAST_DDL), SYSTEM$TYPEOF(CREATED) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' AND TABLE_NAME = 'T'"));
        assertEquals("TIMESTAMP_LTZ(3)[SB8]",
            row("SELECT SYSTEM$TYPEOF(LAST_DDL) FROM INFORMATION_SCHEMA.VIEWS "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' AND TABLE_NAME = 'UV'"));
    }

    @Test
    public void theDefinitionLeavesTheCommentClauseOut() {
        assertEquals("SV, CREATE SECURE VIEW SV AS SELECT x FROM T, sv | UV, CREATE VIEW UV AS SELECT 1 AS x, uv"
                + " | UV2, CREATE VIEW UV2 AS SELECT x FROM T, null",
            row("SELECT TABLE_NAME, VIEW_DEFINITION, COMMENT FROM INFORMATION_SCHEMA.VIEWS "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' ORDER BY TABLE_NAME"));
        engine.execute("CREATE OR REPLACE VIEW UV4 (c COMMENT 'cc') COMMENT = 'v4' AS SELECT 1 AS x");
        engine.execute("CREATE VIEW UV7\n  COMMENT = 'nl'\n  AS\n  SELECT 1 AS x");
        assertEquals("CREATE OR REPLACE VIEW UV4 (c COMMENT 'cc') AS SELECT 1 AS x | CREATE VIEW UV7\n  \n  AS\n  SELECT 1 AS x",
            row("SELECT VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS "
                + "WHERE TABLE_SCHEMA = 'TEST_SCHEMA' AND TABLE_NAME IN ('UV4', 'UV7') ORDER BY TABLE_NAME"));
    }

    @Test
    public void showViewsReprintsTheCommentClause() {
        assertEquals("CREATE VIEW UV comment = 'uv' AS SELECT 1 AS x", listedText("UV"));
        assertEquals("CREATE SECURE VIEW SV comment = 'sv' AS SELECT x FROM T", listedText("SV"));
        assertEquals("CREATE VIEW UV2 AS SELECT x FROM T", listedText("UV2"));
        engine.execute("create view uv6 comment='lower' as select 1 as x");
        assertEquals("create view uv6 comment = 'lower' as select 1 as x", listedText("UV6"));
        engine.execute("CREATE VIEW UV7\n  COMMENT = 'nl'\n  AS\n  SELECT 1 AS x");
        assertEquals("CREATE VIEW UV7\n  comment = 'nl' \n  AS\n  SELECT 1 AS x", listedText("UV7"));
    }
}
