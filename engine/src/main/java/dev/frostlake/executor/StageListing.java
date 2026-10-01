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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code LIST @stage[/path] [PATTERN = '…']} — live's four columns over the files a stage holds (live-verified). LIST
 * takes any stage reference: a named stage, the user's stage {@code @~} and a table's stage {@code @%t}, each
 * qualified or not. A named stage is matched exactly, so {@code @"st"} does not reach a stage created as {@code ST}.
 * The path is a prefix of the files' stage-relative paths — {@code @st/su} lists {@code sub/f.csv}, and a path no
 * file starts with lists nothing. A named stage's files are named after the stage's own name, lower-cased, whatever
 * qualifies it ({@code @db.public.st} lists {@code st/sub/f.csv}); a user's or a table's stage lists the bare
 * stage-relative path. A table stage of a table that does not exist is a missing stage named after it:
 * {@code Stage 'DB.PUBLIC."%T"' does not exist or not authorized.} PATTERN matches a file's path as the account
 * stores it, under the stage's own folder, as COPY's PATTERN does — so {@code '.*g.*'} lists every file of a
 * named stage, its folder being {@code stages/…}.
 */
final class StageListing {

    private final QueryExecutor executor;

    StageListing(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * The listing a LIST statement answers.
     *
     * @param ctx the statement
     * @return the listing
     */
    ResultSet list(final FrostlakeParser.ListStatementContext ctx) {
        final FrostlakeParser.StageRefContext stage = ctx.stageRef();
        StageReferenceShape.refuseEmptyParts(stage, executor.getCatalog());
        final String prefix = stage.stagePath() == null ? "" : stage.stagePath().getText().substring(1);
        final String shownStage;
        final String location;
        if (stage.TILDE() != null) {
            shownStage = null;
            location = "@~";
        } else if (stage.PERCENT() != null) {
            requireTable(stage);
            shownStage = null;
            location = "@%" + QualifiedName.join(StageReferenceShape.nameParts(stage).toArray(new String[0]));
        } else {
            shownStage = StageReferenceShape.namedStage(stage, executor.getCatalog()).getName().toLowerCase(Locale.ROOT);
            location = "@" + QualifiedName.join(StageReferenceShape.nameParts(stage).toArray(new String[0]));
        }
        final Path root = executor.resolveCopyBaseDir(location);
        final String stored = executor.copyPatternPrefix(location);
        final String pattern = ctx.PATTERN() != null ? ParseTreeText.extractStringLiteral(ctx.STRING_LITERAL()) : null;
        final List<ResultSetColumn> cols = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("size", NumericType.NUMBER),
            new ResultSetColumn("md5", StringType.VARCHAR),
            new ResultSetColumn("last_modified", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return new ResultSet(cols, rows);
        }
        try {
            for (final Path file : filesUnder(root)) {
                // The stage path that NAMED the file: a '.' or '..' segment is encoded on disk so it
                // cannot climb out of the stage, and a listing shows what its writer wrote.
                final String relative =
                    StagePathSegments.written(root.relativize(file).toString().replace('\\', '/'));
                if (!relative.startsWith(prefix)) {
                    continue;
                }
                if (pattern != null && !(stored + relative).matches(pattern)) {
                    continue;
                }
                final String name = shownStage == null ? relative : shownStage + "/" + relative;
                rows.add(new Row(Arrays.asList(name, Files.size(file), md5Hex(file),
                    DateTimeFormatter.RFC_1123_DATE_TIME
                        .format(Files.getLastModifiedTime(file).toInstant().atZone(ZoneOffset.UTC)))));
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list stage: " + e.getMessage(), e);
        }
        return new ResultSet(cols, rows);
    }

    /** A table's stage exists only with its table: a missing one is refused as the missing stage it would be. */
    private void requireTable(final FrostlakeParser.StageRefContext stage) {
        final List<String> parts = StageReferenceShape.nameParts(stage);
        final Catalog catalog = executor.getCatalog();
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts.toArray(new String[0])));
        final String table = parts.get(parts.size() - 1);
        if (owner.tableExact(table) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", owner.qualifiedName("%" + table)));
        }
    }

    /** Every regular file under a stage's root, in path order. */
    private static List<Path> filesUnder(final Path root) throws IOException {
        final List<Path> files = new ArrayList<>();
        collectFiles(root, files);
        Collections.sort(files);
        return files;
    }

    private static void collectFiles(final Path directory, final List<Path> files) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (final Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    collectFiles(entry, files);
                } else if (Files.isRegularFile(entry)) {
                    files.add(entry);
                }
            }
        }
    }

    /** The MD5 of a staged file's bytes, hex-encoded — what LIST's md5 column carries. */
    private static String md5Hex(final Path file) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            final byte[] hash = digest.digest(Files.readAllBytes(file));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 unavailable", e);
        }
    }
}
