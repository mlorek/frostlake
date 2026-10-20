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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The rows of INFORMATION_SCHEMA's three privilege views, read from the grants the engine keeps, as the account
 * lists them (live-verified):
 *
 * <pre>
 *   TABLE_PRIVILEGES    one row per privilege held on a table, a view, a materialized view or a dynamic table
 *                       of the database, the owner's OWNERSHIP included; WITH_HIERARCHY is NO
 *   OBJECT_PRIVILEGES   the same relations under OBJECT_TYPE TABLE, the database's schemas (OBJECT_SCHEMA null),
 *                       its functions and procedures named with their arguments, F(X NUMBER, Y VARCHAR), its
 *                       sequences, streams and tasks — and every database, each with OBJECT_CATALOG null. A
 *                       stage, a file format, a tag and a policy are not listed.
 *   USAGE_PRIVILEGES    the database's sequences alone: the owner holds USAGE there, grantable, and every
 *                       granted USAGE follows
 * </pre>
 *
 * GRANTED_TO tells ROLE or DATABASE_ROLE, IS_GRANTABLE tells YES for the owner and for a privilege granted WITH
 * GRANT OPTION, and a future grant is no row. An object is listed only where the rights the statement runs with
 * reach it - they own it or hold a privilege on it (documented).
 */
final class PrivilegeViews {

    private static final String YES = "YES";
    private static final String NO = "NO";
    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    private final QueryExecutor executor;
    private final Catalog catalog;

    PrivilegeViews(final QueryExecutor executor) {
        this.executor = executor;
        this.catalog = executor.getCatalog();
    }

    /**
     * TABLE_PRIVILEGES' rows: GRANTOR, GRANTEE, GRANTED_TO, TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME,
     * PRIVILEGE_TYPE, IS_GRANTABLE, WITH_HIERARCHY, CREATED.
     *
     * @param databaseName the database the view belongs to
     * @return the rows
     */
    List<Row> tablePrivileges(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        final Database database = catalog.getDatabase(databaseName);
        for (final Schema schema : database.getAllSchemas()) {
            for (final GrantedObject relation : relations(database, schema)) {
                for (final Row grant : grantsOn(relation)) {
                    rows.add(new Row(Arrays.asList(grant.getValue(7), grant.getValue(5), grant.getValue(4),
                        database.getName(), schema.getName(), relation.getBareName(), grant.getValue(1),
                        grantable(grant), NO, grant.getValue(0))));
                }
            }
        }
        return rows;
    }

    /**
     * OBJECT_PRIVILEGES' rows: GRANTOR, GRANTEE, GRANTED_TO, OBJECT_CATALOG, OBJECT_SCHEMA, OBJECT_NAME,
     * OBJECT_TYPE, PRIVILEGE_TYPE, IS_GRANTABLE, CREATED.
     *
     * @param databaseName the database the view belongs to
     * @return the rows
     */
    List<Row> objectPrivileges(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        for (final Database each : catalog.getAllDatabases()) {
            addObjectRows(rows, executor.grantedObject("DATABASE", new String[] {each.getName()}, null, null),
                null, null, each.getName(), "DATABASE");
        }
        final Database database = catalog.getDatabase(databaseName);
        for (final Schema schema : database.getAllSchemas()) {
            if (INFORMATION_SCHEMA.equalsIgnoreCase(schema.getName())) {
                continue;
            }
            final String[] schemaName = {database.getName(), schema.getName()};
            addObjectRows(rows, executor.grantedObject("SCHEMA", schemaName, null, null), database.getName(), null,
                schema.getName(), "SCHEMA");
            for (final Function function : schema.getFunctions()) {
                addObjectRows(rows, QueryExecutor.functionGrant(schema, function), database.getName(),
                    schema.getName(), function.getName() + arguments(function.getParameters()), "FUNCTION");
            }
            for (final Procedure procedure : schema.getProcedures()) {
                addObjectRows(rows, QueryExecutor.procedureGrant(schema, procedure), database.getName(),
                    schema.getName(), procedure.getName() + arguments(procedure.getParameters()), "PROCEDURE");
            }
            for (final Sequence sequence : schema.getSequences()) {
                addObjectRows(rows, member("SEQUENCE", database, schema, sequence.getName()), database.getName(),
                    schema.getName(), sequence.getName(), "SEQUENCE");
            }
            for (final Stream stream : schema.getStreams()) {
                addObjectRows(rows, member("STREAM", database, schema, stream.getName()), database.getName(),
                    schema.getName(), stream.getName(), "STREAM");
            }
            for (final Task task : schema.getTasks()) {
                addObjectRows(rows, member("TASK", database, schema, task.getName()), database.getName(),
                    schema.getName(), task.getName(), "TASK");
            }
            for (final GrantedObject relation : relations(database, schema)) {
                addObjectRows(rows, relation, database.getName(), schema.getName(), relation.getBareName(), "TABLE");
            }
        }
        return rows;
    }

    /**
     * USAGE_PRIVILEGES' rows, in OBJECT_PRIVILEGES' columns: the database's sequences, whose owner holds USAGE.
     *
     * @param databaseName the database the view belongs to
     * @return the rows
     */
    List<Row> usagePrivileges(final String databaseName) {
        final List<Row> rows = new ArrayList<>();
        final Database database = catalog.getDatabase(databaseName);
        for (final Schema schema : database.getAllSchemas()) {
            for (final Sequence sequence : schema.getSequences()) {
                for (final Row grant : grantsOn(member("SEQUENCE", database, schema, sequence.getName()))) {
                    rows.add(new Row(Arrays.asList(grant.getValue(7), grant.getValue(5), grant.getValue(4),
                        database.getName(), schema.getName(), sequence.getName(), "SEQUENCE", "USAGE",
                        grantable(grant), grant.getValue(0))));
                }
            }
        }
        return rows;
    }

    private void addObjectRows(final List<Row> rows, final GrantedObject granted, final String catalogName,
                               final String schemaName, final String objectName, final String objectType) {
        for (final Row grant : grantsOn(granted)) {
            rows.add(new Row(Arrays.asList(grant.getValue(7), grant.getValue(5), grant.getValue(4), catalogName,
                schemaName, objectName, objectType, grant.getValue(1), grantable(grant), grant.getValue(0))));
        }
    }

    /** A schema's relations as their grants name them: tables, views, materialized views and dynamic tables. */
    private List<GrantedObject> relations(final Database database, final Schema schema) {
        final List<GrantedObject> relations = new ArrayList<>();
        for (final Table table : schema.getTables()) {
            relations.add(member("TABLE", database, schema, table.getName()));
        }
        for (final View view : schema.getViews()) {
            relations.add(member("TABLE", database, schema, view.getName()));
        }
        for (final MaterializedView view : schema.getMaterializedViews()) {
            relations.add(new GrantedObject("MATERIALIZED_VIEW", schema.qualifiedName(view.getName()),
                view.getOwner(), view.getCreatedTime(), named(view.getName())));
        }
        for (final DynamicTable table : schema.getDynamicTables()) {
            relations.add(new GrantedObject("DYNAMIC_TABLE", schema.qualifiedName(table.getName()), table.getOwner(),
                table.getCreatedTime(), named(table.getName())));
        }
        return relations;
    }

    /** The one name a relation's row is listed under. */
    private static List<String> named(final String name) {
        final List<String> names = new ArrayList<>();
        names.add(name);
        return names;
    }

    private GrantedObject member(final String kind, final Database database, final Schema schema, final String name) {
        return executor.grantedObject(kind, new String[] {database.getName(), schema.getName(), name}, null, null);
    }

    /** SHOW GRANTS ON's rows for an object: when, privilege, kind, name, granted to, grantee, option, grantor. */
    private List<Row> grantsOn(final GrantedObject granted) {
        if (granted == null || executor.getSecurityManager() != null
                && !executor.getSecurityManager().reaches(granted.getKind(), granted.getName(), granted.getOwner())) {
            return new ArrayList<>();
        }
        // The listing also tells the USAGE each database role holds on its own database, which no one granted;
        // the privilege views tell the grants that were made.
        final List<Row> made = new ArrayList<>();
        for (final Row row : executor.getShowExecutor().showGrantsOnObject(granted.getKind(), granted.getName(),
                granted).getRows()) {
            if (!AccessControlListings.DATABASE_ROLE.equals(row.getValue(4)) || !"".equals(row.getValue(7))) {
                made.add(row);
            }
        }
        return made;
    }

    private static String grantable(final Row grant) {
        return "true".equals(String.valueOf(grant.getValue(6))) ? YES : NO;
    }

    /** A routine's arguments as the view names them: each one's name and its type's own name. */
    private static String arguments(final List<Parameter> parameters) {
        final StringBuilder spelled = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            spelled.append(i > 0 ? ", " : "").append(parameters.get(i).getName().toUpperCase(Locale.ROOT))
                .append(' ').append(parameters.get(i).getDataType().getName());
        }
        return spelled.append(')').toString();
    }
}
