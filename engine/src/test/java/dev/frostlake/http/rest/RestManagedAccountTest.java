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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The managed account endpoints ({@code managed-account.yaml}): reader accounts over CREATE, DROP and SHOW. */
public class RestManagedAccountTest extends BaseRestTest {

    @Test
    public void aManagedAccountIsCreatedListedAndDropped() throws Exception {
        final String status = ok(post("/api/v2/managed-accounts", """
            {"name":"reader_basic","admin_name":"admin","admin_password":"Sdfed43da!44","account_type":"READER",
             "comment":"rest"}""")).path("status").asString();
        assertTrue(status.contains("\"accountName\":\"READER_BASIC\""), status);
        final JsonNode accounts = ok(get("/api/v2/managed-accounts?like=reader_%25"));
        assertEquals("READER_BASIC", names(accounts));
        final JsonNode account = accounts.get(0);
        assertEquals("READER", account.path("account_type").asString());
        assertEquals("rest", account.path("comment").asString());
        assertEquals("AWS", account.path("cloud").asString());
        assertTrue(account.path("locator").asString().length() > 0, account.toString());
        assertTrue(account.path("created_on").asString().contains("T"), account.toString());
        assertEquals("", names(ok(get("/api/v2/managed-accounts?like=nothing%25"))));
        error(409, post("/api/v2/managed-accounts",
            "{\"name\":\"reader_basic\",\"admin_name\":\"a\",\"admin_password\":\"p\",\"account_type\":\"READER\"}"));

        ok(delete("/api/v2/managed-accounts/reader_basic"));
        assertEquals("", names(ok(get("/api/v2/managed-accounts"))));
        error(404, delete("/api/v2/managed-accounts/reader_basic"));
    }

    @Test
    public void aCreateMissingARequiredPropertyIsRefused() throws Exception {
        error(400, post("/api/v2/managed-accounts", "{\"name\":\"reader_bad\",\"admin_name\":\"a\"}"));
        error(400, post("/api/v2/managed-accounts",
            "{\"name\":\"reader_bad\",\"admin_name\":\"a\",\"admin_password\":\"p\",\"account_type\":\"FULL\"}"));
    }
}
