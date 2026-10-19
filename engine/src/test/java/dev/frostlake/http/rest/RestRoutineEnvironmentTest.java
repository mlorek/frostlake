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

/**
 * What a routine's REST body tells beyond its signature (live-verified): each argument's default as the DDL
 * spells it, null for one that has none or defaults to NULL; a procedure's restricted caller's rights; and in
 * {@code language_config} a handler routine's packages, target path, artifact repository, integrations and
 * secrets, each secret by its full name.
 */
public class RestRoutineEnvironmentTest extends BaseRestTest {

    private static final String UDFS = "/api/v2/databases/re_db/schemas/s/user-defined-functions";
    private static final String PROCS = "/api/v2/databases/re_db/schemas/s/procedures";

    @BeforeAll
    public static void createRoutines() {
        sql("CREATE DATABASE re_db");
        sql("CREATE SCHEMA re_db.s");
        sql("CREATE STAGE re_db.s.st");
        sql("CREATE SECRET re_db.s.sec TYPE = GENERIC_STRING SECRET_STRING = 'v'");
        sql("CREATE FUNCTION re_db.s.fd(a NUMBER, b VARCHAR DEFAULT 'x', c NUMBER(10,2) DEFAULT 1.5, d NUMBER DEFAULT"
            + " 1 + 1, n NUMBER DEFAULT NULL) RETURNS VARCHAR AS 'b'");
        sql("CREATE PROCEDURE re_db.s.pa(a NUMBER, b VARCHAR DEFAULT 'q') RETURNS VARCHAR LANGUAGE SQL EXECUTE AS"
            + " RESTRICTED CALLER AS 'BEGIN RETURN b; END'");
        sql("CREATE FUNCTION re_db.s.fj(x NUMBER) RETURNS NUMBER LANGUAGE JAVA PACKAGES ="
            + " ('com.snowflake:snowpark:latest') HANDLER = 'H.h' TARGET_PATH = '@re_db.s.st/fj.jar' SECRETS ="
            + " ('k' = re_db.s.sec) AS $$ class H { public static long h(long x) { return x; } } $$");
        sql("CREATE FUNCTION re_db.s.fp(x NUMBER DEFAULT 7) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
            + " PACKAGES = ('numpy') HANDLER = 'h' AS $$\ndef h(x):\n    return x\n$$");
    }

    @Test
    public void anArgumentCarriesItsDefault() throws Exception {
        final JsonNode arguments = ok(get(UDFS + "/fd(NUMBER,VARCHAR,NUMBER,NUMBER,NUMBER)")).path("arguments");
        assertTrue(arguments.get(0).path("default_value").isNull(), "an argument without a default");
        assertEquals("'x'", arguments.get(1).path("default_value").asString());
        assertEquals("1.5", arguments.get(2).path("default_value").asString());
        assertEquals("1 + 1", arguments.get(3).path("default_value").asString());
        assertTrue(arguments.get(4).path("default_value").isNull(), "a default of NULL is told as none");
        JsonNode listed = null;
        for (final JsonNode routine : ok(get(UDFS + "?like=FD"))) {
            listed = routine;
        }
        assertEquals("'x'", listed.path("arguments").get(1).path("default_value").asString(), "in a listing too");
        assertEquals("7", ok(get(UDFS + "/fp(NUMBER)")).path("arguments").get(0).path("default_value").asString());
    }

    @Test
    public void aProcedureTellsItsDefaultsAndItsRights() throws Exception {
        final JsonNode procedure = ok(get(PROCS + "/pa(NUMBER,VARCHAR)"));
        assertEquals("'q'", procedure.path("arguments").get(1).path("default_value").asString());
        assertEquals("RESTRICTED CALLER", procedure.path("execute_as").asString());
    }

    @Test
    public void aJavaRoutineTellsWhereItsClassesAreKept() throws Exception {
        final JsonNode config = ok(get(UDFS + "/fj(NUMBER)")).path("language_config");
        assertEquals("@RE_DB.S.ST/fj.jar", config.path("target_path").asString());
        assertEquals("com.snowflake:snowpark:latest", config.path("packages").get(0).asString());
        assertTrue(config.path("imports").isNull(), "a Java routine without imports tells none");
        assertTrue(config.path("runtime_version").isNull());
        assertEquals("RE_DB.S.SEC", config.path("secrets").path("k").asString(), "a secret by its full name");
        assertEquals(0, config.path("external_access_integrations").size());
    }

    @Test
    public void aBodyCarriesTheSameProperties() throws Exception {
        ok(post(UDFS, "{\"name\":\"fb\",\"arguments\":[{\"name\":\"x\",\"datatype\":\"NUMBER\"}],"
            + "\"return_type\":{\"type\":\"DATATYPE\",\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":"
            + "\"JAVA\",\"handler\":\"H.h\",\"target_path\":\"@re_db.s.st/fb.jar\",\"secrets\":{\"k\":\"re_db.s.sec\"}},"
            + "\"body\":\"class H { public static long h(long x) { return x; } }\"}"));
        final JsonNode config = ok(get(UDFS + "/fb(NUMBER)")).path("language_config");
        assertEquals("@RE_DB.S.ST/fb.jar", config.path("target_path").asString());
        assertEquals("RE_DB.S.SEC", config.path("secrets").path("k").asString());
        error(400, post(UDFS, "{\"name\":\"fc\",\"arguments\":[],\"return_type\":{\"type\":\"DATATYPE\","
            + "\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":\"JAVA\",\"handler\":\"H.h\",\"secrets\":"
            + "{\"k\":\"a b; DROP\"}},\"body\":\"class H { }\"}"));
    }

    @Test
    public void aPythonRoutineTellsItsRepository() throws Exception {
        final JsonNode config = ok(get(UDFS + "/fp(NUMBER)")).path("language_config");
        assertEquals("snowflake.snowpark.pypi_shared_repository", config.path("artifact_repository").asString());
        assertEquals("numpy", config.path("artifact_repository_packages").get(0).asString());
        assertEquals(0, config.path("packages").size());
        assertTrue(config.path("imports").isArray() && config.path("imports").isEmpty(),
            "a Python routine tells an empty list of imports");
        assertEquals("3.11", config.path("runtime_version").asString());
        assertEquals(0, config.path("secrets").size());
    }
}
