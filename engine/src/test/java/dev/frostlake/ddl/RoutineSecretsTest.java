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
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * SECRETS and EXTERNAL_ACCESS_INTEGRATIONS on a routine (live-verified). A secret is written
 * {@code 'variable' = name} and has to exist; the listings tell the secrets as one JSON object, named from the
 * routine's own schema; ALTER … SET replaces them and UNSET leaves them; neither a description nor the DDL tells
 * them. A routine that names secrets and no integration to allow them cannot be called. The account measured
 * against takes no external access integration at all, so what a valid list of them does is pinned on the engine
 * alone.
 */
public class RoutineSecretsTest extends BaseDatabaseTest {

    private static final String JS = "CREATE OR REPLACE FUNCTION rs_j(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT ";
    private static final String JS_BODY = " AS 'return X;'";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String shown(final String listing, final String column) {
        final ResultSet rows = engine.executeQuery(listing);
        return cell(rows, rows.getRows().get(0), column);
    }

    private void secrets() {
        engine.execute("CREATE OR REPLACE SECRET rs_sec TYPE = GENERIC_STRING SECRET_STRING = 'v'");
        engine.execute("CREATE SCHEMA rs_other");
        engine.execute("CREATE OR REPLACE SECRET rs_other.rs_sec2 TYPE = GENERIC_STRING SECRET_STRING = 'w'");
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    @Test
    public void theListingsTellTheSecretsFromTheRoutinesSchema() {
        secrets();
        engine.execute("CREATE FUNCTION rs_f(x NUMBER) RETURNS NUMBER SECRETS = ('k' = rs_sec, 'K2' ="
            + " rs_other.rs_sec2, '' = rs_sec) AS 'x'");
        final String told = "{\"\":\"RS_SEC\",\"K2\":\"RS_OTHER.RS_SEC2\",\"k\":\"RS_SEC\"}";
        assertEquals(told, shown("SHOW USER FUNCTIONS LIKE 'RS_F'", "secrets"));
        assertEquals("", shown("SHOW USER FUNCTIONS LIKE 'RS_F'", "external_access_integrations"));
        assertEquals(told, String.valueOf(engine.executeQuery("SELECT secrets FROM information_schema.functions"
            + " WHERE function_name = 'RS_F'").getRows().get(0).getValue(0)));
        final ResultSet described = engine.executeQuery("DESCRIBE FUNCTION rs_f(NUMBER)");
        assertTrue(rowsWhere(described, "property", "secrets").isEmpty(), "a description tells no secrets");
        assertFalse(String.valueOf(engine.executeQuery("SELECT GET_DDL('FUNCTION', 'rs_f(NUMBER)')").getRows()
            .get(0).getValue(0)).contains("SECRETS"), "and neither does the DDL");
        engine.execute("CREATE FUNCTION rs_plain(x NUMBER) RETURNS NUMBER AS 'x'");
        assertEquals("", shown("SHOW USER FUNCTIONS LIKE 'RS_PLAIN'", "secrets"));
    }

    @Test
    public void aSecretIsAVariableAndAName() {
        secrets();
        assertTrue(refusal(JS + "SECRETS = ('k' = rs_nosuch)" + JS_BODY)
            .contains("Secret 'RS_NOSUCH' does not exist or operation not authorized."));
        assertTrue(refusal(JS + "SECRETS = ('k' = test_db.rs_noschema.rs_sec)" + JS_BODY)
            .contains("Secret 'RS_SEC' does not exist or operation not authorized."), "by its last part alone");
        assertEquals("SQL compilation error: Duplicate secret name 'k' passed in.",
            refusal(JS + "SECRETS = ('k' = rs_sec, 'k' = rs_other.rs_sec2)" + JS_BODY));
        assertTrue(refusal(JS + "SECRETS = (rs_sec)" + JS_BODY)
            .contains("invalid value 'rs_sec' for property 'SECRETS'"));
        assertTrue(refusal(JS + "SECRETS = ('k')" + JS_BODY).contains("invalid value 'k' for property 'SECRETS'"));
        assertTrue(refusal(JS + "SECRETS = rs_sec" + JS_BODY)
            .contains("invalid value 'rs_sec' for property 'SECRETS'"));
        assertEquals("SQL compilation error: Secret names must be specified using single quotes.",
            refusal(JS + "SECRETS = (k = rs_sec)" + JS_BODY));
        assertEquals("SQL compilation error: Secret values must be specified using object identifier.",
            refusal(JS + "SECRETS = ('k' = 'rs_sec')" + JS_BODY));
        engine.execute(JS + "SECRETS = ('a' = rs_nosuch) SECRETS = ('b' = rs_sec, 'c' = rs_sec)" + JS_BODY);
        assertEquals("{\"b\":\"RS_SEC\",\"c\":\"RS_SEC\"}", shown("SHOW USER FUNCTIONS LIKE 'RS_J'", "secrets"),
            "a repeated list is read once, the last one written, and one secret may serve two variables");
        assertTrue(refusal("CREATE OR REPLACE PROCEDURE rs_p() RETURNS VARCHAR LANGUAGE SQL SECRETS = () AS"
            + " 'BEGIN RETURN 1; END'").contains("invalid property 'SECRETS' for 'PROCEDURE'"));
    }

    @Test
    public void alterReplacesTheSecretsAndUnsetLeavesThem() {
        secrets();
        final String cell = "SHOW USER FUNCTIONS LIKE 'RS_J'";
        engine.execute(JS + "SECRETS = ('k' = rs_sec)" + JS_BODY);
        engine.execute("ALTER FUNCTION rs_j(FLOAT) SET SECRETS = ('z' = rs_other.rs_sec2)");
        assertEquals("{\"z\":\"RS_OTHER.RS_SEC2\"}", shown(cell, "secrets"));
        engine.execute("ALTER FUNCTION rs_j(FLOAT) SET COMMENT = 'c', SECRETS = ('b' = rs_sec)");
        assertEquals("{\"b\":\"RS_SEC\"}", shown(cell, "secrets"), "beside another property, with or without a comma");
        engine.execute("ALTER FUNCTION rs_j(FLOAT) UNSET SECRETS");
        assertEquals("{\"b\":\"RS_SEC\"}", shown(cell, "secrets"), "UNSET is taken and changes nothing");
        engine.execute("ALTER FUNCTION rs_j(FLOAT) SET SECRETS = ()");
        assertEquals("{}", shown(cell, "secrets"));
        assertTrue(refusal("ALTER FUNCTION rs_j(FLOAT) SET SECRETS = ('a' = rs_nosuch)")
            .contains("Secret 'RS_NOSUCH' does not exist or operation not authorized."));
        engine.execute("CREATE FUNCTION rs_q(x NUMBER) RETURNS NUMBER AS 'x'");
        assertTrue(refusal("ALTER FUNCTION rs_q(NUMBER) SET SECRETS = ('z' = rs_sec)")
            .contains("invalid property 'SECRETS' for 'FUNCTION'"), "a SQL function's secrets are no property");
        engine.execute("CREATE PROCEDURE rs_sp() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertTrue(refusal("ALTER PROCEDURE rs_sp() UNSET SECRETS")
            .contains("invalid property 'SECRETS' for 'PROCEDURE'"));
        assertTrue(refusal("ALTER PROCEDURE rs_sp() SET EXTERNAL_ACCESS_INTEGRATIONS = ()")
            .contains("invalid property 'EXTERNAL_ACCESS_INTEGRATIONS' for 'PROCEDURE'"));
    }

    @Test
    public void aRoutineWithSecretsAndNoIntegrationCannotBeCalled() {
        secrets();
        engine.execute("CREATE FUNCTION rs_f(x NUMBER) RETURNS NUMBER SECRETS = ('k' = rs_sec) AS 'x'");
        engine.execute("CREATE TABLE rs_t (a NUMBER)");
        engine.execute("INSERT INTO rs_t VALUES (1)");
        assertTrue(refusal("SELECT rs_f(1)").contains("invalid property 'SECRETS' for 'FUNCTION'"));
        assertTrue(refusal("SELECT rs_f(a) FROM rs_t").contains("invalid property 'SECRETS' for 'FUNCTION'"));
        engine.execute("DROP SECRET rs_sec");
        assertTrue(refusal("SELECT rs_f(1)").contains("Object 'RS_SEC' does not exist or not authorized."),
            "a secret that is gone is told first");
        assertEquals("{\"k\":\"RS_SEC\"}", shown("SHOW USER FUNCTIONS LIKE 'RS_F'", "secrets"),
            "and the routine goes on naming it");
    }

    @Test
    public void anIntegrationListIsParenthesizedPlainNames() {
        assertTrue(refusal(JS + "EXTERNAL_ACCESS_INTEGRATIONS = rs_int" + JS_BODY)
            .contains("invalid value 'rs_int' for property 'EXTERNAL_ACCESS_INTEGRATIONS'"));
        assertTrue(refusal(JS + "EXTERNAL_ACCESS_INTEGRATIONS = ('rs_int')" + JS_BODY)
            .contains("invalid value ''rs_int'' for property 'EXTERNAL_ACCESS_INTEGRATIONS'"));
        assertTrue(refusal(JS + "EXTERNAL_ACCESS_INTEGRATIONS = (a.b)" + JS_BODY)
            .contains("invalid value 'a.b' for property 'EXTERNAL_ACCESS_INTEGRATIONS'"));
        assertTrue(refusal("CREATE OR REPLACE PROCEDURE rs_p() RETURNS VARCHAR LANGUAGE SQL"
            + " EXTERNAL_ACCESS_INTEGRATIONS = () AS 'BEGIN RETURN 1; END'")
            .contains("invalid property 'EXTERNAL_ACCESS_INTEGRATIONS' for 'PROCEDURE'"));
    }

    @Test
    public void aNamedIntegrationHasToExistAndAllowsTheSecrets() {
        assumeFalse(isLiveSnowflake(), "the measured account takes no external access integration");
        secrets();
        assertTrue(refusal(JS + "EXTERNAL_ACCESS_INTEGRATIONS = (rs_noint)" + JS_BODY)
            .contains("Integration 'RS_NOINT' does not exist or not authorized."));
        // No statement of the engine creates such an integration yet, so one is put into the catalog as it is.
        engine.getCatalog().getIntegrations().put(new Integration("RS_INT", IntegrationKind.EXTERNAL_ACCESS));
        engine.execute("CREATE FUNCTION rs_f(x NUMBER) RETURNS NUMBER EXTERNAL_ACCESS_INTEGRATIONS = (rs_int)"
            + " SECRETS = ('k' = rs_sec) AS 'x'");
        assertEquals("[\"RS_INT\"]", shown("SHOW USER FUNCTIONS LIKE 'RS_F'", "external_access_integrations"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT rs_f(1)").getRows().get(0).getValue(0)),
            "secrets an integration allows may be used");
        engine.execute(JS + "EXTERNAL_ACCESS_INTEGRATIONS = (rs_int)" + JS_BODY);
        engine.execute("ALTER FUNCTION rs_j(FLOAT) UNSET EXTERNAL_ACCESS_INTEGRATIONS");
        assertEquals("", shown("SHOW USER FUNCTIONS LIKE 'RS_J'", "external_access_integrations"));
        engine.getCatalog().getIntegrations().remove("RS_INT");
    }
}
