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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * The {@code /api/v2} surface: Snowflake's REST APIs for resource management, served as translations into the
 * SQL the engine answers (see {@code docs/rest-api.md}).
 *
 * <p>Every request is answered with an {@code X-Snowflake-Request-ID}, except one no route serves, which the
 * account's edge answers bare — no body, no request id: an unknown path is a {@code 404}; a known path under one of
 * the API's methods it is not served with (a {@code PUT} on a view, a {@code POST} on a warehouse) is a {@code 405};
 * a method the API never uses ({@code PATCH}) is a {@code 404} again. A body in a media type other than JSON is a
 * {@code 415} of the same bare kind. A request whose body or parameters cannot be read is a {@code 400} with an
 * empty body, and every other error carries the {@code ErrorResponse} body. The {@code Authorization} and {@code X-Snowflake-Authorization-Token-Type} headers
 * are accepted and not checked — the server is unauthenticated by design ({@code docs/scope.md}).
 *
 * <p>A call runs in the session {@code X-Sfc-Session} names when the server holds one. Otherwise a call carrying
 * an {@code Authorization} header runs in the session kept for that header's value — the account keeps one session
 * per token, so what a call changes in it (the warehouse {@code :use} selects, the one a create makes current)
 * carries over to the caller's next call. A call with neither runs in a session of its own, released when it
 * completes.
 */
public final class RestApiHandler implements HttpHandler {

    /** The context path the handler serves. */
    public static final String CONTEXT = "/api/v2";

    private static final Logger logger = LoggerFactory.getLogger(RestApiHandler.class);
    /** The methods the API's endpoints are served with; another one on a known path is not a 405 but a 404. */
    private static final List<String> API_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE");

    private final RestContext context;
    private final RestRouter router;
    /** The session kept for each Authorization value, by session id. */
    private final Map<String, String> tokenSessions = new HashMap<>();

    /**
     * @param engine the engine the statements run on
     * @param results the asynchronous result store
     */
    public RestApiHandler(final ConcurrentDatabaseEngine engine, final RestResults results) {
        this.context = new RestContext(engine, results);
        this.router = RestApi.router();
    }

    /** The route table, for inspection. */
    public RestRouter router() {
        return router;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        final String requestId = UUID.randomUUID().toString();
        RestResponse response;
        try {
            response = dispatch(exchange, requestId);
        } catch (final RestException refusal) {
            response = RestResponse.error(refusal);
        } catch (final RuntimeException | Error fault) {
            logger.error("REST request failed: {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), fault);
            response = RestResponse.error(500, fault.getMessage() != null ? fault.getMessage() : fault.toString(),
                null);
        }
        write(exchange, response, requestId);
    }

    private RestResponse dispatch(final HttpExchange exchange, final String requestId) throws IOException {
        final String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        final RestPath path = RestPath.ofRequest(exchange.getRequestURI().getRawPath());
        final RestRouteMatch match = path == null ? null : router.match(method, path);
        if (match == null) {
            drain(exchange);
            return RestResponse.unrouted(404);
        }
        if (match.route() == null) {
            drain(exchange);
            return RestResponse.unrouted(API_METHODS.contains(method) ? 405 : 404);
        }
        final RestRoute route = match.route();
        final String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && route.bodyType() != null && !sameMediaType(contentType, route.bodyType())) {
            drain(exchange);
            return RestResponse.unrouted(415);
        }
        final byte[] body = readBody(exchange);
        final Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        final Map<String, String> headers = headers(exchange.getRequestHeaders());

        if (route.resource() instanceof RestResultResource) {
            return route.resource().handle(new RestCall(route.operationId(), method, match.parameters(), query,
                headers, body, requestId, null, context));
        }

        final ConcurrentDatabaseEngine engine = context.engine();
        final SessionContext existing = keptSession(headers);
        final SessionContext session = existing != null ? existing : engine.createSession();
        final boolean owned = existing == null;
        final RestCall call = new RestCall(route.operationId(), method, match.parameters(), query, headers, body,
            requestId, new RestSql(engine, session), context);
        final boolean async;
        try {
            async = call.flag("asyncExec", false);
        } catch (final RestException refusal) {
            if (owned) {
                engine.removeSession(session.getSessionId());
            }
            throw refusal;
        }
        return context.results().submit(new Callable<RestResponse>() {
            @Override
            public RestResponse call() {
                try {
                    return route.resource().handle(call);
                } catch (final RestException refusal) {
                    return RestResponse.error(refusal);
                } finally {
                    if (owned) {
                        engine.removeSession(session.getSessionId());
                    }
                }
            }
        }, async);
    }

    /**
     * The session a call runs in when it is not one of its own: the one {@code X-Sfc-Session} names, else the one
     * kept for the call's {@code Authorization} value (started on the value's first call, and again once it has
     * expired); null for a call with neither.
     */
    private SessionContext keptSession(final Map<String, String> headers) {
        final ConcurrentDatabaseEngine engine = context.engine();
        final String named = headers.get("x-sfc-session");
        if (named != null && !named.isEmpty()) {
            final SessionContext existing = engine.getSession(named);
            if (existing != null) {
                return existing;
            }
        }
        final String token = headers.get("authorization");
        if (token == null || token.isBlank()) {
            return null;
        }
        synchronized (tokenSessions) {
            final String id = tokenSessions.get(token);
            SessionContext session = id == null ? null : engine.getSession(id);
            if (session == null) {
                session = engine.createSession();
                tokenSessions.put(token, session.getSessionId());
            }
            return session;
        }
    }

    private static boolean sameMediaType(final String header, final String expected) {
        final int semicolon = header.indexOf(';');
        final String type = (semicolon < 0 ? header : header.substring(0, semicolon)).trim();
        return type.equalsIgnoreCase(expected);
    }

    private static byte[] readBody(final HttpExchange exchange) throws IOException {
        final InputStream in = exchange.getRequestBody();
        return in.readAllBytes();
    }

    private static void drain(final HttpExchange exchange) throws IOException {
        readBody(exchange);
    }

    private static Map<String, String> parseQuery(final String rawQuery) {
        final Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }
        for (final String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            final int equals = pair.indexOf('=');
            final String key = decodeQuery(equals < 0 ? pair : pair.substring(0, equals));
            final String value = equals < 0 ? "" : decodeQuery(pair.substring(equals + 1));
            if (!query.containsKey(key)) {
                query.put(key, value);
            }
        }
        return query;
    }

    private static String decodeQuery(final String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException malformed) {
            throw RestException.badRequest("Malformed query string near '" + text + "'.");
        }
    }

    private static Map<String, String> headers(final Headers headers) {
        final Map<String, String> out = new HashMap<>();
        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                out.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().get(0));
            }
        }
        return out;
    }

    private static void write(final HttpExchange exchange, final RestResponse response, final String requestId)
            throws IOException {
        final Headers out = exchange.getResponseHeaders();
        if (response.getContentType() != null) {
            out.set("Content-Type", response.getContentType());
        }
        if (response.carriesRequestId()) {
            out.set("X-Snowflake-Request-ID", requestId);
        }
        for (final Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            out.set(header.getKey(), header.getValue());
        }
        final byte[] body = response.bodyFor(requestId);
        final boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        if (body == null || body.length == 0 || head) {
            exchange.sendResponseHeaders(response.getStatus(), -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(response.getStatus(), body.length);
        final OutputStream os = exchange.getResponseBody();
        os.write(body);
        os.close();
    }
}
