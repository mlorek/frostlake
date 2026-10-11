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
 * DESCRIBE of the kinds Frostlake models no object of. A resource monitor, an image repository and an
 * organization are features the account does not support, whatever is named; every other kind misses its
 * object as a DROP of it does, a share's qualifier being its account. DESCRIBE EXTERNAL TABLE describes
 * whatever relation the name reaches and misses as an external table. Every answer is live-verified.
 */
public class UnmodelledKindDescribeTest extends MeasuredCellsTestSupport {

    @Test
    public void aKindThatHoldsNoObjectIsDescribed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_RESOURCE_MONITOR'.",
            "DESCRIBE RESOURCE MONITOR nosuch_rm_w7");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_RESOURCE_MONITOR'.",
            "DESC RESOURCE MONITOR nosuch_s.nosuch_rm_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE RESOURCE MONITOR");
        assertAnswer(
            "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SP.",
            "DESCRIBE SESSION POLICY nosuch_sp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC SESSION POLICY nosuch_s.nosuch_sp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE SESSION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.NOSUCH_SH_W7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SHARE "
                + "\"<ACCOUNT>.NOSUCH_SH_W7\".",
            "DESCRIBE SHARE nosuch_sh_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.NOSUCH_SH_W7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SHARE "
                + "\"<ACCOUNT>.NOSUCH_SH_W7\".",
            "DESC SHARE nosuch_s.nosuch_sh_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "DESCRIBE SHARE");
        assertAnswer(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_W7.",
            "DESCRIBE APPLICATION nosuch_app_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DESC APPLICATION nosuch_s.nosuch_app_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "DESCRIBE APPLICATION");
        assertAnswer(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_W7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_W7.",
            "DESCRIBE APPLICATION PACKAGE nosuch_pkg_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DESC APPLICATION PACKAGE nosuch_s.nosuch_pkg_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Application 'PACKAGE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE PACKAGE.",
            "DESCRIBE APPLICATION PACKAGE");
        assertAnswer(
            "REFUSED SQL compilation error: | DBT PROJECT 'TEST_DB.TEST_SCHEMA.NOSUCH_DBT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on DBT PROJECT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DBT.",
            "DESCRIBE DBT PROJECT nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC DBT PROJECT nosuch_s.nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "DESCRIBE DBT PROJECT");
        assertAnswer(
            "REFUSED SQL compilation error: | Git repository 'TEST_DB.TEST_SCHEMA.NOSUCH_GIT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_GIT.",
            "DESCRIBE GIT REPOSITORY nosuch_git");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC GIT REPOSITORY nosuch_s.nosuch_git");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE GIT REPOSITORY");
        assertAnswer(
            "REFUSED SQL compilation error: | Semantic View 'TEST_DB.TEST_SCHEMA.NOSUCH_SV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEMANTIC VIEW "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SV.",
            "DESCRIBE SEMANTIC VIEW nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC SEMANTIC VIEW nosuch_s.nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "DESCRIBE SEMANTIC VIEW");
        assertAnswer(
            "REFUSED SQL compilation error: | Packages policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PKP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PKP.",
            "DESCRIBE PACKAGES POLICY nosuch_pkp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC PACKAGES POLICY nosuch_s.nosuch_pkp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "DESCRIBE PACKAGES POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.NOSUCH_AUP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_AUP.",
            "DESCRIBE AUTHENTICATION POLICY nosuch_aup");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC AUTHENTICATION POLICY nosuch_s.nosuch_aup");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE AUTHENTICATION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PRP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PRP.",
            "DESCRIBE PRIVACY POLICY nosuch_prp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC PRIVACY POLICY nosuch_s.nosuch_prp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "DESCRIBE PRIVACY POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Storage lifecycle policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SLP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SLP.",
            "DESCRIBE STORAGE LIFECYCLE POLICY nosuch_slp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC STORAGE LIFECYCLE POLICY nosuch_s.nosuch_slp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "DESCRIBE STORAGE LIFECYCLE POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Organization profile 'NOSUCH_OP' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ORGANIZATION PROFILE "
                + "NOSUCH_OP.",
            "DESCRIBE ORGANIZATION PROFILE nosuch_op");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DESC ORGANIZATION PROFILE nosuch_s.nosuch_op");
        assertAnswer(
            "REFUSED Unsupported feature 'ORGANIZATION'.",
            "DESCRIBE ORGANIZATION PROFILE");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.NOSUCH_SNAP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SNAP.",
            "DESCRIBE SNAPSHOT nosuch_snap");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESC SNAPSHOT nosuch_s.nosuch_snap");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "DESCRIBE SNAPSHOT");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_IMAGE_REPOSITORY'.",
            "DESCRIBE IMAGE REPOSITORY nosuch_ir");
        assertAnswer(
            "REFUSED Unsupported feature 'TOK_IMAGE_REPOSITORY'.",
            "DESC IMAGE REPOSITORY nosuch_s.nosuch_ir");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "DESCRIBE IMAGE REPOSITORY");
    }

    @Test
    public void theWordsOfADescribedKind() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "DESCRIBE DBT x");
        assertAnswer(
            "REFUSED Unsupported feature 'ORGANIZATION'.",
            "DESCRIBE ORGANIZATION nosuch_org_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Connection 'NOSUCH_CONN_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on CONNECTION NOSUCH_CONN_W7.",
            "DESCRIBE CONNECTION nosuch_conn_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.NOSUCH_SNAP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SNAP.",
            "DESCRIBE SNAPSHOT nosuch_snap TYPE = COLUMNS");
        assertAnswer(
            "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SP.",
            "DESCRIBE SESSION POLICY nosuch_sp (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DESCRIBE GIT REPOSITORY nosuch_db_w7.s.x");
    }
}
