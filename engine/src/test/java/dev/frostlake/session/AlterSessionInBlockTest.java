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

package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * An ALTER SESSION run by a statement of a block or of a caller's-rights procedure reaches the statements after it
 * in the same block: WEEK_START, WEEK_OF_YEAR_POLICY, TIMEZONE, TIMESTAMP_TYPE_MAPPING and the input and output
 * formats. An owner's-rights procedure is refused ALTER SESSION and reads the caller's values (all live-verified).
 */
public class AlterSessionInBlockTest extends MeasuredCellsTestSupport {

    /** The parameters the blocks set, put back on their defaults: the account's session outlives the test. */
    private static final String[] SET = {"WEEK_START", "TIMEZONE", "DATE_OUTPUT_FORMAT", "DATE_INPUT_FORMAT",
        "WEEK_OF_YEAR_POLICY", "TIMESTAMP_TYPE_MAPPING", "TIMESTAMP_NTZ_OUTPUT_FORMAT", "TIME_OUTPUT_FORMAT"};

    @AfterEach
    public void unsetTheBlocksParameters() {
        for (final String parameter : SET) {
            engine.execute("ALTER SESSION UNSET " + parameter);
        }
    }

    @Test
    public void aBlocksLaterStatementsReadTheValuesItSet() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 7");
        assertAnswer("7",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET WEEK_START = 1; RETURN DAYOFWEEK('2024-01-07'::date); END$$");
        assertAnswer("7", "SELECT DAYOFWEEK('2024-01-07'::date)");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'UTC'");
        answer("EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'; RETURN '2024-01-01 00:00:00 "
            + "+00:00'::timestamp_tz::timestamp_ltz::varchar; END$$");
        answer("SELECT '2024-01-01 00:00:00 +00:00'::timestamp_tz::timestamp_ltz::varchar");
        assertAnswer("31/01/2024",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'; RETURN "
                + "'2024-01-31'::date::varchar; END$$");
        assertAnswer("31/01/2024", "SELECT '2024-01-31'::date::varchar");
        assertAnswer("31/01/2024",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'; RETURN "
                + "'31/01/2024'::date::varchar; END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : Date "
                + "'2024-01-01' is not recognized",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET WEEK_OF_YEAR_POLICY = 1; RETURN "
                + "WEEKOFYEAR('2024-01-01'::date); END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : Date "
                + "'2024-01-01' is not recognized",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET WEEK_OF_YEAR_POLICY = 0; RETURN "
                + "WEEKOFYEAR('2024-01-01'::date); END$$");
        assertAnswer("TIMESTAMP_LTZ(9)[SB16]",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'; RETURN "
                + "SYSTEM$TYPEOF('2024-01-01 10:00:00'::timestamp); END$$");
        assertAnswer("TIMESTAMP_LTZ(9)[SB16]", "SELECT SYSTEM$TYPEOF('2024-01-01 10:00:00'::timestamp)");
        assertAnswer("2024/01/01 10:00",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY/MM/DD HH24:MI'; RETURN "
                + "'2024-01-01 10:00:00'::timestamp_ntz::varchar; END$$");
        assertAnswer("10.30",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET TIME_OUTPUT_FORMAT = 'HH24.MI'; RETURN "
                + "'10:30:00'::time::varchar; END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : Date "
                + "'2024-01-31' is not recognized",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION UNSET DATE_OUTPUT_FORMAT; RETURN '2024-01-31'::date::varchar; "
                + "END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 72 : Date "
                + "'2024-01-07' is not recognized",
            "EXECUTE IMMEDIATE $$DECLARE d VARCHAR; BEGIN ALTER SESSION SET WEEK_START = 3; LET x INT := "
                + "DAYOFWEEK('2024-01-07'::date); ALTER SESSION SET WEEK_START = 0; RETURN x::varchar || ',' || "
                + "DAYOFWEEK('2024-01-07'::date)::varchar; END$$");
        assertAnswer("Function P723 successfully created.",
            "CREATE OR REPLACE PROCEDURE p723() RETURNS STRING LANGUAGE SQL EXECUTE AS CALLER AS $$BEGIN ALTER "
                + "SESSION SET WEEK_START = 3; RETURN DAYOFWEEK('2024-01-07'::date)::varchar; END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : Date "
                + "'2024-01-07' is not recognized",
            "CALL p723()");
        assertAnswer("REFUSED Date '2024-01-07' is not recognized", "SELECT DAYOFWEEK('2024-01-07'::date)");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : Date "
                + "'2024-01-07' is not recognized",
            "CALL p723()");
        assertAnswer("Function P723B successfully created.",
            "CREATE OR REPLACE PROCEDURE p723b() RETURNS STRING LANGUAGE SQL EXECUTE AS OWNER AS $$BEGIN RETURN "
                + "DAYOFWEEK('2024-01-07'::date)::varchar; END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 2");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Date "
                + "'2024-01-07' is not recognized",
            "CALL p723b()");
    }

    @Test
    public void proceduresAndNestedBlocksReadTheValuesTheySet() {
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIMEZONE = 'UTC'");
        assertAnswer("09 +09",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'; RETURN TO_CHAR('2024-01-01 "
                + "00:00:00 +00:00'::timestamp_tz::timestamp_ltz, 'HH24 TZH'); END$$");
        assertAnswer("09 +09", "SELECT TO_CHAR('2024-01-01 00:00:00 +00:00'::timestamp_tz::timestamp_ltz, 'HH24 TZH')");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET TIMEZONE");
        assertAnswer("53",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET WEEK_OF_YEAR_POLICY = 1; RETURN "
                + "WEEKOFYEAR('2024-12-31'::date); END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET WEEK_OF_YEAR_POLICY");
        assertAnswer("1",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET WEEK_OF_YEAR_POLICY = 0; RETURN "
                + "WEEKOFYEAR('2024-12-31'::date); END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET WEEK_OF_YEAR_POLICY");
        assertAnswer("Function P724 successfully created.",
            "CREATE OR REPLACE PROCEDURE p724() RETURNS STRING LANGUAGE SQL EXECUTE AS CALLER AS $$BEGIN ALTER "
                + "SESSION SET WEEK_START = 3; RETURN DAYOFWEEK('2024-01-07'::date)::varchar; END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("5", "CALL p724()");
        assertAnswer("5", "SELECT DAYOFWEEK('2024-01-07'::date)");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("Function P724B successfully created.",
            "CREATE OR REPLACE PROCEDURE p724b() RETURNS STRING LANGUAGE SQL EXECUTE AS OWNER AS $$BEGIN ALTER "
                + "SESSION SET WEEK_START = 3; RETURN DAYOFWEEK('2024-01-07'::date)::varchar; END$$");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Stored procedure "
                + "execution error: Unsupported statement type 'ALTER_SESSION'.",
            "CALL p724b()");
        assertAnswer("0", "SELECT DAYOFWEEK('2024-01-07'::date)");
        assertAnswer("Function P724C successfully created.",
            "CREATE OR REPLACE PROCEDURE p724c() RETURNS STRING LANGUAGE SQL EXECUTE AS OWNER AS $$BEGIN RETURN "
                + "DAYOFWEEK('2024-01-07'::date)::varchar; END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 2");
        assertAnswer("6", "CALL p724c()");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("7",
            "EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'ALTER SESSION SET WEEK_START = 1'; RETURN "
                + "DAYOFWEEK('2024-01-07'::date); END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("5", "EXECUTE IMMEDIATE $$BEGIN CALL p724(); RETURN DAYOFWEEK('2024-01-07'::date); END$$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET WEEK_START = 0");
        assertAnswer("2024-01-31",
            "EXECUTE IMMEDIATE $$BEGIN ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'; ALTER SESSION UNSET "
                + "DATE_OUTPUT_FORMAT; RETURN '2024-01-31'::date::varchar; END$$");
        assertAnswer("31/01/2024 2024-01-31",
            "EXECUTE IMMEDIATE $$DECLARE r VARCHAR; BEGIN ALTER SESSION SET DATE_OUTPUT_FORMAT = 'DD/MM/YYYY'; r := "
                + "'2024-01-31'::date::varchar; ALTER SESSION UNSET DATE_OUTPUT_FORMAT; RETURN r || ' ' || "
                + "'2024-01-31'::date::varchar; END$$");
    }
}
