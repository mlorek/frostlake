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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;

/**
 * Spark Connect ({@code spark-connect.yaml}, {@code /api/v2/spark-connect/…}): the ten calls of the Spark
 * Connect protocol carried over REST. Frostlake reads no Spark plans, so every endpoint answers {@code 501}.
 * The routes are registered with the media types the specification gives their bodies, so a Spark client's
 * request reaches the answer rather than a {@code 415}.
 */
public final class SparkConnectResource implements RestResource {

    private static final String BASE = "/api/v2/spark-connect/";
    private static final String OCTET_STREAM = "application/octet-stream";

    /** The endpoints: path segment, operation id and body media type. */
    private static final String[][] ENDPOINTS = {
        {"execute-plan", "executePlan", OCTET_STREAM},
        {"analyze-plan", "analyzePlan", OCTET_STREAM},
        {"config", "config", OCTET_STREAM},
        {"add-artifacts", "addArtifacts", OCTET_STREAM},
        {"artifact-status", "artifactStatus", OCTET_STREAM},
        {"interrupt", "interrupt", OCTET_STREAM},
        {"reattach-execute", "reattachExecute", OCTET_STREAM},
        {"release-execute", "releaseExecute", OCTET_STREAM},
        {"pull-request", "pullRequest", RestResponse.JSON},
        {"push-response", "pushResponse", RestResponse.JSON},
    };

    @Override
    public void register(final RestRouter router) {
        for (final String[] endpoint : ENDPOINTS) {
            router.add("POST", BASE + endpoint[0], endpoint[1], this, endpoint[2]);
        }
    }

    @Override
    public RestResponse handle(final RestCall call) {
        throw RestException.notImplemented("Spark Connect is not supported: Frostlake does not execute Spark "
            + "plans (operation " + call.operation() + ").");
    }
}
