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
 * A column DEFAULT that reads the clock as DESCRIBE, GET_DDL and INFORMATION_SCHEMA print it: the context function
 * with its parentheses whether or not they were written, a precision kept, GETDATE() and SYSTIMESTAMP() as
 * CURRENT_TIMESTAMP(), and SYSDATE() as the expression it expands to (all live-verified).
 */
public class ClockDefaultSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void clockDefaults() {
        assertAnswer("Table D2040_CA successfully created.",
            "CREATE OR REPLACE TABLE d2040_ca (v TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP())");
        assertAnswer("CURRENT_TIMESTAMP()", "DESCRIBE TABLE d2040_ca ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CA ( | 	V TIMESTAMP_NTZ(9) DEFAULT CURRENT_TIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_ca')");
        assertAnswer("Table D2040_CB successfully created.",
            "CREATE OR REPLACE TABLE d2040_cb (v TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP)");
        assertAnswer("CURRENT_TIMESTAMP()", "DESCRIBE TABLE d2040_cb ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CB ( | 	V TIMESTAMP_NTZ(9) DEFAULT CURRENT_TIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cb')");
        assertAnswer("Table D2040_CC successfully created.",
            "CREATE OR REPLACE TABLE d2040_cc (v TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP())");
        assertAnswer("CURRENT_TIMESTAMP()", "DESCRIBE TABLE d2040_cc ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CC ( | 	V TIMESTAMP_LTZ(9) DEFAULT CURRENT_TIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cc')");
        assertAnswer("Table D2040_CD successfully created.",
            "CREATE OR REPLACE TABLE d2040_cd (v DATE DEFAULT CURRENT_DATE())");
        assertAnswer("CURRENT_DATE()", "DESCRIBE TABLE d2040_cd ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CD ( | 	V DATE DEFAULT CURRENT_DATE() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cd')");
        assertAnswer("Table D2040_CE successfully created.",
            "CREATE OR REPLACE TABLE d2040_ce (v DATE DEFAULT CURRENT_DATE)");
        assertAnswer("CURRENT_DATE()", "DESCRIBE TABLE d2040_ce ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CE ( | 	V DATE DEFAULT CURRENT_DATE() | );",
            "SELECT GET_DDL('TABLE', 'd2040_ce')");
        assertAnswer("Table D2040_CF successfully created.",
            "CREATE OR REPLACE TABLE d2040_cf (v TIMESTAMP DEFAULT SYSDATE())");
        assertAnswer("CAST(CONVERT_TIMEZONE('UTC', CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_TZ(9))) AS TIMESTAMP_NTZ(9))",
            "DESCRIBE TABLE d2040_cf ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CF ( | 	V TIMESTAMP_NTZ(9) DEFAULT CAST(CONVERT_TIMEZONE('UTC', "
                + "CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_TZ(9))) AS TIMESTAMP_NTZ(9)) | );",
            "SELECT GET_DDL('TABLE', 'd2040_cf')");
        assertAnswer("Table D2040_CG successfully created.",
            "CREATE OR REPLACE TABLE d2040_cg (v TIME DEFAULT CURRENT_TIME)");
        assertAnswer("CURRENT_TIME()", "DESCRIBE TABLE d2040_cg ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CG ( | 	V TIME(9) DEFAULT CURRENT_TIME() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cg')");
        assertAnswer("Table D2040_CH successfully created.",
            "CREATE OR REPLACE TABLE d2040_ch (v TIME DEFAULT CURRENT_TIME())");
        assertAnswer("CURRENT_TIME()", "DESCRIBE TABLE d2040_ch ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CH ( | 	V TIME(9) DEFAULT CURRENT_TIME() | );",
            "SELECT GET_DDL('TABLE', 'd2040_ch')");
        assertAnswer("Table D2040_CI successfully created.",
            "CREATE OR REPLACE TABLE d2040_ci (v TIMESTAMP_LTZ DEFAULT LOCALTIMESTAMP)");
        assertAnswer("LOCALTIMESTAMP()", "DESCRIBE TABLE d2040_ci ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CI ( | 	V TIMESTAMP_LTZ(9) DEFAULT LOCALTIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_ci')");
        assertAnswer("Table D2040_CJ successfully created.",
            "CREATE OR REPLACE TABLE d2040_cj (v TIME DEFAULT LOCALTIME)");
        assertAnswer("LOCALTIME()", "DESCRIBE TABLE d2040_cj ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CJ ( | 	V TIME(9) DEFAULT LOCALTIME() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cj')");
        assertAnswer("Table D2040_CK successfully created.",
            "CREATE OR REPLACE TABLE d2040_ck (v TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP(3))");
        assertAnswer("CURRENT_TIMESTAMP(3)", "DESCRIBE TABLE d2040_ck ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CK ( | 	V TIMESTAMP_LTZ(9) DEFAULT CURRENT_TIMESTAMP(3) | );",
            "SELECT GET_DDL('TABLE', 'd2040_ck')");
        assertAnswer("Table D2040_CL successfully created.",
            "CREATE OR REPLACE TABLE d2040_cl (v TIMESTAMP_NTZ DEFAULT GETDATE())");
        assertAnswer("CURRENT_TIMESTAMP()", "DESCRIBE TABLE d2040_cl ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CL ( | 	V TIMESTAMP_NTZ(9) DEFAULT CURRENT_TIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cl')");
        assertAnswer("Table D2040_CM successfully created.",
            "CREATE OR REPLACE TABLE d2040_cm (v TIMESTAMP_LTZ DEFAULT SYSTIMESTAMP())");
        assertAnswer("CURRENT_TIMESTAMP()", "DESCRIBE TABLE d2040_cm ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CM ( | 	V TIMESTAMP_LTZ(9) DEFAULT CURRENT_TIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cm')");
        assertAnswer("Table D2040_CN successfully created.",
            "CREATE OR REPLACE TABLE d2040_cn (v TIMESTAMP_LTZ DEFAULT LOCALTIMESTAMP())");
        assertAnswer("LOCALTIMESTAMP()", "DESCRIBE TABLE d2040_cn ->> SELECT \"default\" FROM $1");
        assertAnswer("create or replace TABLE D2040_CN ( | 	V TIMESTAMP_LTZ(9) DEFAULT LOCALTIMESTAMP() | );",
            "SELECT GET_DDL('TABLE', 'd2040_cn')");
        assertAnswer("Table D2040_CO successfully created.",
            "CREATE OR REPLACE TABLE d2040_co (v TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP()::TIMESTAMP_NTZ)");
        assertAnswer("Table D2040_NN successfully created.",
            "CREATE OR REPLACE TABLE d2040_nn (c TIMESTAMP_NTZ NOT NULL DEFAULT CURRENT_TIMESTAMP())");
        assertAnswer("CURRENT_TIMESTAMP()", "DESCRIBE TABLE d2040_nn ->> SELECT \"default\" FROM $1");
        assertAnswer("V", "SHOW COLUMNS IN TABLE d2040_ca ->> SELECT \"column_name\" FROM $1");
        assertAnswer("CURRENT_TIMESTAMP()",
            "SELECT column_default FROM information_schema.columns WHERE table_name = 'D2040_CA'");
        assertAnswer("CAST(CONVERT_TIMEZONE('UTC', CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_TZ(9))) AS TIMESTAMP_NTZ(9))",
            "SELECT column_default FROM information_schema.columns WHERE table_name = 'D2040_CF'");
    }
}
