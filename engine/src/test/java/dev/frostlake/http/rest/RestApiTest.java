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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conventions every {@code /api/v2} endpoint shares ({@code common.yaml}): routing and its bare {@code 404},
 * {@code 405} and {@code 415}, the {@code ErrorResponse} body and request ids, identifiers in paths, the
 * unchecked authorization headers, and asynchronous execution through result handles.
 */
public class RestApiTest extends BaseRestTest {

    private static final Logger logger = LoggerFactory.getLogger(RestApiTest.class);

    @Test
    public void aPathNoRouteServesIsABare404WithoutARequestId() throws Exception {
        final HttpResponse<String> response = get("/api/v2/nosuchthing");
        assertEquals(404, response.statusCode());
        assertEquals("", response.body());
        assertFalse(response.headers().firstValue("X-Snowflake-Request-ID").isPresent());
        assertFalse(response.headers().firstValue("Content-Type").isPresent());
    }

    @Test
    public void everyRoutedAnswerCarriesARequestId() throws Exception {
        final HttpResponse<String> response = get("/api/v2/warehouses");
        ok(response);
        assertTrue(response.headers().firstValue("X-Snowflake-Request-ID").isPresent());
    }

    @Test
    public void aKnownPathUnderAnotherOfTheApisMethodsIsABare405() throws Exception {
        for (final String[] call : new String[][] {{"PUT", "/api/v2/warehouses"}, {"POST", "/api/v2/warehouses/w"},
            {"DELETE", "/api/v2/warehouses"}}) {
            final HttpResponse<String> response = request(call[0], call[1], null);
            assertEquals(405, response.statusCode(), call[0] + " " + call[1]);
            assertEquals("", response.body());
            assertFalse(response.headers().firstValue("X-Snowflake-Request-ID").isPresent());
            assertFalse(response.headers().firstValue("Content-Type").isPresent());
        }
    }

    @Test
    public void aMethodTheApiNeverUsesIsABare404() throws Exception {
        final HttpResponse<String> response = request("PATCH", "/api/v2/warehouses/w", null);
        assertEquals(404, response.statusCode());
        assertEquals("", response.body());
        assertFalse(response.headers().firstValue("X-Snowflake-Request-ID").isPresent());
    }

    @Test
    public void aBodyInAnotherMediaTypeIsABare415AndNothingRuns() throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v2/warehouses"))
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"api_plain_wh\"}")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(415, response.statusCode());
        assertEquals("", response.body());
        assertFalse(response.headers().firstValue("X-Snowflake-Request-ID").isPresent());
        error(404, get("/api/v2/warehouses/api_plain_wh"));
    }

    @Test
    public void aJsonContentTypeWithACharsetIsAccepted() throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v2/warehouses"))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"api_charset_wh\"}")).build(),
            HttpResponse.BodyHandlers.ofString());
        ok(response);
    }

    @Test
    public void aBodyThatCannotBeReadIsA400WithAnEmptyBody() throws Exception {
        for (final String body : new String[] {"{\"name\": ", "[\"not\", \"an\", \"object\"]",
            "{\"name\": \"api_typed_wh\", \"auto_suspend\": \"abc\"}"}) {
            final HttpResponse<String> response = post("/api/v2/warehouses", body);
            assertEquals(400, response.statusCode(), body);
            assertEquals("", response.body(), body);
            assertTrue(response.headers().firstValue("X-Snowflake-Request-ID").isPresent(), body);
        }
    }

    @Test
    public void aCreateWithoutANameIs400WithTheRequestEntityCode() throws Exception {
        final HttpResponse<String> response = post("/api/v2/warehouses", "{\"comment\": \"x\"}");
        assertEquals("The request entity had the following errors: name cannot be empty string (was '')",
            error(400, response));
        assertEquals("390400", json(response).path("code").asString());
    }

    @Test
    public void aPropertyTheSchemaDoesNotDefineIsIgnored() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\": \"api_extra_wh\", \"no_such_property\": 1}"));
    }

    @Test
    public void aPathSegmentThatIsNoIdentifierIs400() throws Exception {
        final HttpResponse<String> response = get("/api/v2/warehouses/not-an-identifier");
        assertEquals("Invalid SQL Identifier found in the request: not-an-identifier", error(400, response));
        assertEquals("", json(response).path("code").asString());
        assertEquals("", json(response).path("error_code").asString());
    }

    @Test
    public void aMissingObjectIs404WithTheStatementsSentenceInLowerCase() throws Exception {
        final HttpResponse<String> response = get("/api/v2/warehouses/API_No_Such_WH");
        assertEquals("\nwarehouse 'api_no_such_wh' does not exist or not authorized.", error(404, response));
        assertEquals("002003", json(response).path("code").asString());
        assertEquals("002003", json(response).path("error_code").asString());
    }

    /** A statement's missing-object refusal is relayed whole, the privilege hint that ends it included. */
    @Test
    public void aRelayedMissingObjectRefusalKeepsItsPrivilegeHint() throws Exception {
        final String message = error(404, delete("/api/v2/databases/API_No_Such_DB"));
        assertTrue(message.startsWith(
            "\ndatabase 'api_no_such_db' does not exist or not authorized. your primary role "), message);
        assertTrue(message.endsWith(" must have usage or any other privilege granted on database api_no_such_db."),
            message);
    }

    @Test
    public void aCreateOverAnExistingObjectIs409WithTheStatementsSentence() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\":\"api_twice_wh\"}"));
        final HttpResponse<String> again = post("/api/v2/warehouses", "{\"name\":\"api_twice_wh\"}");
        assertEquals("\nobject 'api_twice_wh' already exists.", error(409, again));
        assertEquals("002002", json(again).path("code").asString());
    }

    @Test
    public void aQuotedNameMayHoldAColonAndStillTakeAnAction() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\":\"\\\"api:quoted\\\"\"}"));
        ok(post("/api/v2/warehouses/%22api:quoted%22:suspend", null));
        final JsonNode fetched = ok(get("/api/v2/warehouses/%22api%3Aquoted%22"));
        assertEquals("\"api:quoted\"", fetched.path("name").asString());
        assertEquals("SUSPENDED", fetched.path("state").asString());
    }

    @Test
    public void authorizationHeadersAreAcceptedAndNotChecked() throws Exception {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v2/warehouses"))
            .header("Authorization", "Bearer not-a-real-token")
            .header("X-Snowflake-Authorization-Token-Type", "KEYPAIR_JWT")
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        ok(response);
    }

    @Test
    public void callsCarryingOneTokenShareASessionAndOthersLeaveNoneBehind() throws Exception {
        final int before = server.getEngine().getActiveSessionCount();
        ok(get("/api/v2/warehouses"));
        assertEquals(before, server.getEngine().getActiveSessionCount(), "a call without a token keeps no session");
        for (int i = 0; i < 3; i++) {
            ok(client.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/v2/warehouses"))
                .header("Authorization", "Snowflake Token=\"api-shared-token\"").GET().build(),
                HttpResponse.BodyHandlers.ofString()));
        }
        assertEquals(before + 1, server.getEngine().getActiveSessionCount(), "one session is kept for the token");
    }

    @Test
    public void asyncExecAnswers202AndTheResultHandleServesTheOutcome() throws Exception {
        final HttpResponse<String> accepted = post("/api/v2/warehouses?asyncExec=true", "{\"name\":\"api_async_wh\"}");
        final JsonNode body = expect(202, accepted);
        assertEquals("392604", body.path("code").asString());
        final String handle = body.path("result_handler").asString();
        assertNotNull(handle);
        assertEquals(handle, body.path("job_id").asString() + "0", "the handle is the job id and its result type");
        final String location = accepted.headers().firstValue("Location").orElse(null);
        assertEquals("/api/v2/results/" + handle, location);

        HttpResponse<String> result = get(location);
        int polls = 0;
        while (result.statusCode() == 202 && polls++ < 200) {
            Thread.sleep(25);
            result = get(location);
        }
        logger.debug("result after {} polls: {}", polls, result.body());
        assertEquals("Warehouse API_ASYNC_WH successfully created.", ok(result).path("status").asString());
        ok(get("/api/v2/warehouses/api_async_wh"));
    }

    @Test
    public void aHandleThatIsNoQueryIdIs400() throws Exception {
        assertEquals("Invalid resultHandler: no-such-handle", error(400, get("/api/v2/results/no-such-handle")));
    }

    @Test
    public void aWellFormedHandleNamingNoResultIs404() throws Exception {
        final HttpResponse<String> response = get("/api/v2/results/01bf2a4e-0000-0000-0000-000000000000");
        assertEquals("Specified object does not exist or not authorized.", error(404, response));
        assertEquals("390404", json(response).path("code").asString());
    }

    @Test
    public void anInvalidCreateModeIs400() throws Exception {
        assertEquals("Not a valid createMode: sometimes. Allowed values are: errorIfExists,orReplace,ifNotExists",
            error(400, post("/api/v2/warehouses?createMode=sometimes", "{\"name\":\"api_mode_wh\"}")));
    }

    @Test
    public void aFlagOtherThanTrueReadsAsFalse() throws Exception {
        error(404, delete("/api/v2/warehouses/api_flag_wh?ifExists=maybe"));
        ok(delete("/api/v2/warehouses/api_flag_wh?ifExists=TRUE"));
    }
}
