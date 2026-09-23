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
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.functions.scalar.file.BuildScopedFileUrl;
import dev.frostlake.functions.scalar.file.BuildStageFileUrl;
import dev.frostlake.functions.scalar.file.PresignedUrls;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Serves the file URLs BUILD_STAGE_FILE_URL and BUILD_SCOPED_FILE_URL hand out, under {@code /api/files/}:
 *
 * <ul>
 *   <li>{@code /api/files/<database>/<schema>/<stage>/<path>} — a stage file URL, which does not expire: the names
 *       are read back from their percent-encoded SQL spelling and the stage is looked up when the URL is fetched,
 *       so a dropped stage's URL stops working;</li>
 *   <li>{@code /api/files/<query id>/<account number>/<token>} — a scoped URL, whose token is signed the way a
 *       presigned URL's is (see {@link PresignedUrls}) and expires 24 hours after the call that made it.</li>
 * </ul>
 *
 * <p>The account asks for a session on both and redirects to its cloud storage; this server is unauthenticated by
 * design and streams the file itself. Failures answer as the presigned URLs do, with an XML error:
 * {@code 404 NoSuchKey} for a file or stage that is not there, {@code 403 AccessDenied} for an expired scoped URL and
 * {@code 403 SignatureDoesNotMatch} for a token this process did not sign.
 */
public class StageFileUrlHandler implements HttpHandler {

    private final ConcurrentDatabaseEngine engine;

    /**
     * @param engine the engine whose stages the stage file URLs name
     */
    public StageFileUrlHandler(final ConcurrentDatabaseEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try {
            final String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                error(exchange, 405, "MethodNotAllowed", "The specified method is not allowed against this resource.");
                return;
            }
            final String rawPath = exchange.getRequestURI().getRawPath();
            final String tail = rawPath.length() > BuildStageFileUrl.CONTEXT.length()
                ? rawPath.substring(BuildStageFileUrl.CONTEXT.length()) : "";
            final String[] segments = tail.split("/", -1);
            final Path file;
            if (segments.length == 3) {
                final String token = BuildScopedFileUrl.tokenOf(segments[2]);
                final long[] expiry = new long[1];
                final Path signed = token == null ? null : PresignedUrls.verify(token, expiry);
                if (signed == null) {
                    error(exchange, 403, "SignatureDoesNotMatch",
                        "The request signature we calculated does not match the signature you provided.");
                    return;
                }
                if (Instant.now().getEpochSecond() > expiry[0]) {
                    error(exchange, 403, "AccessDenied", "Request has expired");
                    return;
                }
                // The token signs the path the file's name spells; a file that later names continued now sits
                // inside the directory of that name.
                file = StagePathSegments.ownFileOf(signed);
            } else if (segments.length == 4) {
                file = engine.stageFileUrlTarget(name(segments[0]), name(segments[1]), name(segments[2]),
                    URLDecoder.decode(segments[3], StandardCharsets.UTF_8));
            } else {
                file = null;
            }
            if (file == null || !Files.isRegularFile(file)) {
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

    /**
     * One name segment back as the canonical name it spells: percent-decoded, and a quoted spelling unquoted with its
     * doubled quotes halved; a bare one is canonical already.
     */
    private static String name(final String segment) {
        final String spelled = URLDecoder.decode(segment, StandardCharsets.UTF_8);
        if (spelled.length() >= 2 && spelled.startsWith("\"") && spelled.endsWith("\"")) {
            return spelled.substring(1, spelled.length() - 1).replace("\"\"", "\"");
        }
        return spelled;
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
