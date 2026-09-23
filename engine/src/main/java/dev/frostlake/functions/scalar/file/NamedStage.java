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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.executor.StagePathSegments;

import java.nio.file.Path;

/**
 * A named stage as the stage functions see it: its canonical database, schema and name, the location its files
 * live at, and the local directory that holds them. An external stage's location is the URL it was created with; an
 * internal stage's is its engine-managed directory as a {@code file://} URL ending in a slash, which is where this
 * engine keeps what the account keeps in its own storage.
 */
public final class NamedStage {

    private final String database;
    private final String schema;
    private final String name;
    private final String location;
    private final Path root;

    /**
     * @param database the database, canonical
     * @param schema   the schema, canonical
     * @param name     the stage's own name, canonical
     * @param location where its files live, as GET_STAGE_LOCATION reports it
     * @param root     the local directory behind it, or null when it has none
     */
    public NamedStage(final String database, final String schema, final String name, final String location,
                      final Path root) {
        this.database = database;
        this.schema = schema;
        this.name = name;
        this.location = location;
        this.root = root;
    }

    /** @return the database, canonical */
    public String getDatabase() {
        return database;
    }

    /** @return the schema, canonical */
    public String getSchema() {
        return schema;
    }

    /** @return the stage's own name, canonical */
    public String getName() {
        return name;
    }

    /** @return where the stage's files live */
    public String getLocation() {
        return location;
    }

    /** @return the local directory behind the stage, or null */
    public Path getRoot() {
        return root;
    }

    /**
     * The local file a stage-relative name addresses, each segment spelled as the stage's directory spells it, so no
     * name climbs out of the stage — a name other staged names continue being its directory's own file.
     *
     * @param relative the stage-relative name
     * @return the file's local path, or null when the stage has no local directory
     */
    public Path fileAt(final String relative) {
        if (root == null) {
            return null;
        }
        final Path resolved = StagePathSegments.fileAt(root, relative == null ? "" : relative);
        return resolved.normalize().startsWith(root.normalize()) ? resolved : null;
    }

    /**
     * The stage fully qualified with every part quoted, {@code @"DB"."SCHEMA"."ST"} — how the account names it when a
     * path does not belong to it.
     *
     * @return the quoted reference
     */
    public String quotedReference() {
        return "@" + quoted(database) + "." + quoted(schema) + "." + quoted(name);
    }

    private static String quoted(final String part) {
        return "\"" + part.replace("\"", "\"\"") + "\"";
    }
}
