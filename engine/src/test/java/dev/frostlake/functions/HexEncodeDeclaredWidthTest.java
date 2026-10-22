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
 * HEX_ENCODE holds its input to the width its type declares: a table function's BINARY(1) column holding two
 * bytes is refused where it is encoded, through a derived table too, while the other functions read it whole
 * (all live-verified).
 */
public class HexEncodeDeclaredWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void declaredWidths() {
        engine.execute("CREATE OR REPLACE FUNCTION t_bin() RETURNS TABLE (b BINARY(1)) AS 'SELECT "
            + "TO_BINARY(''ABCD'')'");
        engine.execute("CREATE OR REPLACE FUNCTION t_bin2() RETURNS TABLE (b BINARY(2)) AS 'SELECT "
            + "TO_BINARY(''ABCD'')'");
        engine.execute("CREATE OR REPLACE FUNCTION t_bin3() RETURNS TABLE (b BINARY) AS 'SELECT TO_BINARY(''ABCD'')'");
        assertAnswer("REFUSED Binary value 'ABCD' is too long and would be truncated in 'HEX_ENCODE'",
            "SELECT HEX_ENCODE(b) FROM TABLE(t_bin())");
        assertAnswer("2", "SELECT LENGTH(b) FROM TABLE(t_bin())");
        assertAnswer("ABCD", "SELECT b FROM TABLE(t_bin())");
        assertAnswer("q80=", "SELECT BASE64_ENCODE(b) FROM TABLE(t_bin())");
        assertAnswer("ABCD", "SELECT TO_VARCHAR(b, 'HEX') FROM TABLE(t_bin())");
        assertAnswer("ABCD", "SELECT TO_VARCHAR(b) FROM TABLE(t_bin())");
        assertAnswer("true", "SELECT b = TO_BINARY('ABCD') FROM TABLE(t_bin())");
        assertAnswer("7838496fd0586421bbb500bb6f472f13", "SELECT MD5(b) FROM TABLE(t_bin())");
        assertAnswer("REFUSED Binary value 'ABCD' is too long and would be truncated in 'HEX_ENCODE'",
            "SELECT HEX_ENCODE(b, 0) FROM TABLE(t_bin())");
        assertAnswer("REFUSED Binary value 'ABCD' is too long and would be truncated in 'HEX_ENCODE'",
            "SELECT HEX_ENCODE(b) FROM (SELECT b FROM TABLE(t_bin()))");
        assertAnswer("ABCD", "SELECT HEX_ENCODE(b) FROM TABLE(t_bin2())");
        assertAnswer("ABCD", "SELECT HEX_ENCODE(b) FROM TABLE(t_bin3())");
        assertShape("B:BINARY", "SELECT b FROM TABLE(t_bin())");
        assertAnswer("q80=", "SELECT BASE64_ENCODE(b, 0) FROM TABLE(t_bin())");
        assertAnswer("ABCD", "SELECT HEX_ENCODE(TO_BINARY('ABCD'))");
        assertAnswer("REFUSED Binary value 'ABCD' is too long and would be truncated",
            "SELECT HEX_ENCODE(b::BINARY(1)) FROM TABLE(t_bin3())");
        assertAnswer("REFUSED Binary value 'ABCD' is too long and would be truncated",
            "SELECT HEX_ENCODE(TO_BINARY('ABCD')::BINARY(1))");
        assertAnswer("32825eb98de842ee3e4df005a07b7d65522a46a0", "SELECT SHA1(b) FROM TABLE(t_bin())");
        assertAnswer("q80=", "SELECT TO_CHAR(b, 'BASE64') FROM TABLE(t_bin())");
        assertAnswer("", "SELECT HEX_ENCODE(b) FROM TABLE(t_bin()) WHERE 1 = 0");
        assertAnswer("ABCD", "SELECT b::VARCHAR FROM TABLE(t_bin())");
    }
}
