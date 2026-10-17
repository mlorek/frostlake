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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * GET_DDL('ICEBERG_TABLE', …) looks the name up as a table: over a plain table it answers the table's DDL, and a
 * missing one is refused as an Iceberg table named in full, {@code Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does
 * not exist or not authorized.}, the type spelled in any case and with a space (live-verified).
 */
public class GetDdlIcebergTableTest extends MeasuredCellsTestSupport {

    @Test
    public void anIcebergTableIsLookedUpAsATable() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE lo (o OBJECT, a ARRAY, v VARIANT, bn BINARY)");
        assertCells(new String[][] {
            {"SELECT GET_DDL('ICEBERG_TABLE', 'nosuch_ice')",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"SELECT GET_DDL('ICEBERG_TABLE', 'fz')",
                "create or replace TABLE FZ ( | \tID NUMBER(38,0), | \tB BOOLEAN | );"},
            {"SELECT GET_DDL('ICEBERG_TABLE', 'test_schema.nosuch_ice')",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"SELECT GET_DDL('ICEBERG_TABLE', '\"Nosuch\"')",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.\"Nosuch\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"Nosuch\"."},
            {"SELECT GET_DDL('iceberg_table', 'nosuch_ice')",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"SELECT GET_DDL('ICEBERG TABLE', 'nosuch_ice')",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"SELECT GET_DDL('ICEBERG_TABLE', 'test_db.test_schema.nosuch_ice')",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"SELECT GET_DDL('ICEBERG_TABLE', 'nosuch_ice', TRUE)",
                "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"SELECT GET_DDL('ICEBERG_TABLE', 'nosuch_db.public.x')",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB."},
        });
    }
}
