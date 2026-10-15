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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.RowExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The arguments a staged file is read with — {@code FROM @stage (FILE_FORMAT => ff, PATTERN => '.*[.]csv')} —
 * judged as the account judges the arguments of the table function behind them (all live-verified).
 *
 * <p>Each value is any expression, named or not, and three things are asked of the list, in this order:
 *
 * <ol>
 *   <li>every value is a constant — a literal, a name, an {@code IDENTIFIER('…')}, in parentheses or not. The
 *       first that is none is told as the tree holds it, under its name as written or, unnamed, under its place
 *       counted with the stage as the first: {@code Invalid argument [FILE_FORMAT=>[unresolved function: "="](TYPE,
 *       CSV)] for table function. Table function argument is required to be a constant.};</li>
 *   <li>every argument is FILE_FORMAT or PATTERN, by an unquoted name. Any other name, a quoted one, and a value
 *       with no name are {@code Unsupported feature 'TABLE'.};</li>
 *   <li>the format exists. A text and a name both name one, as an identifier is read; a number, a boolean and a
 *       NULL name none, and the file is read with the default. A type's name — {@code CSV}, {@code 'JSON'} — is
 *       no format.</li>
 * </ol>
 *
 * <p>Of an argument written twice the last counts. A PATTERN that is a text or a name is the pattern, refused
 * once the format is found when it is no regular expression; a number, a boolean and a NULL are none.
 */
final class StageQueryArguments {

    private final FileFormat format;
    private final String pattern;
    private final boolean formatWritten;

    private StageQueryArguments(final FileFormat format, final String pattern, final boolean formatWritten) {
        this.format = format;
        this.pattern = pattern;
        this.formatWritten = formatWritten;
    }

    /**
     * Read and judge an argument list.
     *
     * @param executor the executor running the query
     * @param params   the list, or null where the stage is written without one
     * @return the arguments
     */
    static StageQueryArguments read(final QueryExecutor executor, final FrostlakeParser.StageQueryParamsContext params) {
        if (params == null) {
            return new StageQueryArguments(null, null, false);
        }
        final List<FrostlakeParser.StageQueryParamContext> written = params.stageQueryParam();
        final List<Expression> values = new ArrayList<>();
        for (final FrostlakeParser.StageQueryParamContext param : written) {
            values.add(ExpressionEvaluator.parse(executor.getOriginalText(param.booleanExpr())));
        }
        for (int i = 0; i < written.size(); i++) {
            if (!isConstant(values.get(i))) {
                final String name = nameOf(written.get(i));
                throw new RuntimeException(SqlCompilationError.of("Invalid argument ["
                    + (name == null ? String.valueOf(i + 2) : name) + "=>" + ParsedEchoPrinter.printUnresolved(values.get(i))
                    + "] for table function. Table function argument is required to be a constant."));
            }
        }
        for (final FrostlakeParser.StageQueryParamContext param : written) {
            if (param.FILE_FORMAT() == null && param.PATTERN() == null) {
                throw new RuntimeException("Unsupported feature 'TABLE'.");
            }
        }
        String formatName = null;
        String pattern = null;
        boolean formatWritten = false;
        for (int i = 0; i < written.size(); i++) {
            final String told = nameTold(values.get(i));
            if (written.get(i).FILE_FORMAT() != null) {
                formatName = told;
                formatWritten = true;
            } else {
                pattern = told;
            }
        }
        final FileFormat format = formatName == null ? null
            : InferSchemaArguments.resolveFileFormat(executor.getCatalog(), formatName);
        if (pattern != null) {
            try {
                Pattern.compile(pattern);
            } catch (final PatternSyntaxException unreadable) {
                throw new RuntimeException(
                    "Failure using stage area. Cause: [Invalid regular expression pattern '" + pattern + "'.]");
            }
        }
        return new StageQueryArguments(format, pattern, formatWritten);
    }

    /** The format the list names, or null where it names none. */
    FileFormat format() {
        return format;
    }

    /**
     * Whether the list writes a FILE_FORMAT at all. One that names none — {@code FILE_FORMAT => NULL} — reads the
     * files by the default format; a list that writes none reads them by the format their stage keeps.
     */
    boolean formatWritten() {
        return formatWritten;
    }

    /** The pattern the list gives, or null where it gives none. */
    String pattern() {
        return pattern;
    }

    /** An argument's name as a refusal tells it — as it is written — or null for a value written without one. */
    private static String nameOf(final FrostlakeParser.StageQueryParamContext param) {
        return param.ARROW() == null ? null : param.getStart().getText();
    }

    /** Whether a value is one the list takes: a literal, a signed number, a name or a name through IDENTIFIER. */
    private static boolean isConstant(final Expression value) {
        if (value instanceof LiteralExpression) {
            return true;
        }
        if (value instanceof ColumnReferenceExpression) {
            return ((ColumnReferenceExpression) value).getPositionalOrdinal() <= 0;
        }
        if (value instanceof UnaryOperationExpression) {
            final UnaryOperationExpression signed = (UnaryOperationExpression) value;
            return signed.getOperator() == UnaryOperator.NEGATE && signed.getOperand() instanceof LiteralExpression
                && (((LiteralExpression) signed.getOperand()).getType() == LiteralType.INTEGER
                    || ((LiteralExpression) signed.getOperand()).getType() == LiteralType.DECIMAL);
        }
        return identified(value) != null;
    }

    /** The text of an {@code IDENTIFIER('…')}, or null for anything else. */
    private static String identified(final Expression value) {
        if (!(value instanceof FunctionCallExpression) || value instanceof RowExpression) {
            return null;
        }
        final FunctionCallExpression call = (FunctionCallExpression) value;
        if (!"IDENTIFIER".equals(call.getFunctionName().toUpperCase(Locale.ROOT)) || call.getArguments().size() != 1
                || !(call.getArguments().get(0) instanceof LiteralExpression)) {
            return null;
        }
        final LiteralExpression text = (LiteralExpression) call.getArguments().get(0);
        return text.getType() == LiteralType.STRING ? String.valueOf(text.getValue()) : null;
    }

    /** The name a constant gives: a text's, a name's as written, or null for a number, a boolean and a NULL. */
    private static String nameTold(final Expression value) {
        if (value instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) value;
            return literal.getType() == LiteralType.STRING ? String.valueOf(literal.getValue()) : null;
        }
        if (value instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression name = (ColumnReferenceExpression) value;
            final String last = SqlIdentifiers.spellCanonical(name.getColumnName());
            return name.isQualified() ? SqlIdentifiers.spellAlreadyCanonicalPath(name.getTableName()) + "." + last : last;
        }
        return identified(value);
    }
}
