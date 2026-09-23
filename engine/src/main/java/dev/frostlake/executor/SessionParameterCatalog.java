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
 * The session parameters SHOW PARAMETERS lists, with their defaults, descriptions and type words.
 *
 * <p>★ IT IS A TRANSCRIPTION, NOT A DESIGN. Every row was read off a real account — 160 of them — because
 * none of this is derivable: the default of WEEK_START is 0 and of LOCK_TIMEOUT is 43200, the type word
 * is one of BOOLEAN / STRING / NUMBER (never TEXT, which this engine used to invent), and the
 * descriptions are prose, some of it several lines long and some of it ungrammatical in ways only a
 * verbatim copy reproduces. Frostlake used to carry twelve hand-written rows, so a parameter it had not
 * heard of returned NO ROWS AT ALL — a tool asking for one got nothing rather than its default.
 *
 * <p>★ THE VALUE AND THE LEVEL ARE NOT IN HERE, deliberately. A row's value is its default until the
 * session sets it, and the level is blank until then and SESSION after — that rule is the executor's and
 * was measured separately. What the table holds is only what does NOT change with the session.
 *
 * <p>The account this was taken from is a plain one; an edition-gated parameter another account has
 * would simply be missing, so tests pin individual ROWS rather than the count.
 */
public final class SessionParameterCatalog {

    private static final String RESOURCE = "/session-parameters.tsv";

    private static final List<SessionParameterRow> ROWS = load(RESOURCE);

    /** The parameters a user carries that a session does not, in the same layout. */
    private static final String USER_ONLY_RESOURCE = "/user-parameters.tsv";

    private static final List<SessionParameterRow> USER_ONLY_ROWS = load(USER_ONLY_RESOURCE);

    /** The parameter names a real account lists for a USER, in its order. */
    private static final String USER_RESOURCE = "/dev/frostlake/parameters/user.txt";

    private static final List<String> USER_NAMES = loadNames(USER_RESOURCE);

    /** The parameter names a real account lists for a TASK, in its order. */
    private static final String TASK_RESOURCE = "/dev/frostlake/parameters/task.txt";

    private static final List<String> TASK_NAMES = loadNames(TASK_RESOURCE);

    private SessionParameterCatalog() {
    }

    /** Every known parameter, in the order live lists them (alphabetical by name). */
    public static List<SessionParameterRow> rows() {
        return ROWS;
    }

    /** The parameter names SHOW PARAMETERS IN USER lists, in the order live lists them. */
    public static List<String> userScopeNames() {
        return USER_NAMES;
    }

    /** The parameters SHOW PARAMETERS IN USER lists that a session does not carry. */
    public static List<SessionParameterRow> userOnlyRows() {
        return USER_ONLY_ROWS;
    }

    /** The parameter names SHOW PARAMETERS IN TASK lists, in the order live lists them. */
    public static List<String> taskScopeNames() {
        return TASK_NAMES;
    }

    /**
     * The session parameter of that name, or null when the account lists none.
     *
     * @param name the parameter name, any case
     * @return the parameter's row, or null
     */
    public static SessionParameterRow find(final String name) {
        for (final SessionParameterRow row : ROWS) {
            if (row.getName().equalsIgnoreCase(name)) {
                return row;
            }
        }
        return null;
    }

    private static List<String> loadNames(final String resource) {
        final List<String> names = new ArrayList<>();
        final InputStream stream = SessionParameterCatalog.class.getResourceAsStream(resource);
        if (stream == null) {
            return Collections.unmodifiableList(names);
        }
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (line != null) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty() && trimmed.charAt(0) != '#') {
                    names.add(trimmed);
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (final IOException unreadable) {
            return Collections.unmodifiableList(names);
        }
        return Collections.unmodifiableList(names);
    }

    private static List<SessionParameterRow> load(final String resource) {
        final List<SessionParameterRow> parsed = new ArrayList<>();
        final InputStream stream = SessionParameterCatalog.class.getResourceAsStream(resource);
        if (stream == null) {
            return Collections.unmodifiableList(parsed);
        }
        try {
            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (line != null) {
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    final String[] parts = line.split("\t", -1);
                    if (parts.length >= 4) {
                        parsed.add(new SessionParameterRow(parts[0], parts[1],
                            parts[2].replace("\\n", "\n").replace("\\t", "\t"), parts[3]));
                    }
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (final IOException unreadable) {
            return Collections.unmodifiableList(parsed);
        }
        return Collections.unmodifiableList(parsed);
    }
}
