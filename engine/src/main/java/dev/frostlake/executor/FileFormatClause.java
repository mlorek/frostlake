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

import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The FILE_FORMAT options a statement writes, read once for every statement that writes them - CREATE and ALTER
 * FILE FORMAT, a stage's {@code FILE_FORMAT = (…)} at CREATE and ALTER STAGE, a table's STAGE_FILE_FORMAT, and
 * COPY's inline list on a load and an unload - decoded and judged alike (live-verified at CREATE FILE FORMAT and
 * at CREATE STAGE): a string's value is its decoded text, so {@code '\t'} names a tab; a parameter given twice
 * conflicts; a name no format knows is an invalid parameter; a value outside its parameter's domain is an invalid
 * value, echoed as written; and an option outside the effective type's tree is not valid for that type. FORMAT_NAME
 * names a format where a stage or a COPY may name one, and COMMENT describes the format object, not its files.
 */
public final class FileFormatClause {

    private final FileFormat format;
    private final String writtenType;
    private final String formatName;
    private final String formatNameWritten;
    private final String comment;

    private FileFormatClause(final FileFormat format, final String writtenType, final String formatName,
                             final String formatNameWritten, final String comment) {
        this.format = format;
        this.writtenType = writtenType;
        this.formatName = formatName;
        this.formatNameWritten = formatNameWritten;
        this.comment = comment;
    }

    /**
     * Read a list of {@code NAME = value} options as CREATE FILE FORMAT, ALTER FILE FORMAT and COPY write them, onto
     * a format.
     *
     * @param options        the options in the order written
     * @param into           the format the options are applied to: a new one, or the one an ALTER changes
     * @param typeChangeable whether TYPE may differ from the format's; an ALTER may not change it
     * @param namesAllowed   whether FORMAT_NAME may name a format, as a stage's list and a COPY's may
     * @return what was written
     */
    public static FileFormatClause read(final List<FrostlakeParser.CopyFormatOptionContext> options,
                                        final FileFormat into, final boolean typeChangeable,
                                        final boolean namesAllowed) {
        final List<WrittenFormatOption> written = new ArrayList<>();
        for (final FrostlakeParser.CopyFormatOptionContext option : options) {
            written.add(writtenOption(option));
        }
        return apply(written, into, typeChangeable, namesAllowed);
    }

    /**
     * Read a parenthesized option list as a stage's FILE_FORMAT and a table's STAGE_FILE_FORMAT write one, onto a
     * format.
     *
     * @param list         the list
     * @param into         the format the options are applied to
     * @param namesAllowed whether FORMAT_NAME may name a format
     * @return what was written
     */
    public static FileFormatClause read(final FrostlakeParser.ParenOptionListContext list, final FileFormat into,
                                        final boolean namesAllowed) {
        final List<WrittenFormatOption> written = new ArrayList<>();
        for (final FrostlakeParser.ParenOptionContext option : list.parenOption()) {
            written.add(writtenOption(option));
        }
        return apply(written, into, true, namesAllowed);
    }

    /** The format the options were applied to. */
    public FileFormat format() {
        return format;
    }

    /** TYPE as the statement wrote it, decoded, or null when it wrote none. */
    public String writtenType() {
        return writtenType;
    }

    /** FORMAT_NAME as a name to resolve - a string's text, a bare name's spelling - or null when none was written. */
    public String formatName() {
        return formatName;
    }

    /** FORMAT_NAME as a stage lists it: a string's text, a bare name as the statement spelled it; or null. */
    public String formatNameWritten() {
        return formatNameWritten;
    }

    /** COMMENT's text, or null when none was written. */
    public String comment() {
        return comment;
    }

    private static WrittenFormatOption writtenOption(final FrostlakeParser.CopyFormatOptionContext option) {
        final String name = optionName(option.getStart().getText());
        if (option.SKIP_HEADER() != null) {
            final String count = (option.MINUS() != null ? "-" : "") + option.INTEGER_LITERAL().getText();
            return new WrittenFormatOption(name, count, count, false, null);
        }
        if (option.LPAREN() != null) {
            return new WrittenFormatOption(name, null, null, false, listed(option.stringLiteralList()));
        }
        return scalar(name, option.copyOptionValue());
    }

    private static WrittenFormatOption writtenOption(final FrostlakeParser.ParenOptionContext option) {
        final String name = optionName(option.optionKey().getText());
        if (option.LPAREN() != null) {
            return new WrittenFormatOption(name, null, null, false, listed(option.stringLiteralList()));
        }
        return scalar(name, option.copyOptionValue());
    }

    private static WrittenFormatOption scalar(final String name, final FrostlakeParser.CopyOptionValueContext value) {
        final boolean string = value.STRING_LITERAL() != null;
        return new WrittenFormatOption(name, ParseTreeText.getOriginalText(value),
            string ? SqlStringLiterals.decode(value.STRING_LITERAL().getText()) : value.getText(), string, null);
    }

    /** A parameter's name in upper case; a quoted name is no parameter of a file format. */
    private static String optionName(final String token) {
        if (token.length() > 1 && token.startsWith("\"") && token.endsWith("\"")) {
            throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + token + "'"));
        }
        return token.toUpperCase(Locale.ROOT);
    }

    private static List<String> listed(final FrostlakeParser.StringLiteralListContext list) {
        final List<String> values = new ArrayList<>();
        if (list != null) {
            for (final TerminalNode node : list.STRING_LITERAL()) {
                values.add(SqlStringLiterals.decode(node.getText()));
            }
        }
        return values;
    }

    private static FileFormatClause apply(final List<WrittenFormatOption> options, final FileFormat into,
                                          final boolean typeChangeable, final boolean namesAllowed) {
        final Set<String> seen = new HashSet<>();
        String writtenType = null;
        String formatName = null;
        String formatNameWritten = null;
        String comment = null;
        WrittenFormatOption compression = null;
        for (final WrittenFormatOption option : options) {
            if (!seen.add(option.name)) {
                throw new RuntimeException(SqlCompilationError.conflictingFileFormatParameter(option.name));
            }
            if ("TYPE".equals(option.name) && option.value != null) {
                FileFormatSurfaces.requireLegalValue("TYPE", option.value, option.written);
                if (!typeChangeable && !option.value.equalsIgnoreCase(into.getType())) {
                    throw new RuntimeException(SqlCompilationError.of("File format type cannot be changed."));
                }
                into.setType(option.value.toUpperCase(Locale.ROOT));
                writtenType = option.value;
            } else if ("FORMAT_NAME".equals(option.name) && namesAllowed && option.value != null) {
                formatName = option.value;
                formatNameWritten = option.string ? option.value : option.written;
            } else if ("COMMENT".equals(option.name)) {
                // COMMENT describes the format object itself; it is not one of its format options.
                comment = option.value != null ? option.value : String.join(",", option.listed);
            } else if (!FileFormatSurfaces.isKnownOption(option.name)) {
                throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + option.name + "'"));
            } else if (option.listed != null) {
                // NULL_IF keeps each value whole (see FileFormat.getNullIfValues); any other list is kept
                // comma-joined so the format round-trips.
                if ("NULL_IF".equals(option.name)) {
                    into.setNullIfValues(option.listed);
                } else {
                    into.setOption(option.name, String.join(",", option.listed));
                }
            } else if ("NULL_IF".equals(option.name)) {
                into.setNullIfValues(Collections.singletonList(option.value));
            } else {
                if ("COMPRESSION".equals(option.name)) {
                    // Which codecs are legal depends on the type, which may be written after COMPRESSION.
                    compression = option;
                } else {
                    FileFormatSurfaces.requireLegalValue(option.name, option.value, option.written);
                }
                into.setOption(option.name, option.value);
            }
        }
        if (compression != null) {
            FileFormatSurfaces.requireLegalCompression(formatName == null || writtenType != null ? into.getType() : null,
                compression.value, compression.written);
        }
        // Cross-type membership is judged against the EFFECTIVE type, wherever TYPE sat in the list; a list that
        // names a format and writes no type of its own is judged by the format it names.
        if (formatName == null || writtenType != null) {
            for (final String key : into.getOptions().keySet()) {
                if (!FileFormatSurfaces.isOptionValidFor(into.getType(), key)) {
                    throw new RuntimeException(SqlCompilationError.of("Option " + key.toUpperCase(Locale.ROOT)
                        + " is not valid for file format type " + into.getType().toUpperCase(Locale.ROOT) + "."));
                }
            }
        }
        return new FileFormatClause(into, writtenType, formatName, formatNameWritten, comment);
    }
}
