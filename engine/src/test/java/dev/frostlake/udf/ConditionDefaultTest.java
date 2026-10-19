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
 * A routine parameter's default may be a condition (live-verified): {@code NOT TRUE}, {@code TRUE AND FALSE} and
 * {@code TRUE OR FALSE} are taken and told back as the plan prints them, {@code NOT(TRUE)}. A default of type BOOLEAN
 * that the parameter's type does not take is refused at the predicate's operator — a comparison, a LIKE, an IN, a
 * BETWEEN, an IS given to a text or a number, TRUE given to a number or a DATE — as "default argument data type
 * BOOLEAN does not match expected data type NUMBER(38,0)".
 */
public class ConditionDefaultTest extends MeasuredCellsTestSupport {

    @Test
    public void aParametersDefaultMayBeACondition() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION nf_e28(p BOOLEAN DEFAULT NOT TRUE) RETURNS VARCHAR AS '''x'''",
                "Function NF_E28 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'nf_e28(BOOLEAN)')",
                "CREATE OR REPLACE FUNCTION \"NF_E28\"(\"P\" BOOLEAN DEFAULT NOT(TRUE)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"SELECT nf_e28()", "x"},
            {"CREATE OR REPLACE FUNCTION nf_e43(p BOOLEAN DEFAULT TRUE AND FALSE) RETURNS VARCHAR AS '''x'''",
                "Function NF_E43 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'nf_e43(BOOLEAN)')",
                "CREATE OR REPLACE FUNCTION \"NF_E43\"(\"P\" BOOLEAN DEFAULT TRUE AND FALSE) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION nf_e44(p BOOLEAN DEFAULT TRUE OR FALSE) RETURNS BOOLEAN AS 'p'",
                "Function NF_E44 successfully created."},
            {"SELECT nf_e44()", "true"},
            {"CREATE OR REPLACE FUNCTION nf_e50(p VARCHAR DEFAULT 'a' LIKE 'b') RETURNS VARCHAR AS '''x'''",
                "REFUSED SQL compilation error: error line 1 at position 56 | default argument data type BOOLEAN "
                + "does not match expected data type VARCHAR(134217728)"},
            {"CREATE OR REPLACE FUNCTION nf_e51(p NUMBER DEFAULT 1 IN (1, 2)) RETURNS VARCHAR AS '''x'''",
                "REFUSED SQL compilation error: error line 1 at position 53 | default argument data type BOOLEAN "
                + "does not match expected data type NUMBER(38,0)"},
            {"CREATE OR REPLACE FUNCTION nf_e52(p NUMBER DEFAULT 2 BETWEEN 1 AND 3) RETURNS VARCHAR AS '''x'''",
                "REFUSED SQL compilation error: error line 1 at position 53 | default argument data type BOOLEAN "
                + "does not match expected data type NUMBER(38,0)"},
            {"CREATE OR REPLACE FUNCTION nf_e53(p BOOLEAN DEFAULT 1 IN (1, 2)) RETURNS BOOLEAN AS 'p'",
                "Function NF_E53 successfully created."},
            {"SELECT nf_e53()", "true"},
            {"CREATE OR REPLACE FUNCTION nf_e54(p VARCHAR DEFAULT NOT TRUE) RETURNS VARCHAR AS 'p'",
                "Function NF_E54 successfully created."},
            {"CREATE OR REPLACE FUNCTION nf_e55(p VARCHAR DEFAULT 1) RETURNS VARCHAR AS 'p'",
                "Function NF_E55 successfully created."},
            {"SELECT nf_e55()", "1"},
            {"CREATE OR REPLACE FUNCTION nf_e56(p NUMBER DEFAULT 'a') RETURNS NUMBER AS 'p'",
                "Function NF_E56 successfully created."},
            {"CREATE OR REPLACE FUNCTION nf_e57(p BOOLEAN DEFAULT 1) RETURNS BOOLEAN AS 'p'",
                "Function NF_E57 successfully created."},
            {"SELECT nf_e57()", "true"},
            {"CREATE OR REPLACE FUNCTION nf_e58(p DATE DEFAULT TRUE) RETURNS DATE AS 'p'",
                "REFUSED SQL compilation error: error line 1 at position 49 | default argument data type BOOLEAN "
                + "does not match expected data type DATE"},
            {"CREATE OR REPLACE FUNCTION nf_e59(p NUMBER DEFAULT TRUE) RETURNS NUMBER AS 'p'",
                "REFUSED SQL compilation error: error line 1 at position 51 | default argument data type BOOLEAN "
                + "does not match expected data type NUMBER(38,0)"},
            {"CREATE OR REPLACE FUNCTION nf_e60(p NUMBER DEFAULT NULL IS NULL) RETURNS NUMBER AS 'p'",
                "REFUSED SQL compilation error: error line 1 at position 56 | default argument data type BOOLEAN "
                + "does not match expected data type NUMBER(38,0)"},
            {"CREATE OR REPLACE PROCEDURE nf_p61(p BOOLEAN DEFAULT NOT FALSE) RETURNS BOOLEAN LANGUAGE SQL AS "
                + "'BEGIN RETURN p; END'",
                "Function NF_P61 successfully created."},
            {"CALL nf_p61()", "true"},
            {"CREATE OR REPLACE PROCEDURE nf_p62(p NUMBER DEFAULT 1 < 2) RETURNS BOOLEAN LANGUAGE SQL AS 'BEGIN "
                + "RETURN p; END'",
                "REFUSED SQL compilation error: error line 1 at position 54 | default argument data type BOOLEAN "
                + "does not match expected data type NUMBER(38,0)"},
        });
    }
}
