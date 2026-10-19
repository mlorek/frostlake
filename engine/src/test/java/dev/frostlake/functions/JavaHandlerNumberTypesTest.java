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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Java handler takes a SQL number as whichever Java number its parameter declares: a NUMBER as an int, a long
 * or a BigDecimal, a FLOAT as a double (live-verified).
 */
public class JavaHandlerNumberTypesTest extends BaseDatabaseTest {

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private void function(final String name, final String sqlType, final String javaType) {
        engine.execute("CREATE FUNCTION " + name + "(x " + sqlType + ") RETURNS " + sqlType + " LANGUAGE JAVA"
            + " HANDLER = 'H.h' AS $$ class H { public static " + javaType + " h(" + javaType + " x) {"
            + " return x; } } $$");
    }

    @Test
    public void aNumberReachesALongAnIntOrABigDecimal() {
        function("jn_long", "NUMBER", "long");
        function("jn_int", "NUMBER", "int");
        function("jn_big", "NUMBER(10,2)", "java.math.BigDecimal");
        assertEquals("5", first("SELECT jn_long(5)"));
        assertEquals("5000000000", first("SELECT jn_long(5000000000)"), "past the range of an int");
        assertEquals("7", first("SELECT jn_int(7)"));
        assertEquals("1.25", first("SELECT jn_big(1.25)"));
        engine.execute("CREATE TABLE jn_t (a NUMBER)");
        engine.execute("INSERT INTO jn_t VALUES (1), (2)");
        assertEquals("3", first("SELECT SUM(jn_long(a)) FROM jn_t"));
    }

    @Test
    public void aFloatReachesADouble() {
        function("jn_double", "FLOAT", "double");
        assertEquals("2.5", first("SELECT jn_double(2.5::FLOAT)"));
    }
}
