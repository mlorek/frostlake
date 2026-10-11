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
 * The words Frostlake's lexer keeps as keywords of its own statements, functions and properties are plain
 * names to the account: SHOW of one and DROP of one with an instance's name miss the class the word names, as
 * for any word, in any case, with IF EXISTS and with a scope or a LIKE. A LIMIT 0 is refused first, and a
 * CASCADE as an unsupported feature. Every answer is live-verified, word by word.
 */
public class KeywordClassNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeywordWordNamesAClass() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DOWNSTREAM' does not exist or not "
                    + "authorized.",
                "SHOW DOWNSTREAM");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DOWNSTREAM' does not exist or not "
                    + "authorized.",
                "DROP DOWNSTREAM x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INCREMENTAL' does not exist or not "
                    + "authorized.",
                "SHOW INCREMENTAL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INCREMENTAL' does not exist or not "
                    + "authorized.",
                "DROP INCREMENTAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INITIALIZE' does not exist or not "
                    + "authorized.",
                "SHOW INITIALIZE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INITIALIZE' does not exist or not "
                    + "authorized.",
                "DROP INITIALIZE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SESSIONS' does not exist or not authorized.",
                "SHOW SESSIONS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SESSIONS' does not exist or not authorized.",
                "DROP SESSIONS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PAUSE' does not exist or not authorized.",
                "SHOW PAUSE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PAUSE' does not exist or not authorized.",
                "DROP PAUSE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCHEDULE' does not exist or not authorized.",
                "SHOW SCHEDULE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCHEDULE' does not exist or not authorized.",
                "DROP SCHEDULE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NEXTVAL' does not exist or not authorized.",
                "SHOW NEXTVAL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NEXTVAL' does not exist or not authorized.",
                "DROP NEXTVAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRVAL' does not exist or not authorized.",
                "SHOW CURRVAL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRVAL' does not exist or not authorized.",
                "DROP CURRVAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOCALTIMESTAMP' does not exist or not "
                    + "authorized.",
                "SHOW LOCALTIMESTAMP");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOCALTIMESTAMP' does not exist or not "
                    + "authorized.",
                "DROP LOCALTIMESTAMP x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOCALTIME' does not exist or not authorized.",
                "SHOW LOCALTIME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOCALTIME' does not exist or not authorized.",
                "DROP LOCALTIME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'GENERATION' does not exist or not "
                    + "authorized.",
                "SHOW GENERATION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'GENERATION' does not exist or not "
                    + "authorized.",
                "DROP GENERATION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TITLE' does not exist or not authorized.",
                "SHOW TITLE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TITLE' does not exist or not authorized.",
                "DROP TITLE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'STANDARD' does not exist or not authorized.",
                "SHOW STANDARD");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'STANDARD' does not exist or not authorized.",
                "DROP STANDARD x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ECONOMY' does not exist or not authorized.",
                "SHOW ECONOMY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ECONOMY' does not exist or not authorized.",
                "DROP ECONOMY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ENCRYPTION' does not exist or not "
                    + "authorized.",
                "SHOW ENCRYPTION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ENCRYPTION' does not exist or not "
                    + "authorized.",
                "DROP ENCRYPTION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'HEADER' does not exist or not authorized.",
                "SHOW HEADER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'HEADER' does not exist or not authorized.",
                "DROP HEADER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SINGLE' does not exist or not authorized.",
                "SHOW SINGLE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SINGLE' does not exist or not authorized.",
                "DROP SINGLE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'COMPRESSION' does not exist or not "
                    + "authorized.",
                "SHOW COMPRESSION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'COMPRESSION' does not exist or not "
                    + "authorized.",
                "DROP COMPRESSION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'JAVASCRIPT' does not exist or not "
                    + "authorized.",
                "SHOW JAVASCRIPT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'JAVASCRIPT' does not exist or not "
                    + "authorized.",
                "DROP JAVASCRIPT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'JAVA' does not exist or not authorized.",
                "SHOW JAVA");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'JAVA' does not exist or not authorized.",
                "DROP JAVA x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCALA' does not exist or not authorized.",
                "SHOW SCALA");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCALA' does not exist or not authorized.",
                "DROP SCALA x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PYTHON' does not exist or not authorized.",
                "SHOW PYTHON");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PYTHON' does not exist or not authorized.",
                "DROP PYTHON x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'HANDLER' does not exist or not authorized.",
                "SHOW HANDLER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'HANDLER' does not exist or not authorized.",
                "DROP HANDLER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SQL' does not exist or not authorized.",
                "SHOW SQL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SQL' does not exist or not authorized.",
                "DROP SQL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'EMAIL' does not exist or not authorized.",
                "SHOW EMAIL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'EMAIL' does not exist or not authorized.",
                "DROP EMAIL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DISABLED' does not exist or not authorized.",
                "SHOW DISABLED");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DISABLED' does not exist or not authorized.",
                "DROP DISABLED x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'GENERATOR' does not exist or not authorized.",
                "SHOW GENERATOR");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'GENERATOR' does not exist or not authorized.",
                "DROP GENERATOR x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ROWCOUNT' does not exist or not authorized.",
                "SHOW ROWCOUNT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ROWCOUNT' does not exist or not authorized.",
                "DROP ROWCOUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TIMELIMIT' does not exist or not authorized.",
                "SHOW TIMELIMIT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TIMELIMIT' does not exist or not authorized.",
                "DROP TIMELIMIT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DELIMITER' does not exist or not authorized.",
                "SHOW DELIMITER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DELIMITER' does not exist or not authorized.",
                "DROP DELIMITER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW FLATTEN");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "DROP FLATTEN x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PATH' does not exist or not authorized.",
                "SHOW PATH");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PATH' does not exist or not authorized.",
                "DROP PATH x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PROPAGATE' does not exist or not authorized.",
                "SHOW PROPAGATE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PROPAGATE' does not exist or not authorized.",
                "DROP PROPAGATE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RUNBOOK' does not exist or not authorized.",
                "SHOW RUNBOOK");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RUNBOOK' does not exist or not authorized.",
                "DROP RUNBOOK x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'OTHER' does not exist or not authorized.",
                "SHOW OTHER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'OTHER' does not exist or not authorized.",
                "DROP OTHER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ABORT_ALL_QUERIES' does not exist or not "
                    + "authorized.",
                "SHOW ABORT_ALL_QUERIES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ABORT_ALL_QUERIES' does not exist or not "
                    + "authorized.",
                "DROP ABORT_ALL_QUERIES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_NAME' does not exist or not "
                    + "authorized.",
                "SHOW ADMIN_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_NAME' does not exist or not "
                    + "authorized.",
                "DROP ADMIN_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_PASSWORD' does not exist or not "
                    + "authorized.",
                "SHOW ADMIN_PASSWORD");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_PASSWORD' does not exist or not "
                    + "authorized.",
                "DROP ADMIN_PASSWORD x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_RSA_PUBLIC_KEY' does not exist or not "
                    + "authorized.",
                "SHOW ADMIN_RSA_PUBLIC_KEY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_RSA_PUBLIC_KEY' does not exist or not "
                    + "authorized.",
                "DROP ADMIN_RSA_PUBLIC_KEY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_USER_TYPE' does not exist or not "
                    + "authorized.",
                "SHOW ADMIN_USER_TYPE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ADMIN_USER_TYPE' does not exist or not "
                    + "authorized.",
                "DROP ADMIN_USER_TYPE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ALLOWED_VALUES_SEQUENCE' does not exist or "
                    + "not authorized.",
                "SHOW ALLOWED_VALUES_SEQUENCE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ALLOWED_VALUES_SEQUENCE' does not exist or "
                    + "not authorized.",
                "DROP ALLOWED_VALUES_SEQUENCE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ALLOW_OVERLAPPING_EXECUTION' does not exist "
                    + "or not authorized.",
                "SHOW ALLOW_OVERLAPPING_EXECUTION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ALLOW_OVERLAPPING_EXECUTION' does not exist "
                    + "or not authorized.",
                "DROP ALLOW_OVERLAPPING_EXECUTION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_INGEST' does not exist or not "
                    + "authorized.",
                "SHOW AUTO_INGEST");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_INGEST' does not exist or not "
                    + "authorized.",
                "DROP AUTO_INGEST x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_RESUME' does not exist or not "
                    + "authorized.",
                "SHOW AUTO_RESUME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_RESUME' does not exist or not "
                    + "authorized.",
                "DROP AUTO_RESUME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_SUSPEND' does not exist or not "
                    + "authorized.",
                "SHOW AUTO_SUSPEND");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_SUSPEND' does not exist or not "
                    + "authorized.",
                "DROP AUTO_SUSPEND x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_SUSPEND_SECS' does not exist or not "
                    + "authorized.",
                "SHOW AUTO_SUSPEND_SECS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AUTO_SUSPEND_SECS' does not exist or not "
                    + "authorized.",
                "DROP AUTO_SUSPEND_SECS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AWS_SNS_TOPIC' does not exist or not "
                    + "authorized.",
                "SHOW AWS_SNS_TOPIC");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'AWS_SNS_TOPIC' does not exist or not "
                    + "authorized.",
                "DROP AWS_SNS_TOPIC x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'BACKUP_INSTANCE_FAMILIES' does not exist or "
                    + "not authorized.",
                "SHOW BACKUP_INSTANCE_FAMILIES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'BACKUP_INSTANCE_FAMILIES' does not exist or "
                    + "not authorized.",
                "DROP BACKUP_INSTANCE_FAMILIES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'COMPUTE_POOL' does not exist or not "
                    + "authorized.",
                "SHOW COMPUTE_POOL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'COMPUTE_POOL' does not exist or not "
                    + "authorized.",
                "DROP COMPUTE_POOL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_DATE' does not exist or not "
                    + "authorized.",
                "SHOW CURRENT_DATE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_DATE' does not exist or not "
                    + "authorized.",
                "DROP CURRENT_DATE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_TIME' does not exist or not "
                    + "authorized.",
                "SHOW CURRENT_TIME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_TIME' does not exist or not "
                    + "authorized.",
                "DROP CURRENT_TIME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_TIMESTAMP' does not exist or not "
                    + "authorized.",
                "SHOW CURRENT_TIMESTAMP");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_TIMESTAMP' does not exist or not "
                    + "authorized.",
                "DROP CURRENT_TIMESTAMP x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_USER' does not exist or not "
                    + "authorized.",
                "SHOW CURRENT_USER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRENT_USER' does not exist or not "
                    + "authorized.",
                "DROP CURRENT_USER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DATA_RETENTION_TIME_IN_DAYS' does not exist "
                    + "or not authorized.",
                "SHOW DATA_RETENTION_TIME_IN_DAYS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DATA_RETENTION_TIME_IN_DAYS' does not exist "
                    + "or not authorized.",
                "DROP DATA_RETENTION_TIME_IN_DAYS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DATE_FORMAT' does not exist or not "
                    + "authorized.",
                "SHOW DATE_FORMAT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DATE_FORMAT' does not exist or not "
                    + "authorized.",
                "DROP DATE_FORMAT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_NAMESPACE' does not exist or not "
                    + "authorized.",
                "SHOW DEFAULT_NAMESPACE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_NAMESPACE' does not exist or not "
                    + "authorized.",
                "DROP DEFAULT_NAMESPACE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_ROLE' does not exist or not "
                    + "authorized.",
                "SHOW DEFAULT_ROLE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_ROLE' does not exist or not "
                    + "authorized.",
                "DROP DEFAULT_ROLE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_SECONDARY_ROLES' does not exist or "
                    + "not authorized.",
                "SHOW DEFAULT_SECONDARY_ROLES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_SECONDARY_ROLES' does not exist or "
                    + "not authorized.",
                "DROP DEFAULT_SECONDARY_ROLES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_WAREHOUSE' does not exist or not "
                    + "authorized.",
                "SHOW DEFAULT_WAREHOUSE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DEFAULT_WAREHOUSE' does not exist or not "
                    + "authorized.",
                "DROP DEFAULT_WAREHOUSE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DISPLAY_NAME' does not exist or not "
                    + "authorized.",
                "SHOW DISPLAY_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DISPLAY_NAME' does not exist or not "
                    + "authorized.",
                "DROP DISPLAY_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'EMBEDDING_MODEL' does not exist or not "
                    + "authorized.",
                "SHOW EMBEDDING_MODEL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'EMBEDDING_MODEL' does not exist or not "
                    + "authorized.",
                "DROP EMBEDDING_MODEL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ENABLE_QUERY_ACCELERATION' does not exist "
                    + "or not authorized.",
                "SHOW ENABLE_QUERY_ACCELERATION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ENABLE_QUERY_ACCELERATION' does not exist "
                    + "or not authorized.",
                "DROP ENABLE_QUERY_ACCELERATION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'EXTERNAL_ACCESS_INTEGRATIONS' does not "
                    + "exist or not authorized.",
                "SHOW EXTERNAL_ACCESS_INTEGRATIONS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'EXTERNAL_ACCESS_INTEGRATIONS' does not "
                    + "exist or not authorized.",
                "DROP EXTERNAL_ACCESS_INTEGRATIONS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FIELD_DELIMITER' does not exist or not "
                    + "authorized.",
                "SHOW FIELD_DELIMITER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FIELD_DELIMITER' does not exist or not "
                    + "authorized.",
                "DROP FIELD_DELIMITER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FILE_FORMAT' does not exist or not "
                    + "authorized.",
                "SHOW FILE_FORMAT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FILE_FORMAT' does not exist or not "
                    + "authorized.",
                "DROP FILE_FORMAT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FILE_URL' does not exist or not authorized.",
                "SHOW FILE_URL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FILE_URL' does not exist or not authorized.",
                "DROP FILE_URL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FIRST_NAME' does not exist or not "
                    + "authorized.",
                "SHOW FIRST_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FIRST_NAME' does not exist or not "
                    + "authorized.",
                "DROP FIRST_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'IDLE_AUTO_SHUTDOWN_TIME_SECONDS' does not "
                    + "exist or not authorized.",
                "SHOW IDLE_AUTO_SHUTDOWN_TIME_SECONDS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'IDLE_AUTO_SHUTDOWN_TIME_SECONDS' does not "
                    + "exist or not authorized.",
                "DROP IDLE_AUTO_SHUTDOWN_TIME_SECONDS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INITIALLY_SUSPENDED' does not exist or not "
                    + "authorized.",
                "SHOW INITIALLY_SUSPENDED");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INITIALLY_SUSPENDED' does not exist or not "
                    + "authorized.",
                "DROP INITIALLY_SUSPENDED x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INSTANCE_FAMILY' does not exist or not "
                    + "authorized.",
                "SHOW INSTANCE_FAMILY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INSTANCE_FAMILY' does not exist or not "
                    + "authorized.",
                "DROP INSTANCE_FAMILY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LAST_NAME' does not exist or not authorized.",
                "SHOW LAST_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LAST_NAME' does not exist or not authorized.",
                "DROP LAST_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOGIN_NAME' does not exist or not "
                    + "authorized.",
                "SHOW LOGIN_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOGIN_NAME' does not exist or not "
                    + "authorized.",
                "DROP LOGIN_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAIN_FILE' does not exist or not authorized.",
                "SHOW MAIN_FILE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAIN_FILE' does not exist or not authorized.",
                "DROP MAIN_FILE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MASKING_POLICY' does not exist or not "
                    + "authorized.",
                "SHOW MASKING_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MASKING_POLICY' does not exist or not "
                    + "authorized.",
                "DROP MASKING_POLICY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MATCH_BY_COLUMN_NAME' does not exist or not "
                    + "authorized.",
                "SHOW MATCH_BY_COLUMN_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MATCH_BY_COLUMN_NAME' does not exist or not "
                    + "authorized.",
                "DROP MATCH_BY_COLUMN_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_BATCH_ROWS' does not exist or not "
                    + "authorized.",
                "SHOW MAX_BATCH_ROWS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_BATCH_ROWS' does not exist or not "
                    + "authorized.",
                "DROP MAX_BATCH_ROWS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_CLUSTER_COUNT' does not exist or not "
                    + "authorized.",
                "SHOW MAX_CLUSTER_COUNT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_CLUSTER_COUNT' does not exist or not "
                    + "authorized.",
                "DROP MAX_CLUSTER_COUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_CONCURRENCY_LEVEL' does not exist or "
                    + "not authorized.",
                "SHOW MAX_CONCURRENCY_LEVEL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_CONCURRENCY_LEVEL' does not exist or "
                    + "not authorized.",
                "DROP MAX_CONCURRENCY_LEVEL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_FILE_SIZE' does not exist or not "
                    + "authorized.",
                "SHOW MAX_FILE_SIZE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_FILE_SIZE' does not exist or not "
                    + "authorized.",
                "DROP MAX_FILE_SIZE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_NODES' does not exist or not authorized.",
                "SHOW MAX_NODES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MAX_NODES' does not exist or not authorized.",
                "DROP MAX_NODES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MIDDLE_NAME' does not exist or not "
                    + "authorized.",
                "SHOW MIDDLE_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MIDDLE_NAME' does not exist or not "
                    + "authorized.",
                "DROP MIDDLE_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MIN_CLUSTER_COUNT' does not exist or not "
                    + "authorized.",
                "SHOW MIN_CLUSTER_COUNT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MIN_CLUSTER_COUNT' does not exist or not "
                    + "authorized.",
                "DROP MIN_CLUSTER_COUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MIN_NODES' does not exist or not authorized.",
                "SHOW MIN_NODES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MIN_NODES' does not exist or not authorized.",
                "DROP MIN_NODES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MULTI_STATEMENT_COUNT' does not exist or "
                    + "not authorized.",
                "SHOW MULTI_STATEMENT_COUNT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MULTI_STATEMENT_COUNT' does not exist or "
                    + "not authorized.",
                "DROP MULTI_STATEMENT_COUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MUST_CHANGE_PASSWORD' does not exist or not "
                    + "authorized.",
                "SHOW MUST_CHANGE_PASSWORD");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'MUST_CHANGE_PASSWORD' does not exist or not "
                    + "authorized.",
                "DROP MUST_CHANGE_PASSWORD x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NETWORK_POLICY' does not exist or not "
                    + "authorized.",
                "SHOW NETWORK_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NETWORK_POLICY' does not exist or not "
                    + "authorized.",
                "DROP NETWORK_POLICY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_CONFLICT' does not exist or not "
                    + "authorized.",
                "SHOW ON_CONFLICT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_CONFLICT' does not exist or not "
                    + "authorized.",
                "DROP ON_CONFLICT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_CREATE' does not exist or not authorized.",
                "SHOW ON_CREATE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_CREATE' does not exist or not authorized.",
                "DROP ON_CREATE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_ERROR' does not exist or not authorized.",
                "SHOW ON_ERROR");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_ERROR' does not exist or not authorized.",
                "DROP ON_ERROR x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_SCHEDULE' does not exist or not "
                    + "authorized.",
                "SHOW ON_SCHEDULE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ON_SCHEDULE' does not exist or not "
                    + "authorized.",
                "DROP ON_SCHEDULE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PLACEMENT_GROUP' does not exist or not "
                    + "authorized.",
                "SHOW PLACEMENT_GROUP");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PLACEMENT_GROUP' does not exist or not "
                    + "authorized.",
                "DROP PLACEMENT_GROUP x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'QUERY_ACCELERATION_MAX_SCALE_FACTOR' does "
                    + "not exist or not authorized.",
                "SHOW QUERY_ACCELERATION_MAX_SCALE_FACTOR");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'QUERY_ACCELERATION_MAX_SCALE_FACTOR' does "
                    + "not exist or not authorized.",
                "DROP QUERY_ACCELERATION_MAX_SCALE_FACTOR x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'QUERY_WAREHOUSE' does not exist or not "
                    + "authorized.",
                "SHOW QUERY_WAREHOUSE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'QUERY_WAREHOUSE' does not exist or not "
                    + "authorized.",
                "DROP QUERY_WAREHOUSE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'READ_ONLY' does not exist or not authorized.",
                "SHOW READ_ONLY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'READ_ONLY' does not exist or not authorized.",
                "DROP READ_ONLY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RECORD_DELIMITER' does not exist or not "
                    + "authorized.",
                "SHOW RECORD_DELIMITER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RECORD_DELIMITER' does not exist or not "
                    + "authorized.",
                "DROP RECORD_DELIMITER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'REFRESH_MODE' does not exist or not "
                    + "authorized.",
                "SHOW REFRESH_MODE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'REFRESH_MODE' does not exist or not "
                    + "authorized.",
                "DROP REFRESH_MODE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RESOURCE_CONSTRAINT' does not exist or not "
                    + "authorized.",
                "SHOW RESOURCE_CONSTRAINT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RESOURCE_CONSTRAINT' does not exist or not "
                    + "authorized.",
                "DROP RESOURCE_CONSTRAINT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RESOURCE_MONITOR' does not exist or not "
                    + "authorized.",
                "SHOW RESOURCE_MONITOR");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RESOURCE_MONITOR' does not exist or not "
                    + "authorized.",
                "DROP RESOURCE_MONITOR x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RESUME_IF_SUSPENDED' does not exist or not "
                    + "authorized.",
                "SHOW RESUME_IF_SUSPENDED");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RESUME_IF_SUSPENDED' does not exist or not "
                    + "authorized.",
                "DROP RESUME_IF_SUSPENDED x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'REVOKE_CURRENT_GRANTS' does not exist or "
                    + "not authorized.",
                "SHOW REVOKE_CURRENT_GRANTS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'REVOKE_CURRENT_GRANTS' does not exist or "
                    + "not authorized.",
                "DROP REVOKE_CURRENT_GRANTS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ROW_ACCESS_POLICY' does not exist or not "
                    + "authorized.",
                "SHOW ROW_ACCESS_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'ROW_ACCESS_POLICY' does not exist or not "
                    + "authorized.",
                "DROP ROW_ACCESS_POLICY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RUNTIME_NAME' does not exist or not "
                    + "authorized.",
                "SHOW RUNTIME_NAME");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RUNTIME_NAME' does not exist or not "
                    + "authorized.",
                "DROP RUNTIME_NAME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RUNTIME_VERSION' does not exist or not "
                    + "authorized.",
                "SHOW RUNTIME_VERSION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'RUNTIME_VERSION' does not exist or not "
                    + "authorized.",
                "DROP RUNTIME_VERSION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCALING_POLICY' does not exist or not "
                    + "authorized.",
                "SHOW SCALING_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCALING_POLICY' does not exist or not "
                    + "authorized.",
                "DROP SCALING_POLICY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SERVERLESS_TASK_MAX_STATEMENT_SIZE' does "
                    + "not exist or not authorized.",
                "SHOW SERVERLESS_TASK_MAX_STATEMENT_SIZE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SERVERLESS_TASK_MAX_STATEMENT_SIZE' does "
                    + "not exist or not authorized.",
                "DROP SERVERLESS_TASK_MAX_STATEMENT_SIZE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SESSION_POLICY' does not exist or not "
                    + "authorized.",
                "SHOW SESSION_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SESSION_POLICY' does not exist or not "
                    + "authorized.",
                "DROP SESSION_POLICY x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SHOW_INITIAL_ROWS' does not exist or not "
                    + "authorized.",
                "SHOW SHOW_INITIAL_ROWS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SHOW_INITIAL_ROWS' does not exist or not "
                    + "authorized.",
                "DROP SHOW_INITIAL_ROWS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SIZE_LIMIT' does not exist or not "
                    + "authorized.",
                "SHOW SIZE_LIMIT");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SIZE_LIMIT' does not exist or not "
                    + "authorized.",
                "DROP SIZE_LIMIT x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SKIP_HEADER' does not exist or not "
                    + "authorized.",
                "SHOW SKIP_HEADER");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SKIP_HEADER' does not exist or not "
                    + "authorized.",
                "DROP SKIP_HEADER x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SPLIT_TO_TABLE' does not exist or not "
                    + "authorized.",
                "SHOW SPLIT_TO_TABLE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SPLIT_TO_TABLE' does not exist or not "
                    + "authorized.",
                "DROP SPLIT_TO_TABLE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'STATEMENT_QUEUED_TIMEOUT_IN_SECONDS' does "
                    + "not exist or not authorized.",
                "SHOW STATEMENT_QUEUED_TIMEOUT_IN_SECONDS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'STATEMENT_QUEUED_TIMEOUT_IN_SECONDS' does "
                    + "not exist or not authorized.",
                "DROP STATEMENT_QUEUED_TIMEOUT_IN_SECONDS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'STATEMENT_TIMEOUT_IN_SECONDS' does not "
                    + "exist or not authorized.",
                "SHOW STATEMENT_TIMEOUT_IN_SECONDS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'STATEMENT_TIMEOUT_IN_SECONDS' does not "
                    + "exist or not authorized.",
                "DROP STATEMENT_TIMEOUT_IN_SECONDS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SUSPEND_ALERT_AFTER_NUM_FAILURES' does not "
                    + "exist or not authorized.",
                "SHOW SUSPEND_ALERT_AFTER_NUM_FAILURES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SUSPEND_ALERT_AFTER_NUM_FAILURES' does not "
                    + "exist or not authorized.",
                "DROP SUSPEND_ALERT_AFTER_NUM_FAILURES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SUSPEND_TASK_AFTER_NUM_FAILURES' does not "
                    + "exist or not authorized.",
                "SHOW SUSPEND_TASK_AFTER_NUM_FAILURES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SUSPEND_TASK_AFTER_NUM_FAILURES' does not "
                    + "exist or not authorized.",
                "DROP SUSPEND_TASK_AFTER_NUM_FAILURES x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TARGET_COMPLETION_INTERVAL' does not exist "
                    + "or not authorized.",
                "SHOW TARGET_COMPLETION_INTERVAL");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TARGET_COMPLETION_INTERVAL' does not exist "
                    + "or not authorized.",
                "DROP TARGET_COMPLETION_INTERVAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TARGET_LAG' does not exist or not "
                    + "authorized.",
                "SHOW TARGET_LAG");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TARGET_LAG' does not exist or not "
                    + "authorized.",
                "DROP TARGET_LAG x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TASK_AUTO_RETRY_ATTEMPTS' does not exist or "
                    + "not authorized.",
                "SHOW TASK_AUTO_RETRY_ATTEMPTS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TASK_AUTO_RETRY_ATTEMPTS' does not exist or "
                    + "not authorized.",
                "DROP TASK_AUTO_RETRY_ATTEMPTS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE' "
                    + "does not exist or not authorized.",
                "SHOW USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE' "
                    + "does not exist or not authorized.",
                "DROP USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class "
                    + "'USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS' does not exist or not authorized.",
                "SHOW USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class "
                    + "'USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS' does not exist or not authorized.",
                "DROP USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'USER_TASK_TIMEOUT_MS' does not exist or not "
                    + "authorized.",
                "SHOW USER_TASK_TIMEOUT_MS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'USER_TASK_TIMEOUT_MS' does not exist or not "
                    + "authorized.",
                "DROP USER_TASK_TIMEOUT_MS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'VALIDATION_MODE' does not exist or not "
                    + "authorized.",
                "SHOW VALIDATION_MODE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'VALIDATION_MODE' does not exist or not "
                    + "authorized.",
                "DROP VALIDATION_MODE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'WAIT_FOR_COMPLETION' does not exist or not "
                    + "authorized.",
                "SHOW WAIT_FOR_COMPLETION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'WAIT_FOR_COMPLETION' does not exist or not "
                    + "authorized.",
                "DROP WAIT_FOR_COMPLETION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'WAREHOUSE_SIZE' does not exist or not "
                    + "authorized.",
                "SHOW WAREHOUSE_SIZE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'WAREHOUSE_SIZE' does not exist or not "
                    + "authorized.",
                "DROP WAREHOUSE_SIZE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'WAREHOUSE_TYPE' does not exist or not "
                    + "authorized.",
                "SHOW WAREHOUSE_TYPE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'WAREHOUSE_TYPE' does not exist or not "
                    + "authorized.",
                "DROP WAREHOUSE_TYPE x");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void itIsReadAsAnyClassNameIs() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DOWNSTREAM' does not exist or not "
                    + "authorized.",
                "SHOW FUNCTIONS IN DOWNSTREAM x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INCREMENTAL' does not exist or not "
                    + "authorized.",
                "SHOW FUNCTIONS IN INCREMENTAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INITIALIZE' does not exist or not "
                    + "authorized.",
                "SHOW FUNCTIONS IN INITIALIZE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SESSIONS' does not exist or not authorized.",
                "SHOW FUNCTIONS IN SESSIONS x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'PAUSE' does not exist or not authorized.",
                "SHOW FUNCTIONS IN PAUSE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SCHEDULE' does not exist or not authorized.",
                "SHOW FUNCTIONS IN SCHEDULE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NEXTVAL' does not exist or not authorized.",
                "SHOW FUNCTIONS IN NEXTVAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CURRVAL' does not exist or not authorized.",
                "SHOW FUNCTIONS IN CURRVAL x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOCALTIMESTAMP' does not exist or not "
                    + "authorized.",
                "SHOW FUNCTIONS IN LOCALTIMESTAMP x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'LOCALTIME' does not exist or not authorized.",
                "SHOW FUNCTIONS IN LOCALTIME x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'GENERATION' does not exist or not "
                    + "authorized.",
                "SHOW FUNCTIONS IN GENERATION x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TITLE' does not exist or not authorized.",
                "SHOW FUNCTIONS IN TITLE x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW flatten");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "DROP flatten x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW \"FLATTEN\"");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "DROP FLATTEN IF EXISTS x");
            assertAnswer(
                "REFUSED Unsupported feature 'DROP FLATTEN'.",
                "DROP FLATTEN x CASCADE");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW FLATTEN IN SCHEMA test_schema");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW FLATTEN LIKE 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW TERSE FLATTEN");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW FLATTEN LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'.",
                "DROP FLATTEN");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "DROP FLATTEN a.b");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "DROP FLATTEN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'x'.",
                "SHOW SQL x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
                "DROP SQL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
                "DROP SQL x y");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.FLATTEN' does not exist "
                    + "or not authorized.",
                "SHOW test_schema.FLATTEN");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.FLATTEN' does not exist "
                    + "or not authorized.",
                "DROP test_schema.FLATTEN x");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.FLATTEN' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.FLATTEN.",
                "SHOW FLATTEN.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.FLATTEN' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.FLATTEN.",
                "DROP FLATTEN.x y");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
