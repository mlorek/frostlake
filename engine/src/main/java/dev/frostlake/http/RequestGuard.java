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
package dev.frostlake.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What every request is held to before a handler sees it. The server takes no credentials, and a page open in a
 * browser on its machine reaches whatever that machine reaches, so the three things only a browser's page gets
 * wrong are refused:
 *
 * <ul>
 *   <li><b>Host</b> — a server bound to the loopback interface answers to the names it has there:
 *       {@code localhost}, {@code 127.0.0.1}, {@code [::1]} and the configured {@code http.host}. Another name is
 *       one pointed at the machine from outside, and is answered {@code 421}. A server bound to any other address
 *       is reached by names it cannot know, and checks none.</li>
 *   <li><b>Origin</b> — a request a page sends names the page's origin. It is served only for an origin listed
 *       in {@code http.cors.allowedOrigins}, none by default, and answered {@code 403} otherwise. A request
 *       without the header is no page's: a driver, a command line.</li>
 *   <li><b>Content-Type</b> — a body sent to a SQL endpoint has to be declared {@code application/json}, else
 *       {@code 415}: a form and a plain text are the bodies a page of another site may send unasked.</li>
 * </ul>
 *
 * <p>For a listed origin every answer carries {@code Access-Control-Allow-Origin} with that origin — never a
 * wildcard — and {@code Vary: Origin}, and a preflight {@code OPTIONS} is answered {@code 204} with the methods
 * and headers the API takes.
 */
final class RequestGuard {

    private static final String JSON = "application/json";

    /** How long a browser may keep a preflight's answer, in seconds. */
    private static final String PREFLIGHT_MAX_AGE = "600";

    private final boolean loopbackBound;
    private final Set<String> hostNames = new HashSet<String>();
    private final Set<String> allowedOrigins = new HashSet<String>();

    /**
     * @param bound the address the server listens on
     * @param configuredHost the {@code http.host} the server was given
     * @param origins the origins {@code http.cors.allowedOrigins} lists
     */
    RequestGuard(final InetAddress bound, final String configuredHost, final List<String> origins) {
        this.loopbackBound = bound != null && bound.isLoopbackAddress();
        hostNames.add("localhost");
        hostNames.add("127.0.0.1");
        hostNames.add("[::1]");
        if (configuredHost != null && !configuredHost.trim().isEmpty()) {
            hostNames.add(hostName(configuredHost.trim()));
        }
        for (final String origin : origins) {
            allowedOrigins.add(normalOrigin(origin));
        }
    }

    /**
     * Judges a request, answering the ones that end here: a refusal, or a preflight.
     *
     * @param exchange the request
     * @param jsonBody whether the endpoint takes a JSON body and no other
     * @return true when the request is answered and the handler must not run
     * @throws IOException when the answer cannot be written
     */
    boolean answered(final HttpExchange exchange, final boolean jsonBody) throws IOException {
        final Headers headers = exchange.getRequestHeaders();
        final String host = headers.getFirst("Host");
        if (loopbackBound && host != null && !hostNames.contains(hostName(host))) {
            return refuse(exchange, 421, "This server does not answer to the host '" + host + "'.");
        }
        final String origin = headers.getFirst("Origin");
        if (origin != null) {
            if (!allowedOrigins.contains(normalOrigin(origin))) {
                return refuse(exchange, 403, "Requests from the origin '" + origin
                    + "' are not allowed; see http.cors.allowedOrigins.");
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin.trim());
            exchange.getResponseHeaders().add("Vary", "Origin");
            if ("OPTIONS".equals(exchange.getRequestMethod())
                    && headers.getFirst("Access-Control-Request-Method") != null) {
                final String asked = headers.getFirst("Access-Control-Request-Headers");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                    asked == null || asked.trim().isEmpty() ? "Content-Type" : asked.trim());
                exchange.getResponseHeaders().set("Access-Control-Max-Age", PREFLIGHT_MAX_AGE);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return true;
            }
        }
        if (jsonBody && hasBody(exchange) && !isJson(headers.getFirst("Content-Type"))) {
            return refuse(exchange, 415, "The API takes a body declared as application/json.");
        }
        return false;
    }

    private static boolean refuse(final HttpExchange exchange, final int status, final String reason)
            throws IOException {
        HttpResponses.send(exchange, status, "{\"error\":\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"")
            + "\"}");
        return true;
    }

    /** Whether the request carries a body: one of a declared length above zero, or a chunked one. */
    private static boolean hasBody(final HttpExchange exchange) {
        final Headers headers = exchange.getRequestHeaders();
        if (headers.getFirst("Transfer-Encoding") != null) {
            return true;
        }
        final String length = headers.getFirst("Content-Length");
        if (length == null) {
            return false;
        }
        try {
            return Long.parseLong(length.trim()) > 0L;
        } catch (final NumberFormatException unreadable) {
            return true;
        }
    }

    /** Whether a Content-Type names JSON, whatever parameters follow the media type. */
    private static boolean isJson(final String contentType) {
        if (contentType == null) {
            return false;
        }
        final int parameters = contentType.indexOf(';');
        final String mediaType = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return JSON.equalsIgnoreCase(mediaType.trim());
    }

    /** A Host header's name without its port, lower-cased; an IPv6 literal keeps its brackets. */
    private static String hostName(final String host) {
        final String lower = host.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("[")) {
            final int close = lower.indexOf(']');
            return close < 0 ? lower : lower.substring(0, close + 1);
        }
        final int port = lower.lastIndexOf(':');
        return port < 0 ? lower : lower.substring(0, port);
    }

    /** An origin as it is compared: lower-cased, without a slash after it. */
    private static String normalOrigin(final String origin) {
        final String lower = origin.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith("/") ? lower.substring(0, lower.length() - 1) : lower;
    }
}
