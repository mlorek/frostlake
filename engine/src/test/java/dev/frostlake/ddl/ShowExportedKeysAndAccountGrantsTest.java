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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SHOW EXPORTED KEYS reads the scopes and the one modifier, TERSE, that SHOW IMPORTED KEYS reads, and answers its
 * seventeen columns: no LIKE, STARTS WITH, LIMIT or HISTORY, a table after a bare IN, a schema named in full.
 * SHOW GRANTS ON ACCOUNT lists the privileges granted on the account itself: the word alone is the account and
 * never a table of that name, a name after it is another account's, which does not exist, and a STARTS or a
 * LIMIT written alone there is such a name. Every grant listing reads TERSE and answers the columns it answers
 * without it. Every statement reads only; every answer is live-verified.
 */
public class ShowExportedKeysAndAccountGrantsTest extends MeasuredCellsTestSupport {

    private static final String EXPORTED_KEYS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, pk_database_name:VARCHAR(16777216), pk_schema_name:VARCHAR(16777216), "
            + "pk_table_name:VARCHAR(16777216), pk_column_name:VARCHAR(16777216), fk_database_name:VARCHAR(16777216),"
            + " fk_schema_name:VARCHAR(16777216), fk_table_name:VARCHAR(16777216), fk_column_name:VARCHAR(16777216), "
            + "key_sequence:NUMBER(9,0), update_rule:VARCHAR(16777216), delete_rule:VARCHAR(16777216), "
            + "fk_name:VARCHAR(16777216), pk_name:VARCHAR(16777216), deferrability:VARCHAR(16777216), "
            + "rely:VARCHAR(16777216), comment:VARCHAR(16777216)";

    private static final String GRANTS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
            + "name:VARCHAR(16777216), granted_to:VARCHAR(16777216), grantee_name:VARCHAR(16777216), "
            + "grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216), granted_by_role_type:VARCHAR(16777216)";

    private static final String GRANTS_COLUMNS_2 =
        "created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
            + "name:VARCHAR(16777216), granted_to:VARCHAR(16777216), grantee_name:VARCHAR(16777216), "
            + "grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216)";

    private static final String PRIMARY_KEYS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
            + "table_name:VARCHAR(16777216), column_name:VARCHAR(16777216), key_sequence:NUMBER(9,0), "
            + "constraint_name:VARCHAR(16777216), rely:VARCHAR(16777216), comment:VARCHAR(16777216)";

    @Test
    public void exportedKeys() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW EXPORTED KEYS");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIKE'.",
            "SHOW EXPORTED KEYS LIKE 'zz%'");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW EXPORTED KEYS IN ACCOUNT",
            "SHOW EXPORTED KEYS IN DATABASE",
            "SHOW EXPORTED KEYS IN SCHEMA",
            "SHOW EXPORTED KEYS IN DATABASE test_db",
            "SHOW EXPORTED KEYS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB.",
            "SHOW EXPORTED KEYS IN test_db.test_schema");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW EXPORTED KEYS IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'STARTS'.",
            "SHOW EXPORTED KEYS STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIMIT'.",
            "SHOW EXPORTED KEYS LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIMIT'.",
            "SHOW EXPORTED KEYS LIMIT 1 FROM 'zz'");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW TERSE EXPORTED KEYS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'HISTORY'.",
            "SHOW EXPORTED KEYS HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIKE'.",
            "SHOW EXPORTED KEYS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'nosuchword'.",
            "SHOW EXPORTED KEYS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_SCHEMA_X9",
            "SHOW EXPORTED KEYS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW EXPORTED KEYS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW EXPORTED KEYS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIMIT'.",
            "SHOW EXPORTED KEYS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIKE'.",
            "SHOW EXPORTED KEYS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
            "SHOW EXPORTED KEYS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIKE'.",
            "SHOW EXPORTED KEYS LIKE");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW EXPORTED KEYS IN TABLE ab");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "",
            "SHOW EXPORTED KEYS IN TABLE ab2");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW EXPORTED KEYS IN ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_TABLE_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE_X9.",
            "SHOW EXPORTED KEYS IN TABLE nosuch_table_x9");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIKE'.",
            "SHOW EXPORTED KEYS LIKE 'AB%' IN SCHEMA test_db.test_schema");
        assertShapes(PRIMARY_KEYS_COLUMNS,
            "SHOW PRIMARY KEYS IN TABLE ab",
            "SHOW UNIQUE KEYS IN TABLE ab");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW IMPORTED KEYS IN TABLE ab2",
            "SHOW TERSE EXPORTED KEYS IN TABLE ab");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for ABV",
            "SHOW EXPORTED KEYS IN VIEW abv");
        assertAnswer(
            "",
            "SHOW EXPORTED KEYS IN abv");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA",
            "SHOW EXPORTED KEYS IN SCHEMA test_schema");
        assertShapes(EXPORTED_KEYS_COLUMNS,
            "SHOW EXPORTED KEYS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW EXPORTED KEYS IN ACCOUNT nosuch_x9");
    }

    @Test
    public void grantListings() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(GRANTS_COLUMNS,
            "SHOW GRANTS ON ACCOUNT",
            "SHOW GRANTS ON ACCOUNT LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'LIKE'.",
            "SHOW GRANTS ON ACCOUNT LIKE 'zz%'");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW GRANTS ON ACCOUNT nosuch_x9");
        assertShapes(GRANTS_COLUMNS,
            "SHOW TERSE GRANTS ON ACCOUNT");
        assertShape(
            "created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
                + "name:VARCHAR(16777216), role:VARCHAR(16777216), granted_to:VARCHAR(16777216), "
                + "grantee_name:VARCHAR(16777216), grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216), "
                + "expires_on:TIMESTAMP_LTZ",
            "SHOW TERSE GRANTS");
        assertShapes(GRANTS_COLUMNS,
            "SHOW TERSE GRANTS ON TABLE ab");
        assertShapes(GRANTS_COLUMNS_2,
            "SHOW TERSE GRANTS TO ROLE PUBLIC");
        assertShape(
            "created_on:TIMESTAMP_LTZ, role:VARCHAR(16777216), granted_to:VARCHAR(16777216), "
                + "grantee_name:VARCHAR(16777216), granted_by:VARCHAR(16777216), expires_on:TIMESTAMP_LTZ",
            "SHOW TERSE GRANTS OF ROLE PUBLIC");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW GRANTS ON ACCOUNT nosuch_a.nosuch_b");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW GRANTS ON ACCOUNT LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ''x''.",
            "SHOW GRANTS ON ACCOUNT STARTS WITH 'x'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IN'.",
            "SHOW GRANTS ON ACCOUNT IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'FROM'.",
            "SHOW GRANTS ON ACCOUNT LIMIT 1 FROM 'x'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'PRIVILEGES'.",
            "SHOW GRANTS ON ACCOUNT WITH PRIVILEGES USAGE");
        assertShapes(GRANTS_COLUMNS,
            "SHOW GRANTS ON ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW GRANTS ON ACCOUNT STARTS");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW GRANTS ON ACCOUNT LIMIT");
        assertShapes(GRANTS_COLUMNS,
            "SHOW TERSE GRANTS ON ACCOUNT LIMIT 1");
        assertShapes(GRANTS_COLUMNS_2,
            "SHOW GRANTS TO ROLE SYSADMIN");
    }
}
