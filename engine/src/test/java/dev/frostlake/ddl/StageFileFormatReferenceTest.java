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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class StageFileFormatReferenceTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aStagesFileFormatMustNameAFormatThatExists() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P474_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            engine.execute("CREATE OR REPLACE FILE FORMAT realff TYPE = CSV");
            assertRefused("CREATE STAGE s1 FILE_FORMAT = nosuchformat",
                "SQL compilation error:\nFile format 'NOSUCHFORMAT' does not exist or not authorized.");
            assertRefused("CREATE STAGE s2 FILE_FORMAT = 'nosuchformat'",
                "SQL compilation error:\nFile format 'NOSUCHFORMAT' does not exist or not authorized.");
            assertRefused("CREATE STAGE s3 FILE_FORMAT = (FORMAT_NAME = 'nosuchformat')",
                "SQL compilation error:\nFile format 'NOSUCHFORMAT' does not exist or not authorized.");
            assertRefused("CREATE STAGE s4 FILE_FORMAT = P474_DB.PUBLIC.nosuchformat",
                "SQL compilation error:\nFile format 'P474_DB.PUBLIC.NOSUCHFORMAT' does not exist or not authorized.");
            engine.execute("CREATE STAGE s5 FILE_FORMAT = realff");
            assertRefused("CREATE STAGE IF NOT EXISTS s5 FILE_FORMAT = nosuchformat",
                "SQL compilation error:\nFile format 'NOSUCHFORMAT' does not exist or not authorized.");
            assertRefused("ALTER STAGE s5 SET FILE_FORMAT = nosuchformat",
                "SQL compilation error:\nFile format 'NOSUCHFORMAT' does not exist or not authorized.");
            assertRefused("COPY INTO t FROM @s5 FILE_FORMAT = (FORMAT_NAME = 'nosuchformat')",
                "SQL compilation error:\nFile format 'NOSUCHFORMAT' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P474_DB");
        }
    }

    @Test
    public void aBareFileFormatValueIsAName() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P474D_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            assertRefused("CREATE STAGE sx FILE_FORMAT = CSV",
                "SQL compilation error:\nFile format 'CSV' does not exist or not authorized.");
            engine.execute("CREATE STAGE sy FILE_FORMAT = (TYPE = CSV)");
            assertRefused("CREATE STAGE sz FILE_FORMAT = 'CSV'",
                "SQL compilation error:\nFile format 'CSV' does not exist or not authorized.");
            engine.execute("CREATE OR REPLACE FILE FORMAT realff TYPE = CSV");
            engine.execute("CREATE STAGE sw FILE_FORMAT = realff");
            engine.execute("ALTER STAGE sw SET FILE_FORMAT = (TYPE = JSON)");
            engine.execute("COPY INTO t FROM @sw FILE_FORMAT = (TYPE = CSV)");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P474D_DB");
        }
    }
}
