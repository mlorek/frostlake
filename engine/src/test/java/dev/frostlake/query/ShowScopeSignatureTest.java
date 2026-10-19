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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A parenthesized list after the name a SHOW's IN names. The account reads a signature there — a list of types
 * or names, empty too — after a bare name, after the name of a database, a schema, a table, a view, a class, an
 * application and the account, and then looks the scope up as if it were not written: SHOW TABLES IN test_schema
 * (x) lists the schema's tables and SHOW TABLES IN nosuch (x) misses the scope in the listing's own words. A
 * faulty signature is refused as a DROP's is, token by token; nothing but the listing's own modifiers follows
 * the list, and neither an instance's name nor the lone word ACCOUNT takes one. The word IDENTIFIER before a
 * list that opens no reference is the scope's own name (live-verified).
 */
public class ShowScopeSignatureTest extends MeasuredCellsTestSupport {

    @Test
    public void aSignatureIsReadAndIgnored() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES IN test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES IN test_db.test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES IN SCHEMA test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES IN DATABASE test_db (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216), is_external_function:VARCHAR(16777216), "
                + "language:VARCHAR(16777216), is_memoizable:VARCHAR(16777216), is_data_metric:VARCHAR(16777216), "
                + "is_ai_function:VARCHAR(16777216)",
            "SHOW FUNCTIONS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 ()");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (x, y)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (NUMBER)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (VARCHAR(10))");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (x.y)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (\"x\")");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (ARRAY)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (INT, VARCHAR)");
    }

    @Test
    public void aFaultySignature() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "SHOW TABLES IN nosuch_zq7 (x y)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'.",
            "SHOW TABLES IN nosuch_zq7 (x,)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ')'.",
            "SHOW TABLES IN nosuch_zq7 (TABLE)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SHOW TABLES IN nosuch_zq7 (1)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 ((x))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 (x) (y)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SHOW TABLES IN nosuch_zq7 (x) y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '.'.",
            "SHOW TABLES IN nosuch_zq7 (x).y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SHOW TABLES IN nosuch_zq7 (");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SHOW TABLES IN nosuch_zq7 (x");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW TABLES IN nosuch_zq7 (x) LIMIT 0");
        assertShape(
            "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
            "SHOW TABLES IN nosuch_zq7 (x) WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 x (y)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'.",
            "SHOW TABLES IN nosuch_zq7 )");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "SHOW TABLES IN nosuch_zq7 (a b)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 ((a), b)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SHOW TABLES IN nosuch_zq7 (1, x)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''a''.",
            "SHOW TABLES IN nosuch_zq7 ('a')");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 (VARCHAR((1)))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'd'.",
            "SHOW TABLES IN nosuch_zq7 (a.b.c.d)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SHOW TABLES IN nosuch_zq7 (ARRAY(1))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "SHOW TABLES IN nosuch_zq7 (x) x y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SHOW TABLES IN nosuch_zq7 (1) LIMIT 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 ((x)) y");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (TABLE(a))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'b'.",
            "SHOW TABLES IN nosuch_zq7 (TABLE(a), b)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (DEFAULT x)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "SHOW TABLES IN nosuch_zq7 (DEFAULT)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TRUE'.",
            "SHOW TABLES IN nosuch_zq7 (TRUE)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (a(b, c(1)))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'.",
            "SHOW TABLES IN nosuch_zq7 (MAP(INT, 1))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'.",
            "SHOW TABLES IN nosuch_zq7 (a, 1)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "SHOW TABLES IN nosuch_zq7 (a) LIMIT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'.",
            "SHOW TABLES IN nosuch_zq7 (a) WITH");
    }

    @Test
    public void afterEveryKindOfName() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN DATASET (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN USER (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN MATERIALIZED (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN a.b (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN \"nosuch\" (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN IDENTIFIER('nosuch') (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN DATABASE nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('.",
            "SHOW TABLES IN ACCOUNT (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW FUNCTIONS IN nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.DATASET' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.DATASET.",
            "SHOW FUNCTIONS IN DATASET (x)");
        assertShape(
            "REFUSED SQL compilation error: | Table 'NOSUCH_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_ZQ7.",
            "SHOW COLUMNS IN nosuch_zq7 (x)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | Object does not exist, or operation cannot be performed.",
            "BEGIN SHOW TABLES IN nosuch_zq7 (x); END;");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for V1",
            "SHOW COLUMNS IN VIEW v1 (x)");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS_ZQ7' does not exist or not "
                + "authorized.",
            "SHOW FUNCTIONS IN CLASS nosuch_cls_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_ZQ7.",
            "SHOW FUNCTIONS IN APPLICATION nosuch_app_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_ZQ7.",
            "SHOW PROCEDURES IN APPLICATION PACKAGE nosuch_pkg_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS_ZQ7' does not exist or not "
                + "authorized.",
            "SHOW PROCEDURES IN CLASS nosuch_cls_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_ZQ7",
            "SHOW TABLES IN ACCOUNT nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN IDENTIFIER(x)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '||'.",
            "SHOW TABLES IN IDENTIFIER('a' || 'b')");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA IDENTIFIER(x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN IDENTIFIER('nosuch_zq7')");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '||'.",
            "SHOW TABLES IN SCHEMA IDENTIFIER('a' || 'b')");
    }

    @Test
    public void afterEveryListing() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "is_materialized:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216)",
            "SHOW VIEWS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW VIEWS IN nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SCHEMAS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SCHEMAS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), url:VARCHAR(16777216), has_credentials:VARCHAR(16777216), "
                + "has_encryption_key:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "region:VARCHAR(16777216), type:VARCHAR(16777216), cloud:VARCHAR(16777216), "
                + "notification_channel:VARCHAR(16777216), storage_integration:VARCHAR(16777216), "
                + "endpoint:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), directory_enabled:VARCHAR(16777216)",
            "SHOW STAGES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES IN nosuch_zq7 (x)");
        assertShape(
            "name:VARCHAR(16777216), database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "next_value:NUMBER(38,0), interval:NUMBER(38,0), created_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216),"
                + " comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), ordered:VARCHAR(16777216)",
            "SHOW SEQUENCES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW SEQUENCES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216)",
            "SHOW PROCEDURES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW PROCEDURES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_dynamic:VARCHAR(16777216), is_iceberg:VARCHAR(16777216), is_interactive:VARCHAR(16777216)",
            "SHOW OBJECTS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW OBJECTS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,"
                + "0), owner:VARCHAR(16777216), target_lag:VARCHAR(16777216), refresh_mode:VARCHAR(16777216), "
                + "refresh_mode_reason:VARCHAR(16777216), warehouse:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "text:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "scheduling_state:VARCHAR(16777216), last_suspended_on:TIMESTAMP_LTZ, is_clone:VARCHAR(16777216), "
                + "is_replica:VARCHAR(16777216), is_iceberg:VARCHAR(16777216), data_timestamp:TIMESTAMP_LTZ, "
                + "owner_role_type:VARCHAR(16777216), execute_as_user:VARCHAR(16777216), "
                + "secondary_role_names:VARCHAR(16777216), insert_only_inputs:VARCHAR(16777216), "
                + "immutable_where:VARCHAR(16777216), initialization_warehouse:VARCHAR(16777216), "
                + "backfill_from:VARCHAR(16777216), scheduler:VARCHAR(16777216), frozen_where:VARCHAR(16777216), "
                + "configured_refresh_mode:VARCHAR(16777216)",
            "SHOW DYNAMIC TABLES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DYNAMIC TABLES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "table_name:VARCHAR(16777216), source_type:VARCHAR(16777216), base_tables:VARCHAR(16777216), "
                + "type:VARCHAR(16777216), stale:VARCHAR(16777216), mode:VARCHAR(16777216), "
                + "stale_after:TIMESTAMP_LTZ, invalid_reason:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW STREAMS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW STREAMS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), definition:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "notification_channel:VARCHAR(16777216), comment:VARCHAR(16777216), integration:VARCHAR(16777216), "
                + "pattern:VARCHAR(16777216), error_integration:VARCHAR(16777216), owner_role_type:VARCHAR(16777216),"
                + " invalid_reason:VARCHAR(16777216), kind:VARCHAR(16777216), is_snowflake_managed:VARCHAR(16777216)",
            "SHOW PIPES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW PIPES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), type:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), format_options:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW FILE FORMATS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW FILE FORMATS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "allowed_values:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), propagate:VARCHAR(16777216), "
                + "on_conflict:VARCHAR(16777216), multi_value:VARCHAR(16777216)",
            "SHOW TAGS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW TAGS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)",
            "SHOW MASKING POLICIES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW MASKING POLICIES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216), is_external_function:VARCHAR(16777216), "
                + "language:VARCHAR(16777216), is_memoizable:VARCHAR(16777216), is_data_metric:VARCHAR(16777216), "
                + "is_ai_function:VARCHAR(16777216)",
            "SHOW USER FUNCTIONS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW USER FUNCTIONS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), "
                + "owner:VARCHAR(16777216), comment:VARCHAR(16777216), stage:VARCHAR(16777216), "
                + "location:VARCHAR(16777216), file_format_name:VARCHAR(16777216), "
                + "file_format_type:VARCHAR(16777216), cloud:VARCHAR(16777216), region:VARCHAR(16777216), "
                + "notification_channel:VARCHAR(16777216), last_refreshed_on:TIMESTAMP_LTZ, "
                + "table_format:VARCHAR(16777216), last_refresh_details:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216)",
            "SHOW EXTERNAL TABLES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW MATERIALIZED VIEWS IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), rows:NUMBER(38,"
                + "0), bytes:NUMBER(38,0), automatic_clustering:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), change_tracking:VARCHAR(16777216), "
                + "search_optimization:VARCHAR(16777216), search_optimization_progress:NUMBER(38,0), "
                + "search_optimization_bytes:NUMBER(38,0), owner_role_type:VARCHAR(16777216), "
                + "cluster_at_ingest_time:VARCHAR(16777216)",
            "SHOW EVENT TABLES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EVENT TABLES IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), external_volume_name:VARCHAR(16777216), "
                + "catalog_name:VARCHAR(16777216), iceberg_table_type:VARCHAR(16777216), "
                + "catalog_table_name:VARCHAR(16777216), catalog_namespace:VARCHAR(16777216), "
                + "base_location:VARCHAR(16777216), can_write_metadata:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), name_mapping:VARCHAR(16777216), "
                + "catalog_sync_name:VARCHAR(16777216), auto_refresh_status:VARCHAR(16777216), "
                + "partition_specs:VARCHAR(16777216), current_partition_spec_id:NUMBER(9,0), "
                + "iceberg_table_format_version:NUMBER(9,0)",
            "SHOW ICEBERG TABLES IN test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW ICEBERG TABLES IN nosuch_zq7 (x)");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN TABLE t1 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "table_name:VARCHAR(16777216), column_name:VARCHAR(16777216), key_sequence:NUMBER(9,0), "
                + "constraint_name:VARCHAR(16777216), rely:VARCHAR(16777216), comment:VARCHAR(16777216)",
            "SHOW PRIMARY KEYS IN TABLE t1 (x)");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN t1 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA",
            "SHOW COLUMNS IN SCHEMA test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA",
            "SHOW PRIMARY KEYS IN SCHEMA test_schema (x)");
    }

    @Test
    public void columnAndKeyListings() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN SCHEMA test_db.test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "table_name:VARCHAR(16777216), column_name:VARCHAR(16777216), key_sequence:NUMBER(9,0), "
                + "constraint_name:VARCHAR(16777216), rely:VARCHAR(16777216), comment:VARCHAR(16777216)",
            "SHOW PRIMARY KEYS IN SCHEMA test_db.test_schema (x)");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN DATABASE test_db (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "table_name:VARCHAR(16777216), column_name:VARCHAR(16777216), key_sequence:NUMBER(9,0), "
                + "constraint_name:VARCHAR(16777216), rely:VARCHAR(16777216), comment:VARCHAR(16777216)",
            "SHOW PRIMARY KEYS IN DATABASE test_db (x)");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN TABLE test_schema.t1 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Table 'TEST_SCHEMA' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE TEST_SCHEMA.",
            "SHOW COLUMNS IN test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "table_name:VARCHAR(16777216), column_name:VARCHAR(16777216), key_sequence:NUMBER(9,0), "
                + "constraint_name:VARCHAR(16777216), rely:VARCHAR(16777216), comment:VARCHAR(16777216)",
            "SHOW PRIMARY KEYS IN t1 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, pk_database_name:VARCHAR(16777216), pk_schema_name:VARCHAR(16777216), "
                + "pk_table_name:VARCHAR(16777216), pk_column_name:VARCHAR(16777216), "
                + "fk_database_name:VARCHAR(16777216), fk_schema_name:VARCHAR(16777216), "
                + "fk_table_name:VARCHAR(16777216), fk_column_name:VARCHAR(16777216), key_sequence:NUMBER(9,0), "
                + "update_rule:VARCHAR(16777216), delete_rule:VARCHAR(16777216), fk_name:VARCHAR(16777216), "
                + "pk_name:VARCHAR(16777216), deferrability:VARCHAR(16777216), rely:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216)",
            "SHOW IMPORTED KEYS IN TABLE t1 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA",
            "SHOW UNIQUE KEYS IN SCHEMA test_schema (x)");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA",
            "SHOW COLUMNS IN SCHEMA test_schema");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN DATABASE test_db");
    }

    @Test
    public void theListingsModifiersAndTheClassListings() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (x) STARTS WITH 'a'");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7 (x) LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES LIKE 'a' IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), kind:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216)",
            "SHOW TERSE TABLES IN test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), dropped_on:TIMESTAMP_LTZ, "
                + "automatic_clustering:VARCHAR(16777216), change_tracking:VARCHAR(16777216), "
                + "search_optimization:VARCHAR(16777216), search_optimization_progress:NUMBER(38,0), "
                + "search_optimization_bytes:NUMBER(38,0), is_external:VARCHAR(16777216), "
                + "enable_schema_evolution:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), "
                + "is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), is_iceberg:VARCHAR(16777216), "
                + "is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), is_interactive:VARCHAR(16777216), "
                + "row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES HISTORY IN test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES LIKE 't%' IN test_schema (x) LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW CLASSES IN SCHEMA nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_ZQ7.",
            "SHOW SNOWFLAKE.ML.FORECAST IN nosuch_zq7 (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), comment:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "versions:VARCHAR(16777216)",
            "SHOW DATASETS IN test_schema (x)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), current_version:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "owner:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW SNOWFLAKE.ML.FORECAST IN test_schema (x)");
        assertAnswer(
            "1",
            "BEGIN SHOW TABLES IN test_schema (x); RETURN 1; END;");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW VIEWS IN SCHEMA nosuch_zq7 (x, y)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SCHEMAS IN DATABASE nosuch_zq7 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW OBJECTS IN DATABASE nosuch_zq7 ()");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE in "
                + "TABLE'.",
            "SHOW TABLES IN TABLE t1 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE in "
                + "VIEW'.",
            "SHOW TABLES IN VIEW v1 (x)");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN nosuch_zq7.nosuch_zq7.nosuch_zq7 (x)");
    }

    @Test
    public void theWordsAfterAKeywordScope() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT4'. | syntax "
                + "error line 1 at position 12 unexpected 'x'.",
            "DROP FLOAT4 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT'. | syntax "
                + "error line 1 at position 11 unexpected 'x'.",
            "DROP FLOAT x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SHOW TABLES IN USER x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'VIEW'.",
            "SHOW TABLES IN MATERIALIZED VIEW mv");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SHOW TABLES IN USER x y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SHOW TABLES IN nosuch_zq7 x (1)");
    }
}
