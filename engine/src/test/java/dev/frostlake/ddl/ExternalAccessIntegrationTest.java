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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CREATE, ALTER, DESCRIBE, SHOW and DROP of an external access integration, and the integrations a routine's
 * EXTERNAL_ACCESS_INTEGRATIONS may name (documented). It takes its egress network rules, the secrets and the
 * external API authentication integrations it allows, ENABLED and a comment; the rules and ENABLED are required.
 * Engine only: the live account takes no external access, and its integrations are not created there.
 */
public class ExternalAccessIntegrationTest extends BaseDatabaseTest {

    private static final String RULES = " ALLOWED_NETWORK_RULES = (eai_out)";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "the live account takes no external access integration");
        engine.execute("CREATE NETWORK RULE eai_out MODE = EGRESS TYPE = HOST_PORT VALUE_LIST = ('example.com')");
        engine.execute("CREATE NETWORK RULE eai_in MODE = INGRESS TYPE = IPV4 VALUE_LIST = ('10.0.0.1')");
        engine.execute("CREATE SECRET eai_sec TYPE = GENERIC_STRING SECRET_STRING = 'x'");
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
        }).getMessage();
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
    public void anIntegrationIsCreatedListedAndDescribed() {
        assertEquals("Integration EAI1 successfully created.", status("CREATE EXTERNAL ACCESS INTEGRATION eai1"
            + RULES + " ALLOWED_AUTHENTICATION_SECRETS = (eai_sec) ENABLED = TRUE COMMENT = 'c1'"));
        final ResultSet shown = engine.executeQuery("SHOW INTEGRATIONS LIKE 'EAI1'");
        final Row row = shown.getRows().get(0);
        assertEquals("EXTERNAL_ACCESS|SECURITY|true|c1", row.getValue(shown.getColumnIndex("type")) + "|"
            + row.getValue(shown.getColumnIndex("category")) + "|" + row.getValue(shown.getColumnIndex("enabled"))
            + "|" + row.getValue(shown.getColumnIndex("comment")));
        assertEquals(List.of("ENABLED=true", "ALLOWED_NETWORK_RULES=TEST_DB.TEST_SCHEMA.EAI_OUT",
            "ALLOWED_API_AUTHENTICATION_INTEGRATIONS=", "ALLOWED_AUTHENTICATION_SECRETS=TEST_DB.TEST_SCHEMA.EAI_SEC",
            "COMMENT=c1"), described("eai1"));
        assertEquals(1, engine.executeQuery("SHOW EXTERNAL ACCESS INTEGRATIONS").getRowCount());
    }

    @Test
    public void theRulesAndEnabledAreRequired() {
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai2 ENABLED = TRUE")
            .contains("Missing option(s): ALLOWED_NETWORK_RULES"));
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai2" + RULES).contains("Missing option(s): ENABLED"));
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai2" + RULES + " API_PROVIDER = PYPI ENABLED = TRUE")
            .contains("invalid value [PYPI] for parameter 'API_PROVIDER'"));
    }

    @Test
    public void eachNameHasToBeThereAndOfItsKind() {
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai3 ALLOWED_NETWORK_RULES = (nosuch) ENABLED = TRUE")
            .contains("Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."));
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai3 ALLOWED_NETWORK_RULES = (eai_in) ENABLED = TRUE")
            .contains("Network rule 'TEST_DB.TEST_SCHEMA.EAI_IN' is not an egress rule"));
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai3" + RULES
            + " ALLOWED_AUTHENTICATION_SECRETS = (nosuch_sec) ENABLED = TRUE")
            .contains("Secret 'NOSUCH_SEC' does not exist or operation not authorized."));
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai3" + RULES
            + " ALLOWED_API_AUTHENTICATION_INTEGRATIONS = (nosuch_int) ENABLED = TRUE")
            .contains("Integration 'NOSUCH_INT' does not exist or not authorized."));
        assertTrue(refusal("CREATE EXTERNAL ACCESS INTEGRATION eai3 ALLOWED_NETWORK_RULES = NONE ENABLED = TRUE")
            .contains("invalid value [NONE] for parameter 'ALLOWED_NETWORK_RULES'"));
        status("CREATE EXTERNAL ACCESS INTEGRATION eai3" + RULES + " ALLOWED_AUTHENTICATION_SECRETS = all"
            + " ALLOWED_API_AUTHENTICATION_INTEGRATIONS = none ENABLED = FALSE");
        assertEquals(List.of("ENABLED=false", "ALLOWED_NETWORK_RULES=TEST_DB.TEST_SCHEMA.EAI_OUT",
            "ALLOWED_API_AUTHENTICATION_INTEGRATIONS=NONE", "ALLOWED_AUTHENTICATION_SECRETS=ALL", "COMMENT="),
            described("eai3"));
    }

    @Test
    public void alterSetsAndUnsetsAndDropRemoves() {
        status("CREATE EXTERNAL ACCESS INTEGRATION eai4" + RULES + " ENABLED = TRUE COMMENT = 'c'");
        engine.execute("ALTER EXTERNAL ACCESS INTEGRATION eai4 SET ALLOWED_AUTHENTICATION_SECRETS = (eai_sec)"
            + " ENABLED = FALSE");
        engine.execute("ALTER INTEGRATION eai4 UNSET COMMENT");
        assertEquals(List.of("ENABLED=false", "ALLOWED_NETWORK_RULES=TEST_DB.TEST_SCHEMA.EAI_OUT",
            "ALLOWED_API_AUTHENTICATION_INTEGRATIONS=", "ALLOWED_AUTHENTICATION_SECRETS=TEST_DB.TEST_SCHEMA.EAI_SEC",
            "COMMENT="), described("eai4"));
        assertTrue(refusal("ALTER INTEGRATION eai4 SET ALLOWED_NETWORK_RULES = (eai_in)")
            .contains("is not an egress rule"));
        assertTrue(refusal("ALTER INTEGRATION eai4 UNSET ENABLED")
            .contains("Cannot unset property 'ENABLED' on integration 'EAI4'."));
        assertEquals("EAI4 successfully dropped.", status("DROP EXTERNAL ACCESS INTEGRATION eai4"));
    }

    @Test
    public void aRoutineNamesOnlyExternalAccessIntegrations() {
        status("CREATE EXTERNAL ACCESS INTEGRATION eai5" + RULES + " ENABLED = TRUE");
        engine.execute("CREATE API INTEGRATION eai_api API_PROVIDER = aws_api_gateway"
            + " API_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r' API_ALLOWED_PREFIXES = ('https://a1.execute-api.us-east-1.amazonaws.com/')"
            + " ENABLED = TRUE");
        engine.execute("CREATE FUNCTION eai_f(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT"
            + " EXTERNAL_ACCESS_INTEGRATIONS = (eai5) AS 'return X;'");
        assertTrue(refusal("CREATE FUNCTION eai_g(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT"
            + " EXTERNAL_ACCESS_INTEGRATIONS = (eai_api) AS 'return X;'")
            .contains("Integration 'EAI_API' is not an external access integration."));
    }
}
