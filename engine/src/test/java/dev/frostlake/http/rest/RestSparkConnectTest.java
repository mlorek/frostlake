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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Spark Connect endpoints ({@code spark-connect.yaml}): all ten are routed and answer 501. */
public class RestSparkConnectTest extends BaseRestTest {

    private HttpResponse<String> binary(final String endpoint) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v2/spark-connect/" + endpoint))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[] {0, 1, 2})).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void everyPlanEndpointIsRoutedAndNotImplemented() throws Exception {
        final String[] endpoints = {"execute-plan", "analyze-plan", "config", "add-artifacts", "artifact-status",
            "interrupt", "reattach-execute", "release-execute"};
        for (final String endpoint : endpoints) {
            assertTrue(error(501, binary(endpoint)).contains("Spark Connect is not supported"), endpoint);
        }
    }

    @Test
    public void theJsonEndpointsAreRoutedToo() throws Exception {
        error(501, post("/api/v2/spark-connect/pull-request", "\"r\""));
        error(501, post("/api/v2/spark-connect/push-response", "\"r\""));
    }

    @Test
    public void aGetOnAPostEndpointIsTheBare405() throws Exception {
        assertEquals(405, get("/api/v2/spark-connect/execute-plan").statusCode());
    }
}
