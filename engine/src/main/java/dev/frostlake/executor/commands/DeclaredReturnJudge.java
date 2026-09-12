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

import dev.frostlake.executor.DeclarationTypes;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VariantType;

import java.util.HashMap;
import java.util.Map;

/**
 * A procedure's declared RETURNS type against the RETURNs written directly in its own block, judged
 * while the block COMPILES, so the CALL is refused before any statement runs:
 *
 * <pre>
 *   CREATE PROCEDURE p() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN 1631711999; END; $$;  CALL p()
 *       SQL compilation error: error line 1 at position 7
 *        Declared return type 'DATE' is incompatible with actual return type 'NUMBER(10,0)'
 * </pre>
 *
 * <p>The RETURNs judged are the ones the declared type governs: a direct statement of the procedure's
 * own block whose value is a literal (parenthesised or negated, an exponent typed by the value it
 * spells), a cast (typed by its target) or a typed name — a parameter, a bind, a DECLARE or a LET,
 * typed as written or by an initialiser that says: an integer makes a NUMBER(38,0), a decimal a FLOAT,
 * TRUE a BOOLEAN, and a name hands on a whole-number NUMBER. A RETURN inside an IF, a loop, a nested
 * block or a handler keeps its own type and is never judged, and neither is arithmetic or a call.
 *
 * <p>A pair is incompatible exactly when no cast carries it: the plain cast's conversion matrix for a
 * scalar target, and for an OBJECT anything but a VARIANT or an OBJECT, a text included. An ARRAY wraps
 * what it is given and a VARIANT holds it, a text converts to any scalar at run time, and a VARIANT
 * source is only ever cast at run time. Every cell is live-verified.
 */
final class DeclaredReturnJudge {

    /** The type an integer literal gives an untyped declaration. */
    private static final DataType WHOLE_NUMBER = new NumericType("NUMBER", 38, 0);

    private final DataType declared;
    private final Map<String, DataType> typedNames = new HashMap<String, DataType>();
    private final QueryExecutor queryExecutor;

    /**
     * @param declared      the procedure's declared RETURNS type
     * @param typesInScope  the declared types of the names already in scope, keyed by canonical name
     * @param queryExecutor the executor whose functions type an initialiser, or null to type none
     */
    DeclaredReturnJudge(final DataType declared, final Map<String, DataType> typesInScope,
                        final QueryExecutor queryExecutor) {
        this.declared = declared;
        typedNames.putAll(typesInScope);
        this.queryExecutor = queryExecutor;
    }

    /** A DECLARE of the block: typed as written; a cursor, RESULTSET or exception carries no type. */
    void declared(final FrostlakeParser.DeclarationItemContext item) {
        record(item.identifier(), item.dataTypeName() == null ? null
            : writtenType(item.dataTypeName(), item.typeParameters(), false));
    }

    /** An untyped DECLARE of the block, typed by its initialiser. */
    void declared(final FrostlakeParser.UntypedDeclarationItemContext item) {
        record(item.identifier(), initialiserType(item.expression()));
    }

    /** One direct statement of the block, once the name pass has walked it. */
    void after(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.ProceduralStatementContext procedural = statement.proceduralStatement();
        if (procedural == null) {
            return;
        }
        if (procedural.letStatement() != null) {
            let(procedural.letStatement());
        } else if (procedural.returnStatement() != null) {
            judge(procedural.returnStatement());
        }
    }

    private void let(final FrostlakeParser.LetStatementContext let) {
        if (let.dataTypeName() != null) {
            record(let.identifier(), writtenType(let.dataTypeName(), let.typeParameters(), false));
        } else if (let.CURSOR() == null && let.RESULTSET() == null && let.expression() != null) {
            record(let.identifier(), initialiserType(let.expression()));
        } else {
            record(let.identifier(), null);
        }
    }

    private void judge(final FrostlakeParser.ReturnStatementContext ret) {
        if (ret.TABLE() != null || ret.expression() == null) {
            return;
        }
        final DataType actual = returnedType(ret.expression());
        if (!incompatible(actual)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(ret.getStart().getLine(),
            ret.getStart().getCharPositionInLine(), " Declared return type '" + spelled(declared)
                + "' is incompatible with actual return type '" + spelled(actual) + "'"));
    }

    /** Whether no cast carries a value of {@code actual} into the declared type. */
    private boolean incompatible(final DataType actual) {
        if (actual == null || actual instanceof VariantType || StructuredTypes.isStructured(declared)) {
            return false;
        }
        if (declared instanceof ObjectType) {
            return !(actual instanceof ObjectType);
        }
        if (declared instanceof ArrayType || declared instanceof VariantType || actual instanceof StringType) {
            return false;
        }
        return ExpressionEvaluatorVisitor.castMatrixRefuses(actual, declared);
    }

    /** The static type a RETURN's value carries, or null when the declared type does not govern it. */
    private DataType returnedType(final FrostlakeParser.ExpressionContext expression) {
        final FrostlakeParser.ExpressionContext value = bare(expression);
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            return literalType(((FrostlakeParser.LiteralExprContext) value).literal());
        }
        if (value instanceof FrostlakeParser.CastExprContext) {
            final FrostlakeParser.CastExprContext cast = (FrostlakeParser.CastExprContext) value;
            return writtenType(cast.dataTypeName(), cast.typeParameters(), true);
        }
        if (value instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) value;
            return writtenType(cast.dataTypeName(), cast.typeParameters(), true);
        }
        return nameType(value);
    }

    /** The type an untyped declaration takes from its initialiser, or null when it says none. */
    private DataType initialiserType(final FrostlakeParser.ExpressionContext initialiser) {
        final FrostlakeParser.ExpressionContext value = bare(initialiser);
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) value).literal();
            if (literal.INTEGER_LITERAL() != null) {
                return WHOLE_NUMBER;
            }
            if (literal.FLOAT_LITERAL() != null) {
                return NumericType.FLOAT;
            }
            if (literal.TRUE() != null || literal.FALSE() != null) {
                return BooleanType.BOOLEAN;
            }
            // A text declares the full-width VARCHAR (see DeclarationTypes); a NULL declares nothing.
            return literal.NULL() != null ? null
                : DeclarationTypes.ofInitialiser(initialiser, typedNames, queryExecutor);
        }
        final DataType named = nameType(value);
        if (named instanceof NumericType) {
            return ((NumericType) named).getScale() == 0 && !NumericType.isApproximate(named)
                ? named : NumericType.FLOAT;
        }
        // Any other initialiser is typed as a SQL expression over the names in scope.
        return DeclarationTypes.ofInitialiser(initialiser, typedNames, queryExecutor);
    }

    /** A bare name's or a bind's declared type, or null for anything else. */
    private DataType nameType(final FrostlakeParser.ExpressionContext value) {
        if (value instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.IdentifierContext bound = ((FrostlakeParser.BindVarExprContext) value).identifier();
            return bound == null ? null : typedNames.get(ScriptingNameValidator.canonical(bound.getText()));
        }
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName();
            return name.nameStartPart() != null && name.namePart().isEmpty()
                ? typedNames.get(ScriptingNameValidator.canonical(name.nameStartPart().getText())) : null;
        }
        return null;
    }

    /** A literal's own type: a number's NUMBER(p,s), a text's VARCHAR(n), a BOOLEAN; null otherwise. */
    private static DataType literalType(final FrostlakeParser.LiteralContext literal) {
        if (literal.INTEGER_LITERAL() != null || literal.FLOAT_LITERAL() != null) {
            return NumericLiteralTypes.of(NumericLiteralTypes.exactValue(literal.getText()));
        }
        if (literal.STRING_LITERAL() != null) {
            return new StringType("VARCHAR", SqlStringLiterals.decode(literal.getText()).length());
        }
        return literal.TRUE() != null || literal.FALSE() != null ? BooleanType.BOOLEAN : null;
    }

    /** The expression inside any parentheses, and a literal under a minus sign. */
    private static FrostlakeParser.ExpressionContext bare(final FrostlakeParser.ExpressionContext expression) {
        if (expression instanceof FrostlakeParser.ParenExprContext) {
            final FrostlakeParser.BooleanExprContext inner = ((FrostlakeParser.ParenExprContext) expression).booleanExpr();
            return inner instanceof FrostlakeParser.ValueExprContext
                ? bare(((FrostlakeParser.ValueExprContext) inner).expression()) : expression;
        }
        if (expression instanceof FrostlakeParser.UnaryExprContext) {
            final FrostlakeParser.UnaryExprContext signed = (FrostlakeParser.UnaryExprContext) expression;
            final FrostlakeParser.ExpressionContext operand = bare(signed.expression());
            return signed.op.getType() == FrostlakeParser.MINUS
                && operand instanceof FrostlakeParser.LiteralExprContext ? operand : expression;
        }
        return expression;
    }

    /**
     * A written type, or null when it cannot be read: the declared-type pass reports that one itself.
     * A cast target's bare VARCHAR takes the conversion default, a declaration's the column one.
     */
    private static DataType writtenType(final FrostlakeParser.DataTypeNameContext name,
                                        final FrostlakeParser.TypeParametersContext parameters,
                                        final boolean castTarget) {
        try {
            return castTarget ? DataTypeParser.parse(name, parameters, DataTypeParser.CAST_STRING_DEFAULT)
                : DataTypeParser.parse(name, parameters);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    private void record(final FrostlakeParser.IdentifierContext name, final DataType type) {
        if (name == null) {
            return;
        }
        final String key = ScriptingNameValidator.canonical(name.getText());
        if (type == null) {
            typedNames.remove(key);
        } else {
            typedNames.put(key, type);
        }
    }

    /** How the sentence spells a type. */
    private static String spelled(final DataType type) {
        return SqlTypeNames.refusalSpelling(type);
    }
}
