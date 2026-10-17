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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The zones of the old rule names — CET, MET, EET and WET — are zones of their own to the account's JavaScript engine,
 * named by their offset where another zone has a long name: {@code GMT+0100 (GMT+01:00)}, and {@code (GMT)} at none.
 * A formatter given a zone it does not know is refused as {@code Invalid time zone specified: X} (all live-verified).
 */
public class JavaScriptLegacyZoneTest extends MeasuredCellsTestSupport {

    @Test
    public void theOldRuleZonesAreNamedByTheirOffset() {
        try {
            engine.execute("CREATE OR REPLACE FUNCTION nf_z() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return "
                + "Intl.DateTimeFormat().resolvedOptions().timeZone;'");
            engine.execute("CREATE OR REPLACE FUNCTION nf_zo(z VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'try { "
                + "return new Intl.DateTimeFormat(\"en-US\", {timeZone: Z}).resolvedOptions().timeZone; } "
                + "catch (e) { return \"ERR \" + e.message; }'");
            engine.execute("CREATE OR REPLACE FUNCTION nf_zd() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return new "
                + "Date(0).toString();'");
            engine.execute("CREATE OR REPLACE FUNCTION nf_zs() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return new "
                + "Date(Date.UTC(2020, 6, 1)).toString();'");
            engine.execute("CREATE OR REPLACE FUNCTION nf_zt(z VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'try { "
                + "return new Date(0).toLocaleString(\"en-US\", {timeZone: Z}); } catch (e) { return \"ERR \" "
                + "+ e.message; }'");
            assertCells(new String[][] {
                {"ALTER SESSION SET TIMEZONE = 'CET'", "Statement executed successfully."},
                {"SELECT nf_z(), nf_zd(), nf_zs()",
                    "CET|Thu Jan 01 1970 01:00:00 GMT+0100 (GMT+01:00)|Wed Jul 01 2020 02:00:00 GMT+0200 (GMT+02:00)"},
                {"ALTER SESSION SET TIMEZONE = 'WET'", "Statement executed successfully."},
                {"ALTER SESSION SET TIMEZONE = 'EET'", "Statement executed successfully."},
                {"SELECT nf_z(), nf_zd(), nf_zs()",
                    "EET|Thu Jan 01 1970 02:00:00 GMT+0200 (GMT+02:00)|Wed Jul 01 2020 03:00:00 GMT+0300 (GMT+03:00)"},
                {"ALTER SESSION SET TIMEZONE = 'MET'", "Statement executed successfully."},
                {"SELECT nf_z(), nf_zd(), nf_zs()",
                    "MET|Thu Jan 01 1970 01:00:00 GMT+0100 (GMT+01:00)|Wed Jul 01 2020 02:00:00 GMT+0200 (GMT+02:00)"},
                {"ALTER SESSION SET TIMEZONE = 'UTC'", "Statement executed successfully."},
                {"SELECT nf_zo('Nonexistent/Zone')", "ERR Invalid time zone specified: Nonexistent/Zone"},
                {"SELECT nf_zo('Z')", "ERR Invalid time zone specified: Z"},
                {"SELECT nf_zo('CET'), nf_zo('WET')", "CET|WET"},
                {"SELECT nf_zt('Nonexistent/Zone')", "ERR Invalid time zone specified: Nonexistent/Zone"},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
