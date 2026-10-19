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
 * The arguments of INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES. A metric is named by a system metric's
 * full path, read as an identifier path, and one that is none is refused as the argument wrote it. An entity
 * is named by an identifier path under a domain that is judged first: a table-like kind finds any table or
 * view and refuses a missing one in its own noun - by the bare name for a table, a view and a materialized
 * view named bare, by the full path otherwise - a schema is looked up as one, another kind of object is no
 * value for the argument and any other word is an unknown domain. A metric beside an entity argument, an
 * entity without its domain and a NULL are refused with the function's argument sentence. Every answer is
 * live-verified.
 */
public class DataMetricReferencesArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aMetricIsEchoedAsWritten() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'public.nosuch_dmf' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'public.nosuch_dmf'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'nosuch_dmf' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'nosuch_dmf'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'snowflake.core.nosuch_dmf' does not exist or "
                + "not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'snowflake.core.nosuch_dmf'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'NULL_COUNT' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'NULL_COUNT'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'SNOWFLAKE.CORE.NO_SUCH_M' does not exist or "
                + "not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'SNOWFLAKE.CORE.NO_SUCH_M'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'SNOWFLAKE.CORE.NULL_COUNT'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function '\"x y\"' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => '\"x y\"'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'a.b.c.d' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'a.b.c.d'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function '' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => ''))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'NoSuch_Dmf' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'NoSuch_Dmf'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'nosuch_db_w7.s.f' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'nosuch_db_w7.s.f'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'Nosuch_T',"
                + " REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED Unknown domain: nosuch.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'nosuch'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES())");
    }

    @Test
    public void anEntityIsReadAsAnIdentifierPathUnderItsDomain() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"t\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"t\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => '\"t\"', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_s.t', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_db_w7.s.t', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'a.b.c.d', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => '', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"A B\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"A B\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'a b', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COLUMN' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'column'))");
        assertAnswer(
            "REFUSED Unknown domain: .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => ''))");
        assertAnswer(
            "REFUSED Unknown domain:  table.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => ' table'))");
        assertAnswer(
            "REFUSED Unknown domain: nosuch.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'nosuch'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => NULL, "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'nosuch', "
                + "REF_ENTITY_NAME => 't', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function ' snowflake.core.null_count' does not exist or "
                + "not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => ' "
                + "snowflake.core.null_count'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'snowflake.core.null_count ' does not exist or "
                + "not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'snowflake.core.null_count '))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'\"SNOWFLAKE\".\"CORE\".\"NULL_COUNT\"'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'core.null_count' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'core.null_count'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'v', "
                + "REF_ENTITY_DOMAIN => 'view'))");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH_V.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_v',"
                + " REF_ENTITY_DOMAIN => 'view'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'schema'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATABASE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'database'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'METRIC_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'y' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'x', "
                + "METRIC_NAME => 'y'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                + "[INFORMATION_SCHEMA] unexpected argument [NOSUCH_ARG] at position 1,",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(NOSUCH_ARG => 'x'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES('snowflake.core.null_count'))");
    }

    @Test
    public void theDomainIsJudgedBeforeTheName() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'materialized view'))");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'external table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'dynamic table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COLUMN' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATABASE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'database'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'nosuch_t',"
                + " REF_ENTITY_DOMAIN => 'schema'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema', REF_ENTITY_DOMAIN => 'schema'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_db_w7.s', REF_ENTITY_DOMAIN => 'schema'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'a.b.c', "
                + "REF_ENTITY_DOMAIN => 'schema'))");
        assertAnswer(
            "REFUSED Unknown domain: TABLE .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'TABLE '))");
        assertAnswer(
            "REFUSED Unknown domain: tables.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'tables'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ACCOUNT' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'account'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'FUNCTION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'STAGE' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'USER' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'user'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'ROLE' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'role'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'WAREHOUSE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'warehouse'))");
        assertAnswer(
            "REFUSED Unknown domain: hybrid table.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'hybrid table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'TAG' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'TASK' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'task'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'PROCEDURE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'procedure'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'INTEGRATION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'integration'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'SEQUENCE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'sequence'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'STREAM' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'PIPE' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'pipe'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'Materialized View'))");
        assertAnswer(
            "REFUSED Unknown domain: materialized  view.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'materialized  view'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'COLUMN' for argument 'ref_entity_domain' for function  "
                + "information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'COLUMN'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value 'DATABASE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'Database'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'SCHEMA'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'x', "
                + "REF_ENTITY_NAME => 't'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'x', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'snowflake.core.null_count', REF_ENTITY_NAME => 't', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'x' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES('snowflake.core.null_count', "
                + "'t'))");
        assertAnswer(
            "REFUSED SQL compilation error: function 'information_schema.data_metric_function_references' expects "
                + "argument (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES('t', 'table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't', "
                + "REF_ENTITY_DOMAIN => 'table', METRIC_NAME => NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function '\"snowflake\".core.null_count' does not exist "
                + "or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'\"snowflake\".core.null_count'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'SNOWFLAKE.CORE.\"null_count\"' does not exist "
                + "or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'SNOWFLAKE.CORE.\"null_count\"'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'snowflake..null_count' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => "
                + "'snowflake..null_count'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Data Metric Function 'SNOWFLAKE . CORE . NULL_COUNT' does not exist "
                + "or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(METRIC_NAME => 'SNOWFLAKE . "
                + "CORE . NULL_COUNT'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.\"t\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"t\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'TEST_SCHEMA.\"t\"', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.\"\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.\"\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db..t', REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\" T\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \" T\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => ' t', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"T \"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"T \".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 't ', "
                + "REF_ENTITY_DOMAIN => 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_V.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_schema.nosuch_v', REF_ENTITY_DOMAIN => 'view'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_s.v', REF_ENTITY_DOMAIN => 'view'))");
    }
}
