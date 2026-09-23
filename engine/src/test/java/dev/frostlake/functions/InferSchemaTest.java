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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFER_SCHEMA over staged CSV and JSON files: the column definitions, the type each column's values settle on,
 * how files merge, which files a call reads, how records are split and malformed ones refused, and the argument
 * refusals in the order the account checks them.
 *
 * <p>Rows are read ORDER BY ORDER_ID, COLUMN_NAME, and a FILENAMES list is compared as a sorted set: the account
 * reads files in parallel, so neither the order of columns sharing an ORDER_ID nor the order of the files a
 * column appears in is fixed.
 */
public class InferSchemaTest extends BaseDatabaseTest {

    private static final String WIDE_CSV = """
        c_int,c_big,c_neg,c_dec,c_exp,c_bool,c_date,c_ts,c_tstz,c_time,c_text,c_empty,c_mix,c_intdec,c_lead0,c_yes,c_huge
        1,123456789012,-5,1.5,1e2,true,2020-01-01,2020-01-01 10:00:00,2020-01-01 10:00:00 +01:00,10:00:00,alpha,,1,1,007,yes,12345678901234567890123456789012345678
        2,23,10,22.25,2.5E-3,false,2020-02-29,2020-02-29 23:59:59.123,2020-02-29T23:59:59Z,23:59:59,beta,,x,2.5,010,no,1
        3,4,0,-3,-1.0e+10,TRUE,2021-12-31,2021-12-31T01:02:03,2021-12-31 01:02:03.456 -0800,01:02:03.5,gamma,,2,3,000,y,99999999999999999999999999999999999999999
        """;

    private static final String J1_JSON = """
        {"a":1,"b":"x","c":1.5,"d":true,"e":null,"f":[1,2],"g":{"k":1},"h":"2020-01-01","i":"2020-01-01 10:00:00","j":1e2,"k":"10:00:00","Mixed":1,"big":123456789012345678901234567890}
        {"a":2,"b":"y","c":22.25,"d":false,"e":null,"f":[],"g":{},"h":"2020-02-29","i":"2020-02-29T23:59:59.123Z","j":2.5E-3,"k":"23:59:59","Mixed":"x","extra":5,"big":1}
        """;

    private static final String J2_JSON = """
        [{"a":3,"B":"z","country":"PL","COUNTRY":"de"},{"a":4.5,"b":null,"country":"US"}]
        """;

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT ffh TYPE = CSV PARSE_HEADER = TRUE");
        engine.execute("CREATE FILE FORMAT ffn TYPE = CSV");
        engine.execute("CREATE FILE FORMAT fj TYPE = JSON");
        engine.execute("CREATE FILE FORMAT fjs TYPE = JSON STRIP_OUTER_ARRAY = TRUE");
    }

    @Test
    public void testCsvColumnTypesUnderParseHeader() {
        stageLocalFile("st/csv/", "wide.csv", WIDE_CSV);
        assertEquals(Arrays.asList(
            "c_int | NUMBER(1, 0) | TRUE | $1::NUMBER(1, 0) | csv/wide.csv | 0",
            "c_big | NUMBER(12, 0) | TRUE | $2::NUMBER(12, 0) | csv/wide.csv | 1",
            "c_neg | NUMBER(2, 0) | TRUE | $3::NUMBER(2, 0) | csv/wide.csv | 2",
            "c_dec | NUMBER(4, 2) | TRUE | $4::NUMBER(4, 2) | csv/wide.csv | 3",
            "c_exp | REAL | TRUE | $5::REAL | csv/wide.csv | 4",
            "c_bool | BOOLEAN | TRUE | $6::BOOLEAN | csv/wide.csv | 5",
            "c_date | DATE | TRUE | $7::DATE | csv/wide.csv | 6",
            "c_ts | TIMESTAMP_NTZ | TRUE | $8::TIMESTAMP_NTZ | csv/wide.csv | 7",
            "c_tstz | TIMESTAMP_NTZ | TRUE | $9::TIMESTAMP_NTZ | csv/wide.csv | 8",
            "c_time | TIME | TRUE | $10::TIME | csv/wide.csv | 9",
            "c_text | TEXT | TRUE | $11::TEXT | csv/wide.csv | 10",
            "c_empty | TEXT | TRUE | $12::TEXT | csv/wide.csv | 11",
            "c_mix | TEXT | TRUE | $13::TEXT | csv/wide.csv | 12",
            "c_intdec | NUMBER(2, 1) | TRUE | $14::NUMBER(2, 1) | csv/wide.csv | 13",
            "c_lead0 | NUMBER(2, 0) | TRUE | $15::NUMBER(2, 0) | csv/wide.csv | 14",
            "c_yes | BOOLEAN | TRUE | $16::BOOLEAN | csv/wide.csv | 15",
            "c_huge | TEXT | TRUE | $17::TEXT | csv/wide.csv | 16"),
            infer("LOCATION => '@st/csv/', FILE_FORMAT => 'ffh'"));
    }

    @Test
    public void testCsvWithoutParseHeaderNamesColumnsByPosition() {
        stageLocalFile("st/n/", "noheader.csv", "1,x,2020-01-01\n2,y,2020-01-02\n");
        assertEquals(Arrays.asList(
            "c1 | NUMBER(1, 0) | TRUE | $1::NUMBER(1, 0) | n/noheader.csv | 0",
            "c2 | TEXT | TRUE | $2::TEXT | n/noheader.csv | 1",
            "c3 | DATE | TRUE | $3::DATE | n/noheader.csv | 2"),
            infer("LOCATION => '@st/n/', FILE_FORMAT => 'ffn'"));
        assertEquals(Arrays.asList(
            "C1 | NUMBER(1, 0) | TRUE | $1::NUMBER(1, 0) | n/noheader.csv | 0",
            "C2 | TEXT | TRUE | $2::TEXT | n/noheader.csv | 1",
            "C3 | DATE | TRUE | $3::DATE | n/noheader.csv | 2"),
            infer("LOCATION => '@st/n/', FILE_FORMAT => 'ffn', IGNORE_CASE => TRUE"));
        // SKIP_HEADER only skips lines: the remaining one reads y as a BOOLEAN.
        engine.execute("CREATE FILE FORMAT ffskip TYPE = CSV SKIP_HEADER = 1");
        assertEquals(Arrays.asList(
            "c1 | NUMBER(1, 0) | TRUE | $1::NUMBER(1, 0) | n/noheader.csv | 0",
            "c2 | BOOLEAN | TRUE | $2::BOOLEAN | n/noheader.csv | 1",
            "c3 | DATE | TRUE | $3::DATE | n/noheader.csv | 2"),
            infer("LOCATION => '@st/n/', FILE_FORMAT => 'ffskip'"));
        // A header read as data is text.
        stageLocalFile("st/h/", "small.csv", "a,b\n1,x\n2,y\n");
        assertEquals(Arrays.asList(
            "c1 | TEXT | TRUE | $1::TEXT | h/small.csv | 0",
            "c2 | TEXT | TRUE | $2::TEXT | h/small.csv | 1"),
            infer("LOCATION => '@st/h/', FILE_FORMAT => 'ffn'"));
    }

    @Test
    public void testCsvNumberScalesAndZeros() {
        stageLocalFile("st/z/", "zeros.csv", """
            z1,z2,z3,z4,z5,z6,z7,z8,z9,z10,z11,z12,z13,z14
            2.0,10.0,0.0,-0.0,0.00,0.10,00.5,-00.50,1.000,+0.0,100,-0.5,1.,-.25
            """);
        assertEquals(Arrays.asList(
            "z1 | NUMBER(2, 1)", "z2 | NUMBER(3, 1)", "z3 | NUMBER(1, 0)", "z4 | NUMBER(1, 0)", "z5 | NUMBER(1, 0)",
            "z6 | NUMBER(3, 2)", "z7 | NUMBER(2, 1)", "z8 | NUMBER(3, 2)", "z9 | NUMBER(4, 3)", "z10 | NUMBER(1, 0)",
            "z11 | NUMBER(3, 0)", "z12 | NUMBER(2, 1)", "z13 | NUMBER(1, 0)", "z14 | NUMBER(3, 2)"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/z/', FILE_FORMAT => 'ffh'))"
                + " ORDER BY ORDER_ID"));
    }

    @Test
    public void testCsvTypesWidenInValueOrder() {
        stageLocalFile("st/e/", "edges.csv", """
            dt_ts,ts_dt,r_i,i_r,b_1,i_b,w38,n_big,e_i,dt_n,tm_tm,hex,nan,ovf,spc,dt3,dt5
            2020-01-01,2020-01-01 10:00:00,1e2,1,true,1,99999999999999999999999999999999999999,1,,2020-01-01,10:00,0x1F,NaN,1e400, 5,31-Jan-2020,01/31/2020
            2020-01-01 10:00:00,2020-01-01,1,1e2,1,true,0.5,12345678901234567890123456789012345678901,5,1,10:00:00.5,0x2,inf,1,6 ,01-Feb-2020,02/01/2020
            """);
        assertEquals(Arrays.asList(
            "dt_ts | DATE", "ts_dt | TEXT", "r_i | REAL", "i_r | TEXT", "b_1 | BOOLEAN", "i_b | TEXT",
            "w38 | NUMBER(38, 0)", "n_big | TEXT", "e_i | NUMBER(1, 0)", "dt_n | TEXT", "tm_tm | TIME",
            "hex | REAL", "nan | REAL", "ovf | TEXT", "spc | TEXT", "dt3 | DATE", "dt5 | DATE"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/e/', FILE_FORMAT => 'ffh'))"
                + " ORDER BY ORDER_ID"));
        // Only the first record is read: each column's first value alone.
        assertEquals(Arrays.asList(
            "dt_ts | DATE", "ts_dt | TIMESTAMP_NTZ", "r_i | REAL", "i_r | NUMBER(1, 0)", "b_1 | BOOLEAN",
            "i_b | NUMBER(1, 0)", "w38 | NUMBER(38, 0)", "n_big | NUMBER(1, 0)", "e_i | TEXT", "dt_n | DATE",
            "tm_tm | TIME", "hex | REAL", "nan | REAL", "ovf | TEXT", "spc | TEXT", "dt3 | DATE", "dt5 | DATE"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/e/', FILE_FORMAT => 'ffh',"
                + " MAX_RECORDS_PER_FILE => 1)) ORDER BY ORDER_ID"));
    }

    @Test
    public void testFilesMergeByTypeAndFileNamesListEveryFile() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        stageLocalFile("st/two/", "small2.csv", "a,b,c\n10,hello,2020-01-01\n20,,2020-01-02\n");
        assertEquals(Arrays.asList(
            "a | NUMBER(2, 0) | TRUE | $1::NUMBER(2, 0) | two/small.csv, two/small2.csv | 0",
            "b | TEXT | TRUE | $2::TEXT | two/small.csv, two/small2.csv | 1",
            "c | DATE | TRUE | $3::DATE | two/small2.csv | 2"),
            infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh'"));
        // Across files types meet by family alone: a DATE file beside a TIMESTAMP one, or a REAL beside a
        // NUMBER, is TEXT, where one file reading the same values in that order keeps the first type.
        stageLocalFile("st/ab/", "filea.csv", "x,y\n1e2,2020-01-01\n");
        stageLocalFile("st/ab/", "fileb.csv", "x,y\n1,2020-01-01 10:00:00\n");
        assertEquals(Arrays.asList("x | TEXT", "y | TEXT"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/ab/', FILE_FORMAT => 'ffh'))"
                + " ORDER BY ORDER_ID"));
        // A column without a value in one file takes the other file's type.
        stageLocalFile("st/nn/", "nullfirst.csv", "x,y\n,1\n");
        stageLocalFile("st/nn/", "nullsecond.csv", "x,y\n5,2\n");
        assertEquals(Arrays.asList("x | NUMBER(1, 0)", "y | NUMBER(1, 0)"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/nn/', FILE_FORMAT => 'ffh'))"
                + " ORDER BY ORDER_ID"));
    }

    @Test
    public void testLocationPathFilesAndCountsSelectTheFiles() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        stageLocalFile("st/two/", "small2.csv", "a,b,c\n10,hello,2020-01-01\n20,,2020-01-02\n");
        final List<String> small = Arrays.asList(
            "a | NUMBER(1, 0) | TRUE | $1::NUMBER(1, 0) | two/small.csv | 0",
            "b | TEXT | TRUE | $2::TEXT | two/small.csv | 1");
        final List<String> small2 = Arrays.asList(
            "a | NUMBER(2, 0) | TRUE | $1::NUMBER(2, 0) | two/small2.csv | 0",
            "b | TEXT | TRUE | $2::TEXT | two/small2.csv | 1",
            "c | DATE | TRUE | $3::DATE | two/small2.csv | 2");
        assertEquals(small, infer("LOCATION => '@st/two/small.csv', FILE_FORMAT => 'ffh'"));
        assertEquals(small2, infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh', FILES => ('small2.csv')"));
        assertEquals(small, infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh', FILES => 'small.csv'"));
        assertEquals(small, infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh', FILES => ('small.csv', 'small.csv')"));
        // The files are read in path order, however the FILES list orders them.
        assertEquals(small, infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh',"
            + " FILES => ('small2.csv', 'small.csv'), MAX_FILE_COUNT => 1"));
        assertEquals(small, infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh', MAX_FILE_COUNT => 1"));
        // The path is a prefix of the stage-relative names, matched in its case.
        assertEquals(3, infer("LOCATION => '@st/two/sm', FILE_FORMAT => 'ffh'").size());
        assertEquals(0, infer("LOCATION => '@st/TWO/', FILE_FORMAT => 'ffh'").size());
        assertEquals(0, infer("LOCATION => '@st/nothing/', FILE_FORMAT => 'ffh'").size());
        assertEquals(small, infer("LOCATION => '@TEST_SCHEMA.ST/two/small.csv', FILE_FORMAT => 'TEST_SCHEMA.FFH'"));
        final RuntimeException missing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                infer("LOCATION => '@st/two/', FILE_FORMAT => 'ffh', FILES => ('small.csv', 'missing.csv')");
            }
        });
        assertTrue(missing.getMessage().startsWith("Remote file '"), missing.getMessage());
        assertTrue(missing.getMessage().endsWith("/two/missing.csv' was not found. There are several potential"
            + " causes. The file might not exist. The required credentials may be missing or invalid. If you are"
            + " running a copy command, please make sure files are not deleted when they are being loaded or files"
            + " are not being loaded into two different tables concurrently with auto purge option.\n"
            + "  File 'two/missing.csv'\n  Row 0 starts at line 0, column "), missing.getMessage());
    }

    @Test
    public void testTableStageAndEmptyStage() {
        engine.execute("CREATE STAGE empty_st");
        assertEquals(0, infer("LOCATION => '@empty_st', FILE_FORMAT => 'ffh'").size());
        engine.execute("CREATE TABLE tt (a INT)");
        assertEquals(0, infer("LOCATION => '@%tt', FILE_FORMAT => 'ffh'").size());
        stageLocalFile("%tt", "small.csv", "a,b\n1,x\n2,y\n");
        assertEquals(Arrays.asList(
            "a | NUMBER(1, 0) | TRUE | $1::NUMBER(1, 0) | @TT/small.csv | 0",
            "b | TEXT | TRUE | $2::TEXT | @TT/small.csv | 1"),
            infer("LOCATION => '@%tt', FILE_FORMAT => 'ffh'"));
    }

    @Test
    public void testJsonColumnsFollowSortedKeys() {
        stageLocalFile("st/j/", "j1.json", J1_JSON);
        assertEquals(Arrays.asList(
            "Mixed | TEXT | TRUE | $1:Mixed::TEXT | j/j1.json | 0",
            "a | NUMBER(1, 0) | TRUE | $1:a::NUMBER(1, 0) | j/j1.json | 1",
            "b | TEXT | TRUE | $1:b::TEXT | j/j1.json | 2",
            "big | NUMBER(30, 0) | TRUE | $1:big::NUMBER(30, 0) | j/j1.json | 3",
            "c | NUMBER(4, 2) | TRUE | $1:c::NUMBER(4, 2) | j/j1.json | 4",
            "d | BOOLEAN | TRUE | $1:d::BOOLEAN | j/j1.json | 5",
            "e | TEXT | TRUE | $1:e::TEXT | j/j1.json | 6",
            "extra | NUMBER(1, 0) | TRUE | $1:extra::NUMBER(1, 0) | j/j1.json | 7",
            "f | ARRAY | TRUE | $1:f::ARRAY | j/j1.json | 7",
            "g | OBJECT | TRUE | $1:g::OBJECT | j/j1.json | 8",
            "h | DATE | TRUE | $1:h::DATE | j/j1.json | 9",
            "i | TIMESTAMP_NTZ | TRUE | $1:i::TIMESTAMP_NTZ | j/j1.json | 10",
            "j | REAL | TRUE | $1:j::REAL | j/j1.json | 11",
            "k | TIME | TRUE | $1:k::TIME | j/j1.json | 12"),
            infer("LOCATION => '@st/j/', FILE_FORMAT => 'fj'"));
        assertEquals(Arrays.asList("Mixed | NUMBER(1, 0)", "c | NUMBER(2, 1)"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/j/', FILE_FORMAT => 'fj',"
                + " MAX_RECORDS_PER_FILE => 1)) WHERE COLUMN_NAME IN ('Mixed', 'c') ORDER BY ORDER_ID"));
    }

    @Test
    public void testJsonValueFamiliesAndScales() {
        stageLocalFile("st/p/", "pairs.json", """
            {"i_r":1,"d_r":2.5,"int39":999999999999999999999999999999999999999,"dt_ts":"2020-01-01","ts_dt":"2020-01-01 10:00:00","a_o":[1],"sn":"123","sb":"true","n_i":null,"z0":0.0,"z1":0.00,"trail":1.50,"exp":1E2,"tm":"10:00","slash":"2020/01/31"}
            {"i_r":1e2,"d_r":1e2,"int39":1,"dt_ts":"2020-01-01 10:00:00","ts_dt":"2020-01-01","a_o":{"k":1},"sn":"456","sb":"false","n_i":5,"z0":0.0,"z1":0.00,"trail":2.0,"exp":5e0,"tm":"23:59","slash":"2020/02/01"}
            """);
        assertEquals(Arrays.asList(
            "a_o | TEXT", "d_r | TEXT", "dt_ts | DATE", "exp | REAL", "i_r | TEXT", "int39 | TEXT",
            "n_i | NUMBER(1, 0)", "sb | TEXT", "slash | TEXT", "sn | TEXT", "tm | TIME", "trail | NUMBER(3, 2)",
            "ts_dt | TEXT", "z0 | NUMBER(2, 1)", "z1 | NUMBER(3, 2)"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/p/', FILE_FORMAT => 'fj'))"
                + " ORDER BY ORDER_ID"));
    }

    @Test
    public void testJsonIgnoreCaseFoldsNamesAndReadsThemCaseBlind() {
        stageLocalFile("st/k/", "j2.json", J2_JSON);
        assertEquals(Arrays.asList(
            "B | TEXT | TRUE | $1:B::TEXT | k/j2.json | 0",
            "COUNTRY | TEXT | TRUE | $1:COUNTRY::TEXT | k/j2.json | 1",
            "b | TEXT | TRUE | $1:b::TEXT | k/j2.json | 1",
            "a | NUMBER(2, 1) | TRUE | $1:a::NUMBER(2, 1) | k/j2.json | 2",
            "country | TEXT | TRUE | $1:country::TEXT | k/j2.json | 3"),
            infer("LOCATION => '@st/k/', FILE_FORMAT => 'fjs'"));
        assertEquals(Arrays.asList(
            "B | TEXT | TRUE | GET_IGNORE_CASE($1, 'B')::TEXT | k/j2.json | 0",
            "COUNTRY | TEXT | TRUE | GET_IGNORE_CASE($1, 'COUNTRY')::TEXT | k/j2.json | 1",
            "A | NUMBER(2, 1) | TRUE | GET_IGNORE_CASE($1, 'A')::NUMBER(2, 1) | k/j2.json | 2"),
            infer("LOCATION => '@st/k/', FILE_FORMAT => 'fjs', IGNORE_CASE => TRUE"));
        // Keys appear in EXPRESSION exactly as written, quotes and spaces included.
        stageLocalFile("st/q/", "keys.json", "{\"my key\": 1, \"a.b\": 2, \"q'x\": 3}\n");
        assertEquals(Arrays.asList("$1:a.b::NUMBER(1, 0)", "$1:my key::NUMBER(1, 0)", "$1:q'x::NUMBER(1, 0)"),
            lines("SELECT EXPRESSION FROM TABLE(INFER_SCHEMA(LOCATION => '@st/q/', FILE_FORMAT => 'fj'))"
                + " ORDER BY ORDER_ID"));
    }

    @Test
    public void testJsonRecordsMustBeObjects() {
        stageLocalFile("st/arr/", "j2.json", J2_JSON);
        assertRefused("Schema Inference failed: ARRAY detected instead of an OBJECT. Please consider"
            + " STRIP_OUTER_ARRAY or validate the JSON data", "LOCATION => '@st/arr/', FILE_FORMAT => 'fj'");
        stageLocalFile("st/num/", "num.json", "1.5\n");
        assertRefused("Schema Inference failed: FIXED detected instead of an OBJECT. Please validate the JSON data"
            + " or use CSV file format", "LOCATION => '@st/num/', FILE_FORMAT => 'fj'");
        stageLocalFile("st/str/", "str.json", "\"x\"\n");
        assertRefused("Schema Inference failed: TEXT detected instead of an OBJECT. Please validate the JSON data"
            + " or use CSV file format", "LOCATION => '@st/str/', FILE_FORMAT => 'fj'");
        stageLocalFile("st/nul/", "nul.json", "null\n{\"a\":1}\n");
        assertRefused("Schema Inference failed: NULL_VALUE detected instead of an OBJECT. Please validate the JSON"
            + " data or use CSV file format", "LOCATION => '@st/nul/', FILE_FORMAT => 'fj'");
    }

    @Test
    public void testCsvHeaderRefusals() {
        stageLocalFile("st/dup/", "hdr_dup.csv", "a,A,a,b\n1,2,3,4\n");
        assertRefused("Error with CSV header: duplicated column names \"a\" is not allowed in the header\n"
            + "  File 'dup/hdr_dup.csv'\n  Row 0 starts at line 0, column ",
            "LOCATION => '@st/dup/', FILE_FORMAT => 'ffh'");
        stageLocalFile("st/s/", "short.csv", "a,b,c\n1,2\n");
        assertRefused("Error with CSV header: header defined 3 columns while data contains 2 columns. \n\n"
            + "  File 's/short.csv'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/s/', FILE_FORMAT => 'ffh'");
        stageLocalFile("st/l/", "long.csv", "a,b\n\"1\",\"x, y\"\n");
        assertRefused("Error with CSV header: header defined (2) columns while data contains more columns\n"
            + "  File 'l/long.csv', line 2, character 8\n  Row 1, column \"TRANSIENT_STAGE_TABLE\"[2]\n"
            + "  File 'l/long.csv'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/l/', FILE_FORMAT => 'ffh'");
        // With the enclosure declared the same record fits the header.
        engine.execute("CREATE FILE FORMAT ffq TYPE = CSV PARSE_HEADER = TRUE FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        assertEquals(Arrays.asList("a | NUMBER(1, 0)", "b | TEXT"),
            lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/l/', FILE_FORMAT => 'ffq'))"
                + " ORDER BY ORDER_ID"));
        // A header alone, like an empty file, names no column.
        stageLocalFile("st/ho/", "hdronly.csv", "a,b\n");
        assertEquals(0, infer("LOCATION => '@st/ho/', FILE_FORMAT => 'ffh'").size());
    }

    @Test
    public void testIcebergKindRefusesConflicts() {
        stageLocalFile("st/ic1/", "ic1.csv", "x\n1\n1e2\n");
        assertRefused("Incompatible data types detected: REAL and FIXED.",
            "LOCATION => '@st/ic1/', FILE_FORMAT => 'ffh', KIND => 'ICEBERG'");
        stageLocalFile("st/ic2/", "ic2.csv", "x\ntrue\n1\n");
        assertEquals(Arrays.asList("x | BOOLEAN"), lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA("
            + "LOCATION => '@st/ic2/', FILE_FORMAT => 'ffh', KIND => 'iceberg'))"));
        stageLocalFile("st/ic3/", "small.csv", "a,b\n1,x\n2,y\n");
        assertEquals(Arrays.asList("a | NUMBER(1, 0)", "b | TEXT"), lines("SELECT COLUMN_NAME, TYPE FROM"
            + " TABLE(INFER_SCHEMA(LOCATION => '@st/ic3/', FILE_FORMAT => 'ffh', KIND => 'ICEBERG')) ORDER BY ORDER_ID"));
        stageLocalFile("st/ic4/", "ic4.json", "{\"x\":1}\n{\"x\":\"s\"}\n");
        assertRefused("Incompatible data types detected: TEXT and FIXED.",
            "LOCATION => '@st/ic4/', FILE_FORMAT => 'fj', KIND => 'ICEBERG'");
    }

    @Test
    public void testUsingTemplateCreatesTheInferredColumns() {
        stageLocalFile("st/cc/", "clean.csv", "i,d,r,b,dt,ts,tm,s,e\n1,2.5,1e2,true,2020-01-01,2020-01-01 10:00:00,10:00:00,x,\n");
        engine.execute("CREATE TABLE tcsv USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*)) FROM TABLE("
            + "INFER_SCHEMA(LOCATION => '@st/cc/', FILE_FORMAT => 'ffh')))");
        assertEquals(Arrays.asList("i | NUMBER(1,0) | Y", "d | NUMBER(2,1) | Y", "r | FLOAT | Y", "b | BOOLEAN | Y",
            "dt | DATE | Y", "ts | TIMESTAMP_NTZ(9) | Y", "tm | TIME(9) | Y", "s | VARCHAR(16777216) | Y",
            "e | VARCHAR(16777216) | Y"), describe("tcsv"));
        engine.execute("COPY INTO tcsv FROM @st/cc/ FILE_FORMAT = (FORMAT_NAME = 'ffh')"
            + " MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE");
        assertEquals(Arrays.asList("1 | 2.5 | TRUE | x | NULL"),
            lines("SELECT \"i\", \"d\", \"b\", \"s\", \"e\" FROM tcsv"));
        stageLocalFile("st/cj/", "clean.json", "{\"i\":1,\"d\":2.5,\"a\":[1],\"o\":{\"k\":1},\"n\":null}\n");
        engine.execute("CREATE TABLE tjson USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*)) WITHIN GROUP"
            + " (ORDER BY ORDER_ID) FROM TABLE(INFER_SCHEMA(LOCATION => '@st/cj/', FILE_FORMAT => 'fj',"
            + " IGNORE_CASE => TRUE)))");
        assertEquals(Arrays.asList("A | ARRAY | Y", "D | NUMBER(2,1) | Y", "I | NUMBER(1,0) | Y",
            "N | VARCHAR(16777216) | Y", "O | OBJECT | Y"), describe("tjson"));
    }

    @Test
    public void testArgumentShapeRefusals() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        assertRefused("SQL compilation error: error line 1 at position 20\nnot enough arguments for function"
            + " [INFER_SCHEMA], expected 2, got 1", "LOCATION => '@st/two/'");
        assertRefused("SQL compilation error: error line 1 at position 20\nnot enough arguments for function"
            + " [INFER_SCHEMA], expected 2, got 0", "");
        assertRefused("SQL compilation error: error line 1 at position 20\nmissing required arguments for function"
            + " [INFER_SCHEMA]. The argument name with arrow (e.g. \"LOCATION=>\") is also required in the query"
            + " text.", "'@st/two/', 'ffh'");
        assertRefused("SQL compilation error: error line 1 at position 20\nmissing required argument [FILE_FORMAT]"
            + " for function [INFER_SCHEMA]", "LOCATION => '@st/two/', IGNORE_CASE => TRUE");
        assertRefused("SQL compilation error: error line 1 at position 20\nmissing required argument [LOCATION]"
            + " for function [INFER_SCHEMA]", "LOCATION => NULL, FILE_FORMAT => 'ffh'");
        assertRefused("SQL compilation error: error line 1 at position 20\ninvalid argument for function"
            + " [INFER_SCHEMA] unexpected argument [BOGUS] at position 3,",
            "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', BOGUS => 1");
        assertRefused("SQL compilation error:\nduplicate property 'LOCATION';",
            "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', LOCATION => '@st'");
        assertRefused("SQL compilation error: Invalid value 'st/two/' provided for argument LOCATION of function"
            + " INFER_SCHEMA. Please use a named stage location.", "LOCATION => 'st/two/', FILE_FORMAT => 'ffh'");
        assertRefused("SQL compilation error: Invalid value 'null' provided for argument LOCATION of function"
            + " INFER_SCHEMA. Please use a named stage location.", "LOCATION => 5, FILE_FORMAT => 'ffh'");
        assertRefused("SQL compilation error:\nmissing stage name in URL: @", "LOCATION => '@', FILE_FORMAT => 'ffh'");
        assertRefused(hinted("SQL compilation error:\nStage 'TEST_DB.TEST_SCHEMA.NOST' does not exist or not"
            + " authorized."), "LOCATION => '@nost/two/', FILE_FORMAT => 'ffh'");
        assertRefused(hinted("SQL compilation error:\nFile format 'NOPE' does not exist or not authorized."),
            "LOCATION => '@st/two/', FILE_FORMAT => 'nope'");
        assertRefused(hinted("SQL compilation error:\nSchema 'TEST_DB.NOSCHEMA' does not exist or not authorized."),
            "LOCATION => '@noschema.st', FILE_FORMAT => 'ffh'");
        assertRefused("SQL compilation error:\ninvalid type [NUMBER(1,0)] for parameter 'FILE_FORMAT'",
            "LOCATION => '@st/two/', FILE_FORMAT => 5");
    }

    @Test
    public void testArgumentValueRefusals() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        final String call = "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', ";
        assertRefused("SQL compilation error:\ninvalid type [VARCHAR(3)] for parameter 'IGNORE_CASE'",
            call + "IGNORE_CASE => 'yes'");
        assertRefused("SQL compilation error:\ninvalid type [NUMBER(1,0)] for parameter 'IGNORE_CASE'",
            call + "IGNORE_CASE => 1");
        assertRefused("SQL compilation error:\ninvalid value '0' for property 'MAX_FILE_COUNT'", call + "MAX_FILE_COUNT => 0");
        assertRefused("SQL compilation error:\ninvalid value '-1' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => -1");
        assertRefused("SQL compilation error:\ninvalid value '1.5' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => 1.5");
        assertRefused("SQL compilation error:\ninvalid value ''1'' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => '1'");
        assertRefused("SQL compilation error:\ninvalid value 'NULL' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => NULL");
        assertRefused("SQL compilation error:\ninvalid value '0' for property 'MAX_RECORDS_PER_FILE'",
            call + "MAX_RECORDS_PER_FILE => 0");
        assertRefused("SQL compilation error:\ninvalid value ''BOGUS'' for property 'KIND'", call + "KIND => 'BOGUS'");
        assertRefused("SQL compilation error:\ninvalid value 'UPPER('iceberg')' for property 'KIND'",
            call + "KIND => UPPER('iceberg')");
        assertRefused("SQL compilation error:\ninvalid value '\"+\"(1, 1)' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => 1+1");
        assertRefused("SQL compilation error:\ninvalid value '\"UNARY PLUS\"(2)' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => +2");
        assertRefused("SQL compilation error:\ninvalid value '\"||\"('small', '.csv')' for property 'FILES'",
            call + "FILES => ('small' || '.csv')");
        assertRefused("SQL compilation error:\ninvalid value 'ARRAY_CONSTRUCT('small.csv')' for property 'FILES'",
            call + "FILES => ['small.csv']");
        assertRefused("SQL compilation error:\ninvalid value 'ROW(1, 2)' for property 'MAX_FILE_COUNT'",
            call + "MAX_FILE_COUNT => (1, 2)");
        assertRefused("SQL compilation error:\ninvalid value 'UPPER('ffh')' for property 'FILE_FORMAT'",
            "LOCATION => '@st/two/', FILE_FORMAT => UPPER('ffh')");
        // Accepted spellings: a whole-number decimal count, KIND in any case, a NULL IGNORE_CASE.
        assertEquals(2, infer(call + "MAX_FILE_COUNT => 2.0, KIND => 'standard', IGNORE_CASE => NULL").size());
    }

    @Test
    public void testRefusalOrder() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        engine.execute("CREATE FILE FORMAT fx TYPE = XML");
        assertRefused(hinted("SQL compilation error:\nStage 'TEST_DB.TEST_SCHEMA.NOST' does not exist or not"
            + " authorized."), "LOCATION => '@nost', FILE_FORMAT => 'nope'");
        assertRefused(hinted("SQL compilation error:\nFile format 'NOPE' does not exist or not authorized."),
            "LOCATION => '@st/two/', FILE_FORMAT => 'nope', MAX_FILE_COUNT => 0");
        assertRefused("SQL compilation error:\ninvalid value '0' for property 'MAX_FILE_COUNT'",
            "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', IGNORE_CASE => 'x', MAX_FILE_COUNT => 0");
        assertRefused("SQL compilation error:\ninvalid value '0' for property 'MAX_FILE_COUNT'",
            "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', MAX_RECORDS_PER_FILE => 0, MAX_FILE_COUNT => 0");
        assertRefused("SQL compilation error:\ninvalid value ''BOGUS'' for property 'KIND'",
            "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', IGNORE_CASE => 'x', KIND => 'BOGUS'");
        assertRefused("SQL compilation error:\ninvalid type [VARCHAR(1)] for parameter 'IGNORE_CASE'",
            "LOCATION => '@st/two/', FILE_FORMAT => 'fx', IGNORE_CASE => 'x'");
        assertRefused("SQL compilation error:\nInvalid file format XML", "LOCATION => '@st/two/', FILE_FORMAT => 'fx'");
    }

    @Test
    public void testListValueIsRefusedByOtherTableFunctions() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT => ('a','b')))");
            }
        });
        assertEquals("SQL compilation error:\ninvalid type [ROW(VARCHAR(1), VARCHAR(1))] for parameter 'INPUT'",
            refused.getMessage());
    }

    @Test
    public void testOutputColumnsAndPlanning() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        final ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(INFER_SCHEMA(LOCATION => '@st/two/', FILE_FORMAT => 'ffh'))");
        final List<String> names = new ArrayList<String>();
        for (int i = 0; i < result.getColumns().size(); i++) {
            names.add(result.getColumns().get(i).getName().toUpperCase(Locale.ROOT));
        }
        assertEquals(Arrays.asList("COLUMN_NAME", "TYPE", "NULLABLE", "EXPRESSION", "FILENAMES", "ORDER_ID"), names);
        assertEquals(Arrays.asList("2"), lines("SELECT COUNT(*) FROM TABLE(INFER_SCHEMA(LOCATION => '@st/two/',"
            + " FILE_FORMAT => 'ffh'))"));
        assertEquals(Arrays.asList("b | 1"), lines("SELECT COLUMN_NAME, ORDER_ID FROM TABLE(INFORMATION_SCHEMA."
            + "INFER_SCHEMA(location => '@st/two/', file_format => 'ffh')) WHERE ORDER_ID > 0"));
    }

    @Test
    public void testCsvEscapesJoinFieldsAndRecords() {
        // The unenclosed escape takes a delimiter, a newline or itself literally, and keeps any other character.
        stageLocalFile("st/e1/", "esc.csv", "a,b\nx\\,y,1\n");
        stageLocalFile("st/e2/", "escnl.csv", "a,b\n1\\\n2,3\n");
        stageLocalFile("st/e3/", "escbs.csv", "a,b\nx\\\\,1\n");
        stageLocalFile("st/e4/", "esc1.csv", "a,b,c\n\\1,2\\,3,4\n");
        stageLocalFile("st/e5/", "escnull.csv", "a,b\n\\N,1\n5,2\n");
        for (final String path : Arrays.asList("e1", "e2", "e3")) {
            assertEquals(Arrays.asList("a | TEXT", "b | NUMBER(1, 0)"), columnTypes(path, "ffh"), path);
        }
        assertEquals(Arrays.asList("a | TEXT", "b | TEXT", "c | NUMBER(1, 0)"), columnTypes("e4", "ffh"));
        assertEquals(Arrays.asList("a | NUMBER(1, 0)", "b | NUMBER(1, 0)"), columnTypes("e5", "ffh"));
        // Another escape character leaves the backslash an ordinary one; NONE turns escaping off.
        engine.execute("CREATE FILE FORMAT ffpipe TYPE = CSV PARSE_HEADER = TRUE ESCAPE_UNENCLOSED_FIELD = '|'");
        assertRefused("Error with CSV header: header defined (3) columns while data contains more columns\n"
            + "  File 'e4/esc1.csv', line 2, character 9\n  Row 1, column \"TRANSIENT_STAGE_TABLE\"[3]\n"
            + "  File 'e4/esc1.csv'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/e4/', FILE_FORMAT => 'ffpipe'");
        // The header is split without the escape: an escaped delimiter still divides two names.
        stageLocalFile("st/e6/", "eschdr.csv", "a\\,b,c\n1,2\n");
        assertRefused("Error with CSV header: header defined 3 columns while data contains 2 columns. \n\n"
            + "  File 'e6/eschdr.csv'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/e6/', FILE_FORMAT => 'ffh'");
    }

    @Test
    public void testCsvEnclosedFieldsAndDelimiters() {
        engine.execute("CREATE FILE FORMAT ffq TYPE = CSV PARSE_HEADER = TRUE FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE FILE FORMAT ffqe TYPE = CSV PARSE_HEADER = TRUE FIELD_OPTIONALLY_ENCLOSED_BY = '\"'"
            + " ESCAPE = '\\\\'");
        stageLocalFile("st/m1/", "mline.csv", "a,b\n1,\"x\ny\"\n2,z\n");
        stageLocalFile("st/m2/", "dq.csv", "a,b\n\"x\"\"\",1\n");
        stageLocalFile("st/m3/", "after.csv", "a,b\n\"x\" ,1\n");
        stageLocalFile("st/m4/", "inside.csv", "a,b\n \"x\",1\nx\"y,2\n");
        stageLocalFile("st/m5/", "qe.csv", "a,b\n\"x\\\"y\",1\n");
        assertEquals(Arrays.asList("a | NUMBER(1, 0)", "b | TEXT"), columnTypes("m1", "ffq"));
        for (final String path : Arrays.asList("m2", "m3", "m4")) {
            assertEquals(Arrays.asList("a | TEXT", "b | NUMBER(1, 0)"), columnTypes(path, "ffq"), path);
        }
        assertEquals(Arrays.asList("a | TEXT", "b | NUMBER(1, 0)"), columnTypes("m5", "ffqe"));
        // A name may be enclosed across a newline.
        stageLocalFile("st/m6/", "hdrmline.csv", "\"a\nb\",c\n1,2\n");
        assertEquals(Arrays.asList("a\nb | NUMBER(1, 0)", "c | NUMBER(1, 0)"), columnTypes("m6", "ffq"));
        // Delimiters of several characters, a carriage return before a newline, and a carriage return alone.
        engine.execute("CREATE FILE FORMAT ffrd TYPE = CSV PARSE_HEADER = TRUE RECORD_DELIMITER = ';'");
        engine.execute("CREATE FILE FORMAT ffrd2 TYPE = CSV PARSE_HEADER = TRUE RECORD_DELIMITER = '||'");
        engine.execute("CREATE FILE FORMAT fffd2 TYPE = CSV PARSE_HEADER = TRUE FIELD_DELIMITER = '||'");
        engine.execute("CREATE FILE FORMAT ffcr TYPE = CSV PARSE_HEADER = TRUE RECORD_DELIMITER = '\\r'");
        stageLocalFile("st/d1/", "rd.csv", "a,b;1,x\ny;2,z;");
        stageLocalFile("st/d2/", "rd2.csv", "a,b||1,x||2,y||");
        stageLocalFile("st/d3/", "fd2.csv", "a||b\n1||x\n");
        stageLocalFile("st/d4/", "crlf.csv", "a,b\r\n1,x\r\n2,y\r\n");
        stageLocalFile("st/d5/", "cr.csv", "a,b\r1,x\r2,y\r");
        final List<String> numberThenText = Arrays.asList("a | NUMBER(1, 0)", "b | TEXT");
        assertEquals(numberThenText, columnTypes("d1", "ffrd"));
        assertEquals(numberThenText, columnTypes("d2", "ffrd2"));
        assertEquals(numberThenText, columnTypes("d3", "fffd2"));
        assertEquals(numberThenText, columnTypes("d4", "ffh"));
        assertEquals(0, columnTypes("d5", "ffh").size());
        assertEquals(numberThenText, columnTypes("d5", "ffcr"));
    }

    @Test
    public void testCsvRecordRefusals() {
        engine.execute("CREATE FILE FORMAT ffq TYPE = CSV PARSE_HEADER = TRUE FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        engine.execute("CREATE FILE FORMAT ffqn TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        stageLocalFile("st/r1/", "open.csv", "a,b\n1,2\n3,\"x\n");
        assertRefused("matching enclosing character '\"' not found before end of file\n"
            + "  File 'r1/open.csv', line 3, character 3\n  Row 2, column \"TRANSIENT_STAGE_TABLE\"[2]\n"
            + "  File 'r1/open.csv', line 3, character 3\n  Row 0 starts at line 0, column \"TRANSIENT_STAGE_TABLE\"[2]",
            "LOCATION => '@st/r1/', FILE_FORMAT => 'ffq'");
        // A character after a closing enclosure: the header's refusal when the record, read on, is too wide.
        stageLocalFile("st/r2/", "after.csv", "a,b\n\"x\"y,1\n");
        assertRefused("Error with CSV header: error caused more fields in data than fields in header.\n"
            + "Found character 'y' instead of field delimiter ','\n  File 'r2/after.csv', line 2, character 4\n"
            + "  Row 1, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]\n  File 'r2/after.csv'\n"
            + "  Row 0 starts at line 0, column ", "LOCATION => '@st/r2/', FILE_FORMAT => 'ffq'");
        stageLocalFile("st/r3/", "fits.csv", "a,b,c\n1,\"x\"y\n");
        assertRefused("Found character 'y' instead of field delimiter ','\n  File 'r3/fits.csv', line 2, character 6\n"
            + "  Row 1, column \"TRANSIENT_STAGE_TABLE\"[2]\n  File 'r3/fits.csv', line 2, character 6\n"
            + "  Row 0 starts at line 0, column \"TRANSIENT_STAGE_TABLE\"[2]", "LOCATION => '@st/r3/', FILE_FORMAT => 'ffq'");
        stageLocalFile("st/r4/", "last.csv", "a,b\n1,\"x\ny\"z\n");
        assertRefused("Error with CSV header: error caused more fields in data than fields in header.\n"
            + "Found character 'z' instead of record delimiter '\\n'\n  File 'r4/last.csv', line 3, character 3\n"
            + "  Row 1 starts at line 2, column \"TRANSIENT_STAGE_TABLE\"[2]\n  File 'r4/last.csv'\n"
            + "  Row 0 starts at line 0, column ", "LOCATION => '@st/r4/', FILE_FORMAT => 'ffq'");
        stageLocalFile("st/r5/", "wide.csv", "a,b\n1,\"x\ny\",3\n");
        assertRefused("Error with CSV header: header defined (2) columns while data contains more columns\n"
            + "  File 'r5/wide.csv', line 3, character 4\n  Row 1 starts at line 2, column \"TRANSIENT_STAGE_TABLE\"[2]\n"
            + "  File 'r5/wide.csv'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/r5/', FILE_FORMAT => 'ffq'");
        // Without a header every record counts from the first.
        assertRefused("Found character 'y' instead of field delimiter ','\n  File 'r2/after.csv', line 2, character 4\n"
            + "  Row 2, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]\n  File 'r2/after.csv', line 2, character 4\n"
            + "  Row 0 starts at line 0, column \"TRANSIENT_STAGE_TABLE\"[\"$1\":1]",
            "LOCATION => '@st/r2/', FILE_FORMAT => 'ffqn'");
    }

    @Test
    public void testCsvHeaderNamesMustNotBeEmpty() {
        engine.execute("CREATE FILE FORMAT fft TYPE = CSV PARSE_HEADER = TRUE TRIM_SPACE = TRUE");
        engine.execute("CREATE FILE FORMAT ffq TYPE = CSV PARSE_HEADER = TRUE FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
        stageLocalFile("st/n1/", "empty.csv", "a,,c\n1,2,3\n");
        stageLocalFile("st/n2/", "trailing.csv", "a,b,\n1,2,3\n");
        stageLocalFile("st/n3/", "emptydup.csv", "a,,,c\n1,2,3,4\n");
        stageLocalFile("st/n4/", "dupempty.csv", "a,a,,\n1,2,3,4\n");
        stageLocalFile("st/n5/", "space.csv", "a, ,c\n1,2,3\n");
        stageLocalFile("st/n6/", "quoted.csv", "a,\"\",c\n1,2,3\n");
        for (final String[] cell : new String[][] {
            {"n1", "empty.csv", "ffh"}, {"n2", "trailing.csv", "ffh"}, {"n3", "emptydup.csv", "ffh"},
            {"n5", "space.csv", "fft"}, {"n6", "quoted.csv", "ffq"}}) {
            assertRefused("Error with CSV header: empty string in the header is not allowed\n  File '" + cell[0] + "/"
                + cell[1] + "'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/" + cell[0] + "/', FILE_FORMAT => '"
                + cell[2] + "'");
        }
        assertRefused("Error with CSV header: duplicated column names \"a\" is not allowed in the header\n"
            + "  File 'n4/dupempty.csv'\n  Row 0 starts at line 0, column ", "LOCATION => '@st/n4/', FILE_FORMAT => 'ffh'");
        assertEquals(Arrays.asList("a | NUMBER(1, 0)", "  | NUMBER(1, 0)", "c | NUMBER(1, 0)"), columnTypes("n5", "ffh"));
    }

    @Test
    public void testTimesNumbersAndTimestampsAsTheyAreWritten() {
        final List<String> values = Arrays.asList("10:00 PM", "10:00PM", "12:00 AM", "9:30 Am", "10:00:00.123456789 PM",
            "13:00 PM", "0:00 AM", "10 PM", "10:00 A.M.", "23:59:59.1234567891", "10:00:00.", "10:00:00.123+07:00",
            "10:00:00.1 Z", "10:00:00.1-24:00", "10:00:00+07:00", "10:00:00.123+0700", "10:00:00.123+07:00 PM", "1:5",
            "24:00", "23:59:60", ".", "-.", "+.", ".e5", "1.e5", ".5e1", "1e-400", "1e400", "1.7976931348623157e308",
            ".5", "2020-01-01 10:00:00.1234567891", "2020-01-01T10:00:00.", "2020-01-01 10:00 PM", "31-JAN-2020 10:00",
            "31-JAN-2020");
        final List<String> types = Arrays.asList("TIME", "TIME", "TIME", "TIME", "TIME", "TEXT", "TEXT", "TEXT", "TEXT",
            "TIME", "TIME", "TIME", "TIME", "TIME", "TEXT", "TEXT", "TEXT", "TIME", "TEXT", "TEXT", "NUMBER(1, 0)",
            "NUMBER(1, 0)", "NUMBER(1, 0)", "TEXT", "REAL", "REAL", "REAL", "TEXT", "REAL", "NUMBER(2, 1)",
            "TIMESTAMP_NTZ", "TIMESTAMP_NTZ", "TEXT", "TEXT", "DATE");
        final List<String> names = new ArrayList<String>();
        final List<String> expected = new ArrayList<String>();
        for (int i = 0; i < values.size(); i++) {
            names.add("t" + (i + 1));
            expected.add("t" + (i + 1) + " | " + types.get(i));
        }
        stageLocalFile("st/v/", "values.csv", String.join(",", names) + "\n" + String.join(",", values) + "\n");
        assertEquals(expected, columnTypes("v", "ffh"));
        stageLocalFile("st/vj/", "values.json", "{\"a\":\"10:00 PM\",\"b\":\"23:59:59.1234567891\","
            + "\"c\":\"10:00:00+07:00\",\"d\":\"13:00 PM\",\"e\":\"10:00:00.123+07:00\","
            + "\"f\":\"2020-01-01 10:00:00.1234567891\"}\n");
        assertEquals(Arrays.asList("a | TIME", "b | TIME", "c | TEXT", "d | TEXT", "e | TIME", "f | TIMESTAMP_NTZ"),
            columnTypes("vj", "fj"));
    }

    @Test
    public void testJsonDuplicateKeys() {
        stageLocalFile("st/k1/", "dup.json", "{\"a\":1,\"a\":\"x\"}\n");
        assertEquals("Error parsing JSON: duplicate object attribute \"a\"\n  File 'stages/X/k1/dup.json', line 1,"
            + " character 10\n  Row 0, column $1\n  File 'k1/dup.json', line 1, character 10\n"
            + "  Row 0 starts at line 0, column $1", withoutStageId(refusalOf("LOCATION => '@st/k1/', FILE_FORMAT => 'fj'")));
        stageLocalFile("st/k2/", "lines.json", "{\"a\":1,\n \"a\":2}\n");
        assertEquals("Error parsing JSON: duplicate object attribute \"a\"\n  File 'stages/X/k2/lines.json', line 2,"
            + " character 4\n  Row 0 starts at line 1, column $1\n  File 'k2/lines.json', line 2, character 4\n"
            + "  Row 0 starts at line 0, column $1", withoutStageId(refusalOf("LOCATION => '@st/k2/', FILE_FORMAT => 'fj'")));
        stageLocalFile("st/k3/", "second.json", "{\"b\":1}\n{\"a\":1,\"c\":2,\"a\":3}\n");
        assertEquals("Error parsing JSON: duplicate object attribute \"a\"\n  File 'stages/X/k3/second.json', line 2,"
            + " character 16\n  Row 1 starts at line 1, column $1\n  File 'k3/second.json', line 2, character 16\n"
            + "  Row 0 starts at line 0, column $1", withoutStageId(refusalOf("LOCATION => '@st/k3/', FILE_FORMAT => 'fj'")));
        // At any depth, after an escape, and case-sensitively.
        stageLocalFile("st/k4/", "nested.json", "{\"o\":{\"k\":1,\"k\":2}}\n");
        stageLocalFile("st/k5/", "escaped.json", "{\"a\\u0062\":1,\"ab\":2}\n");
        stageLocalFile("st/k6/", "cased.json", "{\"a\":1,\"A\":2,\"a\":3}\n");
        assertTrue(refusalOf("LOCATION => '@st/k4/', FILE_FORMAT => 'fj'").startsWith(
            "Error parsing JSON: duplicate object attribute \"k\"\n  File '"), "nested");
        assertTrue(refusalOf("LOCATION => '@st/k5/', FILE_FORMAT => 'fj'").contains(
            "escaped.json', line 1, character 17\n"), "escaped");
        assertTrue(refusalOf("LOCATION => '@st/k6/', FILE_FORMAT => 'fj'").contains(
            "cased.json', line 1, character 16\n"), "cased");
        // ALLOW_DUPLICATE keeps a key's last value.
        engine.execute("CREATE FILE FORMAT fjdup TYPE = JSON ALLOW_DUPLICATE = TRUE");
        stageLocalFile("st/k7/", "last.json", "{\"a\":\"x\",\"a\":1}\n");
        assertEquals(Arrays.asList("a | NUMBER(1, 0)"), columnTypes("k7", "fjdup"));
        assertEquals(Arrays.asList("a | TEXT"), columnTypes("k1", "fjdup"));
        assertEquals(Arrays.asList("o | OBJECT"), columnTypes("k4", "fjdup"));
    }

    @Test
    public void testFilesJoinTheLocationPath() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        for (final String call : Arrays.asList("LOCATION => '@st/two', FILES => 'small.csv'",
                "LOCATION => '@st/two/', FILES => '/small.csv'", "LOCATION => '@st//two', FILES => 'small.csv'",
                "LOCATION => '@st/two//', FILES => 'small.csv'", "LOCATION => '@st/', FILES => 'two/small.csv'",
                "LOCATION => '@st/two', FILES => ('small.csv', '/small.csv')")) {
            assertEquals(Arrays.asList("a | two/small.csv", "b | two/small.csv"), lines("SELECT COLUMN_NAME, FILENAMES"
                + " FROM TABLE(INFER_SCHEMA(" + call + ", FILE_FORMAT => 'ffh')) ORDER BY ORDER_ID"), call);
        }
        for (final String[] cell : new String[][] {
            {"LOCATION => '@st/tw', FILES => 'o/small.csv'", "tw/o/small.csv"},
            {"LOCATION => '@st/two/small.csv', FILES => 'small.csv'", "two/small.csv/small.csv"},
            {"LOCATION => '@st/two/', FILES => '//small.csv'", "two//small.csv"}}) {
            final String refusal = refusalOf(cell[0] + ", FILE_FORMAT => 'ffh'");
            assertTrue(refusal.startsWith("Remote file '") && refusal.contains("/" + cell[1] + "' was not found.")
                && refusal.endsWith("\n  File '" + cell[1] + "'\n  Row 0 starts at line 0, column "), refusal);
        }
    }

    @Test
    public void testQuotedArgumentNamesAreUnexpected() {
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        assertRefused("SQL compilation error: error line 1 at position 20\ninvalid argument for function"
            + " [INFER_SCHEMA] unexpected argument [\"LOCATION\"] at position 1,",
            "\"location\" => '@st/two/', FILE_FORMAT => 'ffh'");
        assertRefused("SQL compilation error: error line 1 at position 20\ninvalid argument for function"
            + " [INFER_SCHEMA] unexpected argument [\"FILE_FORMAT\"] at position 2,",
            "LOCATION => '@st/two/', \"FILE_FORMAT\" => 'ffh', MAX_FILE_COUNT => 0");
        assertRefused("SQL compilation error: error line 1 at position 20\ninvalid argument for function"
            + " [INFER_SCHEMA] unexpected argument [\"FILES\"] at position 3,",
            "LOCATION => '@st/two/', FILE_FORMAT => 'ffh', \"Files\" => 'small.csv'");
        final RuntimeException flatten = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), \"PATH\" => 'a'))");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 20\ninvalid argument for function [FLATTEN]"
            + " unexpected argument [\"PATH\"] at position 2,", flatten.getMessage());
    }

    @Test
    public void testNotListedAndTemplateRefusals() {
        assertEquals(0, engine.executeQuery("SHOW FUNCTIONS LIKE 'INFER_SCHEMA'").getRowCount());
        stageLocalFile("st/two/", "small.csv", "a,b\n1,x\n2,y\n");
        final String infer = " FROM TABLE(INFER_SCHEMA(LOCATION => '@st/two/', FILE_FORMAT => 'ffh'))";
        for (final String[] cell : new String[][] {
            {"SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*)) FROM TABLE(INFER_SCHEMA(LOCATION => '@st/none/', FILE_FORMAT => 'ffh')))",
                "Invalid template: template must be a non-null JSON array\n"},
            {"SELECT ARRAY_CONSTRUCT()" + infer + " LIMIT 1)", "Invalid template: template must be a non-null JSON array\n"},
            {"SELECT ARRAY_AGG(OBJECT_CONSTRUCT('TYPE', TYPE)) WITHIN GROUP (ORDER BY ORDER_ID)" + infer + ")",
                "Invalid template: COLUMN_NAME field is missing in {\"TYPE\":\"NUMBER(1, 0)\"}\n"},
            {"SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', COLUMN_NAME)) WITHIN GROUP (ORDER BY ORDER_ID)" + infer + ")",
                "Invalid template: TYPE field is missing in {\"COLUMN_NAME\":\"a\"}\n"},
            {"SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', COLUMN_NAME, 'TYPE', TYPE)) WITHIN GROUP (ORDER BY"
                + " ORDER_ID)" + infer + ")", "Invalid template: NULLABLE field is missing in"
                + " {\"COLUMN_NAME\":\"a\",\"TYPE\":\"NUMBER(1, 0)\"}\n"}}) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("CREATE OR REPLACE TABLE tt USING TEMPLATE (" + cell[0]);
                }
            }, cell[0]);
            assertEquals("SQL compilation error:\n" + cell[1], refused.getMessage(), cell[0]);
        }
    }

    /** Each column's name and type, in ORDER_ID order, of the files under a path of stage {@code st}. */
    private List<String> columnTypes(final String path, final String format) {
        return lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/" + path + "/', FILE_FORMAT => '"
            + format + "')) ORDER BY ORDER_ID");
    }

    /** The refusal of a call over the given arguments. */
    private String refusalOf(final String arguments) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFER_SCHEMA(" + arguments + "))");
            }
        }).getMessage();
    }

    /** A JSON refusal with the stage's internal name in its first file line, which only the account knows, blanked. */
    private static String withoutStageId(final String refusal) {
        final int from = refusal.indexOf("File 'stages/") + "File 'stages/".length();
        final int to = refusal.indexOf('/', from);
        return from < "File 'stages/".length() || to < 0 ? refusal : refusal.substring(0, from) + "X" + refusal.substring(to);
    }

    /** INFER_SCHEMA's rows over the given arguments, one line each, in ORDER_ID then COLUMN_NAME order. */
    private List<String> infer(final String arguments) {
        return lines("SELECT COLUMN_NAME, TYPE, NULLABLE, EXPRESSION, FILENAMES, ORDER_ID FROM TABLE(INFER_SCHEMA("
            + arguments + ")) ORDER BY ORDER_ID, COLUMN_NAME");
    }

    /** DESCRIBE TABLE's name, type and null? cells, one line per column. */
    private List<String> describe(final String table) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + table);
        final List<String> described = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            described.add(cell(rs, row, "name") + " | " + cell(rs, row, "type") + " | " + cell(rs, row, "null?"));
        }
        return described;
    }

    /** A query's rows, each cell as text and a FILENAMES-shaped list sorted, joined by {@code " | "}. */
    private List<String> lines(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                line.append(text(row.getValue(i)));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static String text(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean || "true".equalsIgnoreCase(value.toString())
                || "false".equalsIgnoreCase(value.toString())) {
            return value.toString().toUpperCase(Locale.ROOT);
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        final String text = value.toString();
        if (text.contains(".csv, ") || text.contains(".json, ")) {
            final List<String> files = new ArrayList<String>(Arrays.asList(text.split(", ")));
            Collections.sort(files);
            return String.join(", ", files);
        }
        return text;
    }

    private void assertRefused(final String expected, final String arguments) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFER_SCHEMA(" + arguments + "))");
            }
        });
        assertEquals(expected, refused.getMessage());
    }
}
