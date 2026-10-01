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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Iceberg table endpoints ({@code iceberg-table.yaml}): Snowflake-managed tables only. */
public class RestIcebergTableTest extends BaseRestTest {

    private static final String BASE = "/api/v2/databases/ri_db/schemas/s1/iceberg-tables";

    @BeforeAll
    public static void schema() {
        sql("CREATE DATABASE IF NOT EXISTS ri_db");
        sql("CREATE SCHEMA IF NOT EXISTS ri_db.s1");
        sql("CREATE OR REPLACE EXTERNAL VOLUME ri_vol STORAGE_LOCATIONS = ((NAME = 'l1' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://ice/' STORAGE_AWS_ROLE_ARN = 'arn:r'))");
    }

    private static String table(final String name, final String comment) {
        return "{\"name\":\"" + name + "\",\"columns\":[{\"name\":\"a\",\"datatype\":\"NUMBER(10,0)\","
            + "\"nullable\":false},{\"name\":\"b\",\"datatype\":\"VARCHAR\",\"comment\":\"bee\"}],"
            + "\"external_volume\":\"ri_vol\",\"catalog\":\"SNOWFLAKE\",\"base_location\":\"" + name + "/\","
            + "\"cluster_by\":[\"a\"],\"comment\":\"" + comment + "\"}";
    }

    @Test
    public void aTableIsCreatedFetchedListedDroppedAndUndropped() throws Exception {
        assertEquals("Table ICE1 successfully created.", ok(post(BASE, table("ice1", "first")))
            .path("status").asString());
        final JsonNode fetched = ok(get(BASE + "/ice1"));
        assertEquals("ICE1", fetched.path("name").asString());
        assertEquals("RI_VOL", fetched.path("external_volume").asString());
        assertEquals("SNOWFLAKE", fetched.path("catalog").asString());
        assertEquals("ice1/", fetched.path("base_location").asString());
        assertEquals("first", fetched.path("comment").asString());
        assertEquals("MANAGED", fetched.path("iceberg_table_type").asString());
        assertEquals(2, fetched.path("columns").size());
        assertEquals("A", fetched.path("columns").get(0).path("name").asString());
        assertEquals(false, fetched.path("columns").get(0).path("nullable").asBoolean());
        assertEquals("bee", fetched.path("columns").get(1).path("comment").asString());
        assertTrue(fetched.path("cluster_by").toString().contains("A"), fetched.toString());

        assertEquals("ICE1", names(ok(get(BASE + "?like=ICE1"))));
        assertTrue(ok(get(BASE + "?like=ICE1")).get(0).path("columns").isNull());
        assertEquals(2, ok(get(BASE + "?like=ICE1&deep=true")).get(0).path("columns").size());

        error(409, post(BASE, table("ice1", "second")));
        ok(post(BASE + "?createMode=ifNotExists", table("ice1", "second")));
        assertEquals("first", ok(get(BASE + "/ice1")).path("comment").asString());

        ok(post(BASE + "/ice1:suspend-recluster", ""));
        ok(post(BASE + "/ice1:resume-recluster", ""));
        error(400, post(BASE + "/ice1:refresh", "{}"));
        error(400, post(BASE + "/ice1:convert-to-managed", "{\"base_location\":\"x\"}"));

        ok(delete(BASE + "/ice1"));
        error(404, get(BASE + "/ice1"));
        ok(delete(BASE + "/ice1?ifExists=true"));
        ok(post(BASE + "/ice1:undrop", ""));
        assertEquals("first", ok(get(BASE + "/ice1")).path("comment").asString());
    }

    @Test
    public void asSelectCloneAndLike() throws Exception {
        ok(post(BASE, table("src", "source")));
        sql("INSERT INTO ri_db.s1.src VALUES (1, 'x'), (2, 'y')");
        ok(post(BASE + ":as-select?query=SELECT%20a%20FROM%20ri_db.s1.src",
            "{\"name\":\"ctas\",\"external_volume\":\"ri_vol\"}"));
        assertEquals(1, ok(get(BASE + "/ctas")).path("columns").size());
        ok(post(BASE + "/src:clone", "{\"name\":\"cloned\"}"));
        assertEquals("RI_VOL", ok(get(BASE + "/cloned")).path("external_volume").asString());
        ok(post(BASE + "/src:create-like", "{\"name\":\"liked\",\"comment\":\"copy\"}"));
        assertEquals("copy", ok(get(BASE + "/liked")).path("comment").asString());
        error(400, post(BASE + ":as-select", "{\"name\":\"no_query\"}"));
        error(404, post(BASE + "/absent:clone", "{\"name\":\"never\"}"));
    }

    @Test
    public void externallyManagedTablesAre501() throws Exception {
        for (final String variant : new String[] {"from-aws-glue-catalog", "from-delta", "from-iceberg-files",
                "from-iceberg-rest"}) {
            error(501, post(BASE + ":" + variant, "{\"name\":\"ext\",\"catalog_table_name\":\"t\","
                + "\"metadata_file_path\":\"m\",\"base_location\":\"b\"}"));
        }
        error(400, post(BASE, "{\"name\":\"no_columns\",\"external_volume\":\"ri_vol\"}"));
    }
}
