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

package dev.frostlake.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The columns of INFORMATION_SCHEMA's own views as a real account describes them: each view's columns in order,
 * with the type, the nullability and the description DESCRIBE answers. The descriptions are the account's fixed
 * text, so they are kept as a list rather than derived (live-verified).
 */
public final class InformationSchemaColumns {

    private static final String RESOURCE = "/dev/frostlake/metadata/information-schema-columns.tsv";

    private static volatile Map<String, List<String[]>> columns;

    private InformationSchemaColumns() {
    }

    /**
     * The described columns of one view.
     *
     * @param viewName the view's name, as INFORMATION_SCHEMA spells it
     * @return one entry per column, in order, holding the column's name, its type, {@code Y} or {@code N} for
     *         whether it takes NULL, and its description; empty for a name the list does not hold
     */
    public static List<String[]> of(final String viewName) {
        Map<String, List<String[]>> loaded = columns;
        if (loaded == null) {
            loaded = load();
            columns = loaded;
        }
        final List<String[]> described = loaded.get(viewName);
        return described == null ? new ArrayList<String[]>() : described;
    }

    private static Map<String, List<String[]>> load() {
        final Map<String, List<String[]>> byView = new HashMap<String, List<String[]>>();
        try (InputStream in = InformationSchemaColumns.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing column list: information schema");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        final String[] cells = line.split("\t", -1);
                        List<String[]> described = byView.get(cells[0]);
                        if (described == null) {
                            described = new ArrayList<String[]>();
                            byView.put(cells[0], described);
                        }
                        described.add(new String[] {cells[1], cells[2], cells[3], cells[4]});
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load column list: information schema", e);
        }
        return byView;
    }
}
