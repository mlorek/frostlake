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
 * What an Iceberg table holds: a FLOAT member of a structured value at 32 bits, a column-list CTAS held to the
 * format's int and long, and LONG as a column type, the format's long, in a CREATE ICEBERG TABLE's column
 * definitions alone (all live-verified).
 */
public class IcebergHeldValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void structuredFloats() {
        assertAnswer("Table ICE2041_S successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_s (o OBJECT(f FLOAT), a ARRAY(FLOAT), d OBJECT(g DOUBLE))");
        assertAnswer("1",
            "INSERT INTO ice2041_s SELECT OBJECT_CONSTRUCT('f', 1.1)::OBJECT(f FLOAT), [1.1]::ARRAY(FLOAT), "
                + "OBJECT_CONSTRUCT('g', 1.1)::OBJECT(g DOUBLE)");
        assertAnswer("false|false|true",
            "SELECT o:f::DOUBLE = 1.1, a[0]::DOUBLE = 1.1, d:g::DOUBLE = 1.1 FROM ice2041_s");
        assertAnswer("REFUSED Number out of representable range: type int, value 2147483648",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_c (i INT, f FLOAT) AS SELECT 2147483648, 1.1");
        assertAnswer("Table ICE2041_C successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_c (i INT, f FLOAT) AS SELECT 2147483647, 1.1");
        assertAnswer("REFUSED Number out of representable range: type int, value -2147483649",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_c (i INT, f FLOAT) AS SELECT -2147483649, 1");
        assertAnswer("Table ICE2041_L successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_l (b LONG) AS SELECT 9223372036854775807");
        assertShapedAnswer("REFUSED Number out of representable range: type long, value <ID19>",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_l (b LONG) AS SELECT 9223372036854775808");
    }

    @Test
    public void heldReads() {
        engine.execute("CREATE OR REPLACE ICEBERG TABLE ice2041_s (o OBJECT(f FLOAT), a ARRAY(FLOAT), d OBJECT(g "
            + "DOUBLE))");
        engine.execute("INSERT INTO ice2041_s SELECT OBJECT_CONSTRUCT('f', 1.1)::OBJECT(f FLOAT), "
            + "[1.1]::ARRAY(FLOAT), OBJECT_CONSTRUCT('g', 1.1)::OBJECT(g DOUBLE)");
        engine.execute("CREATE OR REPLACE ICEBERG TABLE ice2041_c (i INT, f FLOAT) AS SELECT 2147483647, 1.1");
        engine.execute("CREATE OR REPLACE ICEBERG TABLE ice2041_v (f FLOAT, g DOUBLE)");
        engine.execute("INSERT INTO ice2041_v VALUES (0.1, 0.1), (16777217, 16777217), (123456.789, 123456.789), "
            + "(3.4e38, 3.4e38)");
        assertAnswer("1.100000024|1.100000024|1.100000024",
            "SELECT o:f::VARCHAR, a[0]::VARCHAR, f::VARCHAR FROM ice2041_s, ice2041_c");
        assertAnswer("1.100000023841858|1.100000023841858|1.100000023841858",
            "SELECT o:f::NUMBER(20,15), a[0]::NUMBER(20,15), f::NUMBER(20,15) FROM ice2041_s, ice2041_c");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TO_JSON': (OBJECT(f FLOAT))",
            "SELECT TO_JSON(o), TO_JSON(a), TO_JSON(d) FROM ice2041_s");
        assertAnswer("false|true|false",
            "SELECT f = 1.100000024, f = 1.100000023841858::FLOAT, o:f::DOUBLE = 1.100000024 FROM ice2041_s, "
                + "ice2041_c");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 3.4e+38",
            "SELECT f, f::VARCHAR, f::NUMBER(38,10), g, g::VARCHAR FROM ice2041_v ORDER BY f");
        assertAnswer("true|false", "SELECT f = 16777216, f = 16777217 FROM ice2041_v WHERE g = 16777217");
        assertAnswer("Table ICE2041_L successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_l (b LONG, c INT)");
        assertAnswer("B|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; C|NUMBER(10,0)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null",
            "DESCRIBE TABLE ice2041_l");
        assertAnswer("1", "INSERT INTO ice2041_l VALUES (9223372036854775807, 1)");
        assertShapedAnswer("REFUSED Number out of representable range: type long, value <ID19>",
            "INSERT INTO ice2041_l VALUES (9223372036854775808, 1)");
        assertAnswer("REFUSED Number out of representable range: type int, value 2147483648",
            "INSERT INTO ice2041_l VALUES (1, 2147483648)");
        assertShapedAnswer("<ID19>|1|NUMBER(19,0)[SB8]", "SELECT b, c, SYSTEM$TYPEOF(b) FROM ice2041_l");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'LONG'.",
            "CREATE OR REPLACE TABLE t2041 (b LONG)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'.",
            "SELECT 1::LONG");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '('.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_l (b LONG(5))");
        assertAnswer("Table ICE2041_L successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_l (o OBJECT(b LONG), a ARRAY(LONG))");
        assertAnswer("O|OBJECT(b NUMBER(19,0))|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; A|"
                + "ARRAY(NUMBER(19,0))|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
            "DESCRIBE TABLE ice2041_l");
        assertAnswer("Table ICE2041_L successfully created.", "CREATE OR REPLACE ICEBERG TABLE ice2041_l (b long)");
        assertAnswer("B|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null", "DESCRIBE TABLE ice2041_l");
    }

    @Test
    public void longColumns() {
        assertAnswer("Table ICE2041_L successfully created.", "CREATE OR REPLACE ICEBERG TABLE ice2041_l (i INT)");
        assertAnswer("Statement executed successfully.", "ALTER ICEBERG TABLE ice2041_l ADD COLUMN c LONG");
        assertAnswer("I|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; C|NUMBER(19,0)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null",
            "DESCRIBE TABLE ice2041_l");
        assertAnswer("Statement executed successfully.",
            "ALTER ICEBERG TABLE ice2041_l ALTER COLUMN i SET DATA TYPE LONG");
        assertAnswer("I|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; C|NUMBER(19,0)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null",
            "DESCRIBE TABLE ice2041_l");
        assertAnswer("Table ICE2041_M successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_m (m MAP(LONG, LONG))");
        assertAnswer("M|MAP(NUMBER(19,0), NUMBER(19,0))|COLUMN|Y|null|N|N|null|null|null|null|null|null|null",
            "DESCRIBE TABLE ice2041_m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'long'.",
            "CREATE OR REPLACE TABLE t2041 (b long, c VARCHAR(0))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'LONG'.",
            "CREATE OR REPLACE FUNCTION f2041(x LONG) RETURNS INT AS 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'LONG'.",
            "SELECT 1::ARRAY(LONG)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'LONG'.",
            "SELECT CAST(1 AS LONG)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'long'.",
            "SELECT TRY_CAST(1 AS long) AS b");
        assertAnswer("Table T2041 successfully created.", "CREATE OR REPLACE TABLE t2041 (long INT)");
        assertAnswer("1", "INSERT INTO t2041 VALUES (4)");
        assertAnswer("4", "SELECT long.long FROM t2041 long");
        assertAnswer("Table ICE2041_L successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_l (b LONG DEFAULT 5, c LONG NOT NULL)");
        assertAnswer("1", "INSERT INTO ice2041_l (c) VALUES (1)");
        assertAnswer("5|1", "SELECT b, c FROM ice2041_l");
        assertAnswer("Table ICE2041_P successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_p (b BIGINT, n NUMBER(10,2), i INT)");
        assertAnswer("Table ICE2041_Q successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_q AS SELECT * FROM ice2041_p");
        assertAnswer("B|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; N|NUMBER(10,2)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null ;; I|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null|null",
            "DESCRIBE TABLE ice2041_q");
        assertAnswer("REFUSED SQL compilation error: cannot change column N from type NUMBER(10,2) to NUMBER(12,2) | ",
            "ALTER ICEBERG TABLE ice2041_q ALTER COLUMN n SET DATA TYPE NUMBER(12,2)");
        assertAnswer("REFUSED SQL compilation error: cannot change column B from type NUMBER(19,0) to NUMBER(38,0) | ",
            "ALTER ICEBERG TABLE ice2041_q ALTER COLUMN b SET DATA TYPE NUMBER(38,0)");
        assertAnswer("REFUSED SQL compilation error: cannot change column I from type NUMBER(10,0) to NUMBER(19,0) | ",
            "ALTER ICEBERG TABLE ice2041_q ALTER COLUMN i SET DATA TYPE NUMBER(19,0)");
        assertAnswer("B|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; N|NUMBER(10,2)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null ;; I|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null|null",
            "DESCRIBE TABLE ice2041_q");
        assertAnswer("Table ICE2041_R successfully created.",
            "CREATE OR REPLACE ICEBERG TABLE ice2041_r LIKE ice2041_p");
        assertAnswer("B|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; N|NUMBER(10,2)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null ;; I|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null|null",
            "DESCRIBE TABLE ice2041_r");
        assertAnswer("REFUSED SQL compilation error: cannot change column N from type NUMBER(10,2) to NUMBER(12,2) | ",
            "ALTER ICEBERG TABLE ice2041_r ALTER COLUMN n SET DATA TYPE NUMBER(12,2)");
        assertAnswer("REFUSED SQL compilation error: cannot change column B from type NUMBER(19,0) to NUMBER(38,0) | ",
            "ALTER ICEBERG TABLE ice2041_r ALTER COLUMN b SET DATA TYPE NUMBER(38,0)");
        assertAnswer("B|NUMBER(19,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null|null ;; N|NUMBER(10,2)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null|null ;; I|NUMBER(10,0)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null|null",
            "DESCRIBE TABLE ice2041_r");
    }
}
