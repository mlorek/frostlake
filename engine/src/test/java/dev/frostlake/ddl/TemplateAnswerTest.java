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
 * What CREATE TABLE ... USING TEMPLATE makes of its query's answer. The answer is one value holding an array of
 * column definitions; a text is read as JSON, so the text of such an array creates the table. A text that is no
 * JSON is an invalid template, reported with the reader's own sentence and the place it stopped at; a query of
 * two columns is refused for selecting more than a single value; and a fault of the query itself - a QUALIFY
 * with no window function - is placed where it stands in the whole statement. Every answer is live-verified.
 */
public class TemplateAnswerTest extends MeasuredCellsTestSupport {

    @Test
    public void anAnswerThatIsNoListOfColumns() {
        answer(
            "CREATE OR REPLACE STAGE tpl_st");
        answer(
            "CREATE OR REPLACE FILE FORMAT tpl_ff TYPE = CSV PARSE_HEADER = TRUE");
        stageLocalFile("tpl_st/a/", "clean.csv", "i,s,d\n1,x,2020-01-01\n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: Unrecognized token 'x': was expecting (JSON "
                + "String, Number, Array, Object or token 'null', 'true' or 'false') |  at [Source: (String)\"x\"; "
                + "line: 1, column: 2] | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'x' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 221 | found QUALIFY clause but no window "
                + "function.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', "
                + "column_name, 'TYPE', type, 'NULLABLE', nullable)) FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) QUALIFY TRUE)");
        assertAnswer(
            "REFUSED Unsupported feature 'Table function must be used in the TEMPLATE sub-query'.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT '[]' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: Unexpected end-of-input: expected close marker "
                + "for Array (start marker at [Source: (String)\"[1\"; line: 1, column: 1]) |  at [Source: "
                + "(String)\"[1\"; line: 1, column: 3] | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT '[1' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'null' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'true' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT '{}' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 1 FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT '' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: Unrecognized token 'x': was expecting (JSON "
                + "String, Number, Array, Object or token 'null', 'true' or 'false') |  at [Source: (String)\"x y\"; "
                + "line: 1, column: 2] | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'x y' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: template must be a non-null JSON array | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT '\"x\"' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
        assertAnswer(
            "Table TPL_T successfully created.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT '[{\"COLUMN_NAME\":\"a\",\"TYPE\":\"TEXT\","
                + "\"NULLABLE\":true}]' FROM TABLE(INFER_SCHEMA(LOCATION => '@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) "
                + "LIMIT 1)");
        assertAnswer(
            "a|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE tpl_t");
        assertAnswer(
            "Table TPL_T successfully created.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', "
                + "column_name, 'TYPE', type, 'NULLABLE', nullable)) FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')))");
        assertAnswer(
            "i|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "s|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "d|DATE|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE tpl_t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 233 | found QUALIFY clause but no window "
                + "function.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', "
                + "column_name, 'TYPE', type, 'NULLABLE', nullable)) FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) WHERE FALSE QUALIFY TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 233 | found QUALIFY clause but no window "
                + "function.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', "
                + "column_name, 'TYPE', type, 'NULLABLE', nullable)) FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) HAVING TRUE QUALIFY TRUE)");
        assertAnswer(
            "Table TPL_T successfully created.",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', "
                + "column_name, 'TYPE', type, 'NULLABLE', nullable)) FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 85 | found QUALIFY clause but no window "
                + "function.",
            "SELECT 1 FROM TABLE(INFER_SCHEMA(LOCATION => '@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) QUALIFY TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | found QUALIFY clause but no window "
                + "function.",
            "SELECT 1 FROM (SELECT 1 a) QUALIFY TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: Exactly 1 template is expected. Found: 3 | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'x' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid template: The sub-query must SELECT a single scalar value | ",
            "CREATE OR REPLACE TABLE tpl_t USING TEMPLATE (SELECT 'x', 'y' FROM TABLE(INFER_SCHEMA(LOCATION => "
                + "'@tpl_st/a/', FILE_FORMAT => 'tpl_ff')) LIMIT 1)");
    }
}
