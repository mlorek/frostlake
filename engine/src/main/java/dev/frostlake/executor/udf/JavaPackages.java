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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The packages a Java or a Scala routine's PACKAGES may name: the account's list of them for the language, a package
 * by its group and artifact at one of its versions or at {@code latest}, in any case and around any spaces
 * ({@code java-packages.tsv} and {@code scala-packages.tsv}, the account's {@code INFORMATION_SCHEMA.PACKAGES} for each
 * language). Anything else is refused as the routine is created, ahead of its handler, the first one named as written:
 * {@code Package 'com.snowflake:snowpark' is not supported.} for a package without a version, an unknown version, an
 * unknown package, or a name of more or fewer parts (live-verified).
 */
public final class JavaPackages {

    /** The Java packages the account lists, each a name and a version as written, in the list's order. */
    private static final List<String[]> JAVA_LISTED = load("/java-packages.tsv");

    /** The Scala packages the account lists, likewise. */
    private static final List<String[]> SCALA_LISTED = load("/scala-packages.tsv");

    /** The versions of each Java package, by its group and artifact, lower-cased. */
    private static final Map<String, Set<String>> VERSIONS = versions(JAVA_LISTED);

    /** The versions of each Scala package, by its group and artifact, lower-cased. */
    private static final Map<String, Set<String>> SCALA_VERSIONS = versions(SCALA_LISTED);

    private JavaPackages() {
    }

    /**
     * Refuse the first package a Java routine names that the account does not offer.
     *
     * @param packages the packages as written, or null when the routine names none
     */
    public static void requireSupported(final List<String> packages) {
        requireListed(packages, VERSIONS);
    }

    /**
     * Refuse the first package a Scala routine names that the account does not offer.
     *
     * @param packages the packages as written, or null when the routine names none
     */
    public static void requireSupportedForScala(final List<String> packages) {
        requireListed(packages, SCALA_VERSIONS);
    }

    /**
     * The packages the account lists for Java or for Scala, as its {@code INFORMATION_SCHEMA.PACKAGES} holds them.
     *
     * @param scala whether the Scala list is asked for, else the Java one
     * @return each package's name and version, in the list's order
     */
    public static List<String[]> listed(final boolean scala) {
        final List<String[]> rows = new ArrayList<>();
        for (final String[] row : scala ? SCALA_LISTED : JAVA_LISTED) {
            rows.add(row.clone());
        }
        return rows;
    }

    private static void requireListed(final List<String> packages, final Map<String, Set<String>> offered) {
        if (packages == null) {
            return;
        }
        for (final String written : packages) {
            if (written != null && !isSupported(written, offered)) {
                throw new RuntimeException(SqlCompilationError.inline("Package '" + written + "' is not supported."));
            }
        }
    }

    private static boolean isSupported(final String written, final Map<String, Set<String>> offered) {
        final String[] parts = written.trim().toLowerCase(Locale.ROOT).split(":", -1);
        if (parts.length != 3) {
            return false;
        }
        final Set<String> versions = offered.get(parts[0] + ":" + parts[1]);
        return versions != null && ("latest".equals(parts[2]) || versions.contains(parts[2]));
    }

    private static Map<String, Set<String>> versions(final List<String[]> listed) {
        final Map<String, Set<String>> versions = new HashMap<>();
        for (final String[] row : listed) {
            final String name = row[0].toLowerCase(Locale.ROOT);
            Set<String> known = versions.get(name);
            if (known == null) {
                known = new HashSet<>();
                versions.put(name, known);
            }
            known.add(row[1].toLowerCase(Locale.ROOT));
        }
        return versions;
    }

    private static List<String[]> load(final String resource) {
        final List<String[]> rows = new ArrayList<>();
        try (InputStream in = JavaPackages.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing package list " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    final String[] fields = line.split("\t", -1);
                    if (fields.length == 2) {
                        rows.add(fields);
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load the package list " + resource, e);
        }
        return rows;
    }
}
