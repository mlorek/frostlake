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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Java handler's return and parameter types are judged against the routine's SQL types when CREATE compiles
 * the body: a Java type the account never converts to is refused by name, an integral type cannot carry a scaled
 * number, and any other pair the account does not map is refused naming Snowflake's storage type. Every cell is
 * live-verified.
 */
public class JavaHandlerTypeMappingTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** A zero-argument function whose handler returns {@code expression} as {@code javaType}. */
    private static String returning(final String name, final String sqlType, final String javaType,
                                    final String expression) {
        return "CREATE FUNCTION " + name + "() RETURNS " + sqlType + " LANGUAGE JAVA HANDLER='H.h' AS $$ class H { "
            + "public static " + javaType + " h() { return " + expression + "; } } $$";
    }

    /** A one-argument function of {@code sqlType} whose handler takes it as {@code javaType}. */
    private static String taking(final String name, final String sqlType, final String javaType) {
        return "CREATE FUNCTION " + name + "(x " + sqlType + ") RETURNS INT LANGUAGE JAVA HANDLER='H.h' AS $$ "
            + "class H { public static int h(" + javaType + " x) { return 1; } } $$";
    }

    private static String notForReturn(final String storage, final String java, final String name) {
        return "Snowflake type " + storage + "{nullable} is not supported for Java return type " + java
            + " in function " + name + " with handler H.h";
    }

    @Test
    public void aReturnTypeTheSqlTypeCannotBeEncodedAsIsRefused() {
        assertEquals(notForReturn("FIXED[SB16](10,2)", "double", "JP1"),
            refusalOf(returning("jp1", "DECIMAL(10,2)", "double", "99.99")));
        assertEquals(notForReturn("TEXT[LOB](134217728)", "int", "JP3"),
            refusalOf(returning("jp3", "VARCHAR", "int", "1")));
        assertEquals(notForReturn("REAL[DOUBLE](38,0)", "java.math.BigDecimal", "JA5"),
            refusalOf(returning("ja5", "FLOAT", "java.math.BigDecimal", "java.math.BigDecimal.ONE")));
        assertEquals(notForReturn("DATE[SB4](38,0)", "String", "JB4"),
            refusalOf(returning("jb4", "DATE", "String", "\"1\"")));
        assertEquals(notForReturn("BOOLEAN[SB1](38,0)", "Integer", "JB3"),
            refusalOf(returning("jb3", "BOOLEAN", "Integer", "Integer.valueOf(1)")));
        assertEquals(notForReturn("BINARY[LOB](67108864)", "String", "JB2"),
            refusalOf(returning("jb2", "BINARY", "String", "\"1\"")));
        assertEquals(notForReturn("VARIANT[LOB](38,0)", "java.util.Map<String, String>", "JC6"),
            refusalOf(returning("jc6", "VARIANT", "java.util.Map<String, String>", "null")));
        assertEquals(notForReturn("ARRAY[LOB](38,0)", "java.sql.Timestamp", "JC8"),
            refusalOf(returning("jc8", "ARRAY", "java.sql.Timestamp", "null")));
        assertEquals(notForReturn("TEXT[LOB](10)", "byte[]", "JB1"),
            refusalOf(returning("jb1", "VARCHAR(10)", "byte[]", "null")));
    }

    @Test
    public void theStorageTypeCarriesItsWidthAndPrecision() {
        assertEquals(notForReturn("TIMESTAMP_NTZ[SB16](0,9)", "String", "JT1"),
            refusalOf(returning("jt1", "TIMESTAMP(9)", "String", "\"1\"")));
        assertEquals(notForReturn("TIMESTAMP_NTZ[SB8](0,6)", "String", "JT2"),
            refusalOf(returning("jt2", "TIMESTAMP_NTZ(6)", "String", "\"1\"")));
        assertEquals(notForReturn("TIMESTAMP_NTZ[SB8]", "long", "JT3"),
            refusalOf(returning("jt3", "TIMESTAMP_NTZ(0)", "long", "1L")));
        assertEquals(notForReturn("TIMESTAMP_TZ[SB8](0,3)", "String", "JT4"),
            refusalOf(returning("jt4", "TIMESTAMP_TZ(3)", "String", "\"1\"")));
        assertEquals(notForReturn("TIME[SB8](0,9)", "double", "JT5"),
            refusalOf(returning("jt5", "TIME", "double", "1.5")));
        assertEquals(notForReturn("TIME[SB4](0,3)", "double", "JT6"),
            refusalOf(returning("jt6", "TIME(3)", "double", "1.5")));
        assertEquals(notForReturn("TIME[SB4]", "int", "JT7"),
            refusalOf(returning("jt7", "TIME(0)", "int", "1")));
        assertEquals(notForReturn("VARIANT[LOB](38,0)", "long", "JT8"),
            refusalOf(returning("jt8", "GEOGRAPHY", "long", "1L")));
        assertEquals(notForReturn("FIXED[SB16](5,0)", "Boolean", "JT9"),
            refusalOf(returning("jt9", "NUMBER(5,0)", "Boolean", "Boolean.TRUE")));
    }

    @Test
    public void anIntegralTypeCannotCarryAScale() {
        assertEquals("Cannot encode number with a non-zero scale as a long in function JP2 with handler H.h",
            refusalOf(returning("jp2", "NUMBER(10,2)", "long", "1L")));
        assertEquals("Cannot encode number with a non-zero scale as an int in function JS1 with handler H.h",
            refusalOf(returning("js1", "NUMBER(10,2)", "Integer", "Integer.valueOf(1)")));
        assertEquals("Cannot encode number with a non-zero scale as a BigInteger in function JS2 with handler H.h",
            refusalOf(returning("js2", "TIME", "java.math.BigInteger", "java.math.BigInteger.ONE")));
        assertEquals("Cannot encode number with a non-zero scale as a short in function JS3 with handler H.h",
            refusalOf(returning("js3", "TIMESTAMP_LTZ", "short", "(short) 1")));
        assertEquals("Cannot encode number with a non-zero scale as an Integer in function JS4 with handler H.h",
            refusalOf(taking("js4", "NUMBER(10,2)", "Integer")));
        assertEquals("Cannot encode number with a non-zero scale as a Long in function JS5 with handler H.h",
            refusalOf(taking("js5", "NUMBER(10,2)", "Long")));
    }

    @Test
    public void aTypeTheAccountNeverConvertsIsRefusedByName() {
        assertEquals("Unsupported return type: java.lang.Object in function JU1 with handler H.h",
            refusalOf(returning("ju1", "NUMBER(38,0)", "Object", "\"1\"")));
        assertEquals("Unsupported return type: java.util.List in function JU2 with handler H.h",
            refusalOf(returning("ju2", "NUMBER(10,2)", "java.util.List<String>", "null")));
        assertEquals("Unsupported return type: java.lang.Integer[] in function JU3 with handler H.h",
            refusalOf(returning("ju3", "NUMBER(10,2)", "Integer[]", "null")));
        assertEquals("Unsupported return type: java.time.LocalDate in function JU4 with handler H.h",
            refusalOf(returning("ju4", "DATE", "java.time.LocalDate", "null")));
        assertEquals("Unsupported return type: char in function JU5 with handler H.h",
            refusalOf(returning("ju5", "VARCHAR", "char", "'a'")));
        assertEquals("Unsupported return type: java.util.HashMap in function JU6 with handler H.h",
            refusalOf(returning("ju6", "OBJECT", "java.util.HashMap<String, String>", "null")));
        assertEquals("Unsupported argument type: java.lang.StringBuilder in function JU7 with handler H.h",
            refusalOf(taking("ju7", "VARCHAR", "StringBuilder")));
        assertEquals("Unsupported argument type: byte in function JU8 with handler H.h",
            refusalOf(taking("ju8", "BINARY", "byte")));
    }

    @Test
    public void anArgumentIsJudgedByItsIndex() {
        assertEquals("Snowflake type FIXED[SB16](10,2){nullable} is not supported as input to argument at index 0 "
            + "with Java type double in function JD1 with handler H.h",
            refusalOf(taking("jd1", "NUMBER(10,2)", "double")));
        assertEquals("Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument at index 1 "
            + "with Java type int in function JE32 with handler H.h",
            refusalOf("CREATE FUNCTION je32(x NUMBER(10,2), y VARCHAR) RETURNS INT LANGUAGE JAVA HANDLER='H.h' AS $$ "
                + "class H { public static int h(java.math.BigDecimal x, int y) { return 1; } } $$"));
        assertEquals("Snowflake type OBJECT[LOB](38,0){nullable} is not supported as input to argument at index 0 "
            + "with Java type String[] in function JD2 with handler H.h",
            refusalOf(taking("jd2", "OBJECT", "String[]")));
    }

    @Test
    public void theJudgementsComeInTheirOrder() {
        assertEquals("Unsupported argument type: java.lang.Object in function O1 with handler H.h",
            refusalOf("CREATE FUNCTION o1(x NUMBER(10,2), y NUMBER(10,2)) RETURNS INT LANGUAGE JAVA HANDLER='H.h' "
                + "AS $$ class H { public static int h(double x, Object y) { return 1; } } $$"));
        assertEquals("Unsupported argument type: java.lang.Object in function O4 with handler H.h",
            refusalOf("CREATE FUNCTION o4(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' AS $$ class H { "
                + "public static java.time.LocalDate h(Object x) { return null; } } $$"));
        assertEquals("Unsupported return type: java.lang.Object in function JH15 with handler H.h",
            refusalOf("CREATE FUNCTION jh15(x NUMBER(10,2), y NUMBER(10,2)) RETURNS VARCHAR LANGUAGE JAVA "
                + "HANDLER='H.h' AS $$ class H { public static Object h(double x, int y) { return 1; } } $$"));
        assertEquals("Cannot encode number with a non-zero scale as an Integer in function O3 with handler H.h",
            refusalOf("CREATE FUNCTION o3(x NUMBER(10,2)) RETURNS NUMBER(10,2) LANGUAGE JAVA HANDLER='H.h' AS $$ "
                + "class H { public static Integer h(Integer x) { return 1; } } $$"));
        assertEquals("Failed to find a public method named \"h\" with 1 arguments in function JE30 with handler H.h",
            refusalOf("CREATE FUNCTION je30(a INT) RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' AS $$ class H { "
                + "public static int h() { return 1; } } $$"));
        assertEquals("Cannot determine which implementation of handler \"h\" to invoke since there are multiple "
            + "definitions with 1 arguments in function JE41 with handler H.h",
            refusalOf("CREATE FUNCTION je41(x INT) RETURNS INT LANGUAGE JAVA HANDLER='H.h' AS $$ class H { "
                + "public static int h(int x) { return 1; } public static String h(String x) { return null; } } $$"));
    }

    @Test
    public void theHandlerIsJudgedBeforeTheSignaturesNamesAndAnExistingFunction() {
        assertEquals(notForReturn("TEXT[LOB](134217728)", "int", "JE27"),
            refusalOf("CREATE FUNCTION je27(a INT, a INT) RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' AS $$ class H { "
                + "public static int h(int a, int b) { return 1; } } $$"));
        assertEquals("Failed to find a public method named \"h\" with 2 arguments in function JE28 with handler H.h",
            refusalOf("CREATE FUNCTION je28(a INT, a INT) RETURNS INT LANGUAGE JAVA HANDLER='H.h' AS $$ class H { "
                + "public static int h(int a) { return 1; } } $$"));
        engine.execute(returning("je38", "INT", "int", "1"));
        assertEquals(notForReturn("TEXT[LOB](134217728)", "int", "JE38"),
            refusalOf("CREATE FUNCTION IF NOT EXISTS je38() RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' AS $$ "
                + "class H { public static int h() { return 1; } } $$"));
    }

    @Test
    public void thePairsTheAccountMapsAreCreated() {
        engine.execute(returning("ok1", "DECIMAL(10,2)", "java.math.BigDecimal", "new java.math.BigDecimal(\"99.99\")"));
        engine.execute(returning("ok2", "NUMBER(10,0)", "long", "1L"));
        engine.execute(returning("ok3", "TIMESTAMP_NTZ(6)", "java.sql.Timestamp", "new java.sql.Timestamp(0L)"));
        engine.execute(returning("ok4", "NUMBER(10,2)", "String", "\"1.5\""));
        engine.execute(returning("ok5", "OBJECT", "java.util.Map<String, String>", "null"));
        engine.execute(returning("ok6", "TIMESTAMP_TZ", "java.util.Map<String, String>", "null"));
        engine.execute(returning("ok7", "TIME(0)", "java.math.BigInteger", "java.math.BigInteger.ONE"));
        engine.execute(returning("ok8", "ARRAY", "String[]", "null"));
        engine.execute(taking("ok9", "DATE", "String"));
        engine.execute(taking("ok10", "BINARY", "java.util.Map<String, String>"));
        engine.execute(taking("ok11", "NUMBER(38,0)", "java.io.InputStream"));
        engine.execute(returning("ok12", "FLOAT", "Double", "Double.valueOf(1.5)"));
        assertEquals("99.99", String.valueOf(engine.executeQuery("SELECT TO_VARCHAR(ok1())").getRows().get(0)
            .getValue(0)));
    }

    /** A procedure of {@code signature} whose handler takes a Session and then {@code javaParameters}. */
    private static String procedureTaking(final String name, final String signature, final String javaParameters) {
        return "CREATE PROCEDURE " + name + signature + " RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='11' "
            + "PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ import com.snowflake.snowpark_java.*; "
            + "class H { public static int h(" + javaParameters + ") { return 1; } } $$";
    }

    @Test
    public void aProceduresArgumentsAreJudgedAfterItsSessionAndNumberedWithoutIt() {
        assertEquals("Cannot encode number with a non-zero scale as a long in function J1 with handler H.h",
            refusalOf(procedureTaking("j1", "(x NUMBER(10,2))", "Session s, long x")));
        assertEquals("Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument at index 0 "
            + "with Java type int in function J2 with handler H.h",
            refusalOf(procedureTaking("j2", "(x VARCHAR)", "Session s, int x")));
        assertEquals("Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument at index 1 "
            + "with Java type int in function J3 with handler H.h",
            refusalOf(procedureTaking("j3", "(x INT, y VARCHAR)", "Session s, int x, int y")));
        assertEquals("Unsupported argument type: java.lang.Object in function J4 with handler H.h",
            refusalOf(procedureTaking("j4", "(x INT)", "Session s, Object x")));
        assertEquals("Snowflake type BOOLEAN[SB1](38,0){nullable} is not supported as input to argument at index 0 "
            + "with Java type int in function J14 with handler H.h",
            refusalOf(procedureTaking("j14", "(x BOOLEAN)", "Session s, int x")));
        assertEquals("Cannot encode number with a non-zero scale as a Long in function J19 with handler H.h",
            refusalOf(procedureTaking("j19", "(x NUMBER(10,2))", "Session s, Long x")));
        assertEquals("Unsupported argument type: java.time.LocalDate in function J20 with handler H.h",
            refusalOf(procedureTaking("j20", "(x INT)", "Session s, java.time.LocalDate x")));
        engine.execute(procedureTaking("j8", "(x INT)", "Session s, Integer x"));
        engine.execute(procedureTaking("j12", "(x TIMESTAMP_NTZ)", "Session s, String x"));
        engine.execute(procedureTaking("j16", "(x VARIANT)", "Session s, String x"));
    }

    @Test
    public void aProceduresHandlerMustTakeASessionFirst() {
        assertEquals("Invalid first argument type for stored procs: int Expected com.snowflake.snowpark.Session or "
            + "com.snowflake.snowpark_java.Session in function J9 with handler H.h",
            refusalOf(procedureTaking("j9", "(x INT)", "int a, int x")));
        assertEquals("Invalid first argument type for stored procs: int Expected com.snowflake.snowpark.Session or "
            + "com.snowflake.snowpark_java.Session in function K1 with handler H.h",
            refusalOf(procedureTaking("k1", "(x INT)", "int a, Object x")));
        assertEquals("Invalid first argument type for stored procs: java.lang.String Expected "
            + "com.snowflake.snowpark.Session or com.snowflake.snowpark_java.Session in function K3 with handler H.h",
            refusalOf(procedureTaking("k3", "()", "String s")));
        assertEquals("Invalid first argument type for stored procs: int Expected com.snowflake.snowpark.Session or "
            + "com.snowflake.snowpark_java.Session in function K2 with handler H.h",
            refusalOf("CREATE PROCEDURE k2() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='11' "
                + "PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static Object h(int a) { return 1; } } $$"));
    }

    @Test
    public void aProceduresHandlerIsJudgedByItsReturnType() {
        assertEquals(notForReturn("FIXED[SB16](10,2)", "double", "JE34"),
            refusalOf("CREATE PROCEDURE je34() RETURNS NUMBER(10,2) LANGUAGE JAVA RUNTIME_VERSION='11' "
                + "PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static double h(Session s) { return 1.5; } } $$"));
        assertEquals(notForReturn("TEXT[LOB](134217728)", "int", "JE35"),
            refusalOf("CREATE PROCEDURE je35() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION='11' "
                + "PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ import com.snowflake.snowpark_java.*; "
                + "class H { public static int h(Session s) { return 1; } } $$"));
    }
}
