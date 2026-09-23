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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One answer of the {@code /api/v2} surface: a status, a body and the headers that go with it. The request
 * id is added by the router when the answer is written, into the {@code X-Snowflake-Request-ID} header and,
 * for an error, into the body's {@code request_id}.
 */
public final class RestResponse {

    /** The media type every JSON answer declares. */
    public static final String JSON = "application/json";

    /** The {@code status} an action reports when its statement answers no sentence of its own. */
    public static final String DEFAULT_STATUS = "Request successfully completed";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int status;
    private final byte[] body;
    private final String contentType;
    private final ObjectNode error;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final boolean requestIdHeader;

    private RestResponse(final int status, final byte[] body, final String contentType, final ObjectNode error,
                         final boolean requestIdHeader) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
        this.error = error;
        this.requestIdHeader = requestIdHeader;
    }

    /** A JSON answer: an object, or an array for a listing. */
    public static RestResponse json(final int status, final JsonNode node) {
        return new RestResponse(status, MAPPER.writeValueAsString(node).getBytes(StandardCharsets.UTF_8), JSON,
            null, true);
    }

    /** {@code 200} with the {@code SuccessResponse} body, {@code {"status": ...}}. */
    public static RestResponse success(final String statusText) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("status", statusText == null || statusText.isEmpty() ? DEFAULT_STATUS : statusText);
        return json(200, node);
    }

    /**
     * An error in the {@code ErrorResponse} shape. {@code code} and {@code error_code} carry the same Snowflake
     * error code (the second is the specification's deprecated spelling of the first), and are empty strings when
     * the failure has none — the account sends both keys on every error.
     */
    public static RestResponse error(final int status, final String message, final String code) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("code", code == null ? "" : code);
        node.put("error_code", code == null ? "" : code);
        node.put("message", message == null ? "" : message);
        return new RestResponse(status, null, JSON, node, true);
    }

    /** The error response for a refusal raised while serving a request. */
    public static RestResponse error(final RestException refusal) {
        if (refusal.isBodiless()) {
            return new RestResponse(refusal.getStatus(), new byte[0], JSON, null, true);
        }
        return error(refusal.getStatus(), refusal.getMessage(), refusal.getCode());
    }

    /**
     * A bodiless answer with no request id and no content type: what the account's edge answers for a request no
     * route serves — an unknown path, or a known path under a method it is not served with — a {@code 404} with an
     * empty body and no {@code X-Snowflake-Request-ID}.
     */
    public static RestResponse unrouted(final int status) {
        return new RestResponse(status, new byte[0], null, null, false);
    }

    /** A raw answer in a media type of its own, such as a server-sent event stream. */
    public static RestResponse raw(final int status, final String contentType, final byte[] body) {
        return new RestResponse(status, body, contentType, null, true);
    }

    /** Adds a header to the answer. */
    public RestResponse withHeader(final String name, final String value) {
        headers.put(name, value);
        return this;
    }

    /** The HTTP status. */
    public int getStatus() {
        return status;
    }

    /** The media type of the body. */
    public String getContentType() {
        return contentType;
    }

    /** The headers this answer adds, beyond the content type and the request id. */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** Whether the answer carries the request id header. */
    public boolean carriesRequestId() {
        return requestIdHeader;
    }

    /** Whether this is an error in the {@code ErrorResponse} shape. */
    public boolean isError() {
        return error != null;
    }

    /** The error body's message, or null when this is not an error. */
    public String getErrorMessage() {
        return error == null ? null : error.path("message").asString(null);
    }

    /**
     * The body bytes as sent: an error's body is completed with the request id it answers.
     *
     * @param requestId the id of the request this answers
     */
    public byte[] bodyFor(final String requestId) {
        if (error == null) {
            return body;
        }
        final ObjectNode filled = error.deepCopy();
        filled.put("request_id", requestId);
        return MAPPER.writeValueAsString(filled).getBytes(StandardCharsets.UTF_8);
    }
}
