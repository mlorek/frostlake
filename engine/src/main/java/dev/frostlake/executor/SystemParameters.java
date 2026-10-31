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
package dev.frostlake.executor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The parameters the system sets beneath the account, as a real account lists them at level SYSTEM:
 * ENABLE_USE_STABLE_PATH reads true there, its default false (live-verified; the list is in
 * {@code dev/frostlake/parameters/system.tsv}). A value the account, a user, a session or an object sets is in force
 * over it.
 */
public final class SystemParameters {

    private static final Map<String, String> VALUES = load();

    private SystemParameters() {
    }

    /**
     * The value the system sets for a parameter.
     *
     * @param name the parameter's name, in any case
     * @return the value, or null when the system leaves the parameter to the account
     */
    public static String value(final String name) {
        return VALUES.get(name.toUpperCase(Locale.ROOT));
    }

    private static Map<String, String> load() {
        final Map<String, String> values = new HashMap<String, String>();
        try (InputStream in = SystemParameters.class.getResourceAsStream("/dev/frostlake/parameters/system.tsv")) {
            if (in == null) {
                throw new IllegalStateException("missing parameter list: system");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        final String[] cells = line.split("\t", -1);
                        values.put(cells[0], cells.length > 1 ? cells[1] : "");
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException failed) {
            throw new IllegalStateException("cannot load parameter list: system", failed);
        }
        return values;
    }
}
