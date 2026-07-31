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

import dev.frostlake.functions.scalar.file.StageFileLocator;
import dev.frostlake.functions.scalar.file.StagedFile;
import dev.frostlake.metastore.Catalog;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves the stage references {@code TO_FILE} is given, reusing the executor's own
 * {@link QueryExecutor#resolveCopyBaseDir(String)} — the one place that already knows how
 * {@code @stage}, {@code @db.schema.stage}, {@code @~} and {@code @%table} map onto local directories.
 * Only the two descriptor-facing names are computed here.
 *
 * <p>The {@code STAGE} display was measured live. A named stage always renders fully
 * qualified and upper-cased regardless of how the caller wrote it — from a session on
 * {@code PROBE135B_DB.OTHER}, {@code TO_FILE('@s.sse2/hello.txt')} reports
 * {@code @PROBE135B_DB.S.SSE2} — so missing parts are filled from the current database and schema. The
 * user stage renders {@code @"~"} and a table stage renders the bare upper-cased table name
 * ({@code @TSTG}), with no database or schema even when the reference carried one.
 */
public class StageFileResolver implements StageFileLocator {

    private final QueryExecutor executor;
    private final Catalog catalog;

    public StageFileResolver(final QueryExecutor executor, final Catalog catalog) {
        this.executor = executor;
        this.catalog = catalog;
    }

    @Override
    public StagedFile locate(final String location) {
        if (location == null || !location.startsWith("@")) {
            return null;
        }
        final Path path;
        try {
            path = executor.resolveCopyBaseDir(location);
        } catch (final RuntimeException e) {
            // An unknown stage name throws out of the catalog; to the caller that is simply a file it
            // cannot reach, which TO_FILE reports as Snowflake's "was not found".
            return null;
        }
        if (path == null) {
            return null;
        }
        if (location.startsWith("@~")) {
            return new StagedFile(path, "@\"~\"", stripLeadingSlash(location.substring(2)));
        }
        if (location.startsWith("@%")) {
            final String reference = location.substring(2);
            final int slash = reference.indexOf('/');
            final String tableName = slash >= 0 ? reference.substring(0, slash) : reference;
            final String relative = slash >= 0 ? reference.substring(slash) : "";
            return new StagedFile(path, "@" + canonical(lastPart(tableName)), stripLeadingSlash(relative));
        }
        final String reference = location.substring(1);
        final int slash = reference.indexOf('/');
        final String stageName = slash >= 0 ? reference.substring(0, slash) : reference;
        final String relative = slash >= 0 ? reference.substring(slash + 1) : "";
        return new StagedFile(path, "@" + qualifiedStageName(stageName), relative);
    }

    /** {@code name} / {@code schema.name} / {@code db.schema.name} to the full upper-cased three-part form. */
    private String qualifiedStageName(final String stageName) {
        final String[] parts = stageName.split("\\.");
        final StringBuilder qualified = new StringBuilder();
        if (parts.length == 1) {
            qualified.append(canonical(catalog.getCurrentDatabase())).append('.')
                .append(canonical(catalog.getCurrentSchema())).append('.');
        } else if (parts.length == 2) {
            qualified.append(canonical(catalog.getCurrentDatabase())).append('.')
                .append(canonical(parts[0])).append('.');
        } else {
            for (int i = 0; i < parts.length - 1; i++) {
                qualified.append(canonical(parts[i])).append('.');
            }
        }
        return qualified.append(canonical(parts[parts.length - 1])).toString();
    }

    /** The last dot-separated part, for {@code @namespace.%table}. */
    private static String lastPart(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /** Upper-cases an unquoted identifier; a double-quoted one keeps its case, minus the quotes. */
    private static String canonical(final String part) {
        if (part == null) {
            return "";
        }
        if (part.length() >= 2 && part.startsWith("\"") && part.endsWith("\"")) {
            return part.substring(1, part.length() - 1);
        }
        return part.toUpperCase(Locale.ROOT);
    }

    private static String stripLeadingSlash(final String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
