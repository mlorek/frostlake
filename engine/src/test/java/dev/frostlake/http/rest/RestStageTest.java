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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stage endpoints ({@code stage.yaml}), each translated into the SQL the engine answers. */
public class RestStageTest extends BaseRestTest {

    private static String stages(final String database) {
        return "/api/v2/databases/" + database + "/schemas/s/stages";
    }

    private static void schema(final String database) {
        sql("CREATE DATABASE " + database);
        sql("CREATE SCHEMA " + database + ".s");
    }

    /**
     * A listed stage is the body a fetch of it answers — its file format, its copy options and every directory
     * table property — and an external stage's cloud is spelled as SHOW STAGES spells it, an internal one's
     * null; the directory table carries no moment of its last refresh (live-verified).
     */
    @Test
    public void aListedStageCarriesItsWholeBody() throws Exception {
        schema("stg_listed");
        sql("CREATE STAGE stg_listed.s.plain DIRECTORY = (ENABLE = TRUE)");
        sql("ALTER STAGE stg_listed.s.plain REFRESH");
        sql("CREATE STAGE stg_listed.s.fmt FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1) COPY_OPTIONS = (ON_ERROR ="
            + " CONTINUE)");
        sql("CREATE STAGE stg_listed.s.ext URL = 's3://bucket/path/'");
        sql("CREATE STAGE stg_listed.s.az URL = 'azure://account.blob.core.windows.net/c/p/'");

        final JsonNode listed = ok(get(stages("stg_listed")));
        assertEquals("AZ,EXT,FMT,PLAIN", names(listed));
        for (final JsonNode stage : listed) {
            assertEquals(ok(get(stages("stg_listed") + "/" + stage.path("name").asString())), stage);
            assertEquals(5, stage.path("directory_table").size(), stage.toString());
            assertTrue(stage.path("directory_table").has("refresh_on_create"), stage.toString());
            assertTrue(stage.path("copy_options").has("on_error"), stage.toString());
            assertEquals("CSV", stage.path("file_format").path("type").asString());
        }
        assertEquals("AZURE", listed.get(0).path("cloud").asString());
        assertEquals("AWS", listed.get(1).path("cloud").asString());
        assertTrue(listed.get(2).path("cloud").isNull(), listed.get(2).toString());
        assertEquals(1, listed.get(2).path("file_format").path("skip_header").asInt());
        assertEquals("CONTINUE", listed.get(2).path("copy_options").path("on_error").asString());
        assertEquals("ABORT_STATEMENT", listed.get(3).path("copy_options").path("on_error").asString());
        assertTrue(listed.get(3).path("directory_table").path("enable").asBoolean(), listed.get(3).toString());
    }

    @Test
    public void aStageIsCreatedFetchedListedAndDropped() throws Exception {
        schema("stg_basic");
        assertEquals("Stage area ST1 successfully created.", ok(post(stages("stg_basic"),
            "{\"name\":\"st1\",\"comment\":\"rest\",\"directory_table\":{\"enable\":true},"
                + "\"file_format\":{\"type\":\"CSV\",\"field_delimiter\":\"|\",\"skip_header\":1},"
                + "\"copy_options\":{\"on_error\":\"CONTINUE\",\"purge\":true}}")).path("status").asString());

        final JsonNode fetched = ok(get(stages("stg_basic") + "/st1"));
        assertEquals("ST1", fetched.path("name").asString());
        assertEquals("PERMANENT", fetched.path("kind").asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertTrue(fetched.path("directory_table").path("enable").asBoolean(), fetched.toString());
        assertEquals("CSV", fetched.path("file_format").path("type").asString());
        assertEquals("|", fetched.path("file_format").path("field_delimiter").asString());
        assertEquals(1, fetched.path("file_format").path("skip_header").asInt());
        assertEquals("CONTINUE", fetched.path("copy_options").path("on_error").asString());
        assertTrue(fetched.path("copy_options").path("purge").asBoolean());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertEquals("", fetched.path("url").asString(), "an internal stage has an empty URL: " + fetched);
        assertTrue(fetched.path("copy_options").get("size_limit").isNull(), fetched.toString());
        assertTrue(fetched.path("directory_table").get("notification_integration").isNull(), fetched.toString());
        assertTrue(fetched.path("directory_table").path("refresh_on_create").asBoolean(), fetched.toString());

        ok(post(stages("stg_basic"), "{\"name\":\"other\"}"));
        assertEquals("OTHER,ST1", names(ok(get(stages("stg_basic")))));
        assertEquals("ST1", names(ok(get(stages("stg_basic") + "?like=st%25"))));

        assertEquals("ST1 successfully dropped.", ok(delete(stages("stg_basic") + "/st1")).path("status").asString());
        error(404, get(stages("stg_basic") + "/st1"));
        error(404, delete(stages("stg_basic") + "/st1"));
        ok(delete(stages("stg_basic") + "/st1?ifExists=true"));
    }

    @Test
    public void anExternalStageCarriesItsUrlAndCredentials() throws Exception {
        schema("stg_ext");
        ok(post(stages("stg_ext"), "{\"name\":\"ext\",\"url\":\"s3://bucket/path/\",\"credentials\":"
            + "{\"credential_type\":\"AWS\",\"aws_key_id\":\"k\",\"aws_secret_key\":\"s\"},"
            + "\"encryption\":{\"type\":\"AWS_SSE_S3\"}}"));
        final JsonNode fetched = ok(get(stages("stg_ext") + "/ext"));
        assertEquals("s3://bucket/path/", fetched.path("url").asString());
        // The body relays SHOW STAGES' cells: the provider as the listing spells it, and the two flags.
        assertEquals("AWS", fetched.path("cloud").asString());
        assertTrue(fetched.path("has_credentials").asBoolean(), fetched.toString());
        assertEquals("PERMANENT", fetched.path("kind").asString());
        assertTrue(fetched.get("credentials").isNull(), "credentials are write-only: " + fetched);
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        schema("stg_modes");
        ok(post(stages("stg_modes"), "{\"name\":\"m\",\"comment\":\"first\"}"));
        error(409, post(stages("stg_modes") + "?createMode=errorIfExists", "{\"name\":\"m\"}"));
        ok(post(stages("stg_modes") + "?createMode=ifNotExists", "{\"name\":\"m\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(stages("stg_modes") + "/m")).path("comment").asString());
        ok(post(stages("stg_modes") + "?createMode=orReplace", "{\"name\":\"m\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get(stages("stg_modes") + "/m")).path("comment").asString());
        // A temporary stage is its session's alone: the create and the read share the session kept for one token,
        // where a request without one is a session of its own that reaches none of another's.
        ok(asToken("POST", stages("stg_modes"), "{\"name\":\"tmp\",\"kind\":\"TEMPORARY\"}"));
        assertEquals("TEMPORARY", ok(asToken("GET", stages("stg_modes") + "/tmp", null)).path("kind").asString());
        error(404, get(stages("stg_modes") + "/tmp"));
        error(400, post(stages("stg_modes"), "{\"name\":\"bad\",\"kind\":\"TRANSIENT\"}"));
        error(400, post(stages("stg_modes"), "{\"comment\":\"no name\"}"));
        error(400, post(stages("stg_modes"), "{\"name\":\"bad\",\"file_format\":{\"type\":\"CSV\",\"x y\":1}}"));
    }

    @Test
    public void aStageInAMissingSchemaIs404() throws Exception {
        error(404, get("/api/v2/databases/stg_none/schemas/s/stages"));
        error(404, post("/api/v2/databases/stg_none/schemas/s/stages", "{\"name\":\"x\"}"));
    }

    @Test
    public void listFilesListsWhatIsStagedMatchingThePattern() throws Exception {
        schema("stg_files");
        ok(post(stages("stg_files"), "{\"name\":\"files\"}"));
        final Path dir = Files.createTempDirectory("rest-stage");
        final Path csv = dir.resolve("data.csv");
        Files.writeString(csv, "1,a\n");
        final Path json = dir.resolve("data.json");
        Files.writeString(json, "{}\n");
        sql("PUT 'file://" + csv + "' @stg_files.s.files AUTO_COMPRESS = FALSE");
        sql("PUT 'file://" + json + "' @stg_files.s.files AUTO_COMPRESS = FALSE");
        final JsonNode all = ok(get(stages("stg_files") + "/files/files"));
        assertEquals(2, all.size(), all.toString());
        assertTrue(all.get(0).path("size").isString(), "size is a string: " + all);
        final JsonNode matching = ok(get(stages("stg_files") + "/files/files?pattern=.*%5C.csv"));
        assertEquals(1, matching.size(), matching.toString());
        assertTrue(matching.get(0).path("name").asString().endsWith("data.csv"), matching.toString());
        error(404, get(stages("stg_files") + "/nothing/files"));
    }

    @Test
    public void aPresignedUrlDownloadsTheStagedFile() throws Exception {
        schema("stg_url");
        ok(post(stages("stg_url"), "{\"name\":\"u\"}"));
        final Path dir = Files.createTempDirectory("rest-presigned");
        final Path csv = dir.resolve("data.csv");
        Files.writeString(csv, "1,a\n");
        sql("PUT 'file://" + csv + "' @stg_url.s.u AUTO_COMPRESS = FALSE");
        final String url = ok(post(stages("stg_url") + "/u/files/data.csv:presigned-url",
            "{\"expiration_time\":600}")).path("presigned_url").asString();
        assertTrue(url.startsWith(baseUrl + "/presigned/"), url);
        final HttpResponse<String> download = client.send(HttpRequest.newBuilder().uri(URI.create(url)).GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, download.statusCode());
        assertEquals("1,a\n", download.body());

        final String missing = ok(post(stages("stg_url") + "/u/files/none.csv:presigned-url", null))
            .path("presigned_url").asString();
        final HttpResponse<String> absent = client.send(HttpRequest.newBuilder().uri(URI.create(missing)).GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, absent.statusCode());
        assertTrue(absent.body().contains("NoSuchKey"), absent.body());

        final HttpResponse<String> forged = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/presigned/Zm9v.YmFy/data.csv")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forged.statusCode());
        error(404, post(stages("stg_url") + "/nope/files/data.csv:presigned-url", null));
        error(400, post(stages("stg_url") + "/u/files/data.csv:presigned-url", "{\"expiration_time\":0}"));
    }
    /** A request in the session the REST API keeps for one bearer token, which every request carrying it shares. */
    private HttpResponse<String> asToken(final String method, final String path, final String body) throws Exception {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
            .header("Accept", "application/json").header("Authorization", "Bearer stage-modes");
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
