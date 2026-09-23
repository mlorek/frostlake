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

/** The service function endpoints: CREATE FUNCTION … SERVICE = … ENDPOINT = … recorded as metadata. */
public class RestFunctionTest extends BaseRestTest {

    private static final String FUNCTIONS = "/api/v2/databases/sf_db/schemas/s/functions";

    @BeforeAll
    public static void createService() {
        sql("CREATE DATABASE sf_db");
        sql("CREATE SCHEMA sf_db.s");
        sql("CREATE COMPUTE POOL sf_pool MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS");
        sql("CREATE SERVICE sf_db.s.echo_svc IN COMPUTE POOL sf_pool FROM SPECIFICATION 'spec: {}'");
        sql("CREATE FUNCTION sf_db.s.plain_fn() RETURNS NUMBER AS '1'");
    }

    private static String function(final String name) {
        return "{\"function_type\":\"service-function\",\"name\":\"" + name + "\",\"arguments\":[{\"name\":\"x\","
            + "\"datatype\":\"VARCHAR\"}],\"returns\":\"VARCHAR\",\"max_batch_rows\":10,\"service\":\"echo_svc\","
            + "\"endpoint\":\"api\",\"path\":\"/echo\"}";
    }

    @Test
    public void aServiceFunctionIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Function ECHO_FN successfully created.", ok(post(FUNCTIONS, function("echo_fn")))
            .path("status").asString());
        final JsonNode fetched = ok(get(FUNCTIONS + "/echo_fn(VARCHAR)"));
        assertEquals("service-function", fetched.path("function_type").asString());
        assertEquals("ECHO_FN", fetched.path("name").asString());
        assertEquals("X", fetched.path("arguments").get(0).path("name").asString());
        assertEquals("VARCHAR", fetched.path("returns").asString());
        assertEquals(10, fetched.path("max_batch_rows").asInt());
        assertEquals("ECHO_SVC", fetched.path("service").asString());
        assertEquals("S", fetched.path("service_schema").asString());
        assertEquals("API", fetched.path("endpoint").asString());
        assertEquals("/echo", fetched.path("path").asString());
        assertEquals("ECHO_FN,PLAIN_FN", names(ok(get(FUNCTIONS))));
        assertEquals("SQL", ok(get(FUNCTIONS + "/plain_fn()")).path("language_config").path("language").asString());
        error(501, post(FUNCTIONS + "/echo_fn:execute", "[{\"name\":\"x\",\"value\":\"a\"}]"));
        error(409, post(FUNCTIONS, function("echo_fn")));
        ok(post(FUNCTIONS + "?createMode=orReplace", function("echo_fn")));
        ok(delete(FUNCTIONS + "/echo_fn(VARCHAR)"));
        error(404, get(FUNCTIONS + "/echo_fn(VARCHAR)"));
        ok(delete(FUNCTIONS + "/echo_fn(VARCHAR)?ifExists=true"));
    }

    @Test
    public void aFunctionOnAMissingServiceIsRefused() throws Exception {
        error(404, post(FUNCTIONS, function("orphan_fn").replace("echo_svc", "no_such_svc")));
        error(400, post(FUNCTIONS, "{\"name\":\"no_path\",\"arguments\":[],\"service\":\"echo_svc\",\"endpoint\":\"api\"}"));
    }
}
