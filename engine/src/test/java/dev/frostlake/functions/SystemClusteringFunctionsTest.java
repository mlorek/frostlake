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
 * SYSTEM$CLUSTERING_DEPTH, SYSTEM$CLUSTERING_INFORMATION and SYSTEM$CLUSTERING_RATIO: the table and the keys
 * they read, their refusals, and the answers over a table of a few rows and an empty one (live-verified).
 */
public class SystemClusteringFunctionsTest extends MeasuredCellsTestSupport {

    @Test
    public void depthOfATableAndItsKeys() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i, v)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i = 1')");
            assertAnswer(
                "REFUSED Invalid clustering keys or table TSRC is not clustered",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tsrc')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tsrc', '(i)')");
            assertAnswer(
                "0.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tem', '(i)')");
            assertAnswer(
                "REFUSED Invalid clustering keys or table TEM is not clustered",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tem')");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH.",
                "SELECT SYSTEM$CLUSTERING_DEPTH('nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(nosuch)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', 'i')");
            assertAnswer(
                "REFUSED null argument for table name",
                "SELECT SYSTEM$CLUSTERING_DEPTH(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TO_CHAR(1)'",
                "SELECT SYSTEM$CLUSTERING_DEPTH(1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[SYSTEM$CLUSTERING_DEPTH()], expected 1, got 0",
                "SELECT SYSTEM$CLUSTERING_DEPTH()");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i = 1', 'x')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH($tblname, '(i)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('ts' || 'rc', '(i)')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tsrc', n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'UPPER(TSRC.N)'",
                "SELECT SYSTEM$CLUSTERING_DEPTH(UPPER(n)) FROM tsrc");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH((SELECT 'tcl'))");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void informationAndRatio() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "{ |   \"cluster_by_keys\" : \"LINEAR(i)\", |   \"total_partition_count\" : 1, |   "
                    + "\"total_constant_partition_count\" : 0, |   \"average_overlaps\" : 0.0, |   \"average_depth\" "
                    + ": 1.0, |   \"version\" : \"OPTIMA\", |   \"clustering_errors\" : [ ] | }",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tcl')");
            assertAnswer(
                "{ |   \"cluster_by_keys\" : \"LINEAR(i)\", |   \"total_partition_count\" : 1, |   "
                    + "\"total_constant_partition_count\" : 0, |   \"average_overlaps\" : 0.0, |   \"average_depth\" "
                    + ": 1.0, |   \"version\" : \"OPTIMA\", |   \"clustering_errors\" : [ ] | }",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tcl', '(i)')");
            assertAnswer(
                "REFUSED Invalid clustering keys or table TSRC is not clustered",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tsrc')");
            assertAnswer(
                "{ |   \"cluster_by_keys\" : \"LINEAR(i)\", |   \"total_partition_count\" : 1, |   "
                    + "\"total_constant_partition_count\" : 0, |   \"average_overlaps\" : 0.0, |   \"average_depth\" "
                    + ": 1.0, |   \"version\" : \"OPTIMA\", |   \"clustering_errors\" : [ ] | }",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tsrc', '(i)')");
            assertAnswer(
                "{ |   \"cluster_by_keys\" : \"LINEAR(i)\", |   \"total_partition_count\" : 0, |   "
                    + "\"total_constant_partition_count\" : 0, |   \"average_overlaps\" : 0.0, |   \"average_depth\" "
                    + ": 0.0, |   \"version\" : \"OPTIMA\", |   \"clustering_errors\" : [ ] | }",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tem', '(i)')");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH.",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_INFORMATION needs to "
                    + "be constant, found 'TSRC.N'",
                "SELECT SYSTEM$CLUSTERING_INFORMATION(n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$CLUSTERING_INFORMATION needs to "
                    + "be constant, found 'TSRC.N'",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tsrc', n) FROM tsrc");
            assertAnswer(
                "REFUSED null argument for table name",
                "SELECT SYSTEM$CLUSTERING_INFORMATION(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[SYSTEM$CLUSTERING_INFORMATION()], expected 1, got 0",
                "SELECT SYSTEM$CLUSTERING_INFORMATION()");
            assertAnswer(
                "REFUSED Unsupported feature 'Clustering Ratio'.",
                "SELECT SYSTEM$CLUSTERING_RATIO('tcl')");
            assertAnswer(
                "REFUSED Unsupported feature 'Clustering Ratio'.",
                "SELECT SYSTEM$CLUSTERING_RATIO('tcl', '(i)')");
            assertAnswer(
                "REFUSED Invalid clustering keys or table TSRC is not clustered",
                "SELECT SYSTEM$CLUSTERING_RATIO('tsrc')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_RATIO needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT SYSTEM$CLUSTERING_RATIO(n) FROM tsrc");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void aKeyListIsReadAndPlaced() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i, nosuch)')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl','(nosuch)')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_DEPTH( 'tcl' , '( nosuch )' )");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('test_schema.tcl', '(nosuch)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', 'nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tcl', '(nosuch)')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i + nosuch)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', NULL)");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '()')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TO_CHAR(1)'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', 1)");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', NULL)");
            answer(
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'nosuch = 1')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 2 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 2 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TO_CHAR(1)'",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 1)");
            assertAnswer(
                "1.0 ;; 1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl') FROM tsrc");
            assertAnswer(
                "2.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl') + 1");
            assertAnswer(
                "0.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl2')");
            assertAnswer(
                "0.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('tcl3')");
            assertAnswer(
                "REFUSED Cannot specify clustering keys on views | ",
                "SELECT SYSTEM$CLUSTERING_DEPTH('vcl')");
            assertAnswer(
                "REFUSED Cannot specify clustering keys on views | ",
                "SELECT SYSTEM$CLUSTERING_DEPTH('vcl', '(i)')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('TEST_SCHEMA.TCL')");
            assertAnswer(
                "1.0",
                "SELECT SYSTEM$CLUSTERING_DEPTH('test_db.test_schema.tcl')");
            assertAnswer(
                "REFUSED SQL compilation error: | Table '\"tcl\"' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE \"tcl\".",
                "SELECT SYSTEM$CLUSTERING_DEPTH('\"tcl\"')");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "SELECT SYSTEM$CLUSTERING_DEPTH('nosuch_schema.tcl')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT SYSTEM$CLUSTERING_DEPTH('a.b.c.d')");
            assertAnswer(
                "REFUSED SQL compilation error: | Table '\"\"' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE \"\".",
                "SELECT SYSTEM$CLUSTERING_DEPTH('')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'BOOLEAN_TO_TEXT(TRUE)'",
                "SELECT SYSTEM$CLUSTERING_DEPTH(TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TO_CHAR(1.5)'",
                "SELECT SYSTEM$CLUSTERING_DEPTH(1.5)");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void theReportNamesTheKeysAsWritten() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.\"1\"' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.\"1\".",
                "SELECT SYSTEM$CLUSTERING_INFORMATION(1)");
            assertAnswer(
                "{ |   \"cluster_by_keys\" : \"LINEAR(i)\", |   \"total_partition_count\" : 1, |   "
                    + "\"total_constant_partition_count\" : 0, |   \"average_overlaps\" : 0.0, |   \"average_depth\" "
                    + ": 1.0, |   \"version\" : \"OPTIMA\", |   \"clustering_errors\" : [ ] | }",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tcl', 5)");
            assertAnswer(
                "{ |   \"cluster_by_keys\" : \"LINEAR(i)\", |   \"total_partition_count\" : 1, |   "
                    + "\"total_constant_partition_count\" : 0, |   \"average_overlaps\" : 0.0, |   \"average_depth\" "
                    + ": 1.0, |   \"version\" : \"OPTIMA\", |   \"clustering_errors\" : [ ] | }",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('tcl', '(i)', 5)");
            assertAnswer(
                "LINEAR(i, v)",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl', '(i, v)')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR(i)",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl', 'i')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR(I)",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl', '(I)')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR(v, i)",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl', '(v, i)')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR(UPPER(v))",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl', '(UPPER(v))')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR( i ,v )",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl', '( i ,v )')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR(i, v)",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl2')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "LINEAR(UPPER(v), i)",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl3')):cluster_by_keys::VARCHAR");
            assertAnswer(
                "0",
                "SELECT PARSE_JSON(SYSTEM$CLUSTERING_INFORMATION('tcl2')):total_partition_count::INT");
            assertAnswer(
                "REFUSED Cannot specify clustering keys on views | ",
                "SELECT SYSTEM$CLUSTERING_INFORMATION('vcl')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_RATIO needs to be "
                    + "constant, found 'TO_CHAR(1)'",
                "SELECT SYSTEM$CLUSTERING_RATIO(1)");
            assertAnswer(
                "REFUSED null argument for table name",
                "SELECT SYSTEM$CLUSTERING_RATIO(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH.",
                "SELECT SYSTEM$CLUSTERING_RATIO('nosuch')");
            assertAnswer(
                "REFUSED Unsupported feature 'Clustering Ratio'.",
                "SELECT SYSTEM$CLUSTERING_RATIO('tsrc', '(i)')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[SYSTEM$CLUSTERING_RATIO()], expected 1, got 0",
                "SELECT SYSTEM$CLUSTERING_RATIO()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'",
                "SELECT SYSTEM$CLUSTERING_RATIO('tcl', '(nosuch)')");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }
}
