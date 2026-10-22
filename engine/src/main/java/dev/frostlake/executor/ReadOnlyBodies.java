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
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.metastore.model.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether a request whose own text only reads (see {@link ReadOnlyRequest}) reaches a write through a body one of
 * its names stands for: a SQL function's, a SQL procedure's called in a FROM clause, a view's or a materialized
 * view's, and the masking and row access policies' of a table or view so named — each body followed through the
 * names it holds in turn. A body reaches a write as a request's text does: a NEXTVAL, a TO_QUERY, a system
 * function that acts on the server. A procedure written in another language may run anything, so naming one
 * reaches a write.
 *
 * <p>Every database and schema is searched for a name, so no resolution the request makes is missed: an object
 * of another schema that carries the name only costs the request the shared lock. The catalog is read under the
 * engine's shared lock, which holds it still.
 */
public final class ReadOnlyBodies {

    /** The texts each name stands for, by the name as the catalog keeps it. */
    private final Map<String, List<String>> bodies = new HashMap<>();

    /** The names of the procedures written in another language than SQL. */
    private final Set<String> foreign = new HashSet<>();

    private ReadOnlyBodies(final Catalog catalog) {
        final Map<String, List<String>> policies = new HashMap<>();
        final List<Table> tables = new ArrayList<>();
        final List<View> views = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                read(schema, policies, tables, views);
            }
        }
        if (policies.isEmpty()) {
            return;
        }
        for (final Table table : tables) {
            guarded(policies, table.getName(), table.getColumns(),
                table.hasRowAccessPolicy() ? table.getRowAccessPolicyName() : null);
        }
        for (final View view : views) {
            guarded(policies, view.getName(), view.getResolvedColumns(),
                view.hasRowAccessPolicy() ? view.getRowAccessPolicyName() : null);
        }
    }

    /**
     * Whether a request judged to read by its own text reaches a write through the bodies its names stand for.
     * A catalog that cannot be read through is taken to write, so the request runs alone.
     *
     * @param catalog the catalog, held still by the engine's shared lock
     * @param sql     the request's text
     * @return true when a body the text reaches may write
     */
    public static boolean reachWrite(final Catalog catalog, final String sql) {
        try {
            return new ReadOnlyBodies(catalog).reaches(sql);
        } catch (final RuntimeException unreadable) {
            return true;
        }
    }

    private boolean reaches(final String sql) {
        if (bodies.isEmpty() && foreign.isEmpty()) {
            return false;
        }
        final Set<String> followed = new HashSet<>();
        final Deque<String> texts = new ArrayDeque<>();
        texts.add(sql);
        boolean own = true;
        while (!texts.isEmpty()) {
            final String text = texts.poll();
            // The request's own text was judged already; each body it reaches is judged as it is reached.
            if (!own && ReadOnlyRequest.tokenRefusal(text) != null) {
                return true;
            }
            own = false;
            for (final String name : ReadOnlyRequest.names(text)) {
                if (!followed.add(name)) {
                    continue;
                }
                if (foreign.contains(name)) {
                    return true;
                }
                final List<String> held = bodies.get(name);
                if (held != null) {
                    texts.addAll(held);
                }
            }
        }
        return false;
    }

    /** Takes in a schema's routines, views and policies, and the tables and views a policy may guard. */
    private void read(final Schema schema, final Map<String, List<String>> policies, final List<Table> tables,
                      final List<View> views) {
        for (final Function function : schema.allFunctions()) {
            if (function.getUdfLanguage() == UdfLanguage.SQL && function.getBody() != null) {
                add(bodies, function.getName(), function.getBody());
            }
        }
        for (final Procedure procedure : schema.allProcedures()) {
            if (procedure.getUdfLanguage() != UdfLanguage.SQL) {
                foreign.add(procedure.getName());
            } else if (procedure.getBody() != null) {
                add(bodies, procedure.getName(), procedure.getBody());
            }
        }
        for (final View view : schema.allViews()) {
            if (view.getDefinition() != null) {
                add(bodies, view.getName(), view.getDefinition());
            }
            views.add(view);
        }
        for (final MaterializedView view : schema.getMaterializedViews()) {
            if (view.getDefinition() != null) {
                add(bodies, view.getName(), view.getDefinition());
            }
        }
        for (final MaskingPolicy policy : schema.getMaskingPolicies()) {
            if (policy.getBody() != null) {
                add(policies, policy.getName().toUpperCase(Locale.ROOT), policy.getBody());
            }
        }
        for (final RowAccessPolicy policy : schema.getRowAccessPolicies()) {
            if (policy.getBody() != null) {
                add(policies, policy.getName().toUpperCase(Locale.ROOT), policy.getBody());
            }
        }
        tables.addAll(schema.allTables());
    }

    /** Takes in the bodies of the policies guarding a table's or a view's columns and rows, under its name. */
    private void guarded(final Map<String, List<String>> policies, final String holder,
                         final List<TableColumn> columns, final String rowPolicy) {
        if (columns != null) {
            for (final TableColumn column : columns) {
                if (column.hasMaskingPolicy()) {
                    addPolicy(policies, holder, column.getMaskingPolicyName());
                }
            }
        }
        if (rowPolicy != null) {
            addPolicy(policies, holder, rowPolicy);
        }
    }

    /** The bodies of the policies a stored policy name may stand for, under the name of the object it guards. */
    private void addPolicy(final Map<String, List<String>> policies, final String holder, final String stored) {
        final String[] parts = SqlIdentifiers.canonicalTextParts(stored);
        final List<String> held = policies.get(parts[parts.length - 1].toUpperCase(Locale.ROOT));
        if (held != null) {
            for (final String body : held) {
                add(bodies, holder, body);
            }
        }
    }

    private static <T> void add(final Map<String, List<T>> into, final String name, final T value) {
        List<T> held = into.get(name);
        if (held == null) {
            held = new ArrayList<>();
            into.put(name, held);
        }
        held.add(value);
    }
}
