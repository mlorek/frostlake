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
 * The Snowpark package a Python procedure needs (live-verified). A procedure's handler is handed a Snowpark session,
 * which the account builds from {@code snowflake-snowpark-python}, so a procedure whose PACKAGES does not name it fails
 * on the missing module: "Python Interpreter Error:" and, on the next line, "ModuleNotFoundError: No module named
 * 'snowflake.snowpark' in function P with handler main". A procedure naming no package at all, an empty list
 * included, is refused as it is created - a table procedure and an anonymous one too - and one naming other packages
 * when it is called. The name is read in any case and with or without a version; a function needs no Snowpark.
 */
public final class PythonSnowparkPackage {

    private static final String NAME = "snowflake-snowpark-python";

    private PythonSnowparkPackage() {
    }

    /**
     * @param packages the packages a procedure names, or null
     * @return whether one of them is the Snowpark package
     */
    public static boolean isNamed(final List<String> packages) {
        if (packages == null) {
            return false;
        }
        for (final String named : packages) {
            if (named == null) {
                continue;
            }
            final String spec = named.trim().toLowerCase(Locale.ROOT);
            if (spec.startsWith(NAME)
                    && (spec.length() == NAME.length() || !isNameCharacter(spec.charAt(NAME.length())))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param packages the packages a procedure names, or null
     * @return whether it names none
     */
    public static boolean namesNone(final List<String> packages) {
        return packages == null || packages.isEmpty();
    }

    /**
     * The failure of a procedure without the Snowpark package, in the account's words.
     *
     * @param procedureName the procedure's name
     * @param handler       its handler
     * @return the refusal
     */
    public static RuntimeException missing(final String procedureName, final String handler) {
        return new RuntimeException("Python Interpreter Error:\nModuleNotFoundError: No module named "
            + "'snowflake.snowpark' in function " + procedureName + " with handler " + handler);
    }

    /** Whether a character continues a package name rather than starting its version. */
    private static boolean isNameCharacter(final char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
    }
}
