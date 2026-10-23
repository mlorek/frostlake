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
 * Which overload of a SQL function a call runs: a default that would make two overloads ambiguous is refused at
 * CREATE, a text goes to a NUMBER, a DATE, a FLOAT or a timestamp before a TIME, and overloads that weigh alike
 * run in the order of their signatures, the timestamp kinds by their own names (all live-verified).
 */
public class UdfOverloadPairsTest extends MeasuredCellsTestSupport {

    @Test
    public void overloadsByLengthAndFamily() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Function OV57 successfully created.",
            "CREATE OR REPLACE FUNCTION ov57(a NUMBER) RETURNS VARCHAR AS '''one'''");
        assertAnswer("REFUSED SQL compilation error: Cannot overload FUNCTION 'OV57' as it would cause ambiguous "
                + "FUNCTION overloading.",
            "CREATE OR REPLACE FUNCTION ov57(a NUMBER, b NUMBER DEFAULT 1) RETURNS VARCHAR AS '''two'''");
        assertAnswer("one", "SELECT ov57(1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[OV57(1, 2)] expected 1, got 2",
            "SELECT ov57(1, 2)");
        assertAnswer("Function OW57 successfully created.",
            "CREATE OR REPLACE FUNCTION ow57(a VARCHAR) RETURNS VARCHAR AS '''text'''");
        assertAnswer("Function OW57 successfully created.",
            "CREATE OR REPLACE FUNCTION ow57(a NUMBER, b VARCHAR DEFAULT 'x') RETURNS VARCHAR AS '''number'''");
        assertAnswer("number", "SELECT ow57(1)");
        assertAnswer("text", "SELECT ow57('1')");
        assertAnswer("Function OT57 successfully created.",
            "CREATE OR REPLACE FUNCTION ot57(a TIME, b VARCHAR) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function OT57 successfully created.",
            "CREATE OR REPLACE FUNCTION ot57(a TIMESTAMP_NTZ, b VARCHAR) RETURNS VARCHAR AS '''ntz'''");
        assertAnswer("ntz", "SELECT ot57('10:00:00', 'x')");
        assertAnswer("ntz", "SELECT ot57('2024-01-02 10:00:00', 'x')");
        assertAnswer("ntz", "SELECT ot57(DATE '2024-01-02', 'x')");
        assertAnswer("Function OA57 successfully created.",
            "CREATE OR REPLACE FUNCTION oa57(a VARIANT, b NUMBER) RETURNS VARCHAR AS '''variant'''");
        assertAnswer("Function OA57 successfully created.",
            "CREATE OR REPLACE FUNCTION oa57(a ARRAY, b NUMBER) RETURNS VARCHAR AS '''array'''");
        assertAnswer("array", "SELECT oa57([1], 1)");
        assertAnswer("variant", "SELECT oa57(PARSE_JSON('[1]'), 1)");
        assertAnswer("variant", "SELECT oa57(OBJECT_CONSTRUCT('a', 1), 1)");
        assertAnswer("Function OB57 successfully created.",
            "CREATE OR REPLACE FUNCTION ob57(a BINARY, b NUMBER) RETURNS VARCHAR AS '''binary'''");
        assertAnswer("Function OB57 successfully created.",
            "CREATE OR REPLACE FUNCTION ob57(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''text'''");
        assertAnswer("binary", "SELECT ob57(X'AB', 1)");
        assertAnswer("text", "SELECT ob57('AB', 1)");
        assertAnswer("text", "SELECT ob57(1, 1)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void textAndNullAmongTemporals() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Function TN58 successfully created.",
            "CREATE OR REPLACE FUNCTION tn58(a TIME) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function TN58 successfully created.",
            "CREATE OR REPLACE FUNCTION tn58(a NUMBER) RETURNS VARCHAR AS '''number'''");
        assertAnswer("number|number|number", "SELECT tn58('10:00:00'), tn58(NULL), tn58('1')");
        assertAnswer("Function TT58 successfully created.",
            "CREATE OR REPLACE FUNCTION tt58(a TIME) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function TT58 successfully created.",
            "CREATE OR REPLACE FUNCTION tt58(a TIMESTAMP_NTZ) RETURNS VARCHAR AS '''timestamp_ntz'''");
        assertAnswer("timestamp_ntz|time|timestamp_ntz", "SELECT tt58('10:00:00'), tt58(NULL), tt58('1')");
        assertAnswer("Function TD58 successfully created.",
            "CREATE OR REPLACE FUNCTION td58(a TIME) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function TD58 successfully created.",
            "CREATE OR REPLACE FUNCTION td58(a DATE) RETURNS VARCHAR AS '''date'''");
        assertAnswer("date|date|date", "SELECT td58('10:00:00'), td58(NULL), td58('1')");
        assertAnswer("Function SD58 successfully created.",
            "CREATE OR REPLACE FUNCTION sd58(a TIMESTAMP_NTZ) RETURNS VARCHAR AS '''timestamp_ntz'''");
        assertAnswer("Function SD58 successfully created.",
            "CREATE OR REPLACE FUNCTION sd58(a DATE) RETURNS VARCHAR AS '''date'''");
        assertAnswer("date|date|date", "SELECT sd58('10:00:00'), sd58(NULL), sd58('1')");
        assertAnswer("Function TF58 successfully created.",
            "CREATE OR REPLACE FUNCTION tf58(a TIME) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function TF58 successfully created.",
            "CREATE OR REPLACE FUNCTION tf58(a FLOAT) RETURNS VARCHAR AS '''float'''");
        assertAnswer("float|float|float", "SELECT tf58('10:00:00'), tf58(NULL), tf58('1')");
        assertAnswer("Function SF58 successfully created.",
            "CREATE OR REPLACE FUNCTION sf58(a TIMESTAMP_NTZ) RETURNS VARCHAR AS '''timestamp_ntz'''");
        assertAnswer("Function SF58 successfully created.",
            "CREATE OR REPLACE FUNCTION sf58(a FLOAT) RETURNS VARCHAR AS '''float'''");
        assertAnswer("float|float|float", "SELECT sf58('10:00:00'), sf58(NULL), sf58('1')");
        assertAnswer("Function TL58 successfully created.",
            "CREATE OR REPLACE FUNCTION tl58(a TIME) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function TL58 successfully created.",
            "CREATE OR REPLACE FUNCTION tl58(a TIMESTAMP_LTZ) RETURNS VARCHAR AS '''timestamp_ltz'''");
        assertAnswer("timestamp_ltz|time|timestamp_ltz", "SELECT tl58('10:00:00'), tl58(NULL), tl58('1')");
        assertAnswer("Function ZN58 successfully created.",
            "CREATE OR REPLACE FUNCTION zn58(a TIMESTAMP_TZ) RETURNS VARCHAR AS '''timestamp_tz'''");
        assertAnswer("Function ZN58 successfully created.",
            "CREATE OR REPLACE FUNCTION zn58(a TIMESTAMP_NTZ) RETURNS VARCHAR AS '''timestamp_ntz'''");
        assertAnswer("timestamp_ntz|timestamp_ntz|timestamp_ntz", "SELECT zn58('10:00:00'), zn58(NULL), zn58('1')");
        assertAnswer("Function TB58 successfully created.",
            "CREATE OR REPLACE FUNCTION tb58(a TIME) RETURNS VARCHAR AS '''time'''");
        assertAnswer("Function TB58 successfully created.",
            "CREATE OR REPLACE FUNCTION tb58(a BOOLEAN) RETURNS VARCHAR AS '''boolean'''");
        assertAnswer("boolean|boolean|boolean", "SELECT tb58('10:00:00'), tb58(NULL), tb58('1')");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
