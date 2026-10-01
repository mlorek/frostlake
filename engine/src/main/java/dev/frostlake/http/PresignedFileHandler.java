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
import com.sun.net.httpserver.HttpHandler;
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.functions.scalar.file.PresignedUrls;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Serves the presigned URLs GET_PRESIGNED_URL hands out: {@code GET /presigned/<token>[/<file name>]} streams
 * the staged file the token names until the token expires, without a session. Failures answer as an object store
 * does, with an XML error: {@code 403 AccessDenied} for an expired URL, {@code 403 SignatureDoesNotMatch} for a
 * token this process did not sign, and {@code 404 NoSuchKey} for a file that is not staged.
 */
public class PresignedFileHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try {
            final String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                error(exchange, 405, "MethodNotAllowed", "The specified method is not allowed against this resource.");
                return;
            }
            String token = exchange.getRequestURI().getRawPath().substring(PresignedUrls.CONTEXT.length());
            final int slash = token.indexOf('/');
            if (slash >= 0) {
                token = token.substring(0, slash);
            }
            final long[] expiry = new long[1];
            final Path signed = PresignedUrls.verify(token, expiry);
            // The token signs the path the file's name spells; a file that later names continued now sits inside
            // the directory of that name.
            final Path file = StagePathSegments.ownFileOf(signed);
            if (file == null) {
                error(exchange, 403, "SignatureDoesNotMatch",
                    "The request signature we calculated does not match the signature you provided.");
                return;
            }
            if (Instant.now().getEpochSecond() > expiry[0]) {
                error(exchange, 403, "AccessDenied", "Request has expired");
                return;
            }
            if (!Files.isRegularFile(file)) {
                error(exchange, 404, "NoSuchKey", "The specified key does not exist.");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(Files.size(file)));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, Files.size(file));
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(file, out);
            }
        } finally {
            exchange.close();
        }
    }

    private static void error(final HttpExchange exchange, final int status, final String code,
                              final String message) throws IOException {
        final byte[] body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Error><Code>" + code + "</Code><Message>"
            + message + "</Message></Error>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/xml");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
