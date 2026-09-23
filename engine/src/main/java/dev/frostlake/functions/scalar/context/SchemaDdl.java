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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.View;
import dev.frostlake.types.SqlTypeNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * GET_DDL's recursive text for a schema and for a database.
 *
 * <p>A schema's text is its CREATE SCHEMA line ({@code create or replace [transient ]schema S[ with managed
 * access][ COMMENT='…'];} and a line break), then every object it holds, each preceded by a line break and
 * rendered exactly as GET_DDL renders it alone. The objects come kind by kind — tags, sequences, the table-like
 * relations (temporary tables first, then tables, event tables, dynamic tables and materialized views together),
 * views, file formats, functions and procedures together, streams, pipes, tasks, every policy together, contacts,
 * Streamlit apps and alerts — and by name within a kind, compared as binary strings; a routine by its name and
 * parameter list together. Stages, secrets, network rules, notebooks and repositories are not recreated. A
 * database's text is its CREATE DATABASE line, then each schema's text but INFORMATION_SCHEMA's, each preceded by
 * a line break, schemas by name.
 */
final class SchemaDdl {

    /** The schema every database carries, left out of the database's text. */
    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    private final GetDdl relations;

    /**
     * @param relations the GET_DDL whose table, view, sequence and routine renderers recreate those members
     */
    SchemaDdl(final GetDdl relations) {
        this.relations = relations;
    }

    /**
     * {@code create or replace database D[ COMMENT='…'];} and a line break, then each schema's text.
     *
     * @param database the database
     * @param names how recreated objects are named
     * @return the DDL text
     */
    String database(final Database database, final DdlNames names) {
        final StringBuilder out = new StringBuilder("create or replace ");
        if (database.isTransientObject()) {
            out.append("transient ");
        }
        out.append("database ").append(SqlIdentifiers.spellCanonicalEscaped(database.getName()));
        if (ObjectDdl.hasText(database.getComment())) {
            out.append(" COMMENT=").append(ObjectDdl.quoted(database.getComment()));
        }
        out.append(";\n");
        final Map<String, Schema> schemas = new TreeMap<String, Schema>();
        for (final Schema schema : database.getAllSchemas()) {
            if (!INFORMATION_SCHEMA.equals(schema.getName())) {
                schemas.put(schema.getName(), schema);
            }
        }
        for (final Schema schema : schemas.values()) {
            out.append('\n').append(schema(schema, names));
        }
        return out.toString();
    }

    /**
     * The schema's CREATE SCHEMA line, then each member's DDL on the lines after it.
     *
     * @param schema the schema
     * @param names how recreated objects are named
     * @return the DDL text
     */
    String schema(final Schema schema, final DdlNames names) {
        final StringBuilder out = new StringBuilder("create or replace ");
        if (schema.isTransientObject()) {
            out.append("transient ");
        }
        out.append("schema ").append(names.schema(schema));
        if (schema.isManagedAccess()) {
            out.append(" with managed access");
        }
        if (ObjectDdl.hasText(schema.getComment())) {
            out.append(" COMMENT=").append(ObjectDdl.quoted(schema.getComment()));
        }
        out.append(";\n");
        if (INFORMATION_SCHEMA.equals(schema.getName())) {
            // Its views are recreated by the account itself: each one stands as an empty entry.
            for (int i = 0; i < schema.getViews().size(); i++) {
                out.append('\n');
            }
            return out.toString();
        }
        for (final String member : members(schema, names)) {
            out.append('\n').append(member);
        }
        return out.toString();
    }

    /** Every member's DDL, in the order the schema's text lists them. */
    private List<String> members(final Schema schema, final DdlNames names) {
        final List<String> out = new ArrayList<String>();

        final Map<String, List<String>> tags = new TreeMap<String, List<String>>();
        for (final Tag tag : schema.getTags()) {
            add(tags, tag.getName(), ObjectDdl.tag(tag, names.object(schema, tag.getName())));
        }
        appendAll(out, tags);

        final Map<String, List<String>> sequences = new TreeMap<String, List<String>>();
        for (final Sequence sequence : schema.getSequences()) {
            add(sequences, sequence.getName(), relations.sequenceDdl(sequence, names.object(schema, sequence.getName())));
        }
        appendAll(out, sequences);

        final Map<String, List<String>> temporary = new TreeMap<String, List<String>>();
        final Map<String, List<String>> tables = new TreeMap<String, List<String>>();
        for (final Table table : schema.getTables()) {
            add(table.isTemporary() ? temporary : tables, table.getName(), relations.tableDdl(table, schema, names));
        }
        for (final Table table : schema.getShadowedTables()) {
            add(tables, table.getName(), relations.tableDdl(table, schema, names));
        }
        for (final DynamicTable table : schema.getDynamicTables()) {
            add(tables, table.getName(), relations.dynamicTableDdl(table, schema, names));
        }
        for (final MaterializedView view : schema.getMaterializedViews()) {
            add(tables, view.getName(), relations.materializedViewDdl(view, schema, names));
        }
        appendAll(out, temporary);
        appendAll(out, tables);

        final Map<String, List<String>> views = new TreeMap<String, List<String>>();
        for (final View view : schema.getViews()) {
            add(views, view.getName(), relations.viewDdl(view, schema, names));
        }
        appendAll(out, views);

        final Map<String, List<String>> formats = new TreeMap<String, List<String>>();
        for (final FileFormat format : schema.getFileFormats()) {
            add(formats, format.getName(), ObjectDdl.fileFormat(format, names.object(schema, format.getName())));
        }
        appendAll(out, formats);

        final Map<String, List<String>> routines = new TreeMap<String, List<String>>();
        for (final Function function : schema.getFunctions()) {
            add(routines, routineKey(function.getName(), function.getParameters()),
                relations.functionDdl(function, schema, names));
        }
        for (final Procedure procedure : schema.getProcedures()) {
            add(routines, routineKey(procedure.getName(), procedure.getParameters()),
                relations.procedureDdl(procedure, schema, names));
        }
        appendAll(out, routines);

        final Map<String, List<String>> streams = new TreeMap<String, List<String>>();
        for (final Stream stream : schema.getStreams()) {
            add(streams, stream.getName(), ObjectDdl.stream(stream, names.object(schema, stream.getName())));
        }
        appendAll(out, streams);

        final Map<String, List<String>> pipes = new TreeMap<String, List<String>>();
        for (final Pipe pipe : schema.getPipes()) {
            add(pipes, pipe.getName(), ObjectDdl.pipe(pipe, names.object(schema, pipe.getName())));
        }
        appendAll(out, pipes);

        final Map<String, List<String>> tasks = new TreeMap<String, List<String>>();
        for (final Task task : schema.getTasks()) {
            add(tasks, task.getName(), ObjectDdl.task(task, schema, names.object(schema, task.getName())));
        }
        appendAll(out, tasks);

        appendAll(out, policies(schema, names));

        final Map<String, List<String>> contacts = new TreeMap<String, List<String>>();
        for (final Contact contact : schema.getContacts()) {
            add(contacts, contact.getName(), ObjectDdl.contact(contact, names.object(schema, contact.getName())));
        }
        appendAll(out, contacts);

        final Map<String, List<String>> streamlits = new TreeMap<String, List<String>>();
        for (final AppObject app : schema.getAppObjects().all(AppObjectKind.STREAMLIT)) {
            add(streamlits, app.getName(), ObjectDdl.streamlit(app, names.object(schema, app.getName())));
        }
        appendAll(out, streamlits);

        final Map<String, List<String>> alerts = new TreeMap<String, List<String>>();
        for (final Alert alert : schema.getAlerts()) {
            add(alerts, alert.getName(), ObjectDdl.alert(alert, names.object(schema, alert.getName())));
        }
        appendAll(out, alerts);
        return out;
    }

    /** Every policy the schema holds, whatever its kind, by name. */
    private Map<String, List<String>> policies(final Schema schema, final DdlNames names) {
        final Map<String, List<String>> policies = new TreeMap<String, List<String>>();
        for (final MaskingPolicy policy : schema.getMaskingPolicies()) {
            add(policies, policy.getName(), ObjectDdl.maskingPolicy(policy, names.object(schema, policy.getName())));
        }
        for (final RowAccessPolicy policy : schema.getRowAccessPolicies()) {
            add(policies, policy.getName(),
                ObjectDdl.rowAccessPolicy(policy, names.object(schema, policy.getName())));
        }
        for (final AggregationPolicy policy : schema.getAggregationPolicies()) {
            add(policies, policy.getName(),
                ObjectDdl.aggregationPolicy(policy, names.object(schema, policy.getName())));
        }
        for (final ProjectionPolicy policy : schema.getProjectionPolicies()) {
            add(policies, policy.getName(),
                ObjectDdl.projectionPolicy(policy, names.object(schema, policy.getName())));
        }
        for (final JoinPolicy policy : schema.getJoinPolicies()) {
            add(policies, policy.getName(), ObjectDdl.joinPolicy(policy, names.object(schema, policy.getName())));
        }
        for (final SecurityObject policy : schema.getSecurityObjects().list(SecurityObjectKind.PASSWORD_POLICY)) {
            add(policies, policy.getName(),
                ObjectDdl.passwordPolicy(policy, names.object(schema, policy.getName())));
        }
        return policies;
    }

    /**
     * A routine's sort key: its name directly followed by its parameter list as {@code (A NUMBER(38,0), B VARCHAR)} —
     * names unquoted, types as the routine's DDL spells them — compared whole, so overloads follow one another by
     * that list, an empty list first, and a longer name comes before a name it extends where its next character
     * sorts before the parenthesis: {@code F$X()} before {@code F()}, {@code "my fn"()} before {@code "my"()}.
     */
    private static String routineKey(final String name, final List<Parameter> parameters) {
        final StringBuilder key = new StringBuilder(name).append('(');
        for (int i = 0; parameters != null && i < parameters.size(); i++) {
            key.append(i > 0 ? ", " : "").append(parameters.get(i).getName()).append(' ')
                .append(SqlTypeNames.routineType(parameters.get(i).getDataType()));
        }
        return key.append(')').toString();
    }

    private static void add(final Map<String, List<String>> group, final String key, final String ddl) {
        List<String> texts = group.get(key);
        if (texts == null) {
            texts = new ArrayList<String>();
            group.put(key, texts);
        }
        texts.add(ddl);
    }

    private static void appendAll(final List<String> out, final Map<String, List<String>> group) {
        for (final List<String> texts : group.values()) {
            out.addAll(texts);
        }
    }
}
