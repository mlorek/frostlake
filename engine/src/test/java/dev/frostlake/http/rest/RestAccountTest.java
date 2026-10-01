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

/** The account endpoints ({@code account.yaml}): account records over CREATE, DROP, UNDROP and SHOW ACCOUNTS. */
public class RestAccountTest extends BaseRestTest {

    private static final String BODY = """
        {"name":"%s","admin_name":"admin","admin_password":"TestPassword1","email":"admin@example.com",
         "edition":"ENTERPRISE","region":"AWS_US_WEST_2","comment":"rest","must_change_password":true}""";

    @Test
    public void anAccountIsCreatedListedDroppedAndRestored() throws Exception {
        assertEquals("Account ACCT_BASIC successfully created.",
            ok(post("/api/v2/accounts", String.format(BODY, "acct_basic"))).path("status").asString());
        final JsonNode accounts = ok(get("/api/v2/accounts?like=acct_bas%25"));
        assertEquals("ACCT_BASIC", names(accounts));
        final JsonNode account = accounts.get(0);
        assertEquals("ENTERPRISE", account.path("edition").asString());
        assertEquals("AWS_US_WEST_2", account.path("region").asString());
        assertEquals("rest", account.path("comment").asString());
        assertFalse(account.path("is_org_admin").asBoolean());
        assertTrue(account.path("account_locator").asString().length() > 0, account.toString());
        assertTrue(account.get("admin_password").isNull(), "a password is never answered: " + account);
        assertEquals("PUBLIC", account.path("region_group").asString(), account.toString());
        assertFalse(account.path("polaris").asBoolean(), account.toString());
        assertTrue(ok(get("/api/v2/accounts")).size() >= 2, "this account and the new one");
        assertEquals(1, ok(get("/api/v2/accounts?showLimit=1")).size());
        error(409, post("/api/v2/accounts", String.format(BODY, "acct_basic")));

        error(400, delete("/api/v2/accounts/acct_basic"));
        ok(delete("/api/v2/accounts/acct_basic?gracePeriodInDays=5"));
        assertEquals("", names(ok(get("/api/v2/accounts?like=ACCT_BASIC"))));
        final JsonNode history = ok(get("/api/v2/accounts?like=ACCT_BASIC&history=true"));
        assertEquals("ACCT_BASIC", names(history));
        assertTrue(history.get(0).path("dropped_on").asString().contains("T"), history.toString());
        assertTrue(history.get(0).has("scheduled_deletion_time"), history.toString());
        error(404, delete("/api/v2/accounts/acct_basic?gracePeriodInDays=5"));
        ok(delete("/api/v2/accounts/acct_basic?gracePeriodInDays=5&ifExists=true"));

        ok(post("/api/v2/accounts/acct_basic:undrop", null));
        assertEquals("ACCT_BASIC", names(ok(get("/api/v2/accounts?like=ACCT_BASIC"))));
        error(409, post("/api/v2/accounts/acct_basic:undrop", null));
        error(404, post("/api/v2/accounts/no_such_account:undrop", null));
    }

    @Test
    public void aCreateMissingARequiredPropertyIsRefused() throws Exception {
        error(400, post("/api/v2/accounts", "{\"name\":\"acct_bad\",\"admin_name\":\"a\",\"email\":\"e\"}"));
        error(400, post("/api/v2/accounts", "{\"name\":\"acct_bad\",\"admin_name\":\"a\",\"admin_password\":\"p\","
            + "\"email\":\"e\",\"edition\":\"ENTERPRISE; DROP\"}"));
        error(400, post("/api/v2/accounts", "{\"admin_name\":\"a\"}"));
    }
}
