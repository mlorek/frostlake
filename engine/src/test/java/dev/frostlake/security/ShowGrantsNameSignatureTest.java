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
package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SHOW GRANTS reads the names it lists as DROP reads an object's (live-verified): a role or a user after TO and OF, and
 * an object after ON, may be followed by a signature, which the account reads and ignores, so PUBLIC(x) lists PUBLIC's
 * grants; the word IDENTIFIER with no whole reference after it is the name itself, its parentheses the signature,
 * refused inside them where they are not one; and an IDENTIFIER() whose content opens as a whole reference's does is
 * refused where it stops being one. After a routine's kind the parentheses list the types the routine is found by.
 */
public class ShowGrantsNameSignatureTest extends MeasuredCellsTestSupport {

    /**
     * IDENTIFIER() over calls, words, operations and literals after TO, OF and ON, and beside them in a FROM and a
     * DESCRIBE.
     */
    @Test
    public void aPrincipalOrAnObjectReadsAsADropReadsAName() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SHOW GRANTS TO USER IDENTIFIER(CURRENT_USER())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ')'."},
            {"SHOW GRANTS TO ROLE IDENTIFIER(CURRENT_ROLE())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ')'."},
            {"SHOW GRANTS TO ROLE IDENTIFIER(UPPER('public'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''public''."},
            {"SHOW GRANTS ON DATABASE IDENTIFIER(CURRENT_DATABASE())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ')'."},
            {"SELECT 1 FROM IDENTIFIER(CURRENT_USER())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'CURRENT_USER'."},
            {"DESCRIBE TABLE IDENTIFIER(UPPER('t'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''t''."},
            {"SHOW GRANTS TO USER IDENTIFIER(1 + 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '+'."},
            {"SHOW GRANTS TO USER IDENTIFIER(CONCAT('a', 'b'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''a''. | syntax error "
                + "line 1 at position 43 unexpected ''b''."},
        });
    }

    /** Roles named with signatures, and IDENTIFIER() over references, lists and calls after TO, OF and ON. */
    @Test
    public void theParenthesesAfterTheWordIdentifierAreASignature() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SHOW GRANTS TO ROLE r(x)",
                "REFUSED SQL compilation error: | Role 'R' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on ROLE R."},
            {"SHOW GRANTS OF ROLE IDENTIFIER(CURRENT_ROLE())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ')'."},
            {"SHOW GRANTS OF ROLE IDENTIFIER(PUBLIC)",
                "REFUSED SQL compilation error: | Role 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE IDENTIFIER."},
            {"SHOW GRANTS TO USER IDENTIFIER('a' || 'b')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '||'."},
            {"SHOW GRANTS TO ROLE IDENTIFIER('PUB' || 'LIC')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '||'."},
            {"SHOW GRANTS TO USER IDENTIFIER(NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'NULL'."},
            {"SHOW GRANTS TO USER IDENTIFIER(TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'TRUE'."},
            {"SHOW GRANTS TO ROLE IDENTIFIER(PUBLIC) LIMIT 1",
                "REFUSED SQL compilation error: | Role 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE IDENTIFIER."},
            {"SHOW GRANTS TO ROLE IDENTIFIER(1)",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier '1'"},
            {"SHOW GRANTS TO ROLE IDENTIFIER(r(1))",
                "REFUSED SQL compilation error: | Role 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE IDENTIFIER."},
            {"SHOW GRANTS TO ROLE IDENTIFIER(VARCHAR(10))",
                "REFUSED SQL compilation error: | Role 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE IDENTIFIER."},
            {"SHOW GRANTS TO ROLE IDENTIFIER(a, b)",
                "REFUSED SQL compilation error: | Role 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE IDENTIFIER."},
            {"SHOW GRANTS ON TABLE IDENTIFIER(UPPER('t'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''t''."},
            {"SHOW GRANTS ON SCHEMA IDENTIFIER(CURRENT_SCHEMA())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ')'."},
            {"SHOW GRANTS ON DATABASE IDENTIFIER(NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'NULL'."},
            {"SHOW GRANTS ON TABLE IDENTIFIER('t' || '1')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '||'."},
            {"SHOW GRANTS ON WAREHOUSE IDENTIFIER(CURRENT_WAREHOUSE())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ')'."},
            {"SHOW GRANTS ON DATABASE IDENTIFIER(x)",
                "REFUSED SQL compilation error: | Database 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on DATABASE IDENTIFIER."},
        });
    }

    /**
     * Roles, databases, tables and schemas named with signatures, PUBLIC's grants listed through one, and the word
     * IDENTIFIER after a routine's kind.
     */
    @Test
    public void aSignatureAfterAnyNameIsIgnored() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SHOW GRANTS TO ROLE nosuch_role_w7(x)",
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7."},
            {"SHOW GRANTS OF ROLE nosuch_role_w7(x)",
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7."},
            {"SHOW GRANTS ON DATABASE nosuch_db_w7(x)",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7."},
            {"SHOW GRANTS ON DATABASE nosuch_db_w7",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7."},
            {"SHOW GRANTS ON TABLE nosuch_t_w7(x)",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_W7."},
            {"SHOW GRANTS ON SCHEMA nosuch_s_w7(INT)",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S_W7."},
            {"SHOW GRANTS TO ROLE PUBLIC(x) ->> SELECT DISTINCT \"grantee_name\" FROM $1",
                "PUBLIC"},
            {"SHOW GRANTS TO ROLE PUBLIC(VARCHAR(10), INT) ->> SELECT DISTINCT \"grantee_name\" FROM $1",
                "PUBLIC"},
            {"SHOW GRANTS ON FUNCTION IDENTIFIER(INT)",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.IDENTIFIER' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.IDENTIFIER."},
            {"SHOW GRANTS ON FUNCTION IDENTIFIER()",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.IDENTIFIER' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.IDENTIFIER."},
            {"SHOW GRANTS ON PROCEDURE IDENTIFIER(VARCHAR)",
                "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.IDENTIFIER' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.IDENTIFIER."},
            {"SHOW GRANTS OF ROLE IDENTIFIER(x)",
                "REFUSED SQL compilation error: | Role 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE IDENTIFIER."},
        });
    }

    /**
     * The lists after each kind's name: a routine's types, any other kind's signature, read whole or refused where it
     * stops being one.
     */
    @Test
    public void aRoutinesParenthesesListItsTypes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SHOW GRANTS TO ROLE nosuch_role_w7",
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7."},
            {"DESCRIBE DATABASE nosuch_db_w7",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7."},
            {"SHOW GRANTS ON FUNCTION nosuch_f_w7(INT)",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH_F_W7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_F_W7."},
            {"SHOW GRANTS ON FUNCTION nosuch_f_w7",
                "REFUSED Argument types of function 'NOSUCH_F_W7' must be specified."},
            {"SHOW GRANTS ON FUNCTION IDENTIFIER",
                "REFUSED Argument types of function 'IDENTIFIER' must be specified."},
            {"SHOW GRANTS ON FUNCTION nosuch_f_w7(x)",
                "REFUSED SQL compilation error: | Unsupported data type 'X'."},
            {"SHOW GRANTS ON DATABASE nosuch_db_w7(a b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'b'."},
            {"SHOW GRANTS ON TABLE nosuch_t_w7(a(b))",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_W7."},
            {"SHOW GRANTS ON TABLE nosuch_t_w7()",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_W7."},
            {"SHOW GRANTS ON TABLE nosuch_t_w7",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_W7."},
            {"SHOW GRANTS ON VIEW nosuch_t_w7(x)",
                "REFUSED SQL compilation error: | View 'NOSUCH_T_W7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_W7."},
            {"SHOW GRANTS ON SCHEMA nosuch_s_w7(VARCHAR(10), x)",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S_W7."},
            {"SHOW GRANTS ON WAREHOUSE nosuch_wh_w7(x)",
                "REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH_W7."},
            {"SHOW GRANTS ON PROCEDURE nosuch_p_w7(x)",
                "REFUSED SQL compilation error: | Unsupported data type 'X'."},
            {"SHOW GRANTS ON DATABASE IDENTIFIER",
                "REFUSED SQL compilation error: | Database 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on DATABASE IDENTIFIER."},
            {"SHOW GRANTS ON TABLE IDENTIFIER()",
                "REFUSED SQL compilation error: | Table 'IDENTIFIER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE IDENTIFIER."},
        });
    }
}
