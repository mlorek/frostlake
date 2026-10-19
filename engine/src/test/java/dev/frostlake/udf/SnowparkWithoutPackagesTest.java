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
 * A Java routine's inline body compiles against Snowpark only when its PACKAGES name it, at any version and in
 * any case: without it the body is refused with the compiler's report - a Snowpark type is a symbol the
 * compiler cannot find, the Java API's and the Scala API's own packages do not exist - and only the types
 * package's SnowflakeFile is there (live-verified).
 */
public class SnowparkWithoutPackagesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyWithoutSnowpark() {
        try {
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | "
                    + "/InlineCode.java:2: error: cannot find symbol | class H { public static String f(String x) { "
                    + "return new Variant(x).asString(); } } |                                                       "
                    + "  ^ |   symbol:   class Variant |   location: class H | 2 errors |  in function FSV with "
                    + "handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String "
                    + "x) { return new Variant(x).asString(); } }$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FSV.",
                "SELECT fsv('a')");
            assertAnswer(
                "Function FSV successfully created.",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String x) { "
                    + "return new Variant(x).asString(); } }$$");
            assertAnswer(
                "a",
                "SELECT fsv('a')");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | class H { "
                    + "public static String f(String x) { return new "
                    + "com.snowflake.snowpark_java.types.Variant(x).asString(); } } |                                "
                    + "                                                          ^ |   symbol:   class Variant |   "
                    + "location: package com.snowflake.snowpark_java.types | 1 error |  in function FSV with handler "
                    + "H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { public static String f(String x) { return new "
                    + "com.snowflake.snowpark_java.types.Variant(x).asString(); } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:2: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | "
                    + "/InlineCode.java:3: error: cannot find symbol | class H { public static String f(String x) { "
                    + "return new Variant(x).asString(); } } |                                                       "
                    + "  ^ |   symbol:   class Variant |   location: class H | 2 errors |  in function FSV with "
                    + "handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$\nimport com.snowflake.snowpark_java.types.Variant;\nclass H { public static String "
                    + "f(String x) { return new Variant(x).asString(); } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FSV with handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: package "
                    + "com.snowflake.snowpark_java does not exist | import com.snowflake.snowpark_java.*; | ^ | 1 "
                    + "error |  in function FSV with handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.*;\nclass H { public static String f(String x) { return "
                    + "x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | class H { "
                    + "public static String f(com.snowflake.snowpark_java.types.Variant x) { return x.asString(); } "
                    + "} |                                                                   ^ |   symbol:   class "
                    + "Variant |   location: package com.snowflake.snowpark_java.types | 1 error |  in function FSV "
                    + "with handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARIANT) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$class H { public static String f(com.snowflake.snowpark_java.types.Variant x) { return "
                    + "x.asString(); } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | "
                    + "/InlineCode.java:2: error: cannot find symbol | class H { public static String f(String x) { "
                    + "return new Variant(x).asString(); } } |                                                       "
                    + "  ^ |   symbol:   class Variant |   location: class H | 2 errors |  in function FSV with "
                    + "handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '17' "
                    + "HANDLER = 'H.f' AS $$import com.snowflake.snowpark_java.types.Variant;\nclass H { public "
                    + "static String f(String x) { return new Variant(x).asString(); } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | "
                    + "/InlineCode.java:2: error: cannot find symbol | class H { public static String f(String x) { "
                    + "return new Variant(x).asString(); } } |                                                       "
                    + "  ^ |   symbol:   class Variant |   location: class H | 2 errors |  in function FSV with "
                    + "handler H.f",
                "CREATE OR REPLACE FUNCTION fsv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('com.snowflake:telemetry:latest') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String x) { "
                    + "return new Variant(x).asString(); } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: package "
                    + "com.snowflake.snowpark_java does not exist | class H { public static String "
                    + "run(com.snowflake.snowpark_java.Session s) { return \"x\"; } } |                              "
                    + "                                 ^ | 1 error |  in function PNP with handler H.run",
                "CREATE OR REPLACE PROCEDURE pnp() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER = "
                    + "'H.run' AS $$class H { public static String run(com.snowflake.snowpark_java.Session s) { "
                    + "return \"x\"; } }$$");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function PNP.",
                "CALL pnp()");
            assertAnswer(
                "Function PNP successfully created.",
                "CREATE OR REPLACE PROCEDURE pnp() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES "
                    + "= ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$class H { public static String "
                    + "run(com.snowflake.snowpark_java.Session s) { return \"x\"; } }$$");
            assertAnswer(
                "x",
                "CALL pnp()");
            assertAnswer(
                "REFUSED Failed to find a public method named \"run\" with 1 arguments in function PNP with "
                    + "handler H.run",
                "CREATE OR REPLACE PROCEDURE pnp() RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER = "
                    + "'H.run' AS $$class H { public static String run() { return \"x\"; } }$$");
            assertAnswer(
                "x",
                "CALL pnp()");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fsv(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS fsv(VARIANT)");
            answer(
                "DROP PROCEDURE IF EXISTS pnp()");
            answer(
                "DROP FUNCTION IF EXISTS fnoclass(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pnoclass()");
            answer(
                "DROP FUNCTION IF EXISTS fprot(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fnpe(NUMBER)");
        }
    }

    @Test
    public void snowparksPackagesAndTheTypesPackage() {
        try {
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.ArrayType; |                                         ^ |   "
                    + "symbol:   class ArrayType |   location: package com.snowflake.snowpark_java.types | 1 error | "
                    + " in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.ArrayType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.BinaryType; |                                         ^ |   "
                    + "symbol:   class BinaryType |   location: package com.snowflake.snowpark_java.types | 1 error "
                    + "|  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.BinaryType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.BooleanType; |                                         ^ |  "
                    + " symbol:   class BooleanType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.BooleanType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.ByteType; |                                         ^ |   "
                    + "symbol:   class ByteType |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.ByteType;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.DataType; |                                         ^ |   "
                    + "symbol:   class DataType |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.DataType;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.DataTypes; |                                         ^ |   "
                    + "symbol:   class DataTypes |   location: package com.snowflake.snowpark_java.types | 1 error | "
                    + " in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.DataTypes;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.DateType; |                                         ^ |   "
                    + "symbol:   class DateType |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.DateType;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.DecimalType; |                                         ^ |  "
                    + " symbol:   class DecimalType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.DecimalType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.DoubleType; |                                         ^ |   "
                    + "symbol:   class DoubleType |   location: package com.snowflake.snowpark_java.types | 1 error "
                    + "|  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.DoubleType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.FloatType; |                                         ^ |   "
                    + "symbol:   class FloatType |   location: package com.snowflake.snowpark_java.types | 1 error | "
                    + " in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.FloatType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.GeographyType; |                                         ^ "
                    + "|   symbol:   class GeographyType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.GeographyType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.GeometryType; |                                         ^ | "
                    + "  symbol:   class GeometryType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.GeometryType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.IntegerType; |                                         ^ |  "
                    + " symbol:   class IntegerType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.IntegerType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.LongType; |                                         ^ |   "
                    + "symbol:   class LongType |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.LongType;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.MapType; |                                         ^ |   "
                    + "symbol:   class MapType |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.MapType;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.ShortType; |                                         ^ |   "
                    + "symbol:   class ShortType |   location: package com.snowflake.snowpark_java.types | 1 error | "
                    + " in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.ShortType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.StringType; |                                         ^ |   "
                    + "symbol:   class StringType |   location: package com.snowflake.snowpark_java.types | 1 error "
                    + "|  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.StringType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.StructField; |                                         ^ |  "
                    + " symbol:   class StructField |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.StructField;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.StructType; |                                         ^ |   "
                    + "symbol:   class StructType |   location: package com.snowflake.snowpark_java.types | 1 error "
                    + "|  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.StructType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.TimestampType; |                                         ^ "
                    + "|   symbol:   class TimestampType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.TimestampType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.TimeType; |                                         ^ |   "
                    + "symbol:   class TimeType |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.TimeType;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.VariantType; |                                         ^ |  "
                    + " symbol:   class VariantType |   location: package com.snowflake.snowpark_java.types | 1 "
                    + "error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.VariantType;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Geography; |                                         ^ |   "
                    + "symbol:   class Geography |   location: package com.snowflake.snowpark_java.types | 1 error | "
                    + " in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.Geography;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Geometry; |                                         ^ |   "
                    + "symbol:   class Geometry |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.Geometry;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.SnowflakeFile;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.*;\nclass H { public static String f(String x) { "
                    + "return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: package com.snowflake.snowpark "
                    + "does not exist | import com.snowflake.snowpark.*; | ^ | 1 error |  in function FTV with "
                    + "handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark.*;\nclass H { public static String f(String x) { return x; } "
                    + "}$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: package com.snowflake.snowpark "
                    + "does not exist | import com.snowflake.snowpark.SnowparkClientException; |                     "
                    + "         ^ | 1 error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark.SnowparkClientException;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: package "
                    + "com.snowflake.snowpark_java does not exist | import com.snowflake.snowpark_java.Row; |        "
                    + "                           ^ | 1 error |  in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.Row;\nclass H { public static String f(String x) { "
                    + "return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark.SnowparkClientException;\nclass H { public static String f(String x) { "
                    + "return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark.*;\nclass H { public static String f(String x) { return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('COM.SNOWFLAKE:SNOWPARK:LATEST') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String x) { "
                    + "return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('com.snowflake:snowpark:1.14.0') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String x) { "
                    + "return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = "
                    + "('com.snowflake:snowpark:latest', 'com.snowflake:telemetry:latest') HANDLER = 'H.f' AS "
                    + "$$import com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String "
                    + "x) { return x; } }$$");
            assertAnswer(
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot find symbol | import "
                    + "com.snowflake.snowpark_java.types.Variant; |                                         ^ |   "
                    + "symbol:   class Variant |   location: package com.snowflake.snowpark_java.types | 1 error |  "
                    + "in function FTV with handler H.f",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = () HANDLER = "
                    + "'H.f' AS $$import com.snowflake.snowpark_java.types.Variant;\nclass H { public static String "
                    + "f(String x) { return x; } }$$");
            assertAnswer(
                "Function FTV successfully created.",
                "CREATE OR REPLACE FUNCTION ftv(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA PACKAGES = (' "
                    + "com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$import "
                    + "com.snowflake.snowpark_java.types.Variant;\nclass H { public static String f(String x) { "
                    + "return x; } }$$");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS ftv(VARCHAR)");
        }
    }
}
