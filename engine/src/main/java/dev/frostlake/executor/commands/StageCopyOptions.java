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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.copy.OnErrorOption;
import dev.frostlake.parser.FrostlakeParser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.misc.Interval;

/**
 * A stage's copy options as {@code COPY_OPTIONS = (…)} of CREATE and ALTER STAGE and
 * {@code STAGE_COPY_OPTIONS = (…)} of CREATE and ALTER TABLE write them, judged one option after another in the order
 * written (live-verified):
 *
 * <pre>
 *   a name that is no copy option of a stage   invalid copy option 'nope'                  the name as written
 *   a value the option does not take           invalid value ['x'] for parameter 'PURGE'   the value as written
 *   an option given again with another value   conflicting values for copy option 'PURGE'
 * </pre>
 *
 * <p>A stage's own COPY_OPTIONS list reads more values than another option list (see {@code stageCopyOptionList}),
 * each judged (live-verified): NULL leaves SIZE_LIMIT unset and is an invalid value of any other option, as a number
 * with a fraction or an exponent is ({@code invalid value [1.5] for parameter 'SIZE_LIMIT'}); a parenthesized
 * property list is {@code invalid value [TOK_PROPERTY_LIST] for parameter 'INCLUDE_METADATA'}, and a parenthesized
 * list of values, words or strings, {@code Invalid value specified for property 'ON_ERROR'}. A size's NULL after a
 * size conflicts with it. A list of names alone is taken, and ignored.
 *
 * <p>The options and their values: ON_ERROR takes what a COPY statement takes (see {@link OnErrorOption});
 * SIZE_LIMIT a whole number of 64 bits; PURGE, RETURN_FAILED_ONLY,
 * ENFORCE_LENGTH, TRUNCATECOLUMNS and FORCE TRUE or FALSE, never a string; MATCH_BY_COLUMN_NAME CASE_SENSITIVE,
 * CASE_INSENSITIVE or NONE; LOAD_MODE FULL_INGEST or ADD_FILES_COPY. A word or a string of those spellings counts in
 * any case. INCLUDE_METADATA and FILE_PROCESSOR are named options whose values are not judged here. The options of a
 * COPY statement alone — FILES, PATTERN, VALIDATION_MODE, the unload options — and the file format's are no copy
 * options of a stage.
 *
 * <p>Each value is kept as the stage's listing spells it, which is also what tells two values of one option apart: a
 * string's text as written, an unquoted word in upper case, CONTINUE as written, a boolean in lower case and a size
 * as its number.
 */
final class StageCopyOptions {

    private static final Set<String> BOOLEANS = new HashSet<>(Arrays.asList(
        "PURGE", "RETURN_FAILED_ONLY", "ENFORCE_LENGTH", "TRUNCATECOLUMNS", "FORCE"));

    private static final Set<String> UNJUDGED = new HashSet<>(Arrays.asList("INCLUDE_METADATA", "FILE_PROCESSOR"));

    private static final Set<String> MATCH_BY_COLUMN_NAME_WORDS = new HashSet<>(Arrays.asList(
        "CASE_SENSITIVE", "CASE_INSENSITIVE", "NONE"));

    private static final Set<String> LOAD_MODE_WORDS = new HashSet<>(Arrays.asList("FULL_INGEST", "ADD_FILES_COPY"));

    private StageCopyOptions() {
    }

    /**
     * The options of one list, judged, by name in upper case and each value as the listing spells it.
     *
     * @param list the parenthesized list
     * @return the options in the order written
     */
    static Map<String, String> read(final FrostlakeParser.ParenOptionListContext list) {
        final Map<String, String> options = new LinkedHashMap<>();
        for (final FrostlakeParser.ParenOptionContext option : list.parenOption()) {
            final String writtenName = option.optionKey().getText();
            final String name = writtenName.toUpperCase(Locale.ROOT);
            if (writtenName.startsWith("\"") || !isOption(name)) {
                throw new RuntimeException(SqlCompilationError.invalidCopyOption(writtenName));
            }
            final String value = UNJUDGED.contains(name) ? writtenValue(option) : judged(name, option);
            final String earlier = options.get(name);
            if (earlier != null && !earlier.equals(value)) {
                throw new RuntimeException(SqlCompilationError.conflictingCopyOption(name));
            }
            options.put(name, value);
        }
        return options;
    }

    /**
     * The options of a stage's own COPY_OPTIONS list, judged, by name in upper case and each value as the listing
     * spells it; SIZE_LIMIT = NULL is no value, and a list of names alone none at all.
     *
     * @param list the list
     * @return the options in the order written
     */
    static Map<String, String> read(final FrostlakeParser.StageCopyOptionListContext list) {
        final Map<String, String> options = new LinkedHashMap<>();
        for (final FrostlakeParser.StageCopyOptionContext option : list.stageCopyOption()) {
            final String writtenName = option.optionKey().getText();
            final String name = writtenName.toUpperCase(Locale.ROOT);
            if (writtenName.startsWith("\"") || !isOption(name)) {
                throw new RuntimeException(SqlCompilationError.invalidCopyOption(writtenName));
            }
            if (option.stageCopyPropertyList() != null) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter("TOK_PROPERTY_LIST", name));
            }
            if (option.stageCopyValueList() != null || option.LPAREN() != null) {
                throw new RuntimeException("Invalid value specified for property '" + name + "'");
            }
            final String written = option.start.getInputStream().getText(Interval.of(
                option.EQ().getSymbol().getStopIndex() + 1, option.stop.getStopIndex())).trim();
            if (option.NULL() != null && "SIZE_LIMIT".equals(name)) {
                // No size: given after a size, it is another value of the option (live-verified).
                if (options.containsKey(name)) {
                    throw new RuntimeException(SqlCompilationError.conflictingCopyOption(name));
                }
                continue;
            }
            final String value = UNJUDGED.contains(name) ? written
                : option.copyOptionValue() == null ? null : spelled(name, option.copyOptionValue());
            if (value == null) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(written, name));
            }
            final String earlier = options.get(name);
            if (earlier != null && !earlier.equals(value)) {
                throw new RuntimeException(SqlCompilationError.conflictingCopyOption(name));
            }
            options.put(name, value);
        }
        return options;
    }

    private static boolean isOption(final String name) {
        return BOOLEANS.contains(name) || UNJUDGED.contains(name) || "ON_ERROR".equals(name)
            || "SIZE_LIMIT".equals(name) || "MATCH_BY_COLUMN_NAME".equals(name) || "LOAD_MODE".equals(name);
    }

    /** One option's value as the listing spells it, or the refusal of a value the option does not take. */
    private static String judged(final String name, final FrostlakeParser.ParenOptionContext option) {
        final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
        final String spelled = value == null ? null : spelled(name, value);
        if (spelled == null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(writtenValue(option), name));
        }
        return spelled;
    }

    /** A value the option takes, as the listing spells it, or null for one it does not. */
    private static String spelled(final String name, final FrostlakeParser.CopyOptionValueContext value) {
        if (BOOLEANS.contains(name)) {
            return value.booleanValue() == null ? null : value.booleanValue().getText().toLowerCase(Locale.ROOT);
        }
        if ("SIZE_LIMIT".equals(name)) {
            return value.INTEGER_LITERAL() == null || value.MINUS() != null ? null : size(value.getText());
        }
        final String word = word(value);
        if (word == null) {
            return null;
        }
        final String upper = word.toUpperCase(Locale.ROOT);
        if ("ON_ERROR".equals(name)) {
            return OnErrorOption.parse(upper) != null ? word : null;
        }
        if ("MATCH_BY_COLUMN_NAME".equals(name)) {
            return MATCH_BY_COLUMN_NAME_WORDS.contains(upper) ? word : null;
        }
        return LOAD_MODE_WORDS.contains(upper) ? word : null;
    }

    /** A string's text, an unquoted word in upper case, CONTINUE as written; null for a value of another kind. */
    private static String word(final FrostlakeParser.CopyOptionValueContext value) {
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        if (value.CONTINUE() != null) {
            return value.CONTINUE().getText();
        }
        if (value.qualifiedName() != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(value.qualifiedName());
            return parts.length == 1 ? parts[0] : null;
        }
        return null;
    }

    /** A size: a whole number that fits 64 bits, as its number; null past that. */
    private static String size(final String written) {
        try {
            return Long.toString(Long.parseLong(written));
        } catch (final NumberFormatException tooLarge) {
            return null;
        }
    }

    /** An option's value as the statement wrote it, a parenthesized list included. */
    private static String writtenValue(final FrostlakeParser.ParenOptionContext option) {
        if (option.copyOptionValue() != null) {
            return ParseTreeText.getOriginalText(option.copyOptionValue());
        }
        return option.start.getInputStream().getText(
            Interval.of(option.LPAREN().getSymbol().getStartIndex(), option.RPAREN().getSymbol().getStopIndex()));
    }
}
