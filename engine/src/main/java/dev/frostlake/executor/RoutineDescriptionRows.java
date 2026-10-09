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

import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.storage.Row;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The rows DESCRIBE FUNCTION and DESCRIBE PROCEDURE add for a routine a handler runs: its imports, handler,
 * runtime and packages, where a Java or Scala handler's classes are kept, and for a Python routine the artifact
 * repository its packages come from. A SQL or JavaScript routine adds none (live-verified).
 *
 * <p>The two lists of what an account installed for a Python routine are told empty: the engine installs nothing.
 */
final class RoutineDescriptionRows {

    private RoutineDescriptionRows() {
    }

    /**
     * Add a handler routine's rows after its body.
     *
     * @param rows           the description so far
     * @param language       the routine's language
     * @param handler        the routine's handler
     * @param runtimeVersion the routine's runtime version, or null
     * @param imports        the routine's imports
     * @param environment    what the routine is given to run with
     * @param function       whether the routine is a function, which tells whether it is an aggregate
     */
    static void add(final List<Row> rows, final String language, final String handler, final String runtimeVersion,
                    final List<String> imports, final RoutineEnvironment environment, final boolean function) {
        if (!isHandlerLanguage(language)) {
            return;
        }
        final boolean python = "PYTHON".equalsIgnoreCase(language);
        rows.add(row("imports", plainList(imports)));
        rows.add(row("handler", handler));
        if (!python) {
            rows.add(row("target_path", environment.getTargetPath()));
        }
        rows.add(row("runtime_version", runtimeVersion));
        if (!python) {
            rows.add(row("packages", plainList(environment.getPackages())));
            return;
        }
        // A Python routine's packages come from an artifact repository, and are told with it.
        rows.add(row("packages", "[]"));
        final String repository = environment.describedArtifactRepository();
        if (repository != null) {
            rows.add(row("artifact_repository", repository));
            rows.add(row("artifact_repository_packages", RoutineEnvironment.listed(environment.getPackages())));
        }
        rows.add(row("installed_packages", "[]"));
        if (function) {
            rows.add(row("is_aggregate", "false"));
        }
        if (repository != null) {
            rows.add(row("artifact_repository_installed_packages", "[]"));
        }
    }

    /**
     * Whether a routine of this language is run by a handler, and so tells these rows - and a {@code body} row
     * even when it has no inline body, its handler coming from an import.
     *
     * @param language the routine's language, or null for SQL
     * @return true for Java, Python and Scala
     */
    static boolean isHandlerLanguage(final String language) {
        final String name = language == null ? "SQL" : language.toUpperCase(Locale.ROOT);
        return "PYTHON".equals(name) || "JAVA".equals(name) || "SCALA".equals(name);
    }

    private static Row row(final String property, final String value) {
        return new Row(Arrays.asList(property, value));
    }

    /** A list as these rows tell one whose items are not quoted: {@code [a, b]}, and {@code []} for none. */
    static String plainList(final List<String> items) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; items != null && i < items.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(items.get(i));
        }
        return text.append(']').toString();
    }
}
