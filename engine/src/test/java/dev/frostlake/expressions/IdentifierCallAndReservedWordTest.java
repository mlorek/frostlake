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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two leniency/dynamic-name features: {@code IDENTIFIER('name')(args)} / {@code IDENTIFIER($var)(args)}
 * as a dynamically named FUNCTION call (resolved per evaluation, so cached expression ASTs stay
 * correct across sessions), and the reserved words {@code group} (expression positions) and a
 * trailing {@code table} part in qualified names ({@code db.schema.table}).
 */
public class IdentifierCallAndReservedWordTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void identifierAsAFunctionName() {
        engine.execute("CREATE OR REPLACE FUNCTION add_it(a INTEGER, b INTEGER) RETURNS INTEGER AS $$ a + b $$");
        engine.execute("CREATE OR REPLACE FUNCTION speed_of_light() RETURNS INTEGER AS $$ 299792458 $$");
        assertEquals(3L, ((Number) scalar("SELECT IDENTIFIER('add_it')(1, 2)")).longValue());
        assertEquals(299792458L, ((Number) scalar("SELECT IDENTIFIER('speed_of_light')()")).longValue());

        engine.execute("SET my_function_name = 'speed_of_light'");
        assertEquals(299792458L, ((Number) scalar("SELECT IDENTIFIER($my_function_name)()")).longValue());
    }

    @Test
    public void groupIsUsableInExpressionPositions() {
        engine.execute("CREATE TABLE gwords (id INTEGER, group VARCHAR)");
        engine.execute("INSERT INTO gwords VALUES (1, 'alpha'), (2, 'beta')");
        assertEquals("alpha", scalar("SELECT group FROM gwords WHERE id = 1"));
        assertEquals("ALPHA", scalar("SELECT UPPER(group) FROM gwords WHERE id = 1"),
            "group works as a function argument");
        // GROUP BY itself must be untouched by the leniency.
        assertEquals(2L, ((Number) scalar("SELECT COUNT(*) FROM (SELECT group FROM gwords GROUP BY group)")).longValue());
    }

    @Test
    public void keywordAliasesAndIdentifiers() {
        engine.execute("CREATE TABLE kw (session VARCHAR, number INTEGER, rename VARCHAR, replace VARCHAR)");
        engine.execute("INSERT INTO kw VALUES ('s', 7, 'rn', 'rp')");
        assertEquals("s", scalar("SELECT session FROM kw"));
        assertEquals(7L, ((Number) scalar("SELECT number FROM kw")).longValue());
        assertEquals("rn", scalar("SELECT rename FROM kw"));
        final ResultSet two = engine.executeQuery("SELECT rename, replace FROM kw");
        assertEquals(2, two.getColumns().size());
        assertEquals(1L, ((Number) scalar("SELECT 1 put")).longValue(), "put works as a bare alias");
        // The anchored uses stay intact.
        assertEquals("xb", scalar("SELECT REPLACE('ab', 'a', 'x')"));
        engine.execute("CREATE OR REPLACE TABLE kw2 (n NUMBER(10, 2))");
    }

    @Test
    public void getFunctionAndBodylessCreateTable() {
        assertEquals("1", String.valueOf(scalar("SELECT GET(PARSE_JSON('{\"a\": 1}'), 'a')")));
        // Body-less CREATE TABLE (only tail options) makes an empty table; columns arrive via ALTER.
        engine.execute("CREATE TABLE bare_tagged TAG (key1='value_1', key2='value_2')");
        engine.execute("CREATE TABLE bare_clustered CLUSTER BY (n1, n2)");
        engine.execute("ALTER TABLE bare_tagged ADD id INTEGER");
        engine.execute("INSERT INTO bare_tagged VALUES (4)");
        assertEquals(4L, ((Number) scalar("SELECT id FROM bare_tagged")).longValue());
    }

    @Test
    public void tableAsTheFinalQualifiedNamePart() {
        engine.execute("CREATE TABLE test_db.test_schema.table (id INTEGER)");
        engine.execute("INSERT INTO test_db.test_schema.table VALUES (5)");
        assertEquals(5L, ((Number) scalar("SELECT id FROM test_db.test_schema.table")).longValue());
        assertEquals(1, engine.executeQuery("DESCRIBE TABLE test_db.test_schema.table").getRowCount());
    }
}
