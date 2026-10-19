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


package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The {@code name = value} properties a CREATE TABLE, CREATE VIEW or CREATE SEQUENCE is written with, judged
 * before the statement's objects are looked at: the name it creates, the table it copies and its query are all
 * read after them (live-verified). A property list reads any name, so what a name earns is decided here:
 *
 * <pre>
 *   x = 1 x = 2                  duplicate property 'x';
 *   x = 1                        invalid property 'x' for 'TABLE'       neither a property nor a parameter
 *   "x" = 1                      invalid property '"x"' for 'TABLE'     a quoted name keeps its quotes
 *   CHANGE_TRACKING = 5          invalid value [5] for parameter 'CHANGE_TRACKING'
 *   TIMEZONE = 'UTC'             cannot set parameter 'TIMEZONE' at 'TABLE'
 *   AUTO_REFRESH = TRUE          Unsupported feature 'AUTO_REFRESH'.    a property of another kind of table
 * </pre>
 *
 * A statement with several faults is answered for the first class in that order, whichever is written first;
 * of several names of one class the one refused is not the first written but the first of a table hashed by
 * name ({@code A = 1 P = 2} and {@code P = 1 A = 2} both refuse {@code P}), while a repeated name is refused
 * where it is read. {@code COPY} is a word of the statement and takes no value: a number or a switch value is
 * an invalid value of it, a text or a word an invalid type.
 *
 * <p>A view names an unknown property a table's ({@code for 'TABLE'}), is refused a table's parameters as a
 * schema it may not operate on, and a table's staging properties as incompatible options. A materialized view
 * is judged as a view under its own kind, with what it is given as a table: its staging properties are taken,
 * another kind of table's, schema evolution and a file location are an unsupported feature, and change
 * tracking is refused only when it is switched on — as a row timestamp is for both. The schedule the metrics
 * of any of the three run on is held to its grammar (see {@link MetricSchedule}). A sequence takes no property
 * but its own and is refused every parameter.
 */
final class CreatedObjectProperties {

    private static final String BOOLEAN = "BOOLEAN";
    private static final String STRING = "STRING";
    /** A value that is a word or a switch value, never a number. */
    private static final String WORD = "WORD";
    private static final String COPY = "COPY";
    private static final String METRIC_SCHEDULE = "DATA_METRIC_SCHEDULE";
    private static final String ICEBERG_OPTION = "Iceberg table options (e.g. BASE_LOCATION) require"
        + " DEFAULT_METADATA_WRITE_FORMAT=ICEBERG or CREATE ICEBERG TABLE";

    /** Every parameter the account knows, with the type of value it takes. */
    private static final Map<String, String> PARAMETER_TYPES = load();

    /** What a table takes that is no parameter, with the type of value each takes (null: judged elsewhere). */
    private static final Map<String, String> TABLE_PROPERTIES = new HashMap<String, String>();

    /** Properties of an external or an Iceberg table, which a plain table is refused as a feature. */
    private static final Set<String> OTHER_TABLE_KIND_PROPERTIES = new HashSet<String>(Arrays.asList(
        "AUTO_REFRESH", "AWS_SNS_TOPIC", "CATALOG_NAMESPACE", "CATALOG_TABLE_NAME", "FILE_FORMAT", "INTEGRATION",
        "LOCATION", "METADATA_FILE_PATH", "PARTITION_TYPE", "PATTERN", "REFRESH_MODE", "REFRESH_ON_CREATE",
        "TABLE_FORMAT"));

    /** A table's properties a view is refused as options that do not go together. */
    private static final Set<String> VIEW_INCOMPATIBLE = new HashSet<String>(Arrays.asList(
        "ENABLE_SCHEMA_EVOLUTION", "STAGE_FILE_FORMAT", "STAGE_COPY_OPTIONS", "BASE_LOCATION"));

    /** The logging levels, which a view is refused as it is refused a table's parameters. */
    private static final Set<String> VIEW_LEVELS = new HashSet<String>(Arrays.asList("LOG_LEVEL", "TRACE_LEVEL"));

    static {
        TABLE_PROPERTIES.put("CHANGE_TRACKING", BOOLEAN);
        TABLE_PROPERTIES.put("ENABLE_SCHEMA_EVOLUTION", BOOLEAN);
        TABLE_PROPERTIES.put("ROW_TIMESTAMP", BOOLEAN);
        TABLE_PROPERTIES.put("COMMENT", STRING);
        TABLE_PROPERTIES.put("ERROR_LOGGING", WORD);
        TABLE_PROPERTIES.put("STAGE_FILE_FORMAT", null);
        TABLE_PROPERTIES.put("STAGE_COPY_OPTIONS", null);
        TABLE_PROPERTIES.put("BASE_LOCATION", STRING);
    }

    private CreatedObjectProperties() {
    }

    /** Judges the properties written around a CREATE TABLE's columns. */
    static void requireTableProperties(final FrostlakeParser.CreateStatementContext ctx) {
        final List<String> names = new ArrayList<String>();
        final List<FrostlakeParser.CopyOptionValueContext> values =
            new ArrayList<FrostlakeParser.CopyOptionValueContext>();
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.optionKey() != null) {
                names.add(tail.optionKey().getText());
                values.add(tail.copyOptionValue());
            }
        }
        rejectDuplicates(names);
        final List<Integer> order = judgedOrder(names);
        rejectUnknown(names, order);
        if (ctx.ICEBERG() != null) {
            // A parameter a session keeps is no property of a table of any kind: cannot set parameter 'TIMEZONE'
            // at 'ICEBERG_TABLE' (live-verified).
            for (final Integer i : order) {
                final String key = ParameterRegistry.canonical(names.get(i));
                if (ParameterRegistry.isSessionParameter(key) && !ParameterRegistry.isTableParameter(key)) {
                    throw cannotSet(key, "ICEBERG_TABLE");
                }
            }
        }
        if (ctx.ICEBERG() != null || ctx.HYBRID() != null) {
            // What else an Iceberg or a hybrid table may be given is judged where the table is made.
            return;
        }
        rejectValuesOfAnotherType(names, values, order, new HashSet<String>());
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (OTHER_TABLE_KIND_PROPERTIES.contains(key)) {
                throw new RuntimeException("Unsupported feature '" + key + "'.");
            }
            if ("BASE_LOCATION".equals(key)) {
                // Where its files are kept is an Iceberg table's to say.
                throw new RuntimeException(SqlCompilationError.of(ICEBERG_OPTION));
            }
            if (!TABLE_PROPERTIES.containsKey(key) && !takenByTable(key)) {
                throw cannotSet(key, "TABLE");
            }
        }
        requireMetricSchedule(names, values, true);
        for (final Integer i : order) {
            refuseOtherTableKindParameter(ParameterRegistry.canonical(names.get(i)), values.get(i), ctx);
        }
    }

    /**
     * Refuses a parameter a plain table takes no value of: one of an Iceberg table, of a table in a catalog-linked
     * database, of another kind of table, or a staging format no format of the name answers (live-verified).
     */
    private static void refuseOtherTableKindParameter(final String key, final FrostlakeParser.CopyOptionValueContext
            value, final FrostlakeParser.CreateStatementContext ctx) {
        final String text = value == null ? null : value.STRING_LITERAL() != null
            ? SqlStringLiterals.decode(value.STRING_LITERAL().getText()) : value.getText();
        switch (key) {
            case "CATALOG":
                throw new RuntimeException(text == null || text.isEmpty()
                    ? SqlCompilationError.inline("Iceberg tables require a catalog integration to be specified")
                    : "Catalog integration cannot be set for legacy iceberg tables");
            case "EXTERNAL_VOLUME":
                throw new RuntimeException(text == null || text.isEmpty()
                    ? "Iceberg table null must have the table parameter EXTERNAL_VOLUME defined on the table, schema,"
                        + " database, or account."
                    : "EXTERNAL_VOLUME is not supported on table " + createdName(ctx) + ".");
            case "CATALOG_SYNC":
                throw new RuntimeException("Catalog integration " + text
                    + " is not valid. Please ensure required properties are valid.");
            case "PURGE_ON_DROP_TABLE":
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid parameter 'PURGE_ON_DROP_TABLE can only be set on tables in a catalog-linked database'"));
            case "REPLACE_INVALID_CHARACTERS":
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid type of property 'REPLACE_INVALID_CHARACTERS' for 'TABLE'"));
            case "STORAGE_SERIALIZATION_POLICY":
                throw new RuntimeException("SQL Compilation error:\nSetting storage serialization policy is only"
                    + " supported for Iceberg tables using Snowflake catalog.");
            case "TARGET_FILE_SIZE":
                throw new RuntimeException("SQL Compilation error:\nSetting target file size is only supported for"
                    + " Iceberg tables.");
            case "STAGE_FILE_FORMAT":
                // A number names a format by its digits, which no format of the schema answers.
                if (value != null && value.INTEGER_LITERAL() != null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("File format", value.getText()));
                }
                return;
            default:
                return;
        }
    }

    /** The name a CREATE TABLE makes, as its last written part reads. */
    private static String createdName(final FrostlakeParser.CreateStatementContext ctx) {
        final String[] parts = ctx.objectName() != null && ctx.objectName().qualifiedName() != null
            ? ParseTreeText.qualifiedNameParts(ctx.objectName().qualifiedName()) : new String[] {""};
        return parts[parts.length - 1];
    }

    /** Judges the properties written before a CREATE VIEW's query, for a view made in the named schema. */
    static void requireViewProperties(final FrostlakeParser.CreateStatementContext ctx, final String schemaName) {
        final List<String> names = new ArrayList<String>();
        final List<FrostlakeParser.CopyOptionValueContext> values =
            new ArrayList<FrostlakeParser.CopyOptionValueContext>();
        for (final FrostlakeParser.ViewPropertyContext property : ctx.viewProperty()) {
            names.add(property.optionKey().getText());
            values.add(property.copyOptionValue());
        }
        requireViewKindProperties(names, values, schemaName, false);
    }

    /**
     * Judges the properties written among a CREATE MATERIALIZED VIEW's comment and clustering key, for a view
     * made in the named schema.
     */
    static void requireMaterializedViewProperties(final FrostlakeParser.CreateStatementContext ctx,
                                                  final String schemaName) {
        final List<String> names = new ArrayList<String>();
        final List<FrostlakeParser.CopyOptionValueContext> values =
            new ArrayList<FrostlakeParser.CopyOptionValueContext>();
        for (final ParseTree child : ctx.children) {
            if (child instanceof FrostlakeParser.CommentClauseContext) {
                // A comment written as a text is a property like the others, and counts among the repeated.
                names.add("COMMENT");
                values.add(null);
            } else if (child instanceof FrostlakeParser.ViewPropertyContext) {
                final FrostlakeParser.ViewPropertyContext property = (FrostlakeParser.ViewPropertyContext) child;
                names.add(property.optionKey().getText());
                values.add(property.copyOptionValue());
            }
        }
        requireViewKindProperties(names, values, schemaName, true);
    }

    /**
     * What a view's kind makes of the names it knows, class by class: another kind of table's, a parameter of
     * another level, a table's parameter, and last — in an order of their own — what the kind has no use for
     * or only the value decides.
     */
    private static void requireViewKindProperties(final List<String> names,
                                                  final List<FrostlakeParser.CopyOptionValueContext> values,
                                                  final String schemaName, final boolean materialized) {
        final String kind = materialized ? "MATERIALIZED_VIEW" : "VIEW";
        rejectDuplicates(names);
        final List<Integer> order = judgedOrder(names);
        // The property is named a table's, the view's kind notwithstanding.
        rejectUnknown(names, order);
        final Set<String> unread = new HashSet<String>();
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
            if (anotherKindsFor(key, materialized)) {
                unread.add(key);
            }
        }
        rejectValuesOfAnotherType(names, values, order, unread);
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (unread.contains(key)) {
                throw new RuntimeException(materialized ? "Unsupported feature '" + key + "'."
                    : SqlCompilationError.of("options " + key + " and {1} are incompatible."));
            }
        }
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (!TABLE_PROPERTIES.containsKey(key) && !ParameterRegistry.isTableParameter(key)
                    && !VIEW_LEVELS.contains(key)) {
                throw cannotSet(key, kind);
            }
        }
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (!METRIC_SCHEDULE.equals(key) && !TABLE_PROPERTIES.containsKey(key)
                    && (ParameterRegistry.isTableParameter(key) || VIEW_LEVELS.contains(key))) {
                throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema", schemaName));
            }
        }
        requireMetricSchedule(names, values, false);
        if (written(names, "ERROR_LOGGING") >= 0) {
            throw ParameterRegistry.invalidProperty("ERROR_LOGGING", kind);
        }
        // Switched off it is taken; switched on it is no property of the kind. A view tracks changes.
        if (materialized && switchedOn(names, values, "CHANGE_TRACKING")) {
            throw ParameterRegistry.invalidProperty("CHANGE_TRACKING", kind);
        }
        if (switchedOn(names, values, "ROW_TIMESTAMP")) {
            throw ParameterRegistry.invalidProperty("ROW_TIMESTAMP", kind);
        }
    }

    /**
     * Whether a view's kind reads a name as another kind of table's — judged by the name alone, its value
     * unread: a view is refused a table's staging and storage properties with it, a materialized view takes
     * the staging ones.
     */
    private static boolean anotherKindsFor(final String key, final boolean materialized) {
        if (OTHER_TABLE_KIND_PROPERTIES.contains(key)) {
            return true;
        }
        return materialized ? "ENABLE_SCHEMA_EVOLUTION".equals(key) || "BASE_LOCATION".equals(key)
            : VIEW_INCOMPATIBLE.contains(key);
    }

    /** Where a property is written among the names, or -1. */
    private static int written(final List<String> names, final String key) {
        for (int i = 0; i < names.size(); i++) {
            if (key.equals(ParameterRegistry.canonical(names.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean switchedOn(final List<String> names,
                                      final List<FrostlakeParser.CopyOptionValueContext> values, final String key) {
        final int at = written(names, key);
        return at >= 0 && values.get(at) != null && values.get(at).booleanValue() != null
            && values.get(at).booleanValue().TRUE() != null;
    }

    /** The schedule its metrics run on is held to the schedule grammar, whatever the kind. */
    private static void requireMetricSchedule(final List<String> names,
                                              final List<FrostlakeParser.CopyOptionValueContext> values,
                                              final boolean onChanges) {
        final int at = written(names, METRIC_SCHEDULE);
        if (at >= 0 && values.get(at) != null) {
            final FrostlakeParser.CopyOptionValueContext value = values.get(at);
            MetricSchedule.require(value.STRING_LITERAL() != null
                ? SqlStringLiterals.decode(value.getText()) : value.getText(), onChanges);
        }
    }

    /** Judges the properties a CREATE SEQUENCE is written with beside its own. */
    static void requireSequenceProperties(final FrostlakeParser.CreateStatementContext ctx) {
        if (ctx.sequenceOptions() == null) {
            return;
        }
        final List<String> names = new ArrayList<String>();
        final List<FrostlakeParser.CopyOptionValueContext> values =
            new ArrayList<FrostlakeParser.CopyOptionValueContext>();
        for (final FrostlakeParser.SequenceOptionContext option : ctx.sequenceOptions().sequenceOption()) {
            if (option.optionKey() != null) {
                names.add(option.optionKey().getText());
                values.add(option.copyOptionValue());
            }
        }
        rejectDuplicates(names);
        for (final String name : names) {
            if (!PARAMETER_TYPES.containsKey(ParameterRegistry.canonical(name))) {
                throw ParameterRegistry.invalidProperty(name, "SEQUENCE");
            }
        }
        final List<Integer> written = new ArrayList<Integer>();
        for (int i = 0; i < names.size(); i++) {
            written.add(i);
        }
        rejectValuesOfAnotherType(names, values, written, new HashSet<String>());
        if (!names.isEmpty()) {
            throw cannotSet(ParameterRegistry.canonical(names.get(0)), "SEQUENCE");
        }
    }

    /** A name written twice is refused as it is written: an unquoted one whatever its case, a quoted one exactly. */
    private static void rejectDuplicates(final List<String> names) {
        final Set<String> seen = new HashSet<String>();
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
            if (!seen.add(key)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(
                    TABLE_PROPERTIES.containsKey(key) || PARAMETER_TYPES.containsKey(key) ? key : name));
            }
        }
    }

    /**
     * The order the names of a property list are judged in: that of a table hashed by name — the slot the name
     * falls in first, the written order within a slot — not the written one (live-verified).
     */
    static List<Integer> judgedOrder(final List<String> names) {
        int slots = 16;
        while (names.size() * 4 > slots * 3) {
            slots *= 2;
        }
        final List<Integer> order = new ArrayList<Integer>();
        for (int slot = 0; slot < slots; slot++) {
            for (int i = 0; i < names.size(); i++) {
                final int hash = ParameterRegistry.canonical(names.get(i)).hashCode();
                if (((hash ^ (hash >>> 16)) & (slots - 1)) == slot) {
                    order.add(i);
                }
            }
        }
        return order;
    }

    /** Whether a name is a parameter the account knows, of any level. */
    static boolean isParameter(final String key) {
        return PARAMETER_TYPES.containsKey(key);
    }

    /** A name that is neither a property nor a parameter is refused as it is written, as a table's. */
    private static void rejectUnknown(final List<String> names, final List<Integer> order) {
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            if (!TABLE_PROPERTIES.containsKey(key) && !PARAMETER_TYPES.containsKey(key)
                    && !ParameterRegistry.isTableParameter(key) && !OTHER_TABLE_KIND_PROPERTIES.contains(key)
                    && !COPY.equals(key)) {
                throw ParameterRegistry.invalidProperty(names.get(i), "TABLE");
            }
        }
    }

    /**
     * A value that is not of its name's type is refused as written: a switch takes TRUE and FALSE alone, a count
     * an unsigned whole number, a text anything but a number or a switch value. The names in {@code unread}
     * are judged by the name alone.
     */
    private static void rejectValuesOfAnotherType(final List<String> names,
                                                  final List<FrostlakeParser.CopyOptionValueContext> values,
                                                  final List<Integer> order, final Set<String> unread) {
        for (final Integer i : order) {
            final String key = ParameterRegistry.canonical(names.get(i));
            final FrostlakeParser.CopyOptionValueContext value = values.get(i);
            if (COPY.equals(key)) {
                throw copyValue(value);
            }
            final String type = TABLE_PROPERTIES.containsKey(key) ? TABLE_PROPERTIES.get(key) : PARAMETER_TYPES.get(key);
            if (value == null || type == null || unread.contains(key)) {
                continue;
            }
            final boolean number = value.INTEGER_LITERAL() != null;
            final boolean fits;
            if (BOOLEAN.equals(type)) {
                fits = value.booleanValue() != null;
            } else if (WORD.equals(type)) {
                fits = !number;
            } else if (STRING.equals(type)) {
                fits = !number && value.booleanValue() == null;
            } else {
                fits = number && value.MINUS() == null;
            }
            if (!fits) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), key));
            }
        }
    }

    /**
     * COPY given a value: a number or a switch value is no value of it, a text or a word no type of it, and a
     * parenthesized list is named by the parser's own word for one.
     */
    private static RuntimeException copyValue(final FrostlakeParser.CopyOptionValueContext value) {
        if (value == null) {
            return new RuntimeException(SqlCompilationError.of(
                "invalid type of property 'TOK_PROPERTY_LIST' for '" + COPY + "'"));
        }
        if (value.INTEGER_LITERAL() != null || value.booleanValue() != null) {
            return new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), COPY));
        }
        return new RuntimeException(SqlCompilationError.of(
            "invalid type of property '" + value.getText() + "' for '" + COPY + "'"));
    }

    /**
     * The kind of value a parameter the account lists takes — BOOLEAN, NUMBER, FLOAT or STRING.
     *
     * @param name the parameter's name, upper case
     * @return the kind, or null when the account lists no parameter of that name
     */
    static String accountParameterType(final String name) {
        return PARAMETER_TYPES.get(name);
    }

    /** A parameter a table is created with: the ones listed for a table, but for the event level it only reads. */
    private static boolean takenByTable(final String key) {
        return ParameterRegistry.isTableParameter(key) && !"LOG_EVENT_LEVEL".equals(key);
    }

    private static RuntimeException cannotSet(final String parameter, final String kind) {
        return new RuntimeException(SqlCompilationError.of(
            "cannot set parameter '" + parameter + "' at '" + kind + "'"));
    }

    private static Map<String, String> load() {
        final Map<String, String> types = new HashMap<String, String>();
        try (InputStream in = CreatedObjectProperties.class.getResourceAsStream(
                "/dev/frostlake/parameters/account.tsv")) {
            if (in == null) {
                throw new IllegalStateException("missing parameter list: account");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        final int tab = trimmed.indexOf('\t');
                        types.put(trimmed.substring(0, tab).toUpperCase(Locale.ROOT), trimmed.substring(tab + 1));
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load parameter list: account", e);
        }
        return types;
    }
}
