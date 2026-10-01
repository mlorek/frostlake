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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A stage path's segments as DIRECTORY NAMES on the local disk that backs an internal stage.
 *
 * <p>A stage path is a literal prefix, not a filesystem path. The account writes through
 * {@code @st/sub/../other} and reading {@code @st/sub/../other} before that write lists nothing rather
 * than the contents of {@code @st/other}, so neither side collapses the segment. A local directory
 * tree does collapse it, and would let a path climb out of its own stage, so a segment made only of
 * dots is spelled with its dots percent-encoded — {@code .} becomes {@code %2E} and {@code ..} becomes
 * {@code %2E%2E}. Every other segment passes through untouched, so only a path segment spelled
 * literally {@code %2E} could collide with one.
 *
 * <p>A stage's names are flat on the account, so one name may run on past another: {@code dir} and
 * {@code dir/g} are two files side by side. A directory tree cannot hold a file and a directory under one
 * name, so the file {@code dir} is kept INSIDE the directory {@code dir}, as its entry {@link #OWN_FILE},
 * whenever other names continue it — and only then: every other file keeps its plain path, so a stage written
 * before this rule needs no change. A write that needs a directory where such a file stands moves the file in
 * first.
 *
 * <p>Writes and reads must escape identically or a file would be written where it cannot be listed,
 * which is why this lives in one place rather than at each call site.
 */
public final class StagePathSegments {

    private StagePathSegments() {
    }

    /** The on-disk name of the EMPTY segment a doubled slash produces. */
    private static final String EMPTY_SEGMENT = "%2F";

    /**
     * The entry, inside a directory, that holds the file whose stage-relative name is the directory's own name:
     * the staged {@code dir} beside a staged {@code dir/g} is kept at {@code dir/%00}.
     */
    public static final String OWN_FILE = "%00";

    /** One segment as the name it takes on disk. */
    public static String safe(final String segment) {
        if (segment.isEmpty()) {
            // A doubled slash names an empty segment, and the name it makes is its own: `@st//f` is
            // not `@st/f`, and finds nothing where `@st/f` finds the file.
            return EMPTY_SEGMENT;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (segment.charAt(i) != '.') {
                return segment;
            }
        }
        return segment.replace(".", "%2E");
    }

    /**
     * A slash-separated on-disk path back as the stage path that NAMED it: the inverse of
     * {@link #safe}, applied segment by segment, so a listing shows the path its writer wrote.
     *
     * @param onDisk the path relative to the stage's directory
     * @return the same path as a stage path spells it
     */
    public static String written(final String onDisk) {
        // A directory's own file is the directory's name.
        final String named = onDisk.endsWith("/" + OWN_FILE)
            ? onDisk.substring(0, onDisk.length() - OWN_FILE.length() - 1) : onDisk;
        if (named.indexOf("%2E") < 0 && named.indexOf(EMPTY_SEGMENT) < 0) {
            return named;
        }
        final StringBuilder out = new StringBuilder(named.length());
        for (final String segment : named.split("/", -1)) {
            if (out.length() > 0) {
                out.append('/');
            }
            if (EMPTY_SEGMENT.equals(segment)) {
                continue;
            }
            out.append(isEncodedDots(segment) ? segment.replace("%2E", ".") : segment);
        }
        return out.toString();
    }

    /** Whether a segment is nothing but encoded dots, which is what {@link #safe} produces. */
    private static boolean isEncodedDots(final String segment) {
        if (segment.isEmpty() || segment.length() % 3 != 0) {
            return false;
        }
        for (int i = 0; i < segment.length(); i += 3) {
            if (!"%2E".equals(segment.substring(i, i + 3))) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code base} with each of {@code relative}'s slash-separated segments resolved under it, every
     * one escaped so none of them can climb.
     *
     * @param base     the directory the stage owns
     * @param relative the stage path beneath it, slash-separated; empty resolves to {@code base}
     * @return the directory the path names
     */
    public static Path resolve(final Path base, final String relative) {
        final String[] segments = relative.split("/", -1);
        Path resolved = base;
        for (int i = 0; i < segments.length; i++) {
            // A TRAILING empty segment is only how a path spells "a directory" — `@st/dir/` names
            // `dir`. A leading or interior one is a name of its own: `@st//f` is not `@st/f`.
            if (segments[i].isEmpty() && i == segments.length - 1) {
                continue;
            }
            resolved = resolved.resolve(safe(segments[i]));
        }
        return resolved;
    }

    /**
     * The local file a staged FILE name is kept at: its path as {@link #resolve} spells it or, when that path is a
     * directory because other names continue this one, the directory's {@link #OWN_FILE}.
     *
     * @param base     the directory the stage owns
     * @param relative the file's stage-relative name
     * @return the file's local path, whether or not it exists
     */
    public static Path fileAt(final Path base, final String relative) {
        return ownFileOf(resolve(base, relative));
    }

    /**
     * A path that names a staged file, taken to the file it names: a directory holding its {@link #OWN_FILE} is that
     * file, and any other path is itself.
     *
     * @param path the path a stage-relative name resolved to
     * @return the file
     */
    public static Path ownFileOf(final Path path) {
        return path != null && Files.isDirectory(path) ? path.resolve(OWN_FILE) : path;
    }

    /**
     * Where a file of this stage-relative name is written: every directory above it is made, a file that stands where
     * one of them must go moving into it as its {@link #OWN_FILE}, and the file itself goes to its own path, or into
     * the directory of its name as that directory's own file when later names already continue it.
     *
     * @param base     the directory the stage owns
     * @param relative the file's stage-relative name
     * @return the local path to write the file to
     * @throws IOException when a directory cannot be made or a file moved
     */
    public static Path fileToWrite(final Path base, final String relative) throws IOException {
        final String[] segments = relative.split("/", -1);
        Files.createDirectories(base);
        Path at = base;
        for (int i = 0; i < segments.length - 1; i++) {
            at = directory(at, safe(segments[i]));
        }
        return ownFileOf(at.resolve(safe(segments[segments.length - 1])));
    }

    /**
     * The name a staged file goes by on its own, without the directories above it: its own on-disk name, or the name
     * of the directory it is the {@link #OWN_FILE} of.
     *
     * @param file a staged file
     * @return its bare name
     */
    public static String diskName(final Path file) {
        final String name = file.getFileName() == null ? "" : file.getFileName().toString();
        if (OWN_FILE.equals(name) && file.getParent() != null && file.getParent().getFileName() != null) {
            return file.getParent().getFileName().toString();
        }
        return name;
    }

    /**
     * The path a staged file's name spells: a directory's {@link #OWN_FILE} as the directory's own path, any other file
     * as itself — so a file keeps one identity when later names move it inside a directory of its name.
     *
     * @param file a staged file
     * @return the path its name spells
     */
    public static Path namedPath(final Path file) {
        final Path name = file.getFileName();
        return name != null && OWN_FILE.equals(name.toString()) && file.getParent() != null ? file.getParent() : file;
    }

    /**
     * A directory under {@code parent}, made when missing; a file that holds its name moves in as its own file. The
     * file steps aside under a temporary name while the directory is made, and a move that fails puts it back, so the
     * stage's names are what they were whichever step failed.
     */
    private static Path directory(final Path parent, final String name) throws IOException {
        final Path dir = parent.resolve(name);
        if (Files.isRegularFile(dir)) {
            final Path aside = Files.createTempFile(parent, ".own", ".moving");
            Files.move(dir, aside, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.createDirectory(dir);
                Files.move(aside, dir.resolve(OWN_FILE));
            } catch (final IOException failed) {
                try {
                    Files.deleteIfExists(dir);
                    Files.move(aside, dir);
                } catch (final IOException notRestored) {
                    failed.addSuppressed(notRestored);
                }
                throw failed;
            }
            return dir;
        }
        Files.createDirectories(dir);
        return dir;
    }
}
