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

package dev.frostlake.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The account identity a connection URL names — {@code ?account=}, {@code ?accountName=},
 * {@code ?region=}, {@code ?organization=} — reaching the context functions, on both IN-PROCESS URL
 * forms. Precedence is URL over {@code frostlake.properties} over the built-in default, following the
 * {@code ?venv=} precedent.
 *
 * <p>Deliberately not covered: {@code jdbc:frostlake://host:port/db}. There the engine belongs to a
 * server that is already running and shared by every client, so a client cannot redefine the account
 * it is connecting TO — that server is configured on its own side.
 *
 * <p>Each test uses a fresh engine name or directory, because an engine is shared per {@code direct:}
 * name and per {@code file:} directory: the identity applies on the connection that CREATES it, and a
 * later connection naming a different account joins the existing engine rather than reconfiguring it.
 */
public class IdentityUrlParameterTest {

    private String[] identityOf(final String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT CURRENT_ORGANIZATION_NAME(),"
                 + " CURRENT_ACCOUNT(), CURRENT_ACCOUNT_NAME(), CURRENT_REGION()")) {
            rs.next();
            return new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)};
        }
    }

    @Test
    public void theDirectUrlCarriesTheIdentity() throws Exception {
        final String[] identity = identityOf("jdbc:frostlake:direct:id_direct"
            + "?organization=MYORG&account=LOC001&accountName=ACC001&region=AWS_EU_WEST_1");
        assertEquals("MYORG", identity[0]);
        assertEquals("LOC001", identity[1]);
        assertEquals("ACC001", identity[2]);
        assertEquals("AWS_EU_WEST_1", identity[3]);
    }

    @Test
    public void theFileUrlCarriesTheIdentity(@TempDir final Path dir) throws Exception {
        final String[] identity = identityOf("jdbc:frostlake:file:" + dir.resolve("db")
            + "?organization=FILEORG&account=FILELOC&accountName=FILEACC&region=GCP_US_CENTRAL1");
        assertEquals("FILEORG", identity[0]);
        assertEquals("FILELOC", identity[1]);
        assertEquals("FILEACC", identity[2]);
        assertEquals("GCP_US_CENTRAL1", identity[3]);
    }

    /** Naming none of them leaves every default in place. */
    @Test
    public void aUrlThatNamesNoIdentityKeepsTheDefaults() throws Exception {
        final String[] identity = identityOf("jdbc:frostlake:direct:id_defaults");
        assertEquals("ABCORG", identity[0]);
        assertEquals("ABC12345", identity[1]);
        assertEquals("ABC12345", identity[2]);
        assertEquals("AWS_US_EAST_1", identity[3]);
    }

    /** Naming ONE of them leaves the others at their defaults rather than blanking them. */
    @Test
    public void oneNamedParameterDoesNotDisturbTheRest() throws Exception {
        final String[] identity = identityOf("jdbc:frostlake:direct:id_partial?region=AZURE_WESTEUROPE");
        assertEquals("ABCORG", identity[0]);
        assertEquals("ABC12345", identity[1]);
        assertEquals("AZURE_WESTEUROPE", identity[3]);
    }

    /** SHOW ACCOUNTS answers the URL's identity too, not a hardcoded one. */
    @Test
    public void showAccountsFollowsTheUrl() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                 "jdbc:frostlake:direct:id_show?organization=SHOWORG&accountName=SHOWACC");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW ACCOUNTS")) {
            rs.next();
            assertEquals("SHOWORG", rs.getString("organization_name"));
            assertEquals("SHOWACC", rs.getString("account_name"));
            assertEquals("https://showorg-showacc.snowflakecomputing.com", rs.getString("account_url"));
        }
    }
}
