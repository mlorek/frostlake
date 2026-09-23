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

import dev.frostlake.ExecutionResult;
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file URLs BUILD_STAGE_FILE_URL and BUILD_SCOPED_FILE_URL hand out, fetched from the engine's own HTTP server on
 * a free port: the staged bytes for a file that is there, the object store's XML errors otherwise.
 */
public class StageFileUrlHandlerTest {

    private static DatabaseHttpServer server;
    private static String baseUrl;
    private static SessionContext session;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        server = new DatabaseHttpServer(config);
        server.start();
        baseUrl = "http://localhost:" + port;
        session = server.getEngine().createSession();
        sql("CREATE DATABASE sfu_db");
        sql("CREATE SCHEMA sfu_db.\"my sch\"");
        sql("USE SCHEMA sfu_db.\"my sch\"");
        sql("CREATE STAGE st");
        sql("CREATE STAGE \"odd stage\"");
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.getEngine().removeSession(session.getSessionId());
            server.stop();
            server = null;
        }
    }

    private static ExecutionResult sql(final String statement) {
        final ExecutionResult result = server.getEngine().execute(statement, session);
        assertTrue(result.isSuccess(), statement + " -> " + result.getErrorMessage());
        return result;
    }

    private static String value(final String query) {
        return String.valueOf(sql(query).getResultSets().get(0).getRows().get(0).getValue(0));
    }

    private static void put(final String stage, final String name, final String content) throws IOException {
        final Path dir = Files.createTempDirectory("sfu_put");
        final Path file = dir.resolve(name);
        Files.writeString(file, content);
        sql("PUT 'file://" + file.toAbsolutePath() + "' @" + stage + " AUTO_COMPRESS = FALSE");
    }

    private HttpResponse<String> fetch(final String url) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void aStageFileUrlServesTheStagedFile() throws Exception {
        put("st/dir", "data 1.csv", "1,a\n");
        final String url = value("SELECT BUILD_STAGE_FILE_URL(@st, 'dir/data 1.csv')");
        assertEquals(baseUrl + "/api/files/SFU_DB/%22my%20sch%22/ST/dir%2fdata%201.csv", url);
        final HttpResponse<String> download = fetch(url);
        assertEquals(200, download.statusCode());
        assertEquals("1,a\n", download.body());

        put("\"odd stage\"", "q.csv", "q\n");
        assertEquals("q\n", fetch(value("SELECT BUILD_STAGE_FILE_URL('@\"odd stage\"', 'q.csv')")).body());
    }

    @Test
    public void aMissingFileOrStageAnswersNoSuchKey() throws Exception {
        final HttpResponse<String> missing = fetch(value("SELECT BUILD_STAGE_FILE_URL(@st, 'none.csv')"));
        assertEquals(404, missing.statusCode());
        assertTrue(missing.body().contains("<Code>NoSuchKey</Code>"), missing.body());
        assertEquals(404, fetch(baseUrl + "/api/files/SFU_DB/PUBLIC/NOSUCH/f.csv").statusCode());
        assertEquals(404, fetch(baseUrl + "/api/files/SFU_DB/f.csv").statusCode());
    }

    @Test
    public void aScopedUrlServesTheStagedFileUntilTheTokenIsForged() throws Exception {
        put("st", "scoped.csv", "s,1\n");
        final String url = value("SELECT BUILD_SCOPED_FILE_URL(@st, 'scoped.csv')");
        assertTrue(url.startsWith(baseUrl + "/api/files/"), url);
        final HttpResponse<String> download = fetch(url);
        assertEquals(200, download.statusCode());
        assertEquals("s,1\n", download.body());
        final String forged = url.substring(0, url.lastIndexOf('/') + 1) + "Zm9vYmFy";
        final HttpResponse<String> refused = fetch(forged);
        assertEquals(403, refused.statusCode());
        assertTrue(refused.body().contains("SignatureDoesNotMatch"), refused.body());
    }

    /**
     * A file keeps its URLs when later names continue its name: the account's object does not move, so neither
     * may the file a presigned or scoped URL signed.
     */
    @Test
    public void aFileLaterNamesContinueKeepsItsUrls() throws Exception {
        sql("COPY INTO @st/kept FROM (SELECT 50) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        final String presigned = value("SELECT GET_PRESIGNED_URL(@st, 'kept')");
        final String scoped = value("SELECT BUILD_SCOPED_FILE_URL(@st, 'kept')");
        final String named = value("SELECT BUILD_STAGE_FILE_URL(@st, 'kept')");
        assertEquals("50", fetch(presigned).body().trim());
        sql("COPY INTO @st/kept/g FROM (SELECT 5) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        for (final String url : new String[]{presigned, scoped, named}) {
            final HttpResponse<String> download = fetch(url);
            assertEquals(200, download.statusCode(), url + " -> " + download.body());
            assertEquals("50", download.body().trim(), url);
        }
        assertEquals("5", fetch(value("SELECT GET_PRESIGNED_URL(@st, 'kept/g')")).body().trim());
    }

    @Test
    public void onlyReadsAreServed() throws Exception {
        final String url = value("SELECT BUILD_STAGE_FILE_URL(@st, 'x.csv')");
        final HttpResponse<String> posted = client.send(HttpRequest.newBuilder().uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, posted.statusCode());
    }
}
