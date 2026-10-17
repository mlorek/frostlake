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
 * ALTER TABLE ADD COLUMN echoes a DEFAULT it refuses as the plan prints it (live-verified): a niladic keyword with its
 * parentheses, {@code CURRENT_DATE()} and {@code CURRENT_USER()}, and date arithmetic as the function it plans,
 * {@code DATE_ADDDAYSTODATE(NEGATE(1), CURRENT_DATE())} for {@code CURRENT_DATE - 1} and {@code
 * DATE_DIFFDATEINDAYS(CURRENT_DATE(), CURRENT_DATE())} for a difference of dates, a fraction of a day cast to
 * NUMBER(9,0). The default is compiled first: a timestamp plus a number is refused at the operator as argument types
 * the operator does not take.
 */
public class AddColumnDefaultEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aRefusedDefaultIsEchoedAsThePlanPrintsIt() {
        engine.execute("CREATE OR REPLACE TABLE ac (a INT)");
        engine.execute("INSERT INTO ac VALUES (1)");
        assertCells(new String[][] {
            {"ALTER TABLE ac ADD COLUMN d1 DATE DEFAULT CURRENT_DATE - 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(NEGATE(1), "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d2 DATE DEFAULT 1 + CURRENT_DATE",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d3 TIMESTAMP DEFAULT CURRENT_TIMESTAMP + 1",
                "REFUSED SQL compilation error: error line 1 at position 65 | Invalid argument types for function "
                + "'+': (TIMESTAMP_LTZ(9), NUMBER(1,0))"},
            {"ALTER TABLE ac ADD COLUMN d5 DATE DEFAULT CURRENT_DATE() + 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d6 DATE DEFAULT CURRENT_DATE + 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d7 DATE DEFAULT DATEADD(day, 1, CURRENT_DATE)",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d8 DATE DEFAULT '2024-01-01'::DATE + 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CAST('2024-01-01' AS DATE))]"},
            {"ALTER TABLE ac ADD COLUMN d9 TIMESTAMP DEFAULT LOCALTIMESTAMP - 1",
                "REFUSED SQL compilation error: error line 1 at position 62 | Invalid argument types for function "
                + "'-': (TIMESTAMP_LTZ(9), NUMBER(1,0))"},
            {"ALTER TABLE ac ADD COLUMN d10 VARCHAR DEFAULT CURRENT_USER",
                "REFUSED SQL compilation error: | Invalid column default expression [CURRENT_USER()]"},
            {"ALTER TABLE ac ADD COLUMN d12 DATE DEFAULT CURRENT_DATE + 1 + 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "DATE_ADDDAYSTODATE(1, CURRENT_DATE()))]"},
            {"ALTER TABLE ac ADD COLUMN d13 NUMBER DEFAULT CURRENT_DATE - CURRENT_DATE",
                "REFUSED SQL compilation error: | Invalid column default expression "
                + "[DATE_DIFFDATEINDAYS(CURRENT_DATE(), CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d14 TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP - 1",
                "REFUSED SQL compilation error: error line 1 at position 70 | Invalid argument types for function "
                + "'-': (TIMESTAMP_LTZ(9), NUMBER(1,0))"},
            {"ALTER TABLE ac ADD COLUMN d15 DATE DEFAULT SYSDATE() + 1",
                "REFUSED SQL compilation error: error line 1 at position 53 | Invalid argument types for function "
                + "'+': (TIMESTAMP_NTZ(9), NUMBER(1,0))"},
            {"ALTER TABLE ac ADD COLUMN d16 DATE DEFAULT CURRENT_DATE + -1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(-1, "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d18 VARCHAR DEFAULT CURRENT_SCHEMA",
                "REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'CURRENT_SCHEMA'"},
            {"ALTER TABLE ac ADD COLUMN d19 VARCHAR DEFAULT CURRENT_DATABASE",
                "REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'CURRENT_DATABASE'"},
            {"ALTER TABLE ac ADD COLUMN d20 DATE DEFAULT CURRENT_DATE + 1.5",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(CAST(1.5 "
                + "AS NUMBER(9,0)), CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d21 DATE DEFAULT CURRENT_DATE - 1 - 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(NEGATE(1), "
                + "DATE_ADDDAYSTODATE(NEGATE(1), CURRENT_DATE()))]"},
            {"ALTER TABLE ac ADD COLUMN d22 TIMESTAMP DEFAULT CURRENT_TIMESTAMP() - 1",
                "REFUSED SQL compilation error: error line 1 at position 68 | Invalid argument types for function "
                + "'-': (TIMESTAMP_LTZ(9), NUMBER(1,0))"},
            {"ALTER TABLE ac ADD COLUMN d23 DATE DEFAULT (CURRENT_DATE + 1)",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CURRENT_DATE())]"},
            {"ALTER TABLE ac ADD COLUMN d24 VARCHAR DEFAULT CURRENT_REGION",
                "REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'CURRENT_REGION'"},
            {"ALTER TABLE ac ADD COLUMN d25 DATE DEFAULT 2 + CURRENT_DATE - 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(NEGATE(1), "
                + "DATE_ADDDAYSTODATE(2, CURRENT_DATE()))]"},
            {"ALTER TABLE ac ADD COLUMN d26 TIMESTAMP DEFAULT CURRENT_DATE + 1",
                "REFUSED SQL compilation error: | Invalid column default expression [DATE_ADDDAYSTODATE(1, "
                + "CURRENT_DATE())]"},
        });
    }
}
