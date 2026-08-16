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

package dev.frostlake.testkit;

import dev.frostlake.http.DatabaseHttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs statements through the engine's HTTP endpoint ({@code POST /api/execute}) — the transport a
 * remote client or driver uses. Starts a {@link DatabaseHttpServer} in-process on a free port, or
 * attaches to a running one when given its URL, and carries the returned {@code sessionId} across
 * statements. The endpoint reports a refusal as a message only, so {@code ERROR_CODE} checks are
 * capability-skipped; the DML count is the result set's {@code updateCount}, or the count grid's first
 * cell.
 */
public final class HttpBackend implements Backend {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final DatabaseHttpServer server;
    private String sessionId;

    /**
     * @param url a running server's base URL, or null to start one in-process
     * @throws IOException when no server can be started
     */
    public HttpBackend(final String url) throws IOException {
        if (url != null && !url.isBlank()) {
            this.baseUrl = url.replaceAll("/+$", "");
            this.server = null;
            return;
        }
        final int port;
        try (final ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        this.server = new DatabaseHttpServer(port);
        this.server.start();
        this.baseUrl = "http://localhost:" + port;
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.UPDATE_COUNT, Capability.COLUMN_NAMES, Capability.SESSION);
    }

    @Override
    public ExecResult execute(final String sql) throws IOException, InterruptedException {
        final StringBuilder body = new StringBuilder("{\"sql\": ").append(Json.escape(sql));
        if (sessionId != null) {
            body.append(", \"sessionId\": ").append(Json.escape(sessionId));
        }
        body.append(", \"autoCommit\": true}");
        final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/execute"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        final Map<String, Object> reply = Json.obj(Json.parse(response.body()));
        if (reply == null) {
            throw new IllegalStateException("unparseable /api/execute response: HTTP " + response.statusCode());
        }
        final String session = Json.getStr(reply, "sessionId");
        if (session != null) {
            sessionId = session;
        }
        final ExecResult out = new ExecResult();
        if (!Boolean.TRUE.equals(reply.get("success"))) {
            final String message = Json.getStr(reply, "errorMessage");
            out.setErrorMessage(message == null ? "(no message; HTTP " + response.statusCode() + ")" : message);
            return out;
        }
        final List<Object> sets = Json.getArr(reply, "resultSets");
        if (sets != null && !sets.isEmpty()) {
            readSet(Json.obj(sets.get(0)), out);
        }
        out.deriveUpdateCountFromGrid();
        return out;
    }

    private static void readSet(final Map<String, Object> set, final ExecResult out) {
        final List<Object> columns = Json.getArr(set, "columns");
        final List<Boolean> semiStructured = new ArrayList<>();
        if (columns != null) {
            final List<String> names = new ArrayList<>();
            for (final Object column : columns) {
                names.add(Json.getStr(Json.obj(column), "name"));
                semiStructured.add(Boolean.valueOf(
                    SemiStructuredCells.isSemiStructured(Json.getStr(Json.obj(column), "dataType"))));
            }
            out.setColumns(names);
        }
        final List<Object> rows = Json.getArr(set, "rows");
        if (rows != null) {
            final List<List<String>> grid = new ArrayList<>();
            for (final Object row : rows) {
                final List<String> cells = new ArrayList<>();
                int column = 0;
                for (final Object value : Json.arr(row)) {
                    final String cell = stringify(value);
                    final boolean semi = column < semiStructured.size()
                        && semiStructured.get(column).booleanValue();
                    cells.add(semi ? SemiStructuredCells.value(cell) : cell);
                    column++;
                }
                grid.add(cells);
            }
            out.setRows(grid);
        }
        final Integer updateCount = Json.getInt(set, "updateCount");
        if (updateCount != null && updateCount >= 0) {
            out.setUpdateCount(updateCount);
        }
    }

    private static String stringify(final Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }

    @Override
    public void resetContext() throws IOException, InterruptedException {
        for (final String sql : RESET_CONTEXT) {
            final ExecResult result = execute(sql);
            if (result.failed()) {
                throw new IllegalStateException("resetContext failed on '" + sql + "': " + result.getErrorMessage());
            }
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop();
        }
    }
}
