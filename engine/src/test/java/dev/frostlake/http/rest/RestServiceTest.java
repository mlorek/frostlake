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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The service endpoints over the Snowpark Container Services statements, declared state only. */
public class RestServiceTest extends BaseRestTest {

    private static final String SERVICES = "/api/v2/databases/svc_db/schemas/s/services";
    private static final String SPEC = "spec:\\n  containers:\\n  - name: main\\n    image: /svc_db/s/repo/app:1\\n"
        + "  endpoints:\\n  - name: api\\n    port: 8080\\n    public: true\\nserviceRoles:\\n- name: viewer\\n"
        + "  endpoints:\\n  - api\\n";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE svc_db");
        sql("CREATE SCHEMA svc_db.s");
        sql("CREATE COMPUTE POOL svc_pool MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS");
    }

    private static String service(final String name, final String extra) {
        return "{\"name\":\"" + name + "\",\"compute_pool\":\"svc_pool\",\"spec\":{\"spec_type\":\"from_inline\","
            + "\"spec_text\":\"" + SPEC + "\"}" + extra + "}";
    }

    @Test
    public void aServiceIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Service SVC_A successfully created.", ok(post(SERVICES, service("svc_a",
            ",\"min_instances\":1,\"max_instances\":2,\"comment\":\"c\""))).path("status").asString());
        final JsonNode fetched = ok(get(SERVICES + "/svc_a"));
        assertEquals("SVC_A", fetched.path("name").asString());
        assertEquals("RUNNING", fetched.path("status").asString());
        assertEquals("SVC_POOL", fetched.path("compute_pool").asString());
        assertEquals("from_inline", fetched.path("spec").path("spec_type").asString());
        assertTrue(fetched.path("spec").path("spec_text").asString().contains("endpoints:"), fetched.toString());
        assertEquals(2, fetched.path("max_instances").asInt());
        assertEquals("c", fetched.path("comment").asString());
        assertFalse(fetched.path("is_job").asBoolean());
        assertTrue(names(ok(get(SERVICES + "?like=SVC_%25"))).contains("SVC_A"));
        assertEquals("SVC_A", names(ok(get(SERVICES + "?startsWith=SVC_A&showLimit=1"))));
        ok(delete(SERVICES + "/svc_a"));
        error(404, get(SERVICES + "/svc_a"));
        error(404, delete(SERVICES + "/svc_a"));
        ok(delete(SERVICES + "/svc_a?ifExists=true"));
    }

    @Test
    public void createModesAndMissingPools() throws Exception {
        ok(post(SERVICES, service("svc_modes", "")));
        error(409, post(SERVICES, service("svc_modes", "")));
        ok(post(SERVICES + "?createMode=ifNotExists", service("svc_modes", "")));
        error(400, post(SERVICES + "?createMode=orReplace", service("svc_modes", "")));
        error(404, post(SERVICES, service("svc_nopool", "").replace("svc_pool", "no_such_pool")));
        error(400, post(SERVICES, "{\"name\":\"svc_nospec\",\"compute_pool\":\"svc_pool\"}"));
    }

    @Test
    public void theListingsReportTheDeclaredStateOnly() throws Exception {
        ok(post(SERVICES, service("svc_list", "")));
        assertEquals("[]", ok(get(SERVICES + "/svc_list/status")).path("system$get_service_status").asString());
        assertEquals("", ok(get(SERVICES + "/svc_list/logs?instanceId=0&containerName=main"))
            .path("system$get_service_logs").asString());
        assertEquals(0, ok(get(SERVICES + "/svc_list/containers")).size());
        assertEquals(0, ok(get(SERVICES + "/svc_list/instances")).size());
        final JsonNode endpoints = ok(get(SERVICES + "/svc_list/endpoints"));
        assertEquals("api", endpoints.get(0).path("name").asString());
        assertEquals(8080, endpoints.get(0).path("port").asInt());
        assertTrue(endpoints.get(0).path("is_public").asBoolean());
        assertEquals("ALL_ENDPOINTS_USAGE,VIEWER", names(ok(get(SERVICES + "/svc_list/roles"))));
        assertEquals(0, ok(get(SERVICES + "/svc_list/roles/viewer/grants-of")).size());
        assertEquals(0, ok(get(SERVICES + "/svc_list/roles/viewer/grants")).size());
        error(404, get(SERVICES + "/svc_list/roles/nobody/grants"));
        error(404, get(SERVICES + "/no_such_svc/status"));
    }

    @Test
    public void suspendResumeAndPut() throws Exception {
        ok(post(SERVICES, service("svc_put", ",\"comment\":\"c\",\"min_instances\":2,\"max_instances\":2")));
        ok(post(SERVICES + "/svc_put:suspend", null));
        assertEquals("SUSPENDED", ok(get(SERVICES + "/svc_put")).path("status").asString());
        ok(post(SERVICES + "/svc_put:resume", null));
        assertEquals("RUNNING", ok(get(SERVICES + "/svc_put")).path("status").asString());
        ok(put(SERVICES + "/svc_put", service("svc_put", ",\"max_instances\":3")));
        final JsonNode altered = ok(get(SERVICES + "/svc_put"));
        assertEquals(3, altered.path("max_instances").asInt());
        assertEquals(1, altered.path("min_instances").asInt(), altered.toString());
        assertTrue(altered.path("comment").isNull(), altered.toString());
        ok(put(SERVICES + "/svc_put_new", service("svc_put_new", "")));
        ok(get(SERVICES + "/svc_put_new"));
        error(404, post(SERVICES + "/no_such_svc:resume", null));
        ok(post(SERVICES + "/no_such_svc:resume?ifExists=true", null));
    }

    @Test
    public void aJobServiceIsRecordedAsDone() throws Exception {
        ok(post(SERVICES + ":execute-job", "{\"name\":\"job_a\",\"compute_pool\":\"svc_pool\",\"spec\":"
            + "{\"spec_type\":\"from_inline\",\"spec_text\":\"spec: {}\"}}"));
        final JsonNode job = ok(get(SERVICES + "/job_a"));
        assertEquals("DONE", job.path("status").asString());
        assertTrue(job.path("is_job").asBoolean());
    }

    @Test
    public void tagsAreSetAndUnset() throws Exception {
        sql("CREATE TAG svc_db.s.svc_tag");
        ok(post(SERVICES, service("svc_tagged", "")));
        ok(post(SERVICES + "/svc_tagged:set-tags",
            "[{\"tag_database\":\"svc_db\",\"tag_schema\":\"s\",\"tag_name\":\"svc_tag\",\"tag_value\":\"v\"}]"));
        ok(post(SERVICES + "/svc_tagged:unset-tags",
            "[{\"tag_database\":\"svc_db\",\"tag_schema\":\"s\",\"tag_name\":\"svc_tag\"}]"));
    }
}
