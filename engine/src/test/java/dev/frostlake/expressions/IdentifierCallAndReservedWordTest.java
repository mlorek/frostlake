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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Dynamic-name and keyword handling: {@code IDENTIFIER('name')(args)} / {@code IDENTIFIER($var)(args)}
 * as a dynamically named FUNCTION call (resolved per evaluation, so cached expression ASTs stay
 * correct across sessions), non-reserved keywords staying usable as identifiers, and the reserved
 * words {@code GROUP} and {@code TABLE} being rejected as bare identifiers (live-verified).
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
    public void groupIsRejectedAsBareIdentifier() {
        // GROUP is reserved (live-verified): it cannot be used as a bare column name.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE gwords (id INTEGER, group VARCHAR)");
            }
        });
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
        // Live-verified: a table must say what its columns ARE. A body-less `CREATE TABLE t`, or one
        // carrying only tail options (`TAG (…)`, `CLUSTER BY (…)`), is a syntax error — the shape has to
        // come from a column list, CTAS, LIKE or CLONE.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bare_none");
            }
        });
        // A TAG (…) clause also requires the tag to exist — live-verified, an unknown tag
        // fails "Tag 'KEY1' does not exist or not authorized." Create them so the shape, not the tag
        // reference, is what these assertions are about.
        engine.execute("CREATE TAG IF NOT EXISTS key1");
        engine.execute("CREATE TAG IF NOT EXISTS key2");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bare_tagged TAG (key1='value_1', key2='value_2')");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bare_clustered CLUSTER BY (n1, n2)");
            }
        });
        // With a column list the very same tail options parse.
        engine.execute("CREATE TABLE tagged (id INTEGER) TAG (key1='value_1', key2='value_2')");
        engine.execute("CREATE TABLE clustered (n1 INTEGER, n2 INTEGER) CLUSTER BY (n1, n2)");
        engine.execute("ALTER TABLE tagged ADD extra INTEGER");
        engine.execute("INSERT INTO tagged VALUES (4, 5)");
        assertEquals(4L, ((Number) scalar("SELECT id FROM tagged")).longValue());
    }

    @Test
    public void tableIsRejectedAsAQualifiedNamePart() {
        // TABLE is reserved (live-verified): it cannot be the final part of a qualified table name.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test_db.test_schema.table (id INTEGER)");
            }
        });
    }
}
