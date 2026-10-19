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

package dev.frostlake.metastore;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.GrantRemap;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RoutineSignatures;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The names privileges on objects are recorded and checked under, and what keeps them on their objects.
 *
 * <p>A privilege on a schema-level object is recorded under the object's FULL name, database.schema.name, a schema's
 * under database.schema and a database's under its own name, each resolved against the session the way any
 * reference to the object is. A privilege check resolves the name it is asked about the same way, so a grant made on
 * {@code t} in one schema reaches that table however a later statement spells it, and never a same-named table in
 * another schema. Account-level objects keep the name as written.
 *
 * <p>Snowflake ties a grant to its object rather than to its name. A rename, a move or a swap therefore carries the
 * object's privileges to its new name. A new object that takes a dropped one's name starts with none of the dropped
 * object's privileges; CREATE OR REPLACE keeps them only with COPY GRANTS, which copies every privilege but OWNERSHIP.
 * A database or schema clone grants each member of the clone what the source member holds, but not the container
 * itself: "the clone inherits all granted privileges on the clones of all child objects", while "the clone of the
 * container itself ... doesn't inherit the privileges granted on the source container" (Snowflake's documentation).
 * A dropped object's privileges stay recorded, so UNDROP brings the object back with them.
 *
 * <p>A routine is granted one signature at a time ({@code GRANT USAGE ON FUNCTION f(NUMBER)}), so each overload
 * records its privileges under its name followed by its argument types ({@link #routine}). An overload is an object
 * of its own: a new one neither inherits nor clears another's privileges.
 */
public final class GrantNames {

    /** The kinds of object that live inside a schema. */
    private static final Set<String> SCHEMA_LEVEL = new HashSet<>(Arrays.asList(
        "TABLE", "VIEW", "MATERIALIZED_VIEW", "DYNAMIC_TABLE", "EXTERNAL_TABLE", "EVENT_TABLE", "ICEBERG_TABLE",
        "HYBRID_TABLE", "SEQUENCE", "STAGE", "FILE_FORMAT", "FUNCTION", "PROCEDURE", "STREAM", "TASK", "PIPE",
        "ALERT", "MASKING_POLICY", "ROW_ACCESS_POLICY", "PROJECTION_POLICY", "AGGREGATION_POLICY", "JOIN_POLICY",
        "PASSWORD_POLICY", "SESSION_POLICY", "AUTHENTICATION_POLICY", "PACKAGES_POLICY", "NETWORK_RULE", "SECRET",
        "TAG", "STREAMLIT", "NOTEBOOK", "MODEL", "SERVICE", "IMAGE_REPOSITORY", "CORTEX_SEARCH_SERVICE",
        "SEMANTIC_VIEW", "CONTACT", "DATASET", "GIT_REPOSITORY"));

    /** The kinds recorded under a name inside a database: the schema-level ones and schemas. */
    private static final Set<String> MEMBERS = withAll(SCHEMA_LEVEL, "SCHEMA");

    /** Every kind recorded under a database's name or a name inside it. */
    private static final Set<String> IN_DATABASE = withAll(MEMBERS, "DATABASE", "DATABASE_ROLE");

    /** The two relation kinds: a new table or view takes the name of either. */
    private static final Set<String> RELATIONS = new HashSet<>(Arrays.asList("TABLE", "VIEW"));

    /** The part a set-aside name carries after the object's own: no object's name has four parts. */
    private static final String SET_ASIDE = "#COPY_GRANTS";

    private GrantNames() {
    }

    /**
     * The name a privilege on an object is recorded and checked under.
     *
     * @param catalog    the catalog, for the session's context
     * @param objectType the object's kind, as grants record it
     * @param written    the name as a statement wrote it
     * @return the name in full, or as written for an account-level object or a name that resolves to nothing
     */
    public static String of(final Catalog catalog, final String objectType, final String written) {
        if (objectType == null || written == null) {
            return written;
        }
        final String kind = kindOf(objectType);
        try {
            if ("DATABASE".equals(kind)) {
                final Database database = catalog.findDatabase(written);
                return database != null ? database.getName() : written;
            }
            if ("SCHEMA".equals(kind)) {
                final Schema schema = catalog.resolveSchema(written);
                return QualifiedName.join(schema.getDatabaseName(), schema.getName());
            }
            if (SCHEMA_LEVEL.contains(kind)) {
                return catalog.resolveOwningSchema(written).qualifiedName(QualifiedName.parse(written).last());
            }
        } catch (final RuntimeException unresolvable) {
            // A name nothing resolves keeps its spelling: the statement that wrote it reports the miss itself.
            return written;
        }
        return written;
    }

    /**
     * The schema WITH MANAGED ACCESS an object lives in, whose owner alone among object owners decides the grants on
     * it: null for a kind no schema holds, an object in a schema without managed access, or a name that resolves to
     * nothing.
     *
     * @param catalog    the catalog, for the session's context
     * @param objectType the object's kind, as grants record it
     * @param name       the object's name as written or recorded, a routine's with its argument types or without
     * @return the managed schema, or null
     */
    public static Schema managedSchema(final Catalog catalog, final String objectType, final String name) {
        if (objectType == null || name == null || !SCHEMA_LEVEL.contains(kindOf(objectType))) {
            return null;
        }
        final int signature = signatureStart(name);
        try {
            final Schema schema = catalog.resolveOwningSchema(signature < 0 ? name : name.substring(0, signature));
            return schema != null && schema.isManagedAccess() ? schema : null;
        } catch (final RuntimeException unresolvable) {
            return null;
        }
    }

    /**
     * A recorded name as SHOW GRANTS spells it: each part quoted where it has to be, {@code DB.S."t"} for a table
     * created as {@code "t"}, and a routine's signature kept as it is after its name, {@code DB.S."a"()}
     * (live-verified).
     *
     * @param objectType the object's kind, as grants record it
     * @param recorded   the name the grant is recorded under
     * @return the name as a listing shows it
     */
    public static String spelled(final String objectType, final String recorded) {
        if (recorded == null || objectType == null) {
            return recorded;
        }
        final String kind = kindOf(objectType);
        final int signature = "FUNCTION".equals(kind) || "PROCEDURE".equals(kind) ? signatureStart(recorded) : -1;
        return signature < 0 ? SqlIdentifiers.spellAlreadyCanonicalPath(recorded)
            : SqlIdentifiers.spellAlreadyCanonicalPath(recorded.substring(0, signature)) + recorded.substring(signature);
    }

    /**
     * Whether an object of the kind carries the recorded name now. A dropped object's privileges stay recorded, for
     * an UNDROP to find them and for {@link #created} to take back, and are not listed meanwhile: SHOW GRANTS TO
     * names no table, sequence, schema or routine that is gone (live-verified). A kind this cannot look up is
     * taken to be there.
     *
     * @param catalog    the catalog
     * @param objectType the object's kind, as grants record it
     * @param recorded   the name the grant is recorded under
     * @return whether the object is there
     */
    public static boolean present(final Catalog catalog, final String objectType, final String recorded) {
        final String kind = kindOf(objectType);
        final boolean routine = "FUNCTION".equals(kind) || "PROCEDURE".equals(kind);
        if (!routine && !RELATIONS.contains(kind) && !"SEQUENCE".equals(kind) && !"SCHEMA".equals(kind)
                && !"DATABASE".equals(kind)) {
            return true;
        }
        final int signature = routine ? signatureStart(recorded) : -1;
        if (routine && signature < 0) {
            return true;
        }
        final String[] name = parts(routine ? recorded.substring(0, signature) : recorded);
        final Database database = catalog.findDatabase(name[0]);
        if ("DATABASE".equals(kind)) {
            return name.length != 1 || database != null;
        }
        if (name.length != ("SCHEMA".equals(kind) ? 2 : 3)) {
            return true;
        }
        if (database == null || !database.hasSchemaExact(name[1])) {
            return false;
        }
        if ("SCHEMA".equals(kind)) {
            return true;
        }
        final Schema schema = database.getSchema(name[1]);
        final String object = name[2];
        if ("SEQUENCE".equals(kind)) {
            return schema.hasSequenceExact(object);
        }
        if ("FUNCTION".equals(kind)) {
            for (final Function overload : schema.getFunctionOverloads(object)) {
                if (routine(schema, overload.getName(), overload.getParameters()).equals(recorded)) {
                    return true;
                }
            }
            return false;
        }
        if ("PROCEDURE".equals(kind)) {
            for (final Procedure overload : schema.getProcedureOverloads(object)) {
                if (routine(schema, overload.getName(), overload.getParameters()).equals(recorded)) {
                    return true;
                }
            }
            return false;
        }
        return schema.hasTable(object) && schema.getTable(object).getName().equals(object)
            || schema.hasView(object) && schema.getView(object).getName().equals(object)
            || schema.hasMaterializedView(object) || schema.hasDynamicTable(object);
    }

    /** Where a recorded routine name's signature opens, at the parenthesis its last one closes; -1 for none. */
    private static int signatureStart(final String recorded) {
        if (!recorded.endsWith(")")) {
            return -1;
        }
        int depth = 0;
        for (int i = recorded.length() - 1; i > 0; i--) {
            final char c = recorded.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The name privileges on one routine overload are recorded under: its full name followed by its argument types'
     * families, {@code DB.SCHEMA.NAME(NUMBER, VARCHAR)}, which is how SHOW GRANTS names the overload.
     *
     * @param schema     the routine's schema
     * @param name       the routine's name
     * @param parameters its parameters
     * @return the name
     */
    public static String routine(final Schema schema, final String name, final List<Parameter> parameters) {
        return schema.qualifiedName(name) + RoutineSignatures.signatureText(parameters);
    }

    /**
     * An object renamed or moved: its privileges go to its new name.
     *
     * @param catalog    the catalog
     * @param objectType the object's kind
     * @param fromFull   its full name before
     * @param toFull     its full name now
     */
    public static void renamed(final Catalog catalog, final String objectType, final String fromFull,
                               final String toFull) {
        apply(catalog, new GrantRemap(Collections.singleton(kindOf(objectType)), parts(fromFull), parts(toFull),
            false, false, GrantRemap.EXACT));
    }

    /**
     * Two objects swapped names: each one's privileges go with it.
     *
     * @param catalog    the catalog
     * @param objectType their kind
     * @param aFull      the first one's full name before the swap
     * @param bFull      the second one's
     */
    public static void swapped(final Catalog catalog, final String objectType, final String aFull,
                               final String bFull) {
        final String scratch = QualifiedName.join(withPart(parts(aFull), "#SWAP"));
        renamed(catalog, objectType, aFull, scratch);
        renamed(catalog, objectType, bFull, aFull);
        renamed(catalog, objectType, scratch, bFull);
    }

    /**
     * A database or schema renamed or moved: its own privileges and its members' go to the new name.
     *
     * @param catalog the catalog
     * @param from    its canonical name parts before: {db} or {db, schema}
     * @param to      its parts now
     */
    public static void containerRenamed(final Catalog catalog, final String[] from, final String[] to) {
        apply(catalog, new GrantRemap(IN_DATABASE, from, to, false, false, GrantRemap.ALL));
    }

    /**
     * A database or schema cloned: each member of the clone is granted what the source's member holds, and the clone
     * itself nothing.
     *
     * @param catalog the catalog
     * @param from    the source's canonical name parts: {db} or {db, schema}
     * @param to      the clone's
     */
    public static void containerCloned(final Catalog catalog, final String[] from, final String[] to) {
        apply(catalog, new GrantRemap(MEMBERS, from, to, true, false, GrantRemap.MEMBERS));
    }

    /**
     * COPY GRANTS on a CLONE or a LIKE: the new object is granted what its source holds, OWNERSHIP aside.
     *
     * @param catalog    the catalog
     * @param objectType the objects' kind
     * @param fromFull   the source's full name
     * @param toFull     the new object's
     */
    public static void copied(final Catalog catalog, final String objectType, final String fromFull,
                              final String toFull) {
        apply(catalog, new GrantRemap(Collections.singleton(kindOf(objectType)), parts(fromFull), parts(toFull),
            true, true, GrantRemap.EXACT));
    }

    /**
     * A new object takes a name: the privileges still recorded under it, a dropped object's, are taken back. A new
     * table or view clears both relation kinds, since a check on either reads them alike. A new database also takes
     * back every grant of the dropped database's roles: they stay recorded while it is gone, so UNDROP brings them
     * back, and none of them is a role of the database that now has the name (live-verified). A routine is named here
     * with its signature ({@link #routine}), so a new overload clears only what a dropped overload of the same
     * signature held; a routine named without one is left alone.
     *
     * @param catalog    the catalog
     * @param objectType the new object's kind
     * @param full       its full name
     */
    public static void created(final Catalog catalog, final String objectType, final String full) {
        final String kind = kindOf(objectType);
        if (("FUNCTION".equals(kind) || "PROCEDURE".equals(kind)) && full.indexOf('(') < 0) {
            return;
        }
        final Set<String> kinds = RELATIONS.contains(kind) ? RELATIONS : Collections.singleton(kind);
        final String heldUnder = "DATABASE".equals(kind) ? full + "." : null;
        for (final Role role : roles(catalog)) {
            for (final String purged : kinds) {
                role.purgeGrants(purged, full, false);
            }
            if (heldUnder != null) {
                // A dropped database's roles went with it: the new database's are not granted to who held those.
                for (final String databaseRole : role.getDatabaseRoleGrants().keySet()) {
                    if (databaseRole.startsWith(heldUnder)) {
                        role.revokeDatabaseRole(databaseRole);
                    }
                }
            }
        }
        for (final User user : catalog.getAllUsers()) {
            for (final String purged : kinds) {
                user.purgeGrants(purged, full, false);
            }
            if (heldUnder != null) {
                for (final String databaseRole : user.getDatabaseRoleGrants().keySet()) {
                    if (databaseRole.startsWith(heldUnder)) {
                        user.revokeDatabaseRole(databaseRole);
                    }
                }
            }
        }
    }

    /**
     * CREATE OR REPLACE, before the old object's privileges can be taken back: they are set aside under a name no
     * object has, for {@link #takeBack} to return (COPY GRANTS, or a replacement that failed) or {@link #discard} to
     * drop.
     *
     * @param catalog    the catalog
     * @param objectType the object's kind
     * @param full       its full name
     */
    public static void setAside(final Catalog catalog, final String objectType, final String full) {
        apply(catalog, new GrantRemap(Collections.singleton(kindOf(objectType)), parts(full),
            withPart(parts(full), SET_ASIDE), false, false, GrantRemap.EXACT));
    }

    /**
     * The privileges {@link #setAside} kept, given to the object now under the name; whatever is not given back is
     * dropped.
     *
     * @param catalog         the catalog
     * @param objectType      the object's kind
     * @param full            its full name
     * @param exceptOwnership true for COPY GRANTS, which copies every privilege but OWNERSHIP
     */
    public static void takeBack(final Catalog catalog, final String objectType, final String full,
                                final boolean exceptOwnership) {
        apply(catalog, new GrantRemap(Collections.singleton(kindOf(objectType)), withPart(parts(full), SET_ASIDE),
            parts(full), false, exceptOwnership, GrantRemap.EXACT));
        discard(catalog, objectType, full);
    }

    /**
     * Drops the privileges {@link #setAside} kept: the replacement took the name without COPY GRANTS.
     *
     * @param catalog    the catalog
     * @param objectType the object's kind
     * @param full       its full name
     */
    public static void discard(final Catalog catalog, final String objectType, final String full) {
        final String scratch = QualifiedName.join(withPart(parts(full), SET_ASIDE));
        for (final Role role : roles(catalog)) {
            role.purgeGrants(kindOf(objectType), scratch, false);
        }
        for (final User user : catalog.getAllUsers()) {
            user.purgeGrants(kindOf(objectType), scratch, false);
        }
    }

    /**
     * Completes the names an older snapshot recorded as written. A partial name of a table or view that names
     * exactly one in the catalog takes that one's full name; a schema's name likewise. Any other name keeps its
     * spelling, since the session that wrote it is gone. A routine's privileges recorded without a signature, as
     * every overload once shared them, go to each overload of that name.
     *
     * @param catalog the restored catalog
     */
    public static void qualifyLegacy(final Catalog catalog) {
        final List<String[]> remaps = new ArrayList<>();
        final List<String[]> routines = new ArrayList<>();
        for (final Role role : roles(catalog)) {
            collectLegacy(catalog, role.getAllPrivileges(), remaps);
            collectLegacyRoutines(role.getAllPrivileges(), routines);
        }
        for (final User user : catalog.getAllUsers()) {
            collectLegacy(catalog, user.getAllPrivileges(), remaps);
            collectLegacyRoutines(user.getAllPrivileges(), routines);
        }
        for (final String[] remap : remaps) {
            renamed(catalog, remap[0], remap[1], remap[2]);
        }
        for (final String[] routine : routines) {
            spreadOverOverloads(catalog, routine[0], routine[1]);
        }
    }

    /** The routine privileges recorded without a signature, as {kind, name}, each once. */
    private static void collectLegacyRoutines(final Map<String, ?> privileges, final List<String[]> routines) {
        for (final String key : privileges.keySet()) {
            final int split = key.indexOf(':');
            final String kind = key.substring(0, split);
            final String name = key.substring(split + 1);
            if (("FUNCTION".equals(kind) || "PROCEDURE".equals(kind)) && name.indexOf('(') < 0
                    && parts(name).length == 3 && !containsRoutine(routines, kind, name)) {
                routines.add(new String[] {kind, name});
            }
        }
    }

    private static boolean containsRoutine(final List<String[]> routines, final String kind, final String name) {
        for (final String[] routine : routines) {
            if (routine[0].equals(kind) && routine[1].equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** A routine's signature-less privileges given to every overload of its name, then taken off the bare name. */
    private static void spreadOverOverloads(final Catalog catalog, final String kind, final String full) {
        final String[] name = parts(full);
        final Database database = catalog.findDatabase(name[0]);
        if (database == null || !database.hasSchema(name[1])) {
            return;
        }
        final Schema schema = database.getSchema(name[1]);
        final List<String> overloads = new ArrayList<>();
        if ("FUNCTION".equals(kind)) {
            for (final Function overload : schema.getFunctionOverloads(name[2])) {
                overloads.add(routine(schema, overload.getName(), overload.getParameters()));
            }
        } else {
            for (final Procedure overload : schema.getProcedureOverloads(name[2])) {
                overloads.add(routine(schema, overload.getName(), overload.getParameters()));
            }
        }
        if (overloads.isEmpty()) {
            return;
        }
        for (final String overload : overloads) {
            apply(catalog, new GrantRemap(Collections.singleton(kind), name, parts(overload), true, false,
                GrantRemap.EXACT));
        }
        for (final Role role : roles(catalog)) {
            role.purgeGrants(kind, full, false);
        }
        for (final User user : catalog.getAllUsers()) {
            user.purgeGrants(kind, full, false);
        }
    }

    private static void collectLegacy(final Catalog catalog, final Map<String, ?> privileges,
                                      final List<String[]> remaps) {
        for (final String key : privileges.keySet()) {
            final int split = key.indexOf(':');
            final String kind = key.substring(0, split);
            final String name = key.substring(split + 1);
            final String full = uniqueFullName(catalog, kind, name);
            if (full != null && !full.equalsIgnoreCase(name)) {
                remaps.add(new String[] {kind, name, full});
            }
        }
    }

    /** The one table, view or schema a partial recorded name can mean, in full, or null. */
    private static String uniqueFullName(final Catalog catalog, final String kind, final String name) {
        final String[] parts = parts(name);
        final boolean relation = RELATIONS.contains(kind);
        if ((relation && parts.length >= 3) || ("SCHEMA".equals(kind) && parts.length >= 2)
                || (!relation && !"SCHEMA".equals(kind))) {
            return null;
        }
        String found = null;
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                final String candidate;
                if ("SCHEMA".equals(kind)) {
                    candidate = schema.getName().equalsIgnoreCase(parts[0])
                        ? QualifiedName.join(database.getName(), schema.getName()) : null;
                } else {
                    final boolean inSchema = parts.length == 1 || schema.getName().equalsIgnoreCase(parts[0]);
                    final String member = parts[parts.length - 1];
                    candidate = inSchema && (schema.hasTable(member) || schema.hasView(member))
                        ? schema.qualifiedName(member) : null;
                }
                if (candidate != null) {
                    if (found != null) {
                        return null;
                    }
                    found = candidate;
                }
            }
        }
        return found;
    }

    private static void apply(final Catalog catalog, final GrantRemap remap) {
        for (final Role role : roles(catalog)) {
            role.remapGrants(remap);
        }
        for (final User user : catalog.getAllUsers()) {
            user.remapGrants(remap);
        }
    }

    /** Every account role and every database role: everything that holds privileges besides users. */
    private static List<Role> roles(final Catalog catalog) {
        final List<Role> roles = new ArrayList<>(catalog.getAllRoles());
        for (final Database database : catalog.getAllDatabases()) {
            roles.addAll(database.getDatabaseRoles());
        }
        return roles;
    }

    /** A kind as grants record it: upper-case, its words joined by underscores. */
    private static String kindOf(final String objectType) {
        return objectType.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String[] parts(final String name) {
        return QualifiedName.parse(name).parts();
    }

    private static String[] withPart(final String[] parts, final String extra) {
        final String[] longer = Arrays.copyOf(parts, parts.length + 1);
        longer[parts.length] = extra;
        return longer;
    }

    private static Set<String> withAll(final Set<String> kinds, final String... more) {
        final Set<String> all = new HashSet<>(kinds);
        all.addAll(Arrays.asList(more));
        return all;
    }
}
