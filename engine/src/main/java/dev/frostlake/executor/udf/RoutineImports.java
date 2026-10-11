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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an {@code IMPORTS = ('@stage/file.jar')} clause has to be true of at CREATE time.
 *
 * <p>Snowflake checks BOTH legs before the routine exists, measured on a live account:
 *
 * <pre>
 *   IMPORTS=('@nosuchstage/handlers.jar')      Stage 'DB.SCHEMA.NOSUCHSTAGE' does not exist or not authorized.
 *   IMPORTS=('@stg/handlers.jar'), no such jar Remote file 'handlers.jar' was not found. …
 * </pre>
 *
 * <p>Frostlake checked neither, so a routine could be created against a stage that was never made and
 * only fail — if at all — when something tried to load the handler.
 */
public final class RoutineImports {

    private RoutineImports() {
    }

    /**
     * Refuse an import whose stage or file is missing.
     *
     * <p>User stages ({@code @~}) and table stages ({@code @%name}) are not modelled in the catalog, and
     * a bare path with no {@code @} is a local file the loader resolves itself; both are left alone
     * rather than guessed at.
     */
    public static void validate(final List<String> imports, final Catalog catalog) {
        if (imports == null || imports.isEmpty() || catalog == null) {
            return;
        }
        for (final String entry : imports) {
            validateOne(entry, catalog);
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
        return "@" + catalog.resolveOwningSchema(stageName).qualifiedName(stage.getName())
            + (slash >= 0 ? trimmed.substring(slash) : "");
    }

    private static void validateOne(final String entry, final Catalog catalog) {
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
        if (stageName.isEmpty() || stageName.startsWith("~") || stageName.startsWith("%")) {
            return;
        }

        final Stage stage = resolveStage(stageName, catalog);
        if (stage == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", qualified(stageName, catalog)));
        }
        if (relative.isEmpty()) {
            return;
        }
        // A stage whose backing directory the engine cannot see (an external URL it does not resolve)
        // can answer nothing about its files, so there is nothing to refuse.
        if (stage.getLocalPath() == null) {
            return;
        }
        if (!stage.fileExists(relative)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Remote file '" + fileNameOf(relative) + "' was not found. If you are running a copy"
                + " command, please make sure files are not deleted when they are being loaded or files"
                + " are not being loaded into two different tables concurrently with auto purge option."));
        }
    }

    private static Stage resolveStage(final String stageName, final Catalog catalog) {
        try {
            return catalog.getStage(stageName);
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

    /** Live reports the file's own name, not the path it sat under. */
    private static String fileNameOf(final String relative) {
        final int slash = relative.lastIndexOf('/');
        return slash >= 0 ? relative.substring(slash + 1) : relative;
    }
}
