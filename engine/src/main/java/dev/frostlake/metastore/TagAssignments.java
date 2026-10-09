/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tags objects carry, and what keeps each assignment on the tag it was made to.
 *
 * <p>An object keeps an assignment under the tag's own identity ({@link Tag#getId()}), not under its name. Two
 * tags of one name in two schemas are therefore two assignments on one object, a tag that is renamed or moved, or
 * whose schema or database is renamed, takes its assignments along, and an assignment to a tag that was dropped
 * is told by nothing until the tag is restored. A tag created under a dropped tag's name is another tag and starts
 * without assignments (live-verified).
 *
 * <p>A clone of a schema or a database carries clones of the tags that live in it, and what was cloned with them
 * is mapped to those; an assignment to a tag that lives elsewhere keeps naming that one (live-verified).
 *
 * <p>A snapshot written before assignments were kept by identity holds them under the tags' bare names. Such a
 * key is read where the object lives, as it was then: the tag of that name in the object's schema, else in its
 * database, else in the session's schema, else anywhere in the account.
 */
public final class TagAssignments {

    private TagAssignments() {
    }

    /**
     * Sets a tag on an object.
     *
     * @param target the object
     * @param tag the tag
     * @param value the value
     */
    public static void set(final Taggable target, final Tag tag, final String value) {
        // An assignment an older snapshot kept under the tag's name gives way to this one.
        target.unsetTag(tag.getName());
        target.setTag(tag.getId(), value);
    }

    /**
     * Takes a tag off an object; an object that does not carry it is left as it is.
     *
     * @param target the object
     * @param tag the tag
     */
    public static void unset(final Taggable target, final Tag tag) {
        target.unsetTag(tag.getId());
        target.unsetTag(tag.getName());
    }

    /**
     * The tags an object carries that still exist, in the order of their full names as SQL spells them.
     *
     * @param catalog the catalog
     * @param object the object, or null for none
     * @param objectDatabase the database the object lives in, or null for an account object
     * @param objectSchema the schema the object lives in, or null when it lives in none
     * @return the assignments, one per tag
     */
    public static List<TagAssignment> on(final Catalog catalog, final Taggable object, final String objectDatabase,
                                         final String objectSchema) {
        final List<TagAssignment> found = new ArrayList<>();
        if (object == null) {
            return found;
        }
        final Set<String> told = new HashSet<>();
        final Map<String, String> values = object.getTagValues();
        // An assignment kept by identity is told before one an older snapshot kept by name.
        for (int pass = 0; pass < 2; pass++) {
            for (final Map.Entry<String, String> entry : values.entrySet()) {
                if (Tag.isId(entry.getKey()) != (pass == 0)) {
                    continue;
                }
                final TagAssignment assignment = locate(catalog, entry.getKey(), entry.getValue(), objectDatabase,
                    objectSchema);
                if (assignment != null && told.add(assignment.getTag().getId())) {
                    found.add(assignment);
                }
            }
        }
        Collections.sort(found, new Comparator<TagAssignment>() {
            @Override
            public int compare(final TagAssignment a, final TagAssignment b) {
                return a.path().compareTo(b.path());
            }
        });
        return found;
    }

    /**
     * The value an object itself gives a tag, or null when it carries none.
     *
     * @param catalog the catalog
     * @param object the object, or null for none
     * @param tag the tag
     * @param objectDatabase the database the object lives in, or null for an account object
     * @param objectSchema the schema the object lives in, or null when it lives in none
     * @return the value, or null
     */
    public static String value(final Catalog catalog, final Taggable object, final Tag tag,
                               final String objectDatabase, final String objectSchema) {
        for (final TagAssignment assignment : on(catalog, object, objectDatabase, objectSchema)) {
            if (assignment.getTag() == tag) {
                return assignment.getValue();
            }
        }
        return null;
    }

    /**
     * Maps what was cloned with a schema or a database to the clones of the tags that were cloned with it.
     *
     * @param clones the cloned schemas
     * @param database the cloned database, or null when a schema alone was cloned
     */
    public static void mapToClones(final List<Schema> clones, final Database database) {
        final Map<String, String> ids = new HashMap<>();
        for (final Schema clone : clones) {
            for (final Tag tag : clone.getTags()) {
                if (tag.getClonedFromId() != null) {
                    ids.put(tag.getClonedFromId(), tag.getId());
                    tag.setClonedFromId(null);
                }
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        if (database != null) {
            remap(database, ids);
        }
        for (final Schema clone : clones) {
            for (final Taggable object : taggablesOf(clone)) {
                remap(object, ids);
            }
        }
    }

    private static void remap(final Taggable object, final Map<String, String> ids) {
        for (final Map.Entry<String, String> entry : object.getTagValues().entrySet()) {
            final String clone = ids.get(entry.getKey());
            if (clone != null) {
                object.unsetTag(entry.getKey());
                object.setTag(clone, entry.getValue());
            }
        }
    }

    /** Everything in a schema that can carry a tag, the schema itself and its tables' columns included. */
    private static List<Taggable> taggablesOf(final Schema schema) {
        final List<Taggable> objects = new ArrayList<>();
        objects.add(schema);
        for (final Table table : schema.getTables()) {
            objects.add(table);
            objects.addAll(table.getColumns());
        }
        for (final Table table : schema.getShadowedTables()) {
            objects.add(table);
            objects.addAll(table.getColumns());
        }
        objects.addAll(schema.getViews());
        objects.addAll(schema.getMaterializedViews());
        objects.addAll(schema.getDynamicTables());
        objects.addAll(schema.getStreams());
        objects.addAll(schema.getTasks());
        objects.addAll(schema.getAlerts());
        objects.addAll(schema.getPipes());
        objects.addAll(schema.getStages());
        objects.addAll(schema.getFunctions());
        objects.addAll(schema.getProcedures());
        objects.addAll(schema.getMaskingPolicies());
        objects.addAll(schema.getRowAccessPolicies());
        objects.addAll(schema.getAggregationPolicies());
        objects.addAll(schema.getProjectionPolicies());
        objects.addAll(schema.getJoinPolicies());
        objects.addAll(schema.getContacts());
        objects.addAll(schema.getTags());
        for (final SecurityObjectKind kind : SecurityObjectKind.values()) {
            if (kind.isSchemaObject()) {
                for (final SecurityObject object : schema.getSecurityObjects().list(kind)) {
                    objects.add(object);
                }
            }
        }
        return objects;
    }

    /** Where the tag an assignment's key names lives now, with the value; null when no such tag exists. */
    private static TagAssignment locate(final Catalog catalog, final String key, final String value,
                                        final String objectDatabase, final String objectSchema) {
        if (Tag.isId(key)) {
            for (final Database database : catalog.getAllDatabases()) {
                for (final Schema schema : database.getAllSchemas()) {
                    for (final Tag tag : schema.getTags()) {
                        if (key.equals(tag.getId())) {
                            return new TagAssignment(database.getName(), schema.getName(), tag, value);
                        }
                    }
                }
            }
            return null;
        }
        return locateByName(catalog, key, value, objectDatabase, objectSchema);
    }

    /** {@link #locate} for a key an older snapshot kept: the tag's bare name, or its full one. */
    private static TagAssignment locateByName(final Catalog catalog, final String key, final String value,
                                              final String objectDatabase, final String objectSchema) {
        final String[] written = QualifiedName.parse(key).parts();
        final String name = written[written.length - 1];
        if (written.length == 3) {
            return in(catalog.findDatabase(written[0]), written[1], name, value);
        }
        final Database home = objectDatabase == null ? null : catalog.findDatabase(objectDatabase);
        TagAssignment found = in(home, objectSchema, name, value);
        if (found == null && home != null) {
            for (final Schema schema : home.getAllSchemas()) {
                found = found != null ? found : in(home, schema.getName(), name, value);
            }
        }
        if (found == null && catalog.getCurrentDatabase() != null) {
            found = in(catalog.findDatabase(catalog.getCurrentDatabase()), catalog.getCurrentSchema(), name, value);
        }
        if (found == null) {
            for (final Database database : catalog.getAllDatabases()) {
                for (final Schema schema : database.getAllSchemas()) {
                    found = found != null ? found : in(database, schema.getName(), name, value);
                }
            }
        }
        return found;
    }

    private static TagAssignment in(final Database database, final String schemaName, final String name,
                                    final String value) {
        if (database == null || schemaName == null || !database.hasSchema(schemaName)) {
            return null;
        }
        final Schema schema = database.getSchema(schemaName);
        return schema.hasTag(name) ? new TagAssignment(database.getName(), schema.getName(), schema.getTag(name), value)
            : null;
    }
}
