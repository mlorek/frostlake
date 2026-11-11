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
 * TO_CHAR's FF without a digit count prints the scale of the value's own type (live-verified): a TIMESTAMP_NTZ(6)'s
 * six digits, a TIME(1)'s one, a TIMESTAMP_TZ(2)'s two, a TIMESTAMP_NTZ(0)'s none, a DATEADD's nine.
 */
public class ToCharFractionScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void ffPrintsTheTypesScale() {
        assertCells(new String[][] {
            {"SELECT TO_CHAR('2024-01-02 04:04:05.006'::TIMESTAMP_NTZ(6), 'HH24:MI:SS.FF')", "04:04:05.006000"},
            {"SELECT TO_CHAR('10:00:00.5'::TIME(1), 'HH24:MI:SS.FF')", "10:00:00.5"},
            {"SELECT TO_CHAR('2024-01-02 04:04:05.006'::TIMESTAMP_NTZ(3), 'FF3')", "006"},
            {"SELECT TO_CHAR('2024-01-02 04:04:05.006'::TIMESTAMP_LTZ(3), 'HH24:MI:SS.FF')", "04:04:05.006"},
            {"SELECT TO_CHAR('2024-01-02 04:04:05.006'::TIMESTAMP_TZ(2), 'HH24:MI:SS.FF')", "04:04:05.00"},
            {"ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'",
                "Statement executed successfully."},
            {"CREATE OR REPLACE TABLE tf934 (t TIMESTAMP_NTZ(3))", "Table TF934 successfully created."},
            {"INSERT INTO tf934 VALUES ('2024-01-02 04:04:05.006')", "1"},
            {"SELECT TO_CHAR(t, 'HH24:MI:SS.FF') FROM tf934", "04:04:05.006"},
            {"SELECT TO_CHAR(DATEADD(ms, 1, t), 'HH24:MI:SS.FF') FROM tf934", "04:04:05.007000000"},
            {"ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT", "Statement executed successfully."},
        });
    }
}
