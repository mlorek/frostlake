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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A RECURSIVE VIEW whose body unions an anchor with a branch that reads the view itself is created and read
 * recursively; its DDL shows the body as written. Without a UNION, or without RECURSIVE, the body names a view that
 * does not exist yet and is refused.
 */
public class RecursiveViewTest extends BaseDatabaseTest {

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() > 0 ? ";" : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aRecursiveViewReadsItselfToAFixedPoint() {
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW uv5 (n) AS SELECT 1 UNION ALL SELECT n + 1 FROM uv5 WHERE n < 3");
        assertEquals("1;2;3", rows("SELECT * FROM uv5 ORDER BY n"));
        assertEquals("3", rows("SELECT COUNT(*) FROM uv5 a JOIN uv5 b ON a.n = b.n"));
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW uv7 (n) AS SELECT 1 UNION SELECT n + 1 FROM uv7 WHERE n < 3");
        assertEquals("1;2;3", rows("SELECT * FROM uv7 ORDER BY n"));
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW uv8 AS SELECT 1 AS n UNION ALL SELECT n + 1 FROM uv8 WHERE n < 3");
        assertEquals("1;2;3", rows("SELECT * FROM uv8 ORDER BY n"));
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW uv10 (s, d) AS SELECT 'a', 1 UNION ALL "
            + "SELECT s || 'a', d + 1 FROM uv10 WHERE d < 3");
        assertEquals("a|1;aa|2;aaa|3", rows("SELECT * FROM uv10 ORDER BY d"));
    }

    @Test
    public void itsDdlShowsTheBodyAsWritten() {
        final String written = "CREATE OR REPLACE RECURSIVE VIEW uv5 (n) AS SELECT 1 UNION ALL SELECT n + 1 FROM uv5 WHERE n < 3";
        engine.execute(written);
        final ResultSet shown = engine.executeQuery("SHOW VIEWS LIKE 'UV5'");
        assertEquals(written, String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("text"))));
        assertEquals("create or replace recursive view UV5(\n\tN\n) as SELECT 1 UNION ALL SELECT n + 1 FROM uv5 WHERE n < 3;",
            rows("SELECT GET_DDL('VIEW', 'UV5')"));
    }

    @Test
    public void aSelfReferenceWithoutARecursiveUnionNamesNothing() {
        assertTrue(refusal("CREATE OR REPLACE RECURSIVE VIEW uv6 (n) AS SELECT n FROM uv6").startsWith(
            "SQL compilation error:\nObject 'TEST_DB.TEST_SCHEMA.UV6' does not exist or not authorized."));
        assertTrue(refusal("CREATE OR REPLACE VIEW uv9 (n) AS SELECT 1 UNION ALL SELECT n + 1 FROM uv9 WHERE n < 3")
            .startsWith("SQL compilation error:\nObject 'TEST_DB.TEST_SCHEMA.UV9' does not exist or not authorized."));
    }
}
