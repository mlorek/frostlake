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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An Iceberg table's column a column definition typed holds the format's own types (live-verified): GET_DDL spells
 * them in the format's names — INT, LONG, DECIMAL(p, s), FLOAT, DOUBLE, STRING, BINARY — with the table's
 * EXTERNAL_VOLUME, ICEBERG_VERSION and CATALOG after the column list; an int and a long hold 32 and 64 bits, a float
 * 32, and a value past them is refused outside the DML envelope; a change of the column's type is judged by the
 * format's promotions in the format's lowercase names; COMMENT ON ICEBERG TABLE is COMMENT ON TABLE. A column a
 * query or LIKE gave the table holds a table's types and is spelled and judged in a table's names.
 */
public class IcebergFormatTypesTest extends MeasuredCellsTestSupport {

    /**
     * Asserts one statement reads as measured once the eight characters a generated base location ends in are
     * masked as {@code <SUFFIX>}.
     */
    private void assertSuffixedAnswer(final String expected, final String sql) {
        assertEquals(expected, answer(sql).replaceAll("\\.[A-Za-z0-9]{8}/'", ".<SUFFIX>/'"), sql);
    }

    @Test
    public void getDdlSpellsTheFormatsTypesAndAnIntHoldsThirtyTwoBits() {
        engine.execute("CREATE OR REPLACE ICEBERG TABLE ice_g (a INT, b VARCHAR, t TIMESTAMP_NTZ, bi BINARY, m "
            + "MAP(VARCHAR, INT), big BIGINT, d NUMBER(10,2) NOT NULL)");
        assertCells(new String[][] {
            {"SELECT GET_DDL('TABLE', 'ice_g')",
                "create or replace ICEBERG TABLE ICE_G ( | \tA INT, | \tB STRING, | \tT TIMESTAMP_NTZ(6), | \tBI "
                + "BINARY, | \tM MAP(STRING, INT), | \tBIG LONG, | \tD DECIMAL(10, 2) NOT NULL | ) |  "
                + "EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_t (f FLOAT, bo BOOLEAN, dt DATE, tm TIME, tl TIMESTAMP_LTZ, o "
                + "OBJECT(x INT), ar ARRAY(INT))",
                "Table ICE_T successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_t')",
                "create or replace ICEBERG TABLE ICE_T ( | \tF FLOAT, | \tBO BOOLEAN, | \tDT DATE, | \tTM TIME(6), "
                + "| \tTL TIMESTAMP_LTZ(6), | \tO OBJECT(x INT), | \tAR ARRAY(INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_c (a INT DEFAULT 1, b VARCHAR COMMENT 'cb') COMMENT = 'tc' "
                + "CLUSTER BY (a)",
                "Table ICE_C successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_c')",
                "create or replace ICEBERG TABLE ICE_C cluster by (a)( | \tA INT DEFAULT 1, | \tB STRING COMMENT "
                + "'cb' | )COMMENT='tc' |  |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  "
                + "CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_u (u UUID)", "Table ICE_U successfully created."},
            {"CREATE OR REPLACE ICEBERG TABLE ice_v (v VARIANT)",
                "REFUSED SQL Compilation error: | Unsupported data type 'VARIANT' for iceberg tables."},
            {"CREATE OR REPLACE ICEBERG TABLE ice_n (n NUMBER, n2 NUMBER(38,0), n3 NUMBER(18,0), n4 "
                + "NUMBER(9,0), s VARCHAR(10), c CHAR(3))",
                "REFUSED SQL Compilation error: | data type 'NUMBER/DECIMAL' without precision and scale is not "
                + "supported for Iceberg tables."},
            {"SELECT GET_DDL('TABLE', 'ice_n')",
                "REFUSED SQL compilation error: | Table 'ICE_N' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ICE_N."},
            {"DESCRIBE TABLE ice_n",
                "REFUSED SQL compilation error: | Table 'ICE_N' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ICE_N."},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT \"name\", \"type\", \"null?\", \"default\", \"comment\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertCells(new String[][] {
            {"INSERT INTO ice_g (a, d) VALUES (2147483647, 1)", "1"},
            {"INSERT INTO ice_g (a, d) VALUES (2147483648, 1)",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"INSERT INTO ice_g (a, d) VALUES (-2147483649, 1)",
                "REFUSED Number out of representable range: type int, value -2147483649"},
            {"INSERT INTO ice_g (a, d) VALUES (99999999999, 1)",
                "REFUSED DML operation to table ICE_G failed on column A with error: Number out of representable "
                + "range: type FIXED[SB8](10,0){nullable}, value 99999999999"},
        });
        assertShapedAnswer("REFUSED Number out of representable range: type long, value <ID19>",
            "INSERT INTO ice_g (big, d) VALUES (9223372036854775808, 1)");
        assertCells(new String[][] {
            {"INSERT INTO ice_g (a, d) SELECT 2147483648, 1",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"UPDATE ice_g SET a = 2147483648",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN b SET DATA TYPE NUMBER(10,0)",
                "REFUSED SQL compilation error: cannot change column B from type string to decimal(10, 0) | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN a SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column A from type int to string | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN a SET DATA TYPE BIGINT", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN a SET DATA TYPE NUMBER(19,0)",
                "REFUSED SQL compilation error: cannot change column A from type long to decimal(19, 0) | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN a SET DATA TYPE NUMBER(5,0)",
                "REFUSED SQL compilation error: cannot change column A from type long to decimal(5, 0) | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN a SET DATA TYPE INT",
                "REFUSED SQL compilation error: cannot change column A from type long to int | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN big SET DATA TYPE INT",
                "REFUSED SQL compilation error: cannot change column BIG from type long to int | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN d SET DATA TYPE NUMBER(12,2)", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN d SET DATA TYPE NUMBER(9,2)",
                "REFUSED SQL compilation error: cannot change column D from type decimal(12, 2) to decimal(9, 2) | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN d SET DATA TYPE NUMBER(12,3)",
                "REFUSED SQL compilation error: cannot change column D from type decimal(12, 2) to decimal(12, 3) | "},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN t SET DATA TYPE TIMESTAMP_NTZ(3)",
                "REFUSED SQL Compilation error: | Invalid time type scale specified for column 'T' with data type "
                + "'TIMESTAMP_NTZ(3)'. Please consult the Snowflake documentation for supported scales of TIME and "
                + "TIMESTAMP types in Iceberg tables."},
            {"ALTER ICEBERG TABLE ice_g ALTER COLUMN bi SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column BI from type binary to string | "},
        });
        engine.execute("DESCRIBE TABLE ice_g");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\", \"default\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "A|NUMBER(19,0)|Y|null|null ;; B|VARCHAR(134217728)|Y|null|null ;; T|TIMESTAMP_NTZ(6)|Y|null|null "
                + ";; BI|BINARY(67108864)|Y|null|null ;; M|MAP(VARCHAR(134217728), NUMBER(10,0))|Y|null|null ;; "
                + "BIG|NUMBER(19,0)|Y|null|null ;; D|NUMBER(12,2)|N|null|null"},
            {"COMMENT ON ICEBERG TABLE ice_g IS 'x'",
                "REFUSED SQL Compilation error: The table ICE_G is an Iceberg table. Iceberg tables should use "
                + "ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"COMMENT ON TABLE ice_g IS 'x'",
                "REFUSED SQL Compilation error: The table ICE_G is an Iceberg table. Iceberg tables should use "
                + "ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"ALTER TABLE ice_g SET COMMENT = 'x'",
                "REFUSED SQL Compilation error: The table ICE_G is an Iceberg table. Iceberg tables should use "
                + "ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"ALTER ICEBERG TABLE ice_g SET COMMENT = 'y'", "Statement executed successfully."},
            {"COMMENT ON ICEBERG TABLE nosuch_ice IS 'x'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"COMMENT ON COLUMN ice_g.a IS 'ca'",
                "REFUSED SQL Compilation error: The table ICE_G is an Iceberg table. Iceberg tables should use "
                + "ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"COMMENT ON ICEBERG TABLE ice_g IS 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'."},
        });
    }

    @Test
    public void theFormatsPromotionsJudgeATypeChange() {
        engine.execute("CREATE OR REPLACE ICEBERG TABLE ice_k (a INT, n10 NUMBER(10,0), n19 NUMBER(19,0), n38 "
            + "NUMBER(38,0), n9 NUMBER(9,0), f FLOAT, bo BOOLEAN, dt DATE, tm TIME, t TIMESTAMP_NTZ, tl "
            + "TIMESTAMP_LTZ, u UUID, b VARCHAR, bi BINARY, i2 INTEGER, bg BIGINT)");
        engine.execute("CREATE OR REPLACE TABLE plain_k (a INT)");
        assertCells(new String[][] {
            {"SELECT GET_DDL('TABLE', 'ice_k')",
                "create or replace ICEBERG TABLE ICE_K ( | \tA INT, | \tN10 DECIMAL(10, 0), | \tN19 DECIMAL(19, "
                + "0), | \tN38 DECIMAL(38, 0), | \tN9 DECIMAL(9, 0), | \tF FLOAT, | \tBO BOOLEAN, | \tDT DATE, | "
                + "\tTM TIME(6), | \tT TIMESTAMP_NTZ(6), | \tTL TIMESTAMP_LTZ(6), | \tU UUID, | \tB STRING, | \tBI "
                + "BINARY, | \tI2 INT, | \tBG LONG | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = "
                + "2 |  CATALOG = 'SNOWFLAKE';"},
        });
        engine.execute("DESCRIBE TABLE ice_k");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "A|NUMBER(10,0)|Y ;; N10|NUMBER(10,0)|Y ;; N19|NUMBER(19,0)|Y ;; N38|NUMBER(38,0)|Y ;; "
                + "N9|NUMBER(9,0)|Y ;; F|FLOAT|Y ;; BO|BOOLEAN|Y ;; DT|DATE|Y ;; TM|TIME(6)|Y ;; "
                + "T|TIMESTAMP_NTZ(6)|Y ;; TL|TIMESTAMP_LTZ(6)|Y ;; U|UUID|Y ;; B|VARCHAR(134217728)|Y ;; "
                + "BI|BINARY(67108864)|Y ;; I2|NUMBER(10,0)|Y ;; BG|NUMBER(19,0)|Y"},
            {"INSERT INTO ice_k (n10) VALUES (2147483648)", "1"},
            {"INSERT INTO ice_k (n19) VALUES (9223372036854775808)", "1"},
            {"INSERT INTO ice_k (i2) VALUES (2147483648)",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"INSERT INTO ice_k (a) VALUES (-2147483648)", "1"},
            {"MERGE INTO ice_k USING (SELECT 1 k) s ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (2147483648)",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"UPDATE ice_k SET a = a + 1 WHERE a = -2147483648", "1|0"},
            {"UPDATE ice_k SET a = a - 1 WHERE a = -2147483648", "0|0"},
        });
        assertShapedAnswer("REFUSED Number out of representable range: type long, value -<ID19>",
            "INSERT INTO ice_k (bg) VALUES (-9223372036854775809)");
        assertCells(new String[][] {
            {"INSERT INTO ice_k (a) VALUES (2147483647.4)", "1"},
            {"INSERT INTO ice_k (a) VALUES (2147483647.6)",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"INSERT INTO ice_k (a) VALUES ('2147483648')",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN n10 SET DATA TYPE BIGINT",
                "REFUSED SQL compilation error: cannot change column N10 from type decimal(10, 0) to long | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN n10 SET DATA TYPE INT",
                "REFUSED SQL compilation error: cannot change column N10 from type decimal(10, 0) to int | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN a SET DATA TYPE NUMBER(10,0)",
                "REFUSED SQL compilation error: cannot change column A from type int to decimal(10, 0) | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN a SET DATA TYPE INT", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN a SET DATA TYPE INTEGER", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN b SET DATA TYPE VARCHAR", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN b SET DATA TYPE STRING", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN b SET DATA TYPE VARCHAR(10)",
                "REFUSED SQL Compilation error: | For Iceberg tables, only max length (134,217,728) is supported "
                + "for 'VARCHAR(L)/STRING(L)' or use STRING directly."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN f SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column F from type float to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN f SET DATA TYPE DOUBLE", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN bo SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column BO from type boolean to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN dt SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column DT from type date to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN tm SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column TM from type time to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN t SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column T from type timestamp to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN tl SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column TL from type timestamptz to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN u SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column U from type uuid to string | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN b SET DATA TYPE NUMBER",
                "REFUSED SQL Compilation error: | data type 'NUMBER/DECIMAL' without precision and scale is not "
                + "supported for Iceberg tables."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN b SET DATA TYPE VARIANT",
                "REFUSED SQL Compilation error: | Unsupported data type 'VARIANT' for iceberg tables."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN t SET DATA TYPE TIMESTAMP_LTZ",
                "REFUSED SQL compilation error: cannot change column T from type timestamp to timestamptz | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN tl SET DATA TYPE TIMESTAMP_NTZ",
                "REFUSED SQL compilation error: cannot change column TL from type timestamptz to timestamp | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN n38 SET DATA TYPE NUMBER(38,0)",
                "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN n9 SET DATA TYPE NUMBER(10,0)",
                "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN n9 SET DATA TYPE NUMBER(38,0)",
                "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN a SET DATA TYPE FLOAT",
                "REFUSED SQL compilation error: cannot change column A from type int to float | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN dt SET DATA TYPE TIMESTAMP_NTZ",
                "REFUSED SQL compilation error: cannot change column DT from type date to timestamp | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN bi SET DATA TYPE BINARY", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN u SET DATA TYPE UUID", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN i2 SET DATA TYPE NUMBER(19,0)",
                "REFUSED SQL compilation error: cannot change column I2 from type int to decimal(19, 0) | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN bg SET DATA TYPE NUMBER(38,0)",
                "REFUSED SQL compilation error: cannot change column BG from type long to decimal(38, 0) | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN b SET DATA TYPE INT",
                "REFUSED SQL compilation error: cannot change column B from type string to int | "},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN a SET DATA TYPE SMALLINT",
                "REFUSED SQL Compilation error: | Unsupported data type 'SMALLINT' for iceberg tables."},
            {"ALTER ICEBERG TABLE ice_k ALTER COLUMN a SET DATA TYPE BIGINT", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k ADD COLUMN z INT", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_k RENAME COLUMN bg TO bg2", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ice_k')",
                "create or replace ICEBERG TABLE ICE_K ( | \tA LONG, | \tN10 DECIMAL(10, 0), | \tN19 DECIMAL(19, "
                + "0), | \tN38 DECIMAL(38, 0), | \tN9 DECIMAL(38, 0), | \tF DOUBLE, | \tBO BOOLEAN, | \tDT DATE, | "
                + "\tTM TIME(6), | \tT TIMESTAMP_NTZ(6), | \tTL TIMESTAMP_LTZ(6), | \tU UUID, | \tB STRING, | \tBI "
                + "BINARY, | \tI2 INT, | \tBG2 LONG, | \tZ INT | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  "
                + "ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
        });
        engine.execute("DESCRIBE TABLE ice_k");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "A|NUMBER(19,0)|Y ;; N10|NUMBER(10,0)|Y ;; N19|NUMBER(19,0)|Y ;; N38|NUMBER(38,0)|Y ;; "
                + "N9|NUMBER(38,0)|Y ;; F|FLOAT|Y ;; BO|BOOLEAN|Y ;; DT|DATE|Y ;; TM|TIME(6)|Y ;; "
                + "T|TIMESTAMP_NTZ(6)|Y ;; TL|TIMESTAMP_LTZ(6)|Y ;; U|UUID|Y ;; B|VARCHAR(134217728)|Y ;; "
                + "BI|BINARY(67108864)|Y ;; I2|NUMBER(10,0)|Y ;; BG2|NUMBER(19,0)|Y ;; Z|NUMBER(10,0)|Y"},
            {"COMMENT ON ICEBERG TABLE plain_k IS 'x'", "Statement executed successfully."},
            {"COMMENT IF EXISTS ON ICEBERG TABLE nosuch_ice IS 'x'", "Statement executed successfully."},
            {"COMMENT ON TABLE nosuch_ice IS 'x'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ICE."},
            {"COMMENT ON ICEBERG TABLE test_db.test_schema.ice_k IS 'x'",
                "REFUSED SQL Compilation error: The table ICE_K is an Iceberg table. Iceberg tables should use "
                + "ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used."},
            {"COMMENT ON ICEBERG TABLE nosuch_s.nosuch_ice IS 'x'",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S."},
            {"COMMENT ON ICEBERG TABLE ice_k IS 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'."},
            {"CREATE OR REPLACE ICEBERG TABLE ice_p (a INT) STORAGE_SERIALIZATION_POLICY = OPTIMIZED",
                "Table ICE_P successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_p')",
                "create or replace ICEBERG TABLE ICE_P ( | \tA INT | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  "
                + "ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_p (a INT) DATA_RETENTION_TIME_IN_DAYS = 3 CHANGE_TRACKING = TRUE",
                "Table ICE_P successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_p')",
                "create or replace ICEBERG TABLE ICE_P ( | \tA INT | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  "
                + "ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_s (o OBJECT(x INT NOT NULL, y VARCHAR), m MAP(INT, "
                + "ARRAY(VARCHAR)), a2 ARRAY(OBJECT(q BIGINT)))",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.x' contained within a nullable object"},
            {"SELECT GET_DDL('TABLE', 'ice_s')",
                "REFUSED SQL compilation error: | Table 'ICE_S' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ICE_S."},
            {"CREATE OR REPLACE ICEBERG TABLE ice_x AS SELECT a, n10, b, u FROM ice_k",
                "Table ICE_X successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_x')",
                "create or replace ICEBERG TABLE ICE_X ( | \tA NUMBER(19,0), | \tN10 NUMBER(10,0), | \tB "
                + "VARCHAR(134217728), | \tU UUID | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = "
                + "2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_l LIKE ice_k", "Table ICE_L successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_l')",
                "create or replace ICEBERG TABLE ICE_L ( | \tA NUMBER(19,0), | \tN10 NUMBER(10,0), | \tN19 "
                + "NUMBER(19,0), | \tN38 NUMBER(38,0), | \tN9 NUMBER(38,0), | \tF FLOAT, | \tBO BOOLEAN, | \tDT "
                + "DATE, | \tTM TIME(6), | \tT TIMESTAMP_NTZ(6), | \tTL TIMESTAMP_LTZ(6), | \tU UUID, | \tB "
                + "VARCHAR(134217728), | \tBI BINARY(67108864), | \tI2 NUMBER(10,0), | \tBG2 NUMBER(19,0), | \tZ "
                + "NUMBER(10,0) | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = "
                + "'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_cl CLONE ice_s",
                "REFUSED SQL compilation error: | Object 'ICE_S' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ICE_S."},
            {"SELECT GET_DDL('TABLE', 'ice_cl')",
                "REFUSED SQL compilation error: | Table 'ICE_CL' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ICE_CL."},
            {"SELECT GET_DDL('TABLE', 'ice_k', TRUE)",
                "create or replace ICEBERG TABLE TEST_DB.TEST_SCHEMA.ICE_K ( | \tA LONG, | \tN10 DECIMAL(10, 0), | "
                + "\tN19 DECIMAL(19, 0), | \tN38 DECIMAL(38, 0), | \tN9 DECIMAL(38, 0), | \tF DOUBLE, | \tBO "
                + "BOOLEAN, | \tDT DATE, | \tTM TIME(6), | \tT TIMESTAMP_NTZ(6), | \tTL TIMESTAMP_LTZ(6), | \tU "
                + "UUID, | \tB STRING, | \tBI BINARY, | \tI2 INT, | \tBG2 LONG, | \tZ INT | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_q (\"q a\" INT, \"Lo\" VARCHAR)",
                "Table ICE_Q successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_q')",
                "create or replace ICEBERG TABLE ICE_Q ( | \t\"q a\" INT, | \t\"Lo\" STRING | ) |  EXTERNAL_VOLUME "
                + "= 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
        });
    }

    @Test
    public void aQueryOrLikeGivesATablesTypesAndACloneKeepsTheFormats() {
        engine.execute("CREATE OR REPLACE ICEBERG TABLE src_k (a INT, b BIGINT, f FLOAT, d DOUBLE)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE ICEBERG TABLE ice_d (d DOUBLE, dp DOUBLE PRECISION, r REAL, f4 FLOAT4, f8 "
                + "FLOAT8, fl FLOAT, i INTEGER, bi BIGINT, nu NUMERIC(5,1), de DEC(6,2), s STRING, t TEXT, v "
                + "VARCHAR(134217728), bn BINARY(67108864), ts TIMESTAMP, dtm DATETIME, tn TIMESTAMP_NTZ(6), tm "
                + "TIME(6))",
                "Table ICE_D successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_d')",
                "create or replace ICEBERG TABLE ICE_D ( | \tD DOUBLE, | \tDP DOUBLE, | \tR DOUBLE, | \tF4 FLOAT, "
                + "| \tF8 FLOAT, | \tFL FLOAT, | \tI INT, | \tBI LONG, | \tNU DECIMAL(5, 1), | \tDE DECIMAL(6, 2), | "
                + "\tS STRING, | \tT STRING, | \tV STRING, | \tBN BINARY, | \tTS TIMESTAMP_NTZ(6), | \tDTM "
                + "TIMESTAMP_NTZ(6), | \tTN TIMESTAMP_NTZ(6), | \tTM TIME(6) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"ALTER ICEBERG TABLE ice_d ALTER COLUMN d SET DATA TYPE FLOAT",
                "REFUSED SQL compilation error: cannot change column D from type double to float | "},
            {"ALTER ICEBERG TABLE ice_d ALTER COLUMN r SET DATA TYPE DOUBLE", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_d ALTER COLUMN f8 SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column F8 from type float to string | "},
            {"ALTER ICEBERG TABLE ice_d ALTER COLUMN d SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column D from type double to string | "},
            {"ALTER ICEBERG TABLE ice_d ALTER COLUMN fl SET DATA TYPE REAL", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE ice_d ALTER COLUMN fl SET DATA TYPE FLOAT8",
                "REFUSED SQL compilation error: cannot change column FL from type double to float | "},
            {"SELECT GET_DDL('TABLE', 'ice_d')",
                "create or replace ICEBERG TABLE ICE_D ( | \tD DOUBLE, | \tDP DOUBLE, | \tR DOUBLE, | \tF4 FLOAT, "
                + "| \tF8 FLOAT, | \tFL DOUBLE, | \tI INT, | \tBI LONG, | \tNU DECIMAL(5, 1), | \tDE DECIMAL(6, 2), "
                + "| \tS STRING, | \tT STRING, | \tV STRING, | \tBN BINARY, | \tTS TIMESTAMP_NTZ(6), | \tDTM "
                + "TIMESTAMP_NTZ(6), | \tTN TIMESTAMP_NTZ(6), | \tTM TIME(6) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"INSERT INTO src_k (f) VALUES (1e300)", "1"},
            {"INSERT INTO src_k (d) VALUES (1e300)", "1"},
            {"SELECT f, d FROM src_k WHERE f IS NOT NULL OR d IS NOT NULL ORDER BY 1, 2",
                "Infinity|null ;; null|1.0E300"},
            {"INSERT INTO src_k (f) VALUES (0.1)", "1"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_x AS SELECT a, b, f, d FROM src_k",
                "Table ICE_X successfully created."},
            {"INSERT INTO ice_x (a) VALUES (2147483648)", "1"},
            {"INSERT INTO ice_x (b) VALUES (9223372036854775808)", "1"},
            {"ALTER ICEBERG TABLE ice_x ALTER COLUMN a SET DATA TYPE BIGINT",
                "REFUSED SQL compilation error: cannot change column A from type NUMBER(10,0) to NUMBER(19,0) | "},
            {"ALTER ICEBERG TABLE ice_x ALTER COLUMN b SET DATA TYPE NUMBER(38,0)",
                "REFUSED SQL compilation error: cannot change column B from type NUMBER(19,0) to NUMBER(38,0) | "},
            {"ALTER ICEBERG TABLE ice_x ALTER COLUMN f SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column F from type FLOAT to VARCHAR(134217728) | "},
            {"ALTER ICEBERG TABLE ice_x ALTER COLUMN d SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column D from type FLOAT to VARCHAR(134217728) | "},
            {"ALTER ICEBERG TABLE ice_x ALTER COLUMN f SET DATA TYPE DOUBLE", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ice_x')",
                "create or replace ICEBERG TABLE ICE_X ( | \tA NUMBER(10,0), | \tB NUMBER(19,0), | \tF FLOAT, | "
                + "\tD FLOAT | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = "
                + "'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_l LIKE src_k", "Table ICE_L successfully created."},
            {"INSERT INTO ice_l (a) VALUES (2147483648)", "1"},
            {"ALTER ICEBERG TABLE ice_l ALTER COLUMN a SET DATA TYPE BIGINT",
                "REFUSED SQL compilation error: cannot change column A from type NUMBER(10,0) to NUMBER(19,0) | "},
            {"ALTER ICEBERG TABLE ice_l ALTER COLUMN f SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column F from type FLOAT to VARCHAR(134217728) | "},
            {"SELECT GET_DDL('TABLE', 'ice_l')",
                "create or replace ICEBERG TABLE ICE_L ( | \tA NUMBER(10,0), | \tB NUMBER(19,0), | \tF FLOAT, | "
                + "\tD FLOAT | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = "
                + "'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_cl CLONE src_k", "Table ICE_CL successfully created."},
        });
        assertSuffixedAnswer("create or replace ICEBERG TABLE ICE_CL ( | \tA INT, | \tB LONG, | \tF FLOAT, | \tD "
                + "DOUBLE | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG "
                + "= 'SNOWFLAKE' |  BASE_LOCATION = 'TEST_DB/TEST_SCHEMA/SRC_K.<SUFFIX>/';",
            "SELECT GET_DDL('TABLE', 'ice_cl')");
        assertCells(new String[][] {
            {"INSERT INTO ice_cl (a) VALUES (2147483648)",
                "REFUSED Number out of representable range: type int, value 2147483648"},
            {"CREATE OR REPLACE ICEBERG TABLE ice_s (o OBJECT(x INT NOT NULL, y VARCHAR) NOT NULL, m MAP(INT, "
                + "ARRAY(VARCHAR)), a2 ARRAY(OBJECT(q BIGINT)), o2 OBJECT(p DECIMAL(5,2), t TIMESTAMP_LTZ))",
                "Table ICE_S successfully created."},
            {"SELECT GET_DDL('TABLE', 'ice_s')",
                "create or replace ICEBERG TABLE ICE_S ( | \tO OBJECT(x INT, y STRING) NOT NULL, | \tM MAP(INT, "
                + "ARRAY(STRING)), | \tA2 ARRAY(OBJECT(q LONG)), | \tO2 OBJECT(p DECIMAL(5, 2), t TIMESTAMP_LTZ(6)) "
                + "| ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"ALTER ICEBERG TABLE ice_s ALTER COLUMN m SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column M from type MAP(NUMBER(10,0), "
                + "ARRAY(VARCHAR(134217728))) to VARCHAR(134217728) | "},
            {"ALTER ICEBERG TABLE ice_s ALTER COLUMN a2 SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column A2 from type ARRAY(OBJECT(q NUMBER(19,0))) to "
                + "VARCHAR(134217728) | "},
            {"ALTER ICEBERG TABLE ice_s ALTER COLUMN o2 SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column O2 from type OBJECT(p NUMBER(5,2), t "
                + "TIMESTAMP_LTZ(6)) to VARCHAR(134217728) | "},
            {"ALTER ICEBERG TABLE ice_s ALTER COLUMN m SET DATA TYPE MAP(INT, ARRAY(VARCHAR))",
                "Statement executed successfully."},
            {"ALTER ICEBERG TABLE src_k ALTER COLUMN a SET DATA TYPE MAP(INT, INT)",
                "REFUSED SQL compilation error: cannot change column A from type NUMBER(10,0) to MAP(NUMBER(10,0), "
                + "NUMBER(10,0)) | "},
            {"INSERT INTO ice_s (o) SELECT {'x': 3000000000, 'y': 'a'}::OBJECT(x INT NOT NULL, y VARCHAR)",
                "REFUSED Number out of representable range: type int, value 3000000000"},
            {"ALTER ICEBERG TABLE src_k ADD COLUMN n NUMBER(10,0), w INT", "Statement executed successfully."},
            {"ALTER ICEBERG TABLE src_k ALTER COLUMN n SET DATA TYPE NUMBER(12,0), COLUMN w SET DATA TYPE BIGINT",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'src_k')",
                "create or replace ICEBERG TABLE SRC_K ( | \tA INT, | \tB LONG, | \tF FLOAT, | \tD DOUBLE, | \tN "
                + "DECIMAL(12, 0), | \tW LONG | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  "
                + "CATALOG = 'SNOWFLAKE';"},
            {"ALTER ICEBERG TABLE src_k ALTER COLUMN a SET DATA TYPE BIGINT, COLUMN f SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column F from type float to string | "},
            {"SELECT GET_DDL('TABLE', 'src_k')",
                "create or replace ICEBERG TABLE SRC_K ( | \tA INT, | \tB LONG, | \tF FLOAT, | \tD DOUBLE, | \tN "
                + "DECIMAL(12, 0), | \tW LONG | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  "
                + "CATALOG = 'SNOWFLAKE';"},
        });
    }

    @Test
    public void aFloatHoldsThirtyTwoBits() {
        engine.execute("CREATE OR REPLACE ICEBERG TABLE fz_k (k INT, f FLOAT, d DOUBLE)");
        engine.execute("INSERT INTO fz_k SELECT 1, 0.1, 0.1 UNION ALL SELECT 2, 1/3, 1/3 UNION ALL SELECT 3, "
            + "123456789.123, 123456789.123 UNION ALL SELECT 4, 1e300, 1e300");
        assertCells(new String[][] {
            {"SELECT (1/3)::FLOAT", "0.333333"},
            {"SELECT f::VARCHAR, d::VARCHAR FROM fz_k ORDER BY k",
                "0.1000000015|0.1 ;; 0.3333329856|0.333333 ;; 123456792|123456789.123 ;; inf|1e+300"},
            {"SELECT f = 0.1, d = 0.1 FROM fz_k ORDER BY k", "false|true ;; false|false ;; false|false ;; false|false"},
            {"SELECT SYSTEM$TYPEOF(f), SYSTEM$TYPEOF(d) FROM fz_k WHERE k = 1", "FLOAT[DOUBLE]|FLOAT[DOUBLE]"},
            {"UPDATE fz_k SET f = 0.3 WHERE k = 1", "1|0"},
        });
    }
}
