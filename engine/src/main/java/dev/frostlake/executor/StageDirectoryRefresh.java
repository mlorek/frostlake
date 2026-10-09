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

import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A directory table's refresh: the stage's files are compared with the ones the table has registered, and the
 * table is brought up to date. {@code DIRECTORY(@stage)} lists what was registered, so a file staged, replaced
 * or removed since shows there only after the next refresh (live-verified).
 *
 * <p>The refresh answers one row per file whose registration changed, and none for a file that stayed as it was:
 *
 * <pre>
 *   file                       status              description
 *   stages/&lt;id&gt;/a.csv        REGISTERED_NEW      File registered successfully.
 *   stages/&lt;id&gt;/a.csv        REGISTERED_UPDATE   File registered (updated) successfully.
 *   stages/&lt;id&gt;/a.csv        UNREGISTERED        File unregistered successfully.
 * </pre>
 *
 * The id is the stage's own and the same at every refresh. A subpath narrows the refresh to the files whose
 * stage-relative path starts with it, as plain text: {@code su} reaches {@code sub/a.csv} and {@code subfile.csv},
 * {@code /sub/} reaches nothing, and a file outside it keeps its registration whatever became of it.
 */
public final class StageDirectoryRefresh {

    private static final String ID_ALPHABET = "0123456789abcdef";
    private static final String[] REGISTERED_NEW = {"REGISTERED_NEW", "File registered successfully."};
    private static final String[] REGISTERED_UPDATE = {"REGISTERED_UPDATE", "File registered (updated) successfully."};
    private static final String[] UNREGISTERED = {"UNREGISTERED", "File unregistered successfully."};

    private StageDirectoryRefresh() {
    }

    /** The columns a refresh answers. */
    public static List<ResultSetColumn> columns() {
        return Arrays.asList(
            new ResultSetColumn("file", StringType.VARCHAR),
            new ResultSetColumn("status", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR));
    }

    /**
     * Refreshes a stage's directory table, and records what left and what arrived in every stream on the stage: a
     * file that changed leaves and arrives again.
     *
     * @param executor  the executor that lists the stage's files
     * @param stage     the stage, its directory table enabled
     * @param database  the stage's database
     * @param qualified the stage's qualified name, DB.SCHEMA.NAME
     * @param subpath   the path prefix the refresh is narrowed to; empty for the whole stage
     * @return a row for every file registered, registered again or unregistered, by path
     */
    public static List<Row> refresh(final QueryExecutor executor, final Stage stage, final String database,
                                    final String qualified, final String subpath) {
        final Map<String, List<Object>> now = new LinkedHashMap<>();
        for (final Row row : executor.stageDirectoryRows(qualified)) {
            final String path = String.valueOf(row.getValue(0));
            if (path.startsWith(subpath)) {
                now.put(path, new ArrayList<>(row.getValues()));
            }
        }
        final Map<String, List<Object>> registered = stage.getDirectoryRegistry();
        final List<List<Object>> removed = new ArrayList<>();
        final List<List<Object>> added = new ArrayList<>();
        final Map<String, String[]> outcomes = new TreeMap<>();
        for (final Map.Entry<String, List<Object>> file : registered.entrySet()) {
            if (!file.getKey().startsWith(subpath)) {
                continue;
            }
            final List<Object> current = now.get(file.getKey());
            if (current == null) {
                outcomes.put(file.getKey(), UNREGISTERED);
            }
            if (!sameFile(file.getValue(), current)) {
                removed.add(file.getValue());
            }
        }
        for (final Map.Entry<String, List<Object>> file : now.entrySet()) {
            final List<Object> known = registered.get(file.getKey());
            if (!sameFile(known, file.getValue())) {
                added.add(file.getValue());
                outcomes.put(file.getKey(), known == null ? REGISTERED_NEW : REGISTERED_UPDATE);
            }
        }
        final Iterator<String> paths = registered.keySet().iterator();
        while (paths.hasNext()) {
            if (paths.next().startsWith(subpath)) {
                paths.remove();
            }
        }
        registered.putAll(now);
        stage.setDirectoryRefreshed(StatementClock.instant());
        if (executor.getStreamManager() != null) {
            executor.getStreamManager().trackRefresh(database, StreamSourceType.STAGE, qualified, removed, added);
        }
        final String prefix = "stages/" + idOf(stage) + "/";
        final List<Row> rows = new ArrayList<>();
        for (final Map.Entry<String, String[]> outcome : outcomes.entrySet()) {
            rows.add(new Row(Arrays.asList(prefix + outcome.getKey(), outcome.getValue()[0], outcome.getValue()[1])));
        }
        return rows;
    }

    /** The stage's id, given the first time a refresh needs it. */
    private static String idOf(final Stage stage) {
        if (stage.getDirectoryId() == null) {
            final String hex = StatementTokens.draw(ID_ALPHABET, 32);
            stage.setDirectoryId(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20));
        }
        return stage.getDirectoryId();
    }

    /** Whether two directory rows describe the same file: same path, size, modification time and content. */
    private static boolean sameFile(final List<Object> a, final List<Object> b) {
        return a != null && b != null && Objects.equals(a.get(0), b.get(0)) && Objects.equals(a.get(1), b.get(1))
            && sameMoment(a.get(2), b.get(2)) && Objects.equals(a.get(3), b.get(3));
    }

    /** Whether two modification times are the same moment, whatever offset each row was written at. */
    private static boolean sameMoment(final Object a, final Object b) {
        if (a instanceof ZonedDateTime && b instanceof ZonedDateTime) {
            return ((ZonedDateTime) a).toInstant().equals(((ZonedDateTime) b).toInstant());
        }
        return Objects.equals(a, b);
    }
}
