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

import dev.frostlake.executor.FileFormatSurfaces;
import dev.frostlake.executor.FormatProperty;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SecurityPropertySpec;
import dev.frostlake.metastore.model.SecurityPropertySpecs;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;

/**
 * GET_DDL's text for the schema objects that are neither relations nor routines: tags, file formats, streams,
 * pipes, tasks, alerts, policies, contacts and Streamlit apps. Each renders the statement a real account prints for
 * it — alone, and verbatim inside a schema's or a database's DDL — its own name spelled by the caller's
 * {@link DdlNames}. What the account leaves out is left out here too: a stream's or an alert's COMMENT, a password
 * policy's COMMENT, and a stream's SHOW_INITIAL_ROWS.
 */
final class ObjectDdl {

    /** The task parameters a task's DDL spells, in the order the account prints them, when set on the task. */
    private static final String[] TASK_PARAMETERS = {
        "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE", "SUSPEND_TASK_AFTER_NUM_FAILURES", "USER_TASK_TIMEOUT_MS",
        "TASK_AUTO_RETRY_ATTEMPTS"};

    private ObjectDdl() {
    }

    // ─────────────────────────── TAG ───────────────────────────

    /**
     * {@code create or replace tag T ;}, with {@code  allowed_values  'a' , 'b' } after the name, then
     * {@code COMMENT='c'} and {@code PROPAGATE=… ON_CONFLICT=…}, each of those ending its own line, and the
     * semicolon last. The allowed values and the comment are quoted as {@link #quoted} quotes a text.
     */
    static String tag(final Tag tag, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace tag ").append(name).append(' ');
        if (tag.hasAllowedValues()) {
            sb.append(" allowed_values  ");
            final List<String> values = tag.getAllowedValues();
            for (int i = 0; i < values.size(); i++) {
                sb.append(i > 0 ? " , " : "").append(quoted(values.get(i)));
            }
            sb.append(' ');
        }
        if (hasText(tag.getComment())) {
            sb.append("COMMENT=").append(quoted(tag.getComment())).append('\n');
        }
        if (tag.getPropagate() != null) {
            sb.append("PROPAGATE=").append(tag.getPropagate());
            if (tag.getOnConflict() != null) {
                sb.append(" ON_CONFLICT=").append(tag.getOnConflict());
            }
            sb.append('\n');
        }
        return sb.append(';').toString();
    }

    // ─────────────────────────── FILE FORMAT ───────────────────────────

    /**
     * {@code CREATE OR REPLACE FILE FORMAT F}, then one tab-indented {@code NAME = value} line per property whose
     * value differs from the default DESC FILE FORMAT reports for it, in DESC's order, then the COMMENT on a line
     * of its own and the semicolon on the last. TYPE and COMPRESSION are bare words, integers and booleans bare,
     * every other value a quoted string; NULL_IF is a parenthesized list.
     */
    static String fileFormat(final FileFormat format, final String name) {
        final StringBuilder sb = new StringBuilder("CREATE OR REPLACE FILE FORMAT ").append(name).append('\n');
        for (final FormatProperty property : FileFormatSurfaces.tree(format.getType())) {
            final String line = formatPropertyLine(format, property);
            if (line != null) {
                sb.append('\t').append(property.name).append(" = ").append(line).append('\n');
            }
        }
        if (hasText(format.getComment())) {
            sb.append("COMMENT=").append(quoted(format.getComment())).append('\n');
        }
        return sb.append(';').toString();
    }

    /** A property's value as its line spells it, or null when the value is the one DESC calls the default. */
    private static String formatPropertyLine(final FileFormat format, final FormatProperty property) {
        if ("TYPE".equals(property.name)) {
            return "CSV".equals(format.getType()) ? null : format.getType();
        }
        if ("NULL_IF".equals(property.name)) {
            return nullIfLine(format);
        }
        final String written = format.getOption(property.name);
        if (written == null) {
            return null;
        }
        if ("Boolean".equals(property.type)) {
            return written.equalsIgnoreCase(property.shownDefault) ? null : written.toUpperCase();
        }
        if ("Integer".equals(property.type)) {
            return sameNumber(written, property.shownDefault) ? null : written.trim();
        }
        final String escaped = escaped(written);
        if (escaped.equals(property.shownDefault)) {
            return null;
        }
        return "COMPRESSION".equals(property.name) ? written.toUpperCase() : "'" + escaped + "'";
    }

    /**
     * NULL_IF's line: the written list, each value whole, or the type's own default when none was written — the
     * one CSV default ({@code \N}) that DESC calls the default everywhere is left out, any other list is spelled.
     */
    private static String nullIfLine(final FileFormat format) {
        List<String> values = format.getNullIfValues();
        if (values == null) {
            if ("CSV".equals(format.getType())) {
                return null;
            }
            values = new ArrayList<String>();
        }
        if (values.size() == 1 && "\\N".equals(values.get(0))) {
            return null;
        }
        final StringBuilder list = new StringBuilder("(");
        for (int i = 0; i < values.size(); i++) {
            list.append(i > 0 ? ", " : "").append(quoted(values.get(i)));
        }
        return list.append(')').toString();
    }

    /** Whether two integer texts name the same number. */
    private static boolean sameNumber(final String a, final String b) {
        try {
            return Long.parseLong(a.trim()) == Long.parseLong(b.trim());
        } catch (final NumberFormatException notANumber) {
            return a.trim().equals(b.trim());
        }
    }

    /**
     * A text as the account spells it between the quotes of a DDL literal: a backslash, a double quote and the
     * backspace, form-feed, line-feed, carriage-return and tab characters as backslash escapes ({@code \\},
     * {@code \"}, {@code \b}, {@code \f}, {@code \n}, {@code \r}, {@code \t}), a single quote doubled, and every
     * other character — another control character, a NUL, any non-ASCII one — as it is.
     */
    static String escaped(final String text) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\'':
                    out.append("''");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A text as one DDL literal, {@link #escaped} between single quotes — the way every COMMENT, a tag's allowed
     * values and a file format's values are printed.
     */
    static String quoted(final String text) {
        return "'" + escaped(text) + "'";
    }

    // ─────────────────────────── STREAM ───────────────────────────

    /**
     * {@code create or replace stream S on table T;} — the source named by its own name alone, whatever schema
     * holds it: {@code on view V}, {@code on dynamic table D}, {@code on directory(@STG)} for a stage, and
     * {@code on table T} for a table or an event table; then {@code  append_only = true} for an append-only
     * stream.
     */
    static String stream(final Stream stream, final String name) {
        final String source = stream.getSourceTableName() == null ? ""
            : SqlIdentifiers.spellCanonicalEscaped(QualifiedName.parse(stream.getSourceTableName()).last());
        final String kind = stream.getSourceType() == null ? "TABLE" : stream.getSourceType().name();
        final StringBuilder sb = new StringBuilder("create or replace stream ").append(name);
        switch (kind) {
            case "VIEW":
                sb.append(" on view ").append(source);
                break;
            case "DYNAMIC_TABLE":
                sb.append(" on dynamic table ").append(source);
                break;
            case "STAGE":
                sb.append(" on directory(@").append(source).append(')');
                break;
            default:
                sb.append(" on table ").append(source);
                break;
        }
        if (stream.getStreamType() == StreamType.APPEND_ONLY) {
            sb.append(" append_only = true");
        }
        return sb.append(';').toString();
    }

    // ─────────────────────────── PIPE ───────────────────────────

    /**
     * {@code create or replace pipe P auto_ingest=false comment='c' as COPY …;} — the COPY statement as written,
     * and the comment as stored but for its single quotes, each escaped with a backslash; a backslash, a double
     * quote, a tab or a line break in it is printed as it is.
     */
    static String pipe(final Pipe pipe, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace pipe ").append(name)
            .append(" auto_ingest=").append(pipe.isAutoIngest() ? "true" : "false");
        if (hasText(pipe.getComment())) {
            sb.append(" comment='").append(pipe.getComment().replace("'", "\\'")).append('\'');
        }
        return sb.append(" as ").append(SqlObject.withoutTrailingSemicolon(pipe.getCopyStatement())).append(';')
            .toString();
    }

    // ─────────────────────────── TASK ───────────────────────────

    /**
     * {@code create or replace task T}, then one tab-indented line each for the warehouse, the schedule, the
     * CONFIG (its text as written, a backslash and a single quote each doubled), the task parameters set on the
     * task, the COMMENT, overlapping execution, the predecessors (always fully qualified), the WHEN condition, and
     * last {@code as} and the body.
     */
    static String task(final Task task, final Schema schema, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace task ").append(name).append('\n');
        if (task.getWarehouse() != null) {
            sb.append("\twarehouse=").append(task.getWarehouse()).append('\n');
        }
        if (task.getSchedule() != null) {
            sb.append("\tschedule=").append(doubledQuotes(task.getSchedule())).append('\n');
        }
        if (task.getConfig() != null) {
            sb.append("\tconfig=").append(doubledQuotes(task.getConfig().replace("\\", "\\\\"))).append('\n');
        }
        for (final String parameter : TASK_PARAMETERS) {
            if (task.isParameterSetOnTask(parameter)) {
                sb.append('\t').append(parameter).append('=').append(taskParameterValue(task, parameter)).append('\n');
            }
        }
        if (hasText(task.getComment())) {
            sb.append("\tCOMMENT=").append(quoted(task.getComment())).append('\n');
        }
        if (task.isAllowOverlappingExecution()) {
            sb.append("\tallow_overlapping_execution=true\n");
        }
        if (!task.getPredecessors().isEmpty()) {
            sb.append("\tafter ");
            for (int i = 0; i < task.getPredecessors().size(); i++) {
                sb.append(i > 0 ? ", " : "").append(qualifiedPredecessor(task.getPredecessors().get(i), schema));
            }
            sb.append('\n');
        }
        if (task.getCondition() != null) {
            sb.append("\twhen ").append(task.getCondition()).append('\n');
        }
        return sb.append("\tas ").append(SqlObject.withoutTrailingSemicolon(task.getSqlStatement())).append(';')
            .toString();
    }

    private static String taskParameterValue(final Task task, final String parameter) {
        switch (parameter) {
            case "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE":
                return doubledQuotes(String.valueOf(task.getUserTaskManagedInitialWarehouseSize()));
            case "SUSPEND_TASK_AFTER_NUM_FAILURES":
                return String.valueOf(task.getSuspendTaskAfterNumFailures());
            case "USER_TASK_TIMEOUT_MS":
                return String.valueOf(task.getUserTaskTimeoutMs());
            default:
                return String.valueOf(task.getTaskAutoRetryAttempts());
        }
    }

    /** A predecessor as the account names one: database, schema and task, completed from the task's own schema. */
    private static String qualifiedPredecessor(final String written, final Schema schema) {
        final String[] parts = QualifiedName.parse(written).parts();
        final String database = parts.length == 3 ? parts[0] : schema.getDatabaseName();
        final String schemaName = parts.length >= 2 ? parts[parts.length - 2] : schema.getName();
        return SqlIdentifiers.spellCanonicalEscaped(database) + "." + SqlIdentifiers.spellCanonicalEscaped(schemaName)
            + "." + SqlIdentifiers.spellCanonicalEscaped(parts[parts.length - 1]);
    }

    // ─────────────────────────── ALERT ───────────────────────────

    /**
     * {@code create or replace alert A}, the warehouse and the schedule on tab-indented lines, then the condition's
     * query inside {@code if (exists(…))} and the action after {@code then}, each on lines of their own.
     */
    static String alert(final Alert alert, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace alert ").append(name).append('\n');
        if (alert.getWarehouse() != null) {
            sb.append("\twarehouse=").append(alert.getWarehouse()).append('\n');
        }
        if (alert.getSchedule() != null) {
            sb.append("\tschedule=").append(doubledQuotes(alert.getSchedule())).append('\n');
        }
        sb.append("\tif (exists(\n\t\t").append(SqlObject.withoutTrailingSemicolon(alert.getCondition()))
            .append("\n\t))\n");
        return sb.append("\tthen\n\t").append(SqlObject.withoutTrailingSemicolon(alert.getAction())).append(';')
            .toString();
    }

    // ─────────────────────────── POLICIES ───────────────────────────

    /** {@code create or replace masking policy P as (V VARCHAR) \nreturns VARCHAR ->\nbody\n;}. */
    static String maskingPolicy(final MaskingPolicy policy, final String name) {
        final String returns = policy.getReturnDataType() != null
            ? SqlTypeNames.routineType(policy.getReturnDataType()) : policyTypeName(policy.getReturnType());
        return policyDdl("masking policy", name, policySignature(policy.getParameters()), returns,
            policy.getBody(), policy.getComment());
    }

    /** {@code create or replace row access policy P as (A VARCHAR) \nreturns BOOLEAN ->\nbody\n;}. */
    static String rowAccessPolicy(final RowAccessPolicy policy, final String name) {
        return policyDdl("row access policy", name, policySignature(policy.getParameters()), "BOOLEAN",
            policy.getBody(), policy.getComment());
    }

    static String aggregationPolicy(final AggregationPolicy policy, final String name) {
        return policyDdl("aggregation policy", name, "()", "AGGREGATION_CONSTRAINT", policy.getBody(),
            policy.getComment());
    }

    static String projectionPolicy(final ProjectionPolicy policy, final String name) {
        return policyDdl("projection policy", name, "()", "PROJECTION_CONSTRAINT", policy.getBody(),
            policy.getComment());
    }

    static String joinPolicy(final JoinPolicy policy, final String name) {
        return policyDdl("join policy", name, "()", "JOIN_CONSTRAINT", policy.getBody(), policy.getComment());
    }

    /**
     * The shape every expression policy shares: the signature followed by a space and a line break, the RETURNS
     * line ending in the arrow, the body as written, the COMMENT on a line of its own, and the semicolon last.
     */
    private static String policyDdl(final String kind, final String name, final String signature,
                                    final String returns, final String body, final String comment) {
        final StringBuilder sb = new StringBuilder("create or replace ").append(kind).append(' ').append(name)
            .append(" as ").append(signature).append(" \nreturns ").append(returns).append(" ->\n")
            .append(body == null ? "" : body.trim()).append('\n');
        if (hasText(comment)) {
            sb.append("COMMENT=").append(quoted(comment)).append('\n');
        }
        return sb.append(';').toString();
    }

    /**
     * {@code (V VARCHAR, W NUMBER(38,0))} — each parameter's name, then its type as a routine spells it; a string
     * declared without a width is bare VARCHAR, whatever width the policy gives it.
     */
    private static String policySignature(final List<Parameter> parameters) {
        final StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameters.size(); i++) {
            final DataType type = parameters.get(i).getDataType();
            sb.append(i > 0 ? ", " : "").append(SqlIdentifiers.spellCanonicalEscaped(parameters.get(i).getName()))
                .append(' ').append(type instanceof StringType
                    && ((StringType) type).getMaxLength() >= SqlTypeNames.DESCRIBED_STRING_MAXIMUM
                    ? "VARCHAR" : SqlTypeNames.routineType(type));
        }
        return sb.append(')').toString();
    }

    /** A RETURNS type kept only by name: STRING and TEXT are VARCHAR. */
    private static String policyTypeName(final String typeName) {
        if (typeName == null) {
            return "VARCHAR";
        }
        final String upper = typeName.toUpperCase();
        return "STRING".equals(upper) || "TEXT".equals(upper) ? "VARCHAR" : upper;
    }

    /**
     * {@code create or replace password policy P PASSWORD_MIN_LENGTH=14 … PASSWORD_HISTORY=5 ;} — every property,
     * each followed by a space, its default where the policy set none.
     */
    static String passwordPolicy(final SecurityObject policy, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace password policy ").append(name).append(' ');
        for (final SecurityPropertySpec spec : SecurityPropertySpecs.of(SecurityObjectKind.PASSWORD_POLICY)) {
            if ("COMMENT".equals(spec.getName())) {
                continue;
            }
            final Object value = policy.getProperty(spec.getName());
            sb.append(spec.getName()).append('=').append(value == null ? spec.getDefaultValue() : value).append(' ');
        }
        return sb.append(';').toString();
    }

    // ─────────────────────────── CONTACT / STREAMLIT ───────────────────────────

    /** {@code create or replace contact C EMAIL_DISTRIBUTION_LIST='a@b'\n;}, the COMMENT on a line before the semicolon. */
    static String contact(final Contact contact, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace contact ").append(name).append(' ');
        if (contact.getEmailDistributionList() != null) {
            sb.append("EMAIL_DISTRIBUTION_LIST=").append(doubledQuotes(contact.getEmailDistributionList()));
        } else if (contact.getUrl() != null) {
            sb.append("URL=").append(doubledQuotes(contact.getUrl()));
        }
        sb.append('\n');
        if (hasText(contact.getComment())) {
            sb.append("COMMENT=").append(quoted(contact.getComment())).append('\n');
        }
        return sb.append(';').toString();
    }

    /**
     * {@code create or replace streamlit S}, then the root location after {@code from} and the main file, query
     * warehouse, comment and title, each on a tab-indented line; the texts are printed between quotes as stored.
     */
    static String streamlit(final AppObject app, final String name) {
        final StringBuilder sb = new StringBuilder("create or replace streamlit ").append(name);
        final String from = app.getRootLocation() != null ? app.getRootLocation() : app.getFromLocation();
        if (from != null) {
            sb.append("\n\tfrom '").append(from).append('\'');
        }
        if (app.getMainFile() != null) {
            sb.append("\n\tmain_file='").append(app.getMainFile()).append('\'');
        }
        if (app.getQueryWarehouse() != null) {
            sb.append("\n\tquery_warehouse='").append(app.getQueryWarehouse()).append('\'');
        }
        if (hasText(app.getComment())) {
            sb.append("\n\tcomment='").append(app.getComment()).append('\'');
        }
        if (app.getTitle() != null) {
            sb.append("\n\ttitle='").append(app.getTitle()).append('\'');
        }
        return sb.append(';').toString();
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** A text as one single-quoted literal, its own quotes doubled. */
    static String doubledQuotes(final String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    static boolean hasText(final String text) {
        return text != null && !text.isEmpty();
    }
}
