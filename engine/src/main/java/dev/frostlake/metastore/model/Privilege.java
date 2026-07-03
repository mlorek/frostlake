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

    // Special
    ALL
}
