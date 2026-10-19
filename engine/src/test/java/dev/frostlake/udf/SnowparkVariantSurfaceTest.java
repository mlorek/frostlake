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
 * The public constructors and methods of the {@code Variant} class a Java handler compiles against, listed by
 * reflection inside a handler: sixteen constructors, the readers, {@code equals}, {@code hashCode} and
 * {@code toString}, a {@code Serializable} class with no public field. A method that takes one of the
 * engine's own types is the engine's and is left out. Every answer is live-verified.
 */
public class SnowparkVariantSurfaceTest extends MeasuredCellsTestSupport {

    @Test
    public void theClassOffersWhatTheRealOneDoes() {
        assertAnswer(
            "Function VSURF successfully created.",
            "CREATE OR REPLACE FUNCTION vsurf(n NUMBER) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' PACKAGES = "
                + "('com.snowflake:snowpark:latest') AS $$ import com.snowflake.snowpark_java.types.Variant; class H "
                + "{ static String names(Class<?>[] types) { StringBuilder s = new StringBuilder(); for (Class<?> t "
                + ": types) { if (s.length() > 0) s.append(\",\"); s.append(t.getSimpleName()); } return "
                + "s.toString(); } static boolean own(Class<?>[] types) { for (Class<?> t : types) { if "
                + "(t.getName().startsWith(\"dev.\")) return false; } return true; } public static String f(int n) { "
                + "java.util.List<String> l = new java.util.ArrayList<>(); if (n == 1) { for "
                + "(java.lang.reflect.Constructor<?> c : Variant.class.getConstructors()) l.add(\"Variant(\" + "
                + "names(c.getParameterTypes()) + \")\"); } if (n == 2) { for (java.lang.reflect.Method m : "
                + "Variant.class.getDeclaredMethods()) if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && "
                + "own(m.getParameterTypes())) l.add(m.getReturnType().getSimpleName() + \" \" + m.getName() + \"(\" "
                + "+ names(m.getParameterTypes()) + \")\"); } if (n == 3) { "
                + "l.add(java.lang.reflect.Modifier.toString(Variant.class.getModifiers())); for (Class<?> i : "
                + "Variant.class.getInterfaces()) l.add(i.getName()); "
                + "l.add(Variant.class.getSuperclass().getName()); "
                + "l.add(String.valueOf(Variant.class.getFields().length)); } java.util.Collections.sort(l); return "
                + "String.join(\" ## \", l); } } $$");
        assertAnswer(
            "Variant(BigDecimal) ## Variant(BigInteger) ## Variant(Date) ## Variant(List) ## Variant(Object) ## "
                + "Variant(Object[]) ## Variant(String) ## Variant(Time) ## Variant(Timestamp) ## Variant(boolean) "
                + "## Variant(byte[]) ## Variant(double) ## Variant(float) ## Variant(int) ## Variant(long) ## "
                + "Variant(short)",
            "SELECT vsurf(1)");
        assertAnswer(
            "BigDecimal asBigDecimal() ## BigInteger asBigInteger() ## Date asDate() ## JsonNode asJsonNode() ## "
                + "List asList() ## Map asMap() ## String asJsonString() ## String asString() ## String toString() "
                + "## Time asTime() ## Timestamp asTimestamp() ## Variant[] asArray() ## boolean asBoolean() ## "
                + "boolean equals(Object) ## byte[] asBinary() ## double asDouble() ## float asFloat() ## int "
                + "asInt() ## int hashCode() ## long asLong() ## short asShort()",
            "SELECT vsurf(2)");
        assertAnswer(
            "0 ## java.io.Serializable ## java.lang.Object ## public",
            "SELECT vsurf(3)");
    }
}
