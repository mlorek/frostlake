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
 * UNIFORM, NORMAL, ZIPF and RANDSTR draw from the account's generator, and a RANDOM with a seed from the 64-bit
 * Mersenne Twister, so every value of every seed is the account's own (live-verified): the characters RANDSTR draws for
 * each seed and length, the value a seeded RANDOM gives each row, the whole numbers each distribution draws, and its
 * FLOAT values to the last bit.
 */
public class GeneratorDrawsTest extends MeasuredCellsTestSupport {

    /** RANDSTR over seeds from the least to the greatest, per row, and with whole, decimal and text seeds. */
    @Test
    public void randstrDrawsTheAccountsCharacters() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SELECT RANDSTR(20, 0)",
                "046Bony4mtrDm36UcvYd"},
            {"SELECT RANDSTR(20, 1)",
                "CaFHJdoX3upWliCCdAPX"},
            {"SELECT RANDSTR(20, 2)",
                "egeN43eQLumgly9keEGH"},
            {"SELECT RANDSTR(20, 3)",
                "QmNTpS3JtukzkNG1gJxr"},
            {"SELECT RANDSTR(20, 7)",
                "kL3hKdogjubQhNOQl1Wp"},
            {"SELECT RANDSTR(20, 42)",
                "XaNFsnyb6xQRQynx4zPp"},
            {"SELECT RANDSTR(20, 100)",
                "NNxiSGRqaBD885atgOku"},
            {"SELECT RANDSTR(20, 12345)",
                "DDGLbYIYiFKzggX3x9Ql"},
            {"SELECT RANDSTR(20, 987654321)",
                "vNjxdWcEJ9qs3ADTiwbQ"},
            {"SELECT RANDSTR(20, -1)",
                "nYxv4yJcEtujnNzcar7t"},
            {"SELECT RANDSTR(20, -2)",
                "LSYpJITjWtw0ox2v9mgJ"},
            {"SELECT RANDSTR(20, -42)",
                "2ZoxlozYCq2oSxOhkr71"},
            {"SELECT RANDSTR(20, 9223372036854775807)",
                "St20z3eH9YZOSi4HFWCY"},
            {"SELECT RANDSTR(20, -9223372036854775808)",
                "vzB6TS3zRYW8RyBpH0tI"},
            {"SELECT SEQ4() s, RANDSTR(6, SEQ4()) r FROM TABLE(GENERATOR(ROWCOUNT => 40)) ORDER BY s",
                "0|046Bon ;; 1|CaFHJd ;; 2|egeN43 ;; 3|QmNTpS ;; 4|tsmZJI ;; 5|5yV54y ;; 6|HFubpn ;; 7|kL3hKd ;; "
                + "8|WRCn43 ;; 9|yXbtpS ;; 10|b3KzKI ;; 11|N9jF5x ;; 12|pfSLpn ;; 13|2lrRKd ;; 14|Er0X52 ;; 15|gxz3qS "
                + ";; 16|TD89KI ;; 17|vJHf5x ;; 18|7Pglqn ;; 19|KVPrLd ;; 20|m1ox52 ;; 21|Y7XDqS ;; 22|AdwJLI ;; "
                + "23|dj5P6x ;; 24|PpEVrn ;; 25|rvd1Ld ;; 26|4BL762 ;; 27|GHkdrS ;; 28|iNTjMI ;; 29|VTsp6x ;; "
                + "30|xZ1vrn ;; 31|95ABMc ;; 32|Mb9H72 ;; 33|ohINrS ;; 34|0nhTMH ;; 35|DtQZ7x ;; 36|fzp5sn ;; "
                + "37|RFYbMc ;; 38|uLxh72 ;; 39|6S6nsS"},
            {"SELECT RANDSTR(4, 1) FROM TABLE(GENERATOR(ROWCOUNT => 3))",
                "CaFH ;; CaFH ;; CaFH"},
            {"SELECT RANDSTR(0, 1)",
                ""},
            {"SELECT RANDSTR(3, 1.4)",
                "CaF"},
            {"SELECT RANDSTR(3, '5')",
                "5yV"},
            {"SELECT RANDSTR(3, 5)",
                "5yV"},
            {"SELECT RANDSTR(64, 1)",
                "CaFHJdoX3upWliCCdAPXXgytQuXzQpFO4laQEFdmiE1NDOywjwHoBqSNTCzTW66y"},
            {"SELECT RANDSTR(2, s) FROM (SELECT 7 s UNION ALL SELECT 8) ORDER BY 1",
                "WR ;; kL"},
        });
    }

    /** A seeded RANDOM's value for each row, read as its two 32-bit halves. */
    @Test
    public void aSeededRandomIsTheTwistersOutputForItsRow() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SELECT BITSHIFTRIGHT(RANDOM(0), 32) h, BITAND(RANDOM(0), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT => "
                + "4))",
                "686307269|3410091070 ;; -33736068|1044445579 ;; 169947671|4029868217 ;; -1728747261|3471047918"},
            {"SELECT BITSHIFTRIGHT(RANDOM(1), 32) h, BITAND(RANDOM(1), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT => "
                + "4))",
                "574995807|3144183656 ;; 585863760|588839502 ;; 1937953255|2061911450 ;; 90298373|2033565838"},
            {"SELECT BITSHIFTRIGHT(RANDOM(2), 32) h, BITAND(RANDOM(2), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT => "
                + "4))",
                "-414017555|159484492 ;; -643230883|1526411097 ;; -928484032|3598004773 ;; -320760613|1826369075"},
            {"SELECT BITSHIFTRIGHT(RANDOM(3), 32) h, BITAND(RANDOM(3), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT => "
                + "4))",
                "-1895085645|2356345771 ;; 840798924|1895602663 ;; -1759900338|452285907 ;; 1487643137|1784790677"},
            {"SELECT BITSHIFTRIGHT(RANDOM(-1), 32) h, BITAND(RANDOM(-1), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT "
                + "=> 4))",
                "111299194|603703396 ;; -1211559674|1658668356 ;; 165131879|422926743 ;; -2087223200|2575070438"},
            {"SELECT BITSHIFTRIGHT(RANDOM(42), 32) h, BITAND(RANDOM(42), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT "
                + "=> 4))",
                "-1051598979|1860559574 ;; -1550348359|3106210472 ;; -1064528257|241072906 ;; 585286719|2317013838"},
            {"SELECT BITSHIFTRIGHT(RANDOM(7), 32) h, BITAND(RANDOM(7), 4294967295) l FROM TABLE(GENERATOR(ROWCOUNT => "
                + "4))",
                "-1054907087|3949386151 ;; -217749676|2050277730 ;; 504290497|543778766 ;; -464229372|3657399542"},
            {"SELECT BITSHIFTRIGHT(r, 32) h, BITAND(r, 4294967295) l FROM (SELECT RANDOM(1) r)",
                "574995807|3144183656"},
            {"SELECT h, l FROM (SELECT BITSHIFTRIGHT(RANDOM(1), 32) h, BITAND(RANDOM(1), 4294967295) l FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 3)) UNION ALL SELECT 0, 0)",
                "574995807|3144183656 ;; 585863760|588839502 ;; 1937953255|2061911450 ;; 0|0"},
        });
    }

    /** UNIFORM and ZIPF over whole and decimal bounds, a constant generator per row, and RANDOM as a generator. */
    @Test
    public void theDistributionsDrawTheAccountsWholeNumbers() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SELECT RANDOM(-1)",
                "478026398904862820"},
            {"SELECT UNIFORM(0, 61, 1), UNIFORM(0, 61, 0), UNIFORM(0, 61, 2)",
                "38|0|14"},
            {"SELECT UNIFORM(1, 10, 1), UNIFORM(1, 10, 0), UNIFORM(0, 1000000000, 1), UNIFORM(0, 1000000000, 0)",
                "7|1|618033989|0"},
            {"SELECT ZIPF(1, 10, 1), ZIPF(1, 10, 0), ZIPF(1, 10, 2)",
                "3|1|1"},
            {"SELECT UNIFORM(0, 61, 1) FROM TABLE(GENERATOR(ROWCOUNT => 3))",
                "38 ;; 38 ;; 38"},
            {"SELECT SEQ4() s, UNIFORM(0, 61, SEQ4()) u FROM TABLE(GENERATOR(ROWCOUNT => 10)) ORDER BY s",
                "0|0 ;; 1|38 ;; 2|14 ;; 3|52 ;; 4|29 ;; 5|5 ;; 6|43 ;; 7|20 ;; 8|58 ;; 9|34"},
            {"SELECT SEQ4() s, RANDOM(SEQ4()) r FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY s",
                "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found 'SEQ4()'"},
            {"SELECT RANDSTR(5, RANDOM(1))",
                "JsZGh"},
            {"SELECT UNIFORM(-5, 5, 1), UNIFORM(-5, 5, 0), UNIFORM(1.5, 2.5, 1)",
                "1|-5|2.1"},
        });
    }

    /** ZIPF and UNIFORM over forty consecutive seeds, and ZIPF over a million ranks. */
    @Test
    public void theDistributionsAcrossConsecutiveSeeds() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SELECT SEQ4() s, ZIPF(1, 10, SEQ4()), ZIPF(1.5, 100, SEQ4()), ZIPF(2, 1000, SEQ4()), ZIPF(0, 10, "
                + "SEQ4()), ZIPF(0.5, 50, SEQ4()) FROM TABLE(GENERATOR(ROWCOUNT => 40)) ORDER BY s",
                "0|1|1|1|1|1 ;; 1|3|3|2|7|22 ;; 2|1|1|1|3|5 ;; 3|7|13|4|9|38 ;; 4|2|2|1|5|14 ;; 5|1|1|1|1|2 ;; "
                + "6|4|5|2|8|28 ;; 7|1|1|1|4|8 ;; 8|9|36|11|10|46 ;; 9|3|3|1|6|19 ;; 10|1|1|1|2|4 ;; 11|6|8|3|8|34 ;; "
                + "12|2|2|1|5|11 ;; 13|1|1|1|1|1 ;; 14|4|4|2|7|24 ;; 15|1|1|1|3|6 ;; 16|8|18|5|9|41 ;; 17|2|2|1|6|16 "
                + ";; 18|1|1|1|2|2 ;; 19|5|6|2|8|30 ;; 20|2|1|1|4|9 ;; 21|10|64|28|10|49 ;; 22|3|3|1|6|21 ;; "
                + "23|1|1|1|3|4 ;; 24|6|11|4|9|36 ;; 25|2|2|1|5|13 ;; 26|1|1|1|1|1 ;; 27|4|4|2|7|26 ;; 28|1|1|1|4|7 ;; "
                + "29|8|27|8|10|44 ;; 30|3|2|1|6|17 ;; 31|1|1|1|2|3 ;; 32|5|7|3|8|32 ;; 33|2|1|1|4|11 ;; 34|1|1|1|1|1 "
                + ";; 35|4|3|2|7|23 ;; 36|1|1|1|3|5 ;; 37|7|15|5|9|39 ;; 38|2|2|1|5|15 ;; 39|1|1|1|2|2"},
            {"SELECT SEQ4() s, UNIFORM(0, 9, SEQ4()), UNIFORM(-1000000, 1000000, SEQ4()), UNIFORM(0, 999999999999999, "
                + "SEQ4()), UNIFORM(0, 1, SEQ4()) FROM TABLE(GENERATOR(ROWCOUNT => 40)) ORDER BY s",
                "0|0|-1000000|0|0 ;; 1|6|236068|618058918970139|1 ;; 2|2|-527864|236077499996576|0 ;; "
                + "3|8|708204|854136418966716|1 ;; 4|4|-55728|472154999993152|0 ;; 5|0|-819661|90173581019588|0 ;; "
                + "6|7|416408|708232499989728|1 ;; 7|3|-347524|326251081016165|0 ;; 8|9|888544|944309999986304|1 ;; "
                + "9|5|124612|562328581012741|1 ;; 10|1|-639321|180347162039177|0 ;; 11|7|596748|798406081009317|1 ;; "
                + "12|4|-167184|416424662035754|0 ;; 13|0|-931117|34443243062190|0 ;; 14|6|304952|652502162032330|1 ;; "
                + "15|2|-458981|270520743058766|0 ;; 16|8|777088|888579662028906|1 ;; 17|5|13156|506598243055343|1 ;; "
                + "18|1|-750777|124616824081779|0 ;; 19|7|485292|742675743051919|1 ;; 20|3|-278641|360694324078355|0 "
                + ";; 21|9|957428|978753243048495|1 ;; 22|5|193496|596771824074931|1 ;; 23|2|-570437|214790405101368|0 "
                + ";; 24|8|665632|832849324071508|1 ;; 25|4|-98301|450867905097944|0 ;; 26|0|-862233|68886486124381|0 "
                + ";; 27|6|373836|686945405094520|1 ;; 28|3|-390097|304963986120957|0 ;; 29|9|845972|923022905091097|1 "
                + ";; 30|5|82039|541041486117533|1 ;; 31|1|-681893|159060067143970|0 ;; 32|7|554176|777118986114109|1 "
                + ";; 33|3|-209757|395137567140546|0 ;; 34|0|-973689|13156148166982|0 ;; 35|6|262379|631215067137122|1 "
                + ";; 36|2|-501553|249233648163559|0 ;; 37|8|734516|867292567133698|1 ;; 38|4|-29417|485311148160135|0 "
                + ";; 39|1|-793349|103329729186571|0"},
            {"SELECT ZIPF(1, 1, 5), ZIPF(3, 5, 1), ZIPF(1, 1000000, 1), ZIPF(1, 1000000, 3)",
                "1|1|4097|122472"},
        });
    }

    /**
     * FLOAT values of UNIFORM and NORMAL compared to the last bit, a RANDOM generator, and decimal and text generators.
     */
    @Test
    public void floatDrawsAreTheAccountsToTheLastBit() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SELECT UNIFORM(0::FLOAT, 1::FLOAT, 1) = 0.6180339887498949::FLOAT, NORMAL(0, 1, 1) = "
                + "-1.143416214223267::FLOAT, NORMAL(0, 1, 0) = 0.2249704799650637::FLOAT, UNIFORM(0::FLOAT, 1::FLOAT, "
                + "2) = 0.2360679774997897::FLOAT",
                "true|true|true|true"},
            {"SELECT NORMAL(0, 1, 4) = -2.3867665951412396::FLOAT, NORMAL(0, 1, 29) = -2.434263147042672::FLOAT",
                "false|true"},
            {"SELECT RANDSTR(8, RANDOM(42)), UNIFORM(1, 100, RANDOM(42)) FROM TABLE(GENERATOR(ROWCOUNT => 3))",
                "XZEU1Koo|96 ;; 3RucW4Vl|7 ;; 45P1KR9G|8"},
            {"SELECT RANDSTR(3, 'x')",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT UNIFORM(1, 10, 'x')",
                "REFUSED Numeric value 'x' is not recognized"},
        });
    }
}
