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
 * The sentences a database role's statements answer: created as a role named with its database, already
 * there and dropped by that qualified name, and already dropped by the bare one. A user that is not there is
 * refused before the network policy an ALTER USER names, and a number is no policy name. Every answer is
 * live-verified.
 */
public class DatabaseRoleStatusSentenceTest extends MeasuredCellsTestSupport {

    @Test
    public void aDatabaseRoleIsNamedWithItsDatabase() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "Role TEST_DB.DR_A successfully created.",
            "CREATE DATABASE ROLE dr_a");
        assertAnswer(
            "TEST_DB.DR_A already exists, statement succeeded.",
            "CREATE DATABASE ROLE IF NOT EXISTS dr_a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.DR_A' already exists.",
            "CREATE DATABASE ROLE dr_a");
        assertAnswer(
            "Role TEST_DB.DR_A successfully created.",
            "CREATE OR REPLACE DATABASE ROLE dr_a");
        assertAnswer(
            "Role TEST_DB.DR_B successfully created.",
            "CREATE DATABASE ROLE IF NOT EXISTS dr_b");
        assertAnswer(
            "Role TEST_DB.DR_C successfully created.",
            "CREATE DATABASE ROLE test_db.dr_c");
        assertAnswer(
            "Role TEST_DB.\"dr d\" successfully created.",
            "CREATE DATABASE ROLE \"dr d\"");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER DATABASE ROLE dr_a SET COMMENT = 'x'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER DATABASE ROLE dr_a RENAME TO dr_e");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "ALTER DATABASE ROLE nosuch_dr RENAME TO dr_f");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER DATABASE ROLE IF EXISTS nosuch_dr RENAME TO dr_f");
        assertAnswer(
            "TEST_DB.DR_B successfully dropped.",
            "DROP DATABASE ROLE dr_b");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.DR_B' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.DR_B.",
            "DROP DATABASE ROLE dr_b");
        assertAnswer(
            "Drop statement executed successfully (DR_B already dropped).",
            "DROP DATABASE ROLE IF EXISTS dr_b");
        assertAnswer(
            "TEST_DB.DR_C successfully dropped.",
            "DROP DATABASE ROLE test_db.dr_c");
        assertAnswer(
            "TEST_DB.\"dr d\" successfully dropped.",
            "DROP DATABASE ROLE \"dr d\"");
        assertAnswer(
            "TEST_DB.DR_E successfully dropped.",
            "DROP DATABASE ROLE IF EXISTS dr_e");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "DROP DATABASE ROLE nosuch_db_w7.dr_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "CREATE DATABASE ROLE nosuch_db_w7.dr_x");
        assertAnswer(
            "",
            "SHOW DATABASE ROLES IN DATABASE test_db");
    }

    @Test
    public void aMissingUserIsRefusedBeforeItsNetworkPolicy() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "ALTER USER nosuch_user_w7 SET NETWORK_POLICY = 'nosuch_np_g7'");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "ALTER USER nosuch_user_w7 SET NETWORK_POLICY = nosuch_np_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "ALTER USER nosuch_user_w7 SET NETWORK_POLICY = 'NOSUCH_NP_G7'");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "ALTER USER nosuch_user_w7 SET NETWORK_POLICY = \"nosuch_np_g7\"");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER USER IF EXISTS nosuch_user_w7 SET NETWORK_POLICY = 'nosuch_np_g7'");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "ALTER USER nosuch_user_w7 UNSET NETWORK_POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "ALTER USER nosuch_user_w7 SET NETWORK_POLICY = 'a b'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [1] for parameter 'NETWORK_POLICY'",
            "ALTER USER nosuch_user_w7 SET NETWORK_POLICY = 1");
    }
}
