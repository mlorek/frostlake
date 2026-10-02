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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A hybrid table's secondary indexes, written among its columns as {@code INDEX <name> (<column>, …)
 * [INCLUDE (<column>, …)]} — the shape GET_DDL writes a hybrid table back in. They parse and are not kept:
 * the table is stored as an ordinary one. INDEX is no keyword, so a column named INDEX keeps parsing beside
 * them, whatever its type, and only a hybrid table takes the clause.
 */
public class HybridTableIndexTest extends BaseDatabaseTest {

    /** The first cell of every row of a query, joined by commas. */
    private String firstCells(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(row.getValue(0));
        }
        return out.toString();
    }

    /** GET_DDL's spelling of a hybrid table — lower-case keywords, no space before the index's columns. */
    @Test
    public void getDdlShapeOfAHybridTableWithIndexesCreatesIt() {
        engine.execute("create or replace HYBRID TABLE MANAGEMENT_LAYER (\n"
            + "\tTENANT VARCHAR(255) NOT NULL COMMENT 'tenant',\n"
            + "\tUUID VARCHAR(50) NOT NULL,\n"
            + "\tTYPE VARCHAR(255) NOT NULL,\n"
            + "\tVALID BOOLEAN DEFAULT TRUE,\n"
            + "\tSTART_DATE DATE DEFAULT CURRENT_DATE(),\n"
            + "\tconstraint TENANT_AND_UUID primary key (TENANT, UUID),\n"
            + "\tindex INDEX_TYPE_START_DATE(TYPE, START_DATE),\n"
            + "\tindex INDEX_TYPE_VALID(TYPE, VALID) include (UUID, START_DATE)\n"
            + ")COMMENT='orchestration'");

        assertEquals("TENANT,UUID,TYPE,VALID,START_DATE", firstCells("DESCRIBE TABLE MANAGEMENT_LAYER"));
        assertEquals("MANAGEMENT_LAYER",
            engine.executeQuery("SHOW HYBRID TABLES LIKE 'MANAGEMENT_LAYER'").getRows().get(0).getValue(1));
        engine.execute("INSERT INTO MANAGEMENT_LAYER (TENANT, UUID, TYPE) VALUES ('t', 'u', 'sync')");
        assertEquals("sync", firstCells("SELECT TYPE FROM MANAGEMENT_LAYER WHERE VALID"));
    }

    /** A column named INDEX parses beside the clause, whatever its type takes in parentheses. */
    @Test
    public void aColumnNamedIndexStaysAColumn() {
        engine.execute("CREATE HYBRID TABLE h_cols (id INT PRIMARY KEY, index NUMBER(38, 0), "
            + "INDEX idx_index (index))");
        engine.execute("CREATE HYBRID TABLE h_text (id INT PRIMARY KEY, index VARCHAR(10))");
        engine.execute("CREATE HYBRID TABLE h_plain (id INT PRIMARY KEY, \"INDEX\" INT, index idx_q (\"INDEX\"))");

        assertEquals("ID,INDEX", firstCells("DESCRIBE TABLE h_cols"));
        assertEquals("ID,INDEX", firstCells("DESCRIBE TABLE h_text"));
        assertEquals("ID,INDEX", firstCells("DESCRIBE TABLE h_plain"));
    }

    /** Only a hybrid table has indexes: the clause in an ordinary table's column list is still refused. */
    @Test
    public void anOrdinaryTableRefusesAnIndex() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE t_plain (id INT, INDEX idx_id (id))");
            }
        });
    }
}
