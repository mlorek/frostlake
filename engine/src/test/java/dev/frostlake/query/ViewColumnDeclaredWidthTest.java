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
 * A view's NUMBER column is told within the capacity it is declared with — SUM(n + 1) declared NUMBER(18,2)
 * reads [SB8] through the view, and an expression over it follows — where an aggregate over the column, a
 * derived table and a WITH clause keep the body's own interval. BASE64_ENCODE and HEX_ENCODE over a binary an
 * expression makes with no size — TO_BINARY, a cast to BINARY — read the nominal 64 MB where a column's is its
 * stored size, and a line length adds nothing over any binary (live-verified).
 */
public class ViewColumnDeclaredWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewsColumnAndItsBody() {
        answer(
            "CREATE OR REPLACE TABLE t847 (n NUMBER(5,2), n10 NUMBER(10,2))");
        answer(
            "INSERT INTO t847 VALUES (1.5, 17.25), (2.5, 3.5)");
        answer(
            "CREATE OR REPLACE VIEW v847 AS SELECT SUM(n + 0.05) AS s1, SUM(n + 1) AS s2, SUM(n) AS s3, SUM(n10) "
                + "AS s4, SUM(n * 2) AS s5, AVG(n) AS s6, MAX(n + 1) AS s7, COUNT(*) AS s8 FROM t847");
        answer(
            "CREATE OR REPLACE VIEW w847 AS SELECT n + 1 AS p1, n10 + 0.05 AS p2, n AS p3 FROM t847");
        answer(
            "CREATE OR REPLACE VIEW x847 AS SELECT s2 + 1 AS q1, s2 AS q2 FROM v847");
        answer(
            "CREATE OR REPLACE TABLE tb (b BINARY, b100 BINARY(100), s VARCHAR, s10 VARCHAR(10))");
        answer(
            "INSERT INTO tb SELECT TO_BINARY('ab', 'UTF-8'), TO_BINARY('ab', 'UTF-8'), 'ab', 'ab'");
        assertAnswer(
            "NUMBER(18,2)[SB8]|NUMBER(18,2)[SB8]|NUMBER(17,2)[SB8]|NUMBER(22,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(s1), SYSTEM$TYPEOF(s2), SYSTEM$TYPEOF(s3), SYSTEM$TYPEOF(s4) FROM v847");
        assertAnswer(
            "NUMBER(18,2)[SB8]|NUMBER(23,8)[SB16]|NUMBER(6,2)[SB2]|NUMBER(18,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(s5), SYSTEM$TYPEOF(s6), SYSTEM$TYPEOF(s7), SYSTEM$TYPEOF(s8) FROM v847");
        assertAnswer(
            "NUMBER(22,2)[SB8]|NUMBER(24,2)[SB16]|NUMBER(17,2)[SB8]|NUMBER(22,2)[SB8] ;; NUMBER(22,"
                + "2)[SB8]|NUMBER(24,2)[SB16]|NUMBER(17,2)[SB8]|NUMBER(22,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 0.05)), SYSTEM$TYPEOF(SUM(n + 1)), SYSTEM$TYPEOF(SUM(n)), "
                + "SYSTEM$TYPEOF(SUM(n10)) FROM t847");
        assertAnswer(
            "NUMBER(18,2)[SB8]|NUMBER(23,8)[SB16]|NUMBER(6,2)[SB2]|NUMBER(18,0)[SB1] ;; NUMBER(18,"
                + "2)[SB8]|NUMBER(23,8)[SB16]|NUMBER(6,2)[SB2]|NUMBER(18,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(SUM(n * 2)), SYSTEM$TYPEOF(AVG(n)), SYSTEM$TYPEOF(MAX(n + 1)), "
                + "SYSTEM$TYPEOF(COUNT(*)) FROM t847");
        assertAnswer(
            "NUMBER(18,2)[SB8]|NUMBER(18,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(s1), SYSTEM$TYPEOF(s2) FROM (SELECT SUM(n + 0.05) AS s1, SUM(n + 1) AS s2 FROM "
                + "t847)");
        assertAnswer(
            "NUMBER(18,2)[SB8]|NUMBER(18,2)[SB16]",
            "WITH c AS (SELECT SUM(n + 0.05) AS s1, SUM(n + 1) AS s2 FROM t847) SELECT SYSTEM$TYPEOF(s1), "
                + "SYSTEM$TYPEOF(s2) FROM c");
        assertAnswer(
            "NUMBER(6,2)[SB2]|NUMBER(11,2)[SB2]|NUMBER(5,2)[SB2] ;; NUMBER(6,2)[SB2]|NUMBER(11,2)[SB2]|NUMBER(5,"
                + "2)[SB2]",
            "SELECT SYSTEM$TYPEOF(p1), SYSTEM$TYPEOF(p2), SYSTEM$TYPEOF(p3) FROM w847");
        assertAnswer(
            "NUMBER(19,2)[SB8]|NUMBER(18,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(q1), SYSTEM$TYPEOF(q2) FROM x847");
        assertAnswer(
            "NUMBER(19,2)[SB8]|NUMBER(19,2)[SB8]|NUMBER(18,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(s2 + 1), SYSTEM$TYPEOF(s2 * 2), SYSTEM$TYPEOF(-s2) FROM v847");
        assertAnswer(
            "NUMBER(30,2)[SB16]|NUMBER(18,2)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(s2)), SYSTEM$TYPEOF(MAX(s2)) FROM v847");
        assertAnswer(
            "NUMBER(24,2)[SB16]|NUMBER(17,2)[SB8] ;; NUMBER(24,2)[SB16]|NUMBER(17,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(SUM(p1)), SYSTEM$TYPEOF(SUM(p3)) FROM w847");
        assertAnswer(
            "4.10|6.00|4.00|20.75",
            "SELECT s1, s2, s3, s4 FROM v847");
        assertShape(
            "S1:NUMBER(18,2), S2:NUMBER(18,2), S3:NUMBER(17,2), S4:NUMBER(22,2), S5:NUMBER(18,2), S6:NUMBER(23,8),"
                + " S7:NUMBER(6,2), S8:NUMBER(18,0)",
            "SELECT s1, s2, s3, s4, s5, s6, s7, s8 FROM v847");
    }

    @Test
    public void encodedBinaryWidths() {
        answer(
            "CREATE OR REPLACE TABLE t847 (n NUMBER(5,2), n10 NUMBER(10,2))");
        answer(
            "INSERT INTO t847 VALUES (1.5, 17.25), (2.5, 3.5)");
        answer(
            "CREATE OR REPLACE VIEW v847 AS SELECT SUM(n + 0.05) AS s1, SUM(n + 1) AS s2, SUM(n) AS s3, SUM(n10) "
                + "AS s4, SUM(n * 2) AS s5, AVG(n) AS s6, MAX(n + 1) AS s7, COUNT(*) AS s8 FROM t847");
        answer(
            "CREATE OR REPLACE VIEW w847 AS SELECT n + 1 AS p1, n10 + 0.05 AS p2, n AS p3 FROM t847");
        answer(
            "CREATE OR REPLACE VIEW x847 AS SELECT s2 + 1 AS q1, s2 AS q2 FROM v847");
        answer(
            "CREATE OR REPLACE TABLE tb (b BINARY, b100 BINARY(100), s VARCHAR, s10 VARCHAR(10))");
        answer(
            "INSERT INTO tb SELECT TO_BINARY('ab', 'UTF-8'), TO_BINARY('ab', 'UTF-8'), 'ab', 'ab'");
        assertShape(
            "C1:VARCHAR(89478488), C2:VARCHAR(89478488), C3:VARCHAR(12), C4:VARCHAR(23)",
            "SELECT BASE64_ENCODE(TO_BINARY('ab', 'UTF-8')) c1, BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 1) c2, "
                + "BASE64_ENCODE('ab') c3, BASE64_ENCODE('ab', 1) c4");
        assertShape(
            "C1:VARCHAR(11184812), C2:VARCHAR(11184812), C3:VARCHAR(136), C4:VARCHAR(136), C5:VARCHAR(89478488), "
                + "C6:VARCHAR(134217728), C7:VARCHAR(56), C8:VARCHAR(111)",
            "SELECT BASE64_ENCODE(b) c1, BASE64_ENCODE(b, 1) c2, BASE64_ENCODE(b100) c3, BASE64_ENCODE(b100, 1) "
                + "c4, BASE64_ENCODE(s) c5, BASE64_ENCODE(s, 1) c6, BASE64_ENCODE(s10) c7, BASE64_ENCODE(s10, 1) c8 "
                + "FROM tb");
        assertShape(
            "C1:BINARY, C2:BINARY, C3:BINARY, C4:BINARY, C5:BINARY, C6:BINARY, C7:BINARY, C8:BINARY",
            "SELECT TO_BINARY('ab', 'UTF-8') c1, TO_BINARY('ab') c2, b c3, b100 c4, TO_BINARY(s) c5, "
                + "TO_BINARY(s10) c6, s::BINARY c7, 'ab'::BINARY c8 FROM tb");
        assertShape(
            "C1:VARCHAR(89478488), C2:VARCHAR(89478488), C3:VARCHAR(89478488), C4:VARCHAR(136), "
                + "C5:VARCHAR(11184812)",
            "SELECT BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 2) c1, BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 76) c2,"
                + " BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 0) c3, BASE64_ENCODE(b100, 76) c4, BASE64_ENCODE(b, 76) "
                + "c5 FROM tb");
        assertShape(
            "C1:VARCHAR(134217728), C2:VARCHAR(16777216), C3:VARCHAR(200), C4:VARCHAR(16), C5:VARCHAR(89478488), "
                + "C6:VARCHAR(89478488), C7:VARCHAR(89478488)",
            "SELECT HEX_ENCODE(TO_BINARY('ab', 'UTF-8')) c1, HEX_ENCODE(b) c2, HEX_ENCODE(b100) c3, "
                + "HEX_ENCODE('ab') c4, BASE64_ENCODE(TO_BINARY(s10), 1) c5, BASE64_ENCODE(TO_BINARY('6162'), 1) c6, "
                + "BASE64_ENCODE(s::BINARY, 1) c7 FROM tb");
        assertAnswer(
            "YWI=|Y | W | I | =|Y | W | I | =|Y | W | I | =|Y | W | I | =",
            "SELECT BASE64_ENCODE(TO_BINARY('ab', 'UTF-8')), BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 1), "
                + "BASE64_ENCODE(b, 1), BASE64_ENCODE(b100, 1), BASE64_ENCODE(s10, 1) FROM tb");
        assertAnswer(
            "VARCHAR(89478488)[LOB]|VARCHAR(89478488)[LOB]|BINARY[LOB]",
            "SELECT SYSTEM$TYPEOF(BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'))), "
                + "SYSTEM$TYPEOF(BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 1)), SYSTEM$TYPEOF(TO_BINARY('ab', 'UTF-8'))");
        assertAnswer(
            "Table TC successfully created.",
            "CREATE OR REPLACE TABLE tc AS SELECT BASE64_ENCODE(TO_BINARY('ab', 'UTF-8')) c1, "
                + "BASE64_ENCODE(TO_BINARY('ab', 'UTF-8'), 1) c2, BASE64_ENCODE(b) c3, BASE64_ENCODE(b, 1) c4, "
                + "BASE64_ENCODE(b100) c5, BASE64_ENCODE(b100, 1) c6, TO_BINARY('ab', 'UTF-8') c7 FROM tb");
        assertAnswer(
            "C1|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C2|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C3|VARCHAR(11184812)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C4|VARCHAR(11184812)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C5|VARCHAR(136)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C6|VARCHAR(136)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C7|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE tc");
    }
}
