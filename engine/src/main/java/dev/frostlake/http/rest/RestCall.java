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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * One call of an endpoint, as the resource serving it sees it: the operation, the path and query parameters,
 * the body, and the SQL session the call's statements run in.
 */
public final class RestCall {

    private final String operationId;
    private final String method;
    private final Map<String, String> pathParameters;
    private final Map<String, String> queryParameters;
    private final Map<String, String> headers;
    private final byte[] body;
    private final String requestId;
    private final RestSql sql;
    private final RestContext context;
    private JsonNode parsedBody;
    private boolean bodyParsed;

    /**
     * @param operationId the specification's operation id of the endpoint
     * @param method the HTTP method
     * @param pathParameters the path parameters, decoded
     * @param queryParameters the query parameters, decoded (the first value of a repeated one)
     * @param headers the request headers, keyed in lower case
     * @param body the raw body, empty when there is none
     * @param requestId the id this request is answered under
     * @param sql the SQL session of the call
     * @param context what the REST surface shares across calls
     */
    public RestCall(final String operationId, final String method, final Map<String, String> pathParameters,
                    final Map<String, String> queryParameters, final Map<String, String> headers, final byte[] body,
                    final String requestId, final RestSql sql, final RestContext context) {
        this.operationId = operationId;
        this.method = method;
        this.pathParameters = pathParameters == null ? Collections.<String, String>emptyMap() : pathParameters;
        this.queryParameters = queryParameters == null ? Collections.<String, String>emptyMap() : queryParameters;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.body = body == null ? new byte[0] : body;
        this.requestId = requestId;
        this.sql = sql;
        this.context = context;
    }

    /** The specification's operation id of the endpoint called. */
    public String operation() {
        return operationId;
    }

    /** The HTTP method. */
    public String method() {
        return method;
    }

    /** The id the request is answered under. */
    public String requestId() {
        return requestId;
    }

    /** The SQL session of the call. */
    public RestSql sql() {
        return sql;
    }

    /** What the REST surface shares across calls. */
    public RestContext context() {
        return context;
    }

    /** A request header, by case-insensitive name, or null. */
    public String header(final String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    // ---- path ---------------------------------------------------------------------------------------------

    /** A path parameter as written (decoded), or null when the template has none of that name. */
    public String pathParameter(final String name) {
        return pathParameters.get(name);
    }

    /**
     * A path parameter read as an object name.
     *
     * @throws RestException {@code 400} when it is not an identifier
     */
    public RestIdentifier identifier(final String name) {
        return RestIdentifier.parse(pathParameters.get(name), name);
    }

    /** The {@code {database}} path parameter as a quoted SQL identifier. */
    public String databaseSql() {
        return identifier("database").sql();
    }

    /** The {@code {database}}/{@code {schema}} path parameters as a qualified schema name. */
    public String schemaSql() {
        return identifier("database").sql() + "." + identifier("schema").sql();
    }

    /** A schema-level object's qualified name: {@code {database}.{schema}.<name>}. */
    public String qualifiedSql(final RestIdentifier name) {
        return schemaSql() + "." + name.sql();
    }

    // ---- query --------------------------------------------------------------------------------------------

    /** A query parameter, or null. */
    public String query(final String name) {
        return queryParameters.get(name);
    }

    /**
     * A boolean query parameter: {@code true} in any case is true, and any other value false — the account reads
     * {@code ifExists=maybe} as false rather than refusing it.
     *
     * @param fallback the value when the parameter is absent
     */
    public boolean flag(final String name, final boolean fallback) {
        final String value = queryParameters.get(name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    /**
     * An integer query parameter, or null when it is absent.
     *
     * @throws RestException a bodiless {@code 400} when it is not an integer
     */
    public Long integer(final String name) {
        final String value = queryParameters.get(name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (final NumberFormatException notNumeric) {
            throw RestException.unreadable("query parameter '" + name + "' is not an integer: " + value);
        }
    }

    /** The {@code createMode} query parameter. */
    public RestCreateMode createMode() {
        return RestCreateMode.of(queryParameters.get("createMode"));
    }

    /** {@code " IF EXISTS"} when {@code ifExists=true}, else nothing. */
    public String ifExists() {
        return flag("ifExists", false) ? " IF EXISTS" : "";
    }

    // ---- body ---------------------------------------------------------------------------------------------

    /** Whether the request carries a body. */
    public boolean hasBody() {
        return body.length > 0 && !new String(body, StandardCharsets.UTF_8).isBlank();
    }

    /** The raw body. */
    public byte[] rawBody() {
        return body;
    }

    /**
     * The body parsed as JSON, or null when there is none.
     *
     * @throws RestException {@code 400} when it is not JSON
     */
    public JsonNode json() {
        if (!bodyParsed) {
            bodyParsed = true;
            if (hasBody()) {
                try {
                    parsedBody = RestJson.mapper().readTree(new String(body, StandardCharsets.UTF_8));
                } catch (final JacksonException malformed) {
                    throw RestException.unreadable("malformed JSON request body: " + malformed.getOriginalMessage());
                }
            }
        }
        return parsedBody;
    }

    /**
     * The body as a JSON object.
     *
     * @throws RestException {@code 400} when there is no body or it is not an object
     */
    public JsonNode body() {
        final JsonNode node = json();
        if (node == null || !node.isObject()) {
            throw RestException.unreadable("the request body is not a JSON object");
        }
        return node;
    }

    /**
     * For a {@code PUT}: the body's {@code name}, when it has one, must name the object the path names; another
     * name is the account's create-or-alter conflict.
     *
     * @throws RestException {@code 409} code {@code 001520} for a body naming another object
     */
    public void requireBodyNames(final RestIdentifier name) {
        final RestIdentifier named = RestJson.identifier(body(), "name", false);
        if (named != null && !named.equals(name)) {
            throw RestException.createOrAlterRace();
        }
    }

    /** The body as a JSON object, or an empty object when there is none. */
    public JsonNode bodyOrEmpty() {
        final JsonNode node = json();
        if (node == null || node.isNull()) {
            return RestJson.object();
        }
        if (!node.isObject()) {
            throw RestException.unreadable("the request body is not a JSON object");
        }
        return node;
    }

    /**
     * The body as a JSON array.
     *
     * @throws RestException {@code 400} when there is no body or it is not an array
     */
    public JsonNode bodyArray() {
        final JsonNode node = json();
        if (node == null || !node.isArray()) {
            throw RestException.unreadable("the request body is not a JSON array");
        }
        return node;
    }
}
