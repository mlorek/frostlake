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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * INFORMATION_SCHEMA's own views as SHOW COLUMNS and INFORMATION_SCHEMA.COLUMNS list them: every view's columns
 * with their types, nullability and descriptions, of kind VIEW_COLUMN in SHOW COLUMNS (all live-verified).
 */
public class InformationSchemaOwnColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void ownViewColumns() {
        assertAnswer("COMMENT|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,"
                + "\"fixed\":false}|true|VIEW_COLUMN ;; CREATED|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,"
                + "\"scale\":9,\"nullable\":true}|true|VIEW_COLUMN ;; CYCLE_OPTION|{\"type\":\"TEXT\",\"length\":3,"
                + "\"byteLength\":12,\"nullable\":true,\"fixed\":false}|true|VIEW_COLUMN ;; DATA_TYPE|"
                + "{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":false,\"fixed\":false}|"
                + "NOT_NULL|VIEW_COLUMN ;; INCREMENT|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false}|true|VIEW_COLUMN ;; LAST_ALTERED|{\"type\":\"TIMESTAMP_LTZ\","
                + "\"precision\":0,\"scale\":9,\"nullable\":true}|true|VIEW_COLUMN ;; MAXIMUM_VALUE|"
                + "{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false}|"
                + "true|VIEW_COLUMN ;; MINIMUM_VALUE|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false}|true|VIEW_COLUMN ;; NEXT_VALUE|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false}|true|VIEW_COLUMN ;; "
                + "NUMERIC_PRECISION|{\"type\":\"FIXED\",\"precision\":9,\"scale\":0,\"nullable\":false}|NOT_NULL|"
                + "VIEW_COLUMN ;; NUMERIC_PRECISION_RADIX|{\"type\":\"FIXED\",\"precision\":9,\"scale\":0,"
                + "\"nullable\":false}|NOT_NULL|VIEW_COLUMN ;; NUMERIC_SCALE|{\"type\":\"FIXED\",\"precision\":9,"
                + "\"scale\":0,\"nullable\":false}|NOT_NULL|VIEW_COLUMN ;; ORDERED|{\"type\":\"TEXT\",\"length\":3,"
                + "\"byteLength\":12,\"nullable\":true,\"fixed\":false}|true|VIEW_COLUMN ;; SEQUENCE_CATALOG|"
                + "{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":false,\"fixed\":false}|"
                + "NOT_NULL|VIEW_COLUMN ;; SEQUENCE_NAME|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":false,\"fixed\":false}|NOT_NULL|VIEW_COLUMN ;; SEQUENCE_OWNER|"
                + "{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":false,\"fixed\":false}|"
                + "NOT_NULL|VIEW_COLUMN ;; SEQUENCE_SCHEMA|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":false,\"fixed\":false}|NOT_NULL|VIEW_COLUMN ;; START_VALUE|"
                + "{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false}|"
                + "true|VIEW_COLUMN",
            "SHOW COLUMNS IN VIEW test_db.INFORMATION_SCHEMA.SEQUENCES ->> SELECT \"column_name\", \"data_type\", "
                + "\"null?\", \"kind\" FROM $1 ORDER BY 1");
        assertAnswer("18", "SHOW COLUMNS IN VIEW test_db.INFORMATION_SCHEMA.SEQUENCES ->> SELECT COUNT(*) FROM $1");
        assertAnswer("SEQUENCES|SEQUENCE_CATALOG|TEXT|NO|1 ;; SEQUENCES|SEQUENCE_SCHEMA|TEXT|NO|2 ;; SEQUENCES|"
                + "SEQUENCE_NAME|TEXT|NO|3 ;; SEQUENCES|SEQUENCE_OWNER|TEXT|NO|4 ;; SEQUENCES|DATA_TYPE|TEXT|NO|5 ;; "
                + "SEQUENCES|NUMERIC_PRECISION|NUMBER|NO|6 ;; SEQUENCES|NUMERIC_PRECISION_RADIX|NUMBER|NO|7 ;; "
                + "SEQUENCES|NUMERIC_SCALE|NUMBER|NO|8 ;; SEQUENCES|START_VALUE|TEXT|YES|9 ;; SEQUENCES|MINIMUM_VALUE|"
                + "TEXT|YES|10 ;; SEQUENCES|MAXIMUM_VALUE|TEXT|YES|11 ;; SEQUENCES|NEXT_VALUE|TEXT|YES|12 ;; "
                + "SEQUENCES|INCREMENT|TEXT|YES|13 ;; SEQUENCES|CYCLE_OPTION|TEXT|YES|14 ;; SEQUENCES|CREATED|"
                + "TIMESTAMP_LTZ|YES|15 ;; SEQUENCES|LAST_ALTERED|TIMESTAMP_LTZ|YES|16 ;; SEQUENCES|ORDERED|TEXT|YES|"
                + "17 ;; SEQUENCES|COMMENT|TEXT|YES|18",
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, ORDINAL_POSITION FROM "
                + "test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = "
                + "'SEQUENCES' ORDER BY ORDINAL_POSITION");
        assertAnswer("DATABASES|DATABASE_NAME|TEXT|NO|1 ;; DATABASES|DATABASE_OWNER|TEXT|NO|2 ;; DATABASES|"
                + "IS_TRANSIENT|TEXT|YES|3 ;; DATABASES|COMMENT|TEXT|YES|4 ;; DATABASES|CREATED|TIMESTAMP_LTZ|YES|5 "
                + ";; DATABASES|LAST_ALTERED|TIMESTAMP_LTZ|YES|6 ;; DATABASES|RETENTION_TIME|NUMBER|YES|7 ;; "
                + "DATABASES|TYPE|TEXT|NO|8 ;; DATABASES|REPLICABLE_WITH_FAILOVER_GROUPS|TEXT|YES|9 ;; DATABASES|"
                + "OWNER_ROLE_TYPE|TEXT|YES|10",
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, ORDINAL_POSITION FROM "
                + "test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = "
                + "'DATABASES' ORDER BY ORDINAL_POSITION");
        assertAnswer("SCHEMATA|CATALOG_NAME|TEXT|NO|1 ;; SCHEMATA|SCHEMA_NAME|TEXT|NO|2 ;; SCHEMATA|SCHEMA_OWNER|TEXT|"
                + "NO|3 ;; SCHEMATA|IS_TRANSIENT|TEXT|YES|4 ;; SCHEMATA|IS_MANAGED_ACCESS|TEXT|YES|5 ;; SCHEMATA|"
                + "RETENTION_TIME|NUMBER|YES|6 ;; SCHEMATA|DEFAULT_CHARACTER_SET_CATALOG|TEXT|YES|7 ;; SCHEMATA|"
                + "DEFAULT_CHARACTER_SET_SCHEMA|TEXT|YES|8 ;; SCHEMATA|DEFAULT_CHARACTER_SET_NAME|TEXT|YES|9 ;; "
                + "SCHEMATA|SQL_PATH|TEXT|YES|10 ;; SCHEMATA|CREATED|TIMESTAMP_LTZ|YES|11 ;; SCHEMATA|LAST_ALTERED|"
                + "TIMESTAMP_LTZ|YES|12 ;; SCHEMATA|COMMENT|TEXT|YES|13 ;; SCHEMATA|REPLICABLE_WITH_FAILOVER_GROUPS|"
                + "TEXT|YES|14 ;; SCHEMATA|OWNER_ROLE_TYPE|TEXT|YES|15",
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, ORDINAL_POSITION FROM "
                + "test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = "
                + "'SCHEMATA' ORDER BY ORDINAL_POSITION");
        assertAnswer("VIEWS|TABLE_CATALOG|TEXT|NO|1 ;; VIEWS|TABLE_SCHEMA|TEXT|NO|2 ;; VIEWS|TABLE_NAME|TEXT|NO|3 ;; "
                + "VIEWS|TABLE_OWNER|TEXT|NO|4 ;; VIEWS|VIEW_DEFINITION|TEXT|NO|5 ;; VIEWS|CHECK_OPTION|TEXT|YES|6 ;; "
                + "VIEWS|IS_UPDATABLE|TEXT|YES|7 ;; VIEWS|INSERTABLE_INTO|TEXT|YES|8 ;; VIEWS|IS_SECURE|TEXT|YES|9 ;; "
                + "VIEWS|CREATED|TIMESTAMP_LTZ|YES|10 ;; VIEWS|LAST_ALTERED|TIMESTAMP_LTZ|YES|11 ;; VIEWS|LAST_DDL|"
                + "TIMESTAMP_LTZ|YES|12 ;; VIEWS|LAST_DDL_BY|TEXT|YES|13 ;; VIEWS|COMMENT|TEXT|YES|14",
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, ORDINAL_POSITION FROM "
                + "test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = "
                + "'VIEWS' ORDER BY ORDINAL_POSITION");
        assertAnswer("STAGES|STAGE_CATALOG|TEXT|NO|1 ;; STAGES|STAGE_SCHEMA|TEXT|NO|2 ;; STAGES|STAGE_NAME|TEXT|NO|3 "
                + ";; STAGES|STAGE_URL|TEXT|YES|4 ;; STAGES|STAGE_REGION|TEXT|YES|5 ;; STAGES|STAGE_TYPE|TEXT|YES|6 "
                + ";; STAGES|STAGE_OWNER|TEXT|YES|7 ;; STAGES|COMMENT|TEXT|YES|8 ;; STAGES|CREATED|TIMESTAMP_LTZ|YES|"
                + "9 ;; STAGES|LAST_ALTERED|TIMESTAMP_LTZ|YES|10 ;; STAGES|ENDPOINT|TEXT|YES|11 ;; STAGES|"
                + "DIRECTORY_ENABLED|BOOLEAN|YES|12",
            "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, ORDINAL_POSITION FROM "
                + "test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = "
                + "'STAGES' ORDER BY ORDINAL_POSITION");
        assertAnswer("true|true",
            "SELECT COUNT(*) > 800, COUNT(DISTINCT TABLE_NAME) > 50 FROM test_db.INFORMATION_SCHEMA.COLUMNS WHERE "
                + "TABLE_SCHEMA = 'INFORMATION_SCHEMA'");
        assertAnswer("16777216|null|null|true ;; 16777216|null|null|true ;; 16777216|null|null|true ;; 16777216|null|"
                + "null|true ;; 16777216|null|null|true ;; null|9|0|true ;; null|9|0|true ;; null|9|0|true ;; "
                + "16777216|null|null|true ;; 16777216|null|null|true ;; 16777216|null|null|true ;; 16777216|null|"
                + "null|true ;; 16777216|null|null|true ;; 3|null|null|true ;; null|null|null|true ;; null|null|null|"
                + "true ;; 3|null|null|true ;; 16777216|null|null|true",
            "SELECT CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, COMMENT IS NOT NULL FROM "
                + "test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = "
                + "'SEQUENCES' ORDER BY ORDINAL_POSITION");
        assertAnswer("TEST_DB|INFORMATION_SCHEMA",
            "SELECT TABLE_CATALOG, TABLE_SCHEMA FROM test_db.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = "
                + "'INFORMATION_SCHEMA' AND TABLE_NAME = 'SEQUENCES' AND ORDINAL_POSITION = 1");
        assertAnswer("true", "SHOW COLUMNS IN SCHEMA test_db.INFORMATION_SCHEMA ->> SELECT COUNT(*) > 800 FROM $1");
        assertAnswer("SEQUENCES|SEQUENCE_NAME",
            "SHOW COLUMNS LIKE 'SEQUENCE_NAME' IN SCHEMA test_db.INFORMATION_SCHEMA ->> SELECT \"table_name\", "
                + "\"column_name\" FROM $1 ORDER BY 1");
        assertAnswer("SEQUENCE_CATALOG|VARCHAR(16777216)|N ;; SEQUENCE_SCHEMA|VARCHAR(16777216)|N ;; SEQUENCE_NAME|"
                + "VARCHAR(16777216)|N ;; SEQUENCE_OWNER|VARCHAR(16777216)|N ;; DATA_TYPE|VARCHAR(16777216)|N ;; "
                + "NUMERIC_PRECISION|NUMBER(9,0)|N ;; NUMERIC_PRECISION_RADIX|NUMBER(9,0)|N ;; NUMERIC_SCALE|NUMBER(9,"
                + "0)|N ;; START_VALUE|VARCHAR(16777216)|Y ;; MINIMUM_VALUE|VARCHAR(16777216)|Y ;; MAXIMUM_VALUE|"
                + "VARCHAR(16777216)|Y ;; NEXT_VALUE|VARCHAR(16777216)|Y ;; INCREMENT|VARCHAR(16777216)|Y ;; "
                + "CYCLE_OPTION|VARCHAR(3)|Y ;; CREATED|TIMESTAMP_LTZ(9)|Y ;; LAST_ALTERED|TIMESTAMP_LTZ(9)|Y ;; "
                + "ORDERED|VARCHAR(3)|Y ;; COMMENT|VARCHAR(16777216)|Y",
            "DESCRIBE VIEW test_db.INFORMATION_SCHEMA.SEQUENCES ->> SELECT \"name\", \"type\", \"null?\" FROM $1");
    }
}
