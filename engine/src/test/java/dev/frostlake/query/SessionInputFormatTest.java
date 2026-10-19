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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * DATE_INPUT_FORMAT, TIMESTAMP_INPUT_FORMAT and TIME_INPUT_FORMAT, once set to a format, are the only reading of
 * text as a date, a timestamp or a time: a cast, a typed literal, TO_DATE, TO_TIMESTAMP and TO_TIME and their
 * TRY_ forms, a comparison, a value written into a column. Text the format does not fit is not recognized, an ISO
 * text and a string of digits included; a written 'AUTO' reads by the format first and by AUTO after it
 * (live-verified).
 */
public class SessionInputFormatTest extends MeasuredCellsTestSupport {

    @Test
    public void theSessionFormatIsTheReading() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'");
            assertAnswer(
                "2024-03-05|2024-03-05|2024-03-13",
                "SELECT '05/03/2024'::DATE, TO_DATE('05/03/2024'), TRY_TO_DATE('13/03/2024')");
            assertAnswer(
                "2024-01-15",
                "SELECT '15/01/2024'::DATE");
            assertAnswer(
                "REFUSED Date '2024-01-15' is not recognized",
                "SELECT '2024-01-15'::DATE");
            assertAnswer(
                "null",
                "SELECT TRY_TO_DATE('2024-01-15')");
            assertAnswer(
                "2024-01-15",
                "SELECT DATE '15/01/2024'");
            assertAnswer(
                "REFUSED Date '2024-01-15' is not recognized",
                "SELECT DATE '2024-01-15'");
            assertAnswer(
                "2024-01-15",
                "SELECT TO_DATE('2024-01-15', 'YYYY-MM-DD')");
            assertAnswer(
                "Table DIN successfully created.",
                "CREATE OR REPLACE TABLE din (d DATE)");
            assertAnswer(
                "1",
                "INSERT INTO din VALUES ('05/03/2024')");
            assertAnswer(
                "2024-03-05|3",
                "SELECT d, MONTH(d) FROM din");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM din WHERE d = '05/03/2024'");
            assertAnswer(
                "2024-05-03T00:00",
                "SELECT '05/03/2024'::TIMESTAMP_NTZ");
            assertAnswer(
                "2024-03-05",
                "SELECT '05/03/2024'::VARIANT::DATE");
            assertAnswer(
                "2024-03-05",
                "SELECT TO_DATE('5/3/2024')");
            assertAnswer(
                "REFUSED Date '20240305' is not recognized",
                "SELECT TO_DATE('20240305')");
            assertAnswer(
                "2024-03-05",
                "SELECT TRY_CAST('05/03/2024' AS DATE)");
            assertAnswer(
                "REFUSED Date '05/03/2024 10:00' is not recognized",
                "SELECT TO_DATE('05/03/2024 10:00')");
            assertAnswer(
                "1|0",
                "UPDATE din SET d = '07/08/2024'");
            assertAnswer(
                "2024-08-07",
                "SELECT d FROM din");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET DATE_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI'");
            assertAnswer(
                "2024-03-05T10:00|2024-03-13T10:00",
                "SELECT '05/03/2024 10:00'::TIMESTAMP_NTZ, TRY_TO_TIMESTAMP('13/03/2024 10:00')");
            answer(
                "SELECT '05/03/2024 10:00'::TIMESTAMP_LTZ::VARCHAR, '05/03/2024 "
                    + "10:00'::TIMESTAMP_TZ::TIMESTAMP_NTZ");
            assertAnswer(
                "2024-03-05T10:00",
                "SELECT TO_TIMESTAMP_NTZ('05/03/2024 10:00')");
            answer(
                "SELECT '2024-03-05 10:00:00'::TIMESTAMP_NTZ");
            assertAnswer(
                "2024-05-03",
                "SELECT '05/03/2024'::DATE");
            assertAnswer(
                "Table TIN successfully created.",
                "CREATE OR REPLACE TABLE tin (t TIMESTAMP_NTZ)");
            assertAnswer(
                "1",
                "INSERT INTO tin VALUES ('05/03/2024 10:00')");
            assertAnswer(
                "2024-03-05T10:00",
                "SELECT t FROM tin");
            assertAnswer(
                "null",
                "SELECT TRY_TO_TIMESTAMP('05/03/2024')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIMESTAMP_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIME_INPUT_FORMAT = 'HH12:MI AM'");
            assertAnswer(
                "15:15|null",
                "SELECT TRY_TO_TIME('03:15 PM'), TRY_TO_TIME('15:15')");
            assertAnswer(
                "15:15|11:05",
                "SELECT '03:15 PM'::TIME, TO_TIME('11:05 AM')");
            assertAnswer(
                "REFUSED Time '15:15' is not recognized",
                "SELECT '15:15'::TIME");
            assertAnswer(
                "null",
                "SELECT TRY_TO_TIMESTAMP('2024-03-05 03:15 PM')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIME_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_INPUT_FORMAT = 'YYYY.MM.DD'");
            assertAnswer(
                "2024-03-05|null",
                "SELECT '2024.03.05'::DATE, TRY_TO_DATE('2024-03-05')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_INPUT_FORMAT = 'AUTO'");
            assertAnswer(
                "2024-05-03",
                "SELECT '05/03/2024'::DATE");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_INPUT_FORMAT = 'NOT A FORMAT'");
            assertAnswer(
                "REFUSED Date '05/03/2024' is not recognized",
                "SELECT '05/03/2024'::DATE");
        } finally {
            answer(
                "alter session unset date_input_format");
            answer(
                "alter session unset timestamp_input_format");
            answer(
                "alter session unset time_input_format");
            answer(
                "DROP TABLE IF EXISTS din");
            answer(
                "DROP TABLE IF EXISTS tin");
        }
    }

    @Test
    public void everyReadingFollowsIt() {
        try {
            assertAnswer(
                "2024-03-05|2024-03-05",
                "SELECT TO_DATE('2024-03-05 10:00:00'), '2024-03-05 10:00:00'::DATE");
            assertAnswer(
                "2024-03-05",
                "SELECT TRY_TO_DATE('03/05/2024 10:00:00')");
            assertAnswer(
                "Table DD successfully created.",
                "CREATE OR REPLACE TABLE dd (d DATE, t TIMESTAMP_NTZ)");
            assertAnswer(
                "1",
                "INSERT INTO dd VALUES ('2024-03-05', '2024-03-05 10:00:00')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM dd WHERE d = '05/03/2024'");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dd WHERE d = '03/05/2024'");
            assertAnswer(
                "121",
                "SELECT DATEDIFF(day, '01/02/2024', '05/02/2024')");
            assertAnswer(
                "2024-01-15",
                "SELECT TO_DATE('2024-01-15', 'AUTO')");
            assertAnswer(
                "null",
                "SELECT TRY_TO_DATE('05/03/2024', 'YYYY-MM-DD')");
            assertAnswer(
                "2024-03-05",
                "SELECT PARSE_JSON('\"05/03/2024\"')::DATE");
            assertAnswer(
                "2024-03-05",
                "SELECT d FROM dd WHERE d BETWEEN '01/03/2024' AND '31/03/2024'");
            assertAnswer(
                "2024-03-05",
                "EXECUTE IMMEDIATE $$BEGIN RETURN '05/03/2024'::DATE; END;$$");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET DATE_INPUT_FORMAT = 'auto'");
            assertAnswer(
                "2024-05-03",
                "SELECT '05/03/2024'::DATE");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET DATE_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI'");
            assertAnswer(
                "2024-03-05T10:00|2024-03-05T10:00",
                "SELECT TIMESTAMP '05/03/2024 10:00', TO_TIMESTAMP_TZ('05/03/2024 10:00')::TIMESTAMP_NTZ");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM dd WHERE t = '05/03/2024 10:00'");
            assertAnswer(
                "REFUSED Timestamp '1709632800' is not recognized",
                "SELECT TO_TIMESTAMP('1709632800')");
            assertAnswer(
                "2024-05-03",
                "SELECT TO_DATE('05/03/2024')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIMESTAMP_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIME_INPUT_FORMAT = 'HH12:MI AM'");
            assertAnswer(
                "15:15",
                "SELECT TIME '03:15 PM'");
            assertAnswer(
                "true",
                "SELECT TIME_FROM_PARTS(15, 15, 0) = '03:15 PM'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIME_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI'");
            assertAnswer(
                "2024-03-05T10:00|2024-03-05T10:00",
                "SELECT TO_TIMESTAMP('05/03/2024 10:00', 'AUTO'), TO_TIMESTAMP_NTZ('2024-03-05 10:00:00', 'AUTO')");
            assertAnswer(
                "2024-03-05T10:00",
                "SELECT TRY_TO_TIMESTAMP('2024-03-05 10:00:00', 'AUTO')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIMESTAMP_INPUT_FORMAT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIME_INPUT_FORMAT = 'HH12:MI AM'");
            assertAnswer(
                "15:15|15:15",
                "SELECT TO_TIME('15:15', 'AUTO'), TRY_TO_TIME('15:15:00', 'AUTO')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIME_INPUT_FORMAT");
        } finally {
            answer(
                "alter session unset date_input_format");
            answer(
                "alter session unset timestamp_input_format");
            answer(
                "alter session unset time_input_format");
            answer(
                "DROP TABLE IF EXISTS dd");
        }
    }
}
