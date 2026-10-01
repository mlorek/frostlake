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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The parameters {@code SHOW PARAMETERS IN DATABASE} and {@code SHOW PARAMETERS IN SCHEMA} list: every container
 * parameter a real account lists for a database or a schema, with its account default, type and description
 * (resource {@code dev/frostlake/parameters/container.tsv}). A database lists the ones marked for databases, a
 * schema the ones marked for schemas.
 */
public final class ContainerParameterCatalog {

    private static final List<SessionParameterRow> DATABASE = new ArrayList<>();
    private static final List<SessionParameterRow> SCHEMA = new ArrayList<>();

    static {
        try (InputStream in = ContainerParameterCatalog.class.getResourceAsStream(
                "/dev/frostlake/parameters/container.tsv")) {
            if (in == null) {
                throw new IllegalStateException("missing parameter list: container");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        final String[] cells = line.split("\t", -1);
                        final SessionParameterRow row = new SessionParameterRow(cells[0], cells[1], cells[4], cells[2]);
                        if (!"S".equals(cells[3])) {
                            DATABASE.add(row);
                        }
                        if (!"D".equals(cells[3])) {
                            SCHEMA.add(row);
                        }
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load parameter list: container", e);
        }
    }

    /** Static tables only. */
    private ContainerParameterCatalog() {
    }

    /** The parameters a database lists, in name order. */
    public static List<SessionParameterRow> databaseRows() {
        return Collections.unmodifiableList(DATABASE);
    }

    /** The parameters a schema lists, in name order. */
    public static List<SessionParameterRow> schemaRows() {
        return Collections.unmodifiableList(SCHEMA);
    }

    /** Whether a database lists the parameter (an upper-case name). */
    public static boolean isDatabaseParameter(final String name) {
        return contains(DATABASE, name);
    }

    /** Whether a schema lists the parameter (an upper-case name). */
    public static boolean isSchemaParameter(final String name) {
        return contains(SCHEMA, name);
    }

    private static boolean contains(final List<SessionParameterRow> rows, final String name) {
        for (final SessionParameterRow row : rows) {
            if (row.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
