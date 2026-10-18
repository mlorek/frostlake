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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.misc.Interval;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The parameters ALTER DYNAMIC TABLE … SET gives a dynamic table beside its lag, warehouse, comment and retention, each
 * judged as the account judges it and kept as SHOW PARAMETERS lists it (live-verified):
 *
 * <pre>
 *   LOG_LEVEL, LOG_EVENT_LEVEL        TRACE, DEBUG, INFO, WARN, ERROR, FATAL or OFF, a word or a string in any case
 *   TRACE_LEVEL                       ALWAYS, ON_EVENT, PROPAGATE or OFF
 *   DATA_METRIC_SCHEDULE              a metric schedule (MetricSchedule), TRIGGER_ON_CHANGES among them
 *   DEFAULT_DDL_COLLATION             a string
 *   ENABLE_DATA_COMPACTION,
 *   QUOTED_IDENTIFIERS_IGNORE_CASE    TRUE or FALSE
 *   MAX_DATA_EXTENSION_TIME_IN_DAYS   a whole number
 * </pre>
 *
 * A value of another kind is {@code invalid value [5] for parameter 'LOG_LEVEL'}, a word no level is {@code Invalid
 * value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF.}, a schedule that is none {@code
 * Invalid schedule was specified. …}, and NULL takes the table's own value away, as UNSET does.
 */
final class DynamicTableParameters {

    private static final String MAX_DATA_EXTENSION = "MAX_DATA_EXTENSION_TIME_IN_DAYS";

    private static final List<String> LOG_LEVELS = Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL",
        "OFF");

    private static final List<String> TRACE_LEVELS = Arrays.asList("ALWAYS", "ON_EVENT", "PROPAGATE", "OFF");

    private static final Set<String> BOOLEANS = new HashSet<>(Arrays.asList("ENABLE_DATA_COMPACTION",
        "QUOTED_IDENTIFIERS_IGNORE_CASE"));

    private static final Set<String> TAKEN = new HashSet<>(Arrays.asList("LOG_LEVEL", "TRACE_LEVEL", "LOG_EVENT_LEVEL",
        "DATA_METRIC_SCHEDULE", "DEFAULT_DDL_COLLATION", "ENABLE_DATA_COMPACTION", "QUOTED_IDENTIFIERS_IGNORE_CASE",
        MAX_DATA_EXTENSION));

    private DynamicTableParameters() {
    }

    /**
     * Whether ALTER DYNAMIC TABLE … SET takes the name here.
     *
     * @param name the property's name, upper-cased
     * @return whether it is one of these parameters
     */
    static boolean takes(final String name) {
        return TAKEN.contains(name);
    }

    /**
     * Judges one setting and gives the table its value.
     *
     * @param dt      the dynamic table
     * @param name    the parameter's name, upper-cased
     * @param setting the setting as written
     */
    static void set(final DynamicTable dt, final String name,
                    final FrostlakeParser.DynamicTableSettingContext setting) {
        if (setting.NULL() != null) {
            unset(dt, name);
            return;
        }
        final String written = written(setting);
        final boolean number = setting.INTEGER_LITERAL() != null;
        if (MAX_DATA_EXTENSION.equals(name)) {
            if (!number || setting.MINUS() != null) {
                throw invalidValue(written, name);
            }
            dt.setMaxDataExtensionDays(Integer.parseInt(setting.INTEGER_LITERAL().getText()));
            return;
        }
        if (BOOLEANS.contains(name)) {
            if (setting.booleanValue() == null) {
                throw invalidValue(written, name);
            }
            dt.setParameter(name, setting.booleanValue().TRUE() != null ? "true" : "false");
            return;
        }
        if (number) {
            throw invalidValue(written, name);
        }
        final String text = setting.STRING_LITERAL() != null
            ? SqlStringLiterals.decode(setting.STRING_LITERAL().getText()) : written;
        if ("DATA_METRIC_SCHEDULE".equals(name)) {
            MetricSchedule.require(text, true);
            dt.setParameter(name, text);
        } else if ("DEFAULT_DDL_COLLATION".equals(name)) {
            if (setting.STRING_LITERAL() == null) {
                throw invalidValue(written, name);
            }
            dt.setParameter(name, text);
        } else {
            final String level = text.trim().toUpperCase(Locale.ROOT);
            final boolean trace = "TRACE_LEVEL".equals(name);
            if (!(trace ? TRACE_LEVELS : LOG_LEVELS).contains(level)) {
                throw new RuntimeException("Invalid value for " + (trace ? "trace level"
                    : "LOG_EVENT_LEVEL".equals(name) ? "log event level" : "log level") + ". Allowed values are "
                    + String.join(",", trace ? TRACE_LEVELS : LOG_LEVELS) + ".");
            }
            dt.setParameter(name, level);
        }
    }

    /**
     * Takes the table's own value of a parameter away, so it inherits what its schema has.
     *
     * @param dt   the dynamic table
     * @param name the parameter's name, upper-cased
     */
    static void unset(final DynamicTable dt, final String name) {
        if (MAX_DATA_EXTENSION.equals(name)) {
            dt.unsetMaxDataExtensionDays();
        } else {
            dt.setParameter(name, null);
        }
    }

    /** The value as the statement wrote it: its tokens from the one after the '=' to the setting's last. */
    private static String written(final FrostlakeParser.DynamicTableSettingContext setting) {
        return setting.start.getInputStream().getText(Interval.of(setting.EQ().getSymbol().getStopIndex() + 1,
            setting.stop.getStopIndex())).trim();
    }

    private static RuntimeException invalidValue(final String written, final String name) {
        return new RuntimeException(SqlCompilationError.invalidValueForParameter(written, name));
    }
}
