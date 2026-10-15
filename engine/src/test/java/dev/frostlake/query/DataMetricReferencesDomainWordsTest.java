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
 * DATA_METRIC_FUNCTION_REFERENCES' entity and domain. The table-like kinds are domains, written with a blank or
 * an underscore between their words, and a missing entity is told in the kind's own noun — Iceberg table and
 * Event table by the full path. A kind of object the account knows and the argument takes no value of is
 * Invalid value 'ROW_ACCESS_POLICY' for argument 'ref_entity_domain', its words joined by underscores, and
 * any other word an unknown domain as written. The three arguments are texts: another type is invalid type
 * [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME', the entity judged before the domain wherever each is written,
 * and one argument written without a name is the metric's (live-verified).
 */
public class DataMetricReferencesDomainWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void theTableKindsAndTheirNouns() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v',"
                + " REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.\"nosuch_t\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_t\".",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'\"nosuch_t\"', REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_s_zq7.nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_db_zq7.s.nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v',"
                + " REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.\"nosuch_t\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_t\".",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'\"nosuch_t\"', REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_s_zq7.nosuch_t', REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_db_zq7.s.nosuch_t', REF_ENTITY_DOMAIN => 'EVENT TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v',"
                + " REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.\"nosuch_t\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_t\".",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'\"nosuch_t\"', REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_s_zq7.nosuch_t', REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_db_zq7.s.nosuch_t', REF_ENTITY_DOMAIN => 'DYNAMIC TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v',"
                + " REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.\"nosuch_t\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_t\".",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'\"nosuch_t\"', REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_s_zq7.nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_db_zq7.s.nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG_TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EVENT_TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DYNAMIC_TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL_TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MATERIALIZED_VIEW'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'iceberg table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'Event Table'))");
    }

    @Test
    public void theKindsOfObjectAndTheUnknownWords() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ALERT' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ALERT'))");
        assertAnswer(
            "REFUSED Unknown domain: BACKUP POLICY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'BACKUP POLICY'))");
        assertAnswer(
            "REFUSED Unknown domain: BACKUP SET.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'BACKUP SET'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COMPUTE_POOL' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'COMPUTE POOL'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CORTEX_AGENT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CORTEX AGENT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CORTEX_SEARCH_SERVICE' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CORTEX SEARCH SERVICE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATABASE_ROLE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DATABASE ROLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'FAILOVER_GROUP' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'FAILOVER GROUP'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'IMAGE_REPOSITORY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'IMAGE REPOSITORY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'INSTANCE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'INSTANCE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'NETWORK_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NETWORK POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'REPLICATION_GROUP' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'REPLICATION GROUP'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SERVICE' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SERVICE'))");
        assertAnswer(
            "REFUSED Unknown domain: SNAPSHOT POLICY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SNAPSHOT POLICY'))");
        assertAnswer(
            "REFUSED Unknown domain: SNAPSHOT SET.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SNAPSHOT SET'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SNOWFLAKE_INTELLIGENCE' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SNOWFLAKE INTELLIGENCE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'PASSWORD_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PASSWORD POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'FILE_FORMAT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'FILE FORMAT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'MASKING_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MASKING POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ROW_ACCESS_POLICY' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ROW ACCESS POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SESSION_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SESSION POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'NETWORK_RULE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NETWORK RULE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SECRET' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SECRET'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'NOTEBOOK' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NOTEBOOK'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'STREAMLIT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'STREAMLIT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'MODEL' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MODEL'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATASET' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DATASET'))");
        assertAnswer(
            "REFUSED Unknown domain: SNAPSHOT.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SNAPSHOT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'EXTERNAL_VOLUME' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL VOLUME'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'VOLUME' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'VOLUME'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'APPLICATION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'APPLICATION'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'APPLICATION_PACKAGE' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'APPLICATION PACKAGE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SEMANTIC_VIEW' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SEMANTIC VIEW'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'GIT_REPOSITORY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'GIT REPOSITORY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CONNECTION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CONNECTION'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'RESOURCE_MONITOR' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'RESOURCE MONITOR'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CLASS' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CLASS'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'POLICY' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'AGGREGATION_POLICY' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'AGGREGATION POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'PROJECTION_POLICY' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PROJECTION POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'JOIN_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'JOIN POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'AUTHENTICATION_POLICY' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'AUTHENTICATION POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'PRIVACY_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PRIVACY POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'PACKAGES_POLICY' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PACKAGES POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'STORAGE_LIFECYCLE_POLICY' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'STORAGE LIFECYCLE POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DBT_PROJECT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DBT PROJECT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ORGANIZATION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ORGANIZATION'))");
        assertAnswer(
            "REFUSED Unknown domain: LISTING.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'LISTING'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CONTACT' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CONTACT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATA_METRIC_FUNCTION' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DATA METRIC FUNCTION'))");
        assertAnswer(
            "REFUSED Unknown domain: EXTERNAL FUNCTION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL FUNCTION'))");
        assertAnswer(
            "REFUSED Unknown domain: CATALOG INTEGRATION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CATALOG INTEGRATION'))");
        assertAnswer(
            "REFUSED Unknown domain: SECURITY INTEGRATION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SECURITY INTEGRATION'))");
        assertAnswer(
            "REFUSED Unknown domain: STORAGE INTEGRATION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'STORAGE INTEGRATION'))");
        assertAnswer(
            "REFUSED Unknown domain: NOTIFICATION INTEGRATION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NOTIFICATION INTEGRATION'))");
        assertAnswer(
            "REFUSED Unknown domain: API INTEGRATION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'API INTEGRATION'))");
        assertAnswer(
            "REFUSED Unknown domain: EXTERNAL ACCESS INTEGRATION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL ACCESS INTEGRATION'))");
        assertAnswer(
            "REFUSED Unknown domain: INDEX.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'INDEX'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CONSTRAINT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CONSTRAINT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SESSION' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SESSION'))");
        assertAnswer(
            "REFUSED Unknown domain: QUERY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'QUERY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'TABLE_FUNCTION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TABLE FUNCTION'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SCHEMA'))");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'VIEW'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MATERIALIZED VIEW'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: NOSUCHKIND.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NOSUCHKIND'))");
    }

    @Test
    public void argumentsThatAreNoText() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 1, "
                + "REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "TRUE, REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 1.5,"
                + " REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "NULL, REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'2024-01-01'::DATE, REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_DOMAIN'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'REF_ENTITY_DOMAIN'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 1, "
                + "REF_ENTITY_DOMAIN => 1))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v',"
                + " REF_ENTITY_DOMAIN => 'VIEW'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v',"
                + " REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'VIEW'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema', REF_ENTITY_DOMAIN => 'SCHEMA'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARIANT] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "PARSE_JSON('1'), REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter 'REF_ENTITY_DOMAIN'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => ARRAY_CONSTRUCT('TABLE')))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "UPPER('t'), REF_ENTITY_DOMAIN => UPPER('table')))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't' "
                + "|| '', REF_ENTITY_DOMAIN => 'TA' || 'BLE'))");
    }

    @Test
    public void spellingsAndMoreWords() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COMPUTE_POOL' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'COMPUTE_POOL'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ROW_ACCESS_POLICY' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ROW_ACCESS_POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COMPUTE_POOL' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'compute pool'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COMPUTE_POOL' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'Compute_Pool'))");
        assertAnswer(
            "REFUSED Unknown domain: SNAPSHOT_POLICY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SNAPSHOT_POLICY'))");
        assertAnswer(
            "REFUSED Unknown domain: ICEBERG  TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG  TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain:  ICEBERG_TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => ' ICEBERG_TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MATERIALIZED_view'))");
        assertAnswer(
            "REFUSED Unknown domain: ICEBERG__TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG__TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ROW_ACCESS_POLICY' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ROW_ACCESS POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'TABLE_FUNCTION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TABLE_FUNCTION'))");
        assertAnswer(
            "REFUSED Unknown domain: EVENT.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EVENT'))");
        assertAnswer(
            "REFUSED Unknown domain: ICEBERG.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ICEBERG'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'MANAGED_ACCOUNT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MANAGED ACCOUNT'))");
        assertAnswer(
            "REFUSED Unknown domain: REPOSITORY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'REPOSITORY'))");
        assertAnswer(
            "REFUSED Unknown domain: KEY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'KEY'))");
        assertAnswer(
            "REFUSED Unknown domain: EXTERNAL.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXTERNAL'))");
        assertAnswer(
            "REFUSED Unknown domain: TEMPORARY TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TEMPORARY TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: TRANSIENT TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TRANSIENT TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: BASE TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'BASE TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'NOTIFICATION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NOTIFICATION'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'OBJECT' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'OBJECT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATA_EXCHANGE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DATA EXCHANGE'))");
        assertAnswer(
            "REFUSED Unknown domain: PROFILE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PROFILE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'BUDGET' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'BUDGET'))");
        assertAnswer(
            "REFUSED Unknown domain: AGENT.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'AGENT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'EXPERIMENT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EXPERIMENT'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'JOB' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'JOB'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'WORKSPACE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'WORKSPACE'))");
        assertAnswer(
            "REFUSED Unknown domain: GATEWAY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'GATEWAY'))");
        assertAnswer(
            "REFUSED Unknown domain: OFFLOAD POLICY.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'OFFLOAD POLICY'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ONLINE_FEATURE_TABLE' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ONLINE FEATURE TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'INTERACTIVE_TABLE' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'INTERACTIVE TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: TYPE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TYPE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'CATALOG' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CATALOG'))");
        assertAnswer(
            "REFUSED Unknown domain: HYBRID_TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'HYBRID_TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: COLUMN .",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'COLUMN '))");
        assertAnswer(
            "REFUSED Unknown domain: TAG_.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TAG_'))");
        assertAnswer(
            "REFUSED Unknown domain: _TABLE.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => '_TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: TABLE_.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TABLE_'))");
    }

    @Test
    public void theOrderTheArgumentsAreJudgedIn() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 1, "
                + "REF_ENTITY_DOMAIN => 'NOSUCHKIND'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter 'REF_ENTITY_DOMAIN'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 1.5))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'x', "
                + "REF_ENTITY_NAME => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'METRIC_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_DOMAIN'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_DOMAIN => "
                + "1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARIANT] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'t'::VARIANT, REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"1\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"1\".",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "TO_VARCHAR(1), REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'TABLE'::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARIANT] for parameter 'REF_ENTITY_DOMAIN'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'TABLE'::VARIANT))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 1, "
                + "REF_ENTITY_DOMAIN => 'COLUMN'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => NULL::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "NULL::VARCHAR, REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_DOMAIN => "
                + "'TABLE', REF_ENTITY_NAME => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'REF_ENTITY_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_DOMAIN => 1,"
                + " REF_ENTITY_NAME => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'METRIC_NAME'",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 1, "
                + "REF_ENTITY_NAME => 1, REF_ENTITY_DOMAIN => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COLUMN' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'COLUMN'))");
        assertAnswer(
            "REFUSED Unknown domain: SCHEMA_.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema', REF_ENTITY_DOMAIN => 'SCHEMA_'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't',"
                + " REF_ENTITY_DOMAIN => 'Dynamic_Table'))");
    }
}
