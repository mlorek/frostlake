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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API integration endpoints ({@code api-integration.yaml}). */
public class RestApiIntegrationTest extends BaseRestTest {

    private static final String AWS = "{\"name\":\"%s\",\"api_hook\":{\"type\":\"AWS\","
        + "\"api_provider\":\"AWS_API_GATEWAY\",\"api_aws_role_arn\":\"arn:aws:iam::1:role/r\",\"api_key\":\"k\"},"
        + "\"api_allowed_prefixes\":[\"https://a.example.com/\"],\"enabled\":true,\"comment\":\"%s\"}";

    @Test
    public void anApiIntegrationIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Integration RI_API1 successfully created.",
            ok(post("/api/v2/api-integrations", String.format(AWS, "ri_api1", "first"))).path("status").asString());
        final JsonNode fetched = ok(get("/api/v2/api-integrations/ri_api1"));
        assertEquals("RI_API1", fetched.path("name").asString());
        assertEquals("AWS", fetched.path("api_hook").path("type").asString());
        assertEquals("AWS_API_GATEWAY", fetched.path("api_hook").path("api_provider").asString());
        assertEquals("arn:aws:iam::1:role/r", fetched.path("api_hook").path("api_aws_role_arn").asString());
        assertEquals("\u263A", fetched.path("api_hook").path("api_key").asString(), "the key comes back masked");
        assertEquals("https://a.example.com/", fetched.path("api_allowed_prefixes").get(0).asString());
        assertTrue(fetched.path("api_blocked_prefixes").isNull(), fetched.toString());
        assertTrue(fetched.path("enabled").asBoolean());
        assertEquals("first", fetched.path("comment").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());

        assertEquals("RI_API1", names(ok(get("/api/v2/api-integrations?like=RI_API1"))));
        assertEquals("", names(ok(get("/api/v2/api-integrations?like=nothing_like_this"))));

        assertEquals("RI_API1 successfully dropped.", ok(delete("/api/v2/api-integrations/ri_api1"))
            .path("status").asString());
        error(404, get("/api/v2/api-integrations/ri_api1"));
        error(404, delete("/api/v2/api-integrations/ri_api1"));
        ok(delete("/api/v2/api-integrations/ri_api1?ifExists=true"));
    }

    @Test
    public void createModesAndTheOtherHookTypes() throws Exception {
        ok(post("/api/v2/api-integrations", String.format(AWS, "ri_modes", "first")));
        error(409, post("/api/v2/api-integrations", String.format(AWS, "ri_modes", "second")));
        ok(post("/api/v2/api-integrations?createMode=ifNotExists", String.format(AWS, "ri_modes", "second")));
        assertEquals("first", ok(get("/api/v2/api-integrations/ri_modes")).path("comment").asString());
        ok(post("/api/v2/api-integrations?createMode=orReplace", String.format(AWS, "ri_modes", "third")));
        assertEquals("third", ok(get("/api/v2/api-integrations/ri_modes")).path("comment").asString());

        ok(post("/api/v2/api-integrations", "{\"name\":\"ri_git\",\"api_hook\":{\"type\":\"GIT\","
            + "\"allow_any_secret\":true},\"api_allowed_prefixes\":[\"https://github.com/x\"],\"enabled\":false}"));
        final JsonNode git = ok(get("/api/v2/api-integrations/ri_git")).path("api_hook");
        assertEquals("GIT", git.path("type").asString());
        assertTrue(git.path("allow_any_secret").asBoolean(), git.toString());
        assertFalse(git.has("api_provider"), git.toString());

        ok(post("/api/v2/api-integrations", "{\"name\":\"ri_azure\",\"api_hook\":{\"type\":\"AZURE\","
            + "\"api_provider\":\"AZURE_API_MANAGEMENT\",\"azure_tenant_id\":\"t\",\"azure_ad_application_id\":\"a\"},"
            + "\"api_allowed_prefixes\":[\"https://z/\"],\"enabled\":true}"));
        assertEquals("a", ok(get("/api/v2/api-integrations/ri_azure")).path("api_hook")
            .path("azure_ad_application_id").asString());
        error(400, post("/api/v2/api-integrations", "{\"name\":\"ri_bad\",\"api_hook\":{\"type\":\"AWS\","
            + "\"api_provider\":\"GOOGLE_API_GATEWAY\"},\"api_allowed_prefixes\":[\"x\"],\"enabled\":true}"));
        error(400, post("/api/v2/api-integrations", "{\"name\":\"ri_bad\",\"api_allowed_prefixes\":[\"x\"]}"));
    }

    @Test
    public void putCreatesThenAltersAndUnsetsWhatTheBodyLeavesOut() throws Exception {
        ok(put("/api/v2/api-integrations/ri_put", String.format(AWS, "ri_put", "created")));
        assertEquals("created", ok(get("/api/v2/api-integrations/ri_put")).path("comment").asString());
        ok(put("/api/v2/api-integrations/ri_put", "{\"name\":\"ri_put\",\"api_hook\":{\"type\":\"AWS\","
            + "\"api_provider\":\"AWS_API_GATEWAY\",\"api_aws_role_arn\":\"arn:aws:iam::1:role/r2\"},"
            + "\"api_allowed_prefixes\":[\"https://b/\"],\"api_blocked_prefixes\":[\"https://b/x\"],"
            + "\"enabled\":false}"));
        final JsonNode fetched = ok(get("/api/v2/api-integrations/ri_put"));
        assertEquals("arn:aws:iam::1:role/r2", fetched.path("api_hook").path("api_aws_role_arn").asString());
        assertEquals("https://b/x", fetched.path("api_blocked_prefixes").get(0).asString());
        assertFalse(fetched.path("enabled").asBoolean());
        assertTrue(fetched.path("comment").isNull(), "a comment the body leaves out is unset: " + fetched);
        error(400, put("/api/v2/api-integrations/ri_put", "{\"name\":\"ri_put\",\"api_hook\":{\"type\":\"GIT\"},"
            + "\"api_allowed_prefixes\":[\"x\"],\"enabled\":true}"));
        error(409, put("/api/v2/api-integrations/ri_put", String.format(AWS, "other_name", "x")));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE DATABASE IF NOT EXISTS ri_tag_db");
        sql("CREATE TAG IF NOT EXISTS ri_tag_db.public.ri_tag");
        ok(post("/api/v2/api-integrations", String.format(AWS, "ri_tagged", "t")));
        ok(post("/api/v2/api-integrations/ri_tagged:set-tags",
            "[{\"tag_database\":\"ri_tag_db\",\"tag_schema\":\"public\",\"tag_name\":\"ri_tag\",\"tag_value\":\"v1\"}]"));
        final JsonNode tags = ok(get("/api/v2/api-integrations/ri_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("v1", tags.get(0).path("tag_value").asString());
        ok(post("/api/v2/api-integrations/ri_tagged:unset-tags",
            "[{\"tag_database\":\"ri_tag_db\",\"tag_schema\":\"public\",\"tag_name\":\"ri_tag\"}]"));
        assertEquals(0, ok(get("/api/v2/api-integrations/ri_tagged:get-tags")).size());
    }
}
