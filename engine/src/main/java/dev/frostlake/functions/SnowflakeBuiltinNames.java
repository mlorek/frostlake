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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The names of the functions a Snowflake account has built in — scalar, aggregate and window alike — as the
 * account lists them. The engine implements most of them; the list is what tells a function the engine lacks
 * from a name nothing declares, so a routine calling one is not refused at CREATE where the account creates it.
 */
public final class SnowflakeBuiltinNames {

    private static final String RESOURCE = "snowflake-builtin-functions.txt";

    private static final Set<String> NAMES = load();

    private SnowflakeBuiltinNames() {
    }

    /**
     * Whether the account has a built-in function of that name.
     *
     * @param name the function's name, in any case
     * @return whether the account builds it in
     */
    public static boolean contains(final String name) {
        return name != null && NAMES.contains(name.toUpperCase(Locale.ROOT));
    }

    private static Set<String> load() {
        final Set<String> names = new HashSet<String>();
        try (InputStream in = SnowflakeBuiltinNames.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return names;
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    names.add(line.trim());
                }
            }
        } catch (final IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return names;
    }
}
