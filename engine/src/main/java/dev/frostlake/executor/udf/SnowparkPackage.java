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

package dev.frostlake.executor.udf;

import java.util.List;
import java.util.Locale;

/**
 * Snowpark as a Java routine's PACKAGES name it. An account puts the Snowpark library on an inline Java body's path
 * only when the routine names it, at any version and in any case — {@code 'com.snowflake:snowpark:latest'},
 * {@code 'COM.SNOWFLAKE:SNOWPARK:1.14.0'}; without it the body compiles against a machine that has neither the
 * {@code com.snowflake.snowpark_java} classes nor the {@code com.snowflake.snowpark} ones, where of their packages
 * it finds {@code com.snowflake.snowpark_java.types.SnowflakeFile} (live-verified).
 */
public final class SnowparkPackage {

    private static final String SPEC = "com.snowflake:snowpark:";

    private SnowparkPackage() {
    }

    /**
     * @param packages the packages a routine names, or null
     * @return whether one of them is Snowpark
     */
    public static boolean isNamed(final List<String> packages) {
        if (packages == null) {
            return false;
        }
        for (final String named : packages) {
            if (named != null && named.trim().toLowerCase(Locale.ROOT).startsWith(SPEC)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param packageName a Java package
     * @return whether it is one of Snowpark's
     */
    static boolean holds(final String packageName) {
        return isOrUnder(packageName, "com.snowflake.snowpark")
            || isOrUnder(packageName, "com.snowflake.snowpark_java");
    }

    private static boolean isOrUnder(final String packageName, final String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }
}
