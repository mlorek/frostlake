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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The overload a call reaches for a TIME and for a BINARY argument. A TIME reaches a timestamp parameter of any kind
 * before a text one, by position and by name, of a procedure and of a function, and is refused there,
 * {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}, where a TIME parameter would have taken it; a DATE
 * parameter and a VARIANT one take no TIME, and the timestamps weigh alike, the first by signature reached. A BINARY
 * reaches none of a VARIANT, a TIMESTAMP_TZ and a VARCHAR overload. A SQL function's BINARY parameter is typed by a
 * column argument's width (live-verified).
 */
public class OverloadArgumentReachTest extends MeasuredCellsTestSupport {

    @Test
    public void aTimeAndABinaryAmongOverloads() {
        engine.execute("CREATE OR REPLACE PROCEDURE jo(a VARIANT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
            + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
            + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
            + "com.snowflake.snowpark_java.types.Variant a) { return \"v=V:\" + a; } } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jo(a TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
            + "import com.snowflake.snowpark_java.*; class H { public static String run(Session s, "
            + "java.sql.Timestamp a) { return \"v=T:\" + (a == null ? \"null\" : "
            + "String.valueOf(a.getTime())); } } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE jo(a VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = "
            + "'11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
            + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, String a) { "
            + "return \"v=S:\" + a; } } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_a(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'N'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_a(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_b(x TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'L'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_b(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_c(x DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'D'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_c(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_d(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'T'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_d(x VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'V'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_e(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'N'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_e(x TIME) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'M'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_f(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_f(x VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'V'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_g(x BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_g(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_h(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'T'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_h(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_h(x VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'V'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_i(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'N'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_i(x NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'F'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_j(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'N'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_j(x TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'T'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_k(x VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'V'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_k(x NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'F'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_l(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_m(x VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'V'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fo(x VARCHAR) RETURNS VARCHAR AS '''S'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo(x VARIANT) RETURNS VARCHAR AS '''V'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo2(x TIMESTAMP_TZ) RETURNS VARCHAR AS '''T'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo2(x VARCHAR) RETURNS VARCHAR AS '''S'''");
        engine.execute("CREATE OR REPLACE TABLE wb (b BINARY, c BINARY(4), v VARCHAR, w VARCHAR(5))");
        engine.execute("INSERT INTO wb SELECT TO_BINARY('41', 'HEX'), TO_BINARY('42', 'HEX'), 'a', 'b'");
        engine.execute("CREATE OR REPLACE FUNCTION wf(x BINARY) RETURNS BINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wv(x VARCHAR) RETURNS VARCHAR AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wf4(x BINARY(4)) RETURNS BINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wfr(x BINARY) RETURNS BINARY(2) AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wfx(x BINARY) RETURNS BINARY AS 'TO_BINARY(''41'', ''HEX'')'");
        engine.execute("CREATE OR REPLACE FUNCTION wfv(x VARCHAR) RETURNS VARCHAR AS 'x || x'");
        assertCells(new String[][] {
            {"CALL jo(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JO': (BINARY(67108864))"},
            {"CALL jo('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"CALL jo(NULL)", "v=S:null"},
            {"CALL jo(OBJECT_CONSTRUCT('k', 1))", "v=V:{\"k\":1}"},
            {"CALL jo(X'41')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'JO': (BINARY(1))"},
            {"CALL ov_a('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_b('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"CALL ov_c('10:11:12'::TIME)", "S"},
            {"CALL ov_d('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"CALL ov_e('10:11:12'::TIME)", "M"},
            {"CALL ov_f('10:11:12'::TIME)", "S"},
            {"CALL ov_f(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_F': (BINARY(67108864))"},
            {"CALL ov_g(TO_BINARY('ABCD'))", "B"},
            {"CALL ov_g('x')", "S"},
            {"CALL ov_h('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"CALL ov_h(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_H': (BINARY(67108864))"},
            {"CALL ov_i('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_j('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_k(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_K': (BINARY(67108864))"},
            {"CALL ov_a(x => '10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_f(X'41')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_F': (BINARY(1))"},
            {"CALL ov_h(TO_BINARY('41', 'HEX'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_H': (BINARY(67108864))"},
            {"CALL ov_a(TIME_FROM_PARTS(1, 2, 3))",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_f(TO_TIME('10:11'))", "S"},
            {"CALL ov_l(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_L': (BINARY(67108864))"},
            {"CALL ov_m(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_M': (BINARY(67108864))"},
            {"CALL ov_l(X'41')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'OV_L': (BINARY(1))"},
            {"CALL ov_h(x => TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [X] do not match any "
                + "signature for function OV_H"},
            {"SELECT fo(TO_BINARY('ABCD'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FO': (BINARY(67108864))"},
            {"SELECT fo('10:11:12'::TIME)", "S"},
            {"SELECT fo2('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT SYSTEM$TYPEOF(wf(b)), SYSTEM$TYPEOF(wf(c)) FROM wb", "BINARY(8388608)[LOB]|BINARY(4)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wv(v)), SYSTEM$TYPEOF(wv(w)) FROM wb", "VARCHAR(16777216)[LOB]|VARCHAR(5)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wf(TO_BINARY('ABCD')))", "BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wf4(b)), SYSTEM$TYPEOF(wf4(c)) FROM wb", "BINARY(8388608)[LOB]|BINARY(4)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wfr(c)), SYSTEM$TYPEOF(wfr(b)) FROM wb", "BINARY(4)[LOB]|BINARY(8388608)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wfx(c)) FROM wb", "BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wf(X'4142'))", "BINARY(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wf(c::BINARY(8))) FROM wb", "BINARY(8)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wf(IFF(TRUE, c, c))) FROM wb", "BINARY(4)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wfv(w)) FROM wb", "VARCHAR(10)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wv(w || 'x')) FROM wb", "VARCHAR(6)[LOB]"},
        });
        engine.execute("CREATE OR REPLACE TABLE wbt AS SELECT wf(b) b, wf(c) c, wv(w) w FROM wb");
        engine.execute("DESCRIBE TABLE wbt");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "B|BINARY(8388608) ;; C|BINARY(4) ;; W|VARCHAR(5)"},
        });
    }

    @Test
    public void aTimeReachesATimestampFirst() {
        engine.execute("CREATE OR REPLACE TABLE wb (b BINARY, c BINARY(4), v VARCHAR, w VARCHAR(5))");
        engine.execute("INSERT INTO wb SELECT TO_BINARY('41', 'HEX'), TO_BINARY('42', 'HEX'), 'a', 'b'");
        engine.execute("CREATE OR REPLACE FUNCTION wf(x BINARY) RETURNS BINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wv(x VARCHAR) RETURNS VARCHAR AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wf4(x BINARY(4)) RETURNS BINARY AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wv5(x VARCHAR(5)) RETURNS VARCHAR AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wn(x NUMBER(5,2)) RETURNS NUMBER AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION wfr(x BINARY) RETURNS BINARY(2) AS 'x'");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_t(x TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
            + "RETURN 'N'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_t(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'S'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_t2(x TIMESTAMP_NTZ, y INT) RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'N'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ov_t2(x VARCHAR, y INT) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'S'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fo3(x TIMESTAMP_LTZ) RETURNS VARCHAR AS '''L'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo3(x VARCHAR) RETURNS VARCHAR AS '''S'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo4(x TIMESTAMP_NTZ) RETURNS VARCHAR AS '''N'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo4(x TIME) RETURNS VARCHAR AS '''M'''");
        engine.execute("CREATE OR REPLACE FUNCTION fo5(x TIMESTAMP_TZ) RETURNS VARCHAR AS '''T'''");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(wf4(NULL))", "BINARY(4)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wn(NULL))", "NUMBER(5,2)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(wf(NULL::BINARY(3)))", "BINARY(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wv(NULL::VARCHAR(3)))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wv('abc'))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(wf(TO_BINARY('41', 'HEX')))", "BINARY[LOB]"},
            {"CALL ov_t('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_t2('10:11:12'::TIME, 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL ov_t(NULL::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT fo3('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT fo4('10:11:12'::TIME)", "M"},
            {"SELECT fo5('10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT fo3(TIME_FROM_PARTS(1, 2, 3))",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT fo3(NULL::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT fo3(x => '10:11:12'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
        });
    }
}
