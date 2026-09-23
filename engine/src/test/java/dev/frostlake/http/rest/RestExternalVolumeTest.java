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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The external volume endpoints ({@code external-volume.yaml}). */
public class RestExternalVolumeTest extends BaseRestTest {

    private static final String BODY = "{\"name\":\"%s\",\"storage_locations\":[{\"name\":\"l1\","
        + "\"storage_provider\":\"S3\",\"storage_base_url\":\"s3://b/p/\",\"storage_aws_role_arn\":\"arn:r\","
        + "\"encryption\":{\"type\":\"AWS_SSE_KMS\",\"kms_key_id\":\"k\"}},{\"name\":\"l2\","
        + "\"storage_provider\":\"GCS\",\"storage_base_url\":\"gcs://b/\"}],\"allow_writes\":false,"
        + "\"comment\":\"%s\"}";

    @Test
    public void aVolumeIsCreatedFetchedListedDroppedAndUndropped() throws Exception {
        assertEquals("RV_ONE successfully created.", ok(post("/api/v2/external-volumes",
            String.format(BODY, "rv_one", "first"))).path("status").asString());
        final JsonNode fetched = ok(get("/api/v2/external-volumes/rv_one"));
        assertEquals("RV_ONE", fetched.path("name").asString());
        assertFalse(fetched.path("allow_writes").asBoolean());
        assertEquals("first", fetched.path("comment").asString());
        final JsonNode first = fetched.path("storage_locations").get(0);
        assertEquals("l1", first.path("name").asString());
        assertEquals("S3", first.path("storage_provider").asString());
        assertEquals("arn:r", first.path("storage_aws_role_arn").asString());
        assertEquals("AWS_SSE_KMS", first.path("encryption").path("type").asString());
        assertEquals("k", first.path("encryption").path("kms_key_id").asString());
        assertEquals("GCS", fetched.path("storage_locations").get(1).path("storage_provider").asString());
        assertEquals("RV_ONE", names(ok(get("/api/v2/external-volumes?like=RV_ONE"))));

        ok(delete("/api/v2/external-volumes/rv_one"));
        error(404, get("/api/v2/external-volumes/rv_one"));
        ok(delete("/api/v2/external-volumes/rv_one?ifExists=true"));
        ok(post("/api/v2/external-volumes/rv_one:undrop", ""));
        assertEquals("first", ok(get("/api/v2/external-volumes/rv_one")).path("comment").asString());
        error(409, post("/api/v2/external-volumes/rv_one:undrop", ""));
        error(400, post("/api/v2/external-volumes/rv_never:undrop", ""));
    }

    @Test
    public void createModes() throws Exception {
        ok(post("/api/v2/external-volumes", String.format(BODY, "rv_modes", "first")));
        error(409, post("/api/v2/external-volumes", String.format(BODY, "rv_modes", "second")));
        ok(post("/api/v2/external-volumes?createMode=ifNotExists", String.format(BODY, "rv_modes", "second")));
        assertEquals("first", ok(get("/api/v2/external-volumes/rv_modes")).path("comment").asString());
        ok(post("/api/v2/external-volumes?createMode=orReplace", String.format(BODY, "rv_modes", "third")));
        assertEquals("third", ok(get("/api/v2/external-volumes/rv_modes")).path("comment").asString());
        error(400, post("/api/v2/external-volumes", "{\"name\":\"rv_bad\",\"storage_locations\":[]}"));
        error(400, post("/api/v2/external-volumes", "{\"name\":\"rv_bad\",\"storage_locations\":[{\"name\":\"x\","
            + "\"storage_provider\":\"S3\"}]}"));
    }
}
