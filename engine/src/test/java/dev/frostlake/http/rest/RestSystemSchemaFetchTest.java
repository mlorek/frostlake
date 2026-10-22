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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The system database through the REST API, measured on the account's own: a fetch of one of its schemas is
 * refused as DESCRIBE SCHEMA refuses it, an access refusal with status 401 and code 003001 — the share's own lacking
 * the privilege, an application's asking the role for MONITOR — and three of them missing, while its
 * INFORMATION_SCHEMA is described; and its database roles are listed whole, a page size refused with status 400 and
 * code 000004 and a name to start from passed over.
 */
public class RestSystemSchemaFetchTest extends BaseRestTest {

    private static final String DATABASE = "/api/v2/databases/SNOWFLAKE";

    /** The schemas the share holds, whose fetch the share refuses for want of the privilege. */
    private static final String[] SHARED = {
        "ACCOUNT_USAGE", "ALERT", "BCR_ROLLOUT", "BILLING", "CORE", "CORTEX", "DATA_PRIVACY", "DATA_SHARING_USAGE",
        "IMAGES", "MARKETPLACE_NOTIFICATION", "ML", "MONITORING", "NOTIFICATION", "ORGANIZATION_USAGE",
        "READER_ACCOUNT_USAGE", "SNOWPARK", "SNOWPARK_CONNECT", "SPCS", "WORKLOAD_INSIGHTS", "WORKLOAD_OPTIMIZATION"
    };

    /** The SNOWFLAKE application's schemas, whose fetch asks the session's role for MONITOR. */
    private static final String[] APPLICATIONS = {
        "ACCESS_INTELLIGENCE", "DATA_SECURITY", "DEFAULT_IMAGE_STORE", "LOCAL", "MODELS", "NATIVE_APPS_SPCS",
        "NETWORK_SECURITY", "POSTGRES", "TAGS", "TELEMETRY", "TRUST_CENTER"
    };

    /** The schemas a fetch answers as missing, though the database lists them. */
    private static final String[] HIDDEN = {
        "ACCESS_INTELLIGENCE_INTERNAL", "ORG_USAGE_LOCAL", "TRUST_CENTER_INTERNAL"
    };

    /** The database roles of the system database, in the order its listing names them. */
    private static final String[] ROLES = {
        "AI_FUNCTIONS_USER", "ALERT_VIEWER", "ANACONDA_REPOSITORY_USER", "BUDGET_CREATOR", "CLASSIFICATION_ADMIN",
        "CLASSIFICATION_VIEWER", "COPILOT_USER", "CORE_VIEWER", "CORTEX_AGENT_USER", "CORTEX_ANALYST_USER",
        "CORTEX_EMBED_USER", "CORTEX_REST_API_USER", "CORTEX_SENSE_BUILDER", "CORTEX_SENSE_USER", "CORTEX_USER",
        "DATA_METRIC_USER", "DATA_PRIVACY_VIEWER", "DOCUMENT_INTELLIGENCE_CREATOR", "GOVERNANCE_ADMIN",
        "GOVERNANCE_VIEWER", "ML_USER", "MONITORING_VIEWER", "NOTIFICATION_VIEWER", "OBJECT_VIEWER",
        "ORGANIZATION_ACCOUNTS_VIEWER", "ORGANIZATION_BILLING_VIEWER", "ORGANIZATION_GOVERNANCE_VIEWER",
        "ORGANIZATION_USAGE_VIEWER", "ORG_BUDGET_CREATOR", "PYPI_REPOSITORY_USER", "QUOTA_CREATOR",
        "READER_USAGE_VIEWER", "SECURITY_VIEWER", "SHARING_USAGE_VIEWER", "SNOWFLAKE_MARKETPLACE_INVOICE_VIEWER",
        "SNOWPARK_CONNECT_VIEWER", "SPARK_USER", "SPCS_REGISTRY_VIEWER", "SPCS_VIEWER", "USAGE_ADMIN", "USAGE_VIEWER",
        "WORKLOAD_INSIGHTS_USER", "WORKLOAD_OPTIMIZATION_USER"
    };

    @Test
    public void aSystemSchemaIsNotDescribed() throws Exception {
        for (final String schema : SHARED) {
            assertRefused(schema, "sql access control error:\ninsufficient privileges to operate on schema '"
                + schema.toLowerCase(Locale.ROOT) + "'. provider share does not have sufficient privileges.");
        }
        for (final String schema : APPLICATIONS) {
            final String name = schema.toLowerCase(Locale.ROOT);
            assertRefused(schema, "sql access control error:\ninsufficient privileges to operate on schema '" + name
                + "'. your primary role " + role() + " must have monitor granted on schema snowflake." + name + ".");
        }
        for (final String schema : HIDDEN) {
            final String name = "snowflake." + schema.toLowerCase(Locale.ROOT);
            final HttpResponse<String> response = get(DATABASE + "/schemas/" + schema);
            assertEquals("\nschema '" + name + "' does not exist or not authorized. your primary role " + role()
                + " must have usage or any other privilege granted on schema " + name + ".", error(404, response),
                schema);
            assertEquals("002003", json(response).path("code").asString(), schema);
        }
        assertEquals("INFORMATION_SCHEMA", ok(get(DATABASE + "/schemas/INFORMATION_SCHEMA")).path("name").asString());
    }

    @Test
    public void theSystemDatabasesRolesAreListedWhole() throws Exception {
        final JsonNode roles = ok(get(DATABASE + "/database-roles"));
        assertEquals(String.join(",", ROLES), names(roles));
        for (final JsonNode role : roles.values()) {
            assertEquals("", role.path("comment").asString(), role.path("name").asString());
            assertEquals("", role.path("owner_role_type").asString(), role.path("name").asString());
        }
        assertEquals(String.join(",", ROLES), names(ok(get(DATABASE + "/database-roles?fromName=U"))));
        final HttpResponse<String> paged = get(DATABASE + "/database-roles?showLimit=2");
        assertEquals("unsupported feature 'show database roles in database <shared database> [starts with <s>] [limit"
            + " <l>]'; use 'show database roles in database <shared database>' instead.", error(400, paged));
        assertEquals("000004", json(paged).path("code").asString());
    }

    private void assertRefused(final String schema, final String message) throws Exception {
        final HttpResponse<String> response = get(DATABASE + "/schemas/" + schema);
        assertEquals(message, error(401, response), schema);
        assertEquals("003001", json(response).path("code").asString(), schema);
    }

    private static String role() {
        return server.getEngine().getEngine().getSecurityManager().getSessionContext().getCurrentRole()
            .toLowerCase(Locale.ROOT);
    }
}
