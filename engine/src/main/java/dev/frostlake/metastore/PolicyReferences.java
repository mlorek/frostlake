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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The policy names tables, columns and views record for their attachments, and what keeps each one naming the
 * policy it was attached to.
 *
 * <p>An attachment records its policy's FULL name, database.schema.policy, resolved against the session when the
 * policy is attached. A query resolves that name exactly and never against the session's schema. A table read from
 * another schema meets the same policies it meets from its own. A name that resolves to no policy refuses the query
 * instead of leaving the data unprotected.
 *
 * <p>The account binds an attachment to the policy itself rather than to its name. A database, schema or policy
 * that is renamed or moved therefore takes its attachments along, and the names that pointed at it are rewritten
 * to its new name. A clone of a database or schema maps its attachments to the clone's own copies of the policies
 * that lived inside it, and keeps an attachment to a policy elsewhere as it was. Snowflake's documentation: "the
 * cloned table or view is mapped to the corresponding cloned policy (in the target schema/database)", while a
 * foreign reference "retains the foreign reference".
 */
public final class PolicyReferences {

    private PolicyReferences() {
    }

    /**
     * Re-points every attachment in {@code scope} that names something under {@code from}, so that it names the
     * same thing under {@code to}. A database or a schema covers the attachments of every kind; a single policy
     * covers only those of its own kind.
     *
     * @param scope the schemas whose tables and views hold the attachments
     * @param kind  the kind of the one policy that moved, or null when a database or schema did
     * @param from  what moved, in canonical parts: {db}, {db, schema} or {db, schema, policy}
     * @param to    what it is called now, in as many parts
     */
    public static void repoint(final List<Schema> scope, final PolicyKind kind, final String[] from,
                               final String[] to) {
        for (final Schema schema : scope) {
            for (final Table table : relationsOf(schema)) {
                for (final TableColumn column : table.getColumns()) {
                    if (covers(kind, PolicyKind.MASKING)) {
                        column.setMaskingPolicyName(repointed(column.getMaskingPolicyName(), from, to));
                    }
                    if (covers(kind, PolicyKind.PROJECTION)) {
                        column.setProjectionPolicyName(repointed(column.getProjectionPolicyName(), from, to));
                    }
                }
                if (covers(kind, PolicyKind.ROW_ACCESS)) {
                    table.setRowAccessPolicyName(repointed(table.getRowAccessPolicyName(), from, to));
                }
                if (covers(kind, PolicyKind.AGGREGATION)) {
                    table.setAggregationPolicyName(repointed(table.getAggregationPolicyName(), from, to));
                }
                if (covers(kind, PolicyKind.JOIN)) {
                    table.setJoinPolicyName(repointed(table.getJoinPolicyName(), from, to));
                }
            }
            // A view's attachments go along as a table's do (live-verified: a schema's clone names its own copies of
            // the policies a view of it carries).
            for (final View view : schema.getViews()) {
                if (covers(kind, PolicyKind.ROW_ACCESS)) {
                    view.setRowAccessPolicyName(repointed(view.getRowAccessPolicyName(), from, to));
                }
                if (covers(kind, PolicyKind.AGGREGATION)) {
                    view.setAggregationPolicyName(repointed(view.getAggregationPolicyName(), from, to));
                }
                if (covers(kind, PolicyKind.JOIN)) {
                    view.setJoinPolicyName(repointed(view.getJoinPolicyName(), from, to));
                }
                if (view.hasResolvedColumns()) {
                    for (final TableColumn column : view.getResolvedColumns()) {
                        if (covers(kind, PolicyKind.MASKING)) {
                            column.setMaskingPolicyName(repointed(column.getMaskingPolicyName(), from, to));
                        }
                        if (covers(kind, PolicyKind.PROJECTION)) {
                            column.setProjectionPolicyName(repointed(column.getProjectionPolicyName(), from, to));
                        }
                    }
                }
            }
        }
    }

    /**
     * Completes every attachment in {@code scope} that records less than a full name. A snapshot written before
     * attachments were recorded in full holds such names. The session that attached the policy is gone, so the
     * name is read where the relation holding it lives, as GET_DDL reads it.
     *
     * @param scope the schemas whose tables and views hold the attachments
     */
    public static void qualify(final List<Schema> scope) {
        for (final Schema schema : scope) {
            for (final Table table : relationsOf(schema)) {
                for (final TableColumn column : table.getColumns()) {
                    column.setMaskingPolicyName(completed(column.getMaskingPolicyName(), schema));
                    column.setProjectionPolicyName(completed(column.getProjectionPolicyName(), schema));
                }
                table.setRowAccessPolicyName(completed(table.getRowAccessPolicyName(), schema));
                table.setAggregationPolicyName(completed(table.getAggregationPolicyName(), schema));
                table.setJoinPolicyName(completed(table.getJoinPolicyName(), schema));
            }
            for (final View view : schema.getViews()) {
                view.setRowAccessPolicyName(completed(view.getRowAccessPolicyName(), schema));
            }
        }
    }

    /**
     * Whether an attachment names one policy: its full name, completed where the relation holding it lives, is the
     * policy's own - in the policy's schema, by exactly its name. A namesake in another schema, or a quoted name
     * differing only in case, is another policy, which the attachment does not hold (live-verified).
     *
     * @param catalog    the catalog
     * @param attached   the name the relation records, or null for none
     * @param where      the schema of the relation holding the attachment
     * @param home       the policy's schema
     * @param policyName the policy's own name
     * @return whether the attachment is to that policy
     */
    public static boolean names(final Catalog catalog, final String attached, final Schema where, final Schema home,
                                final String policyName) {
        if (attached == null || home == null) {
            return false;
        }
        final String[] name = QualifiedName.parse(completed(attached, where)).parts();
        return name.length == 3 && name[2].equals(policyName) && home(catalog, name) == home;
    }

    /**
     * The masking policy an attachment names.
     *
     * @param catalog  the catalog
     * @param attached the name the column records
     * @param owner    the table holding the column
     * @return the policy; a name that resolves to none refuses the statement
     */
    public static MaskingPolicy maskingPolicy(final Catalog catalog, final String attached, final Table owner) {
        final String[] name = fullName(catalog, attached, owner);
        final Schema home = home(catalog, name);
        final MaskingPolicy policy = home != null ? home.getMaskingPolicy(name[2]) : null;
        if (policy == null) {
            throw dangling("Masking policy", name);
        }
        return policy;
    }

    /**
     * The row access policy an attachment names.
     *
     * @param catalog  the catalog
     * @param attached the name the table or view records
     * @param owner    the table holding it, or the relation standing for the view
     * @return the policy; a name that resolves to none refuses the statement
     */
    public static RowAccessPolicy rowAccessPolicy(final Catalog catalog, final String attached, final Table owner) {
        final String[] name = fullName(catalog, attached, owner);
        final Schema home = home(catalog, name);
        final RowAccessPolicy policy = home != null ? home.getRowAccessPolicy(name[2]) : null;
        if (policy == null) {
            throw dangling("Row access policy", name);
        }
        return policy;
    }

    /**
     * The projection policy an attachment names.
     *
     * @param catalog  the catalog
     * @param attached the name the column records
     * @param owner    the table holding the column
     * @return the policy; a name that resolves to none refuses the statement
     */
    public static ProjectionPolicy projectionPolicy(final Catalog catalog, final String attached, final Table owner) {
        final String[] name = fullName(catalog, attached, owner);
        final Schema home = home(catalog, name);
        final ProjectionPolicy policy = home != null ? home.getProjectionPolicy(name[2]) : null;
        if (policy == null) {
            throw dangling("Projection policy", name);
        }
        return policy;
    }

    /**
     * The aggregation policy an attachment names.
     *
     * @param catalog  the catalog
     * @param attached the name the table records
     * @param owner    the table
     * @return the policy; a name that resolves to none refuses the statement
     */
    public static AggregationPolicy aggregationPolicy(final Catalog catalog, final String attached,
                                                      final Table owner) {
        final String[] name = fullName(catalog, attached, owner);
        final Schema home = home(catalog, name);
        final AggregationPolicy policy = home != null ? home.getAggregationPolicy(name[2]) : null;
        if (policy == null) {
            throw dangling("Aggregation policy", name);
        }
        return policy;
    }

    /**
     * The join policy an attachment names.
     *
     * @param catalog  the catalog
     * @param attached the name the table records
     * @param owner    the table
     * @return the policy; a name that resolves to none refuses the statement
     */
    public static JoinPolicy joinPolicy(final Catalog catalog, final String attached, final Table owner) {
        final String[] name = fullName(catalog, attached, owner);
        final Schema home = home(catalog, name);
        final JoinPolicy policy = home != null ? home.getJoinPolicy(name[2]) : null;
        if (policy == null) {
            throw dangling("Join policy", name);
        }
        return policy;
    }

    /** Whether a rewrite for {@code moved} reaches the attachments of kind {@code slot}. */
    private static boolean covers(final PolicyKind moved, final PolicyKind slot) {
        return moved == null || moved == slot;
    }

    /** A schema's tables, the permanent ones a temporary table hides included. */
    private static List<Table> relationsOf(final Schema schema) {
        final List<Table> tables = new ArrayList<>(schema.getTables());
        tables.addAll(schema.getShadowedTables());
        return tables;
    }

    /** The attachment re-pointed when its full name lies under {@code from}, else as it was. */
    private static String repointed(final String attached, final String[] from, final String[] to) {
        if (attached == null) {
            return null;
        }
        final String[] parts = QualifiedName.parse(attached).parts();
        if (parts.length != 3) {
            return attached;
        }
        for (int i = 0; i < from.length; i++) {
            if (!parts[i].equals(from[i])) {
                return attached;
            }
        }
        System.arraycopy(to, 0, parts, 0, to.length);
        return QualifiedName.join(parts);
    }

    /** The attachment in full, a shorter name read in the schema of the relation holding it. */
    private static String completed(final String attached, final Schema home) {
        if (attached == null || home.getDatabaseName() == null) {
            return attached;
        }
        final String[] parts = QualifiedName.parse(attached).parts();
        if (parts.length == 1) {
            return home.qualifiedName(parts[0]);
        }
        return parts.length == 2 ? QualifiedName.join(home.getDatabaseName(), parts[0], parts[1]) : attached;
    }

    /**
     * An attachment's name in full. A shorter one, which only a path not yet recording names in full could have
     * left, is read where the relation holding it lives. A relation that names no home (the stand-in for a view
     * that no query has placed) falls back to the session's schema, the only context there is.
     */
    private static String[] fullName(final Catalog catalog, final String attached, final Table owner) {
        final String[] parts = QualifiedName.parse(attached).parts();
        if (parts.length >= 3) {
            return parts;
        }
        final String[] where = owner != null && owner.getQualifiedName() != null
            ? QualifiedName.parse(owner.getQualifiedName()).parts()
            : new String[] {catalog.getCurrentDatabase(), catalog.getCurrentSchema()};
        if (where.length < 2 || where[0] == null || where[1] == null) {
            return parts;
        }
        return parts.length == 2
            ? new String[] {where[0], parts[0], parts[1]}
            : new String[] {where[0], where[1], parts[0]};
    }

    /** The schema a full name points into, or null when its database or schema is gone. */
    private static Schema home(final Catalog catalog, final String[] name) {
        if (name.length != 3) {
            return null;
        }
        final Database database = catalog.findDatabase(name[0]);
        return database != null && database.hasSchema(name[1]) ? database.getSchema(name[1]) : null;
    }

    /**
     * The refusal for an attachment whose policy is gone, as when its database or schema was dropped. The
     * sentence is the one an attaching statement gives for a policy it cannot find. Snowflake's documentation
     * gives no wording for a query that meets a dangling attachment.
     */
    private static RuntimeException dangling(final String kind, final String[] name) {
        return new RuntimeException(SqlCompilationError.doesNotExist(kind, QualifiedName.join(name)));
    }
}
