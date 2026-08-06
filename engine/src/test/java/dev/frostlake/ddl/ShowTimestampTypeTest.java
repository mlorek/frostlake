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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Every SHOW command's timestamp column is TIMESTAMP_LTZ and carries a temporal VALUE — measured across
 * the whole family on a real account, where {@code created_on} is TIMESTAMPLTZ in all of SHOW TABLES /
 * VIEWS / MATERIALIZED VIEWS / OBJECTS / SEQUENCES / STAGES / FILE FORMATS / STREAMS / TASKS / PIPES /
 * DYNAMIC TABLES / USER FUNCTIONS / PROCEDURES / MASKING POLICIES / ROW ACCESS POLICIES / TAGS /
 * PRIMARY KEYS / DATABASES / SCHEMAS / WAREHOUSES / ROLES / USERS / GRANTS / COMPUTE POOLS, alongside
 * {@code resumed_on}, {@code updated_on}, {@code last_committed_on}, {@code last_suspended_on},
 * {@code expires_at_time}, {@code locked_until_time} and {@code started_on}.
 *
 * <p><b>The three that look temporal and are not.</b> Also measured, and the reason this rule is a
 * declared list rather than a naming convention:
 *
 * <pre>
 *   retention_time   VARCHAR   a number of DAYS, not a moment
 *   granted_on       VARCHAR   in SHOW GRANTS it is the granted object's TYPE ("TABLE")
 *   mins_to_unlock   VARCHAR   as are days_to_expiry and mins_to_bypass_mfa
 * </pre>
 *
 * <p>The declared type and the cell move together: a TIMESTAMP_LTZ column holds a
 * {@link LocalDateTime}, which is what the engine's other TIMESTAMP_LTZ cells carry and what a live
 * account's own TIMESTAMPLTZ arrives as over JDBC. Declaring the type while leaving pre-rendered text
 * in the cell would make the declaration a lie.
 */
public class ShowTimestampTypeTest extends BaseDatabaseTest {

    private static final String HARNESS_ERASES_COLUMN_TYPES =
        "the comparison harness declares every live column VARCHAR";

    @BeforeEach
    public void createOneOfEachKind() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE STAGE stg");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE STREAM strm ON TABLE t");
        engine.execute("CREATE WAREHOUSE wh");
        engine.execute("CREATE TASK tk WAREHOUSE = wh SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE ROLE r");
        engine.execute("CREATE USER u");
    }

    private String typeOf(final ResultSet rs, final String column) {
        return rs.getColumns().get(rs.getColumnIndex(column)).getDataType().getName();
    }

    @Test
    public void everyShowCommandDeclaresItsTimestampColumnsTimestampLtz() {
        Assumptions.assumeFalse(isLiveSnowflake(), HARNESS_ERASES_COLUMN_TYPES);
        for (final String listing : List.of(
                "SHOW TABLES", "SHOW VIEWS", "SHOW OBJECTS", "SHOW SEQUENCES", "SHOW STAGES",
                "SHOW FILE FORMATS", "SHOW STREAMS", "SHOW TASKS", "SHOW SCHEMAS", "SHOW DATABASES",
                "SHOW WAREHOUSES", "SHOW ROLES", "SHOW USERS", "SHOW USER FUNCTIONS",
                "SHOW PROCEDURES", "SHOW TAGS", "SHOW MASKING POLICIES", "SHOW ROW ACCESS POLICIES")) {
            assertEquals("TIMESTAMP_LTZ", typeOf(engine.executeQuery(listing), "created_on"), listing);
        }
        final ResultSet warehouses = engine.executeQuery("SHOW WAREHOUSES");
        assertEquals("TIMESTAMP_LTZ", typeOf(warehouses, "resumed_on"));
        assertEquals("TIMESTAMP_LTZ", typeOf(warehouses, "updated_on"));
        final ResultSet tasks = engine.executeQuery("SHOW TASKS");
        assertEquals("TIMESTAMP_LTZ", typeOf(tasks, "last_committed_on"));
        assertEquals("TIMESTAMP_LTZ", typeOf(tasks, "last_suspended_on"));
        final ResultSet users = engine.executeQuery("SHOW USERS");
        assertEquals("TIMESTAMP_LTZ", typeOf(users, "expires_at_time"));
        assertEquals("TIMESTAMP_LTZ", typeOf(users, "locked_until_time"));
    }

    /** The lookalikes: named like a moment, measured VARCHAR, and they must stay that way. */
    @Test
    public void theColumnsThatOnlyLookTemporalStayText() {
        Assumptions.assumeFalse(isLiveSnowflake(), HARNESS_ERASES_COLUMN_TYPES);
        assertEquals("VARCHAR", typeOf(engine.executeQuery("SHOW TABLES"), "retention_time"));
        assertEquals("VARCHAR", typeOf(engine.executeQuery("SHOW DATABASES"), "retention_time"));
        assertEquals("VARCHAR", typeOf(engine.executeQuery("SHOW SCHEMAS"), "retention_time"));
        assertEquals("VARCHAR", typeOf(engine.executeQuery("SHOW GRANTS TO ROLE r"), "granted_on"));
        assertEquals("VARCHAR", typeOf(engine.executeQuery("SHOW USERS"), "mins_to_unlock"));
        assertEquals("VARCHAR", typeOf(engine.executeQuery("SHOW USERS"), "days_to_expiry"));
    }

    /** The cell follows the declaration: a temporal value, not text that merely looks like one. */
    @Test
    public void theCellIsATemporalValue() {
        final ResultSet tables = engine.executeQuery("SHOW TABLES");
        assertInstanceOf(LocalDateTime.class,
            tables.getRows().get(0).getValue(tables.getColumnIndex("created_on")));
        final ResultSet databases = engine.executeQuery("SHOW DATABASES");
        assertInstanceOf(String.class,
            databases.getRows().get(0).getValue(databases.getColumnIndex("retention_time")));
    }
}
