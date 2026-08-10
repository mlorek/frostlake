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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every SHOW listing emits the column layout a real account emits — same names, same order.
 * Callers read these listings back through {@code TABLE(RESULT_SCAN(LAST_QUERY_ID()))} and select
 * columns by name, so a missing or reordered column breaks scripts that never display the output.
 *
 * <p>SHOW spells its values differently from INFORMATION_SCHEMA: booleans are {@code Y}/{@code N}
 * (but lowercase {@code true}/{@code false} for a handful of columns, and {@code OFF} for the
 * toggles), and an absent text value is the empty string rather than NULL. NULL is reserved for
 * columns that do not structurally apply to the row.
 */
public class ShowCommandLayoutTest extends BaseDatabaseTest {

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    private Object cell(final ResultSet rs, final int rowIndex, final String columnName) {
        final List<ResultSetColumn> columns = rs.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columnName.equals(columns.get(i).getName())) {
                return rs.getRows().get(rowIndex).getValue(i);
            }
        }
        throw new IllegalArgumentException("No such column: " + columnName);
    }

    private Row rowNamed(final ResultSet rs, final String name) {
        final List<ResultSetColumn> columns = rs.getColumns();
        int nameIndex = -1;
        for (int i = 0; i < columns.size(); i++) {
            if ("name".equals(columns.get(i).getName())) {
                nameIndex = i;
            }
        }
        assertTrue(nameIndex >= 0, "listing has no name column");
        for (final Row row : rs.getRows()) {
            if (name.equalsIgnoreCase(String.valueOf(row.getValue(nameIndex)))) {
                return row;
            }
        }
        return null;
    }

    @Test
    public void showObjectsCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE obj_probe (k INTEGER)");
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "kind", "comment", "cluster_by",
            "rows", "bytes", "owner", "retention_time", "owner_role_type", "is_hybrid",
            "is_dynamic", "is_iceberg", "is_interactive"), columnNames(rs));
    }

    /** SHOW OBJECTS lists tables and views. Sequences, streams and tags belong to their own listings. */
    @Test
    public void showObjectsListsOnlyTablesAndViews() {
        engine.execute("CREATE TABLE only_t (k INTEGER)");
        engine.execute("CREATE VIEW only_v AS SELECT * FROM only_t");
        engine.execute("CREATE SEQUENCE only_s START 1 INCREMENT 1");
        engine.execute("CREATE STREAM only_str ON TABLE only_t");
        engine.execute("CREATE TAG only_tag");
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        assertNotNull(rowNamed(rs, "ONLY_T"));
        assertNotNull(rowNamed(rs, "ONLY_V"));
        assertNull(rowNamed(rs, "ONLY_S"));
        assertNull(rowNamed(rs, "ONLY_STR"));
        assertNull(rowNamed(rs, "ONLY_TAG"));
    }

    /** A transient table is a plain TABLE in SHOW OBJECTS, even though SHOW TABLES calls it TRANSIENT. */
    @Test
    public void showObjectsReportsTransientTablesAsTable() {
        engine.execute("CREATE TRANSIENT TABLE tr_probe (k INTEGER)");
        final ResultSet objects = engine.executeQuery("SHOW OBJECTS");
        final Row objectRow = rowNamed(objects, "TR_PROBE");
        assertNotNull(objectRow);
        assertEquals("TABLE", objectRow.getValue(columnNames(objects).indexOf("kind")));

        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'TR_PROBE'");
        assertEquals("TRANSIENT", cell(tables, 0, "kind"));
    }

    @Test
    public void showSchemasCarriesLiveColumnLayout() {
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS");
        assertEquals(List.of(
            "created_on", "name", "is_default", "is_current", "database_name", "owner", "comment",
            "options", "retention_time", "owner_role_type", "classification_profile_database",
            "classification_profile_schema", "classification_profile", "object_visibility",
            "is_nested"),
            columnNames(rs));
        assertEquals("1", cell(rs, 0, "retention_time"));
        assertNull(cell(rs, 0, "object_visibility"));
        // Spelled as the text false — not the Y/N convention of the flags beside it.
        assertEquals("false", cell(rs, 0, "is_nested"));

        // An owned schema names its role; INFORMATION_SCHEMA, which nobody owns, leaves BOTH the
        // owner and its role type empty rather than naming a type for an absent owner.
        final Row owned = rowNamed(rs, "TEST_SCHEMA");
        assertNotNull(owned);
        assertEquals("ROLE", owned.getValue(columnNames(rs).indexOf("owner_role_type")));
        assertFalse(String.valueOf(owned.getValue(columnNames(rs).indexOf("owner"))).isEmpty());

        final Row unowned = rowNamed(rs, "INFORMATION_SCHEMA");
        assertNotNull(unowned);
        assertEquals("", unowned.getValue(columnNames(rs).indexOf("owner")));
        assertEquals("", unowned.getValue(columnNames(rs).indexOf("owner_role_type")));
    }

    @Test
    public void showDatabasesCarriesLiveColumnLayout() {
        final ResultSet rs = engine.executeQuery("SHOW DATABASES");
        assertEquals(List.of(
            "created_on", "name", "is_default", "is_current", "origin", "owner", "comment",
            "options", "retention_time", "kind", "owner_role_type", "object_visibility",
            "data_quality_monitoring_settings", "resharing_settings"), columnNames(rs));
        // The row for the database this test KNOWS about, never row 0: SHOW DATABASES is
        // account-wide and its first row is whichever name sorts first. On a real account that is a
        // database nobody here made, and its kind is its own — live carries STANDARD, APPLICATION,
        // IMPORTED DATABASE and PERSONAL DATABASE side by side. An ordinary database is STANDARD.
        final Row own = rowNamed(rs, "TEST_DB");
        assertNotNull(own);
        assertEquals("STANDARD", own.getValue(columnNames(rs).indexOf("kind")));
        assertEquals("ROLE", own.getValue(columnNames(rs).indexOf("owner_role_type")));
    }

    @Test
    public void showViewsCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE view_src (k INTEGER)");
        engine.execute("CREATE VIEW view_probe AS SELECT * FROM view_src");
        final ResultSet rs = engine.executeQuery("SHOW VIEWS LIKE 'VIEW_PROBE'");
        assertEquals(List.of(
            "created_on", "name", "reserved", "database_name", "schema_name", "owner", "comment",
            "text", "is_secure", "is_materialized", "owner_role_type", "change_tracking"),
            columnNames(rs));
        // These three are spelled as lowercase words, not as the Y / N the rest of SHOW uses.
        assertEquals("false", cell(rs, 0, "is_secure"));
        assertEquals("false", cell(rs, 0, "is_materialized"));
        assertEquals("OFF", cell(rs, 0, "change_tracking"));
    }

    @Test
    public void showSequencesCarriesLiveColumnLayout() {
        engine.execute("CREATE SEQUENCE seq_probe START 1 INCREMENT 1");
        final ResultSet rs = engine.executeQuery("SHOW SEQUENCES");
        assertEquals(List.of(
            "name", "database_name", "schema_name", "next_value", "interval", "created_on",
            "owner", "comment", "owner_role_type", "ordered"), columnNames(rs));
        assertNotNull(cell(rs, 0, "created_on"));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
        assertEquals("N", cell(rs, 0, "ordered"));
    }

    @Test
    public void showStagesCarriesLiveColumnLayout() {
        engine.execute("CREATE STAGE stage_probe");
        final ResultSet rs = engine.executeQuery("SHOW STAGES");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "url", "has_credentials",
            "has_encryption_key", "owner", "comment", "region", "type", "cloud",
            "notification_channel", "storage_integration", "endpoint", "owner_role_type",
            "directory_enabled"), columnNames(rs));
        // SHOW spells the stage kind as a bare word; INFORMATION_SCHEMA.STAGES says "Internal Named".
        assertEquals("INTERNAL", cell(rs, 0, "type"));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
    }

    @Test
    public void showFileFormatsCarriesLiveColumnLayout() {
        engine.execute("CREATE FILE FORMAT ff_probe TYPE = CSV COMMENT = 'ff comment'");
        final ResultSet rs = engine.executeQuery("SHOW FILE FORMATS");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "type", "owner", "comment",
            "format_options", "owner_role_type"), columnNames(rs));
        assertNotNull(cell(rs, 0, "created_on"));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
        assertTrue(String.valueOf(cell(rs, 0, "format_options")).contains("\"TYPE\":\"CSV\""));
    }

    /** COMMENT on CREATE FILE FORMAT describes the object; it is not one of the format's options. */
    @Test
    public void fileFormatCommentIsTheObjectCommentNotAnOption() {
        engine.execute("CREATE FILE FORMAT ff_comment TYPE = CSV COMMENT = 'describes the format'");
        final ResultSet rs = engine.executeQuery("SHOW FILE FORMATS LIKE 'FF_COMMENT'");
        assertEquals("describes the format", cell(rs, 0, "comment"));
        assertTrue(!String.valueOf(cell(rs, 0, "format_options")).contains("COMMENT"));
    }

    @Test
    public void showStreamsCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE stream_src (k INTEGER)");
        engine.execute("CREATE STREAM stream_probe ON TABLE stream_src");
        final ResultSet rs = engine.executeQuery("SHOW STREAMS");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "owner", "comment", "table_name",
            "source_type", "base_tables", "type", "stale", "mode", "stale_after", "invalid_reason",
            "owner_role_type"), columnNames(rs));
        assertEquals("DELTA", cell(rs, 0, "type"));
        assertEquals("Table", cell(rs, 0, "source_type"));
        assertEquals("false", cell(rs, 0, "stale"));
        assertEquals("N/A", cell(rs, 0, "invalid_reason"));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
        // Live qualifies the source with its database and schema even inside the current schema.
        assertEquals("TEST_DB.TEST_SCHEMA.STREAM_SRC", cell(rs, 0, "table_name"));
        assertEquals("TEST_DB.TEST_SCHEMA.STREAM_SRC", cell(rs, 0, "base_tables"));
    }

    /** A stream on a table turns that table's change tracking on, which SHOW TABLES reports. */
    @Test
    public void aStreamTurnsOnChangeTrackingForItsSourceTable() {
        engine.execute("CREATE TABLE ct_untracked (k INTEGER)");
        engine.execute("CREATE TABLE ct_tracked (k INTEGER)");
        engine.execute("CREATE STREAM ct_stream ON TABLE ct_tracked");
        assertEquals("OFF", cell(engine.executeQuery("SHOW TABLES LIKE 'CT_UNTRACKED'"), 0, "change_tracking"));
        assertEquals("ON", cell(engine.executeQuery("SHOW TABLES LIKE 'CT_TRACKED'"), 0, "change_tracking"));
    }

    @Test
    public void showTagsCarriesLiveColumnLayout() {
        engine.execute("CREATE TAG tag_probe");
        final ResultSet rs = engine.executeQuery("SHOW TAGS");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "owner", "comment",
            "allowed_values", "owner_role_type", "propagate", "on_conflict", "multi_value"),
            columnNames(rs));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
        assertEquals("NONE", cell(rs, 0, "propagate"));
        assertEquals("false", cell(rs, 0, "multi_value"));
        assertNull(cell(rs, 0, "on_conflict"));
    }

    @Test
    public void showPipesCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE pipe_target (k INTEGER)");
        engine.execute("CREATE STAGE pipe_stage");
        engine.execute("CREATE PIPE pipe_probe AS COPY INTO pipe_target FROM @pipe_stage"
            + " FILE_FORMAT = (TYPE = CSV)");
        final ResultSet rs = engine.executeQuery("SHOW PIPES");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "definition", "owner",
            "notification_channel", "comment", "integration", "pattern", "error_integration",
            "owner_role_type", "invalid_reason", "kind", "is_snowflake_managed"), columnNames(rs));
        assertEquals("STAGE", cell(rs, 0, "kind"));
        assertEquals("false", cell(rs, 0, "is_snowflake_managed"));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
    }

    @Test
    public void showRolesCarriesLiveColumnLayout() {
        final ResultSet rs = engine.executeQuery("SHOW ROLES");
        assertEquals(List.of(
            "created_on", "name", "is_default", "is_current", "is_inherited", "assigned_to_users",
            "granted_to_roles", "granted_roles", "owner", "comment",
            "is_from_organization_user_group"), columnNames(rs));
        final Row accountAdmin = rowNamed(rs, "ACCOUNTADMIN");
        assertNotNull(accountAdmin);
        final List<String> names = columnNames(rs);
        assertEquals("Account administrator can manage all aspects of the account.",
            accountAdmin.getValue(names.indexOf("comment")));
        assertEquals("N", accountAdmin.getValue(names.indexOf("is_from_organization_user_group")));
    }

    /** An absent comment reads back as the empty string in SHOW, where INFORMATION_SCHEMA gives NULL. */
    @Test
    public void absentTextValuesAreEmptyStringsNotNull() {
        engine.execute("CREATE TABLE no_comment (k INTEGER)");
        engine.execute("CREATE SEQUENCE no_comment_seq START 1 INCREMENT 1");
        assertEquals("", cell(engine.executeQuery("SHOW TABLES LIKE 'NO_COMMENT'"), 0, "comment"));
        assertEquals("", cell(engine.executeQuery("SHOW OBJECTS"), 0, "comment"));
        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertEquals("", cell(sequences, 0, "comment"));
    }

    /**
     * A clustering key reads back as LINEAR(...), the form live emits, and keeps the key expression
     * exactly as written — live does not case-fold it the way it folds object names.
     */
    @Test
    public void clusteringKeysRenderAsLinear() {
        engine.execute("CREATE TABLE clustered_probe (a INTEGER, b INTEGER) CLUSTER BY (a, b)");
        engine.execute("CREATE TABLE unclustered_probe (a INTEGER)");
        final ResultSet clustered = engine.executeQuery("SHOW TABLES LIKE 'CLUSTERED_PROBE'");
        assertEquals("LINEAR(a, b)", cell(clustered, 0, "cluster_by"));
        assertEquals("ON", cell(clustered, 0, "automatic_clustering"));
        final ResultSet unclustered = engine.executeQuery("SHOW TABLES LIKE 'UNCLUSTERED_PROBE'");
        assertEquals("", cell(unclustered, 0, "cluster_by"));
        assertEquals("OFF", cell(unclustered, 0, "automatic_clustering"));
    }

    private static final List<String> TASK_COLUMNS = List.of(
        "created_on", "name", "id", "database_name", "schema_name", "owner", "comment", "warehouse",
        "schedule", "predecessors", "state", "definition", "condition",
        "allow_overlapping_execution", "error_integration", "last_committed_on", "last_suspended_on",
        "owner_role_type", "config", "task_relations", "last_suspended_reason", "success_integration",
        "scheduling_mode", "target_completion_interval", "execute_as_user", "overlap_policy",
        "created_by_user");

    @Test
    public void showTasksCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE task_sink (k INTEGER)");
        engine.execute("CREATE TASK task_probe WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE'"
            + " COMMENT = 'tc' ALLOW_OVERLAPPING_EXECUTION = TRUE WHEN 1 = 1"
            + " AS INSERT INTO task_sink VALUES (1)");
        final ResultSet rs = engine.executeQuery("SHOW TASKS");
        assertEquals(TASK_COLUMNS, columnNames(rs));
        assertEquals("suspended", cell(rs, 0, "state"));
        assertEquals("true", cell(rs, 0, "allow_overlapping_execution"));
        assertEquals("ALLOW_CHILD_OVERLAP", cell(rs, 0, "overlap_policy"));
        assertEquals("ROLE", cell(rs, 0, "owner_role_type"));
        assertNotNull(cell(rs, 0, "id"));
        assertEquals("{\"Predecessors\":[]}", cell(rs, 0, "task_relations"));
    }

    /** DESCRIBE TASK returns the task's SHOW TASKS row — live gives the two commands one shape. */
    @Test
    public void describeTaskReturnsTheShowTasksRow() {
        engine.execute("CREATE TABLE desc_sink (k INTEGER)");
        engine.execute("CREATE TASK desc_probe SCHEDULE = '60 MINUTE' AS INSERT INTO desc_sink VALUES (1)");
        final ResultSet rs = engine.executeQuery("DESCRIBE TASK desc_probe");
        assertEquals(TASK_COLUMNS, columnNames(rs));
        assertEquals(1, rs.getRows().size());
        assertEquals("DESC_PROBE", cell(rs, 0, "name"));
    }

    /**
     * A task's own settings do not appear in SHOW TASKS at all — live reports them through
     * SHOW PARAMETERS IN TASK, whose level column separates a value the task set from a default.
     */
    @Test
    public void taskParametersAreReadThroughShowParameters() {
        engine.execute("CREATE TABLE param_sink (k INTEGER)");
        engine.execute("CREATE TASK param_probe SCHEDULE = '60 MINUTE'"
            + " USER_TASK_TIMEOUT_MS = 120000 SUSPEND_TASK_AFTER_NUM_FAILURES = 3"
            + " AS INSERT INTO param_sink VALUES (1)");
        final List<String> taskColumns = columnNames(engine.executeQuery("SHOW TASKS"));
        assertTrue(!taskColumns.contains("user_task_timeout_ms"));
        assertTrue(!taskColumns.contains("suspend_task_after_num_failures"));

        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS IN TASK param_probe");
        assertEquals(List.of("key", "value", "default", "level", "description", "type"),
            columnNames(rs));
        final Row timeout = rowKeyed(rs, "USER_TASK_TIMEOUT_MS");
        assertNotNull(timeout);
        final List<String> names = columnNames(rs);
        assertEquals("120000", timeout.getValue(names.indexOf("value")));
        assertEquals("3600000", timeout.getValue(names.indexOf("default")));
        assertEquals("TASK", timeout.getValue(names.indexOf("level")));
        assertEquals("NUMBER", timeout.getValue(names.indexOf("type")));

        // A parameter the task left alone reports its default and a blank level.
        final Row retries = rowKeyed(rs, "TASK_AUTO_RETRY_ATTEMPTS");
        assertNotNull(retries);
        assertEquals("0", retries.getValue(names.indexOf("value")));
        assertEquals("", retries.getValue(names.indexOf("level")));
    }

    /** A child task has no schedule of its own, and live prints the text "null" for its overlap cell. */
    @Test
    public void aChildTaskReportsItsPredecessorAndNoOverlapSetting() {
        engine.execute("CREATE TABLE child_sink (k INTEGER)");
        engine.execute("CREATE TASK parent_probe SCHEDULE = '60 MINUTE' AS INSERT INTO child_sink VALUES (1)");
        engine.execute("CREATE TASK child_probe AFTER parent_probe AS INSERT INTO child_sink VALUES (2)");
        final ResultSet rs = engine.executeQuery("SHOW TASKS");
        final Row child = rowNamed(rs, "CHILD_PROBE");
        assertNotNull(child);
        final List<String> names = columnNames(rs);
        assertEquals("null", child.getValue(names.indexOf("allow_overlapping_execution")));
        assertNull(child.getValue(names.indexOf("overlap_policy")));
        assertNull(child.getValue(names.indexOf("schedule")));
        // Live qualifies a predecessor with its database and schema.
        assertEquals("[\"TEST_DB.TEST_SCHEMA.PARENT_PROBE\"]",
            String.valueOf(child.getValue(names.indexOf("predecessors"))));
        assertEquals("{\"Predecessors\":[\"TEST_DB.TEST_SCHEMA.PARENT_PROBE\"]}",
            child.getValue(names.indexOf("task_relations")));
    }

    /** The row whose {@code key} cell matches, or null when the listing has none. */
    private Row rowKeyed(final ResultSet rs, final String key) {
        final int keyIndex = columnNames(rs).indexOf("key");
        for (final Row row : rs.getRows()) {
            if (key.equalsIgnoreCase(String.valueOf(row.getValue(keyIndex)))) {
                return row;
            }
        }
        return null;
    }

    @Test
    public void showUsersCarriesLiveColumnLayout() {
        engine.execute("CREATE USER user_probe COMMENT = 'uc' DEFAULT_ROLE = PUBLIC");
        final ResultSet rs = engine.executeQuery("SHOW USERS");
        assertEquals(List.of(
            "name", "created_on", "login_name", "display_name", "first_name", "last_name", "email",
            "mins_to_unlock", "days_to_expiry", "comment", "disabled", "must_change_password",
            "snowflake_lock", "default_warehouse", "default_namespace", "default_role",
            "default_secondary_roles", "ext_authn_duo", "ext_authn_uid", "mins_to_bypass_mfa",
            "owner", "last_success_login", "expires_at_time", "locked_until_time", "has_password",
            "has_rsa_public_key", "type", "has_mfa", "has_pat", "has_workload_identity",
            "is_from_organization_user"), columnNames(rs));
        final Row probe = rowNamed(rs, "USER_PROBE");
        assertNotNull(probe);
        final List<String> names = columnNames(rs);
        // SHOW USERS spells its flags as lower-case words, unlike the Y / N most SHOW output uses.
        assertEquals("false", probe.getValue(names.indexOf("disabled")));
        assertEquals("false", probe.getValue(names.indexOf("must_change_password")));
        assertEquals("PERSON", probe.getValue(names.indexOf("type")));
        assertEquals("[\"ALL\"]", probe.getValue(names.indexOf("default_secondary_roles")));
        // An unset login or display name falls back to the user's own name.
        assertEquals("USER_PROBE", probe.getValue(names.indexOf("login_name")));
        assertEquals("USER_PROBE", probe.getValue(names.indexOf("display_name")));
    }

    /** CREATE USER takes the full property list, and every property reaches SHOW USERS. */
    @Test
    public void userPropertiesRoundTripThroughShowUsers() {
        // TYPE stays PERSON here: a service user may carry none of the personal properties below,
        // so pairing them with TYPE = SERVICE is rejected rather than round-tripped.
        engine.execute("CREATE USER prop_probe"
            + " LOGIN_NAME = 'probe_login' DISPLAY_NAME = 'Probe Display'"
            + " FIRST_NAME = 'First' LAST_NAME = 'Last' EMAIL = 'probe@example.com'"
            + " DEFAULT_NAMESPACE = test_db.test_schema DEFAULT_SECONDARY_ROLES = ('ALL')"
            + " MUST_CHANGE_PASSWORD = TRUE DISABLED = TRUE COMMENT = 'pc'");
        final ResultSet rs = engine.executeQuery("SHOW USERS");
        final Row probe = rowNamed(rs, "PROP_PROBE");
        assertNotNull(probe);
        final List<String> names = columnNames(rs);
        // A login name is upper-cased however it is written; the display name beside it is not.
        assertEquals("PROBE_LOGIN", probe.getValue(names.indexOf("login_name")));
        assertEquals("Probe Display", probe.getValue(names.indexOf("display_name")));
        assertEquals("First", probe.getValue(names.indexOf("first_name")));
        assertEquals("Last", probe.getValue(names.indexOf("last_name")));
        assertEquals("probe@example.com", probe.getValue(names.indexOf("email")));
        assertEquals("TEST_DB.TEST_SCHEMA", probe.getValue(names.indexOf("default_namespace")));
        assertEquals("true", probe.getValue(names.indexOf("must_change_password")));
        assertEquals("true", probe.getValue(names.indexOf("disabled")));
        assertEquals("PERSON", probe.getValue(names.indexOf("type")));
        assertEquals("pc", probe.getValue(names.indexOf("comment")));
    }

    /**
     * A service user carries no personal details: FIRST_NAME, LAST_NAME and MUST_CHANGE_PASSWORD are
     * refused on one, at CREATE and at ALTER alike, whichever order the properties are written in.
     * EMAIL, DISPLAY_NAME, COMMENT, DISABLED and DEFAULT_ROLE stay allowed.
     */
    @Test
    public void aServiceUserRefusesThePersonalProperties() {
        engine.execute("CREATE USER svc_probe TYPE = SERVICE");
        for (final String property : List.of("FIRST_NAME = 'F'", "LAST_NAME = 'L'",
                "MUST_CHANGE_PASSWORD = TRUE")) {
            final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("ALTER USER svc_probe SET " + property);
                }
            });
            assertEquals("SQL execution error: Cannot set " + property.substring(0, property.indexOf(' '))
                + " on users with TYPE=SERVICE.", e.getMessage());
        }
        // The properties a service user may still carry.
        engine.execute("ALTER USER svc_probe SET EMAIL = 'svc@example.com' DISPLAY_NAME = 'Svc'"
            + " COMMENT = 'c' DISABLED = TRUE");

        // Rejected at CREATE too, with TYPE written on either side of the personal property.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE USER svc_probe2 TYPE = SERVICE FIRST_NAME = 'F'");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE USER svc_probe3 FIRST_NAME = 'F' TYPE = SERVICE");
            }
        });
    }

    /**
     * MIDDLE_NAME is a real user property: CREATE and ALTER … SET both take it, and DESCRIBE reports
     * it between FIRST_NAME and LAST_NAME. SHOW USERS has no column for it — a real account lists
     * display_name, first_name and last_name only — so the listing is deliberately unchanged.
     */
    @Test
    public void middleNameRoundTripsThroughDescribe() {
        engine.execute("CREATE USER mid_probe FIRST_NAME = 'First' MIDDLE_NAME = 'Mid'"
            + " LAST_NAME = 'Last'");
        assertEquals("Mid", describeUserProperty("MIDDLE_NAME"));
        assertEquals("First", describeUserProperty("FIRST_NAME"));
        assertEquals("Last", describeUserProperty("LAST_NAME"));

        engine.execute("ALTER USER mid_probe SET MIDDLE_NAME = 'Changed'");
        assertEquals("Changed", describeUserProperty("MIDDLE_NAME"));

        // No middle_name column in the listing, matching a real account.
        final ResultSet users = engine.executeQuery("SHOW USERS");
        assertFalse(columnNames(users).contains("middle_name"),
            "SHOW USERS should carry no middle_name column");
    }

    /** A user created without one reports it absent, not as an empty string. */
    @Test
    public void anAbsentMiddleNameIsReportedNull() {
        engine.execute("CREATE USER mid_probe");
        assertEquals("null", describeUserProperty("MIDDLE_NAME"));
    }

    /** A service user carries no middle name either. */
    @Test
    public void aServiceUserRefusesAMiddleName() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE USER mid_svc TYPE = SERVICE MIDDLE_NAME = 'M'");
            }
        });
        assertEquals("SQL execution error: Cannot set MIDDLE_NAME on users with TYPE=SERVICE.",
            e.getMessage());
    }

    /**
     * ALTER USER … UNSET takes every property CREATE does, as a comma list, and what unsetting MEANS
     * is per property: most go back to nothing, LOGIN_NAME reverts to the user's own name, the two
     * booleans go false and TYPE goes back to PERSON.
     */
    @Test
    public void unsetRestoresEachPropertyItsOwnWay() {
        engine.execute("CREATE USER mid_probe LOGIN_NAME = 'ln' DISPLAY_NAME = 'dn'"
            + " FIRST_NAME = 'F' MIDDLE_NAME = 'M' LAST_NAME = 'L' EMAIL = 'e@x.com'"
            + " MUST_CHANGE_PASSWORD = TRUE DISABLED = TRUE COMMENT = 'c'");
        engine.execute("ALTER USER mid_probe UNSET LOGIN_NAME, DISPLAY_NAME, FIRST_NAME,"
            + " MIDDLE_NAME, LAST_NAME, EMAIL, MUST_CHANGE_PASSWORD, DISABLED, COMMENT, TYPE");

        // LOGIN_NAME goes back to the user's own name, not to nothing.
        assertEquals("MID_PROBE", describeUserProperty("LOGIN_NAME"));
        assertEquals("PERSON", describeUserProperty("TYPE"));
        assertEquals("false", describeUserProperty("MUST_CHANGE_PASSWORD"));
        assertEquals("false", describeUserProperty("DISABLED"));
        for (final String cleared : List.of("DISPLAY_NAME", "FIRST_NAME", "MIDDLE_NAME",
                "LAST_NAME", "EMAIL", "COMMENT")) {
            assertEquals("null", describeUserProperty(cleared), cleared + " should be cleared");
        }
    }

    /** Unsetting something that was never set is fine. */
    @Test
    public void unsetIsIdempotent() {
        engine.execute("CREATE USER mid_probe");
        engine.execute("ALTER USER mid_probe UNSET COMMENT");
        engine.execute("ALTER USER mid_probe UNSET COMMENT");
        assertEquals("null", describeUserProperty("COMMENT"));
    }

    /** An unknown or repeated property is refused, and the whole list is left unapplied. */
    @Test
    public void unsetRejectsUnknownAndDuplicateProperties() {
        engine.execute("CREATE USER mid_probe COMMENT = 'keep me'");

        final RuntimeException unknown = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER USER mid_probe UNSET COMMENT, ROBOT");
            }
        });
        assertTrue(unknown.getMessage().contains("invalid property 'ROBOT' for 'USER'"),
            "got: " + unknown.getMessage());

        final RuntimeException duplicate = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER USER mid_probe UNSET COMMENT, COMMENT");
            }
        });
        assertTrue(duplicate.getMessage().contains("duplicate property 'COMMENT';"),
            "got: " + duplicate.getMessage());

        // Neither statement applied anything.
        assertEquals("keep me", describeUserProperty("COMMENT"));
    }

    private String describeUserProperty(final String property) {
        final ResultSet rs = engine.executeQuery("DESCRIBE USER mid_probe");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (property.equals(rs.getRows().get(i).getValue(0))) {
                return String.valueOf(rs.getRows().get(i).getValue(1));
            }
        }
        throw new AssertionError("no property " + property);
    }

    /** The guard is on the assignment, not the resulting state: a person with a name can become one. */
    @Test
    public void anExistingPersonCanBecomeAServiceUser() {
        engine.execute("CREATE USER becomes_svc FIRST_NAME = 'F'");
        engine.execute("ALTER USER becomes_svc SET TYPE = SERVICE");
        final ResultSet rs = engine.executeQuery("SHOW USERS");
        final Row row = rowNamed(rs, "BECOMES_SVC");
        assertNotNull(row);
        assertEquals("SERVICE", row.getValue(columnNames(rs).indexOf("type")));
    }

    /** ALTER USER … SET takes the same property list CREATE USER does. */
    @Test
    public void alterUserSetsTheSameProperties() {
        engine.execute("CREATE USER alter_probe");
        engine.execute("ALTER USER alter_probe SET EMAIL = 'new@example.com' DISABLED = TRUE");
        final ResultSet rs = engine.executeQuery("SHOW USERS");
        final Row probe = rowNamed(rs, "ALTER_PROBE");
        assertNotNull(probe);
        final List<String> names = columnNames(rs);
        assertEquals("new@example.com", probe.getValue(names.indexOf("email")));
        assertEquals("true", probe.getValue(names.indexOf("disabled")));
    }

    @Test
    public void showWarehousesCarriesLiveColumnLayout() {
        engine.execute("CREATE WAREHOUSE wh_probe WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60"
            + " INITIALLY_SUSPENDED = TRUE COMMENT = 'wc'");
        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES");
        assertEquals(List.of(
            "name", "state", "type", "size", "min_cluster_count", "max_cluster_count",
            "started_clusters", "running", "queued", "is_default", "is_current", "auto_suspend",
            "auto_resume", "available", "provisioning", "quiescing", "other", "created_on",
            "resumed_on", "updated_on", "owner", "comment", "enable_query_acceleration",
            "query_acceleration_max_scale_factor", "resource_monitor", "actives", "pendings",
            "failed", "suspended", "uuid", "scaling_policy", "owner_role_type",
            "resource_constraint", "generation", "query_throughput_multiplier",
            "max_query_performance_level", "disabled_reasons", "tables"), columnNames(rs));
        final Row probe = rowNamed(rs, "WH_PROBE");
        assertNotNull(probe);
        final List<String> names = columnNames(rs);
        // Live spells the size the way CREATE WAREHOUSE displays it, not as the bare keyword.
        assertEquals("X-Small", probe.getValue(names.indexOf("size")));
        assertEquals("SUSPENDED", probe.getValue(names.indexOf("state")));
        assertEquals("ROLE", probe.getValue(names.indexOf("owner_role_type")));
        assertEquals("STANDARD_GEN_2", probe.getValue(names.indexOf("resource_constraint")));
        assertEquals("2", probe.getValue(names.indexOf("generation")));
        assertEquals("null", probe.getValue(names.indexOf("resource_monitor")));
        // The cluster-health cells are empty strings on an idle warehouse, not NULL.
        assertEquals("", probe.getValue(names.indexOf("available")));
        assertEquals("", probe.getValue(names.indexOf("quiescing")));
    }

    /**
     * A warehouse's own settings are not SHOW WAREHOUSES columns — live reports them through
     * SHOW PARAMETERS IN WAREHOUSE, with the same level convention tasks use.
     */
    @Test
    public void warehouseParametersAreReadThroughShowParameters() {
        engine.execute("CREATE WAREHOUSE param_wh WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE"
            + " MAX_CONCURRENCY_LEVEL = 4 STATEMENT_TIMEOUT_IN_SECONDS = 300");
        final List<String> warehouseColumns = columnNames(engine.executeQuery("SHOW WAREHOUSES"));
        assertTrue(!warehouseColumns.contains("max_concurrency_level"));
        assertTrue(!warehouseColumns.contains("statement_timeout_in_seconds"));

        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS IN WAREHOUSE param_wh");
        final List<String> names = columnNames(rs);
        final Row concurrency = rowKeyed(rs, "MAX_CONCURRENCY_LEVEL");
        assertNotNull(concurrency);
        assertEquals("4", concurrency.getValue(names.indexOf("value")));
        assertEquals("8", concurrency.getValue(names.indexOf("default")));
        assertEquals("WAREHOUSE", concurrency.getValue(names.indexOf("level")));

        final Row queued = rowKeyed(rs, "STATEMENT_QUEUED_TIMEOUT_IN_SECONDS");
        assertNotNull(queued);
        assertEquals("0", queued.getValue(names.indexOf("value")));
        assertEquals("", queued.getValue(names.indexOf("level")));

        // Live's default statement timeout is two days, not zero.
        final Row timeout = rowKeyed(rs, "STATEMENT_TIMEOUT_IN_SECONDS");
        assertNotNull(timeout);
        assertEquals("300", timeout.getValue(names.indexOf("value")));
        assertEquals("172800", timeout.getValue(names.indexOf("default")));
    }

    @Test
    public void showGrantsOnCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE grant_probe (k INTEGER)");
        engine.execute("CREATE ROLE grant_role");
        engine.execute("GRANT SELECT ON TABLE grant_probe TO ROLE grant_role");
        final ResultSet rs = engine.executeQuery("SHOW GRANTS ON TABLE grant_probe");
        assertEquals(List.of(
            "created_on", "privilege", "granted_on", "name", "granted_to", "grantee_name",
            "grant_option", "granted_by", "granted_by_role_type"), columnNames(rs));
        // The object is named in full here, not by the bare name the statement used.
        assertEquals("TEST_DB.TEST_SCHEMA.GRANT_PROBE", cell(rs, 0, "name"));
        assertEquals("ROLE", cell(rs, 0, "granted_by_role_type"));
    }

    /** SHOW GRANTS TO ROLE keeps its own eight columns — no granted_by_role_type. */
    @Test
    public void showGrantsToRoleCarriesLiveColumnLayout() {
        engine.execute("CREATE ROLE to_role_probe");
        assertEquals(List.of(
            "created_on", "privilege", "granted_on", "name", "granted_to", "grantee_name",
            "grant_option", "granted_by"),
            columnNames(engine.executeQuery("SHOW GRANTS TO ROLE to_role_probe")));
    }

    /** SHOW GRANTS OF ROLE lists who holds the role, in its own narrower shape. */
    @Test
    public void showGrantsOfRoleListsTheRoleHolders() {
        engine.execute("CREATE ROLE held_role");
        engine.execute("CREATE USER holder_user");
        engine.execute("GRANT ROLE held_role TO USER holder_user");
        final ResultSet rs = engine.executeQuery("SHOW GRANTS OF ROLE held_role");
        assertEquals(List.of("created_on", "role", "granted_to", "grantee_name", "granted_by"),
            columnNames(rs));
        // This listing names the ROLE rather than an object, so it has no name column at all.
        assertEquals(1, rs.getRows().size());
        final Row holder = rs.getRows().get(0);
        assertEquals("HELD_ROLE", holder.getValue(columnNames(rs).indexOf("role")));
        assertEquals("USER", holder.getValue(columnNames(rs).indexOf("granted_to")));
        assertEquals("HOLDER_USER", holder.getValue(columnNames(rs).indexOf("grantee_name")));
    }

    /** Bare SHOW GRANTS carries a role column the other grants listings do not. */
    @Test
    public void bareShowGrantsCarriesLiveColumnLayout() {
        assertEquals(List.of(
            "created_on", "privilege", "granted_on", "name", "role", "granted_to", "grantee_name",
            "grant_option", "granted_by"), columnNames(engine.executeQuery("SHOW GRANTS")));
    }

    /** DESCRIBE PIPE returns the pipe's SHOW PIPES row, as DESCRIBE TASK does for tasks. */
    @Test
    public void describePipeReturnsTheShowPipesRow() {
        engine.execute("CREATE TABLE pipe_desc_target (k INTEGER)");
        engine.execute("CREATE STAGE pipe_desc_stage");
        engine.execute("CREATE PIPE pipe_desc AS COPY INTO pipe_desc_target"
            + " FROM @pipe_desc_stage FILE_FORMAT = (TYPE = CSV)");
        final ResultSet rs = engine.executeQuery("DESCRIBE PIPE pipe_desc");
        assertEquals(List.of(
            "created_on", "name", "database_name", "schema_name", "definition", "owner",
            "notification_channel", "comment", "integration", "pattern", "error_integration",
            "owner_role_type", "invalid_reason", "kind", "is_snowflake_managed"), columnNames(rs));
        assertEquals(1, rs.getRows().size());
        assertEquals("PIPE_DESC", cell(rs, 0, "name"));
    }

    /** DESCRIBE WAREHOUSE names the warehouse; its settings live in the other two listings. */
    @Test
    public void describeWarehouseReturnsIdentityOnly() {
        engine.execute("CREATE WAREHOUSE desc_wh WAREHOUSE_SIZE = XSMALL");
        final ResultSet rs = engine.executeQuery("DESCRIBE WAREHOUSE desc_wh");
        assertEquals(List.of("created_on", "name", "kind"), columnNames(rs));
        assertEquals("DESC_WH", cell(rs, 0, "name"));
        assertEquals("WAREHOUSE", cell(rs, 0, "kind"));
    }

    /** DESCRIBE USER is a property listing, with the default and description columns live returns. */
    @Test
    public void describeUserCarriesLiveColumnLayout() {
        engine.execute("CREATE USER desc_user EMAIL = 'desc@example.com'");
        final ResultSet rs = engine.executeQuery("DESCRIBE USER desc_user");
        assertEquals(List.of("property", "value", "default", "description"), columnNames(rs));
        assertEquals("desc@example.com", userProperty(rs, "EMAIL"));
    }

    /** Live lists 44 properties, in this order, whether or not the user set any of them. */
    @Test
    public void describeUserListsEveryPropertyLiveLists() {
        engine.execute("CREATE USER desc_all_user");
        final ResultSet rs = engine.executeQuery("DESCRIBE USER desc_all_user");
        assertEquals(44, rs.getRows().size());
        final List<String> names = new ArrayList<>();
        final int propertyIndex = columnNames(rs).indexOf("property");
        for (final Row row : rs.getRows()) {
            names.add(String.valueOf(row.getValue(propertyIndex)));
        }
        assertEquals("NAME", names.get(0));
        assertEquals("COMMENT", names.get(1));
        assertEquals("LOCK_DETAILS", names.get(names.size() - 1));
        assertTrue(names.contains("RSA_PUBLIC_KEY_2_LAST_SET_TIME"));
        // Sits mid-list (after PASSWORD_LAST_SET_TIME), not appended at the end.
        assertEquals("MINS_TO_BYPASS_SESSION_POLICY", names.get(38));
        assertEquals("null", userProperty(rs, "MINS_TO_BYPASS_SESSION_POLICY"));

        // Both cells spell "nothing here" as the text null, not as a SQL NULL.
        assertEquals("null", userProperty(rs, "FIRST_NAME"));
        assertEquals("null", userProperty(rs, "PASSWORD"), "password material is never returned");
        assertEquals("false", userProperty(rs, "DISABLED"));
        assertEquals("[\"ALL\"]", userProperty(rs, "DEFAULT_SECONDARY_ROLES"));
    }

    /** The {@code value} cell of a DESCRIBE USER property. */
    private String userProperty(final ResultSet rs, final String property) {
        final int propertyIndex = columnNames(rs).indexOf("property");
        final int valueIndex = columnNames(rs).indexOf("value");
        for (final Row row : rs.getRows()) {
            if (property.equals(row.getValue(propertyIndex))) {
                return String.valueOf(row.getValue(valueIndex));
            }
        }
        throw new IllegalArgumentException("No such property: " + property);
    }

    /** TERSE is a no-op for warehouses: live returns the full listing either way. */
    @Test
    public void showTerseWarehousesReturnsTheFullListing() {
        engine.execute("CREATE WAREHOUSE terse_wh WAREHOUSE_SIZE = XSMALL");
        assertEquals(columnNames(engine.executeQuery("SHOW WAREHOUSES")),
            columnNames(engine.executeQuery("SHOW TERSE WAREHOUSES")));
    }
}
