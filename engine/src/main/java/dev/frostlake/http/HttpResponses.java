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

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Reading a request body and writing a JSON response — shared by every handler. */
final class HttpResponses {

    /** Static helpers only — never instantiated. */
    private HttpResponses() {
    }

    static String readBody(final HttpExchange exchange) throws IOException {
        final InputStream is = exchange.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    static void send(final HttpExchange exchange, final int statusCode, final String response)
            throws IOException {
        final byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        final OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    /** The "wrong verb" response every handler answers for a method it does not serve. */
    static void methodNotAllowed(final HttpExchange exchange) throws IOException {
        send(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }
}
