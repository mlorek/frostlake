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
import dev.frostlake.parser.FrostlakeParser;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>The options and their values: ON_ERROR takes ABORT_STATEMENT, CONTINUE, SKIP_FILE, SKIP_FILE_n and a string
 * 'SKIP_FILE_p%' whose p is a number from 0 to 100; SIZE_LIMIT a whole number of 64 bits; PURGE, RETURN_FAILED_ONLY,
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

    private static final Set<String> ON_ERROR_WORDS = new HashSet<>(Arrays.asList(
        "ABORT_STATEMENT", "CONTINUE", "SKIP_FILE"));

    private static final Set<String> MATCH_BY_COLUMN_NAME_WORDS = new HashSet<>(Arrays.asList(
        "CASE_SENSITIVE", "CASE_INSENSITIVE", "NONE"));

    private static final Set<String> LOAD_MODE_WORDS = new HashSet<>(Arrays.asList("FULL_INGEST", "ADD_FILES_COPY"));

    private static final Pattern SKIP_FILE_COUNT = Pattern.compile("SKIP_FILE_[0-9]+");

    private static final Pattern SKIP_FILE_SHARE = Pattern.compile("SKIP_FILE_(.+)%");

    private static final BigDecimal WHOLE = new BigDecimal(100);

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
            return ON_ERROR_WORDS.contains(upper) || SKIP_FILE_COUNT.matcher(upper).matches()
                || value.STRING_LITERAL() != null && isShare(upper) ? word : null;
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

    /** ON_ERROR's share of a file's records: SKIP_FILE_p% with p a number from 0 to 100. */
    private static boolean isShare(final String upper) {
        final Matcher share = SKIP_FILE_SHARE.matcher(upper);
        if (!share.matches()) {
            return false;
        }
        try {
            final BigDecimal percent = new BigDecimal(share.group(1));
            return percent.signum() >= 0 && percent.compareTo(WHOLE) <= 0;
        } catch (final NumberFormatException notANumber) {
            return false;
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
