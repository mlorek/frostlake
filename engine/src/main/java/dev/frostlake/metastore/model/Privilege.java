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

package dev.frostlake.metastore.model;

/**
 * Privilege types in Snowflake
 * Based on: https://docs.snowflake.com/en/sql-reference/sql/grant-privilege
 */
public enum Privilege {
    // Global privileges
    CREATE_ACCOUNT,
    CREATE_DATABASE,
    CREATE_INTEGRATION,
    CREATE_NETWORK_POLICY,
    CREATE_ROLE,
    CREATE_USER,
    CREATE_WAREHOUSE,
    APPLY_MASKING_POLICY,
    APPLY_ROW_ACCESS_POLICY,
    APPLY_SESSION_POLICY,
    APPLY_TAG,
    EXECUTE_TASK,
    IMPORT_SHARE,
    MANAGE_GRANTS,
    MONITOR_EXECUTION,
    MONITOR_USAGE,

    // DML privileges (Table, View)
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    TRUNCATE,

    // DDL privileges
    CREATE,
    DROP,
    ALTER,
    MODIFY,

    // Schema-level CREATE privileges
    CREATE_SCHEMA,
    CREATE_TABLE,
    CREATE_VIEW,
    CREATE_STAGE,
    CREATE_FILE_FORMAT,
    CREATE_SEQUENCE,
    CREATE_FUNCTION,
    CREATE_PROCEDURE,
    CREATE_PIPE,
    CREATE_STREAM,
    CREATE_TASK,
    CREATE_MASKING_POLICY,
    CREATE_ROW_ACCESS_POLICY,
    CREATE_TAG,

    // Usage and operational privileges
    USAGE,
    OPERATE,
    MONITOR,
    READ,
    WRITE,

    // Function and procedure privileges
    EXECUTE,

    // Reference privileges
    REFERENCES,

    // Ownership and administration
    OWNERSHIP,
    APPLY,

    // Database privileges
    IMPORTED_PRIVILEGES,

    // Integration privileges
    USE_ANY_ROLE,

    // The rest of what GRANT ALL expands to on a table, a view, a schema, a database, a task or a tag (live-verified)
    ADD_SEARCH_OPTIMIZATION,
    ADD_SEMANTIC_VIEW_MATERIALIZATION,
    APPLYBUDGET,
    CREATE_AGENT,
    CREATE_AGENT_TASK,
    CREATE_AGGREGATION_POLICY,
    CREATE_ALERT,
    CREATE_APPLICATION_SERVICE,
    CREATE_ARTIFACT_REPOSITORY,
    CREATE_AUTHENTICATION_POLICY,
    CREATE_BACKUP_POLICY,
    CREATE_BACKUP_SET,
    CREATE_CONTACT,
    CREATE_CORTEX_EXTENSION,
    CREATE_CORTEX_SEARCH_SERVICE,
    CREATE_DATABASE_ROLE,
    CREATE_DATASET,
    CREATE_DATA_METRIC_FUNCTION,
    CREATE_DATA_MOVEMENT_POLICY,
    CREATE_DATA_MOVEMENT_RULE,
    CREATE_DBT_PROJECT,
    CREATE_DCM_PROJECT,
    CREATE_DYNAMIC_TABLE,
    CREATE_EVENT_TABLE,
    CREATE_EXPERIMENT,
    CREATE_EXTERNAL_AGENT,
    CREATE_EXTERNAL_MCP_SERVER,
    CREATE_EXTERNAL_TABLE,
    CREATE_FEATURE_POLICY,
    CREATE_GATEWAY,
    CREATE_GIT_REPOSITORY,
    CREATE_HYBRID_TABLE,
    CREATE_ICEBERG_TABLE,
    CREATE_IMAGE_REPOSITORY,
    CREATE_INTERACTIVE_TABLE,
    CREATE_JOIN_POLICY,
    CREATE_MAINTENANCE_POLICY,
    CREATE_MANAGED_MCP_SERVER,
    CREATE_MATERIALIZED_VIEW,
    CREATE_MCP_SERVER,
    CREATE_MODEL,
    CREATE_MODEL_MONITOR,
    CREATE_MULTI_PARTY_APPROVAL_POLICY,
    CREATE_NETWORK_RULE,
    CREATE_NOTEBOOK,
    CREATE_NOTEBOOK_PROJECT,
    CREATE_ONLINE_FEATURE_TABLE,
    CREATE_OPENFLOW_CONNECTOR,
    CREATE_OPENFLOW_RUNTIME,
    CREATE_PACKAGES_POLICY,
    CREATE_PASSWORD_POLICY,
    CREATE_PRIVACY_POLICY,
    CREATE_PROJECTION_POLICY,
    CREATE_RESOURCE_GROUP,
    CREATE_RESTRICTED_SESSION_SCOPE,
    CREATE_SECRET,
    CREATE_SEMANTIC_VIEW,
    CREATE_SERVICE,
    CREATE_SERVICE_CLASS,
    CREATE_SESSION_POLICY,
    CREATE_SNAPSHOT,
    CREATE_STORAGE_LIFECYCLE_POLICY,
    CREATE_STREAMLIT,
    CREATE_TEMPORARY_TABLE,
    CREATE_TYPE,
    CREATE_VARIABLE,
    CREATE_WORKSPACE,
    CREATE_ZEROCOPY_CONNECTOR,
    DELETE_ERROR_TABLE,
    EVOLVE_SCHEMA,
    EXECUTE_AUTO_CLASSIFICATION,
    REBUILD,
    SELECT_ERROR_TABLE,
    VIEW_EXPANDED_QUERY_PROFILE,

    // Special
    ALL;

    /**
     * The privilege as SHOW GRANTS and live's refusals spell it, in words: {@code CREATE_TABLE} is {@code CREATE TABLE}.
     *
     * @return the name with its underscores read as spaces
     */
    public String displayName() {
        return name().replace('_', ' ');
    }
}
