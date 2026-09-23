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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SessionVarExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The argument rules of INFER_SCHEMA, checked while the statement compiles and in the order the account checks
 * them — so of several faults the first one here is the one refused:
 *
 * <ol>
 *   <li>every argument must be named ({@code missing required arguments for function [INFER_SCHEMA]. The
 *       argument name with arrow …}), at least two must be given ({@code not enough arguments …, expected 2,
 *       got 1}), and LOCATION and FILE_FORMAT must be among them and not NULL ({@code missing required argument
 *       [FILE_FORMAT] …}) — each of these at the function's name, like the unknown-argument refusal before
 *       them; then an argument named twice, {@code duplicate property 'LOCATION';};</li>
 *   <li>LOCATION must name a stage — a string starting with {@code @}, else {@code Invalid value 'st/'
 *       provided for argument LOCATION of function INFER_SCHEMA. Please use a named stage location.}, a
 *       number reading as {@code 'null'} there — and the stage, its schema and its database must exist;</li>
 *   <li>FILE_FORMAT must be a text naming a file format that exists, its schema resolved first;</li>
 *   <li>MAX_FILE_COUNT and MAX_RECORDS_PER_FILE must each be a positive whole number, and KIND
 *       {@code 'STANDARD'} or {@code 'ICEBERG'} in any case — else {@code invalid value '0' for property
 *       'MAX_FILE_COUNT'}, the value echoed as written, a text with its quotes;</li>
 *   <li>IGNORE_CASE must be a BOOLEAN ({@code invalid type [VARCHAR(3)] for parameter 'IGNORE_CASE'}), and
 *       FILES a text, a number or a parenthesized list of them;</li>
 *   <li>and the file format must be one INFER_SCHEMA reads: {@code Invalid file format XML}.</li>
 * </ol>
 *
 * <p>Every property takes a constant — a literal or a session variable. An expression is refused with its plan
 * text, {@code invalid value 'UPPER('ffh')' for property 'FILE_FORMAT'}. A NULL KIND or FILES entry is not a
 * compilation error at all but the account's internal one.
 */
final class InferSchemaArguments {

    /** The function's name. */
    static final String FUNCTION = "INFER_SCHEMA";

    /** Every parameter INFER_SCHEMA takes, in its documented order. */
    static final List<String> PARAMETERS = Collections.unmodifiableList(Arrays.asList(
        "LOCATION", "FILE_FORMAT", "FILES", "IGNORE_CASE", "MAX_FILE_COUNT", "MAX_RECORDS_PER_FILE", "KIND"));

    private static final String LOCATION = "LOCATION";
    private static final String FILE_FORMAT = "FILE_FORMAT";
    private static final String FILES = "FILES";
    private static final String IGNORE_CASE = "IGNORE_CASE";
    private static final String MAX_FILE_COUNT = "MAX_FILE_COUNT";
    private static final String MAX_RECORDS_PER_FILE = "MAX_RECORDS_PER_FILE";
    private static final String KIND = "KIND";

    /** The account's internal refusal of a NULL KIND. */
    private static final String NULL_KIND =
        "SQL execution internal error:\nProcessing aborted due to error 300002:3191895034.";

    /** The account's internal refusal of a NULL among the FILES. */
    private static final String NULL_FILE =
        "SQL execution internal error:\nProcessing aborted due to error 300002:2197913990.";

    private final QueryExecutor executor;
    private final ExpressionEvaluator evaluator;

    InferSchemaArguments(final QueryExecutor executor, final ExpressionEvaluator evaluator) {
        this.executor = executor;
        this.evaluator = evaluator;
    }

    /**
     * Refuse a call INFER_SCHEMA does not take. Unknown argument names are refused before this, by the
     * table-function rules every function shares.
     *
     * @param nameToken the function's name, where the arity refusals point
     * @param positional the positional arguments, or null
     * @param named the named arguments, or null
     */
    void validate(final Token nameToken, final List<FrostlakeParser.ExpressionContext> positional,
                  final List<FrostlakeParser.NamedArgumentContext> named) {
        if (positional != null && !positional.isEmpty()) {
            throw at(nameToken, "missing required arguments for function [INFER_SCHEMA]. The argument name with"
                + " arrow (e.g. \"LOCATION=>\") is also required in the query text.");
        }
        final List<FrostlakeParser.NamedArgumentContext> arguments = named == null
            ? new ArrayList<FrostlakeParser.NamedArgumentContext>() : named;
        if (arguments.size() < 2) {
            throw at(nameToken, "not enough arguments for function [INFER_SCHEMA], expected 2, got "
                + arguments.size());
        }
        final Map<String, FrostlakeParser.NamedArgumentContext> byName =
            new HashMap<String, FrostlakeParser.NamedArgumentContext>();
        String duplicate = null;
        for (final FrostlakeParser.NamedArgumentContext argument : arguments) {
            final String name = parameterName(argument);
            if (byName.containsKey(name)) {
                duplicate = duplicate == null ? name : duplicate;
            } else {
                byName.put(name, argument);
            }
        }
        requirePresent(byName.get(LOCATION), LOCATION, nameToken);
        requirePresent(byName.get(FILE_FORMAT), FILE_FORMAT, nameToken);
        if (duplicate != null) {
            throw new RuntimeException(SqlCompilationError.duplicateProperty(duplicate));
        }
        final Catalog catalog = executor.getCatalog();
        final String location = location(byName.get(LOCATION));
        final InferSchemaLocation stage = InferSchemaLocation.parse(location);
        if (stage == null) {
            throw new RuntimeException(SqlCompilationError.of("missing stage name in URL: " + location));
        }
        stage.requireStage(catalog);
        final FileFormat format = fileFormat(byName.get(FILE_FORMAT), catalog);
        requirePositiveCount(byName.get(MAX_FILE_COUNT), MAX_FILE_COUNT);
        requirePositiveCount(byName.get(MAX_RECORDS_PER_FILE), MAX_RECORDS_PER_FILE);
        final boolean nullKind = kind(byName.get(KIND));
        requireBoolean(byName.get(IGNORE_CASE));
        final boolean nullFile = files(byName.get(FILES));
        if ("XML".equalsIgnoreCase(format.getType())) {
            throw new RuntimeException(SqlCompilationError.of("Invalid file format XML"));
        }
        if (nullKind) {
            throw new RuntimeException(NULL_KIND);
        }
        if (nullFile) {
            throw new RuntimeException(NULL_FILE);
        }
    }

    /**
     * The file format a validated FILE_FORMAT names.
     *
     * @param catalog the catalog
     * @param name the FILE_FORMAT argument's value
     * @return the file format
     */
    static FileFormat resolveFileFormat(final Catalog catalog, final String name) {
        final String[] parts = SqlIdentifiers.canonicalTextParts(name);
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String last = parts[parts.length - 1];
        if (!owner.hasFileFormat(last)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("File format", QualifiedName.join(parts)));
        }
        return owner.getFileFormat(last);
    }

    /**
     * An argument's parameter name, upper-cased. A quoted name never gets here: it names no parameter, and the
     * table-function rules refuse it first ({@code unexpected argument ["LOCATION"] at position 1,}).
     */
    static String parameterName(final FrostlakeParser.NamedArgumentContext argument) {
        return argument.identifier().getText().toUpperCase(Locale.ROOT);
    }

    private void requirePresent(final FrostlakeParser.NamedArgumentContext argument, final String name,
                                final Token nameToken) {
        final Expression value = argument == null ? null : single(argument);
        if (argument == null || value != null && isConstant(value) && constant(value) == null) {
            throw at(nameToken, "missing required argument [" + name + "] for function [INFER_SCHEMA]");
        }
    }

    /** LOCATION's value, refused unless it is a text naming a stage. */
    private String location(final FrostlakeParser.NamedArgumentContext argument) {
        final Expression value = single(argument);
        if (value == null || !isConstant(value)) {
            throw invalidValue(argument, LOCATION);
        }
        final Object constant = constant(value);
        final String text = constant instanceof String ? (String) constant : "null";
        if (!text.startsWith("@")) {
            throw new RuntimeException(SqlCompilationError.inline("Invalid value '" + text + "' provided for argument"
                + " LOCATION of function INFER_SCHEMA. Please use a named stage location."));
        }
        return text;
    }

    /** FILE_FORMAT's file format, refused unless it is a text naming one that exists. */
    private FileFormat fileFormat(final FrostlakeParser.NamedArgumentContext argument, final Catalog catalog) {
        final Expression value = single(argument);
        if (value == null || !isConstant(value)) {
            throw invalidValue(argument, FILE_FORMAT);
        }
        final Object constant = constant(value);
        if (!(constant instanceof String)) {
            throw new RuntimeException(SqlCompilationError.of("invalid type [" + evaluator.argumentTypeText(value)
                + "] for parameter 'FILE_FORMAT'"));
        }
        return resolveFileFormat(catalog, (String) constant);
    }

    /** A count, when given, must be a positive whole number. */
    private void requirePositiveCount(final FrostlakeParser.NamedArgumentContext argument, final String name) {
        if (argument == null) {
            return;
        }
        final Expression value = single(argument);
        if (value != null && isConstant(value)) {
            final Object constant = constant(value);
            if (constant instanceof Number) {
                final BigDecimal count = new BigDecimal(constant.toString());
                if (count.signum() > 0 && count.stripTrailingZeros().scale() <= 0) {
                    return;
                }
            }
        }
        throw invalidValue(argument, name);
    }

    /** KIND, when given, must be STANDARD or ICEBERG; whether it is a NULL, refused only after the rest. */
    private boolean kind(final FrostlakeParser.NamedArgumentContext argument) {
        if (argument == null) {
            return false;
        }
        final Expression value = single(argument);
        if (value != null && isConstant(value)) {
            final Object constant = constant(value);
            if (constant == null) {
                return true;
            }
            if (constant instanceof String && ("STANDARD".equalsIgnoreCase((String) constant)
                    || "ICEBERG".equalsIgnoreCase((String) constant))) {
                return false;
            }
        }
        throw invalidValue(argument, KIND);
    }

    /** IGNORE_CASE, when given, must be a BOOLEAN or NULL. */
    private void requireBoolean(final FrostlakeParser.NamedArgumentContext argument) {
        if (argument == null) {
            return;
        }
        final Expression value = single(argument);
        if (value == null || !isConstant(value)) {
            throw invalidValue(argument, IGNORE_CASE);
        }
        final Object constant = constant(value);
        if (constant != null && !(constant instanceof Boolean)) {
            throw new RuntimeException(SqlCompilationError.of("invalid type [" + evaluator.argumentTypeText(value)
                + "] for parameter 'IGNORE_CASE'"));
        }
    }

    /** FILES, when given, must be constants; whether one is a NULL, refused only after the rest. */
    private boolean files(final FrostlakeParser.NamedArgumentContext argument) {
        if (argument == null) {
            return false;
        }
        if (argument.argumentRow() != null) {
            boolean nullFile = false;
            for (final Expression element : rowElements(argument)) {
                if (!isConstant(element)) {
                    throw invalidValue(argument, FILES);
                }
                nullFile = nullFile || constant(element) == null;
            }
            return nullFile;
        }
        final Expression value = single(argument);
        if (value == null || !isConstant(value)) {
            throw invalidValue(argument, FILES);
        }
        return constant(value) == null;
    }

    /** The argument's value when it is one expression; null for a list or a bare subquery. */
    private Expression single(final FrostlakeParser.NamedArgumentContext argument) {
        return argument.expression() == null ? null
            : ExpressionEvaluator.parse(executor.getOriginalText(argument.expression()));
    }

    private List<Expression> rowElements(final FrostlakeParser.NamedArgumentContext argument) {
        final List<Expression> elements = new ArrayList<Expression>();
        for (final FrostlakeParser.ExpressionContext element : argument.argumentRow().expression()) {
            elements.add(ExpressionEvaluator.parse(executor.getOriginalText(element)));
        }
        return elements;
    }

    /** A literal, a negated number literal or a session variable: a value known before any row is read. */
    private static boolean isConstant(final Expression value) {
        if (value instanceof LiteralExpression || value instanceof SessionVarExpression) {
            return true;
        }
        return value instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) value).getOperator() == UnaryOperator.NEGATE
            && isNumberLiteral(((UnaryOperationExpression) value).getOperand());
    }

    private Object constant(final Expression value) {
        if (value instanceof LiteralExpression) {
            return ((LiteralExpression) value).getType() == LiteralType.NULL ? null
                : ((LiteralExpression) value).getValue();
        }
        if (value instanceof UnaryOperationExpression) {
            return new BigDecimal(((LiteralExpression) ((UnaryOperationExpression) value).getOperand())
                .getValue().toString()).negate();
        }
        return evaluator.evaluate(value, null);
    }

    private static boolean isNumberLiteral(final Expression value) {
        return value instanceof LiteralExpression
            && (((LiteralExpression) value).getType() == LiteralType.INTEGER
                || ((LiteralExpression) value).getType() == LiteralType.DECIMAL);
    }

    /** {@code invalid value '…' for property 'X'}, the value as the plan spells it. */
    private RuntimeException invalidValue(final FrostlakeParser.NamedArgumentContext argument, final String name) {
        final String echo;
        if (argument.argumentRow() != null) {
            echo = PropertyValueText.row(rowElements(argument), evaluator);
        } else if (argument.expression() != null) {
            echo = PropertyValueText.of(single(argument), evaluator);
        } else {
            echo = "(" + executor.getOriginalText(argument.selectStatement()) + ")";
        }
        return new RuntimeException(SqlCompilationError.invalidValueForProperty(echo, name));
    }

    private static RuntimeException at(final Token nameToken, final String detail) {
        return new RuntimeException(SqlCompilationError.at(nameToken.getLine(), nameToken.getCharPositionInLine(),
            detail));
    }
}
