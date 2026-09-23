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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA's LAST_DDL_BY names the USER whose DDL created a relation — {@code CURRENT_USER()}, never
 * the owning role — for a table of every kind, a view and a secure view alike, and is empty for
 * INFORMATION_SCHEMA's own views, which no statement created (live-verified).
 */
public class LastDdlByUserTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE TRANSIENT TABLE t2 (a INT)");
        engine.execute("CREATE TABLE t3 AS SELECT 1 AS a");
        engine.execute("CREATE TABLE t4 CLONE t1");
        engine.execute("CREATE VIEW v1 AS SELECT a FROM t1");
        engine.execute("CREATE SECURE VIEW v2 AS SELECT a FROM t1");
    }

    private String row(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void tablesNameTheCreatingUser() {
        assertEquals("T1, user | T2, user | T3, user | T4, user | V1, user | V2, user",
            row("SELECT table_name, IFF(last_ddl_by = CURRENT_USER(), 'user', last_ddl_by) "
                + "FROM INFORMATION_SCHEMA.TABLES WHERE table_schema = 'TEST_SCHEMA' ORDER BY table_name"));
    }

    @Test
    public void viewsNameTheCreatingUser() {
        assertEquals("V1, user | V2, user",
            row("SELECT table_name, IFF(last_ddl_by = CURRENT_USER(), 'user', last_ddl_by) "
                + "FROM INFORMATION_SCHEMA.VIEWS WHERE table_schema = 'TEST_SCHEMA' ORDER BY table_name"));
    }

    @Test
    public void informationSchemaOwnViewsNameNoUser() {
        assertEquals("0, 0",
            row("SELECT COUNT(last_ddl_by), (SELECT COUNT(last_ddl_by) FROM INFORMATION_SCHEMA.VIEWS "
                + "WHERE table_schema = 'INFORMATION_SCHEMA') FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE table_schema = 'INFORMATION_SCHEMA'"));
    }
}
