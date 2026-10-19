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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE of a security integration that authenticates an external API — an OAuth
 * client or an AWS role — and of an API integration of the package index's provider.
 */
public class SecurityIntegrationTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    private static final String OAUTH = "CREATE OR REPLACE SECURITY INTEGRATION si_sec TYPE = API_AUTHENTICATION"
        + " AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'csecret'";

    /** The integrations are the account's, not the test database's: none is left behind. */
    @AfterEach
    public void dropIntegrations() {
        for (final String name : new String[] {"si_sec", "si_sec2", "si_iam", "si_pypi"}) {
            engine.execute("DROP INTEGRATION IF EXISTS " + name);
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** DESCRIBE's rows, each as {@code property|type|value|default}. */
    private List<String> described(final String name) {
        final ResultSet rs = engine.executeQuery("DESCRIBE INTEGRATION " + name);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3));
        }
        return out;
    }

    /** The name, type, category and enabled cells of the integrations a SHOW lists. */
    private List<String> shown(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(rs.getColumnIndex("name")) + "|" + row.getValue(rs.getColumnIndex("type")) + "|"
                + row.getValue(rs.getColumnIndex("category")) + "|" + row.getValue(rs.getColumnIndex("enabled")));
        }
        return out;
    }

    @Test
    public void anOauthClientIsCreatedShownAndDescribed() {
        engine.execute(OAUTH + " OAUTH_TOKEN_ENDPOINT = 'https://example.com/oauth/token'"
            + " OAUTH_AUTHORIZATION_ENDPOINT = 'https://example.com/oauth/auth' OAUTH_ALLOWED_SCOPES = ('a', 'b')"
            + " OAUTH_GRANT = 'CLIENT_CREDENTIALS' OAUTH_ACCESS_TOKEN_VALIDITY = 3600"
            + " OAUTH_REFRESH_TOKEN_VALIDITY = 7200 OAUTH_CLIENT_AUTH_METHOD = CLIENT_SECRET_POST ENABLED = TRUE"
            + " COMMENT = 'sec one'");
        assertEquals(Arrays.asList("SI_SEC|API_AUTHENTICATION|SECURITY|true"), shown("SHOW INTEGRATIONS LIKE 'SI_SEC'"));
        assertEquals(Arrays.asList("SI_SEC|API_AUTHENTICATION|SECURITY|true"),
            shown("SHOW SECURITY INTEGRATIONS LIKE 'SI_SEC'"));
        assertEquals(0, shown("SHOW API INTEGRATIONS LIKE 'SI_SEC'").size());
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|true",
            "OAUTH_ACCESS_TOKEN_VALIDITY|Long|3600|",
            "OAUTH_REFRESH_TOKEN_VALIDITY|Long|7200|7776000",
            "OAUTH_CLIENT_ID|String|cid|",
            "OAUTH_CLIENT_AUTH_METHOD|String|CLIENT_SECRET_POST|CLIENT_SECRET_BASIC",
            "OAUTH_AUTHORIZATION_ENDPOINT|String|https://example.com/oauth/auth|",
            "OAUTH_TOKEN_ENDPOINT|String|https://example.com/oauth/token|",
            "OAUTH_ALLOWED_SCOPES|List|[a, b]|[]",
            "OAUTH_GRANT|String|CLIENT_CREDENTIALS|",
            "OAUTH_USERNAME|String||",
            "OAUTH_RESOURCE_URL|String||",
            "PARENT_INTEGRATION|String||",
            "AUTH_TYPE|String|OAUTH2|",
            "OAUTH_ASSERTION_ISSUER|String||",
            "COMMENT|String|sec one|"), described("si_sec"));
        assertFalse(described("si_sec").toString().contains("csecret"), "the client's secret is never told");
    }

    @Test
    public void whatIsLeftOutReadsItsDefault() {
        engine.execute(OAUTH + " ENABLED = TRUE");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|true",
            "OAUTH_ACCESS_TOKEN_VALIDITY|Long|0|",
            "OAUTH_REFRESH_TOKEN_VALIDITY|Long|0|7776000",
            "OAUTH_CLIENT_ID|String|cid|",
            "OAUTH_CLIENT_AUTH_METHOD|String||CLIENT_SECRET_BASIC",
            "OAUTH_AUTHORIZATION_ENDPOINT|String||",
            "OAUTH_TOKEN_ENDPOINT|String||",
            "OAUTH_ALLOWED_SCOPES|List||[]",
            "OAUTH_GRANT|String||",
            "OAUTH_USERNAME|String||",
            "OAUTH_RESOURCE_URL|String||",
            "PARENT_INTEGRATION|String||",
            "AUTH_TYPE|String|OAUTH2|",
            "OAUTH_ASSERTION_ISSUER|String||",
            "COMMENT|String||"), described("si_sec"));
        assertEquals("SI_SEC already exists, statement succeeded.", engine.executeQuery(
            "CREATE SECURITY INTEGRATION IF NOT EXISTS si_sec TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2"
                + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs' ENABLED = TRUE").getRows().get(0).getValue(0));
        assertEquals(PREFIX + "Object 'SI_SEC' already exists.", refusal("CREATE SECURITY INTEGRATION si_sec"
            + " TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs'"
            + " ENABLED = TRUE"));
    }

    @Test
    public void createAsksForWhatTheAuthenticationTypeNeeds() {
        final String create = "CREATE OR REPLACE SECURITY INTEGRATION si_sec2 ";
        assertEquals(PREFIX + "Missing option(s): TYPE", refusal(create + "AUTH_TYPE = OAUTH2 ENABLED = TRUE"));
        assertEquals(PREFIX + "invalid value [NOPE] for parameter 'TYPE'", refusal(create + "TYPE = NOPE ENABLED = TRUE"));
        // With ENABLED and AUTH_TYPE both left out the account names either, so each is left out alone here.
        assertEquals(PREFIX + "Missing option(s): ENABLED", refusal(create + "TYPE = API_AUTHENTICATION"
            + " AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs'"));
        assertEquals(PREFIX + "Missing option(s): AUTH_TYPE", refusal(create + "TYPE = API_AUTHENTICATION"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs' ENABLED = TRUE"));
        assertEquals(PREFIX + "invalid value [NOPE] for parameter 'AUTH_TYPE'", refusal(create
            + "TYPE = API_AUTHENTICATION AUTH_TYPE = NOPE OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs'"
            + " ENABLED = TRUE"));
        assertEquals(PREFIX + "Missing option(s): [OAUTH_CLIENT_ID]", refusal(create + "TYPE = API_AUTHENTICATION"
            + " AUTH_TYPE = OAUTH2 OAUTH_CLIENT_SECRET = 'cs' ENABLED = TRUE"));
        assertEquals(PREFIX + "Missing option(s): [OAUTH_CLIENT_SECRET]", refusal(create + "TYPE = API_AUTHENTICATION"
            + " AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' ENABLED = TRUE"));
        assertEquals(PREFIX + "Missing option(s): [OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET]", refusal(create
            + "TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2 ENABLED = TRUE"));
        assertEquals(PREFIX + "Missing option(s): [AWS_ROLE_ARN]", refusal(create + "TYPE = API_AUTHENTICATION"
            + " AUTH_TYPE = AWS_IAM ENABLED = TRUE"));
        assertEquals(PREFIX + "invalid property 'BOGUS' for 'INTEGRATION'", refusal(create + "TYPE = API_AUTHENTICATION"
            + " AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs' ENABLED = TRUE BOGUS = 1"));
        assertEquals(PREFIX + "invalid property 'OAUTH_USERNAME' for 'INTEGRATION - API_AUTHENTICATION'", refusal(create
            + "TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs'"
            + " OAUTH_USERNAME = 'u' ENABLED = TRUE"));
        assertEquals(PREFIX + "invalid property 'OAUTH_CLIENT_ID' for 'AUTH_TYPE - AWS_IAM'", refusal(create
            + "TYPE = API_AUTHENTICATION AUTH_TYPE = AWS_IAM AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/my_role'"
            + " OAUTH_CLIENT_ID = 'x' ENABLED = TRUE"));
        assertEquals(PREFIX + "invalid property 'AWS_ROLE_ARN' for 'AUTH_TYPE - OAUTH2'", refusal(create
            + "TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2 OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'cs'"
            + " AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/my_role' ENABLED = TRUE"));
    }

    @Test
    public void alterSetsWhatMayChange() {
        engine.execute(OAUTH + " OAUTH_ALLOWED_SCOPES = ('a', 'b') ENABLED = TRUE COMMENT = 'one'");
        engine.execute("ALTER SECURITY INTEGRATION si_sec SET COMMENT = 'two'");
        engine.execute("ALTER SECURITY INTEGRATION si_sec SET OAUTH_ALLOWED_SCOPES = ('a')");
        engine.execute("ALTER INTEGRATION si_sec SET OAUTH_CLIENT_ID = 'cid2'");
        assertEquals(PREFIX + "invalid property 'TYPE' for 'INTEGRATION - API_AUTHENTICATION'",
            refusal("ALTER SECURITY INTEGRATION si_sec SET TYPE = SAML2"));
        assertEquals(PREFIX + "invalid property 'AUTH_TYPE' for 'INTEGRATION - API_AUTHENTICATION'",
            refusal("ALTER SECURITY INTEGRATION si_sec SET AUTH_TYPE = AWS_IAM"));
        assertEquals(PREFIX + "invalid property 'BOGUS' for 'INTEGRATION'",
            refusal("ALTER SECURITY INTEGRATION si_sec SET BOGUS = 1"));
        assertEquals(PREFIX + "Cannot unset property 'OAUTH_ALLOWED_SCOPES' on integration 'SI_SEC'.",
            refusal("ALTER SECURITY INTEGRATION si_sec UNSET OAUTH_ALLOWED_SCOPES"));
        assertEquals("Integration SI_SEC is not a API integration.",
            refusal("ALTER API INTEGRATION si_sec SET ENABLED = TRUE"));
        assertEquals("Integration SI_SEC is not a API integration.", refusal("DROP API INTEGRATION si_sec"));
        final List<String> rows = described("si_sec");
        assertEquals("OAUTH_CLIENT_ID|String|cid2|", rows.get(3));
        assertEquals("OAUTH_ALLOWED_SCOPES|List|[a]|[]", rows.get(7));
        assertEquals("COMMENT|String|two|", rows.get(14));
        engine.execute("ALTER SECURITY INTEGRATION si_sec SET ENABLED = FALSE");
        assertEquals("ENABLED|Boolean|false|true", described("si_sec").get(0));
        engine.execute("ALTER SECURITY INTEGRATION si_sec UNSET COMMENT");
        engine.execute("ALTER SECURITY INTEGRATION si_sec UNSET ENABLED");
        assertEquals("ENABLED|Boolean|true|true", described("si_sec").get(0));
        assertEquals("COMMENT|String||", described("si_sec").get(14));
        assertEquals("SI_SEC successfully dropped.",
            engine.executeQuery("DROP SECURITY INTEGRATION si_sec").getRows().get(0).getValue(0));
    }

    @Test
    public void anAwsRoleTellsTheIdentityTheAccountGivesIt() {
        engine.execute("CREATE OR REPLACE SECURITY INTEGRATION si_iam TYPE = API_AUTHENTICATION AUTH_TYPE = AWS_IAM"
            + " AWS_ROLE_ARN = 'arn:aws:iam::123456789012:role/my_role' ENABLED = TRUE");
        assertEquals(Arrays.asList("SI_IAM|API_AUTHENTICATION|SECURITY|true"), shown("SHOW INTEGRATIONS LIKE 'SI_IAM'"));
        final List<String> properties = new ArrayList<>();
        final ResultSet rs = engine.executeQuery("DESCRIBE INTEGRATION si_iam");
        for (final Row row : rs.getRows()) {
            final String property = String.valueOf(row.getValue(0));
            properties.add(property);
            // The user and the external id are the account's own, so only their presence is common ground.
            if (property.startsWith("API_AWS_")) {
                assertFalse(String.valueOf(row.getValue(2)).isEmpty(), property);
            }
        }
        assertEquals(Arrays.asList("ENABLED", "PARENT_INTEGRATION", "API_AWS_IAM_USER_ARN", "API_AWS_EXTERNAL_ID",
            "AUTH_TYPE", "AWS_ROLE_ARN", "COMMENT"), properties);
        assertEquals("AUTH_TYPE|String|AWS_IAM|", described("si_iam").get(4));
        assertEquals("AWS_ROLE_ARN|String|arn:aws:iam::123456789012:role/my_role|", described("si_iam").get(5));
    }

    @Test
    public void anApiIntegrationOfThePackageIndexTakesNoGatewayOption() {
        final String create = "CREATE OR REPLACE API INTEGRATION si_pypi API_PROVIDER = pypi ";
        assertEquals(PREFIX + "Invalid option(s) for API integration: [API_ALLOWED_PREFIXES]", refusal(create
            + "API_ALLOWED_PREFIXES = ('https://example.com/') ENABLED = TRUE"));
        assertEquals(PREFIX + "Invalid option(s) for API integration: [API_KEY]", refusal(create
            + "API_KEY = 'k' ENABLED = TRUE"));
        assertEquals(PREFIX + "Missing option(s): ENABLED", refusal(create));
        engine.execute(create + "ENABLED = TRUE COMMENT = 'c'");
        assertEquals(Arrays.asList("SI_PYPI|EXTERNAL_API|API|true"), shown("SHOW INTEGRATIONS LIKE 'SI_PYPI'"));
        assertEquals(Arrays.asList("ENABLED|Boolean|true|true", "API_PROVIDER|String|PYPI|", "COMMENT|String|c|"),
            described("si_pypi"));
        assertEquals(PREFIX + "Invalid property on API integration with api provider PYPI.",
            refusal("ALTER API INTEGRATION si_pypi SET API_ALLOWED_PREFIXES = ('https://x/')"));
        engine.execute("ALTER API INTEGRATION si_pypi SET ENABLED = FALSE");
        engine.execute("ALTER API INTEGRATION si_pypi UNSET COMMENT");
        assertEquals(Arrays.asList("ENABLED|Boolean|false|true", "API_PROVIDER|String|PYPI|", "COMMENT|String||"),
            described("si_pypi"));
    }
}
