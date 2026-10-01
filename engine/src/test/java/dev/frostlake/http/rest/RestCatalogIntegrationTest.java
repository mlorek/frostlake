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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The catalog integration endpoints ({@code catalog-integration.yaml}). */
public class RestCatalogIntegrationTest extends BaseRestTest {

    @Test
    public void aGlueIntegrationRoundTrips() throws Exception {
        assertEquals("Integration RC_GLUE successfully created.", ok(post("/api/v2/catalog-integrations",
            "{\"name\":\"rc_glue\",\"catalog\":{\"catalog_source\":\"GLUE\",\"glue_aws_role_arn\":\"arn:g\","
                + "\"glue_catalog_id\":\"123\",\"glue_region\":\"us-east-1\",\"catalog_namespace\":\"ns\"},"
                + "\"table_format\":\"ICEBERG\",\"enabled\":true,\"comment\":\"glue\"}")).path("status").asString());
        final JsonNode fetched = ok(get("/api/v2/catalog-integrations/rc_glue"));
        assertEquals("GLUE", fetched.path("catalog").path("catalog_source").asString());
        assertEquals("123", fetched.path("catalog").path("glue_catalog_id").asString());
        assertEquals("ns", fetched.path("catalog").path("catalog_namespace").asString());
        assertEquals("ICEBERG", fetched.path("table_format").asString());
        assertEquals("CATALOG", fetched.path("type").asString());
        assertEquals("CATALOG", fetched.path("category").asString());
        assertEquals("glue", fetched.path("comment").asString());
        assertEquals("RC_GLUE", names(ok(get("/api/v2/catalog-integrations?like=RC_GLUE"))));
        error(409, post("/api/v2/catalog-integrations", "{\"name\":\"rc_glue\",\"catalog\":{\"catalog_source\":"
            + "\"OBJECT_STORE\"},\"table_format\":\"ICEBERG\",\"enabled\":true}"));
        ok(delete("/api/v2/catalog-integrations/rc_glue"));
        error(404, get("/api/v2/catalog-integrations/rc_glue"));
        ok(delete("/api/v2/catalog-integrations/rc_glue?ifExists=true"));
    }

    @Test
    public void aPolarisIntegrationKeepsItsSecretWriteOnly() throws Exception {
        ok(post("/api/v2/catalog-integrations", "{\"name\":\"rc_pol\",\"catalog\":{\"catalog_source\":\"POLARIS\","
            + "\"catalog_namespace\":\"ns\",\"rest_config\":{\"catalog_uri\":\"https://p/api/catalog\","
            + "\"warehouse\":\"wh\"},\"rest_authentication\":{\"type\":\"OAUTH\",\"oauth_client_id\":\"id\","
            + "\"oauth_client_secret\":\"sec\",\"oauth_allowed_scopes\":[\"PRINCIPAL_ROLE:ALL\"]}},"
            + "\"table_format\":\"ICEBERG\",\"enabled\":true}"));
        final JsonNode catalog = ok(get("/api/v2/catalog-integrations/rc_pol")).path("catalog");
        assertEquals("https://p/api/catalog", catalog.path("rest_config").path("catalog_uri").asString());
        assertEquals("wh", catalog.path("rest_config").path("warehouse").asString());
        assertEquals("id", catalog.path("rest_authentication").path("oauth_client_id").asString());
        assertFalse("sec".equals(catalog.path("rest_authentication").path("oauth_client_secret").asString()),
            catalog.toString());
        assertEquals("PRINCIPAL_ROLE:ALL", catalog.path("rest_authentication").path("oauth_allowed_scopes").get(0)
            .asString());
        ok(post("/api/v2/catalog-integrations", "{\"name\":\"rc_obj\",\"catalog\":{\"catalog_source\":"
            + "\"OBJECT_STORE\"},\"table_format\":\"ICEBERG\",\"enabled\":false}"));
        assertEquals("OBJECT_STORE", ok(get("/api/v2/catalog-integrations/rc_obj")).path("catalog")
            .path("catalog_source").asString());
        error(400, post("/api/v2/catalog-integrations", "{\"name\":\"rc_bad\",\"catalog\":{\"catalog_source\":"
            + "\"GLUE\"},\"table_format\":\"ICEBERG\",\"enabled\":true}"));
    }
}
