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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The security integration types beside API_AUTHENTICATION: Snowflake OAuth, External OAuth, SAML2 and SCIM, each
 * created with the properties its page documents, listed with its type — a custom OAuth client's as
 * {@code OAUTH - CUSTOM} (live-verified) — and described by what it was written with; and an OAuth secret's
 * scopes, which an ALTER may set only to scopes its integration allows, as a CREATE may. Engine only: integrations
 * are not created on the live account.
 */
public class SecurityIntegrationTypesTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "integrations are not created on the live account");
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** SHOW INTEGRATIONS' row for one integration: name, type, category, enabled and comment. */
    private String listed(final String like) {
        final Row row = engine.executeQuery("SHOW INTEGRATIONS LIKE '" + like + "'").getRows().get(0);
        return row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|"
            + row.getValue(4);
    }

    /** DESCRIBE's rows, each as {@code property=value}. */
    private List<String> described(final String name) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("DESCRIBE INTEGRATION " + name).getRows()) {
            out.add(row.getValue(0) + "=" + row.getValue(2));
        }
        return out;
    }

    @Test
    public void aSnowflakeOauthIntegrationIsListedWithItsClient() {
        assertEquals("Integration SO_CUSTOM successfully created.", status("CREATE SECURITY INTEGRATION so_custom"
            + " TYPE = OAUTH OAUTH_CLIENT = CUSTOM OAUTH_CLIENT_TYPE = 'CONFIDENTIAL'"
            + " OAUTH_REDIRECT_URI = 'https://example.com/cb' ENABLED = TRUE"));
        assertEquals("SO_CUSTOM|OAUTH - CUSTOM|SECURITY|true|null", listed("SO_CUSTOM"));
        // ENABLED may be left out, and is then TRUE.
        engine.execute("CREATE SECURITY INTEGRATION so_tableau TYPE = OAUTH OAUTH_CLIENT = tableau_desktop"
            + " OAUTH_REFRESH_TOKEN_VALIDITY = 36000 COMMENT = 'bi'");
        assertEquals("SO_TABLEAU|OAUTH - TABLEAU_DESKTOP|SECURITY|true|bi", listed("SO_TABLEAU"));
        assertEquals("[ENABLED=true, OAUTH_CLIENT=TABLEAU_DESKTOP, OAUTH_REFRESH_TOKEN_VALIDITY=36000, COMMENT=bi]",
            described("so_tableau").toString());
        assertEquals(PREFIX + "Missing option(s): [OAUTH_CLIENT_TYPE, OAUTH_REDIRECT_URI]",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = OAUTH OAUTH_CLIENT = CUSTOM"));
        assertEquals(PREFIX + "Missing option(s): [OAUTH_REDIRECT_URI]",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = OAUTH OAUTH_CLIENT = LOOKER"));
        assertEquals(PREFIX + "Missing option(s): [OAUTH_CLIENT]",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = OAUTH ENABLED = TRUE"));
        assertEquals(PREFIX + "invalid value [NOPE] for parameter 'OAUTH_CLIENT'",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = OAUTH OAUTH_CLIENT = NOPE"));
        assertEquals(PREFIX + "invalid property 'AUTH_TYPE' for 'INTEGRATION'",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = OAUTH OAUTH_CLIENT = LOOKER AUTH_TYPE = OAUTH2"));
    }

    @Test
    public void anExternalOauthASaml2AndAScimIntegrationAreCreated() {
        engine.execute("CREATE SECURITY INTEGRATION so_ext TYPE = EXTERNAL_OAUTH ENABLED = TRUE"
            + " EXTERNAL_OAUTH_TYPE = OKTA EXTERNAL_OAUTH_ISSUER = 'https://okta.example.com'"
            + " EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM = 'sub'"
            + " EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE = 'LOGIN_NAME'"
            + " EXTERNAL_OAUTH_JWS_KEYS_URL = 'https://okta.example.com/keys'");
        assertEquals("SO_EXT|EXTERNAL_OAUTH - OKTA|SECURITY|true|null", listed("SO_EXT"));
        assertEquals(PREFIX + "Missing option(s): ENABLED", refusal("CREATE SECURITY INTEGRATION so_bad"
            + " TYPE = EXTERNAL_OAUTH EXTERNAL_OAUTH_TYPE = OKTA"));
        assertEquals(PREFIX + "Missing option(s): [EXTERNAL_OAUTH_ISSUER, EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM,"
            + " EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE]", refusal("CREATE SECURITY INTEGRATION so_bad"
            + " TYPE = EXTERNAL_OAUTH ENABLED = TRUE EXTERNAL_OAUTH_TYPE = AZURE"));
        engine.execute("CREATE SECURITY INTEGRATION so_saml TYPE = SAML2 ENABLED = TRUE"
            + " METADATA_URL = 'https://idp.example.com/metadata'");
        assertEquals("SO_SAML|SAML2|SECURITY|true|null", listed("SO_SAML"));
        assertEquals(PREFIX + "Missing option(s): [SAML2_ISSUER, SAML2_SSO_URL, SAML2_PROVIDER, SAML2_X509_CERT]",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = SAML2 ENABLED = TRUE"));
        engine.execute("CREATE SECURITY INTEGRATION so_saml2 TYPE = SAML2 ENABLED = FALSE"
            + " SAML2_ISSUER = 'https://idp' SAML2_SSO_URL = 'https://idp/sso' SAML2_PROVIDER = 'OKTA'"
            + " SAML2_X509_CERT = 'MIIC'");
        assertEquals("SO_SAML2|SAML2|SECURITY|false|null", listed("SO_SAML2"));
        engine.execute("CREATE SECURITY INTEGRATION so_scim TYPE = SCIM ENABLED = TRUE SCIM_CLIENT = 'okta'"
            + " RUN_AS_ROLE = 'OKTA_PROVISIONER'");
        assertEquals("SO_SCIM|SCIM - OKTA|SECURITY|true|null", listed("SO_SCIM"));
        assertEquals(PREFIX + "Missing option(s): [RUN_AS_ROLE]",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = SCIM ENABLED = TRUE SCIM_CLIENT = 'GENERIC'"));
        assertEquals(PREFIX + "invalid property 'OAUTH_CLIENT' for 'INTEGRATION'",
            refusal("CREATE SECURITY INTEGRATION so_bad TYPE = SCIM ENABLED = TRUE OAUTH_CLIENT = CUSTOM"));
    }

    @Test
    public void alterSetsWhatTheTypeTakes() {
        engine.execute("CREATE SECURITY INTEGRATION so_alter TYPE = SCIM ENABLED = TRUE SCIM_CLIENT = 'GENERIC'"
            + " RUN_AS_ROLE = 'GENERIC_SCIM_PROVISIONER'");
        assertEquals("Statement executed successfully.",
            status("ALTER SECURITY INTEGRATION so_alter SET SYNC_PASSWORD = TRUE COMMENT = 'c'"));
        assertEquals("[ENABLED=true, SCIM_CLIENT=GENERIC, RUN_AS_ROLE=GENERIC_SCIM_PROVISIONER, SYNC_PASSWORD=true,"
            + " COMMENT=c]", described("so_alter").toString());
        assertEquals(PREFIX + "invalid property 'OAUTH_CLIENT_ID' for 'INTEGRATION'",
            refusal("ALTER SECURITY INTEGRATION so_alter SET OAUTH_CLIENT_ID = 'x'"));
        assertEquals(PREFIX + "invalid property 'TYPE' for 'INTEGRATION - SCIM - GENERIC'",
            refusal("ALTER SECURITY INTEGRATION so_alter SET TYPE = SAML2"));
    }

    @Test
    public void anAlterOfASecretsScopesIsJudgedByItsIntegration() {
        engine.execute("CREATE SECURITY INTEGRATION so_api TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs' OAUTH_ALLOWED_SCOPES = ('a', 'b') ENABLED = TRUE");
        engine.execute("CREATE SECRET so_secret TYPE = OAUTH2 API_AUTHENTICATION = so_api OAUTH_SCOPES = ('a')");
        assertEquals(PREFIX + "invalid value '[c]' for property 'OAUTH_SCOPES'",
            refusal("ALTER SECRET so_secret SET OAUTH_SCOPES = ('c')"));
        assertEquals(PREFIX + "invalid value '[a, c]' for property 'OAUTH_SCOPES'",
            refusal("ALTER SECRET so_secret SET OAUTH_SCOPES = ('a', 'c')"));
        assertEquals("Statement executed successfully.", status("ALTER SECRET so_secret SET OAUTH_SCOPES = ('b')"));
    }
}
