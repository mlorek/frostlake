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
        assertEquals("aws", fetched.path("cloud").asString());
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
        ok(post(stages("stg_modes"), "{\"name\":\"tmp\",\"kind\":\"TEMPORARY\"}"));
        ok(get(stages("stg_modes") + "/tmp"));
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
}
