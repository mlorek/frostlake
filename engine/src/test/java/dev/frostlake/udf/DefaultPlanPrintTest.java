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
 * A routine parameter's default told back by GET_DDL (live-verified): an arithmetic operation written in
 * parentheses as an operand of another prints as the cast to its type, {@code 1 * (CAST(2 + 3 AS NUMBER(2,0)))};
 * a cast or a one-argument TO_VARCHAR or TO_CHAR to a text written without a width is to the bare VARCHAR; and
 * TO_VARCHAR given a format is TO_CHAR.
 */
public class DefaultPlanPrintTest extends MeasuredCellsTestSupport {

    @Test
    public void aDefaultIsToldAsThePlanPrintsIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION dp1(p NUMBER DEFAULT 1 * (2 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP1 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp1(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP1\"(\"P\" NUMBER(38,0) DEFAULT 1 * (CAST(2 + 3 AS NUMBER(2,0)))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp2(p VARCHAR DEFAULT TO_VARCHAR(1)) RETURNS VARCHAR AS '''x'''",
                "Function DP2 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp2(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP2\"(\"P\" VARCHAR DEFAULT CAST(1 AS VARCHAR)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp3(p VARCHAR DEFAULT TO_CHAR(1)) RETURNS VARCHAR AS '''x'''",
                "Function DP3 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp3(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP3\"(\"P\" VARCHAR DEFAULT CAST(1 AS VARCHAR)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp4(p NUMBER DEFAULT 1 + (2 * 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP4 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp4(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP4\"(\"P\" NUMBER(38,0) DEFAULT 1 + (CAST(2 * 3 AS NUMBER(2,0)))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp5(p NUMBER DEFAULT (2 + 3) * 1) RETURNS VARCHAR AS '''x'''",
                "Function DP5 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp5(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP5\"(\"P\" NUMBER(38,0) DEFAULT (CAST(2 + 3 AS NUMBER(2,0))) * 1) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp6(p NUMBER DEFAULT 1 * (2 * 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP6 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp6(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP6\"(\"P\" NUMBER(38,0) DEFAULT 1 * (CAST(2 * 3 AS NUMBER(2,0)))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp7(p NUMBER DEFAULT 1 - (2 - 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP7 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp7(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP7\"(\"P\" NUMBER(38,0) DEFAULT 1 - (CAST(2 - 3 AS NUMBER(2,0)))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp8(p NUMBER DEFAULT 1 * (2.5 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP8 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp8(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP8\"(\"P\" NUMBER(38,0) DEFAULT 1 * (CAST(2.5 + (CAST(3 AS NUMBER(3,"
                + "1))) AS NUMBER(4,1)))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp9(p NUMBER DEFAULT -(2 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP9 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp9(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP9\"(\"P\" NUMBER(38,0) DEFAULT NEGATE(2 + 3)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp10(p NUMBER DEFAULT (2 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP10 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp10(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP10\"(\"P\" NUMBER(38,0) DEFAULT 2 + 3) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp11(p NUMBER DEFAULT ABS(2 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP11 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp11(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP11\"(\"P\" NUMBER(38,0) DEFAULT ABS(2 + 3)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp12(p NUMBER DEFAULT 1 * (2 + 3) * 4) RETURNS VARCHAR AS '''x'''",
                "Function DP12 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp12(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP12\"(\"P\" NUMBER(38,0) DEFAULT (1 * (CAST(2 + 3 AS NUMBER(2,0)))) * "
                + "4) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp13(p VARCHAR DEFAULT 'a' || ('b' || 'c')) RETURNS VARCHAR AS '''x'''",
                "Function DP13 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp13(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP13\"(\"P\" VARCHAR DEFAULT 'a' || 'b' || 'c') | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp14(p VARCHAR DEFAULT TO_VARCHAR(1.5)) RETURNS VARCHAR AS '''x'''",
                "Function DP14 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp14(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP14\"(\"P\" VARCHAR DEFAULT CAST(1.5 AS VARCHAR)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp15(p VARCHAR DEFAULT TO_VARCHAR('x')) RETURNS VARCHAR AS '''x'''",
                "Function DP15 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp15(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP15\"(\"P\" VARCHAR DEFAULT 'x') | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp16(p VARCHAR DEFAULT 1 || 2) RETURNS VARCHAR AS '''x'''",
                "Function DP16 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp16(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP16\"(\"P\" VARCHAR DEFAULT (CAST(1 AS VARCHAR(134217728))) || "
                + "(CAST(2 AS VARCHAR(134217728)))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp17(p VARCHAR DEFAULT CAST(1 AS VARCHAR)) RETURNS VARCHAR AS '''x'''",
                "Function DP17 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp17(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP17\"(\"P\" VARCHAR DEFAULT CAST(1 AS VARCHAR)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp18(p VARCHAR DEFAULT 1::VARCHAR) RETURNS VARCHAR AS '''x'''",
                "Function DP18 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp18(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP18\"(\"P\" VARCHAR DEFAULT CAST(1 AS VARCHAR)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp19(p VARCHAR DEFAULT TO_VARCHAR(1, '999')) RETURNS VARCHAR AS '''x'''",
                "Function DP19 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp19(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP19\"(\"P\" VARCHAR DEFAULT TO_CHAR(1, '999')) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp20(p NUMBER DEFAULT 1 + (2 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP20 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp20(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP20\"(\"P\" NUMBER(38,0) DEFAULT 1 + (CAST(2 + 3 AS NUMBER(2,0)))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp21(p NUMBER DEFAULT 10 / (2 + 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP21 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp21(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP21\"(\"P\" NUMBER(38,0) DEFAULT (CAST(10 AS NUMBER(8,6))) / (CAST(2 "
                + "+ 3 AS NUMBER(2,0)))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp22(p NUMBER DEFAULT 1 * (2 - 3)) RETURNS VARCHAR AS '''x'''",
                "Function DP22 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp22(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP22\"(\"P\" NUMBER(38,0) DEFAULT 1 * (CAST(2 - 3 AS NUMBER(2,0)))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp23(p VARCHAR DEFAULT TO_CHAR(DATE '2024-01-02')) RETURNS VARCHAR AS '''x'''",
                "Function DP23 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp23(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP23\"(\"P\" VARCHAR DEFAULT CAST(CAST('2024-01-02' AS DATE) AS "
                + "VARCHAR)) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp24(p VARCHAR DEFAULT TO_VARCHAR(TRUE)) RETURNS VARCHAR AS '''x'''",
                "Function DP24 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp24(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP24\"(\"P\" VARCHAR DEFAULT CAST(TRUE AS VARCHAR)) | RETURNS VARCHAR |"
                + " LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp25(p NUMBER DEFAULT 2 * (1 + 0.5)) RETURNS VARCHAR AS '''x'''",
                "Function DP25 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp25(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP25\"(\"P\" NUMBER(38,0) DEFAULT 2 * (CAST((CAST(1 AS NUMBER(3,1))) + "
                + "0.5 AS NUMBER(4,1)))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp26(p VARCHAR DEFAULT UPPER('a' || 'b')) RETURNS VARCHAR AS '''x'''",
                "Function DP26 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp26(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP26\"(\"P\" VARCHAR DEFAULT UPPER('a' || 'b')) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp27(p NUMBER DEFAULT (1 + 2) + (3 * 4)) RETURNS VARCHAR AS '''x'''",
                "Function DP27 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp27(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"DP27\"(\"P\" NUMBER(38,0) DEFAULT (CAST(1 + 2 AS NUMBER(2,0))) + "
                + "(CAST(3 * 4 AS NUMBER(2,0)))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';"},
            {"CREATE OR REPLACE FUNCTION dp28(p VARCHAR DEFAULT TO_VARCHAR(1 + 2)) RETURNS VARCHAR AS '''x'''",
                "Function DP28 successfully created."},
            {"SELECT GET_DDL('FUNCTION', 'dp28(VARCHAR)')",
                "CREATE OR REPLACE FUNCTION \"DP28\"(\"P\" VARCHAR DEFAULT CAST(1 + 2 AS VARCHAR)) | RETURNS VARCHAR "
                + "| LANGUAGE SQL | AS '''x''';"},
        });
    }
}
