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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A Snowpark Variant built from a top-level array - of Variants, of numbers, of texts - reads afterwards as the
 * account's library reads it (live-verified): asString, asList, asArray, toString and the list's first element.
 */
public class SnowparkVariantArrayReadsTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariantOfATopLevelArrayReadsAfterwards() {
        engine.execute("CREATE OR REPLACE FUNCTION bv915(n NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') AS $$ import "
            + "com.snowflake.snowpark_java.types.Variant; class H { interface C { Object get() throws "
            + "Exception; } static String g(C c) { try { Object o = c.get(); return o == null ? \"NULL\" : "
            + "String.valueOf(o); } catch (Throwable e) { return \"EX:\" + e.getClass().getSimpleName(); } "
            + "} static Variant v(int n) { switch (n) { case 1: return new Variant((Object) new Object[] "
            + "{new Variant(1), new Variant(\"b\")}); case 2: return new Variant((Object) new Object[] {1, "
            + "2}); case 3: return new Variant((Object) new Object[] {\"x\", \"y\"}); default: return new "
            + "Variant((Object) new Object[] {new Object[] {new Variant(1)}}); } } public static String "
            + "f(int n) { final Variant x = v(n % 10); switch (n / 10) { case 0: return g(() -> "
            + "x.asString()); case 1: return g(() -> x.asList().toString()); case 2: return g(() -> "
            + "x.asArray().length); case 3: return g(() -> x.toString()); default: return g(() -> "
            + "x.asList().get(0).asString()); } } } $$");
        assertCells(new String[][] {
            {"SELECT bv915(1)", "[1,\"b\"]"},
            {"SELECT bv915(2)", "[1,2]"},
            {"SELECT bv915(3)", "[\"x\",\"y\"]"},
            {"SELECT bv915(11)", "[1, b]"},
            {"SELECT bv915(12)", "[1, 2]"},
            {"SELECT bv915(13)", "[x, y]"},
            {"SELECT bv915(21)", "2"},
            {"SELECT bv915(22)", "2"},
            {"SELECT bv915(23)", "2"},
            {"SELECT bv915(31)", "[1,\"b\"]"},
            {"SELECT bv915(32)", "[1,2]"},
            {"SELECT bv915(33)", "[\"x\",\"y\"]"},
            {"SELECT bv915(41)", "1"},
            {"SELECT bv915(42)", "1"},
            {"SELECT bv915(43)", "x"},
        });
    }
}
