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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A signature and the properties after the name a DESCRIBE names, kind by kind: the account reads both alike
 * after every kind — a faulty signature's lines, a TYPE that is read and changes nothing, any other name
 * refused as an invalid parameter before the object is looked up (live-verified). Every name here is one that
 * cannot exist.
 */
public class DescribeSignatureEveryKindTest extends MeasuredCellsTestSupport {

    @Test
    public void aSignatureThatIsRead() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_SEC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEC.",
            "DESCRIBE SECRET nosuch_sec (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
            "DESCRIBE SECRET nosuch_sec (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH_NR' does not exist or not "
                + "authorized. Your primary role <ROLE> must have APPLY MASKING POLICY granted on ACCOUNT <ACCOUNT>.",
            "DESCRIBE NETWORK RULE nosuch_nr (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PP.",
            "DESCRIBE PASSWORD POLICY nosuch_pp (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Notebook 'TEST_DB.TEST_SCHEMA.NOSUCH_NB' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NOTEBOOK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_NB.",
            "DESCRIBE NOTEBOOK nosuch_nb (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Streamlit 'TEST_DB.TEST_SCHEMA.NOSUCH_SL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAMLIT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SL.",
            "DESCRIBE STREAMLIT nosuch_sl (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_AL.",
            "DESCRIBE ALERT nosuch_al (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE nosuch_ice (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE.",
            "DESCRIBE ICEBERG TABLE nosuch_ice");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_DB_ZQ_7F3A91.",
            "DESCRIBE ICEBERG TABLE nosuch_db_zq_7f3a91.s.t");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_ZQ' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_ZQ.",
            "DESCRIBE ICEBERG TABLE test_db.nosuch_schema_zq.t");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on WAREHOUSE "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Compute pool 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on COMPUTE POOL "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Network policy 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NETWORK POLICY "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (a)");
    }

    @Test
    public void tablesSecretsAndApps() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'. | syntax error line 1 at position 43 unexpected ')'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''x''. | syntax error "
                + "line 1 at position 54 unexpected 'y'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'b'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '.'. | syntax error "
                + "line 1 at position 46 unexpected '.'. | syntax error line 1 at position 48 unexpected ')'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'b'. | syntax error "
                + "line 1 at position 47 unexpected 'b'. | syntax error line 1 at position 48 unexpected ')'.",
            "DESCRIBE TABLE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax error "
                + "line 1 at position 40 unexpected ')'. | syntax error line 1 at position 44 unexpected ')'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax error "
                + "line 1 at position 46 unexpected ')'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''x''. | syntax error "
                + "line 1 at position 55 unexpected 'y'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'b'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '.'. | syntax error "
                + "line 1 at position 47 unexpected '.'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'. | syntax error "
                + "line 1 at position 48 unexpected 'b'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE SECRET nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '('. | syntax error "
                + "line 1 at position 46 unexpected ')'. | syntax error line 1 at position 50 unexpected ')'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''x''. | syntax error "
                + "line 1 at position 61 unexpected 'y'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '<EOF>'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'b'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '.'. | syntax error "
                + "line 1 at position 53 unexpected '.'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have APPLY MASKING POLICY granted on "
                + "ACCOUNT <ACCOUNT>.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'b'. | syntax error "
                + "line 1 at position 54 unexpected 'b'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE NETWORK RULE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax error "
                + "line 1 at position 49 unexpected ')'. | syntax error line 1 at position 53 unexpected ')'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '1'. | syntax error "
                + "line 1 at position 55 unexpected ')'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ''x''. | syntax error "
                + "line 1 at position 64 unexpected 'y'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '<EOF>'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'b'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '.'. | syntax error "
                + "line 1 at position 56 unexpected '.'. | syntax error line 1 at position 58 unexpected ')'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'b'. | syntax error "
                + "line 1 at position 57 unexpected 'b'. | syntax error line 1 at position 58 unexpected ')'.",
            "DESCRIBE PASSWORD POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                + "line 1 at position 42 unexpected ')'. | syntax error line 1 at position 46 unexpected ')'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax error "
                + "line 1 at position 48 unexpected ')'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''x''. | syntax error "
                + "line 1 at position 57 unexpected 'y'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'b'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '.'. | syntax error "
                + "line 1 at position 49 unexpected '.'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Notebook 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on NOTEBOOK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'b'. | syntax error "
                + "line 1 at position 50 unexpected 'b'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE NOTEBOOK nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '('. | syntax error "
                + "line 1 at position 43 unexpected ')'. | syntax error line 1 at position 47 unexpected ')'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '1'. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''x''. | syntax error "
                + "line 1 at position 58 unexpected 'y'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '<EOF>'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'b'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '.'. | syntax error "
                + "line 1 at position 50 unexpected '.'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Streamlit 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on STREAMLIT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'. | syntax error "
                + "line 1 at position 51 unexpected 'b'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE STREAMLIT nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'. | syntax error line 1 at position 43 unexpected ')'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''x''. | syntax error "
                + "line 1 at position 54 unexpected 'y'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'b'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '.'. | syntax error "
                + "line 1 at position 46 unexpected '.'. | syntax error line 1 at position 48 unexpected ')'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'b'. | syntax error "
                + "line 1 at position 47 unexpected 'b'. | syntax error line 1 at position 48 unexpected ')'.",
            "DESCRIBE ALERT nosuch_sig_zq_7f3a91 (a) x = (a b)");
    }

    @Test
    public void warehousesPoolsAndPolicies() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '('. | syntax error "
                + "line 1 at position 43 unexpected ')'. | syntax error line 1 at position 47 unexpected ')'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '1'. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''x''. | syntax error "
                + "line 1 at position 58 unexpected 'y'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '<EOF>'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'b'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '.'. | syntax error "
                + "line 1 at position 50 unexpected '.'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on WAREHOUSE "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'. | syntax error "
                + "line 1 at position 51 unexpected 'b'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE WAREHOUSE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '('. | syntax error "
                + "line 1 at position 46 unexpected ')'. | syntax error line 1 at position 50 unexpected ')'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''x''. | syntax error "
                + "line 1 at position 61 unexpected 'y'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '<EOF>'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'b'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '.'. | syntax error "
                + "line 1 at position 53 unexpected '.'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Compute pool 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on COMPUTE POOL "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'b'. | syntax error "
                + "line 1 at position 54 unexpected 'b'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('. | syntax error "
                + "line 1 at position 51 unexpected ')'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''. | syntax error "
                + "line 1 at position 66 unexpected 'y'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'. | syntax error "
                + "line 1 at position 58 unexpected '.'. | syntax error line 1 at position 60 unexpected ')'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'b'. | syntax error "
                + "line 1 at position 59 unexpected 'b'. | syntax error line 1 at position 60 unexpected ')'.",
            "DESCRIBE PROJECTION POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '('. | syntax error "
                + "line 1 at position 52 unexpected ')'. | syntax error line 1 at position 56 unexpected ')'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '1'. | syntax error "
                + "line 1 at position 58 unexpected ')'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ''x''. | syntax error "
                + "line 1 at position 67 unexpected 'y'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '<EOF>'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'b'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '.'. | syntax error "
                + "line 1 at position 59 unexpected '.'. | syntax error line 1 at position 61 unexpected ')'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'b'. | syntax error "
                + "line 1 at position 60 unexpected 'b'. | syntax error line 1 at position 61 unexpected ')'.",
            "DESCRIBE AGGREGATION POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax error "
                + "line 1 at position 45 unexpected ')'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax error "
                + "line 1 at position 51 unexpected ')'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ''x''. | syntax error "
                + "line 1 at position 60 unexpected 'y'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'b'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '.'. | syntax error "
                + "line 1 at position 52 unexpected '.'. | syntax error line 1 at position 54 unexpected ')'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'b'. | syntax error "
                + "line 1 at position 53 unexpected 'b'. | syntax error line 1 at position 54 unexpected ')'.",
            "DESCRIBE JOIN POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('. | syntax error "
                + "line 1 at position 48 unexpected ')'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '<EOF>'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'. | syntax error "
                + "line 1 at position 55 unexpected '.'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'b'. | syntax error "
                + "line 1 at position 56 unexpected 'b'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE MASKING POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('. | syntax error "
                + "line 1 at position 51 unexpected ')'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''. | syntax error "
                + "line 1 at position 66 unexpected 'y'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'. | syntax error "
                + "line 1 at position 58 unexpected '.'. | syntax error line 1 at position 60 unexpected ')'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'b'. | syntax error "
                + "line 1 at position 59 unexpected 'b'. | syntax error line 1 at position 60 unexpected ')'.",
            "DESCRIBE ROW ACCESS POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
    }

    @Test
    public void containersAndServices() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('. | syntax error "
                + "line 1 at position 48 unexpected ')'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '<EOF>'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'. | syntax error "
                + "line 1 at position 55 unexpected '.'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Network policy 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NETWORK POLICY "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'b'. | syntax error "
                + "line 1 at position 56 unexpected 'b'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE NETWORK POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax error "
                + "line 1 at position 45 unexpected ')'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax error "
                + "line 1 at position 51 unexpected ')'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ''x''. | syntax error "
                + "line 1 at position 60 unexpected 'y'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'b'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '.'. | syntax error "
                + "line 1 at position 52 unexpected '.'. | syntax error line 1 at position 54 unexpected ')'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FILE FORMAT TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'b'. | syntax error "
                + "line 1 at position 53 unexpected 'b'. | syntax error line 1 at position 54 unexpected ')'.",
            "DESCRIBE FILE FORMAT nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '('. | syntax error "
                + "line 1 at position 37 unexpected ')'. | syntax error line 1 at position 41 unexpected ')'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ''x''. | syntax error "
                + "line 1 at position 52 unexpected 'y'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '<EOF>'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'b'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '.'. | syntax error "
                + "line 1 at position 44 unexpected '.'. | syntax error line 1 at position 46 unexpected ')'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED Unsupported feature 'TAG'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'b'. | syntax error "
                + "line 1 at position 45 unexpected 'b'. | syntax error line 1 at position 46 unexpected ')'.",
            "DESCRIBE TAG nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax error "
                + "line 1 at position 40 unexpected ')'. | syntax error line 1 at position 44 unexpected ')'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax error "
                + "line 1 at position 46 unexpected ')'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''x''. | syntax error "
                + "line 1 at position 55 unexpected 'y'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'b'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '.'. | syntax error "
                + "line 1 at position 47 unexpected '.'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'. | syntax error "
                + "line 1 at position 48 unexpected 'b'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE SCHEMA nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                + "line 1 at position 42 unexpected ')'. | syntax error line 1 at position 46 unexpected ')'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax error "
                + "line 1 at position 48 unexpected ')'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''x''. | syntax error "
                + "line 1 at position 57 unexpected 'y'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'b'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '.'. | syntax error "
                + "line 1 at position 49 unexpected '.'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'b'. | syntax error "
                + "line 1 at position 50 unexpected 'b'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE DATABASE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '('. | syntax error "
                + "line 1 at position 55 unexpected ')'. | syntax error line 1 at position 59 unexpected ')'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '1'. | syntax error "
                + "line 1 at position 61 unexpected ')'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ''x''. | syntax error "
                + "line 1 at position 70 unexpected 'y'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '<EOF>'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'b'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '.'. | syntax error "
                + "line 1 at position 62 unexpected '.'. | syntax error line 1 at position 64 unexpected ')'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' "
                + "does not exist or not authorized. Your primary role <ROLE> must have at least one privilege "
                + "granted on CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'b'. | syntax error "
                + "line 1 at position 63 unexpected 'b'. | syntax error line 1 at position 64 unexpected ')'.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax error "
                + "line 1 at position 45 unexpected ')'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax error "
                + "line 1 at position 51 unexpected ')'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ''x''. | syntax error "
                + "line 1 at position 60 unexpected 'y'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'b'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '.'. | syntax error "
                + "line 1 at position 52 unexpected '.'. | syntax error line 1 at position 54 unexpected ')'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'b'. | syntax error "
                + "line 1 at position 53 unexpected 'b'. | syntax error line 1 at position 54 unexpected ')'.",
            "DESCRIBE EVENT TABLE nosuch_sig_zq_7f3a91 (a) x = (a b)");
    }

    @Test
    public void tableKindsAndOtherPolicies() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '('. | syntax error "
                + "line 1 at position 47 unexpected ')'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '1'. | syntax error "
                + "line 1 at position 53 unexpected ')'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ''x''. | syntax error "
                + "line 1 at position 62 unexpected 'y'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '<EOF>'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'b'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '.'. | syntax error "
                + "line 1 at position 54 unexpected '.'. | syntax error line 1 at position 56 unexpected ')'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'b'. | syntax error "
                + "line 1 at position 55 unexpected 'b'. | syntax error line 1 at position 56 unexpected ')'.",
            "DESCRIBE ICEBERG TABLE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('. | syntax error "
                + "line 1 at position 48 unexpected ')'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '<EOF>'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'. | syntax error "
                + "line 1 at position 55 unexpected '.'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'b'. | syntax error "
                + "line 1 at position 56 unexpected 'b'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE EXTERNAL TABLE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '('. | syntax error "
                + "line 1 at position 47 unexpected ')'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '1'. | syntax error "
                + "line 1 at position 53 unexpected ')'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ''x''. | syntax error "
                + "line 1 at position 62 unexpected 'y'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '<EOF>'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'b'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '.'. | syntax error "
                + "line 1 at position 54 unexpected '.'. | syntax error line 1 at position 56 unexpected ')'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'b'. | syntax error "
                + "line 1 at position 55 unexpected 'b'. | syntax error line 1 at position 56 unexpected ')'.",
            "DESCRIBE DYNAMIC TABLE nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('. | syntax error "
                + "line 1 at position 51 unexpected ')'. | syntax error line 1 at position 55 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '1'. | syntax error "
                + "line 1 at position 57 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''x''. | syntax error "
                + "line 1 at position 66 unexpected 'y'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'b'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '.'. | syntax error "
                + "line 1 at position 58 unexpected '.'. | syntax error line 1 at position 60 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'b'. | syntax error "
                + "line 1 at position 59 unexpected 'b'. | syntax error line 1 at position 60 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('. | syntax error "
                + "line 1 at position 40 unexpected ')'. | syntax error line 1 at position 44 unexpected ')'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax error "
                + "line 1 at position 46 unexpected ')'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''x''. | syntax error "
                + "line 1 at position 55 unexpected 'y'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'b'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '.'. | syntax error "
                + "line 1 at position 47 unexpected '.'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on STREAM NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'. | syntax error "
                + "line 1 at position 48 unexpected 'b'. | syntax error line 1 at position 49 unexpected ')'.",
            "DESCRIBE STREAM nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('. | syntax error "
                + "line 1 at position 48 unexpected ')'. | syntax error line 1 at position 52 unexpected ')'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '1'. | syntax error "
                + "line 1 at position 54 unexpected ')'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | syntax error "
                + "line 1 at position 63 unexpected 'y'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '<EOF>'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'b'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected '.'. | syntax error "
                + "line 1 at position 55 unexpected '.'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'b'. | syntax error "
                + "line 1 at position 56 unexpected 'b'. | syntax error line 1 at position 57 unexpected ')'.",
            "DESCRIBE SESSION POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '('. | syntax error "
                + "line 1 at position 55 unexpected ')'. | syntax error line 1 at position 59 unexpected ')'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '1'. | syntax error "
                + "line 1 at position 61 unexpected ')'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ''x''. | syntax error "
                + "line 1 at position 70 unexpected 'y'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected '<EOF>'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'b'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '.'. | syntax error "
                + "line 1 at position 62 unexpected '.'. | syntax error line 1 at position 64 unexpected ')'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' "
                + "does not exist or not authorized. Your primary role <ROLE> must have at least one privilege "
                + "granted on POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'b'. | syntax error "
                + "line 1 at position 63 unexpected 'b'. | syntax error line 1 at position 64 unexpected ')'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_sig_zq_7f3a91 (a) x = (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                + "line 1 at position 42 unexpected ')'. | syntax error line 1 at position 46 unexpected ')'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax error "
                + "line 1 at position 48 unexpected ')'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''x''. | syntax error "
                + "line 1 at position 57 unexpected 'y'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (a) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'b'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '.'. | syntax error "
                + "line 1 at position 49 unexpected '.'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (a) TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'b'. | syntax error "
                + "line 1 at position 50 unexpected 'b'. | syntax error line 1 at position 51 unexpected ')'.",
            "DESCRIBE SEQUENCE nosuch_sig_zq_7f3a91 (a) x = (a b)");
    }
}
