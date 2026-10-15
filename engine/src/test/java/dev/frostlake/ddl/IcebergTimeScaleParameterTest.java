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
 * The one time scale an Iceberg table keeps, whatever the session says. ICEBERG_TIMESTAMP_DEFAULT_PRECISION is
 * a session parameter of two values, 6 and 9, and an Iceberg table's TIME and TIMESTAMP columns are scale 6
 * under either: an unsized one is created at 6 and a 9 written is refused for its scale, in a column list, in
 * a query's result and in an added column. DESCRIBE shows an Iceberg table's columns with a name mapping
 * beside the write default. A parameter a session keeps is no property of the table: {@code cannot set
 * parameter 'TIMEZONE' at 'ICEBERG_TABLE'}. Every answer is live-verified.
 */
public class IcebergTimeScaleParameterTest extends MeasuredCellsTestSupport {

    @Test
    public void theScaleIsSixUnderEitherValue() {
        try {
            answer(
                "CREATE OR REPLACE ICEBERG TABLE nf_ice (a INT)");
            assertAnswer(
                "ICEBERG_TIMESTAMP_DEFAULT_PRECISION|6|6||Default precision for iceberg timestamps when no "
                    + "precision is specified. Valid values are 6 (microseconds) and 9 (nanoseconds).|NUMBER",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION'");
            assertAnswer(
                "ICEBERG_TIMESTAMP_DEFAULT_PRECISION|6|6||Default precision for iceberg timestamps when no "
                    + "precision is specified. Valid values are 6 (microseconds) and 9 (nanoseconds).|NUMBER",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' IN ACCOUNT");
            assertAnswer(
                "",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' IN DATABASE test_db");
            assertAnswer(
                "",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' IN TABLE nf_ice");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 9");
            assertAnswer(
                "ICEBERG_TIMESTAMP_DEFAULT_PRECISION|9|6|SESSION|Default precision for iceberg timestamps when no "
                    + "precision is specified. Valid values are 6 (microseconds) and 9 (nanoseconds).|NUMBER",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION'");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'E' with data type "
                    + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9 (a TIMESTAMP_NTZ, b TIMESTAMP_LTZ, c TIME, d "
                    + "TIMESTAMP_NTZ(6), e TIMESTAMP_NTZ(9), f TIMESTAMP, g DATETIME)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NF_T9' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NF_T9.",
                "DESCRIBE TABLE nf_t9");
            assertAnswer(
                "Table NF_T9A successfully created.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9a (a TIMESTAMP_NTZ)");
            assertAnswer(
                "A|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_t9a");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'E' with data type "
                    + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9b (e TIMESTAMP_NTZ(9))");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NF_T9B' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NF_T9B.",
                "DESCRIBE TABLE nf_t9b");
            assertAnswer(
                "Table NF_T9C successfully created.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9c (d TIMESTAMP_NTZ(6))");
            assertAnswer(
                "D|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_t9c");
            assertAnswer(
                "Table NF_T9D successfully created.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9d (c TIME)");
            assertAnswer(
                "C|TIME(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_t9d");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'C' with data type "
                    + "'TIME(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                    + "TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9e (c TIME(9))");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NF_T9E' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NF_T9E.",
                "DESCRIBE TABLE nf_t9e");
            assertAnswer(
                "Table NF_T9F successfully created.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9f (c TIMESTAMP_LTZ)");
            assertAnswer(
                "C|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_t9f");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'C' with data type "
                    + "'TIMESTAMP_NTZ(3)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9g (c TIMESTAMP_NTZ(3))");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'C' with data type "
                    + "'TIMESTAMP_LTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9j (c TIMESTAMP_LTZ(9))");
            assertAnswer(
                "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9k (c TIMESTAMP_TZ)");
            assertAnswer(
                "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9l (c TIMESTAMP_TZ(9))");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'A' with data type "
                    + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9h AS SELECT '2020-01-01'::TIMESTAMP_NTZ AS a");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NF_T9H' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NF_T9H.",
                "DESCRIBE TABLE nf_t9h");
            assertAnswer(
                "Table NF_T9I successfully created.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9i AS SELECT '2020-01-01'::TIMESTAMP_NTZ(6) AS a");
            assertAnswer(
                "A|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_t9i");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'A' with data type "
                    + "'TIMESTAMP_NTZ(3)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t9m AS SELECT '2020-01-01'::TIMESTAMP_NTZ(3) AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER ICEBERG TABLE nf_ice ADD COLUMN t9 TIMESTAMP_NTZ");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'T99' with data "
                    + "type 'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of "
                    + "TIME and TIMESTAMP types in Iceberg tables.",
                "ALTER ICEBERG TABLE nf_ice ADD COLUMN t99 TIMESTAMP_NTZ(9)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER ICEBERG TABLE nf_ice ADD COLUMN t96 TIMESTAMP_NTZ(6)");
            assertAnswer(
                "A|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; "
                    + "T9|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; "
                    + "T96|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_ice");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 6");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'E' with data type "
                    + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t6 (a TIMESTAMP_NTZ, e TIMESTAMP_NTZ(9))");
            assertAnswer(
                "Table NF_T6 successfully created.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t6 (a TIMESTAMP_NTZ, c TIME)");
            assertAnswer(
                "A|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; "
                    + "C|TIME(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_t6");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'A' with data type "
                    + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME "
                    + "and TIMESTAMP types in Iceberg tables.",
                "CREATE OR REPLACE ICEBERG TABLE nf_t6b AS SELECT '2020-01-01'::TIMESTAMP_NTZ AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER ICEBERG TABLE nf_ice ADD COLUMN t6 TIMESTAMP_NTZ");
            assertAnswer(
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'T69' with data "
                    + "type 'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of "
                    + "TIME and TIMESTAMP types in Iceberg tables.",
                "ALTER ICEBERG TABLE nf_ice ADD COLUMN t69 TIMESTAMP_NTZ(9)");
            assertAnswer(
                "A|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; "
                    + "T9|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; "
                    + "T96|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; "
                    + "T6|TIMESTAMP_NTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
                "DESCRIBE TABLE nf_ice");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [7] for parameter "
                    + "'ICEBERG_TIMESTAMP_DEFAULT_PRECISION'",
                "ALTER SESSION SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 7");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['9'] for parameter "
                    + "'ICEBERG_TIMESTAMP_DEFAULT_PRECISION'",
                "ALTER SESSION SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = '9'");
            assertAnswer(
                "ICEBERG_TIMESTAMP_DEFAULT_PRECISION|6|6|SESSION|Default precision for iceberg timestamps when no "
                    + "precision is specified. Valid values are 6 (microseconds) and 9 (nanoseconds).|NUMBER",
                "SHOW PARAMETERS LIKE 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION'");
        } finally {
            answer(
                "ALTER SESSION UNSET ICEBERG_TIMESTAMP_DEFAULT_PRECISION");
        }
    }

    @Test
    public void aSessionsParameterIsNoPropertyOfTheTable() {
        answer(
            "CREATE OR REPLACE ICEBERG TABLE nf_ice (a INT)");
        answer(
            "CREATE OR REPLACE TABLE nf_p (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'ICEBERG_TABLE'",
            "CREATE ICEBERG TABLE nf_ix (a INT) TIMEZONE = 'UTC'");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' at "
                + "'TABLE'",
            "CREATE TABLE nf_px (a INT) ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 9");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' at "
                + "'ICEBERG_TABLE'",
            "CREATE ICEBERG TABLE nf_iy (a INT) ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 7");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'TABLE'",
            "CREATE ICEBERG TABLE nf_iz (a INT) NOSUCH_PARAMETER = 1");
        assertAnswer(
            "Table NF_IW successfully created.",
            "CREATE ICEBERG TABLE nf_iw (a INT) DATA_RETENTION_TIME_IN_DAYS = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | cannot set parameter 'AUTOCOMMIT' at 'ICEBERG_TABLE'",
            "CREATE ICEBERG TABLE nf_iv (a INT) AUTOCOMMIT = TRUE");
        assertShape(
            "name:VARCHAR(16777216), type:VARCHAR(16777216), kind:VARCHAR(16777216), null?:VARCHAR(16777216), "
                + "default:VARCHAR(16777216), primary key:VARCHAR(16777216), unique key:VARCHAR(16777216), "
                + "check:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), policy "
                + "name:VARCHAR(16777216), privacy domain:OBJECT, name mapping:VARCHAR(16777216), write "
                + "default:VARCHAR(16777216)",
            "DESCRIBE TABLE nf_ice");
        assertShape(
            "name:VARCHAR(16777216), type:VARCHAR(16777216), kind:VARCHAR(16777216), null?:VARCHAR(16777216), "
                + "default:VARCHAR(16777216), primary key:VARCHAR(16777216), unique key:VARCHAR(16777216), "
                + "check:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), policy "
                + "name:VARCHAR(16777216), privacy domain:OBJECT, write default:VARCHAR(16777216)",
            "DESCRIBE TABLE nf_p");
        assertShape(
            "name:VARCHAR(16777216), type:VARCHAR(16777216), kind:VARCHAR(16777216), null?:VARCHAR(16777216), "
                + "default:VARCHAR(16777216), primary key:VARCHAR(16777216), unique key:VARCHAR(16777216), "
                + "check:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), policy "
                + "name:VARCHAR(16777216), privacy domain:OBJECT, write default:VARCHAR(16777216)",
            "DESCRIBE VIEW information_schema.tables");
        assertAnswer(
            "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE nf_p");
    }
}
