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
 * DROP of the kinds the account knows and Frostlake models no object of - a resource monitor, a session, a
 * packages, an authentication, a privacy and a storage lifecycle policy, an application and its package, a DBT
 * project, a Git repository, a semantic view, an organization and its profile, a snapshot, a connection. The
 * name resolves as any object's does and nothing is found: a schema's kind misses by its full path after its
 * schema and database, an account's kind by its bare name, and IF EXISTS answers as a drop of nothing does. A
 * kind's first word alone is refused at the word after it, and a table or a view is no semantic view. Every
 * answer is live-verified.
 */
public class UnmodelledKindDropTest extends MeasuredCellsTestSupport {

    @Test
    public void aKindThatHoldsNoObjectIsDropped() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Resource monitor 'NOSUCH_RM_W7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on RESOURCE MONITOR "
                + "NOSUCH_RM_W7.",
            "DROP RESOURCE MONITOR nosuch_rm_w7");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_RM_W7 already dropped).",
            "DROP RESOURCE MONITOR IF EXISTS nosuch_rm_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP RESOURCE MONITOR nosuch_s.nosuch_rm_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'x'.",
            "DROP RESOURCE MONITOR nosuch_rm_w7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
            "DROP RESOURCE MONITOR");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_RM_W7'.",
            "DROP RESOURCE MONITOR IF EXISTS nosuch_rm_w7 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Resource monitor '\"nosuch_rm_w7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on RESOURCE MONITOR "
                + "\"nosuch_rm_w7\".",
            "DROP RESOURCE MONITOR \"nosuch_rm_w7\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SP.",
            "DROP SESSION POLICY nosuch_sp");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SP already dropped).",
            "DROP SESSION POLICY IF EXISTS nosuch_sp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP SESSION POLICY nosuch_s.nosuch_sp");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP SESSION POLICY nosuch_db_w7.s.nosuch_sp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "DROP SESSION POLICY nosuch_sp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "DROP SESSION POLICY");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SP'.",
            "DROP SESSION POLICY IF EXISTS nosuch_sp PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.\"nosuch_sp\"' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_sp\".",
            "DROP SESSION POLICY \"nosuch_sp\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.NOSUCH_SH_W7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SHARE "
                + "\"<ACCOUNT>.NOSUCH_SH_W7\".",
            "DROP SHARE nosuch_sh_w7");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SH_W7 already dropped).",
            "DROP SHARE IF EXISTS nosuch_sh_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP SHARE nosuch_s.nosuch_sh_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "DROP SHARE nosuch_sh_w7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "DROP SHARE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SH_W7'.",
            "DROP SHARE IF EXISTS nosuch_sh_w7 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.nosuch_sh_w7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SHARE "
                + "\"<ACCOUNT>.nosuch_sh_w7\".",
            "DROP SHARE \"nosuch_sh_w7\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_W7.",
            "DROP APPLICATION nosuch_app_w7");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_APP_W7 already dropped).",
            "DROP APPLICATION IF EXISTS nosuch_app_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP APPLICATION nosuch_s.nosuch_app_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "DROP APPLICATION nosuch_app_w7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "DROP APPLICATION");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_APP_W7'.",
            "DROP APPLICATION IF EXISTS nosuch_app_w7 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Application '\"nosuch_app_w7\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "\"nosuch_app_w7\".",
            "DROP APPLICATION \"nosuch_app_w7\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_W7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_W7.",
            "DROP APPLICATION PACKAGE nosuch_pkg_w7");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PKG_W7 already dropped).",
            "DROP APPLICATION PACKAGE IF EXISTS nosuch_pkg_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP APPLICATION PACKAGE nosuch_s.nosuch_pkg_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'.",
            "DROP APPLICATION PACKAGE nosuch_pkg_w7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | Application 'PACKAGE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE PACKAGE.",
            "DROP APPLICATION PACKAGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PKG_W7'.",
            "DROP APPLICATION PACKAGE IF EXISTS nosuch_pkg_w7 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Application package '\"nosuch_pkg_w7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "\"nosuch_pkg_w7\".",
            "DROP APPLICATION PACKAGE \"nosuch_pkg_w7\"");
        assertAnswer(
            "REFUSED SQL compilation error: | DBT PROJECT 'TEST_DB.TEST_SCHEMA.NOSUCH_DBT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on DBT PROJECT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DBT.",
            "DROP DBT PROJECT nosuch_dbt");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_DBT already dropped).",
            "DROP DBT PROJECT IF EXISTS nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP DBT PROJECT nosuch_s.nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP DBT PROJECT nosuch_db_w7.s.nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "DROP DBT PROJECT nosuch_dbt x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "DROP DBT PROJECT");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_DBT'.",
            "DROP DBT PROJECT IF EXISTS nosuch_dbt PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | DBT PROJECT 'TEST_DB.TEST_SCHEMA.\"nosuch_dbt\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on DBT PROJECT "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_dbt\".",
            "DROP DBT PROJECT \"nosuch_dbt\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Git repository 'TEST_DB.TEST_SCHEMA.NOSUCH_GIT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_GIT.",
            "DROP GIT REPOSITORY nosuch_git");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_GIT already dropped).",
            "DROP GIT REPOSITORY IF EXISTS nosuch_git");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP GIT REPOSITORY nosuch_s.nosuch_git");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP GIT REPOSITORY nosuch_db_w7.s.nosuch_git");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "DROP GIT REPOSITORY nosuch_git x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "DROP GIT REPOSITORY");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_GIT'.",
            "DROP GIT REPOSITORY IF EXISTS nosuch_git PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Git repository 'TEST_DB.TEST_SCHEMA.\"nosuch_git\"' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_git\".",
            "DROP GIT REPOSITORY \"nosuch_git\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Semantic View 'TEST_DB.TEST_SCHEMA.NOSUCH_SV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEMANTIC VIEW "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SV.",
            "DROP SEMANTIC VIEW nosuch_sv");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SV already dropped).",
            "DROP SEMANTIC VIEW IF EXISTS nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP SEMANTIC VIEW nosuch_s.nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP SEMANTIC VIEW nosuch_db_w7.s.nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'.",
            "DROP SEMANTIC VIEW nosuch_sv x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
            "DROP SEMANTIC VIEW");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SV'.",
            "DROP SEMANTIC VIEW IF EXISTS nosuch_sv PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Semantic View 'TEST_DB.TEST_SCHEMA.\"nosuch_sv\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on SEMANTIC "
                + "VIEW TEST_DB.TEST_SCHEMA.\"nosuch_sv\".",
            "DROP SEMANTIC VIEW \"nosuch_sv\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Packages policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PKP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PKP.",
            "DROP PACKAGES POLICY nosuch_pkp");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PKP already dropped).",
            "DROP PACKAGES POLICY IF EXISTS nosuch_pkp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP PACKAGES POLICY nosuch_s.nosuch_pkp");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP PACKAGES POLICY nosuch_db_w7.s.nosuch_pkp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "DROP PACKAGES POLICY nosuch_pkp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "DROP PACKAGES POLICY");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PKP'.",
            "DROP PACKAGES POLICY IF EXISTS nosuch_pkp PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Packages policy 'TEST_DB.TEST_SCHEMA.\"nosuch_pkp\"' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_pkp\".",
            "DROP PACKAGES POLICY \"nosuch_pkp\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.NOSUCH_AUP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_AUP.",
            "DROP AUTHENTICATION POLICY nosuch_aup");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_AUP already dropped).",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_aup");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP AUTHENTICATION POLICY nosuch_s.nosuch_aup");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP AUTHENTICATION POLICY nosuch_db_w7.s.nosuch_aup");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'.",
            "DROP AUTHENTICATION POLICY nosuch_aup x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DROP AUTHENTICATION POLICY");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_AUP'.",
            "DROP AUTHENTICATION POLICY IF EXISTS nosuch_aup PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.\"nosuch_aup\"' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.\"nosuch_aup\".",
            "DROP AUTHENTICATION POLICY \"nosuch_aup\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PRP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PRP.",
            "DROP PRIVACY POLICY nosuch_prp");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PRP already dropped).",
            "DROP PRIVACY POLICY IF EXISTS nosuch_prp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP PRIVACY POLICY nosuch_s.nosuch_prp");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP PRIVACY POLICY nosuch_db_w7.s.nosuch_prp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "DROP PRIVACY POLICY nosuch_prp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "DROP PRIVACY POLICY");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PRP'.",
            "DROP PRIVACY POLICY IF EXISTS nosuch_prp PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.\"nosuch_prp\"' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_prp\".",
            "DROP PRIVACY POLICY \"nosuch_prp\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Storage lifecycle policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SLP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_SLP.",
            "DROP STORAGE LIFECYCLE POLICY nosuch_slp");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SLP already dropped).",
            "DROP STORAGE LIFECYCLE POLICY IF EXISTS nosuch_slp");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP STORAGE LIFECYCLE POLICY nosuch_s.nosuch_slp");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP STORAGE LIFECYCLE POLICY nosuch_db_w7.s.nosuch_slp");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'.",
            "DROP STORAGE LIFECYCLE POLICY nosuch_slp x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "DROP STORAGE LIFECYCLE POLICY");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SLP'.",
            "DROP STORAGE LIFECYCLE POLICY IF EXISTS nosuch_slp PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Storage lifecycle policy 'TEST_DB.TEST_SCHEMA.\"nosuch_slp\"' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.\"nosuch_slp\".",
            "DROP STORAGE LIFECYCLE POLICY \"nosuch_slp\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Organization profile 'NOSUCH_OP' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ORGANIZATION PROFILE "
                + "NOSUCH_OP.",
            "DROP ORGANIZATION PROFILE nosuch_op");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_OP already dropped).",
            "DROP ORGANIZATION PROFILE IF EXISTS nosuch_op");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP ORGANIZATION PROFILE nosuch_s.nosuch_op");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'x'.",
            "DROP ORGANIZATION PROFILE nosuch_op x");
        assertAnswer(
            "REFUSED SQL compilation error: | Organization 'PROFILE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ORGANIZATION PROFILE.",
            "DROP ORGANIZATION PROFILE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_OP'.",
            "DROP ORGANIZATION PROFILE IF EXISTS nosuch_op PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Organization profile '\"nosuch_op\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ORGANIZATION "
                + "PROFILE \"nosuch_op\".",
            "DROP ORGANIZATION PROFILE \"nosuch_op\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.NOSUCH_SNAP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SNAP.",
            "DROP SNAPSHOT nosuch_snap");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SNAP already dropped).",
            "DROP SNAPSHOT IF EXISTS nosuch_snap");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP SNAPSHOT nosuch_s.nosuch_snap");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP SNAPSHOT nosuch_db_w7.s.nosuch_snap");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "DROP SNAPSHOT nosuch_snap x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "DROP SNAPSHOT");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_SNAP'.",
            "DROP SNAPSHOT IF EXISTS nosuch_snap PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.\"nosuch_snap\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.\"nosuch_snap\".",
            "DROP SNAPSHOT \"nosuch_snap\"");
    }

    @Test
    public void theWordsOfAKindAndTheNameAfterThem() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'x'.",
            "DROP DBT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'x'.",
            "DROP GIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "DROP SEMANTIC x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "DROP AUTHENTICATION x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "DROP PRIVACY x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "DROP RESOURCE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "DROP SESSION x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "DROP PACKAGES x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "DROP STORAGE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
            "DROP STORAGE LIFECYCLE x");
        assertAnswer(
            "REFUSED SQL compilation error: | Organization 'NOSUCH_ORG_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ORGANIZATION NOSUCH_ORG_W7.",
            "DROP ORGANIZATION nosuch_org_w7");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_ORG_W7 already dropped).",
            "DROP ORGANIZATION IF EXISTS nosuch_org_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.CASCADE\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on SHARE \"<ACCOUNT>.CASCADE\".",
            "DROP SHARE CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.CASCADE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.CASCADE.",
            "DROP SNAPSHOT CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.NOSUCH_SH_W7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SHARE "
                + "\"<ACCOUNT>.NOSUCH_SH_W7\".",
            "DROP SHARE nosuch_sh_w7 CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SP.",
            "DROP SESSION POLICY nosuch_sp RESTRICT");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_CONN_W7 already dropped).",
            "DROP CONNECTION IF EXISTS nosuch_conn_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP CONNECTION nosuch_s.nosuch_conn_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | DBT PROJECT 'TEST_DB.TEST_SCHEMA.NOSUCH_DBT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on DBT PROJECT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DBT.",
            "DROP DBT PROJECT nosuch_dbt (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Share '\"<ACCOUNT>.NOSUCH_SH_W7\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SHARE "
                + "\"<ACCOUNT>.NOSUCH_SH_W7\".",
            "DROP SHARE nosuch_sh_w7 (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP SEMANTIC VIEW IF EXISTS nosuch_s.nosuch_sv");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP SNAPSHOT IF EXISTS nosuch_db_w7.s.nosuch_snap");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'SEMANTIC_VIEW'.",
            "DROP SEMANTIC VIEW t");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'SEMANTIC_VIEW'.",
            "DROP SEMANTIC VIEW vw");
        assertAnswer(
            "REFUSED SQL compilation error: | Git repository 'TEST_DB.TEST_SCHEMA.T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.T.",
            "DROP GIT REPOSITORY t");
        assertAnswer(
            "REFUSED SQL compilation error: | Snapshot 'TEST_DB.TEST_SCHEMA.T' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on SNAPSHOT "
                + "TEST_DB.TEST_SCHEMA.T.",
            "DROP SNAPSHOT t");
        assertAnswer(
            "REFUSED SQL compilation error: | DBT PROJECT 'TEST_DB.TEST_SCHEMA.NOSUCH_DBT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on DBT PROJECT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DBT.",
            "DROP dbt project nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'nosuch_dbt'.",
            "DROP \"DBT\" PROJECT nosuch_dbt");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'y'.",
            "DROP APPLICATION PACKAGE x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH_IR' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IR.",
            "DROP IMAGE REPOSITORY nosuch_ir");
        assertAnswer(
            "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH_SVC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SVC.",
            "DROP SERVICE nosuch_svc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected 'y'.",
            "DROP SHARE s x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_EXT' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EXT.",
            "DROP EXTERNAL TABLE nosuch_ext");
        assertAnswer(
            "REFUSED SQL compilation error: | Replication group 'NOSUCH_FG' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on REPLICATION GROUP NOSUCH_FG.",
            "DROP FAILOVER GROUP nosuch_fg");
        assertAnswer(
            "REFUSED SQL compilation error: | Compute pool 'NOSUCH_CP_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on COMPUTE POOL NOSUCH_CP_W7.",
            "DROP COMPUTE POOL nosuch_cp_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Notebook 'TEST_DB.TEST_SCHEMA.NOSUCH_NB' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NOTEBOOK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_NB.",
            "DROP NOTEBOOK nosuch_nb");
        assertAnswer(
            "REFUSED SQL compilation error: | Streamlit 'TEST_DB.TEST_SCHEMA.NOSUCH_SL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAMLIT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SL.",
            "DROP STREAMLIT nosuch_sl");
        assertAnswer(
            "REFUSED SQL compilation error: | Connection 'NOSUCH_CONN_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on CONNECTION NOSUCH_CONN_W7.",
            "DROP CONNECTION nosuch_conn_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Integration 'NOSUCH_CI_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on INTEGRATION NOSUCH_CI_W7.",
            "DROP CATALOG INTEGRATION nosuch_ci_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "DROP MATERIALIZED VIEW nosuch_mv");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_SEC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEC.",
            "DROP SECRET nosuch_sec");
        assertAnswer(
            "REFUSED SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH_NR' does not exist or not "
                + "authorized. Your primary role <ROLE> must have MONITOR granted on ACCOUNT <ACCOUNT>.",
            "DROP NETWORK RULE nosuch_nr");
    }
}
