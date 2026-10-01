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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stage reference taken apart the way the account reads it: the stage's ROOT, and the path written after it
 * as a PREFIX of the staged files' stage-relative names — never as a directory to list. A stage is a flat set
 * of object names, so the written path may end anywhere, inside a directory's name or a file's:
 *
 * <pre>
 *   stage holds  f1, f1.csv, dir/g, dirx/k
 *   &#64;st/f        f1, f1.csv            &#64;st/dir     dir/g, dirx/k
 *   &#64;st/f1       f1, f1.csv            &#64;st/dir/    dir/g
 *   &#64;st/f1/      nothing               &#64;st         every file, at any depth
 * </pre>
 *
 * <p>Live-verified for COPY INTO a table, a query over the stage, REMOVE and GET, on a named stage, a table's
 * stage and the user's stage, and case-sensitively ({@code @st/up} does not reach {@code Up/u}). Everything
 * else about the files is judged against the name AS STORED, whatever prefix selected them: a PATTERN is
 * matched against the file's internal path under the stage's folder ({@code stages/st/dir/g}), so it neither
 * sees the prefix removed nor a path relative to it, and a {@code FILES = (…)} entry is appended to the written
 * path as one string — {@code @st/dir} with {@code FILES = ('g')} names {@code dirg}, and
 * {@code @st/di} with {@code FILES = ('r/g')} names {@code dir/g}.
 */
public final class StagePrefix {

    /** Which kind of stage the reference names, which decides how a file's name is shown back. */
    private final StageKind kind;

    /** The stage's own directory; null when the stage has no local directory behind it. */
    private final Path root;

    /** The path written after the stage reference, without its leading slash, verbatim. */
    private final String prefix;

    /** Whether any path was written at all ({@code @st/} writes an empty one). */
    private final boolean pathWritten;

    /** The folder a PATTERN sees in front of each file's stage-relative name, e.g. {@code stages/st/}. */
    private final String patternFolder;

    /** The canonical name of the table a table stage belongs to; null for the other kinds. */
    private final String tableName;

    /** The kind of stage this prefix names — a positional column's declared width follows it. */
    StageKind kind() {
        return kind;
    }

    StagePrefix(final StageKind kind, final Path root, final String prefix, final boolean pathWritten,
                final String patternFolder, final String tableName) {
        this.kind = kind;
        this.root = root == null ? null : root.toAbsolutePath().normalize();
        this.prefix = prefix;
        this.pathWritten = pathWritten;
        this.patternFolder = patternFolder;
        this.tableName = tableName;
    }

    /**
     * The stage's own directory.
     *
     * @return the root, or null when the stage has no local directory
     */
    public Path getRoot() {
        return root;
    }

    /**
     * The path written after the stage reference, without its leading slash.
     *
     * @return the prefix, empty when none was written
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Whether the reference wrote a path after the stage at all.
     *
     * @return true for {@code @st/…} including {@code @st/}, false for a bare {@code @st}
     */
    public boolean isPathWritten() {
        return pathWritten;
    }

    /**
     * Every staged file the reference selects, in path order: each file under the root whose stage-relative
     * name starts with the prefix, filtered by a PATTERN when one is given.
     *
     * @param pattern a full-match regular expression over the file's internal path, or null
     * @return the files
     */
    public List<Path> files(final String pattern) {
        final List<Path> selected = new ArrayList<>();
        if (root == null) {
            return selected;
        }
        if (Files.isRegularFile(root)) {
            // A stage whose location is itself one file holds that one file, at an empty relative name.
            if (prefix.isEmpty()) {
                selected.add(root);
            }
            return selected;
        }
        if (!Files.isDirectory(root)) {
            return selected;
        }
        final List<Path> all = new ArrayList<>();
        try {
            collect(root, all);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list stage: " + e.getMessage(), e);
        }
        Collections.sort(all);
        for (final Path file : all) {
            final String relative = relative(file);
            if (!relative.startsWith(prefix)) {
                continue;
            }
            if (pattern != null && !(patternFolder + relative).matches(pattern)) {
                continue;
            }
            selected.add(file);
        }
        return selected;
    }

    /**
     * The file a {@code FILES = (…)} entry names: the written path and the entry joined as one string, so the
     * entry continues the prefix exactly where it stopped.
     *
     * @param entry the entry as written
     * @return the file's local path, or null when the joined name escapes the stage
     */
    public Path named(final String entry) {
        if (root == null || entry == null || entry.isEmpty()) {
            return null;
        }
        final Path resolved = root.resolve(prefix + entry).normalize();
        return resolved.startsWith(root) && !resolved.equals(root) ? StagePathSegments.ownFileOf(resolved) : null;
    }

    /**
     * Whether a file with exactly this stage-relative name exists. Other names that merely continue it do not count:
     * the stage is a flat set of names, so {@code dir} may be written beside a staged {@code dir/g}.
     *
     * @param relative the stage-relative name
     * @return whether it is taken
     */
    public boolean occupied(final String relative) {
        if (root == null) {
            return false;
        }
        final Path resolved = StagePathSegments.fileAt(root, relative).normalize();
        return resolved.startsWith(root) && !resolved.equals(root) && Files.isRegularFile(resolved);
    }

    /**
     * Whether any staged file's stage-relative name starts with {@code namePrefix}, at any depth.
     *
     * @param namePrefix the prefix to look for
     * @return whether one does
     */
    public boolean anyFileStartingWith(final String namePrefix) {
        return !new StagePrefix(kind, root, namePrefix, true, patternFolder, tableName).files(null).isEmpty();
    }

    /**
     * A staged file's name relative to the stage root, with forward slashes.
     *
     * @param file a file under the root
     * @return its stage-relative name
     */
    public String relative(final Path file) {
        if (root == null || file.equals(root) || !file.startsWith(root)) {
            return file.getFileName().toString();
        }
        // Back as the stage path that named it: a '.' or '..' segment is encoded on disk so it cannot
        // climb, and a listing must show what its writer wrote.
        return StagePathSegments.written(root.relativize(file).toString().replace('\\', '/'));
    }

    /**
     * A staged file's name relative to the WRITTEN path, as a pipe's REFRESH PREFIX reads it: the prefix
     * removed, and the separator that followed it too.
     *
     * @param file a file the reference selected
     * @return the rest of its name
     */
    public String relativeToPath(final Path file) {
        final String relative = relative(file);
        final String rest = relative.startsWith(prefix) ? relative.substring(prefix.length()) : relative;
        return rest.startsWith("/") ? rest.substring(1) : rest;
    }

    /**
     * A file's {@code METADATA$FILENAME}, which names it the way the stage kind does, live-verified: a named
     * stage gives the bare stage-relative name ({@code dir/g}), a table's stage prefixes the table's name as
     * an identifier spells it ({@code @T1/d/f2}, {@code @"lt"/f1}), and the user's stage prefixes
     * {@code @~/}.
     *
     * @param file a file the reference selected
     * @return the name a query over the stage reports
     */
    public String metadataFileName(final Path file) {
        final String relative = relative(file);
        if (kind == StageKind.TABLE) {
            return "@" + SqlIdentifiers.spellCanonical(tableName) + "/" + relative;
        }
        if (kind == StageKind.USER) {
            return "@~/" + relative;
        }
        return relative;
    }

    private static void collect(final Path directory, final List<Path> files) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (final Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    collect(entry, files);
                } else if (Files.isRegularFile(entry)) {
                    files.add(entry);
                }
            }
        }
    }
}
