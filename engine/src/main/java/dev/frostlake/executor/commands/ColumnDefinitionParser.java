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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.IntegerLiteralRange;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.expressions.BinaryLiteralText;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.ConstraintNames;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.CodePointText;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses column / data-type / constraint / default-value grammar into metastore models — extracted from
 * {@link DDLCommandHandler} (CREATE TABLE, CREATE FUNCTION/PROCEDURE parameters, DROP FUNCTION arg types,
 * ALTER) so those handlers share one parser. Implements {@link CommandHandler} only to reuse its default
 * {@code getText}/{@code extractComment} helpers; the parsing itself is stateless.
 */
public class ColumnDefinitionParser implements CommandHandler {

    /** Whether a declared referential action pair discards the foreign key: Snowflake supports
     *  NO ACTION only, and a constraint declaring anything else is dropped whole, silently. */
    static boolean dropsForeignKey(final String onDelete, final String onUpdate) {
        return isUnsupportedAction(onDelete) || isUnsupportedAction(onUpdate);
    }

    private static boolean isUnsupportedAction(final String action) {
        return action != null && !"NO ACTION".equalsIgnoreCase(action);
    }

    /**
     * A BINARY column takes NO default at all — not even a binary literal — and the refusal names the
     * family rather than the column, on CREATE TABLE and ALTER TABLE ADD COLUMN alike (live-verified).
     */
    private static void rejectDefaultOnBinaryColumn(final DataType dataType, final boolean sawDefault) {
        if (sawDefault && dataType instanceof BinaryType) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " Default values are not allowed on 'BINARY' columns.");
        }
    }

    /**
     * ALTER TABLE ADD COLUMN takes a BARE LITERAL default and nothing else — not a cast, not
     * arithmetic, not even {@code CURRENT_TIMESTAMP()}, all of which CREATE TABLE accepts
     * (live-verified). The refused expression is echoed in brackets, and a {@code ::} cast is echoed
     * in its CAST spelling rather than as written.
     */
    private void rejectComputedDefaultOnAddColumn(
            final FrostlakeParser.DefaultExpressionContext defaultExpression) {
        if (defaultExpression == null || defaultExpression.expression() == null
                || negatedLiteral(defaultExpression.expression()) != null) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("Invalid column default expression ["
            + castSpelling(defaultExpression.expression()) + "]"));
    }

    /**
     * The literal under a default expression, seeing through a leading MINUS — {@code DEFAULT -1} is a
     * negative literal and is accepted (live-verified), not a computed expression. A leading PLUS is
     * NOT the same: live refuses it, echoing the internal {@code UNARY PLUS(1)}, so it is left to the
     * refusal path.
     *
     * @return the literal, or null when the expression is not one
     */
    private FrostlakeParser.LiteralContext negatedLiteral(final FrostlakeParser.ExpressionContext expr) {
        if (expr instanceof FrostlakeParser.LiteralExprContext) {
            return ((FrostlakeParser.LiteralExprContext) expr).literal();
        }
        if (expr instanceof FrostlakeParser.UnaryExprContext) {
            final FrostlakeParser.UnaryExprContext unary = (FrostlakeParser.UnaryExprContext) expr;
            if (unary.op != null && unary.op.getType() == FrostlakeParser.MINUS
                    && unary.expression() instanceof FrostlakeParser.LiteralExprContext) {
                return ((FrostlakeParser.LiteralExprContext) unary.expression()).literal();
            }
        }
        return null;
    }

    /**
     * A {@code x::T} cast written back as {@code CAST(x AS T)}, and an infix {@code x COLLATE 's'} as
     * the call it is, {@code COLLATE(x, 's')}; every other expression as written.
     */
    private String castSpelling(final FrostlakeParser.ExpressionContext expr) {
        if (expr instanceof FrostlakeParser.CollateExprContext) {
            final FrostlakeParser.CollateExprContext collated = (FrostlakeParser.CollateExprContext) expr;
            final String spec = collated.STRING_LITERAL() != null
                ? collated.STRING_LITERAL().getText() : collated.DOLLAR_QUOTED_STRING().getText();
            return "COLLATE(" + getOriginalText(collated.expression()) + ", " + spec + ")";
        }
        if (expr instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) expr;
            return "CAST(" + getOriginalText(cast.expression()) + " AS "
                + getOriginalText(cast.dataTypeName()).toUpperCase()
                + (cast.typeParameters() != null ? getOriginalText(cast.typeParameters()) : "") + ")";
        }
        return getOriginalText(expr);
    }

    /**
     * ADD COLUMN then requires the literal's own type to be in the column's FAMILY — no coercion at
     * all, so {@code BOOLEAN DEFAULT 'true'} and {@code DATE DEFAULT 7} are both refused here though
     * CREATE TABLE takes them, and a DATE, TIME or TIMESTAMP column accepts NO literal but NULL. Only
     * the family is judged: neither a string's length nor a number's precision is
     * ({@code VARCHAR(2) DEFAULT 'toolong'} and {@code NUMBER(5,1) DEFAULT 123456} are accepted).
     *
     * <p>VARIANT takes no part — it accepts a string, a boolean and a binary literal here and leaves a
     * numeric one to the coercion rule, which is why live answers THAT sentence for it (live-verified).
     */
    private void rejectMistypedLiteralOnAddColumn(final String colName, final DataType dataType,
            final FrostlakeParser.DefaultExpressionContext defaultExpression, final Object defaultValue) {
        if (defaultExpression == null || defaultExpression.expression() == null) {
            return;
        }
        final FrostlakeParser.LiteralContext literal = negatedLiteral(defaultExpression.expression());
        if (literal == null || literal.NULL() != null) {
            return;
        }
        final DataType literalType = literalDataType(literal, defaultValue);
        if (literalType == null || !addColumnJudges(dataType)) {
            return;
        }
        if (!addColumnAccepts(dataType, literalType)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Expression type does not match column data type, expecting "
                + SqlTypeNames.canonical(dataType) + " but got " + SqlTypeNames.canonical(literalType)
                + " for column " + colName.toUpperCase()));
        }
    }

    /**
     * Whether an added column of {@code dataType} takes a literal of {@code literalType}. The numeric
     * family is ONE family here — {@code FLOAT DEFAULT 7} is accepted (live-verified), unlike the
     * policy-attachment notion of a family where NUMBER never matches FLOAT — and a datetime column
     * takes no literal at all, only the NULL the caller has already let through.
     */
    private boolean addColumnAccepts(final DataType dataType, final DataType literalType) {
        return (dataType instanceof NumericType && literalType instanceof NumericType)
            || (dataType instanceof StringType && literalType instanceof StringType)
            || (dataType instanceof BooleanType && literalType instanceof BooleanType);
    }

    /**
     * The families ADD COLUMN judges a literal against; the rest are left to the coercion rule.
     *
     * <p>MAP and VECTOR are in the list because they accept NO literal at all — live refuses every
     * one of a string, a number, a boolean and a binary against them. OBJECT, ARRAY, GEOGRAPHY,
     * GEOMETRY and FILE are genuinely exempt and take any literal (all live-verified), and VARIANT
     * sits out this rule so the coercion one can refuse a numeric default for it.
     */
    private boolean addColumnJudges(final DataType dataType) {
        return dataType instanceof NumericType || dataType instanceof StringType
            || dataType instanceof BooleanType || dataType instanceof DateTimeType
            || dataType instanceof MapType || dataType instanceof VectorType;
    }

    /** A literal's OWN type, spelled as live spells it: VARCHAR(3) for 'abc', NUMBER(2,1) for 7.5. */
    private DataType literalDataType(final FrostlakeParser.LiteralContext literal,
                                     final Object defaultValue) {
        if (literal.STRING_LITERAL() != null || literal.DOLLAR_QUOTED_STRING() != null) {
            return new StringType("VARCHAR",
                defaultValue == null ? 0 : CodePointText.length(String.valueOf(defaultValue)));
        }
        if (literal.INTEGER_LITERAL() != null) {
            return NumericLiteralTypes.forDecimal(new BigDecimal(literal.INTEGER_LITERAL().getText()));
        }
        if (literal.FLOAT_LITERAL() != null) {
            return NumericLiteralTypes.forDecimal(new BigDecimal(literal.FLOAT_LITERAL().getText()));
        }
        if (literal.TRUE() != null || literal.FALSE() != null) {
            return BooleanType.BOOLEAN;
        }
        if (literal.HEX_LITERAL() != null) {
            // X'AB' is one byte per two hex digits, and live reports it BINARY(1) — spelled as the
            // non-fixed variant, since a literal is not a declared width.
            return new BinaryType("VARBINARY",
                BinaryLiteralText.declaredWidth(BinaryLiteralText.decode(literal.HEX_LITERAL().getText())));
        }
        return null;
    }

    /**
     * A DEFAULT must be COERCIBLE to the column's type, judged on the DEFAULT's OWN type — a literal's
     * measured type, or a computed expression's static type. The table is directional and not at all
     * symmetric (every cell live-measured), read as what each column family REFUSES:
     *
     * <ul>
     *   <li>VARCHAR refuses a number and every temporal, so {@code VARCHAR DEFAULT 1 + 1} and
     *       {@code VARCHAR DEFAULT CURRENT_DATE()} are both out, while {@code UPPER('a')} is fine.</li>
     *   <li>BOOLEAN and VARIANT refuse a number and nothing else — {@code BOOLEAN DEFAULT 'true'} is
     *       legal, and so is {@code BOOLEAN DEFAULT CURRENT_DATE()}.</li>
     *   <li>The numeric family refuses a string, so {@code NUMBER DEFAULT '7'} and
     *       {@code NUMBER DEFAULT UPPER('a')} are out while {@code LENGTH('abc')} is fine, and
     *       {@code DATE DEFAULT 7} is legal in the other direction.</li>
     *   <li>DATE and TIME refuse a string AND a TIMESTAMP — a timestamp does not narrow into either,
     *       though {@code TIMESTAMP DEFAULT CURRENT_DATE()} widens happily — and between two temporal
     *       types the flavour and the fractional precision decide ({@link #temporalDefaultFits}).</li>
     *   <li>A BOOLEAN or BINARY default, and NULL, are taken by every family; OBJECT, ARRAY and
     *       GEOGRAPHY judge nothing at all.</li>
     * </ul>
     *
     * <p>A computed default whose static type the channel cannot determine is left alone — the rule
     * never guesses, so an unaudited expression stays accepted rather than being refused on a hunch.
     */
    private void rejectUncoercibleDefault(final String colName, final DataType dataType,
            final FrostlakeParser.DefaultExpressionContext defaultExpression, final Object defaultValue) {
        final DataType source = defaultSourceType(defaultExpression, defaultValue);
        if (source == null || defaultAccepts(dataType, source)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Default value data type does not match data type for column " + colName.toUpperCase()));
    }

    /**
     * A DEFAULT that carries an explicit collation is refused as a mismatched type — written infix or
     * as a call, in parentheses or inside a concatenation, before or after NOT NULL, and even beside a
     * column of the SAME collation; COLLATE '' carries none and is taken (all live-verified). A
     * non-string operand is refused for the operand first, as it is anywhere else.
     */
    private void rejectCollatedDefault(final String colName,
            final FrostlakeParser.DefaultExpressionContext defaultExpression) {
        if (defaultExpression == null || defaultExpression.expression() == null) {
            return;
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(null,
            queryExecutor.getFunctionRegistry(), catalog, queryExecutor);
        final Expression parsed = ExpressionEvaluator.parse(getOriginalText(defaultExpression.expression()));
        if (evaluator.collationOf(parsed) == null) {
            return;
        }
        evaluator.inferStaticType(parsed);
        throw new RuntimeException(SqlCompilationError.of(
            "Default value data type does not match data type for column " + colName.toUpperCase()));
    }

    /** The DEFAULT's own type: a literal's measured type, or a computed expression's static type. */
    private DataType defaultSourceType(final FrostlakeParser.DefaultExpressionContext defaultExpression,
                                       final Object defaultValue) {
        if (defaultExpression == null || defaultExpression.expression() == null) {
            return null;
        }
        final FrostlakeParser.ExpressionContext expr = defaultExpression.expression();
        if (expr instanceof FrostlakeParser.LiteralExprContext) {
            final FrostlakeParser.LiteralContext literal =
                ((FrostlakeParser.LiteralExprContext) expr).literal();
            return literal.NULL() != null ? null : literalDataType(literal, defaultValue);
        }
        return staticDefaultType(expr);
    }

    /**
     * The static type of a computed default, or null when the channel cannot determine one.
     *
     * <p>A COMPILATION error is not "undetermined": a real account compiles a DEFAULT where it stands
     * and refuses it there, so an unknown function, a wrong argument count and a TRY_CAST between
     * types it will not convert all stop the CREATE (live-verified). Anything else the channel cannot
     * read leaves the type unknown, as before - a value-time fault such as {@code 1/0} or
     * {@code TO_NUMBER('x')} is the row's, and live creates those columns.
     */
    private DataType staticDefaultType(final FrostlakeParser.ExpressionContext expr) {
        try {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(null,
                queryExecutor.getFunctionRegistry(), catalog, queryExecutor);
            return evaluator.inferStaticType(ExpressionEvaluator.parse(getOriginalText(expr)));
        } catch (final RuntimeException undetermined) {
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            return null;
        }
    }

    /**
     * Refuse the two shapes a DEFAULT may not have at all, each in the account's own words and at the
     * offending node's own position: a sub-query and an aggregate call. Both are refused at CREATE
     * even where the column's type would take the value (live-verified).
     */
    private void rejectUnsupportedDefaultShape(final FrostlakeParser.DefaultExpressionContext defaultExpression) {
        if (defaultExpression == null || defaultExpression.expression() == null) {
            return;
        }
        rejectDefaultShape(defaultExpression.expression());
    }

    /** The walk behind {@link #rejectUnsupportedDefaultShape}, in source order. */
    private void rejectDefaultShape(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            final Token at = ((ParserRuleContext) node).getStart();
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "sub-queries are not supported as part of the specification of a default value clause."));
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String name = call.functionName().getText().toUpperCase();
            if (queryExecutor.getFunctionRegistry().hasAggregateFunction(name)) {
                final Token at = call.getStart();
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "aggregate functions are not allowed as part of the specification of a default value clause."));
            }
            rejectDefaultCallArity(call, name);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectDefaultShape(node.getChild(i));
        }
    }

    /** Whether a column of {@code dataType} takes a DEFAULT of {@code source} — the table above. */
    private boolean defaultAccepts(final DataType dataType, final DataType source) {
        if (source instanceof BooleanType || source instanceof BinaryType) {
            return true;
        }
        if (dataType instanceof StringType) {
            return source instanceof StringType;
        }
        if (IntervalCasts.isIntervalType(dataType)) {
            // An interval column refuses a text DEFAULT though a write reads text: DEFAULT '1' is out while
            // DEFAULT INTERVAL '1' DAY and DEFAULT NULL are taken (live-verified).
            return !(source instanceof StringType);
        }
        if (dataType instanceof BooleanType || dataType instanceof VariantType) {
            return !(source instanceof NumericType);
        }
        if (dataType instanceof NumericType) {
            return !(source instanceof StringType);
        }
        if (dataType instanceof DateTimeType) {
            if (source instanceof StringType) {
                return false;
            }
            return !(source instanceof DateTimeType)
                || temporalDefaultFits((DateTimeType) dataType, (DateTimeType) source);
        }
        return true;
    }

    /**
     * Whether a temporal column takes a temporal DEFAULT, judged on the DEFAULT's static type — a cast
     * is its target, a clock call its precision — by flavour and fractional precision (the whole table
     * live-measured). A DATE fits every temporal column. A TIMESTAMP fits neither a DATE nor a TIME.
     * Within one flavour the DEFAULT may be narrower than the column but never wider. A TIMESTAMP_TZ
     * fits an NTZ or an LTZ column at any precision. Every other change of flavour, a TIME into a
     * TIMESTAMP included, needs the two precisions to be equal. So a TIMESTAMP_NTZ(3) column takes
     * {@code SYSDATE()::TIMESTAMP_NTZ(3)}, {@code SYSDATE()::TIMESTAMP_LTZ(3)} and
     * {@code CURRENT_TIMESTAMP(3)}, but neither {@code SYSDATE()} (NTZ(9)) nor
     * {@code CURRENT_TIMESTAMP()} (LTZ(9)).
     */
    private static boolean temporalDefaultFits(final DateTimeType column, final DateTimeType source) {
        final String columnFlavour = temporalFlavour(column);
        final String sourceFlavour = temporalFlavour(source);
        if ("DATE".equals(sourceFlavour)) {
            return true;
        }
        if ("DATE".equals(columnFlavour)) {
            return "TIME".equals(sourceFlavour);
        }
        if ("TIME".equals(columnFlavour)) {
            return "TIME".equals(sourceFlavour) && source.getPrecision() <= column.getPrecision();
        }
        if ("TZ".equals(sourceFlavour)) {
            return !"TZ".equals(columnFlavour) || source.getPrecision() <= column.getPrecision();
        }
        if (sourceFlavour.equals(columnFlavour)) {
            return source.getPrecision() <= column.getPrecision();
        }
        return source.getPrecision() == column.getPrecision();
    }

    /** A temporal type's flavour: DATE, TIME, or a TIMESTAMP's NTZ, LTZ or TZ. */
    private static String temporalFlavour(final DateTimeType type) {
        final String name = type.getName().toUpperCase();
        if ("DATE".equals(name) || "TIME".equals(name)) {
            return name;
        }
        if (name.endsWith("_LTZ")) {
            return "LTZ";
        }
        if (name.endsWith("_TZ")) {
            return "TZ";
        }
        return "NTZ";
    }

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public ColumnDefinitionParser(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    /**
     * Build a single {@link TableColumn} from one {@code columnDef} — its data type plus column-level
     * constraints (PRIMARY KEY, NOT NULL, UNIQUE, AUTOINCREMENT/IDENTITY, DEFAULT, column-level
     * REFERENCES, COLLATE, COMMENT). Shared by CREATE TABLE's {@link #parseColumnList} and ALTER TABLE
     * ADD COLUMN, so both honour DEFAULT / NOT NULL identically. Table-level PRIMARY KEY / FOREIGN KEY
     * constraints are the caller's concern, not this method's.
     */
    public TableColumn parseSingleColumnDef(final FrostlakeParser.ColumnDefContext colDef) {
        return parseSingleColumnDef(colDef, false);
    }

    /**
     * As above, with {@code addColumn} telling which surface asked. It matters for DEFAULTs: CREATE
     * TABLE judges one by COERCION (a string default is refused for the numeric and datetime families,
     * a numeric default for VARCHAR / BOOLEAN / VARIANT, and {@code BOOLEAN DEFAULT 'true'} is taken),
     * while ALTER TABLE ADD COLUMN judges the same default far more strictly, in the INSERT surface's
     * own vocabulary. Only the CREATE rule is enforced here — see the ADD COLUMN task for the rest.
     */
    public TableColumn parseSingleColumnDef(final FrostlakeParser.ColumnDefContext colDef,
                                            final boolean addColumn) {
        final String colName = ParseTreeText.namePartText(colDef.columnDefName());
        rejectAnsiReservedColumnName(colName, colDef);
        final DataType dataType = parseDataType(colDef.dataTypeName(), colDef.typeParameters());

        boolean primaryKey = false;
        boolean sawDefault = false;
        boolean notNull = false;
        boolean unique = false;
        boolean autoIncrement = false;
        long identityStart = 1;
        long identityIncrement = 1;
        Object defaultValue = null;
        FrostlakeParser.DefaultExpressionContext defaultExpression = null;
        String referencedTable = null;
        String referencedColumn = null;
        String onDelete = null;
        String onUpdate = null;
        Boolean rely = null;
        String collation = null;
        String maskingPolicy = null;
        String projectionPolicy = null;

        rejectRepeatedColumnConstraints(colName, colDef.columnConstraint());

        for (final FrostlakeParser.ColumnConstraintContext constraint : colDef.columnConstraint()) {
            if (constraint.PRIMARY() != null) {
                primaryKey = true;
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.NOT() != null) {
                notNull = true;
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.UNIQUE() != null) {
                unique = true;
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.AUTOINCREMENT() != null || constraint.IDENTITY() != null) {
                autoIncrement = true;
                // Seed + step from either (start, step) or START <n> INCREMENT <n> (both yield two
                // INTEGER_LITERALs under identityProperties). ORDER/NOORDER parse but don't change values.
                final FrostlakeParser.IdentityPropertiesContext props = constraint.identityProperties();
                if (props != null) {
                    if (props.signedInteger().size() == 2) {
                        // (start, step) paren form
                        identityStart = parseSignedInteger(props.signedInteger(0));
                        identityIncrement = parseSignedInteger(props.signedInteger(1));
                    }
                    for (final FrostlakeParser.IdentityWordOptionContext opt : props.identityWordOption()) {
                        if (opt.START() != null) {
                            identityStart = parseSignedInteger(opt.signedInteger());
                        } else if (opt.INCREMENT() != null) {
                            identityIncrement = parseSignedInteger(opt.signedInteger());
                        }
                        // ORDER / NOORDER parse but don't change values.
                    }
                }
            } else if (constraint.DEFAULT() != null) {
                defaultValue = parseDefaultExpression(constraint.defaultExpression());
                defaultExpression = constraint.defaultExpression();
                sawDefault = true;
            } else if (constraint.REFERENCES() != null) {
                // Column-level foreign key: REFERENCES table(column)
                referencedTable = getText(constraint.qualifiedName());
                // bare `REFERENCES parent` names no column: it defaults to the parent's
                // primary key, resolved when the constraint is registered
                referencedColumn = constraint.identifier() != null
                    ? getText(constraint.identifier()) : null;
                if (constraint.referentialActions() != null) {
                    final String[] actions = parseReferentialActions(constraint.referentialActions());
                    onDelete = actions[0];
                    onUpdate = actions[1];
                }
                rely = parseRelyOption(constraint.relyOption());
            } else if (constraint.PROJECTION() != null) {
                // A projection policy attaches in the column definition too, WITH optional.
                projectionPolicy = getText(constraint.qualifiedName());
            } else if (constraint.MASKING() != null) {
                // A masking policy attached in the column definition itself, on CREATE TABLE and on
                // ALTER TABLE ADD COLUMN alike (live-verified). Its USING argument list is a
                // conditional-policy detail the ALTER path already models; the attachment is what the
                // definition records.
                maskingPolicy = getText(constraint.qualifiedName());
            } else if (constraint.collateClause() != null) {
                // A COLLATE that FOLLOWS the DEFAULT retypes the column under a default already
                // typed without it, and Snowflake refuses the pair (live-verified). The other
                // order — COLLATE first, then DEFAULT — is accepted, as is COLLATE after NOT NULL
                // or UNIQUE.
                if (sawDefault) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "Default value data type does not match data type for column "
                        + colName.toUpperCase()));
                }
                collation = extractCollation(constraint.collateClause());
            }
        }

        if (collation != null) {
            // A collation needs a string column, refused naming the declared type before the
            // specification itself is judged; one that parses is kept lower-cased.
            if (!(dataType instanceof StringType)) {
                throw new RuntimeException(SqlCompilationError.of("Cannot specify column collation for data type '"
                    + SqlTypeNames.canonical(dataType) + "' for column '" + colName.toUpperCase() + "'"));
            }
            collation = CollationSpec.parse(collation).getText();
        }
        rejectDefaultOnBinaryColumn(dataType, sawDefault);
        if (addColumn) {
            rejectComputedDefaultOnAddColumn(defaultExpression);
            rejectMistypedLiteralOnAddColumn(colName, dataType, defaultExpression, defaultValue);
        }
        rejectUnsupportedDefaultShape(defaultExpression);
        rejectCollatedDefault(colName, defaultExpression);
        rejectUncoercibleDefault(colName, dataType, defaultExpression, defaultValue);
        rejectNonNullableFieldsInNullableStructure(colName, dataType, notNull);

        // A PRIMARY KEY column is NOT NULL whether or not the definition says so, and an explicit NULL
        // beside the key does not win. The rule belongs to the declaration: a key added later by
        // ALTER TABLE ... ADD PRIMARY KEY leaves nullability alone.
        final TableColumn column = new TableColumn(colName, dataType, !notNull && !primaryKey, defaultValue,
                                    primaryKey, unique, autoIncrement, identityStart, identityIncrement);
        if (projectionPolicy != null) {
            if (catalog.findProjectionPolicy(projectionPolicy) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                    catalog.qualifiedObjectName(projectionPolicy)));
            }
            column.setProjectionPolicyName(catalog.qualifiedObjectName(projectionPolicy));
        }
        if (maskingPolicy != null) {
            // Recorded in full, exactly as the ALTER path records it: DESCRIBE and GET_DDL report an
            // attached policy fully qualified whichever statement attached it (live-verified). The
            // policy has to be there — live refuses this column definition otherwise, in the same
            // sentence the ALTER form uses.
            if (catalog.findMaskingPolicy(maskingPolicy) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                    catalog.qualifiedObjectName(maskingPolicy)));
            }
            MaskingPolicyAttachment.requireTypeMatch(catalog.findMaskingPolicy(maskingPolicy), column);
            column.setMaskingPolicyName(catalog.qualifiedObjectName(maskingPolicy));
        }

        // Set foreign key info if present. A referential action other than NO ACTION silently
        // drops the WHOLE constraint (live-verified: the table is created, but the foreign key
        // appears in neither SHOW IMPORTED KEYS nor GET_DDL; explicit NO ACTION is kept).
        if (referencedTable != null && !dropsForeignKey(onDelete, onUpdate)) {
            column.setReferencedTable(referencedTable);
            column.setReferencedColumn(referencedColumn);
            column.setOnDelete(onDelete);
            column.setOnUpdate(onUpdate);
        }

        // Set RELY/NORELY if specified
        if (rely != null) {
            column.setRely(rely);
        }

        final String comment = extractComment(colDef.columnCommentClause());
        if (comment != null) {
            column.setComment(comment);
        }

        // A tag written in the column definition is the column's own, as ALTER ... MODIFY COLUMN ... SET TAG sets
        // it; its value has already been judged with the statement's others.
        for (final FrostlakeParser.ColumnConstraintContext constraint : colDef.columnConstraint()) {
            if (constraint.tagList() != null) {
                InlineTags.apply(column, constraint.tagList(), queryExecutor);
            }
        }

        // Set collation if specified
        if (collation != null) {
            column.setCollation(collation);
        }

        return column;
    }

    /**
     * A name used TWICE in one column list, which live refuses:
     * {@code duplicate column name 'C'} — unpositioned, lower-cased, and the name quoted in its
     * CANONICAL spelling rather than as written.
     *
     * <p>★ THE QUOTED PAIR IS THE WHOLE RULE, and it is why the comparison is over canonical names
     * rather than written text:
     *
     * <pre>
     *   (c INT, "c" INT)     ACCEPTED — a quoted lowercase c is a DIFFERENT column
     *   ("C" INT, c INT)     duplicate column name 'C'
     *   ("c" INT, "c" INT)   duplicate column name 'c'   ← the echo keeps the quoted case
     * </pre>
     *
     * <p>★ IT RUNS AFTER THE COLUMNS ARE PARSED, so a bad WIDTH still outranks it:
     * {@code (c VARCHAR(0), c INT)} reports the character-length refusal, which is measured.
     *
     * <p>★ THREE OF THE SAME NAME REPORT ONCE, not once per pair — the first repeat wins.
     */
    private void rejectDuplicateColumnNames(final List<TableColumn> columns) {
        final List<String> names = new ArrayList<>();
        for (final TableColumn column : columns) {
            names.add(column.getName());
        }
        rejectDuplicateNames(names);
    }

    /**
     * The same rule over bare NAMES, for the lists that carry no types — a CTAS's names-only list and a
     * view's column list, both of which live refuses with this same sentence.
     */
    public static void rejectDuplicateNames(final List<String> names) {
        final Set<String> seen = new HashSet<>();
        for (final String name : names) {
            if (!seen.add(name)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "duplicate column name '" + name + "'"));
            }
        }
    }

    public List<TableColumn> parseColumnList(final FrostlakeParser.ColumnListContext ctx) {
        List<TableColumn> columns = new ArrayList<>();
        final List<String> tablePrimaryKeys = new ArrayList<>();
        final List<String> tableUniqueColumns = new ArrayList<>();
        final List<ForeignKeyConstraint> tableForeignKeys = new ArrayList<>();

        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.columnDef() != null) {
                columns.add(parseSingleColumnDef(item.columnDef()));
            } else if (item.tableConstraint() != null) {
                final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
                if (constraint.PRIMARY() != null) {
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        tablePrimaryKeys.add(getText(id));
                    }
                } else if (constraint.UNIQUE() != null) {
                    // Table-level UNIQUE (a, b): every listed column carries the uniqueness flag. The fact
                    // that they form ONE constraint (and any CONSTRAINT <name>) is kept separately — see
                    // parseUniqueConstraints, which the CREATE TABLE handler records on the table.
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        tableUniqueColumns.add(getText(id));
                    }
                } else if (constraint.FOREIGN() != null) {
                    // Table-level foreign key
                    final String constraintName = constraint.constraintName() != null ?
                        getText(constraint.constraintName().identifier()) : null;

                    final List<String> columnNames = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        columnNames.add(getText(id));
                    }

                    final String refTable = getText(constraint.qualifiedName());

                    final List<String> refColumns = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(1).identifier()) {
                        refColumns.add(getText(id));
                    }

                    String onDelete = null;
                    String onUpdate = null;
                    if (constraint.referentialActions() != null) {
                        final String[] actions = parseReferentialActions(constraint.referentialActions());
                        onDelete = actions[0];
                        onUpdate = actions[1];
                    }

                    final Boolean rely = parseRelyOption(constraint.relyOption());

                    // Same silent drop as the column-level form: any action but NO ACTION
                    // discards the constraint while the CREATE succeeds (live-verified).
                    if (!dropsForeignKey(onDelete, onUpdate)) {
                        tableForeignKeys.add(new ForeignKeyConstraint(
                            constraintName, columnNames, refTable, refColumns, onDelete, onUpdate, rely
                        ));
                    }
                }
            }
        }

        if (!tablePrimaryKeys.isEmpty() || !tableUniqueColumns.isEmpty()) {
            final List<TableColumn> updatedColumns = new ArrayList<>();
            for (final TableColumn col : columns) {
                final boolean isPrimaryKey = namesContain(tablePrimaryKeys, col.getName(), columns);
                final boolean isUnique = namesContain(tableUniqueColumns, col.getName(), columns);

                final TableColumn newCol = new TableColumn(
                    col.getName(),
                    col.getDataType(),
                    // Named out of line, the key makes each of its columns NOT NULL exactly as the
                    // inline form does, over whatever the column definition declared.
                    col.isNullable() && !isPrimaryKey,
                    col.getDefaultValue(),
                    isPrimaryKey || col.isPrimaryKey(),
                    isUnique || col.isUnique(),
                    col.isAutoIncrement(),
                    col.getIdentityStart(),      // 9-arg ctor: the 7-arg one resets identity to (1,1)
                    col.getIdentityIncrement()
                );
                newCol.setComment(col.getComment());
                newCol.setCollation(col.getCollation());
                newCol.setRely(col.getRely());

                // Copy foreign key info
                if (col.hasForeignKey()) {
                    newCol.setReferencedTable(col.getReferencedTable());
                    newCol.setReferencedColumn(col.getReferencedColumn());
                    newCol.setOnDelete(col.getOnDelete());
                    newCol.setOnUpdate(col.getOnUpdate());
                }

                updatedColumns.add(newCol);
            }
            columns = updatedColumns;
        }

        rejectDuplicateColumnNames(columns);
        return columns;
    }

    /**
     * Whether a table-level constraint's column list names this column: by its exact name, and in another case
     * only when no column carries the listed name exactly — beside a quoted {@code "x"}, {@code PRIMARY KEY ("x")}
     * names that column alone and not {@code X} (live-verified).
     */
    private boolean namesContain(final List<String> names, final String columnName, final List<TableColumn> columns) {
        for (final String name : names) {
            if (name.equals(columnName)) {
                return true;
            }
            if (name.equalsIgnoreCase(columnName) && !namesAColumnExactly(columns, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesAColumnExactly(final List<TableColumn> columns, final String name) {
        for (final TableColumn column : columns) {
            if (name.equals(column.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A column carries each constraint once. Live-verified, and the two refusals are worded quite
     * differently: a second DEFAULT / AUTOINCREMENT / IDENTITY — they share one slot, so DEFAULT
     * beside AUTOINCREMENT is the same refusal as DEFAULT twice — reports "Multiple DEFAULT or
     * AUTOINCREMENT expressions declared for column X.". A repeated NOT NULL, a NOT NULL beside
     * NULL, and a repeated COLLATE are all ACCEPTED live and stay accepted here. PRIMARY KEY and
     * UNIQUE repeats are caught table-wide instead — their refusals name the TABLE, and two
     * DIFFERENT columns each declaring a primary key is the same refusal as one declaring it twice.
     */
    private static void rejectRepeatedColumnConstraints(final String colName,
            final List<FrostlakeParser.ColumnConstraintContext> constraints) {
        int defaults = 0;
        for (final FrostlakeParser.ColumnConstraintContext constraint : constraints) {
            if (constraint.DEFAULT() != null
                    || constraint.AUTOINCREMENT() != null || constraint.IDENTITY() != null) {
                defaults++;
            }
        }
        if (defaults > 1) {
            throw new RuntimeException(SqlCompilationError.multipleDefaultOrAutoincrement(
                colName.toUpperCase(Locale.ROOT)));
        }
    }

    /**
     * A table declares at most ONE primary key and no two constraints over the SAME columns, however
     * they are spelled — inline on a column or table-level, live-verified for all four shapes (twice
     * on one column, once on each of two columns, inline beside table-level, two table-level). The
     * check is table-wide because both refusals name the table.
     */
    static void rejectDuplicateTableConstraints(final String tableName,
            final FrostlakeParser.ColumnListContext columnList) {
        int primaryKeys = 0;
        final List<String> uniqueSignatures = new ArrayList<>();
        for (final FrostlakeParser.ColumnOrConstraintContext item : columnList.columnOrConstraint()) {
            if (item.columnDef() != null) {
                final String column =
                    ParseTreeText.namePartText(item.columnDef().columnDefName()).toUpperCase(Locale.ROOT);
                for (final FrostlakeParser.ColumnConstraintContext constraint
                        : item.columnDef().columnConstraint()) {
                    if (constraint.PRIMARY() != null) {
                        primaryKeys++;
                    } else if (constraint.UNIQUE() != null) {
                        uniqueSignatures.add(column);
                    }
                }
            } else if (item.tableConstraint() != null) {
                final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
                if (constraint.PRIMARY() != null) {
                    primaryKeys++;
                } else if (constraint.UNIQUE() != null && !constraint.identifierList().isEmpty()) {
                    final List<String> columns = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id
                            : constraint.identifierList().get(0).identifier()) {
                        columns.add(ParseTreeText.getIdentifier(id).toUpperCase(Locale.ROOT));
                    }
                    uniqueSignatures.add(String.join(",", columns));
                }
            }
        }
        if (primaryKeys > 1) {
            throw new RuntimeException(SqlCompilationError.primaryKeyAlreadyExists(
                tableName.toUpperCase(Locale.ROOT)));
        }
        final Set<String> seenUnique = new HashSet<>();
        for (final String signature : uniqueSignatures) {
            if (!seenUnique.add(signature)) {
                throw new RuntimeException(SqlCompilationError.duplicateConstraintSignature(
                    tableName.toUpperCase(Locale.ROOT)));
            }
        }
    }

    /**
     * The name an explicit table-level {@code CONSTRAINT <name> PRIMARY KEY (...)} gave the key, or null
     * when it was declared without one (or only as a column-level {@code PRIMARY KEY}). A table has at most
     * one primary key, so the first such declaration wins.
     */
    public String parsePrimaryKeyConstraintName(final FrostlakeParser.ColumnListContext ctx) {
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
            if (constraint != null && constraint.PRIMARY() != null && constraint.constraintName() != null) {
                return getText(constraint.constraintName().identifier());
            }
        }
        return null;
    }

    /**
     * The table-level UNIQUE constraints of a column list — ONE per declaration, so {@code UNIQUE (a, b)} is
     * a single multi-column constraint rather than one constraint per column. Unnamed ones auto-name
     * themselves; column-level {@code UNIQUE} declarations are not returned here (they are plain column
     * flags and {@code Table} names them itself).
     */
    public List<UniqueConstraint> parseUniqueConstraints(final FrostlakeParser.ColumnListContext ctx) {
        final List<UniqueConstraint> uniques = new ArrayList<>();
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
            if (constraint == null || constraint.UNIQUE() == null) {
                continue;
            }
            final String constraintName = constraint.constraintName() != null
                ? getText(constraint.constraintName().identifier()) : null;
            final List<String> columnNames = new ArrayList<>();
            for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                columnNames.add(getText(id));
            }
            uniques.add(new UniqueConstraint(constraintName, columnNames));
        }
        return uniques;
    }

    /**
     * Every CHECK constraint a column list declares, in declaration order — the column-level ones as
     * their columns appear, then the table-level ones. GET_DDL renders them all as table-level lines
     * whichever way they were written (live-verified), so both forms land in the same list here.
     *
     * @param ctx         the parsed column list
     * @param columnNames the table's column names, to work out which columns each expression names
     */
    public List<CheckConstraint> parseCheckConstraints(final FrostlakeParser.ColumnListContext ctx,
                                                       final List<String> columnNames) {
        final List<CheckConstraint> checks = new ArrayList<>();
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.columnDef() != null) {
                for (final FrostlakeParser.ColumnConstraintContext constraint
                        : item.columnDef().columnConstraint()) {
                    if (constraint.CHECK() != null) {
                        checks.add(buildCheckConstraint(null, constraint.booleanExpr(), columnNames));
                    }
                }
            } else if (item.tableConstraint() != null && item.tableConstraint().checkConstraint() != null) {
                final FrostlakeParser.CheckConstraintContext check = item.tableConstraint().checkConstraint();
                checks.add(buildCheckConstraint(check.constraintName(), check.booleanExpr(), columnNames));
            }
        }
        return checks;
    }

    /**
     * One CHECK constraint from its parse tree: the expression kept as written, an unnamed one given
     * the auto-generated name live reports it under, and the columns it names read from the tree.
     */
    public CheckConstraint buildCheckConstraint(final FrostlakeParser.ConstraintNameContext nameCtx,
                                                final FrostlakeParser.BooleanExprContext expr,
                                                final List<String> columnNames) {
        final boolean autoNamed = nameCtx == null;
        final String name = autoNamed ? ConstraintNames.generate() : getText(nameCtx.identifier());
        return new CheckConstraint(name, getOriginalText(expr), autoNamed,
            referencedColumns(expr, columnNames));
    }

    /**
     * The table columns an expression names, read from the PARSE TREE rather than from its text: every
     * column reference in the expression is a qualified-name node, so collecting those and keeping the
     * ones that match a column tells them apart from function names and literals.
     */
    private List<String> referencedColumns(final org.antlr.v4.runtime.tree.ParseTree tree,
                                           final List<String> columnNames) {
        final List<String> referenced = new ArrayList<>();
        collectReferencedColumns(tree, columnNames, referenced);
        return referenced;
    }

    private void collectReferencedColumns(final org.antlr.v4.runtime.tree.ParseTree tree,
                                          final List<String> columnNames, final List<String> into) {
        if (tree instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) tree).qualifiedName();
            final String[] parts = ParseTreeText.qualifiedNameParts(name);
            final String last = parts[parts.length - 1];
            for (final String column : columnNames) {
                if (column.equalsIgnoreCase(last) && !into.contains(column)) {
                    into.add(column);
                }
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectReferencedColumns(tree.getChild(i), columnNames, into);
        }
    }

    public List<ForeignKeyConstraint> parseForeignKeys(final FrostlakeParser.ColumnListContext ctx) {
        final List<ForeignKeyConstraint> foreignKeys = new ArrayList<>();

        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.tableConstraint() != null) {
                final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
                if (constraint.FOREIGN() != null) {
                    final String constraintName = constraint.constraintName() != null ?
                        getText(constraint.constraintName().identifier()) : null;

                    final List<String> columnNames = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        columnNames.add(getText(id));
                    }

                    final String refTable = getText(constraint.qualifiedName());

                    final List<String> refColumns = new ArrayList<>();
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(1).identifier()) {
                        refColumns.add(getText(id));
                    }

                    String onDelete = null;
                    String onUpdate = null;
                    if (constraint.referentialActions() != null) {
                        final String[] actions = parseReferentialActions(constraint.referentialActions());
                        onDelete = actions[0];
                        onUpdate = actions[1];
                    }

                    final Boolean rely = parseRelyOption(constraint.relyOption());

                    // Same silent drop as the column-level form: any action but NO ACTION
                    // discards the constraint while the CREATE succeeds (live-verified).
                    if (!dropsForeignKey(onDelete, onUpdate)) {
                        foreignKeys.add(new ForeignKeyConstraint(
                            constraintName, columnNames, refTable, refColumns, onDelete, onUpdate, rely
                        ));
                    }
                }
            }
        }

        return foreignKeys;
    }

    /**
     * A POLICY signature's parameter. Identical to {@link #parseParameterDef} but for one measured
     * detail: a string type declared without a length is 128MB wide here, not the column default —
     * live reports a bare VARCHAR policy argument as {@code VARCHAR(134217728)} and an explicitly
     * sized one as it was written.
     */
    public Parameter parsePolicyParameterDef(final FrostlakeParser.ParameterDefContext p) {
        final Parameter parameter = parseParameterDef(p);
        if (p.typeParameters() == null && parameter.getDataType() instanceof StringType) {
            return new Parameter(parameter.getName(),
                new StringType(parameter.getDataType().getName(), POLICY_VARCHAR_LENGTH),
                parameter.getDefaultValue());
        }
        return parameter;
    }

    /** How wide a policy signature's unsized string parameter is, as live spells it back. */
    private static final int POLICY_VARCHAR_LENGTH = 134217728;

    public Parameter parseParameterDef(final FrostlakeParser.ParameterDefContext p) {
        final String name = getText(p.identifier());
        final DataType type = parseDataType(p.dataTypeName(), p.typeParameters());
        final String defaultVal = p.expression() != null ? getOriginalText(p.expression()) : null;
        return new Parameter(name, type, defaultVal);
    }


    /**
     * The ANSI-reserved context names may not name a column DEFINITION, although they stay legal
     * as aliases — live-verified, including the leading dot of the message:
     * {@code .invalid column definition name 'CURRENT_DATE' (ANSI reserved)}, positioned on the
     * column name.
     */
    private static void rejectAnsiReservedColumnName(final String colName,
                                                     final FrostlakeParser.ColumnDefContext colDef) {
        final String upper = colName.toUpperCase();
        if (!"CURRENT_DATE".equals(upper) && !"CURRENT_TIME".equals(upper)
                && !"CURRENT_TIMESTAMP".equals(upper) && !"CURRENT_USER".equals(upper)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(
            colDef.columnDefName().getStart().getLine(),
            colDef.columnDefName().getStart().getCharPositionInLine(),
            ".invalid column definition name '" + upper + "' (ANSI reserved)"));
    }

    public DataType parseDataType(final FrostlakeParser.DataTypeNameContext ctx) {
        return parseDataType(ctx, null);
    }

    public DataType parseDataType(final FrostlakeParser.DataTypeNameContext ctx, final FrostlakeParser.TypeParametersContext typeParams) {
        return sessionMappedTimestamp(DataTypeParser.parse(ctx, typeParams));
    }

    /**
     * A bare TIMESTAMP resolved to the flavour the session's TIMESTAMP_TYPE_MAPPING names. Live
     * resolves it when the column is CREATED and keeps it: a column made under TIMESTAMP_LTZ still
     * DESCRIBEs as TIMESTAMP_LTZ(9) after the session switches back to TIMESTAMP_NTZ, so the mapping
     * is not consulted again on read. The three explicit spellings never reach here — only the bare
     * word is unresolved, and it is the only one that carries the name TIMESTAMP.
     *
     * @param parsed the type as written
     * @return the resolved type, or the same type when it is not a bare TIMESTAMP
     */
    private DataType sessionMappedTimestamp(final DataType parsed) {
        if (!(parsed instanceof DateTimeType) || !"TIMESTAMP".equalsIgnoreCase(parsed.getName())) {
            return parsed;
        }
        final int precision = ((DateTimeType) parsed).getPrecision();
        final String mapping = sessionTimestampMapping();
        if ("TIMESTAMP_LTZ".equals(mapping) || "TIMESTAMP_TZ".equals(mapping)) {
            return new DateTimeType(mapping, precision, true);
        }
        return new DateTimeType("TIMESTAMP_NTZ", precision, false);
    }

    /**
     * The session's TIMESTAMP_TYPE_MAPPING, or the account default when the session has not set one.
     *
     * @return the mapping name, upper-cased
     */
    private String sessionTimestampMapping() {
        final SecurityManager securityManager = queryExecutor == null
            ? null : queryExecutor.getSecurityManager();
        final Object set = securityManager == null ? null
            : securityManager.getSessionContext().getSessionParameter("TIMESTAMP_TYPE_MAPPING");
        return set == null ? "TIMESTAMP_NTZ"
            : String.valueOf(set).trim().replace("'", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Snowflake's structured-nullability DDL rule, every cell live-measured:
     * a NOT NULL structured field is legal only when EVERYTHING enclosing it is itself non-nullable.
     * A NOT NULL field whose immediately-enclosing object (the column, or an object-typed field) is
     * nullable fails with "DDL operation failed because it would result in a non-nullable structured
     * type field '&lt;path&gt;' contained within a nullable object" — so {@code o OBJECT(inner
     * OBJECT(x INT NOT NULL)) NOT NULL} is still refused ('O.inner.x'; {@code inner} is nullable)
     * while marking every level NOT NULL is accepted. Under an ARRAY or MAP the refusal is
     * unconditional — "'&lt;path&gt;' contained within an array or map" — with path segments
     * {@code .element} (array) / {@code .value} (map), even when the column is NOT NULL.
     */
    private void rejectNonNullableFieldsInNullableStructure(final String columnName,
                                                            final DataType dataType,
                                                            final boolean columnNotNull) {
        walkStructuredNullability(columnName, dataType, !columnNotNull, false);
    }

    /**
     * @param path              dotted path to this position ({@code COL}, {@code COL.f},
     *                          {@code COL.a.element}, …) — upper column name, field names verbatim
     * @param enclosingNullable whether the immediately-enclosing object or column is nullable here
     * @param insideContainer   whether an ARRAY or MAP lies between the column and this position
     */
    private void walkStructuredNullability(final String path, final DataType type,
                                           final boolean enclosingNullable,
                                           final boolean insideContainer) {
        if (type instanceof StructuredObjectType) {
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                final String fieldPath = path + "." + field.getName();
                if (field.isNotNull() && insideContainer) {
                    throw new RuntimeException("SQL compilation error: DDL operation failed because it"
                        + " would result in a non-nullable structured type field '" + fieldPath
                        + "' contained within an array or map");
                }
                if (field.isNotNull() && enclosingNullable) {
                    throw new RuntimeException("SQL compilation error: DDL operation failed because it"
                        + " would result in a non-nullable structured type field '" + fieldPath
                        + "' contained within a nullable object");
                }
                walkStructuredNullability(fieldPath, field.getDataType(), !field.isNotNull(),
                    insideContainer);
            }
        } else if (type instanceof MapType) {
            walkStructuredNullability(path + ".value", ((MapType) type).getValueType(), true, true);
        } else if (type instanceof ArrayType && ((ArrayType) type).getElementType() != null) {
            walkStructuredNullability(path + ".element", ((ArrayType) type).getElementType(),
                true, true);
        }
    }

    /** The canonical {onDelete, onUpdate} pair — each a spelled-out option ("NO ACTION",
     *  "SET NULL", …) or null when the clause is absent. */
    static String[] parseReferentialActions(final FrostlakeParser.ReferentialActionsContext ctx) {
        String onDelete = null;
        String onUpdate = null;

        for (final FrostlakeParser.ReferentialActionContext action : ctx.referentialAction()) {
            final String actionValue = parseReferentialOption(action.referentialOption());
            if (action.DELETE() != null) {
                onDelete = actionValue;
            } else if (action.UPDATE() != null) {
                onUpdate = actionValue;
            }
        }

        return new String[] { onDelete, onUpdate };
    }

    private static String parseReferentialOption(final FrostlakeParser.ReferentialOptionContext ctx) {
        if (ctx.CASCADE() != null) {
            return "CASCADE";
        } else if (ctx.SET() != null && ctx.NULL() != null) {
            return "SET NULL";
        } else if (ctx.SET() != null && ctx.DEFAULT() != null) {
            return "SET DEFAULT";
        } else if (ctx.RESTRICT() != null) {
            return "RESTRICT";
        } else if (ctx.NO() != null && ctx.ACTION() != null) {
            return "NO ACTION";
        }
        return null;
    }

    private Boolean parseRelyOption(final FrostlakeParser.RelyOptionContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.RELY() != null) {
            return true;
        } else if (ctx.NORELY() != null) {
            return false;
        }
        return null;
    }

    public Object parseDefaultExpression(final FrostlakeParser.DefaultExpressionContext ctx) {
        if (ctx.expression() != null) {
            final FrostlakeParser.ExpressionContext expr = ctx.expression();

            // Handle literal expressions
            if (expr instanceof FrostlakeParser.LiteralExprContext) {
                final FrostlakeParser.LiteralExprContext literalCtx = (FrostlakeParser.LiteralExprContext) expr;
                // Keep DEFAULT NULL as the canonical text "NULL" (mirrors the qualified-name path below),
                // so the column records an explicit NULL default that is evaluated at insert time.
                if (literalCtx.literal().NULL() != null) {
                    return "NULL";
                }
                return parseLiteral(literalCtx.literal());
            }

            // Handle qualified names (identifiers like CURRENT_TIMESTAMP, TRUE, FALSE, NULL)
            if (expr instanceof FrostlakeParser.QualifiedNameExprContext) {
                final FrostlakeParser.QualifiedNameExprContext nameCtx = (FrostlakeParser.QualifiedNameExprContext) expr;
                final String name = getText(nameCtx.qualifiedName()).toUpperCase();
                // Return recognized special values
                if (name.equals("CURRENT_TIMESTAMP") || name.equals("CURRENT_DATE") ||
                    name.equals("CURRENT_TIME") || name.equals("TRUE") ||
                    name.equals("FALSE") || name.equals("NULL")) {
                    return name;
                }
                // A BARE identifier in a DEFAULT has nothing legal to name: a column reference is
                // refused AT CREATE, at the identifier's own position (live-verified — the table
                // is never created). A sequence default is the dotted seq.NEXTVAL form and never
                // lands here.
                if (!name.contains(".")) {
                    final Token start = nameCtx.qualifiedName().getStart();
                    throw new RuntimeException(SqlCompilationError.at(start.getLine(),
                        start.getCharPositionInLine(), "invalid identifier '" + name + "'"));
                }
            }

            // Handle function calls (store as canonical name for evaluation at insert time)
            if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
                final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
                final String funcName = funcCtx.functionName().getText().toUpperCase();
                if (funcName.equals("CURRENT_TIMESTAMP") || funcName.equals("CURRENT_DATE") ||
                    funcName.equals("CURRENT_TIME")) {
                    return funcName;
                }
                if (funcName.equals("UUID_STRING")) {
                    return "UUID_STRING()";
                }
            }

            // Handle parenthesized expressions - unwrap them
            if (expr instanceof FrostlakeParser.ParenExprContext) {
                final FrostlakeParser.ParenExprContext parenCtx = (FrostlakeParser.ParenExprContext) expr;
                final FrostlakeParser.DefaultExpressionContext innerCtx = new FrostlakeParser.DefaultExpressionContext(null, 0);
                final FrostlakeParser.BooleanExprContext be = parenCtx.booleanExpr();
                if (be instanceof FrostlakeParser.ValueExprContext) {
                    innerCtx.addChild(((FrostlakeParser.ValueExprContext) be).expression());
                }
                return parseDefaultExpression(innerCtx);
            }

            // Recognized context/datetime keywords can reach here as other context shapes (e.g. bare
            // CURRENT_TIMESTAMP); keep returning their canonical string so evaluateDefaultValue resolves
            // them and the stored metadata stays a plain keyword (not an opaque expression).
            final String canonical = getOriginalText(expr).trim().toUpperCase();
            switch (canonical) {
                case "CURRENT_TIMESTAMP":
                case "CURRENT_TIMESTAMP()":
                    return "CURRENT_TIMESTAMP";
                case "CURRENT_DATE":
                case "CURRENT_DATE()":
                    return "CURRENT_DATE";
                case "CURRENT_TIME":
                case "CURRENT_TIME()":
                    return "CURRENT_TIME";
                case "UUID_STRING()":
                    return "UUID_STRING()";
                case "TRUE":
                    return "TRUE";
                case "FALSE":
                    return "FALSE";
                case "NULL":
                    return "NULL";
                default:
                    break;
            }

            // A complex expression (arithmetic, concatenation, function call, CASE, …): keep the text
            // wrapped so INSERT evaluates it per row, rather than inserting the raw text as the value.
            // Use the ORIGINAL source text (whitespace preserved) — ctx.getText() concatenates tokens with
            // no spaces (e.g. "CASE WHEN 1=1 THEN…" → "CASEWHEN1=1THEN…"), which cannot be re-parsed.
            return new DefaultValueExpression(getOriginalText(expr));
        }
        return null;
    }

    public Object parseLiteral(final FrostlakeParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            // A DEFAULT reads its literal here rather than through the expression AST, so it needs the
            // reader's width refusal of its own.
            IntegerLiteralRange.reject(ctx.INTEGER_LITERAL().getSymbol());
            // Up to 38 digits are an exact NUMBER(38,0); one past a long's range stays exact as a BigDecimal.
            final BigInteger digits = new BigInteger(ctx.INTEGER_LITERAL().getText());
            return digits.bitLength() < Long.SIZE ? (Object) Long.valueOf(digits.longValue()) : new BigDecimal(digits);
        } else if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            return extractStringLiteral(ctx.STRING_LITERAL());
        } else if (ctx.DOLLAR_QUOTED_STRING() != null) {
            final String text = ctx.DOLLAR_QUOTED_STRING().getText();
            if (text.startsWith("$$") && text.endsWith("$$")) {
                return text.substring(2, text.length() - 2);
            }
            return text;
        } else if (ctx.TRUE() != null) {
            return true;
        } else if (ctx.FALSE() != null) {
            return false;
        } else if (ctx.NULL() != null) {
            // A NULL literal is a real null. (Passing NULL as a CALL argument or SET value must bind
            // null, not the string "NULL" — otherwise :param IS NOT NULL is wrongly true.)
            return null;
        }
        return null;
    }

    private String extractCollation(final FrostlakeParser.CollateClauseContext ctx) {
        return writtenCollation(ctx);
    }

    /** A COLLATE clause's specification as written, in either quoting, or null without a clause. */
    static String writtenCollation(final FrostlakeParser.CollateClauseContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(ctx.STRING_LITERAL().getText());
        }
        final String dollar = ctx.DOLLAR_QUOTED_STRING().getText();
        return dollar.substring(2, dollar.length() - 2);
    }

    /**
     * A COLLATE clause's specification validated and lower-cased, as the catalog keeps it — live
     * reports a column declared COLLATE 'EN-CI' as 'en-ci' in COLLATION, DESCRIBE and GET_DDL alike —
     * or null without a clause.
     */
    static String storedCollation(final FrostlakeParser.CollateClauseContext ctx) {
        final String written = writtenCollation(ctx);
        return written == null ? null : CollationSpec.parse(written).getText();
    }

    private String extractStringLiteral(final TerminalNode node) {
        return SqlStringLiterals.decode(node.getText());
    }

    private String getOriginalText(final ParserRuleContext ctx) {
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex())
        );
    }

    /** The value of a signedInteger context ({@code MINUS? INTEGER_LITERAL}). */
    private static long parseSignedInteger(final FrostlakeParser.SignedIntegerContext ctx) {
        final long value = Long.parseLong(ctx.INTEGER_LITERAL().getText());
        return ctx.MINUS() != null ? -value : value;
    }


    /**
     * A DEFAULT's call is counted where it stands. A real account refuses a wrong argument count at
     * CREATE, in the two sentences it uses everywhere else - the too-FEW form carries a comma after
     * the bracket and the too-many form does not, both live-verified - and the engine's own evaluation
     * path already builds them; a DEFAULT never reached it, because a default is typed, not evaluated.
     * Only a built-in the registry knows is counted: an unknown name is refused by the type channel.
     */
    private void rejectDefaultCallArity(final FrostlakeParser.FunctionCallExprContext call, final String name) {
        final BuiltInFunction function = queryExecutor.getFunctionRegistry().getFunction(name);
        if (function == null) {
            return;
        }
        final int given = call.functionArgList() == null ? 0 : call.functionArgList().functionArg().size();
        final Token at = call.getStart();
        final String echoed = ParseTreeText.getOriginalText(call);
        if (given < function.getMinArgCount()) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "not enough arguments for function [" + echoed + "], expected "
                    + function.getMinArgCount() + ", got " + given));
        }
        if (!function.isVariadic() && function.getMaxArgCount() >= 0 && given > function.getMaxArgCount()) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "too many arguments for function [" + echoed + "] expected "
                    + function.getMaxArgCount() + ", got " + given));
        }
    }

}
