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

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers a request whose handler failed without answering it. A fault that escapes a handler, whether a
 * RuntimeException it did not expect or an Error such as a class that cannot be loaded, would otherwise
 * end the server's pool thread with the exchange still open. The client would then wait out its own
 * timeout for an answer that never comes. Here it gets a 500 at once or, when the handler had already
 * begun its response, the end of the exchange.
 */
public final class ErrorAnsweringHandler implements HttpHandler {

    /** The 500's body: a fixed text, so answering needs nothing that could itself fail to load. */
    static final String FAULT_BODY = "{\"success\":false,\"errorMessage\":\"Internal server error\"}";

    private static final Logger logger = LoggerFactory.getLogger(ErrorAnsweringHandler.class);

    private final HttpHandler delegate;

    public ErrorAnsweringHandler(final HttpHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try {
            delegate.handle(exchange);
        } catch (final RuntimeException | Error fault) {
            logger.error("Unanswered fault handling {} {}", exchange.getRequestMethod(), exchange.getRequestURI(),
                fault);
            if (exchange.getResponseCode() == -1) {
                HttpResponses.send(exchange, 500, FAULT_BODY);
            } else {
                exchange.close();
            }
        }
    }
}
