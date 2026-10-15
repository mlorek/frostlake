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
package dev.frostlake.http.rest;

import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two answers of the REST API measured on the account's own API. A request under a schema the system database
 * serves without holding it — ACCOUNT_USAGE, and INFORMATION_SCHEMA alike — names the object that is missing, in
 * the sentence a statement gives it, and not the schema. And a database role's grants are listed by a statement
 * that reads the session's current database: a session that has none is refused for that with status 400 and
 * code 090105, whether the path's database is there or not. What the listing answers under a current database
 * could not be measured, and stays as it was.
 */
public class RestSystemSchemaAndRoleGrantsTest extends BaseRestTest {

    private static final String USAGE = "/api/v2/databases/SNOWFLAKE/schemas/ACCOUNT_USAGE/";

    /** The collection, the name asked for, the noun of the sentence and the kind its privilege is granted on. */
    private static final String[][] KINDS = {
        {"tables", "NOSUCH_T", "table", "table"},
        {"views", "NOSUCH_V", "view", "table"},
        {"stages", "NOSUCH_S", "stage", "stage"},
        {"tasks", "NOSUCH_K", "task", "task"},
        {"streams", "NOSUCH_M", "stream", "stream"},
        {"dynamic-tables", "NOSUCH_D", "dynamic table", "table"},
        {"pipes", "NOSUCH_P", "pipe", "pipe"},
        {"alerts", "NOSUCH_A", "alert", "alert"},
        {"event-tables", "NOSUCH_E", "event table", "table"},
        {"notebooks", "NOSUCH_N", "notebook", "notebook"},
        {"functions", "NOSUCH_F()", "function", "function"},
        {"procedures", "NOSUCH_Q()", "procedure", "procedure"},
        {"user-defined-functions", "NOSUCH_U()", "function", "function"},
        {"iceberg-tables", "NOSUCH_I", "iceberg table", "table"},
        {"cortex-search-services", "NOSUCH_C", "cortex search service", "cortex search service"},
        {"image-repositories", "NOSUCH_R", "image repository", "stage"},
        {"services", "NOSUCH_W", "service", "service"},
        {"streamlits", "NOSUCH_L", "streamlit", "streamlit"},
    };

    @Test
    public void aMissingObjectOfASystemSchemaIsNamedAndNotTheSchema() throws Exception {
        for (final String[] kind : KINDS) {
            assertMissing(USAGE + kind[0] + "/" + kind[1], kind[2], kind[3], "snowflake.account_usage."
                + bare(kind[1]));
        }
        assertMissing("/api/v2/databases/SNOWFLAKE/schemas/INFORMATION_SCHEMA/tables/NOSUCH_T", "table", "table",
            "snowflake.information_schema.nosuch_t");
        assertMissing("/api/v2/databases/SNOWFLAKE/schemas/INFORMATION_SCHEMA/views/NOSUCH_V", "view", "table",
            "snowflake.information_schema.nosuch_v");
    }

    @Test
    public void aSchemaTheSystemDatabaseDoesNotServeIsStillTheOneMissing() throws Exception {
        final String role = role();
        final String schema = "\nschema 'snowflake.nosuch_s_zq7' does not exist or not authorized. your primary role "
            + role + " must have usage or any other privilege granted on schema snowflake.nosuch_s_zq7.";
        assertEquals(schema, error(404, get("/api/v2/databases/SNOWFLAKE/schemas/NOSUCH_S_ZQ7")));
        assertEquals(schema, error(404, get("/api/v2/databases/SNOWFLAKE/schemas/NOSUCH_S_ZQ7/tables/NOSUCH_T")));
        assertEquals(schema, error(404, get("/api/v2/databases/SNOWFLAKE/schemas/NOSUCH_S_ZQ7/views/NOSUCH_V")));
    }

    @Test
    public void aRolesGrantsWantACurrentDatabase() throws Exception {
        final SessionContext session = server.getEngine().createSession();
        try {
            run(session, "CREATE OR REPLACE DATABASE REST_NO_DB_R9");
            run(session, "USE DATABASE REST_NO_DB_R9");
            run(session, "DROP DATABASE REST_NO_DB_R9");
            final String refusal = "cannot perform show grants. this session does not have a current database. call"
                + " 'use database', or use a qualified name.";
            for (final String database : new String[] {"SNOWFLAKE", "NOSUCH_DB_R9REST"}) {
                final HttpResponse<String> response = inSession(session, "/api/v2/databases/" + database
                    + "/database-roles/NOSUCH_DR/grants");
                assertEquals(refusal, error(400, response), database);
                assertEquals("090105", json(response).path("code").asString(), database);
                assertEquals("090105", json(response).path("error_code").asString(), database);
            }
        } finally {
            server.getEngine().removeSession(session.getSessionId());
        }
    }

    private void assertMissing(final String path, final String noun, final String kind, final String name)
            throws Exception {
        final HttpResponse<String> response = get(path.replace("(", "%28").replace(")", "%29"));
        assertEquals("\n" + noun + " '" + name + "' does not exist or not authorized. your primary role " + role()
            + " must have at least one privilege granted on " + kind + " " + name + ".", error(404, response), path);
        assertEquals("002003", json(response).path("code").asString(), path);
    }

    private static String bare(final String name) {
        return name.replace("()", "").toLowerCase(Locale.ROOT);
    }

    private static String role() {
        return server.getEngine().getEngine().getSecurityManager().getSessionContext().getCurrentRole()
            .toLowerCase(Locale.ROOT);
    }

    private static void run(final SessionContext session, final String statement) {
        final ExecutionResult result = server.getEngine().execute(statement, session);
        assertTrue(result.isSuccess(), statement + " -> " + result.getErrorMessage());
    }

    private HttpResponse<String> inSession(final SessionContext session, final String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET()
            .header("Accept", "application/json").header("X-Sfc-Session", session.getSessionId()).build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
