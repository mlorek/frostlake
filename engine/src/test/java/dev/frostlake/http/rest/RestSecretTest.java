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

/** The secret endpoints ({@code secret.yaml}); a credential is written and never answered back. */
public class RestSecretTest extends BaseRestTest {

    private static final String SECRETS = "/api/v2/databases/sec_db/schemas/s/secrets";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE sec_db");
        sql("CREATE SCHEMA sec_db.s");
    }

    @Test
    public void aPasswordSecretIsCreatedFetchedListedAndDeletedWithoutItsPassword() throws Exception {
        assertEquals("Secret SEC_PW successfully created.", ok(post(SECRETS,
            "{\"name\":\"sec_pw\",\"type\":\"PASSWORD\",\"username\":\"jsmith\",\"password\":\"hunter2\","
            + "\"comment\":\"rest\"}")).path("status").asString());
        final JsonNode fetched = ok(get(SECRETS + "/sec_pw"));
        assertEquals("SEC_PW", fetched.path("name").asString());
        assertEquals("PASSWORD", fetched.path("type").asString());
        assertEquals("jsmith", fetched.path("username").asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("SEC_DB", fetched.path("database_name").asString());
        assertEquals("********", fetched.path("password").asString(), "a credential is answered masked");
        assertFalse(fetched.toString().contains("hunter2"), fetched.toString());

        assertEquals("SEC_PW", names(ok(get(SECRETS + "?like=sec_pw"))));
        assertFalse(ok(get(SECRETS)).toString().contains("hunter2"));
        assertEquals("SEC_PW successfully dropped.", ok(delete(SECRETS + "/sec_pw")).path("status").asString());
        error(404, get(SECRETS + "/sec_pw"));
        error(404, delete(SECRETS + "/sec_pw"));
        ok(delete(SECRETS + "/sec_pw?ifExists=true"));
    }

    @Test
    public void everyTypeMapsItsOwnProperties() throws Exception {
        ok(post(SECRETS, "{\"name\":\"sec_gs\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"tok-1\"}"));
        ok(post(SECRETS, "{\"name\":\"sec_oa\",\"type\":\"OAUTH2\",\"api_authentication\":\"my_int\","
            + "\"oauth_scopes\":[\"a\",\"b\"],\"oauth_refresh_token\":\"rt-1\","
            + "\"oauth_refresh_token_expiry_time\":\"2030-01-01\"}"));
        ok(post(SECRETS, "{\"name\":\"sec_cp\",\"type\":\"CLOUD_PROVIDER_TOKEN\",\"api_authentication\":\"aws_int\"}"));
        ok(post(SECRETS, "{\"name\":\"sec_sk\",\"type\":\"SYMMETRIC_KEY\",\"algorithm\":\"GENERIC\"}"));
        final JsonNode generic = ok(get(SECRETS + "/sec_gs"));
        assertEquals("GENERIC_STRING", generic.path("type").asString());
        assertEquals("********", generic.path("secret_string").asString());
        assertFalse(generic.toString().contains("tok-1"), generic.toString());
        final JsonNode oauth = ok(get(SECRETS + "/sec_oa"));
        assertEquals("MY_INT", oauth.path("api_authentication").asString());
        assertEquals("b", oauth.path("oauth_scopes").get(1).asString());
        assertTrue(oauth.path("oauth_refresh_token_expiry_time").asString().startsWith("2030-01-01"));
        assertFalse(oauth.toString().contains("rt-1"), oauth.toString());
        assertEquals("AWS_INT", ok(get(SECRETS + "/sec_cp")).path("api_authentication").asString());
        assertEquals("GENERIC", ok(get(SECRETS + "/sec_sk")).path("algorithm").asString());
        error(400, post(SECRETS, "{\"name\":\"sec_bad\",\"type\":\"PASSWORD\",\"username\":\"u\"}"));
        error(400, post(SECRETS, "{\"name\":\"sec_bad\"}"));
        error(400, post(SECRETS, "{\"name\":\"sec_bad\",\"type\":\"JWT_KEY_PAIR\"}"));
    }

    @Test
    public void createModesAndPaging() throws Exception {
        ok(post(SECRETS, "{\"name\":\"sec_m\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"a\",\"comment\":\"first\"}"));
        error(409, post(SECRETS, "{\"name\":\"sec_m\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"b\"}"));
        ok(post(SECRETS + "?createMode=ifNotExists",
            "{\"name\":\"sec_m\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"b\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(SECRETS + "/sec_m")).path("comment").asString());
        ok(post(SECRETS + "?createMode=orReplace",
            "{\"name\":\"sec_m\",\"type\":\"PASSWORD\",\"username\":\"u\",\"password\":\"p\"}"));
        assertEquals("PASSWORD", ok(get(SECRETS + "/sec_m")).path("type").asString());
        ok(post(SECRETS, "{\"name\":\"sec_page_a\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"a\"}"));
        ok(post(SECRETS, "{\"name\":\"sec_page_b\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"a\"}"));
        ok(post(SECRETS, "{\"name\":\"sec_page_c\",\"type\":\"GENERIC_STRING\",\"secret_string\":\"a\"}"));
        assertEquals("SEC_PAGE_A,SEC_PAGE_B,SEC_PAGE_C", names(ok(get(SECRETS + "?startsWith=SEC_PAGE"))));
        assertEquals("SEC_PAGE_B", names(ok(get(SECRETS + "?startsWith=SEC_PAGE&showLimit=1&fromName=SEC_PAGE_A"))));
        error(400, get(SECRETS + "?showLimit=0"));
        assertTrue(names(ok(get(SECRETS + "?like=sec_page%25"))).startsWith("SEC_PAGE_A"));
    }
}
