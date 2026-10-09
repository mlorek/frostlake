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

import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

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
 * A statement with several faults is answered for the first class in that order, whichever is written first.
 * A view names an unknown property a table's ({@code for 'TABLE'}), is refused a table's parameters as a
 * schema it may not operate on, and a table's staging properties as incompatible options; a sequence takes
 * no property but its own and is refused every parameter.
 */
final class CreatedObjectProperties {

    private static final String BOOLEAN = "BOOLEAN";
    private static final String STRING = "STRING";
    /** A value that is a word or a switch value, never a number. */
    private static final String WORD = "WORD";
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
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
            if (!TABLE_PROPERTIES.containsKey(key) && !PARAMETER_TYPES.containsKey(key)
                    && !ParameterRegistry.isTableParameter(key) && !OTHER_TABLE_KIND_PROPERTIES.contains(key)) {
                throw ParameterRegistry.invalidProperty(name, "TABLE");
            }
        }
        if (ctx.ICEBERG() != null || ctx.HYBRID() != null) {
            // What an Iceberg or a hybrid table may be given is judged where the table is made.
            return;
        }
        rejectValuesOfAnotherType(names, values);
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
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
        rejectDuplicates(names);
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
            if (!TABLE_PROPERTIES.containsKey(key) && !PARAMETER_TYPES.containsKey(key)
                    && !ParameterRegistry.isTableParameter(key)) {
                // The property is named a table's, the view's kind notwithstanding.
                throw ParameterRegistry.invalidProperty(name, "TABLE");
            }
        }
        rejectValuesOfAnotherType(names, values);
        for (final String name : names) {
            final String key = ParameterRegistry.canonical(name);
            if ("COMMENT".equals(key) || "CHANGE_TRACKING".equals(key) || "DATA_METRIC_SCHEDULE".equals(key)) {
                continue;
            }
            if (VIEW_INCOMPATIBLE.contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("options " + key + " and {1} are incompatible."));
            }
            if (TABLE_PROPERTIES.containsKey(key)) {
                throw ParameterRegistry.invalidProperty(key, "VIEW");
            }
            if (ParameterRegistry.isTableParameter(key) || VIEW_LEVELS.contains(key)) {
                throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema", schemaName));
            }
            throw cannotSet(key, "VIEW");
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
        rejectValuesOfAnotherType(names, values);
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
     * A value that is not of its name's type is refused as written: a switch takes TRUE and FALSE alone, a count
     * an unsigned whole number, a text anything but a number or a switch value.
     */
    private static void rejectValuesOfAnotherType(final List<String> names,
                                                  final List<FrostlakeParser.CopyOptionValueContext> values) {
        for (int i = 0; i < names.size(); i++) {
            final String key = ParameterRegistry.canonical(names.get(i));
            final FrostlakeParser.CopyOptionValueContext value = values.get(i);
            final String type = TABLE_PROPERTIES.containsKey(key) ? TABLE_PROPERTIES.get(key) : PARAMETER_TYPES.get(key);
            if (value == null || type == null) {
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
