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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW SCHEMAS takes its database scope with or without the DATABASE keyword — {@code IN db} lists the
 * database's schemas as {@code IN DATABASE db} does, beside LIKE, TERSE, STARTS WITH and LIMIT — and a bare
 * {@code IN DATABASE} lists the current database's. A name that is no database — a missing one, a quoted
 * name in the wrong case, a schema's, or one with more parts — is refused as no object, and a SCHEMA or
 * TABLE scope holds no schemas at all. Every cell is live-verified.
 */
public class ShowSchemasScopeTest extends BaseDatabaseTest {

    private static final String DB = "FL_SHOW_SCHEMAS_SCOPE";

    @BeforeEach
    public void createDatabase() {
        engine.execute("CREATE OR REPLACE DATABASE " + DB);
        engine.execute("CREATE OR REPLACE SCHEMA " + DB + ".s1");
        engine.execute("USE SCHEMA " + DB + ".PUBLIC");
    }

    @AfterEach
    public void dropDatabase() {
        engine.execute("DROP DATABASE IF EXISTS " + DB);
    }

    /** The listed schema names, sorted and comma-separated, or the refusal on one line. */
    private String names(final String sql) {
        try {
            final List<String> listed = new ArrayList<>();
            final ResultSet rs = engine.executeQuery(sql);
            int name = 0;
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if ("name".equals(rs.getColumns().get(i).getName())) {
                    name = i;
                }
            }
            for (final Row row : rs.getRows()) {
                listed.add(String.valueOf(row.getValue(name)));
            }
            Collections.sort(listed);
            return String.join(",", listed);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aDatabaseScopeNeedsNoKeyword() {
        final String all = "INFORMATION_SCHEMA,PUBLIC,S1";
        assertEquals(all, names("SHOW SCHEMAS IN " + DB));
        assertEquals(all, names("SHOW SCHEMAS IN \"" + DB + "\""));
        assertEquals(all, names("SHOW SCHEMAS IN DATABASE " + DB));
        assertEquals(all, names("SHOW TERSE SCHEMAS IN " + DB));
        assertEquals(all, names("SHOW SCHEMAS IN DATABASE"));
        assertEquals("PUBLIC", names("SHOW SCHEMAS LIKE 'P%' IN " + DB));
        assertEquals("S1", names("SHOW SCHEMAS IN " + DB + " STARTS WITH 'S'"));
        assertEquals("INFORMATION_SCHEMA", names("SHOW SCHEMAS IN " + DB + " LIMIT 1"));
        assertEquals("S1", names("SHOW SCHEMAS LIKE 'S%' IN " + DB + " STARTS WITH 'S' LIMIT 5"));
    }

    @Test
    public void aNameThatIsNoDatabaseIsRefused() {
        final String noObject = "SQL compilation error:|Object does not exist, or operation cannot be performed.";
        final String[] refused = {
            "SHOW SCHEMAS IN nosuch_show_db", "SHOW SCHEMAS IN " + DB + ".public", "SHOW SCHEMAS IN " + DB + ".public.x",
            "SHOW SCHEMAS IN \"fl_show_schemas_scope\"", "SHOW SCHEMAS IN information_schema", "SHOW SCHEMAS IN public",
            "SHOW SCHEMAS IN DATABASE nosuch_show_db", "SHOW SCHEMAS IN DATABASE " + DB + ".public",
        };
        for (final String sql : refused) {
            assertEquals(noObject, names(sql), sql);
        }
        final String inSchema = "SQL compilation error:|Unsupported statement type 'Cannot show objects of type SCHEMA in SCHEMA'.";
        assertEquals(inSchema, names("SHOW SCHEMAS IN SCHEMA nosuch_show_db"));
        assertEquals(inSchema, names("SHOW SCHEMAS IN SCHEMA " + DB + ".public"));
        assertEquals(inSchema, names("SHOW TERSE SCHEMAS IN SCHEMA " + DB + ".public"));
        assertEquals(inSchema, names("SHOW SCHEMAS IN SCHEMA"));
        assertEquals("SQL compilation error:|Unsupported statement type 'Cannot show objects of type SCHEMA in TABLE'.",
            names("SHOW SCHEMAS IN TABLE " + DB));
        assertEquals("SQL compilation error:|syntax error line 1 at position 38 unexpected 'LIKE'.",
            names("SHOW SCHEMAS IN " + DB + " LIKE 'P%'"));
    }
}
