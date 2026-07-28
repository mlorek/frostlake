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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A VARIANT JSON null cast to a primitive type is SQL NULL in Snowflake — from a path access, from
 * PARSE_JSON directly, and from a VARIANT-typed column — while a plain {@code 'null'} VARCHAR keeps
 * its text. (Loaders diff semi-structured columns as {@code col::VARCHAR}; the old text-'null'
 * result made every seeded {@code PARSE_JSON('null')} column compare unequal to SQL NULL.)
 */
public class VariantJsonNullCastTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void parseJsonNullCastsToSqlNull() {
        assertNull(scalar("SELECT PARSE_JSON('null')::VARCHAR"));
        assertEquals(true, scalar("SELECT PARSE_JSON('null')::VARCHAR IS NULL"));
    }

    @Test
    public void variantColumnJsonNullCastsToSqlNull() {
        engine.execute("CREATE TABLE vjn (v VARIANT)");
        engine.execute("INSERT INTO vjn SELECT PARSE_JSON('null')");
        assertNull(scalar("SELECT v::VARCHAR FROM vjn"));
        assertEquals(true, scalar("SELECT v::VARCHAR IS NULL FROM vjn"));
    }

    @Test
    public void plainVarcharNullTextKeepsItsText() {
        assertEquals("null", scalar("SELECT 'null'::VARCHAR"));
        engine.execute("CREATE TABLE vtx (s VARCHAR)");
        engine.execute("INSERT INTO vtx VALUES ('null')");
        assertEquals("null", scalar("SELECT s::VARCHAR FROM vtx"));
    }

    @Test
    public void pathAccessJsonNullStillCastsToSqlNull() {
        assertNull(scalar("SELECT PARSE_JSON('{\"a\": null}'):a::VARCHAR"));
    }

    @Test
    public void castToVariantKeepsTheJsonNull() {
        assertEquals(false, scalar("SELECT (PARSE_JSON('null')::VARIANT) IS NULL"));
    }
}
