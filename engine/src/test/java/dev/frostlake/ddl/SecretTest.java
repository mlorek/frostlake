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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE SECRET. A secret's credential is written and never shown back.
 */
public class SecretTest extends BaseDatabaseTest {

    /** A secret of these types names an INTEGRATION, which this account does not carry. */
    private static final String NO_INTEGRATION = "a secret of this type names an API or notification"
        + " integration, and the account has none to name";

    /** Runs a statement that must be refused and answers the refusal's message. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    /** Every cell of a listing, as text. */
    private static String allCells(final ResultSet listing) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : listing.getRows()) {
            for (int i = 0; i < listing.getColumns().size(); i++) {
                out.append(row.getValue(i)).append('|');
            }
        }
        return out.toString();
    }

    @Test
    public void everyTypeIsCreatedAndListed() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_INTEGRATION);
        engine.execute("CREATE SECRET s_pw TYPE = PASSWORD USERNAME = 'jsmith' PASSWORD = 'hunter2' COMMENT = 'basic'");
        engine.execute("CREATE SECRET s_gs TYPE = GENERIC_STRING SECRET_STRING = 'tok-123'");
        engine.execute("CREATE SECRET s_oa TYPE = OAUTH2 API_AUTHENTICATION = my_int OAUTH_SCOPES = ('useraccount')");
        engine.execute("CREATE SECRET s_oc TYPE = OAUTH2 OAUTH_REFRESH_TOKEN = 'refresh-xyz' "
            + "OAUTH_REFRESH_TOKEN_EXPIRY_TIME = '2030-01-01 00:00:00' API_AUTHENTICATION = my_int");
        engine.execute("CREATE SECRET s_cp TYPE = CLOUD_PROVIDER_TOKEN API_AUTHENTICATION = aws_int ENABLED = TRUE");
        engine.execute("CREATE SECRET s_sk TYPE = SYMMETRIC_KEY ALGORITHM = GENERIC");
        engine.execute("CREATE SECRET s_wi TYPE = WORKLOAD_IDENTITY_FEDERATION");
        final ResultSet listed = engine.executeQuery("SHOW SECRETS LIKE 'S%'");
        assertEquals(7, listed.getRows().size());
        assertEquals("PASSWORD", cell(listed, soleRowWhere(listed, "name", "S_PW"), "secret_type"));
        assertEquals("basic", cell(listed, soleRowWhere(listed, "name", "S_PW"), "comment"));
        assertEquals("[useraccount]", cell(listed, soleRowWhere(listed, "name", "S_OA"), "oauth_scopes"));
        assertEquals("TEST_DB", cell(listed, soleRowWhere(listed, "name", "S_GS"), "database_name"));

        final ResultSet described = engine.executeQuery("DESCRIBE SECRET s_pw");
        assertEquals("jsmith", cell(described, described.getRows().get(0), "username"));
        final ResultSet oauth = engine.executeQuery("DESC SECRET s_oc");
        assertEquals("MY_INT", cell(oauth, oauth.getRows().get(0), "integration_name"));
        assertTrue(cell(oauth, oauth.getRows().get(0), "oauth_refresh_token_expiry_time").startsWith("2030-01-01"));
        final ResultSet key = engine.executeQuery("DESC SECRET s_sk");
        assertEquals("256", cell(key, key.getRows().get(0), "key_length"));
    }

    @Test
    public void aCredentialIsNeverShown() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_INTEGRATION);
        engine.execute("CREATE SECRET s_hidden TYPE = PASSWORD USERNAME = 'u' PASSWORD = 'hunter2-secret'");
        engine.execute("CREATE SECRET s_hidden2 TYPE = GENERIC_STRING SECRET_STRING = 'generic-secret'");
        engine.execute("CREATE SECRET s_hidden3 TYPE = OAUTH2 OAUTH_REFRESH_TOKEN = 'refresh-secret' "
            + "API_AUTHENTICATION = i");
        final String shown = allCells(engine.executeQuery("SHOW SECRETS"))
            + allCells(engine.executeQuery("DESC SECRET s_hidden"))
            + allCells(engine.executeQuery("DESC SECRET s_hidden2"))
            + allCells(engine.executeQuery("DESC SECRET s_hidden3"));
        assertFalse(shown.contains("hunter2-secret"), shown);
        assertFalse(shown.contains("generic-secret"), shown);
        assertFalse(shown.contains("refresh-secret"), shown);
    }

    @Test
    public void aTypeTakesOnlyItsOwnProperties() {
        assertTrue(refusal("CREATE SECRET s_bad USERNAME = 'u'").contains("Missing option(s): [TYPE]"));
        assertTrue(refusal("CREATE SECRET s_bad TYPE = PASSWORD USERNAME = 'u'").contains(
            "Missing option(s): [PASSWORD]"));
        assertTrue(refusal("CREATE SECRET s_bad TYPE = PASSWORD USERNAME = 'u' PASSWORD = 'p' SECRET_STRING = 'x'")
            .contains("invalid type of property 'SECRET_STRING' for 'PASSWORD'"));
        assertTrue(refusal("CREATE SECRET s_bad TYPE = JWT").contains("invalid value 'JWT' for property 'TYPE'"));
        assertTrue(refusal("CREATE SECRET s_bad TYPE = SYMMETRIC_KEY ALGORITHM = AES").contains(
            "Invalid algorithm name AES for SYMMETRIC_KEY Secret."));
        assertTrue(refusal("CREATE SECRET s_bad TYPE = GENERIC_STRING NAME = 'x'").contains(
            "invalid property 'NAME' for 'SECRET'"));
    }

    @Test
    public void alterChangesWhatTheTypeAllows() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_INTEGRATION);
        engine.execute("CREATE SECRET s_alt TYPE = PASSWORD USERNAME = 'u1' PASSWORD = 'p1' COMMENT = 'c'");
        engine.execute("ALTER SECRET s_alt SET USERNAME = 'u2' PASSWORD = 'p2'");
        ResultSet described = engine.executeQuery("DESC SECRET s_alt");
        assertEquals("u2", cell(described, described.getRows().get(0), "username"));
        assertEquals("Unsupported feature 'SECRET'.", refusal("ALTER SECRET s_alt UNSET COMMENT"));
        assertTrue(refusal("ALTER SECRET s_alt SET OAUTH_SCOPES = ('x')").contains(
            "invalid type of property 'OAUTH_SCOPES' for 'PASSWORD'"));
        assertEquals("Unsupported feature 'SECRET'.", refusal("ALTER SECRET s_alt UNSET USERNAME"));
        engine.execute("CREATE SECRET s_alt2 TYPE = OAUTH2 API_AUTHENTICATION = i OAUTH_SCOPES = ('a')");
        engine.execute("ALTER SECRET s_alt2 SET OAUTH_SCOPES = ('a', 'b')");
        described = engine.executeQuery("DESC SECRET s_alt2");
        assertEquals("[a, b]", cell(described, described.getRows().get(0), "oauth_scopes"));
        engine.execute("ALTER SECRET IF EXISTS s_nope SET COMMENT = 'x'");
    }

    @Test
    public void createModesDropAndScope() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_INTEGRATION);
        engine.execute("CREATE SECRET s_m TYPE = GENERIC_STRING SECRET_STRING = 'a'");
        assertTrue(refusal("CREATE SECRET s_m TYPE = GENERIC_STRING SECRET_STRING = 'b'").contains(
            "Object 'S_M' already exists."));
        engine.execute("CREATE SECRET IF NOT EXISTS s_m TYPE = GENERIC_STRING SECRET_STRING = 'b'");
        assertTrue(refusal("CREATE OR REPLACE SECRET IF NOT EXISTS s_m TYPE = GENERIC_STRING SECRET_STRING = 'b'")
            .contains("options IF NOT EXISTS and OR REPLACE are incompatible."));
        engine.execute("CREATE OR REPLACE SECRET s_m TYPE = PASSWORD USERNAME = 'u' PASSWORD = 'p'");
        ResultSet listed = engine.executeQuery("SHOW SECRETS LIKE 'S_M' IN SCHEMA test_db.test_schema");
        assertEquals("PASSWORD", cell(listed, soleRowWhere(listed, "name", "S_M"), "secret_type"));
        engine.execute("CREATE SCHEMA s_other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SECRET s_other.s_m TYPE = GENERIC_STRING SECRET_STRING = 'c'");
        assertEquals(2, engine.executeQuery("SHOW SECRETS LIKE 'S_M' IN DATABASE test_db").getRows().size());
        assertEquals(1, engine.executeQuery("SHOW SECRETS LIKE 'S_M' IN s_other").getRows().size());
        engine.execute("DROP SECRET s_m");
        engine.execute("DROP SECRET IF EXISTS s_m");
        assertTrue(refusal("DROP SECRET s_m").contains(
            "Secret 'TEST_DB.TEST_SCHEMA.S_M' does not exist or not authorized."));
        listed = engine.executeQuery("SHOW SECRETS LIKE 'S_M'");
        assertEquals("S_OTHER", cell(listed, soleRowWhere(listed, "name", "S_M"), "schema_name"));
    }
}
