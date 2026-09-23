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

import java.util.ArrayList;
import java.util.List;

/**
 * INFER_SCHEMA's LOCATION: a string naming a stage — {@code '@st'}, {@code '@db.schema.st/path/'},
 * {@code '@%t'} or {@code '@~/path'} — taken apart into the stage and the path written after it. The stage's
 * name ends at the first slash outside double quotes, and each part of it reads as an identifier does: folded
 * to upper case, or kept as written between double quotes.
 */
final class InferSchemaLocation {

    private final StageKind kind;

    /** The stage's canonical name parts; for a table's stage, the table's, the table last. */
    private final List<String> nameParts;

    /** The path written after the stage, with its leading slash; empty when none was written. */
    private final String path;

    private InferSchemaLocation(final StageKind kind, final List<String> nameParts, final String path) {
        this.kind = kind;
        this.nameParts = nameParts;
        this.path = path;
    }

    /**
     * Take a location apart.
     *
     * @param location the location, starting with {@code @}
     * @return the parts, or null when no stage is named ({@code '@'}, {@code '@/path'})
     */
    static InferSchemaLocation parse(final String location) {
        final String body = location.substring(1);
        if (body.startsWith("~")) {
            return new InferSchemaLocation(StageKind.USER, new ArrayList<String>(), body.substring(1));
        }
        int end = 0;
        boolean quoted = false;
        while (end < body.length() && (quoted || body.charAt(end) != '/')) {
            if (body.charAt(end) == '"') {
                quoted = !quoted;
            }
            end++;
        }
        final String name = body.substring(0, end);
        if (name.isEmpty()) {
            return null;
        }
        final List<String> parts = new ArrayList<String>();
        StageKind kind = StageKind.NAMED;
        for (final String part : SqlIdentifiers.canonicalTextParts(name)) {
            if (part.startsWith("%")) {
                kind = StageKind.TABLE;
                parts.add(part.substring(1));
            } else {
                parts.add(part);
            }
        }
        return new InferSchemaLocation(kind, parts, body.substring(end));
    }

    /**
     * Refuse a location whose stage does not exist: its database or schema, a named stage, or the table a
     * table's stage belongs to. The user's stage always exists.
     *
     * @param catalog the catalog the stage resolves in
     */
    void requireStage(final Catalog catalog) {
        if (kind == StageKind.USER) {
            return;
        }
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(nameParts.toArray(new String[0])));
        final String last = nameParts.get(nameParts.size() - 1);
        if (kind == StageKind.TABLE) {
            if (owner.tableExact(last) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", owner.qualifiedName("%" + last)));
            }
            return;
        }
        if (!owner.hasStageExact(last)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", owner.qualifiedName(last)));
        }
    }

    /**
     * The location as the executor's stage resolution reads one, path included.
     *
     * @return {@code @name/path}, {@code @%table/path} or {@code @~/path}
     */
    String canonical() {
        return stageOnly() + path;
    }

    /**
     * The path written after the stage.
     *
     * @return the path with its leading slash, or empty when none was written
     */
    String path() {
        return path;
    }

    /**
     * The stage without the path.
     *
     * @return {@code @name}, {@code @%table} or {@code @~}
     */
    String stageOnly() {
        switch (kind) {
            case USER:
                return "@~";
            case TABLE:
                return "@%" + QualifiedName.join(nameParts.toArray(new String[0]));
            default:
                return "@" + QualifiedName.join(nameParts.toArray(new String[0]));
        }
    }
}
