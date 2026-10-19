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
 * The order an Iceberg table's column list is judged in. What a type is written as comes first, column by
 * column: a word that names no type, a length the format does not take, a NUMBER without digits, a name the
 * format has no type for - so {@code (v VARCHAR(10), n nosuchtype)} is refused for the length and
 * {@code (n nosuchtype, v VARCHAR(10))} for the word. Then, each over every column: an OBJECT, an ARRAY and a
 * TIMESTAMP_TZ; the scale of a time; a VARIANT and the other values the format holds none of. Columns added to
 * a table are judged for their scales before any type the format does not hold. A word that names no type is
 * echoed as written, by its first part, and one written with parameters as any table refuses it; a member of a
 * structured type is read the same. Every answer is live-verified.
 */
public class IcebergColumnListOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void aWordThatNamesNoTypeInItsTurn() {
        answer(
            "CREATE OR REPLACE ICEBERG TABLE nf_ice (a INT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i1 (v VARCHAR(10), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i2 (n nosuchtype, v VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i3 (a INT, v VARCHAR(10), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i4 (a INT, n nosuchtype, v VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i5 (v CHAR, n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i6 (v VARIANT, n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | data type 'NUMBER/DECIMAL' without precision and scale is not "
                + "supported for Iceberg tables.",
            "CREATE ICEBERG TABLE nf_i7 (v NUMBER, n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i8 (v TIMESTAMP_NTZ(9), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i9 (v TIMESTAMP_TZ, n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (67,108,864) is supported for "
                + "'BINARY(L)'. Alternatively, use BINARY directly.",
            "CREATE ICEBERG TABLE nf_i10 (v BINARY(5), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE ICEBERG TABLE nf_i11 (v VARCHAR(0), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i12 (n nosuchtype, v VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i13 (a INT, n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i14 (n nosuchtype, m othertype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i15 (v VARCHAR(10), n nosuchtype, m othertype)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_i16 (n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5, 2)'.",
            "CREATE ICEBERG TABLE nf_i17 (n nosuchtype(5, 2))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i18 (v VARCHAR(10), n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_i19 (n nosuchtype(5), v VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i20 (n x.nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('.",
            "CREATE ICEBERG TABLE nf_i21 (n x.nosuch(5))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i22 (n x.y.z)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i23 (n x.y.z.w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('.",
            "CREATE ICEBERG TABLE nf_i24 (n x.y.z(1, 2))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"nosuch\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i25 (n \"nosuch\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('.",
            "CREATE ICEBERG TABLE nf_i26 (n \"nosuch\"(5))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"no such\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i27 (n \"no such\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('.",
            "CREATE ICEBERG TABLE nf_i28 (n \"no such\"(5))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"VARCHAR\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i29 (n \"VARCHAR\")");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"varchar\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i30 (n \"varchar\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('.",
            "CREATE ICEBERG TABLE nf_i31 (n \"VARCHAR\"(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"INT\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i32 (n \"INT\")");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"x\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i33 (n \"x\".\"y\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('.",
            "CREATE ICEBERG TABLE nf_i34 (n x.\"y\"(1))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"x\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i35 (n \"x\".y)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'NoSuchType' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i36 (n NoSuchType)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NoSuchType(5)'.",
            "CREATE ICEBERG TABLE nf_i37 (n NoSuchType(5))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i38 (n nosuchtype NOT NULL)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i39 (n nosuchtype DEFAULT 1)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i40 (n nosuchtype COMMENT 'c')");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i41 (n nosuchtype, n INT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i42 (a INT, a nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i43 (a INT, a VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i44 (v VARCHAR(10), v VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE test_db.nosuch_schema.nf_ix (n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE test_db.nosuch_schema.nf_ix (v VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i45 (n nosuchtype) EXTERNAL_VOLUME = 'nosuch_vol' CATALOG = 'SNOWFLAKE' "
                + "BASE_LOCATION = 'x'");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE IF NOT EXISTS nf_ice (n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE OR REPLACE ICEBERG TABLE nf_i46 (v VARCHAR(10), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_ice (n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_ice (v VARCHAR(10), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i47 (n nosuchtype, PRIMARY KEY (n))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i48 (n nosuchtype PRIMARY KEY)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i49 (n ARRAY(nosuchtype))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i50 (n OBJECT(k nosuchtype))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i51 (n MAP(VARCHAR, nosuchtype))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_i52 (v VARCHAR(10), n ARRAY(nosuchtype))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "CREATE ICEBERG TABLE nf_i53 (n nosuchtype) nosuchword");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ')'.",
            "CREATE ICEBERG TABLE nf_i54 (n nosuchtype,)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_i55 (n nosuchtype) AS SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'.",
            "CREATE ICEBERG TABLE nf_i56 (n SELECT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'badword'.",
            "CREATE ICEBERG TABLE nf_i57 (n INT badword)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'.",
            "CREATE ICEBERG TABLE nf_i58 (n)");
        answer(
            "CREATE ICEBERG TABLE nf_i59 (n, m INT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x VARCHAR(10), y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch, x VARCHAR(10)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch(5)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y x.nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '('.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y x.nosuch(5)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"nosuch\"' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y \"nosuch\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '('.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y \"nosuch\"(5)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN a INT, y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch, a INT");
        answer(
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN (x VARCHAR(10), y nosuch)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x CHAR, y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | data type 'NUMBER/DECIMAL' without precision and scale is not "
                + "supported for Iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x NUMBER, y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "ALTER ICEBERG TABLE nf_ice ADD x VARCHAR(10), y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nosuch_table ADD COLUMN y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "ALTER ICEBERG TABLE nosuch_table ADD COLUMN x VARCHAR(10), y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE IF EXISTS nosuch_table ADD COLUMN y nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ALTER COLUMN a SET DATA TYPE nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ALTER COLUMN a SET DATA TYPE x.nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "ALTER ICEBERG TABLE nf_ice ALTER COLUMN a SET DATA TYPE nosuch(5)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ALTER COLUMN nosuchcol SET DATA TYPE nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ALTER COLUMN a TYPE nosuch");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice MODIFY COLUMN a nosuch");
    }

    @Test
    public void theStepsTheListIsJudgedIn() {
        answer(
            "CREATE OR REPLACE ICEBERG TABLE nf_ice (a INT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j1 (v VARIANT, c CHAR)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j2 (c CHAR, v VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j3 (v TIMESTAMP_NTZ(9), c VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j4 (c VARCHAR(10), v TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j5 (v VARIANT, c VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | data type 'NUMBER/DECIMAL' without precision and scale is not "
                + "supported for Iceberg tables.",
            "CREATE ICEBERG TABLE nf_j6 (v TIMESTAMP_TZ, c NUMBER)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'C' with data type "
                + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_j7 (v VARIANT, c TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'C' with data type "
                + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_j8 (c TIMESTAMP_NTZ(9), v VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j9 (v ARRAY, c CHAR)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j10 (v GEOGRAPHY, n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j11 (v VARCHAR(10), c BINARY(5))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (67,108,864) is supported for "
                + "'BINARY(L)'. Alternatively, use BINARY directly.",
            "CREATE ICEBERG TABLE nf_j12 (c BINARY(5), v VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j13 (v CHAR, c VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j14 (v VARCHAR(10), c VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 50 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE ICEBERG TABLE nf_j15 (v VARIANT, c VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL Compilation error: | data type 'NUMBER/DECIMAL' without precision and scale is not "
                + "supported for Iceberg tables.",
            "CREATE ICEBERG TABLE nf_j16 (v NUMBER, c CHAR)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j17 (c CHAR, v NUMBER)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j18 (v OBJECT, c TIME(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j19 (c TIME(3), v OBJECT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j20 (v TIME(3), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j21 (v ARRAY(nosuchtype), c CHAR)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j22 (c ARRAY(CHAR), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j23 (c ARRAY(VARCHAR(10)), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported for "
                + "'VARCHAR(L)/STRING(L)' or use STRING directly.",
            "CREATE ICEBERG TABLE nf_j24 (c OBJECT(k VARCHAR(10)), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuchtype' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j25 (c OBJECT(k VARIANT), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j26 (n ARRAY(x.nosuch))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"nosuch\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_j27 (n ARRAY(\"nosuch\"))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "CREATE ICEBERG TABLE nf_j28 (n ARRAY(nosuch(5)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "CREATE ICEBERG TABLE nf_j29 (n OBJECT(k nosuch(5)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'MAP with key type UNKNOWN'.",
            "CREATE ICEBERG TABLE nf_j30 (n MAP(nosuchtype, VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'nosuchtype'.",
            "CREATE ICEBERG TABLE nf_j31 (n VECTOR(nosuchtype, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "CREATE ICEBERG TABLE nf_j32 (n nosuchtype) nosuchword");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected '<EOF>'.",
            "CREATE ICEBERG TABLE nf_j33 (v VARCHAR(10), n nosuchtype) nosuchword");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ')'.",
            "CREATE ICEBERG TABLE nf_j34 (v VARCHAR(10), n nosuchtype,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ')'.",
            "CREATE ICEBERG TABLE nf_j35 (n nosuchtype, m othertype,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'badword'.",
            "CREATE ICEBERG TABLE nf_j36 (n nosuchtype, m INT badword)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x ARRAY(nosuch)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'CHAR' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x VARIANT, y CHAR");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x VARIANT, y nosuch");
        answer(
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch, z other");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'nosuchword'.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch nosuchword");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k1 (v TIMESTAMP_NTZ(9), o OBJECT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k2 (o OBJECT, v TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'TIME(3)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k3 (v TIME(3), w VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'TIME(3)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k4 (w VARIANT, v TIME(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k5 (v VARIANT, o OBJECT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k6 (o OBJECT, v VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'TIME(3)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k7 (v TIME(3), w TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'W' with data type "
                + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k8 (w TIMESTAMP_NTZ(9), v TIME(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k9 (v TIMESTAMP_TZ, w TIME(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k10 (w TIME(3), v TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k11 (v TIMESTAMP_TZ, w TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k12 (w TIMESTAMP_NTZ(9), v TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'ARRAY' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k13 (v ARRAY, w GEOGRAPHY)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'ARRAY' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k14 (v GEOGRAPHY, w ARRAY)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'TIMESTAMP_LTZ(3)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k15 (v TIMESTAMP_LTZ(3), w VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'W' with data type "
                + "'TIMESTAMP_LTZ(3)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k16 (v VARIANT, w TIMESTAMP_LTZ(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k17 (v TIME(3), w OBJECT, x TIMESTAMP_NTZ(9), y VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'W' with data type "
                + "'TIME(3)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k18 (v OBJECT(k VARIANT), w TIME(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'ARRAY(TIMESTAMP_NTZ(9))'. Please consult the Snowflake documentation for supported scales of "
                + "TIME and TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k19 (v ARRAY(TIMESTAMP_NTZ(9)), w VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'W' with data type "
                + "'ARRAY(TIMESTAMP_NTZ(9))'. Please consult the Snowflake documentation for supported scales of "
                + "TIME and TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k20 (v VARIANT, w ARRAY(TIMESTAMP_NTZ(9)))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'W' with data type "
                + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k21 (v ARRAY(VARIANT), w TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'OBJECT(k TIME(3))'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k22 (v OBJECT(k TIME(3)), w VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'V' with data type "
                + "'TIME(9)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k23 (v TIME(9), w VARIANT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'W' with data type "
                + "'TIME(9)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "CREATE ICEBERG TABLE nf_k24 (v VARIANT, w TIME(9))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k25 (v TIMESTAMP_NTZ(3), w OBJECT)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'OBJECT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k26 (v OBJECT, w TIMESTAMP_NTZ(3))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'VARIANT' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k27 (v VARIANT, w GEOMETRY)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'TIMESTAMP_TZ(3)' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k28 (v TIMESTAMP_TZ(3), w VARIANT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_k29 (n nosuchtype(5), v VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE ICEBERG TABLE nf_k30 (v VARCHAR(0), n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_k31 (n nosuchtype(5), v CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_k32 (v VARIANT, n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_k33 (v TIMESTAMP_NTZ(9), n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE ICEBERG TABLE nf_k34 (n MAP(VARCHAR, nosuchtype(5)))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"nosuch\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k35 (n MAP(VARCHAR, \"nosuch\"))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'MAP with key type UNKNOWN'.",
            "CREATE ICEBERG TABLE nf_k36 (n MAP(x.nosuch, VARCHAR))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'x' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k37 (n OBJECT(k x.nosuch))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type '\"nosuch\"' for iceberg tables.",
            "CREATE ICEBERG TABLE nf_k38 (n OBJECT(k \"nosuch\"))");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'X' with data type "
                + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x TIMESTAMP_NTZ(9), y VARIANT");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'Y' with data type "
                + "'TIMESTAMP_NTZ(9)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x VARIANT, y TIMESTAMP_NTZ(9)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'X' with data type "
                + "'TIME(3)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x TIME(3), y OBJECT");
        assertAnswer(
            "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'Y' with data type "
                + "'TIME(3)'. Please consult the Snowflake documentation for supported scales of TIME and TIMESTAMP "
                + "types in Iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x OBJECT, y TIME(3)");
        assertAnswer(
            "REFUSED SQL Compilation error: | Unsupported data type 'VARIANT' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN x VARIANT, y OBJECT");
    }
}
