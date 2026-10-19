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
 * A plain table refused the parameters of another kind of table (live-verified): an Iceberg table's catalog,
 * external volume, catalog sync, storage serialization policy and target file size, a catalog-linked database's
 * PURGE_ON_DROP_TABLE, an external table's REPLACE_INVALID_CHARACTERS, a staging format no format answers; a clone
 * refused a clustering key; a view tracking the changes of a query that reads no stored relation.
 */
public class PlainTableParameterTest extends MeasuredCellsTestSupport {

    @Test
    public void aPlainTableIsRefusedAnotherKindsParameters() {
        engine.execute("CREATE OR REPLACE TABLE tq_src (a INT, b INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tq2 (a INT) CATALOG = ''",
                "REFUSED SQL compilation error: Iceberg tables require a catalog integration to be specified"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) CATALOG = 'SNOWFLAKE'",
                "REFUSED Catalog integration cannot be set for legacy iceberg tables"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) EXTERNAL_VOLUME = ''",
                "REFUSED Iceberg table null must have the table parameter EXTERNAL_VOLUME defined on the table, "
                + "schema, database, or account."},
            {"CREATE OR REPLACE TABLE tq2 (a INT) EXTERNAL_VOLUME = 'nosuch_vol'",
                "REFUSED EXTERNAL_VOLUME is not supported on table TQ2."},
            {"CREATE OR REPLACE TABLE tq2 (a INT) PURGE_ON_DROP_TABLE = TRUE",
                "REFUSED SQL compilation error: | invalid parameter 'PURGE_ON_DROP_TABLE can only be set on tables "
                + "in a catalog-linked database'"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) REPLACE_INVALID_CHARACTERS = TRUE",
                "REFUSED SQL compilation error: | invalid type of property 'REPLACE_INVALID_CHARACTERS' for 'TABLE'"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) STORAGE_SERIALIZATION_POLICY = 'COMPATIBLE'",
                "REFUSED SQL Compilation error: | Setting storage serialization policy is only supported for "
                + "Iceberg tables using Snowflake catalog."},
            {"CREATE OR REPLACE TABLE tq2 (a INT) TARGET_FILE_SIZE = 'AUTO'",
                "REFUSED SQL Compilation error: | Setting target file size is only supported for Iceberg tables."},
            {"CREATE OR REPLACE TABLE tq2 (a INT) STAGE_FILE_FORMAT = 1",
                "REFUSED SQL compilation error: | File format '\"1\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"1\"."},
            {"CREATE OR REPLACE TABLE tq2 (a INT) COPY = TRUE",
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'COPY'"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) COPY = 'x'",
                "REFUSED SQL compilation error: | invalid type of property ''x'' for 'COPY'"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) CHANGE_TRACKING = TRUE", "Table TQ2 successfully created."},
            {"CREATE OR REPLACE TABLE tq2 (a INT) BASE_LOCATION = 'x'",
                "REFUSED SQL compilation error: | Iceberg table options (e.g. BASE_LOCATION) require "
                + "DEFAULT_METADATA_WRITE_FORMAT=ICEBERG or CREATE ICEBERG TABLE"},
            {"CREATE OR REPLACE TABLE tq2 (a INT) CATALOG_SYNC = 'x'",
                "REFUSED Catalog integration x is not valid. Please ensure required properties are valid."},
            {"CREATE OR REPLACE TABLE tq3 CLONE tq_src CLUSTER BY (a)", "REFUSED Invalid clustering key A | "},
            {"CREATE OR REPLACE TABLE tq4 LIKE tq_src CLUSTER BY (a)", "Table TQ4 successfully created."},
            {"CREATE OR REPLACE VIEW v_ct CHANGE_TRACKING = TRUE AS SELECT 1 a",
                "REFUSED SQL compilation error: line 1 at position 54: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW v_ct2 CHANGE_TRACKING = TRUE AS SELECT a FROM tq_src",
                "View V_CT2 successfully created."},
            {"CREATE OR REPLACE VIEW v_ct3 CHANGE_TRACKING = FALSE AS SELECT 1 a", "View V_CT3 successfully created."},
        });
    }
}
