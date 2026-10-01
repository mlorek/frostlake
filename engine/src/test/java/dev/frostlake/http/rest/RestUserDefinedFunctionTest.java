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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The user-defined function endpoints over CREATE / SHOW / DESCRIBE / DROP / ALTER FUNCTION and SELECT. */
public class RestUserDefinedFunctionTest extends BaseRestTest {

    private static final String UDFS = "/api/v2/databases/udf_db/schemas/s/user-defined-functions";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE udf_db");
        sql("CREATE SCHEMA udf_db.s");
    }

    private static String sqlFunction(final String name, final String comment) {
        return "{\"name\":\"" + name + "\",\"arguments\":[{\"name\":\"a\",\"datatype\":\"NUMBER\"},"
            + "{\"name\":\"b\",\"datatype\":\"NUMBER\"}],\"return_type\":{\"type\":\"DATATYPE\",\"datatype\":\"NUMBER\"},"
            + "\"language_config\":{\"language\":\"SQL\"},\"body\":\"a + b\",\"comment\":\"" + comment + "\"}";
    }

    @Test
    public void aFunctionIsCreatedFetchedListedExecutedAndDropped() throws Exception {
        assertEquals("Function ADD_AB successfully created.", ok(post(UDFS, sqlFunction("add_ab", "adds")))
            .path("status").asString());
        final JsonNode fetched = ok(get(UDFS + "/add_ab(NUMBER,NUMBER)"));
        assertEquals("ADD_AB", fetched.path("name").asString());
        assertEquals("A", fetched.path("arguments").get(0).path("name").asString());
        assertEquals("NUMBER", fetched.path("arguments").get(1).path("datatype").asString());
        assertEquals("DATATYPE", fetched.path("return_type").path("type").asString());
        assertTrue(fetched.path("return_type").path("datatype").asString().startsWith("NUMBER"), fetched.toString());
        assertEquals("SQL", fetched.path("language_config").path("language").asString());
        assertEquals("a + b", fetched.path("body").asString());
        assertEquals("adds", fetched.path("comment").asString());
        assertEquals("UDF_DB", fetched.path("database_name").asString());
        assertEquals("S", fetched.path("schema_name").asString());
        assertEquals(2, fetched.path("max_num_arguments").asInt());
        assertFalse(fetched.path("is_secure").asBoolean());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        // arguments written with their names meet the statement's refusal of a named argument
        error(400, get(UDFS + "/add_ab(a%20number,%20b%20number)"));
        assertTrue(names(ok(get(UDFS + "?like=ADD_A%25"))).contains("ADD_AB"));
        assertEquals("", names(ok(get(UDFS + "?like=nothing_like_this"))));
        final JsonNode value = ok(post(UDFS + "/add_ab:execute",
            "[{\"name\":\"a\",\"datatype\":\"NUMBER\",\"value\":40},{\"name\":\"b\",\"datatype\":\"NUMBER\",\"value\":2}]"));
        assertEquals("42", value.path("ADD_AB").asString(), value.toString());
        final JsonNode positional = ok(post(UDFS + "/add_ab:execute",
            "[{\"datatype\":\"NUMBER\",\"value\":1},{\"datatype\":\"NUMBER\",\"value\":2}]"));
        assertEquals("3", positional.path("ADD_AB").asString(), positional.toString());
        ok(delete(UDFS + "/add_ab(NUMBER,NUMBER)"));
        error(404, get(UDFS + "/add_ab(NUMBER,NUMBER)"));
        error(404, delete(UDFS + "/add_ab(NUMBER,NUMBER)"));
        ok(delete(UDFS + "/add_ab(NUMBER,NUMBER)?ifExists=true"));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        ok(post(UDFS, sqlFunction("modes_fn", "first")));
        error(409, post(UDFS + "?createMode=errorIfExists", sqlFunction("modes_fn", "again")));
        ok(post(UDFS + "?createMode=ifNotExists", sqlFunction("modes_fn", "second")));
        assertEquals("first", ok(get(UDFS + "/modes_fn(NUMBER,NUMBER)")).path("comment").asString());
        ok(post(UDFS + "?createMode=orReplace", sqlFunction("modes_fn", "third")));
        assertEquals("third", ok(get(UDFS + "/modes_fn(NUMBER,NUMBER)")).path("comment").asString());
    }

    @Test
    public void overloadsAreAddressedByTheirArgumentTypes() throws Exception {
        ok(post(UDFS, "{\"name\":\"over\",\"arguments\":[{\"name\":\"x\",\"datatype\":\"VARCHAR\"}],"
            + "\"return_type\":{\"datatype\":\"VARCHAR\"},\"language_config\":{\"language\":\"SQL\"},\"body\":\"x || '!'\"}"));
        ok(post(UDFS, "{\"name\":\"over\",\"arguments\":[{\"name\":\"x\",\"datatype\":\"NUMBER\"}],"
            + "\"return_type\":{\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":\"SQL\"},\"body\":\"x * 2\"}"));
        assertEquals("x || '!'", ok(get(UDFS + "/over(VARCHAR)")).path("body").asString());
        assertEquals("x * 2", ok(get(UDFS + "/over(NUMBER)")).path("body").asString());
        assertEquals("OVER,OVER", names(ok(get(UDFS + "?like=OVER"))));
        ok(delete(UDFS + "/over(VARCHAR)"));
        assertEquals("OVER", names(ok(get(UDFS + "?like=OVER"))));
    }

    @Test
    public void aTableFunctionAndASecureOneKeepTheirShape() throws Exception {
        ok(post(UDFS, "{\"name\":\"tab_fn\",\"arguments\":[],\"return_type\":{\"type\":\"TABLE\",\"column_list\":"
            + "[{\"name\":\"c\",\"datatype\":\"NUMBER\"}]},\"language_config\":{\"language\":\"SQL\"},\"body\":\"SELECT 1\"}"));
        final JsonNode table = ok(get(UDFS + "/tab_fn()"));
        assertEquals("TABLE", table.path("return_type").path("type").asString());
        assertTrue(table.path("is_table_function").asBoolean());
        assertEquals("C", table.path("return_type").path("column_list").get(0).path("name").asString());
        ok(post(UDFS, "{\"name\":\"sec_fn\",\"is_secure\":true,\"arguments\":[],\"return_type\":{\"datatype\":\"NUMBER\"},"
            + "\"language_config\":{\"language\":\"SQL\"},\"body\":\"1\"}"));
        assertTrue(ok(get(UDFS + "/sec_fn()")).path("is_secure").asBoolean());
    }

    @Test
    public void renameMovesTheFunction() throws Exception {
        sql("CREATE SCHEMA udf_db.other");
        ok(post(UDFS, sqlFunction("ren_fn", "r")));
        ok(post(UDFS + "/ren_fn(NUMBER,NUMBER):rename?targetName=ren_fn2", null));
        error(404, get(UDFS + "/ren_fn(NUMBER,NUMBER)"));
        ok(get(UDFS + "/ren_fn2(NUMBER,NUMBER)"));
        ok(post(UDFS + "/ren_fn2(NUMBER,NUMBER):rename?targetSchema=other&targetName=ren_fn3", null));
        ok(get("/api/v2/databases/udf_db/schemas/other/user-defined-functions/ren_fn3(NUMBER,NUMBER)"));
        error(400, post(UDFS + "/ren_fn3(NUMBER,NUMBER):rename", null));
        error(404, post(UDFS + "/no_such_fn(NUMBER):rename?targetName=x", null));
        ok(post(UDFS + "/no_such_fn(NUMBER):rename?targetName=x&ifExists=true", null));
    }

    @Test
    public void malformedBodiesAndSignaturesAre400AndAggregatesAre501() throws Exception {
        error(400, post(UDFS, "{\"name\":\"bad_fn\",\"arguments\":[],\"language_config\":{\"language\":\"SQL\"}}"));
        error(400, post(UDFS, "{\"name\":\"bad_fn\",\"arguments\":[{\"name\":\"a\",\"datatype\":\"NUMBER; DROP\"}],"
            + "\"return_type\":{\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":\"SQL\"},\"body\":\"1\"}"));
        error(400, get(UDFS + "/bad_fn(NUMBER"));
        error(501, post(UDFS, "{\"name\":\"agg_fn\",\"is_aggregate\":true,\"arguments\":[],"
            + "\"return_type\":{\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":\"PYTHON\"},\"body\":\"1\"}"));
        error(404, get("/api/v2/databases/no_such_db/schemas/s/user-defined-functions/f()"));
    }

    @Test
    public void tagsAreSetAndUnsetOnTheOverload() throws Exception {
        sql("CREATE TAG udf_db.s.fn_tag");
        ok(post(UDFS, sqlFunction("tagged_fn", "t")));
        ok(post(UDFS + "/tagged_fn(NUMBER,NUMBER):set-tags",
            "[{\"tag_database\":\"udf_db\",\"tag_schema\":\"s\",\"tag_name\":\"fn_tag\",\"tag_value\":\"v\"}]"));
        ok(post(UDFS + "/tagged_fn(NUMBER,NUMBER):unset-tags",
            "[{\"tag_database\":\"udf_db\",\"tag_schema\":\"s\",\"tag_name\":\"fn_tag\"}]"));
        error(404, post(UDFS + "/no_such_fn(NUMBER):set-tags",
            "[{\"tag_database\":\"udf_db\",\"tag_schema\":\"s\",\"tag_name\":\"fn_tag\",\"tag_value\":\"v\"}]"));
    }
}
