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

import dev.frostlake.executor.SqlCompilationError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The packages a Java routine's PACKAGES may name: the account's list of them, a package by its group and artifact
 * at one of its versions or at {@code latest}, in any case and around any spaces ({@code java-packages.tsv}, the
 * account's {@code INFORMATION_SCHEMA.PACKAGES} for Java). Anything else is refused as the routine is created, ahead
 * of its handler, the first one named as written: {@code Package 'com.snowflake:snowpark' is not supported.} for a
 * package without a version, an unknown version, an unknown package, or a name of more or fewer parts
 * (live-verified).
 */
public final class JavaPackages {

    /** The versions of each package, by its group and artifact, lower-cased. */
    private static final Map<String, Set<String>> VERSIONS = load();

    private JavaPackages() {
    }

    /**
     * Refuse the first package a Java routine names that the account does not offer.
     *
     * @param packages the packages as written, or null when the routine names none
     */
    public static void requireSupported(final List<String> packages) {
        if (packages == null) {
            return;
        }
        for (final String written : packages) {
            if (written != null && !isSupported(written)) {
                throw new RuntimeException(SqlCompilationError.inline("Package '" + written + "' is not supported."));
            }
        }
    }

    private static boolean isSupported(final String written) {
        final String[] parts = written.trim().toLowerCase(Locale.ROOT).split(":", -1);
        if (parts.length != 3) {
            return false;
        }
        final Set<String> versions = VERSIONS.get(parts[0] + ":" + parts[1]);
        return versions != null && ("latest".equals(parts[2]) || versions.contains(parts[2]));
    }

    private static Map<String, Set<String>> load() {
        final Map<String, Set<String>> versions = new HashMap<>();
        try (InputStream in = JavaPackages.class.getResourceAsStream("/java-packages.tsv")) {
            if (in == null) {
                throw new IllegalStateException("missing Java package list");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    final String[] fields = line.split("\t", -1);
                    if (fields.length == 2) {
                        final String name = fields[0].toLowerCase(Locale.ROOT);
                        Set<String> known = versions.get(name);
                        if (known == null) {
                            known = new HashSet<>();
                            versions.put(name, known);
                        }
                        known.add(fields[1].toLowerCase(Locale.ROOT));
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load the Java package list", e);
        }
        return versions;
    }
}
