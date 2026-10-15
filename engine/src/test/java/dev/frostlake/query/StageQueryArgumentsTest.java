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
 * The arguments a staged file is read with: each value is any expression and has to be a constant, only
 * FILE_FORMAT and PATTERN are arguments, a format is named by a name or a text and a type's name is none
 * (live-verified).
 */
public class StageQueryArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesOverAnEmptyStage() {
        answer(
            "CREATE OR REPLACE STAGE ust");
        answer(
            "CREATE OR REPLACE FILE FORMAT ff TYPE = CSV");
        answer(
            "CREATE OR REPLACE TABLE st_t (a VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'FIELD_DELIMITER'.",
            "SELECT $1 IS NULL, $2 FROM @ust/nul (FILE_FORMAT => (TYPE = CSV FIELD_DELIMITER = '|'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"=\"](TYPE, "
                + "CSV)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => (TYPE = CSV))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"=\"](TYPE, "
                + "'CSV')] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => (TYPE = 'CSV'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"=\"](TYPE, "
                + "CSV)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => TYPE = CSV)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>ROW([unresolved function: "
                + "\"=\"](TYPE, CSV), [unresolved function: \"=\"](FIELD_DELIMITER, '|'))] for table function. Table "
                + "function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => (TYPE = CSV, FIELD_DELIMITER = '|'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "\"=\"](FORMAT_NAME, 'ff')] for table function. Table function argument is required to be a "
                + "constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => (FORMAT_NAME = 'ff'))");
        answer(
            "SELECT $1 FROM @ust (FILE_FORMAT => (FORMAT_NAME => 'ff'))");
        answer(
            "SELECT $1 FROM @ust (FILE_FORMAT => ())");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"+\"](1, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 1 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"||\"]('f', "
                + "'f')] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'f' || 'f')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: UPPER]('ff')] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => UPPER('ff'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>(SELECT 'ff' AS \"'FF'\" FROM "
                + "(VALUES (NULL)) DUAL)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => (SELECT 'ff'))");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => NULL)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => TRUE)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => ('ff'))");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => (ff))");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => IDENTIFIER('ff'))");
        answer(
            "SELECT $1 FROM @ust (FILE_FORMAT => \"ff\")");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => \"FF\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff.x.y.z)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>CAST('ff' AS VARCHAR(134217728))] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'ff'::VARCHAR)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'ff')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'FF')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => test_db.test_schema.ff)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'test_db.test_schema.ff')");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT NOSUCH.",
            "SELECT $1 FROM @ust (FILE_FORMAT => nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT NOSUCH.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'nosuch')");
        assertAnswer(
            "REFUSED SQL compilation error: | File format '\"\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT \"\".",
            "SELECT $1 FROM @ust (FILE_FORMAT => '')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => '.*')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => 1)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => (1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [PATTERN=>[unresolved function: \"||\"]('a', 'b')] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (PATTERN => 'a' || 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [PATTERN=>[unresolved function: UPPER]('x')] for "
                + "table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (PATTERN => UPPER('x'))");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => NULL)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => x)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => ('.*'))");
        assertAnswer(
            "REFUSED Failure using stage area. Cause: [Invalid regular expression pattern '['.]",
            "SELECT $1 FROM @ust (PATTERN => '[')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff, PATTERN => '.*')");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1 FROM @ust (NOSUCH => 1)");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1 FROM @ust (NOSUCH => 'x')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff, FILE_FORMAT => ff)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => '.*', PATTERN => '.*')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [2=>[unresolved function: \"=\"](FILE_FORMAT, FF)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT = ff)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ')'.",
            "SELECT $1 FROM @ust (FILE_FORMAT => )");
        answer(
            "SELECT $1 FROM @ust (FILE_FORMAT => ff PATTERN => '.*')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'.",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff,)");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1 FROM @ust (FILE_FORMAT)");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1 FROM @ust (ff)");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1 FROM @ust ('ff')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('.",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff) (PATTERN => '.*')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff) t");
        answer(
            "COPY INTO st_t FROM (SELECT $1 FROM @ust (FILE_FORMAT => (TYPE = CSV)))");
        answer(
            "COPY INTO st_t FROM (SELECT $1 FROM @ust (FILE_FORMAT => ff))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'FIELD_DELIMITER'.",
            "SELECT $1 FROM @ust/p/ (FILE_FORMAT => (TYPE = CSV FIELD_DELIMITER = '|'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'FIELD_DELIMITER'.",
            "SELECT $1 FROM '@ust' (FILE_FORMAT => (TYPE = CSV FIELD_DELIMITER = '|'))");
        answer(
            "SELECT $1 FROM @ust (FILE_FORMAT => (TYPE = CSV) PATTERN => '.*')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'field_delimiter'.",
            "SELECT $1 FROM @ust (file_format => (type = csv field_delimiter = '|'))");
    }

    @Test
    public void filesReadThroughTheArguments() {
        answer(
            "CREATE OR REPLACE STAGE ust");
        answer(
            "CREATE OR REPLACE FILE FORMAT ff TYPE = CSV");
        answer(
            "CREATE OR REPLACE FILE FORMAT ffp TYPE = CSV FIELD_DELIMITER = '|'");
        answer(
            "COPY INTO @ust/a1.csv FROM (SELECT 'p|q', 'r') FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE "
                + "= TRUE");
        answer(
            "COPY INTO @ust/b2.csv FROM (SELECT 's|t', 'u') FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE "
                + "= TRUE");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 'ffp') ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => (ffp)) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ('ffp')) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => IDENTIFIER('ffp')) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 1) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => NULL) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => TRUE) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT NOSUCH.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => (nosuch)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT NOSUCH.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => IDENTIFIER('nosuch')) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT NOSUCH.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ('nosuch')) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'CSV' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT CSV.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 'CSV') ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'CSV' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT CSV.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => csv) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'JSON' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT JSON.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 'JSON') ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'JSON' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT JSON.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => json) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (file_format => ffp) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (\"FILE_FORMAT\" => ffp) ORDER BY 1");
        assertAnswer(
            "p|q|r",
            "SELECT $1, $2 FROM @ust (PATTERN => '.*a1.*') ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (PATTERN => 1) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (PATTERN => (1)) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (PATTERN => NULL) ORDER BY 1");
        assertAnswer(
            "",
            "SELECT $1, $2 FROM @ust (PATTERN => x) ORDER BY 1");
        assertAnswer(
            "",
            "SELECT $1, $2 FROM @ust (PATTERN => a1) ORDER BY 1");
        assertAnswer(
            "p|q|r",
            "SELECT $1, $2 FROM @ust (PATTERN => ('.*a1.*')) ORDER BY 1");
        assertAnswer(
            "",
            "SELECT $1, $2 FROM @ust (PATTERN => '.*A1.*') ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (PATTERN => TRUE) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (PATTERN => 1.5) ORDER BY 1");
        assertAnswer(
            "",
            "SELECT $1, $2 FROM @ust (PATTERN => 'a1.csv') ORDER BY 1");
        assertAnswer(
            "s|t|u",
            "SELECT $1, $2 FROM @ust (pattern => '.*b2.*') ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp, FILE_FORMAT => ff) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ff, FILE_FORMAT => ffp) ORDER BY 1");
        assertAnswer(
            "s|t|u",
            "SELECT $1, $2 FROM @ust (PATTERN => '.*a1.*', PATTERN => '.*b2.*') ORDER BY 1");
        assertAnswer(
            "s|t,u",
            "SELECT $1, $2 FROM @ust (PATTERN => '.*b2.*', FILE_FORMAT => ffp) ORDER BY 1");
        assertAnswer(
            "s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp, PATTERN => '.*b2.*') ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust () ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [NOSUCH=>[unresolved function: \"+\"](1, 1)] for "
                + "table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (NOSUCH => 1 + 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [2=>[unresolved function: \"+\"](1, 1)] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (1 + 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [3=>[unresolved function: \"+\"](1, 1)] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (ffp, 1 + 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [3=>[unresolved function: \"+\"](1, 1)] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp, 1 + 1) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp, NOSUCH => 1) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (NOSUCH => 1, FILE_FORMAT => nosuchff) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [PATTERN=>[unresolved function: \"+\"](1, 1)] for "
                + "table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => nosuchff, PATTERN => 1 + 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'NOSUCHFF' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT NOSUCHFF.",
            "SELECT $1, $2 FROM @ust (PATTERN => '[', FILE_FORMAT => nosuchff) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => nosuchff, NOSUCH => 1) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (1) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (NULL) ORDER BY 1");
        assertAnswer(
            "REFUSED Unsupported feature 'TABLE'.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp, ffp) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "UPPER]([unresolved function: \"||\"]('a', 'b'))] for table function. Table function argument is "
                + "required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => UPPER('a' || 'b')) ORDER BY 1");
        assertAnswer(
            "p|q|r ;; s|t|u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => -1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: NOT](TRUE)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => NOT TRUE) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "AND]([unresolved function: \"=\"](1, 1), [unresolved function: \"=\"](2, 2))] for table function. "
                + "Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 1 = 1 AND 2 = 2) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "CASE]([unresolved function: \"=\"](1, 1), 'ff', NULL)] for table function. Table function "
                + "argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => CASE WHEN 1 = 1 THEN 'ff' END) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"IS "
                + "NULL\"](X)] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => x IS NULL) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: LIKE]('a', "
                + "'b')] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 'a' LIKE 'b') ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.A' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.A.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => a.b) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>CURRENT_DATE()] for table function. "
                + "Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => CURRENT_DATE) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "CURRENT_DATE]()] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => CURRENT_DATE()) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: IFF](TRUE, "
                + "'ff', 'ff')] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => IFF(TRUE, 'ff', 'ff')) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>CAST(1 AS NUMBER(38,0))] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 1::INT) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>X IN 1 IN 2] for table function. "
                + "Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => x IN (1, 2)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: BETWEEN](X, 1, "
                + "2)] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => x BETWEEN 1 AND 2) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "TO_VARCHAR]('x')] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => TO_VARCHAR('x')) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"-\"](1, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 1 - 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"*\"](2, 3)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 2 * 3) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"=\"]('a', "
                + "'b')] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 'a' = 'b') ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>ROW(1, 2)] for table function. Table "
                + "function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => (1, 2)) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ((ffp))) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>TO_DATE('2024-01-01')] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => DATE '2024-01-01') ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"<\"](1, 2)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 1 < 2) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"=\"](X.Y.Z, "
                + "1)] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => x.y.z = 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"=\"](\"q\", "
                + "1)] for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => \"q\" = 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: F](1)] for "
                + "table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => f(1)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"/\"](1, 2)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 1 / 2) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "\"||\"]([unresolved function: \"||\"]('a', 'b'), 'c')] for table function. Table function "
                + "argument is required to be a constant.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => 'a' || 'b' || 'c') ORDER BY 1");
        answer(
            "SELECT $1, $2 FROM @ust (,) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ff, , PATTERN => '.*') ORDER BY 1");
        answer(
            "SELECT $1, $2 FROM @ust (=> ff) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '=>'.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => => ff) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '=>'.",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ff => 1) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '=>'.",
            "SELECT $1, $2 FROM @ust (1 => 2) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '=>'.",
            "SELECT $1, $2 FROM @ust ('a' => 'b') ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (File_Format => ffp) ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp) t ORDER BY 1");
        assertAnswer(
            "p|q,r ;; s|t,u",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp) AS t ORDER BY 1");
        assertAnswer(
            "p ;; s",
            "SELECT t.$1 FROM @ust (FILE_FORMAT => ffp) t ORDER BY 1");
        assertAnswer(
            "p|q,r",
            "SELECT $1, $2 FROM @ust (FILE_FORMAT => ffp) WHERE $1 = 'p' ORDER BY 1");
    }

    @Test
    public void howAValueThatIsNoConstantIsTold() {
        answer(
            "CREATE OR REPLACE STAGE ust");
        answer(
            "CREATE OR REPLACE FILE FORMAT ff TYPE = CSV");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>X IN ([unresolved function: \"+\"](1,"
                + " 1)) IN 2] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x IN (1 + 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: UPPER](NULL)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => UPPER(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>NOT(X IN 1 IN 2)] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x NOT IN (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>NOT([unresolved function: LIKE](X, "
                + "'a'))] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x NOT LIKE 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>NOT([unresolved function: BETWEEN](X,"
                + " 1, 2))] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x NOT BETWEEN 1 AND 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: NEGATE](X)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => -x)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"UNARY "
                + "PLUS\"](X)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => +x)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"IS NOT "
                + "NULL\"](X)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x IS NOT NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"!=\"](X, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x != 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"<>\"](X, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x <> 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: "
                + "OR]([unresolved function: \"=\"](1, 1), [unresolved function: \"=\"](2, 2))] for table function. "
                + "Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 1 = 1 OR 2 = 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: CASE](X, 1, "
                + "'a', 'b')] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => CASE x WHEN 1 THEN 'a' ELSE 'b' END)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>TRY_CAST('1')] for table function. "
                + "Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => TRY_CAST('1' AS INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>CAST(X AS VARCHAR(5))] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x::VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: COALESCE](NULL,"
                + " 'ff')] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => COALESCE(NULL, 'ff'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [\"FILE_FORMAT\"=>[unresolved function: \"+\"](1, "
                + "1)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (\"FILE_FORMAT\" => 1 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [\"nosuch\"=>[unresolved function: \"+\"](1, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (\"nosuch\" => 1 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [nosuch=>[unresolved function: \"+\"](1, 1)] for "
                + "table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (nosuch => 1 + 1, FILE_FORMAT => 2 + 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"+\"](1, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 1 + 1, PATTERN => 2 + 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: ILIKE](X, "
                + "'a')] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x ILIKE 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: LIKE]('a', 'b',"
                + " '!')] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'a' LIKE 'b' ESCAPE '!')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \">=\"](X, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x >= 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"<=\"](X, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x <= 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \">\"](X, 1)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x > 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"%\"](X, 2)] "
                + "for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x % 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: NOT](X)] for "
                + "table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => NOT x)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: AND](TRUE, "
                + "FALSE)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => TRUE AND FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>TO_TIME('01:02:03')] for table "
                + "function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => TIME '01:02:03')");
        answer(
            "SELECT $1 FROM @ust (FILE_FORMAT => TIMESTAMP '2024-01-01 00:00:00')");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.X' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.X.",
            "SELECT $1 FROM @ust (FILE_FORMAT => x.y)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT $1 FROM @ust (FILE_FORMAT => db.sc.ff.extra)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (PATTERN => x.y)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => 1.5)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => X'41')");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => FALSE)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => 'ff', PATTERN => NULL)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => ff, PATTERN => ff)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => (('ff')))");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => (-1))");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => - 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid argument [FILE_FORMAT=>[unresolved function: \"UNARY "
                + "PLUS\"](1)] for table function. Table function argument is required to be a constant.",
            "SELECT $1 FROM @ust (FILE_FORMAT => +1)");
        assertAnswer(
            "",
            "SELECT $1 FROM @ust (FILE_FORMAT => -(1))");
    }
}
