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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the listings of this batch answer from the engine's own state, where an account answers from its own: the
 * privileges granted on the account, the regions, a key's columns in the order the engine keeps them, and the
 * delegated authorizations of a user and of an integration that exist.
 *
 * <p>SHOW GRANTS ON ACCOUNT needs a grant on the account to list, and SHOW REGIONS answers a list that grows on an
 * account, so the class runs on an engine of its own and is never sent to an account: the shapes and the refusals
 * of these listings are measured in their two-sided tests.
 */
public class ShowAccountGrantsAndRegionsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void startEngine() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE own_db");
        engine.execute("CREATE SCHEMA own_db.own_schema");
        engine.execute("USE SCHEMA own_db.own_schema");
        engine.execute("USE ROLE ACCOUNTADMIN");
    }

    @AfterEach
    public void stopEngine() {
        engine.shutdown();
    }

    private List<String> column(final ResultSet rs, final String name) {
        final List<String> values = new ArrayList<>();
        final int index = rs.getColumnIndex(name);
        for (final Row row : rs.getRows()) {
            values.add(String.valueOf(row.getValue(index)));
        }
        return values;
    }

    private String account() {
        return String.valueOf(engine.executeQuery("SELECT CURRENT_ACCOUNT()").getRows().get(0).getValue(0));
    }

    @Test
    public void theAccountsGrantsNameTheAccountByItsLocator() {
        engine.execute("CREATE ROLE acct_reader");
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE acct_reader");
        engine.execute("GRANT MONITOR USAGE ON ACCOUNT TO ROLE acct_reader WITH GRANT OPTION");
        final ResultSet grants = engine.executeQuery("SHOW GRANTS ON ACCOUNT");
        assertEquals(9, grants.getColumns().size());
        assertEquals(List.of("CREATE DATABASE", "MONITOR USAGE"), column(grants, "privilege"));
        assertEquals(List.of("ACCOUNT", "ACCOUNT"), column(grants, "granted_on"));
        assertEquals(List.of(account(), account()), column(grants, "name"));
        assertEquals(List.of("ROLE", "ROLE"), column(grants, "granted_to"));
        assertEquals(List.of("ACCT_READER", "ACCT_READER"), column(grants, "grantee_name"));
        assertEquals(List.of("false", "true"), column(grants, "grant_option"));
    }

    @Test
    public void theAccountsGrantsReadTerseAndALimit() {
        engine.execute("CREATE ROLE acct_reader");
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE acct_reader");
        engine.execute("GRANT MONITOR USAGE ON ACCOUNT TO ROLE acct_reader");
        assertEquals(1, engine.executeQuery("SHOW GRANTS ON ACCOUNT LIMIT 1").getRows().size());
        final ResultSet terse = engine.executeQuery("SHOW TERSE GRANTS ON ACCOUNT");
        assertEquals(2, terse.getRows().size());
        assertEquals(9, terse.getColumns().size());
        engine.execute("REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE acct_reader");
        assertEquals(List.of("MONITOR USAGE"), column(engine.executeQuery("SHOW GRANTS ON ACCOUNT"), "privilege"));
    }

    @Test
    public void theWordAccountIsTheAccountAndNeverATableOfThatName() {
        engine.execute("CREATE ROLE acct_reader");
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE acct_reader");
        engine.execute("CREATE TABLE account (a INT)");
        assertEquals(List.of("ACCOUNT"), column(engine.executeQuery("SHOW GRANTS ON account"), "granted_on"));
        assertEquals(List.of("TABLE"), column(engine.executeQuery("SHOW GRANTS ON TABLE account"), "granted_on"));
    }

    @Test
    public void theRegionsAreThePublicList() {
        final ResultSet regions = engine.executeQuery("SHOW REGIONS");
        assertEquals(List.of("snowflake_region", "cloud", "region", "display_name"),
            List.of(regions.getColumns().get(0).getName(), regions.getColumns().get(1).getName(),
                regions.getColumns().get(2).getName(), regions.getColumns().get(3).getName()));
        assertEquals(48, regions.getRows().size());
        for (final String cloud : column(regions, "cloud")) {
            assertTrue(List.of("aws", "azure", "gcp").contains(cloud), cloud);
        }
        final Row oregon = engine.executeQuery("SHOW REGIONS LIKE 'aws_us_west_2'").getRows().get(0);
        assertEquals(List.of("AWS_US_WEST_2", "aws", "us-west-2", "US West (Oregon)"), oregon.getValues());
        assertEquals(List.of("AZURE_WESTEUROPE", "AZURE_WESTUS2"),
            column(engine.executeQuery("SHOW REGIONS LIKE 'AZURE_WEST%'"), "snowflake_region"));
    }

    @Test
    public void theRegionsReadTheModifiersAndFollowNoneButLike() {
        assertEquals(48, engine.executeQuery("SHOW REGIONS STARTS WITH 'AWS_US'").getRows().size());
        assertEquals(48, engine.executeQuery("SHOW REGIONS LIMIT 2").getRows().size());
        assertEquals(48, engine.executeQuery("SHOW REGIONS LIMIT 2 FROM 'AWS_US_EAST_1'").getRows().size());
        assertEquals(48, engine.executeQuery("SHOW TERSE REGIONS").getRows().size());
        assertEquals(48, engine.executeQuery("SHOW REGIONS HISTORY").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW REGIONS LIKE '%oregon%'").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW REGIONS LIKE 'us-west-2'").getRows().size());
    }

    @Test
    public void anExportedKeyOfSeveralColumnsListsInTheReferencingTablesColumnOrder() {
        engine.execute("CREATE TABLE pair (p INT, q INT, PRIMARY KEY (p, q))");
        engine.execute("""
            CREATE TABLE pair_child (x INT, y INT, CONSTRAINT fk_pair FOREIGN KEY (y, x) REFERENCES pair (p, q))
            """);
        final ResultSet exported = engine.executeQuery("SHOW EXPORTED KEYS IN TABLE pair");
        assertEquals(List.of("X", "Y"), column(exported, "fk_column_name"));
        assertEquals(List.of("Q", "P"), column(exported, "pk_column_name"));
        assertEquals(List.of("2", "1"), column(exported, "key_sequence"));
        final ResultSet imported = engine.executeQuery("SHOW IMPORTED KEYS IN TABLE pair_child");
        assertEquals(List.of("Y", "X"), column(imported, "fk_column_name"));
        assertEquals(List.of("1", "2"), column(imported, "key_sequence"));
    }

    @Test
    public void aUserAndAnIntegrationThatExistHoldNoDelegatedAuthorizations() {
        engine.execute("CREATE USER delegating_user");
        engine.execute("""
            CREATE SECURITY INTEGRATION delegated_to TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2
                OAUTH_CLIENT_ID = 'a' OAUTH_CLIENT_SECRET = 'b' OAUTH_TOKEN_ENDPOINT = 'https://example.com/token'
                ENABLED = TRUE
            """);
        final ResultSet byUser = engine.executeQuery("SHOW DELEGATED AUTHORIZATIONS BY USER delegating_user");
        assertEquals(7, byUser.getColumns().size());
        assertEquals(0, byUser.getRows().size());
        assertEquals(0, engine.executeQuery(
            "SHOW DELEGATED AUTHORIZATIONS TO SECURITY INTEGRATION delegated_to").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW TERSE DELEGATED AUTHORIZATIONS").getRows().size());
    }
}
