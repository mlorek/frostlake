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
 * What follows the name in a DROP, a DESCRIBE and an ALTER of the kinds Frostlake models no object of. A
 * DESCRIBE reads a signature and properties after the name of a kind the account knows — a snapshot, a
 * dataset, a model, a service, an image repository, an external volume — so a surplus word is refused at the
 * statement's end; after any other word the statement ends with the name. A DROP of such a kind reads a
 * signature and a behaviour word, PURGE being refused in its one sentence. RESOURCE without MONITOR and
 * STORAGE without LIFECYCLE read the words after them in pairs, and the kinds named by two words read a wrong
 * second word as the name. ALTER EXTERNAL TABLE refuses SET TAG at the SET, UNSET TAG at the tag's name and
 * any other UNSET as no property (live-verified).
 */
public class UnmodelledKindTailTest extends MeasuredCellsTestSupport {

    @Test
    public void theMeasuredRows() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'.",
            "DROP HYBRID TABLE nosuch_hy");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'.",
            "DROP HYBRID TABLE IF EXISTS nosuch_hy");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'METRIC'.",
            "DROP DATA METRIC FUNCTION nosuch_dmf(TABLE(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'METRIC'.",
            "DROP DATA METRIC FUNCTION IF EXISTS nosuch_dmf(TABLE(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'SET'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET TAG tg = 'y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'tg'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET TAG tg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'SET'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET TAG tg = 'y', tg2 = 'z'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'tg'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET TAG tg, tg2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'COMMENT' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 19 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'.",
            "DESCRIBE SEMANTIC VIEW nosuch_sv x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 18 unexpected '<EOF>'.",
            "DESCRIBE STORAGE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE MONITOR nosuch_rm_w7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | Semantic View 'TEST_DB.TEST_SCHEMA.NOSUCH_SV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEMANTIC VIEW "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SV.",
            "DESCRIBE SEMANTIC VIEW nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'.",
            "DESCRIBE SEMANTIC VIEW nosuch_sv x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'.",
            "DESCRIBE STORAGE");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_RESOURCE_MONITOR'.",
            "DESCRIBE RESOURCE MONITOR nosuch_rm_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'.",
            "DESCRIBE RESOURCE MONITOR nosuch_rm_w7 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected 'y'.",
            "DESCRIBE RESOURCE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "DESCRIBE STORAGE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
            "DESCRIBE SEMANTIC x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "DESCRIBE SEMANTIC");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE NOTEBOOK nosuch_nb x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE STREAMLIT nosuch_st x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE SNAPSHOT nosuch_sn x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DESCRIBE SERVICE nosuch_svc x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'.",
            "DESCRIBE IMAGE REPOSITORY nosuch_ir x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'.",
            "DESCRIBE COMPUTE POOL nosuch_sig_zq_7f3a91 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "DESCRIBE DATASET nosuch_ds x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE MODEL nosuch_m x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "DESCRIBE GIT REPOSITORY nosuch_gr x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'.",
            "DESCRIBE EXTERNAL VOLUME nosuch_sig_zq_7f3a91 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE NETWORK RULE nosuch_nr x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "DESCRIBE PACKAGES POLICY nosuch_pp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "DESCRIBE SESSION POLICY nosuch_sp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_ap x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "DESCRIBE PRIVACY POLICY nosuch_pv x");
    }

    @Test
    public void aSignatureAndABehaviourAfterADrop() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP IMAGE REPOSITORY IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP IMAGE REPOSITORY IF EXISTS nosuch_purge_zq_7f3a91");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 FORCE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP IMAGE REPOSITORY IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP SNAPSHOT IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP CONNECTION IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SIG_ZQ_7F3A91'.",
            "DROP SNAPSHOT IF EXISTS nosuch_sig_zq_7f3a91 PURGE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP DATASET IF EXISTS nosuch_sig_zq_7f3a91 (a)");
    }

    @Test
    public void thePlainWordsOfAListing() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'x'.",
            "SHOW DBT x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'x'.",
            "SHOW GIT x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "SHOW SEMANTIC x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SHOW AUTHENTICATION x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "SHOW PRIVACY x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "SHOW DBT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "SHOW GIT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SHOW SEMANTIC");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "SHOW AUTHENTICATION");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'.",
            "SHOW PRIVACY");
    }

    @Test
    public void afterADescribedName() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'y'.",
            "DESCRIBE NOSUCHKIND x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'y'.",
            "DESCRIBE NOSUCHKIND x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('.",
            "DESCRIBE NOSUCHKIND x (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TYPE'.",
            "DESCRIBE NOSUCHKIND x TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TYPE'.",
            "DESCRIBE NOSUCHKIND x TYPE = STAGE y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ','.",
            "DESCRIBE NOSUCHKIND x, y");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.NOSUCH_SN' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SN.",
            "DESCRIBE SNAPSHOT nosuch_sn");
        assertAnswer(
            "REFUSED SQL compilation error: | Dataset 'TEST_DB.TEST_SCHEMA.NOSUCH_DS' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on DATASET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DS.",
            "DESCRIBE DATASET nosuch_ds");
        assertAnswer(
            "REFUSED SQL compilation error: | Model 'TEST_DB.TEST_SCHEMA.NOSUCH_M' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on MODEL "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_M.",
            "DESCRIBE MODEL nosuch_m");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "DESCRIBE CONNECTION nosuch_sig_zq_7f3a91 x");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.NOSUCH_SN' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SN.",
            "DESCRIBE SNAPSHOT nosuch_sn (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SNAPSHOT nosuch_sn x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE DATASET nosuch_ds x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Model 'TEST_DB.TEST_SCHEMA.NOSUCH_M' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on MODEL "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_M.",
            "DESCRIBE MODEL nosuch_m TYPE = STAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Model 'TEST_DB.TEST_SCHEMA.NOSUCH_M' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on MODEL "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_M.",
            "DESCRIBE MODEL nosuch_m (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "DESCRIBE SNAPSHOT nosuch_sn x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "DESCRIBE DATASET nosuch_ds x y");
        assertAnswer(
            "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH_SVC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SVC.",
            "DESCRIBE SERVICE nosuch_svc (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE SERVICE nosuch_svc x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "DESCRIBE SERVICE nosuch_svc x y");
        assertAnswer(
            "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH_SVC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SVC.",
            "DESCRIBE SERVICE nosuch_svc");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_IMAGE_REPOSITORY'.",
            "DESCRIBE IMAGE REPOSITORY nosuch_ir (a)");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_IMAGE_REPOSITORY'.",
            "DESCRIBE IMAGE REPOSITORY nosuch_ir x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'.",
            "DESCRIBE IMAGE REPOSITORY nosuch_ir x y");
        assertAnswer(
            "REFUSED SQL compilation error: | External volume 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE EXTERNAL VOLUME nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE EXTERNAL VOLUME nosuch_sig_zq_7f3a91 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'y'.",
            "DESCRIBE EXTERNAL VOLUME nosuch_sig_zq_7f3a91 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | External volume 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE EXTERNAL VOLUME nosuch_sig_zq_7f3a91");
        assertAnswer(
            "REFUSED SQL compilation error: | Connection 'NOSUCH_SIG_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on CONNECTION "
                + "NOSUCH_SIG_ZQ_7F3A91.",
            "DESCRIBE CONNECTION nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE CONNECTION nosuch_sig_zq_7f3a91 x = 1");
        answer(
            "DESCRIBE DATASET nosuch_ds x y z");
    }

    @Test
    public void theWordsOfAKind() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected 'y'. | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 19 unexpected 'y'. | syntax error line 1 at position 22 unexpected "
                + "'<EOF>'.",
            "DESCRIBE STORAGE x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 19 unexpected ';'.",
            "DESCRIBE RESOURCE x;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 18 unexpected ';'.",
            "DESCRIBE STORAGE x;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DESCRIBE STORAGE LIFECYCLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "DESCRIBE STORAGE LIFECYCLE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE STORAGE LIFECYCLE POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '<EOF>'.",
            "DESCRIBE STORAGE LIFECYCLE POLICY nosuch_slp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE MONITOR");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax error "
                + "line 1 at position 16 unexpected '<EOF>'.",
            "DESCRIBE DBT x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '\"x\"'. | syntax "
                + "error line 1 at position 21 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE \"x\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 20 unexpected '<EOF>'.",
            "DESCRIBE STORAGE \"x\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax error "
                + "line 1 at position 15 unexpected '<EOF>'.",
            "DESC RESOURCE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 14 unexpected '<EOF>'.",
            "DESC STORAGE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'.",
            "DESCRIBE STORAGE;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected 'y'.",
            "DESCRIBE RESOURCE x y;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "DESCRIBE STORAGE x y;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected '<EOF>'.",
            "DESCRIBE SEMANTIC x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax error "
                + "line 1 at position 16 unexpected '<EOF>'.",
            "DESCRIBE GIT x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'z'.",
            "DESCRIBE DBT x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "DESCRIBE DBT x;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected 'y'. | syntax error line 1 at position 24 unexpected 'w'.",
            "DESCRIBE RESOURCE x y z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected '<EOF>'.",
            "DESCRIBE AUTHENTICATION x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected '<EOF>'.",
            "DESCRIBE SESSION x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected '<EOF>'.",
            "DESCRIBE PACKAGES x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected '<EOF>'.",
            "DESCRIBE PRIVACY x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'STORAGE'. | syntax "
                + "error line 1 at position 19 unexpected 'y'. | syntax error line 1 at position 23 unexpected 'w'.",
            "DESCRIBE STORAGE x y z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax error "
                + "line 1 at position 16 unexpected ';'.",
            "DESCRIBE DBT x y;");
    }

    @Test
    public void dropsAndTheTagActions() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SIG_ZQ_7F3A91 already dropped).",
            "DROP MODEL IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '('.",
            "DROP NOSUCHKIND_ZQ IF EXISTS nosuch_sig_zq_7f3a91 (a)");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SIG_ZQ_7F3A91'.",
            "DROP SNAPSHOT IF EXISTS nosuch_sig_zq_7f3a91 (a) PURGE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP IMAGE REPOSITORY IF EXISTS nosuch_purge_zq_7f3a91 (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'PURGE'.",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 FORCE PURGE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP IMAGE REPOSITORY IF EXISTS nosuch_purge_zq_7f3a91 RESTRICT");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP IMAGE REPOSITORY IF EXISTS nosuch_purge_zq_7f3a91 (a) PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'METRIC'.",
            "DROP DATA METRIC FUNCTION");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'METRIC'.",
            "DROP DATA METRIC FUNCTION IF EXISTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'METRIC'.",
            "DROP DATA METRIC x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "DROP DATA x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'SET'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'TAG' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SET'.",
            "ALTER EXTERNAL TABLE nosuch_ext SET TAG tg = 'y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'tg'.",
            "ALTER EXTERNAL TABLE nosuch_ext UNSET TAG tg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'SET'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_s.nosuch_ext SET TAG tg = 'y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'tg'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_s.nosuch_ext UNSET TAG tg");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'tg'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET TAG tg;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'SET'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET TAG tg = 'y';");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'x' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '\"tg\"'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET TAG \"tg\"");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value 'null' for property 'AUTO_REFRESH'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET AUTO_REFRESH");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'X' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET X");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property '\"x\"' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET \"x\"");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'x' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET x, y");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value 'null' for property 'auto_refresh'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET auto_refresh");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'comment' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET comment = 'x'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext SET auto_refresh = TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'tg'.",
            "ALTER EXTERNAL TABLE IF EXISTS nosuch_ext UNSET TAG tg x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'x' for 'EXTERNAL_TABLE'",
            "ALTER EXTERNAL TABLE nosuch_ext UNSET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'CASCADE'.",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 FORCE CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 (a) FORCE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('.",
            "DROP SERVICE IF EXISTS nosuch_purge_zq_7f3a91 FORCE (a)");
    }
}
