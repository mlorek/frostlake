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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an {@code IMPORTS = ('@stage/file.jar')} clause has to be true of at CREATE time. The account judges each
 * import before the routine exists (live-verified):
 *
 * <pre>
 *   IMPORTS=('@stg')                           IMPORT must specify the path of a file on a stage, but only …
 *   IMPORTS=('@nosuchstage/handlers.jar')      Stage 'DB.SCHEMA.NOSUCHSTAGE' does not exist or not authorized.
 *   IMPORTS=('@stg/lib/a.jar'), no such jar    Remote file 'lib/a.jar' was not found. …
 * </pre>
 */
public final class RoutineImports {

    private RoutineImports() {
    }

    /**
     * Refuse an import whose stage or file is missing, in the order the imports are written (live-verified): an
     * import that names a stage and no file below it ({@code @stg}, {@code @stg/}, {@code @~}) is refused before
     * its stage is looked for, a stage that is not there — a table's stage among them, which is there while its
     * table is — and then a file the stage does not hold, named by its path below the stage as written. A path
     * that ends in a slash names a directory, which is not judged here.
     *
     * <p>A stage whose files the engine cannot see — an external URL it does not resolve — is left alone, and so
     * is a path with no {@code @}, a local file the loader resolves itself.
     *
     * @param imports the imports as written
     * @param catalog the catalog the stages resolve in
     * @param stages  where the stages the engine keeps itself hold their files, or null
     */
    public static void validate(final List<String> imports, final Catalog catalog, final ImportLocalizer stages) {
        if (imports == null || imports.isEmpty() || catalog == null) {
            return;
        }
        for (final String entry : imports) {
            validateOne(entry, catalog, stages);
        }
    }

    /**
     * The imports as a routine keeps and tells them: a named stage spelled in full, upper case where it was
     * written without quotes - {@code @stg/a.jar} is {@code @DB.SCHEMA.STG/a.jar} - and the path beneath it as
     * written (live-verified). A user's or a table's stage, and a path that is no stage, stay as written.
     *
     * @param imports the imports as written, already judged by {@link #validate}
     * @param catalog the catalog the stages resolve in
     * @return the imports, in written order
     */
    public static List<String> canonical(final List<String> imports, final Catalog catalog) {
        final List<String> kept = new ArrayList<>();
        if (imports == null) {
            return kept;
        }
        for (final String entry : imports) {
            kept.add(canonicalOne(entry, catalog));
        }
        return kept;
    }

    private static String canonicalOne(final String entry, final Catalog catalog) {
        final String trimmed = entry == null ? "" : entry.trim();
        if (catalog == null || !trimmed.startsWith("@")) {
            return entry;
        }
        final int slash = trimmed.indexOf('/');
        final String stageName = slash >= 0 ? trimmed.substring(1, slash) : trimmed.substring(1);
        if (stageName.isEmpty() || stageName.startsWith("~") || stageName.startsWith("%")) {
            return entry;
        }
        final Stage stage = resolveStage(stageName, catalog);
        if (stage == null) {
            return entry;
        }
        return "@" + catalog.resolveOwningSchema(SqlIdentifiers.canonicalText(stageName)).qualifiedName(stage.getName())
            + (slash >= 0 ? trimmed.substring(slash) : "");
    }

    private static void validateOne(final String entry, final Catalog catalog, final ImportLocalizer stages) {
        if (entry == null) {
            return;
        }
        final String trimmed = entry.trim();
        if (!trimmed.startsWith("@")) {
            return;
        }
        final String reference = trimmed.substring(1);
        final int slash = reference.indexOf('/');
        final String stageName = slash >= 0 ? reference.substring(0, slash) : reference;
        final String relative = slash >= 0 ? reference.substring(slash + 1) : "";
        if (relative.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.inline("IMPORT must specify the path of a file on a"
                + " stage, but only the stage name '" + stageName + "' was provided."));
        }
        if (stageName.isEmpty()) {
            return;
        }
        if (stageName.startsWith("%")) {
            requireTableOf(stageName, catalog);
        } else if (!stageName.startsWith("~")) {
            final Stage stage = resolveStage(stageName, catalog);
            if (stage == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", qualified(stageName, catalog)));
            }
            if (stage.getLocalPath() != null) {
                if (!relative.endsWith("/") && !stage.fileExists(relative)) {
                    throw new RuntimeException(missingFile(relative));
                }
                return;
            }
        }
        if (relative.endsWith("/") || stages == null) {
            return;
        }
        final Path kept = stages.keptStageFile(trimmed);
        if (kept != null && !Files.isRegularFile(kept)) {
            throw new RuntimeException(missingFile(relative));
        }
    }

    /** A table's stage is there while its table is: one of a table that is not is a stage that does not exist. */
    private static void requireTableOf(final String stageName, final Catalog catalog) {
        final String tableName = stageName.substring(1);
        boolean held = false;
        try {
            final String[] parts = SqlIdentifiers.canonicalTextParts(tableName);
            held = parts.length > 0 && catalog.resolveOwningSchema(SqlIdentifiers.canonicalText(tableName))
                .hasTable(parts[parts.length - 1]);
        } catch (final RuntimeException missing) {
            held = false;
        }
        if (!held) {
            final int dot = tableName.lastIndexOf('.');
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage",
                qualified(tableName.substring(0, dot + 1) + "%" + tableName.substring(dot + 1), catalog)));
        }
    }

    /** The account's sentence for a stage that holds no file at an import's path, the path as written. */
    private static String missingFile(final String relative) {
        return "Remote file '" + relative + "' was not found. If you are running a copy command, please make sure"
            + " files are not deleted when they are being loaded or files are not being loaded into two different"
            + " tables concurrently with auto purge option.";
    }

    private static Stage resolveStage(final String stageName, final Catalog catalog) {
        try {
            // The import names the stage as an identifier does: quoted parts kept, the rest folded.
            return catalog.getStage(SqlIdentifiers.canonicalText(stageName));
        } catch (final RuntimeException notFound) {
            return null;
        }
    }

    /** Live names the stage in full, under the current database and schema when the reference is bare. */
    private static String qualified(final String stageName, final Catalog catalog) {
        if (stageName.indexOf('.') >= 0) {
            return stageName.toUpperCase(Locale.ROOT);
        }
        final String database = catalog.getCurrentDatabase();
        final String schema = catalog.getCurrentSchema();
        if (database == null || schema == null) {
            return stageName.toUpperCase(Locale.ROOT);
        }
        return database + "." + schema + "." + stageName.toUpperCase(Locale.ROOT);
    }
}
