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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An Iceberg table and any other table are kinds of their own: a CREATE OR REPLACE of either refuses a name the other
 * holds, as it refuses a view's, and so does a CREATE ... IF NOT EXISTS, while a transient table replaces a permanent
 * one. Each kind is altered by its own statement only: ALTER TABLE refuses an Iceberg table, and ALTER ICEBERG TABLE a
 * table, which it calls a legacy Iceberg table (live-verified).
 */
public class IcebergTableKindReplacementTest extends MeasuredCellsTestSupport {

    /** ALTER TABLE refuses an Iceberg table once its type words are read, ALTER ICEBERG TABLE refuses a table. */
    @Test
    public void eachKindIsAlteredByItsOwnStatementOnly() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE ICEBERG TABLE ri784 (o OBJECT(a INT, b INT))",
                "Table RI784 successfully created."},
            {"ALTER TABLE ri784 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT)",
                "REFUSED SQL Compilation error: The table RI784 is an Iceberg table. Iceberg tables should use ALTER "
                + "ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"ALTER TABLE ri784 ALTER COLUMN o SET DATA TYPE OBJECT(b LONG, a INT)",
                "REFUSED SQL compilation error: | Unsupported data type 'LONG'."},
            {"ALTER TABLE ri784 ALTER COLUMN o SET DATA TYPE OBJECT(b NUMBER(19,0), a INT)",
                "REFUSED SQL Compilation error: The table RI784 is an Iceberg table. Iceberg tables should use ALTER "
                + "ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"CREATE OR REPLACE TABLE re783 (o OBJECT(a INT, b INT))",
                "Table RE783 successfully created."},
            {"ALTER TABLE re783 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT)",
                "Statement executed successfully."},
            {"ALTER ICEBERG TABLE re783 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT)",
                "REFUSED SQL Compilation error: The table RE783 is a legacy Iceberg table. Legacy Iceberg tables "
                + "should use the ALTER TABLE commands. Please ensure the appropriate command format is used."},
            {"CREATE OR REPLACE TABLE rx785 (o MAP(VARCHAR, INT))",
                "Table RX785 successfully created."},
            {"CREATE OR REPLACE ICEBERG TABLE rx785 (o MAP(INT, INT))",
                "REFUSED SQL compilation error: | Object 'RX785' already exists as TABLE"},
            {"ALTER ICEBERG TABLE rx785 ALTER COLUMN o SET DATA TYPE MAP(INT, INT NOT NULL)",
                "REFUSED SQL Compilation error: The table RX785 is a legacy Iceberg table. Legacy Iceberg tables "
                + "should use the ALTER TABLE commands. Please ensure the appropriate command format is used."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO MAP(VARCHAR(16777216), NUMBER(38,0)) | );"},
        });
    }

    /** Neither kind replaces the other or a view, and IF NOT EXISTS takes neither for the other. */
    @Test
    public void anIcebergTableAndATableDoNotReplaceEachOther() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE kt786 (i INT)",
                "Table KT786 successfully created."},
            {"CREATE OR REPLACE ICEBERG TABLE kt786 (i INT)",
                "REFUSED SQL compilation error: | Object 'KT786' already exists as TABLE"},
            {"CREATE OR REPLACE ICEBERG TABLE ki786 (i INT)",
                "Table KI786 successfully created."},
            {"CREATE OR REPLACE TABLE ki786 (i INT)",
                "REFUSED SQL compilation error: | Object 'KI786' already exists as ICEBERG_TABLE"},
            {"CREATE OR REPLACE VIEW kv786 AS SELECT 1 x",
                "View KV786 successfully created."},
            {"CREATE OR REPLACE ICEBERG TABLE kv786 (i INT)",
                "REFUSED SQL compilation error: | Object 'KV786' already exists as VIEW"},
            {"CREATE ICEBERG TABLE IF NOT EXISTS kt786 (i INT)",
                "REFUSED SQL compilation error: | Object 'KT786' already exists as TABLE"},
            {"CREATE OR REPLACE TABLE kv786 (i INT)",
                "REFUSED SQL compilation error: | Object 'KV786' already exists as VIEW"},
            {"CREATE OR REPLACE TRANSIENT TABLE kt786 (i INT)",
                "Table KT786 successfully created."},
            {"CREATE OR REPLACE ICEBERG TABLE kt786 (i INT)",
                "REFUSED SQL compilation error: | Object 'KT786' already exists as TABLE"},
        });
    }
}
