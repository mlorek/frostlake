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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * CONVERT_TIMEZONE converts no TIME: the two-argument form refuses one as no TIMESTAMP_TZ and the three-argument
 * form as no TIMESTAMP_NTZ, and a BOOLEAN or a number standing as a zone is an unknown zone (all live-verified).
 */
public class ConvertTimezoneTimeArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aTimeIsNoTimestamp() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE tsr2 (a INT)");
        engine.execute("INSERT INTO tsr2 VALUES (1)");
        engine.execute("CREATE OR REPLACE FUNCTION fr(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_ins(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT "
            + "INTO IDENTIFIER(:r) VALUES (9); RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_upd(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN UPDATE "
            + "IDENTIFIER(:r) SET a = a + 100 WHERE a = 9; RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_del(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN DELETE "
            + "FROM IDENTIFIER(:r) WHERE a > 100; RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_fn(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT IDENTIFIER(:r)(1)); RETURN n; END $$");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]",
            "SELECT CONVERT_TIMEZONE('UTC', 'America/New_York', '12:00:00'::TIME)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]",
            "SELECT CONVERT_TIMEZONE('America/New_York', '12:00:00'::TIME)");
        assertAnswer("REFUSED Unknown timezone: 'true'",
            "SELECT CONVERT_TIMEZONE(TRUE, '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer("REFUSED Unknown timezone: '1'",
            "SELECT CONVERT_TIMEZONE(1, '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer("REFUSED Unknown timezone: 'true'",
            "SELECT CONVERT_TIMEZONE('UTC', TRUE, '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer("REFUSED Unknown timezone: '1'",
            "SELECT CONVERT_TIMEZONE('UTC', 1, '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
        assertAnswer("REFUSED Unknown timezone: 'true'",
            "SELECT CONVERT_TIMEZONE(TRUE, 'UTC', '2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
    }
}
