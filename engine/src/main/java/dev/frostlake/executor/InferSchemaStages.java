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

import dev.frostlake.executor.copy.InferenceFile;
import dev.frostlake.executor.copy.InferredColumn;
import dev.frostlake.executor.copy.SchemaInference;
import dev.frostlake.executor.copy.SchemaScanOptions;
import dev.frostlake.functions.scalar.file.FileDescriptor;
import dev.frostlake.functions.table.StagedFileSchemas;
import dev.frostlake.metastore.model.FileFormat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The staged files an INFER_SCHEMA call names, found and read through the executor's own stage resolution.
 *
 * <p>The files are every file under the LOCATION's path, or the FILES entries each joined to that path with one
 * slash — {@code @st/two}, {@code @st/two/} and {@code @st//two} with {@code 'a.csv'} or {@code '/a.csv'} all
 * name {@code two/a.csv}, while {@code '//a.csv'} names {@code two//a.csv} and {@code './a.csv'}
 * {@code two/./a.csv}, neither of them that file — read in path order whichever way they were named, a name
 * listed twice read once. A FILES entry naming no file refuses the call: {@code Remote file '…' was not found.
 * …}, then the joined stage-relative name.
 */
public final class InferSchemaStages implements StagedFileSchemas {

    private final QueryExecutor executor;

    /**
     * The stages of one executor.
     *
     * @param executor the executor whose stages and file formats resolve the calls
     */
    public InferSchemaStages(final QueryExecutor executor) {
        this.executor = executor;
    }

    @Override
    public List<InferredColumn> inferColumns(final Map<String, Object> arguments) {
        final String written = String.valueOf(arguments.get("LOCATION"));
        final InferSchemaLocation location = InferSchemaLocation.parse(written);
        final StagePrefix staged = executor.resolveStagePrefix(location.canonical());
        final FileFormat format = InferSchemaArguments.resolveFileFormat(executor.getCatalog(),
            String.valueOf(arguments.get("FILE_FORMAT")));
        List<Path> paths = selectFiles(staged, location, arguments.get("FILES"), written);
        final long maxFiles = count(arguments.get("MAX_FILE_COUNT"));
        if (maxFiles > 0 && paths.size() > maxFiles) {
            paths = paths.subList(0, (int) maxFiles);
        }
        final String storedFolder = executor.copyPatternPrefix(location.stageOnly());
        final List<InferenceFile> files = new ArrayList<InferenceFile>();
        for (final Path path : paths) {
            files.add(new InferenceFile(path, staged.metadataFileName(path), staged.relative(path),
                storedFolder + staged.relative(path)));
        }
        final SchemaScanOptions options = new SchemaScanOptions(format.getType(), format.getOptions(),
            "ICEBERG".equalsIgnoreCase(String.valueOf(arguments.get("KIND"))),
            Boolean.TRUE.equals(arguments.get("IGNORE_CASE")), count(arguments.get("MAX_RECORDS_PER_FILE")));
        return SchemaInference.infer(files, options);
    }

    /** The files the call reads, in path order. */
    private List<Path> selectFiles(final StagePrefix staged, final InferSchemaLocation location,
                                   final Object filesArgument, final String written) {
        if (filesArgument == null) {
            return staged.files(null);
        }
        final List<String> entries = new ArrayList<String>();
        if (filesArgument instanceof List) {
            for (final Object entry : (List<?>) filesArgument) {
                entries.add(entryName(entry));
            }
        } else {
            entries.add(entryName(filesArgument));
        }
        // Every file of the stage by its stage-relative name, so a joined name matches only a file of exactly
        // that name: no '.' segment or doubled slash is resolved away.
        final StagePrefix whole = executor.resolveStagePrefix(location.stageOnly());
        final Map<String, Path> byName = new HashMap<String, Path>();
        for (final Path file : whole.files(null)) {
            byName.put(whole.relative(file), file);
        }
        final String stageAsWritten = written.substring(0, written.length() - location.path().length());
        final TreeSet<Path> selected = new TreeSet<Path>();
        for (final String entry : entries) {
            final String joined = joined(location.path(), entry);
            final Path file = byName.get(joined);
            if (file == null || !Files.isRegularFile(file)) {
                throw new RuntimeException(FileDescriptor.notFoundMessage(stageAsWritten + "/" + joined)
                    + "\n  File '" + joined + "'\n  Row 0 starts at line 0, column ");
            }
            selected.add(file);
        }
        return new ArrayList<Path>(selected);
    }

    /**
     * A FILES entry's stage-relative name: the LOCATION's path without its leading and trailing slashes, one
     * slash, then the entry without one leading slash.
     */
    private static String joined(final String locationPath, final String entry) {
        int from = 0;
        int to = locationPath.length();
        while (from < to && locationPath.charAt(from) == '/') {
            from++;
        }
        while (to > from && locationPath.charAt(to - 1) == '/') {
            to--;
        }
        final String folder = locationPath.substring(from, to);
        final String name = entry.startsWith("/") ? entry.substring(1) : entry;
        return folder.isEmpty() ? name : folder + "/" + name;
    }

    /** A FILES entry as a file name: a number names the file spelled by its digits. */
    private static String entryName(final Object entry) {
        if (entry instanceof BigDecimal) {
            return ((BigDecimal) entry).toPlainString();
        }
        return String.valueOf(entry);
    }

    /** A count argument's value, 0 when it was not given. */
    private static long count(final Object value) {
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).longValue();
        }
        return 0L;
    }
}
