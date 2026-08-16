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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER DYNAMIC TABLE takes a target lag, a warehouse, a comment and the two retention settings, set or
 * unset, several at a time with or without commas; the refresh mode, INITIALIZE, CHANGE_TRACKING and any
 * name the account does not know are refused as invalid properties before the table is looked up, even
 * with no current database. Every cell is live-verified, over a warehouse created suspended.
 */
public class AlterDynamicTablePropertyTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** Measured property by property, with a current database. */
    @Test
    public void setTakesOnlyTheAlterableProperties() {
        try {
            engine.execute("CREATE OR REPLACE WAREHOUSE P445_WH WITH WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
            engine.execute("CREATE OR REPLACE DATABASE P445_DB");
            engine.execute("CREATE OR REPLACE TABLE P445_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE DYNAMIC TABLE P445_DB.PUBLIC.DT TARGET_LAG = '1 day' WAREHOUSE = P445_WH INITIALIZE = ON_SCHEDULE AS SELECT x FROM P445_DB.PUBLIC.T");
            engine.execute("ALTER DYNAMIC TABLE P445_DB.PUBLIC.DT SUSPEND");
            assertRefused("ALTER DYNAMIC TABLE nosuchdt SET REFRESH_MODE = FULL",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET REFRESH_MODE = FULL",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET REFRESH_MODE = AUTO",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET REFRESH_MODE = INCREMENTAL",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            engine.execute("ALTER DYNAMIC TABLE DT SET TARGET_LAG = '2 days'");
            engine.execute("ALTER DYNAMIC TABLE DT SET WAREHOUSE = P445_WH");
            assertRefused("ALTER DYNAMIC TABLE DT SET INITIALIZE = ON_SCHEDULE",
                "invalid property 'INITIALIZE' for 'DYNAMIC_TABLE'");
            engine.execute("ALTER DYNAMIC TABLE DT SET COMMENT = 'c'");
            engine.execute("ALTER DYNAMIC TABLE DT SET DATA_RETENTION_TIME_IN_DAYS = 1");
            engine.execute("ALTER DYNAMIC TABLE DT SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 1");
            engine.execute("ALTER DYNAMIC TABLE DT SET TARGET_LAG = DOWNSTREAM");
            engine.execute("ALTER DYNAMIC TABLE DT UNSET COMMENT");
            assertRefused("ALTER DYNAMIC TABLE DT SET REFRESH_MODE = NOSUCH",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT UNSET REFRESH_MODE",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET INITIALIZE = ON_CREATE",
                "invalid property 'INITIALIZE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE nosuchdt SET TARGET_LAG = '2 days'",
                "Dynamic table 'P445_DB.PUBLIC.NOSUCHDT' does not exist or not authorized.");
            assertRefused("ALTER DYNAMIC TABLE DT SET REFRESH_MODE = FULL, TARGET_LAG = '3 days'",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET TARGET_LAG = '3 days' REFRESH_MODE = FULL",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET CHANGE_TRACKING = TRUE",
                "invalid property 'CHANGE_TRACKING' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET NOSUCHPROP = 1",
                "invalid property 'NOSUCHPROP' for 'DYNAMIC_TABLE'");
            engine.execute("SHOW DYNAMIC TABLES LIKE 'DT' IN SCHEMA P445_DB.PUBLIC");
            assertEquals("DOWNSTREAM, INCREMENTAL, ",
                rows("SELECT \"target_lag\", \"refresh_mode\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P445_DB");
            engine.execute("DROP WAREHOUSE IF EXISTS P445_WH");
        }
    }

    /** With none, a property the account refuses is still the refusal. */
    @Test
    public void withNoCurrentDatabaseAnUntakeablePropertyIsStillNamedFirst() {
        try {
            engine.execute("CREATE OR REPLACE WAREHOUSE P445_WH WITH WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
            engine.execute("CREATE OR REPLACE DATABASE P445_DB");
            engine.execute("CREATE OR REPLACE TABLE P445_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE DYNAMIC TABLE P445_DB.PUBLIC.DT TARGET_LAG = '1 day' WAREHOUSE = P445_WH INITIALIZE = ON_SCHEDULE AS SELECT x FROM P445_DB.PUBLIC.T");
            engine.execute("ALTER DYNAMIC TABLE P445_DB.PUBLIC.DT SUSPEND");
            engine.execute("CREATE OR REPLACE DATABASE P445_IDLE");
            engine.execute("DROP DATABASE P445_IDLE");
            assertRefused("ALTER DYNAMIC TABLE dt SET REFRESH_MODE = FULL",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE dt SET TARGET_LAG = '2 days'",
                "Cannot perform ALTER DYNAMIC TABLE OPERATE PROPERTY. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("ALTER DYNAMIC TABLE PUBLIC.dt SET REFRESH_MODE = FULL",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P445_DB");
            engine.execute("DROP WAREHOUSE IF EXISTS P445_WH");
        }
    }

    /** Measured property by property, with a current database. */
    @Test
    public void unsetAndSeveralSettingsFollowTheSameRule() {
        try {
            engine.execute("CREATE OR REPLACE WAREHOUSE P445_WH WITH WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
            engine.execute("CREATE OR REPLACE DATABASE P445B_DB");
            engine.execute("CREATE OR REPLACE TABLE P445B_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE DYNAMIC TABLE P445B_DB.PUBLIC.DT TARGET_LAG = '1 day' WAREHOUSE = P445_WH INITIALIZE = ON_SCHEDULE AS SELECT x FROM P445B_DB.PUBLIC.T");
            engine.execute("ALTER DYNAMIC TABLE P445B_DB.PUBLIC.DT SUSPEND");
            assertRefused("ALTER DYNAMIC TABLE DT UNSET TARGET_LAG",
                "invalid value 'null' for property 'TARGET_LAG'");
            assertRefused("ALTER DYNAMIC TABLE DT UNSET WAREHOUSE",
                "SQL compilation error: cannot unset property 'WAREHOUSE' for 'DT'");
            engine.execute("ALTER DYNAMIC TABLE DT UNSET DATA_RETENTION_TIME_IN_DAYS");
            engine.execute("ALTER DYNAMIC TABLE DT UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS");
            assertRefused("ALTER DYNAMIC TABLE DT UNSET INITIALIZE",
                "invalid property 'INITIALIZE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT UNSET NOSUCHPROP",
                "invalid property 'NOSUCHPROP' for 'DYNAMIC_TABLE'");
            engine.execute("ALTER DYNAMIC TABLE DT UNSET COMMENT, DATA_RETENTION_TIME_IN_DAYS");
            assertRefused("ALTER DYNAMIC TABLE DT SET TARGET_LAG = '1 day', INITIALIZE = ON_SCHEDULE",
                "invalid property 'INITIALIZE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET INITIALIZE = ON_SCHEDULE, REFRESH_MODE = FULL",
                "invalid property 'INITIALIZE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET TARGET_LAG = 'nonsense'",
                "invalid value 'nonsense' for property 'TARGET_LAG'");
            engine.execute("ALTER DYNAMIC TABLE DT SET COMMENT = 'x' TARGET_LAG = '2 days'");
            assertRefused("ALTER DYNAMIC TABLE nosuchdt UNSET COMMENT",
                "Dynamic table 'P445B_DB.PUBLIC.NOSUCHDT' does not exist or not authorized.");
            assertRefused("ALTER DYNAMIC TABLE nosuchdt UNSET REFRESH_MODE",
                "invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'");
            assertRefused("ALTER DYNAMIC TABLE DT SET WAREHOUSE = nosuchwh",
                "Warehouse 'NOSUCHWH' does not exist.");
            engine.execute("ALTER DYNAMIC TABLE DT SET COMMENT = 'x', COMMENT = 'y'");
            engine.execute("SHOW DYNAMIC TABLES LIKE 'DT' IN SCHEMA P445B_DB.PUBLIC");
            assertEquals("2 days, y",
                rows("SELECT \"target_lag\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P445B_DB");
            engine.execute("DROP WAREHOUSE IF EXISTS P445_WH");
        }
    }

    /** With none, a property the account refuses is still the refusal. */
    @Test
    public void withNoCurrentDatabaseACommentIsAPlainAlter() {
        try {
            engine.execute("CREATE OR REPLACE WAREHOUSE P445_WH WITH WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
            engine.execute("CREATE OR REPLACE DATABASE P445B_DB");
            engine.execute("CREATE OR REPLACE TABLE P445B_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE DYNAMIC TABLE P445B_DB.PUBLIC.DT TARGET_LAG = '1 day' WAREHOUSE = P445_WH INITIALIZE = ON_SCHEDULE AS SELECT x FROM P445B_DB.PUBLIC.T");
            engine.execute("ALTER DYNAMIC TABLE P445B_DB.PUBLIC.DT SUSPEND");
            engine.execute("CREATE OR REPLACE DATABASE P445B_IDLE");
            engine.execute("DROP DATABASE P445B_IDLE");
            assertRefused("ALTER DYNAMIC TABLE dt SET COMMENT = 'x'",
                "Cannot perform ALTER. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("ALTER DYNAMIC TABLE dt UNSET COMMENT",
                "Cannot perform ALTER. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("ALTER DYNAMIC TABLE PUBLIC.dt SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "Cannot perform ALTER. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("ALTER DYNAMIC TABLE dt SUSPEND",
                "Cannot perform ALTER DYNAMIC TABLE OPERATE PROPERTY. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P445B_DB");
            engine.execute("DROP WAREHOUSE IF EXISTS P445_WH");
        }
    }
}
