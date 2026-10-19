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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A CREATE ICEBERG TABLE that names no external volume takes the EXTERNAL_VOLUME parameter of its schema, else
 * its database's: a volume that is not there is refused by its name, upper-cased, whatever catalog the
 * statement names, the schema's parameter before the database's; SNOWFLAKE_MANAGED in any case keeps the table
 * in managed storage, and so does a parameter set to the empty text or to nothing. A volume the statement
 * names itself wins over both. SHOW PARAMETERS tells the level the value in force comes from. Every answer
 * is live-verified.
 */
public class IcebergDefaultVolumeTest extends MeasuredCellsTestSupport {

    @Test
    public void theNearestContainersVolumeIsTaken() {
        try {
            assertAnswer(
                "EXTERNAL_VOLUME||||Name of an external volume that will be used for persisted Iceberg metadata "
                    + "and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "Table IT0 successfully created.",
                "CREATE ICEBERG TABLE it0 (a INT)");
            engine.execute(
                "SHOW ICEBERG TABLES IN SCHEMA test_db.test_schema");
            assertAnswer(
                "IT0|SNOWFLAKE_MANAGED|SNOWFLAKE",
                "SELECT \"name\", \"external_volume_name\", \"catalog_name\" FROM "
                    + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema SET EXTERNAL_VOLUME = 'nosuch_vol_p1307'");
            assertAnswer(
                "EXTERNAL_VOLUME|nosuch_vol_p1307||SCHEMA|Name of an external volume that will be used for "
                    + "persisted Iceberg metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_VOL_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_VOL_P1307.",
                "CREATE ICEBERG TABLE it1 (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_VOL_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_VOL_P1307.",
                "CREATE ICEBERG TABLE it1b (a INT) CATALOG = 'SNOWFLAKE'");
            assertAnswer(
                "Table IT1C successfully created.",
                "CREATE ICEBERG TABLE it1c (a INT) EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema SET EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED'");
            assertAnswer(
                "EXTERNAL_VOLUME|SNOWFLAKE_MANAGED||SCHEMA|Name of an external volume that will be used for "
                    + "persisted Iceberg metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "Table IT2 successfully created.",
                "CREATE ICEBERG TABLE it2 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema UNSET EXTERNAL_VOLUME");
            assertAnswer(
                "EXTERNAL_VOLUME||||Name of an external volume that will be used for persisted Iceberg metadata "
                    + "and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER DATABASE test_db SET EXTERNAL_VOLUME = 'nosuch_dbvol_p1307'");
            assertAnswer(
                "EXTERNAL_VOLUME|nosuch_dbvol_p1307||DATABASE|Name of an external volume that will be used for "
                    + "persisted Iceberg metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "EXTERNAL_VOLUME|nosuch_dbvol_p1307||DATABASE|Name of an external volume that will be used for "
                    + "persisted Iceberg metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN DATABASE test_db");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_DBVOL_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_DBVOL_P1307.",
                "CREATE ICEBERG TABLE it3 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema SET EXTERNAL_VOLUME = 'nosuch_schvol_p1307'");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_SCHVOL_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_SCHVOL_P1307.",
                "CREATE ICEBERG TABLE it4 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema UNSET EXTERNAL_VOLUME");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER DATABASE test_db UNSET EXTERNAL_VOLUME");
            assertAnswer(
                "EXTERNAL_VOLUME||||Name of an external volume that will be used for persisted Iceberg metadata "
                    + "and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "REFUSED SQL Compilation Error: BASE_LOCATION property is not supported for Iceberg tables using "
                    + "Snowflake Managed Storage.",
                "CREATE ICEBERG TABLE it5 (a INT) BASE_LOCATION = 'b5'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema SET EXTERNAL_VOLUME = nosuch_ident_p1307");
            assertAnswer(
                "EXTERNAL_VOLUME|NOSUCH_IDENT_P1307||SCHEMA|Name of an external volume that will be used for "
                    + "persisted Iceberg metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_IDENT_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_IDENT_P1307.",
                "CREATE ICEBERG TABLE it6 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema SET EXTERNAL_VOLUME = ''");
            assertAnswer(
                "EXTERNAL_VOLUME|||SCHEMA|Name of an external volume that will be used for persisted Iceberg "
                    + "metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "Table IT7 successfully created.",
                "CREATE ICEBERG TABLE it7 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema UNSET EXTERNAL_VOLUME");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema SET EXTERNAL_VOLUME = 'snowflake_managed'");
            assertAnswer(
                "EXTERNAL_VOLUME|snowflake_managed||SCHEMA|Name of an external volume that will be used for "
                    + "persisted Iceberg metadata and data files.|STRING",
                "SHOW PARAMETERS LIKE 'EXTERNAL_VOLUME' IN SCHEMA test_db.test_schema");
            assertAnswer(
                "Table IT8 successfully created.",
                "CREATE ICEBERG TABLE it8 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema UNSET EXTERNAL_VOLUME");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER DATABASE test_db SET EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED'");
            assertAnswer(
                "Table IT9 successfully created.",
                "CREATE ICEBERG TABLE it9 (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER DATABASE test_db UNSET EXTERNAL_VOLUME");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_TABVOL_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_TABVOL_P1307.",
                "CREATE ICEBERG TABLE it10 (a INT) EXTERNAL_VOLUME = 'nosuch_tabvol_p1307'");
            assertAnswer(
                "REFUSED SQL compilation error: | External volume 'NOSUCH_TABVOL_P1307' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on VOLUME "
                    + "NOSUCH_TABVOL_P1307.",
                "CREATE ICEBERG TABLE it11 (a INT) EXTERNAL_VOLUME = 'nosuch_tabvol_p1307' CATALOG = 'SNOWFLAKE' "
                    + "BASE_LOCATION = 'b11'");
            engine.execute(
                "SHOW ICEBERG TABLES IN SCHEMA test_db.test_schema");
            assertAnswer(
                "IT0|SNOWFLAKE_MANAGED|SNOWFLAKE ;; IT1C|SNOWFLAKE_MANAGED|SNOWFLAKE ;; "
                    + "IT2|SNOWFLAKE_MANAGED|SNOWFLAKE ;; IT7|SNOWFLAKE_MANAGED|SNOWFLAKE ;; "
                    + "IT8|SNOWFLAKE_MANAGED|SNOWFLAKE ;; IT9|SNOWFLAKE_MANAGED|SNOWFLAKE",
                "SELECT \"name\", \"external_volume_name\", \"catalog_name\" FROM "
                    + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
