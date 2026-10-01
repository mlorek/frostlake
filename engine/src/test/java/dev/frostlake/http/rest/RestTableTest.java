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

import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The table endpoints ({@code table.yaml}), each translated into the SQL the engine answers. */
public class RestTableTest extends BaseRestTest {

    private static final String TABLES = "/api/v2/databases/rest_db/schemas/s1/tables";

    @BeforeAll
    public static void createSchemas() {
        sql("CREATE DATABASE rest_db");
        sql("CREATE SCHEMA rest_db.s1");
        sql("CREATE SCHEMA rest_db.s2");
    }

    /** The number of rows a table holds, counted on the server's engine. */
    private static long count(final String table) {
        final SessionContext session = server.getEngine().createSession();
        try {
            final ExecutionResult result = server.getEngine().execute("SELECT COUNT(*) FROM " + table, session);
            return ((Number) result.getResultSets().get(0).getRows().get(0).getValue(0)).longValue();
        } finally {
            server.getEngine().removeSession(session.getSessionId());
        }
    }

    @Test
    public void aTableIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Table T_BASIC successfully created.", ok(post(TABLES, """
            {"name": "t_basic", "kind": "TRANSIENT", "comment": "rest", "data_retention_time_in_days": 0,
             "change_tracking": true, "cluster_by": ["id"],
             "columns": [
               {"name": "id", "datatype": "NUMBER(38,0)", "nullable": false, "autoincrement": true,
                "autoincrement_start": 10, "autoincrement_increment": 5, "comment": "the id"},
               {"name": "label", "datatype": "VARCHAR(20)", "collate": "en-ci"},
               {"name": "qty", "datatype": "INT", "default": "7"}],
             "constraints": [{"name": "pk_basic", "column_names": ["id"], "constraint_type": "PRIMARY KEY"},
                             {"name": "uq_basic", "column_names": ["label"], "constraint_type": "UNIQUE"}]}
            """)).path("status").asString());
        final JsonNode table = ok(get(TABLES + "/t_basic"));
        assertEquals("T_BASIC", table.path("name").asString());
        assertEquals("TRANSIENT", table.path("kind").asString());
        assertEquals("rest", table.path("comment").asString());
        assertEquals(0, table.path("data_retention_time_in_days").asInt());
        assertTrue(table.path("change_tracking").asBoolean());
        assertEquals("id", table.path("cluster_by").get(0).asString());
        assertEquals("LINEAR(id)", table.path("cluster_by_raw").asString());
        assertEquals("NORMAL", table.path("table_type").asString());
        assertEquals("REST_DB", table.path("database_name").asString());
        assertTrue(table.path("created_on").asString().contains("T"), table.toString());
        final JsonNode id = table.path("columns").get(0);
        assertEquals("ID", id.path("name").asString());
        assertEquals("NUMBER(38,0)", id.path("datatype").asString());
        assertFalse(id.path("nullable").asBoolean());
        assertTrue(id.path("autoincrement").asBoolean());
        assertEquals(10, id.path("autoincrement_start").asInt());
        assertEquals(5, id.path("autoincrement_increment").asInt());
        assertEquals("the id", id.path("comment").asString());
        final JsonNode label = table.path("columns").get(1);
        assertEquals("VARCHAR(20)", label.path("datatype").asString());
        assertEquals("en-ci", label.path("collate").asString());
        assertEquals("7", table.path("columns").get(2).path("default").asString());
        assertEquals("PK_BASIC", table.path("constraints").get(0).path("name").asString());
        assertEquals("PRIMARY KEY", table.path("constraints").get(0).path("constraint_type").asString());
        assertEquals("ID", table.path("constraints").get(0).path("column_names").get(0).asString());
        assertEquals("UNIQUE", table.path("constraints").get(1).path("constraint_type").asString());
        assertEquals("T_BASIC", names(ok(get(TABLES + "?like=t_bas%25"))));
        assertEquals("", names(ok(get(TABLES + "?like=nothing_like_this"))));
        assertTrue(ok(get(TABLES + "?like=t_bas%25")).get(0).path("columns").isNull(), "a shallow listing has no columns");
        assertEquals(3, ok(get(TABLES + "?like=t_bas%25&deep=true")).get(0).path("columns").size());
        assertEquals("T_BASIC successfully dropped.", ok(delete(TABLES + "/t_basic")).path("status").asString());
        error(404, get(TABLES + "/t_basic"));
        error(404, delete(TABLES + "/t_basic"));
        ok(delete(TABLES + "/t_basic?ifExists=true"));
    }

    @Test
    public void foreignKeysAreReadBack() throws Exception {
        ok(post(TABLES, """
            {"name": "t_parent", "columns": [{"name": "id", "datatype": "INT"}],
             "constraints": [{"name": "pk_parent", "column_names": ["id"], "constraint_type": "PRIMARY KEY"}]}
            """));
        ok(post(TABLES, """
            {"name": "t_child", "columns": [{"name": "pid", "datatype": "INT",
              "constraints": [{"name": "fk_child", "constraint_type": "FOREIGN KEY",
                               "referenced_table_name": "t_parent", "referenced_column_names": ["id"]}]}]}
            """));
        final JsonNode fk = ok(get(TABLES + "/t_child")).path("constraints").get(0);
        assertEquals("FK_CHILD", fk.path("name").asString());
        assertEquals("FOREIGN KEY", fk.path("constraint_type").asString());
        assertEquals("PID", fk.path("column_names").get(0).asString());
        assertEquals("REST_DB.S1.T_PARENT", fk.path("referenced_table_name").asString());
        assertEquals("ID", fk.path("referenced_column_names").get(0).asString());
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        final String body = "{\"name\":\"t_modes\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}],\"comment\":\"%s\"}";
        ok(post(TABLES, String.format(body, "first")));
        error(409, post(TABLES + "?createMode=errorIfExists", String.format(body, "x")));
        ok(post(TABLES + "?createMode=ifNotExists", String.format(body, "second")));
        assertEquals("first", ok(get(TABLES + "/t_modes")).path("comment").asString());
        ok(post(TABLES + "?createMode=orReplace&copyGrants=true", String.format(body, "third")));
        assertEquals("third", ok(get(TABLES + "/t_modes")).path("comment").asString());
        error(400, post(TABLES, "{\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
        error(400, post(TABLES, "{\"name\":\"t_nocols\"}"));
        error(400, post(TABLES, "{\"name\":\"t_badkind\",\"kind\":\"EXTERNAL\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
    }

    @Test
    public void asSelectCreatesAPopulatedTable() throws Exception {
        ok(post(TABLES + ":as-select?query=SELECT%201%20AS%20a", "{\"name\":\"t_ctas\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
        ok(get(TABLES + "/t_ctas"));
        assertEquals(1L, count("rest_db.s1.t_ctas"));
        ok(post(TABLES + "/t_ctas_old:as_select?query=SELECT%202%20AS%20b", "{\"name\":\"t_ctas_old\"}"));
        assertEquals("B", ok(get(TABLES + "/t_ctas_old")).path("columns").get(0).path("name").asString());
        error(400, post(TABLES + ":as-select", "{\"name\":\"t_ctas_noquery\"}"));
    }

    @Test
    public void putCreatesThenAltersTheTable() throws Exception {
        ok(put(TABLES + "/t_put", "{\"name\":\"t_put\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}],\"comment\":\"v1\"}"));
        sql("INSERT INTO rest_db.s1.t_put VALUES (1)");
        ok(put(TABLES + "/t_put", """
            {"name": "t_put", "comment": "v2",
             "columns": [{"name": "a", "datatype": "INT"}, {"name": "b", "datatype": "VARCHAR"}]}
            """));
        final JsonNode table = ok(get(TABLES + "/t_put"));
        assertEquals("v2", table.path("comment").asString());
        assertEquals(2, table.path("columns").size());
        assertEquals(1L, count("rest_db.s1.t_put"), "the rows are kept");
        error(409, put(TABLES + "/t_put", "{\"name\":\"other\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
    }

    @Test
    public void cloneLikeUndropAndSwap() throws Exception {
        ok(post(TABLES, "{\"name\":\"t_src\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
        sql("INSERT INTO rest_db.s1.t_src VALUES (1), (2)");
        ok(post(TABLES + "/t_src:clone?targetSchema=s2", """
            {"name": "t_cloned", "point_of_time": {"point_of_time_type": "offset", "reference": "at", "offset": "-1"}}
            """));
        ok(get("/api/v2/databases/rest_db/schemas/s2/tables/t_cloned"));
        assertEquals(2L, count("rest_db.s2.t_cloned"));
        error(400, post(TABLES + "/t_src:clone", "{\"name\":\"t_bad\",\"point_of_time\":{\"point_of_time_type\":\"era\"}}"));
        ok(post(TABLES + "/t_src:create-like", "{\"name\":\"t_like\"}"));
        ok(get(TABLES + "/t_like"));
        assertEquals(0L, count("rest_db.s1.t_like"));
        ok(post(TABLES + "/t_src:create_like?newTableName=t_like_old", null));
        ok(get(TABLES + "/t_like_old"));
        ok(post(TABLES + "/t_src:swap-with?targetName=t_like", null));
        assertEquals(2L, count("rest_db.s1.t_like"));
        ok(post(TABLES + "/t_src:swapwith?targetTableName=rest_db.s1.t_like", null));
        assertEquals(2L, count("rest_db.s1.t_src"));
        ok(delete(TABLES + "/t_like_old"));
        error(404, get(TABLES + "/t_like_old"));
        ok(post(TABLES + "/t_like_old:undrop", null));
        ok(get(TABLES + "/t_like_old"));
    }

    @Test
    public void reclusteringIsSuspendedAndResumed() throws Exception {
        ok(post(TABLES, "{\"name\":\"t_rc\",\"cluster_by\":[\"a\"],\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
        ok(post(TABLES + "/t_rc:suspend-recluster", null));
        assertFalse(ok(get(TABLES + "/t_rc")).path("automatic_clustering").asBoolean());
        ok(post(TABLES + "/t_rc:resume_recluster", null));
        assertTrue(ok(get(TABLES + "/t_rc")).path("automatic_clustering").asBoolean());
        ok(post(TABLES + "/t_rc:suspend_recluster", null));
        ok(post(TABLES + "/t_rc:resume-recluster", null));
        error(404, post(TABLES + "/nosuch:suspend-recluster", null));
        ok(post(TABLES + "/nosuch:suspend-recluster?ifExists=true", null));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE TAG rest_db.s1.tbl_tag");
        ok(post(TABLES, "{\"name\":\"t_tags\",\"columns\":[{\"name\":\"a\",\"datatype\":\"INT\"}]}"));
        ok(post(TABLES + "/t_tags:set-tags",
            "[{\"tag_database\":\"rest_db\",\"tag_schema\":\"s1\",\"tag_name\":\"tbl_tag\",\"tag_value\":\"gold\"}]"));
        final JsonNode tags = ok(get(TABLES + "/t_tags:get-tags"));
        assertEquals(1, tags.size());
        assertEquals("TBL_TAG", tags.get(0).path("tag_name").asString());
        assertEquals("gold", tags.get(0).path("tag_value").asString());
        ok(post(TABLES + "/t_tags:unset-tags",
            "[{\"tag_database\":\"rest_db\",\"tag_schema\":\"s1\",\"tag_name\":\"tbl_tag\"}]"));
        assertEquals(0, ok(get(TABLES + "/t_tags:get-tags")).size());
    }

    @Test
    public void usingTemplateTakesTheColumnsTheQueryDescribes() throws Exception {
        final String query = "SELECT%20ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME',%20value::VARCHAR,%20'TYPE',%20'TEXT',"
            + "%20'NULLABLE',%20FALSE))%20FROM%20TABLE(FLATTEN(INPUT%20%3D%3E%20ARRAY_CONSTRUCT('c1')))";
        ok(post(TABLES + ":using-template?query=" + query, "{\"name\":\"t_tpl\"}"));
        final JsonNode column = ok(get(TABLES + "/t_tpl")).path("columns").get(0);
        assertEquals("\"c1\"", column.path("name").asString());
        assertFalse(column.path("nullable").asBoolean());
        ok(post(TABLES + "/t_tpl_old:using_template?query=" + query, null));
        ok(get(TABLES + "/t_tpl_old"));
        error(409, post(TABLES + ":using-template?query=" + query, "{\"name\":\"t_tpl\"}"));
        error(400, post(TABLES + ":using-template", "{\"name\":\"t_tpl_noquery\"}"));
    }
}
