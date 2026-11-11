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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * How the refusal of a miscounted call of a user's function writes the block variables the call reads: the call
 * compiles as a query, which reads each variable as a bind named by the variable and a number the block's compilation
 * drew for it, converted to the variable's type - {@code TO_NUMBER(:X_9)}, a text bare as {@code :S_9},
 * {@code CAST(:X_9 AS FLOAT)}, {@code CAST(:X_9 AS BOOLEAN)}, {@code CAST(:X_11 AS DATE)} (live-verified). The numbers
 * count up from 3: two for every literal the block writes, anywhere in it, then, declaration by declaration in the
 * block's order, what the variable's initial value computes - one for a name it reads or a sign, two for a call or a
 * cast, three for an operator between two values - and four for the variable itself, whose bind takes the first; an
 * assignment's value computes alike and takes none. A variable declared without a value and assigned one later reads
 * one lower on the account, which this does not follow (live-verified over 41 blocks).
 */
final class UserCallBinds {

    private static final String CALL_OPENS = "function [";
    private static final int FIRST_NUMBER = 3;
    private static final int PER_LITERAL = 2;
    private static final int PER_VARIABLE = 4;
    private static final int PER_NAME = 1;
    private static final int PER_SIGN = 1;
    private static final int PER_CALL = 2;
    private static final int PER_OPERATOR = 3;
    private static final int WHOLE_PRECISION = 38;

    private UserCallBinds() {
    }

    /**
     * The refusal with each variable the miscounted call reads written as its bind, or null where the call is no user's
     * function, the expression stands in no block, or a variable it reads takes no known bind.
     *
     * @param message    the refusal, each variable written as its bound value (see BoundValueRendering)
     * @param expression the expression as parsed in the block
     * @param types      the types of the names in scope, keyed as the resolver folds them
     * @param values     the values of the names in scope, keyed upper-cased
     * @param functions  the built-in functions, which a user's function is none of
     * @return the refusal, or null
     */
    static String rewrite(final String message, final ParserRuleContext expression, final Map<String, DataType> types,
                          final Map<String, Object> values, final FunctionRegistry functions) {
        final int opens = message.indexOf(CALL_OPENS);
        if (opens < 0 || expression == null) {
            return null;
        }
        final int start = opens + CALL_OPENS.length();
        final int paren = message.indexOf('(', start);
        final int closes = message.lastIndexOf(']');
        if (paren < 0 || closes < paren || functions == null) {
            return null;
        }
        final String called = message.substring(start, paren);
        if (functions.hasFunction(called) || functions.hasAggregateFunction(called)) {
            return null;
        }
        final FrostlakeParser.BeginEndBlockContext block = outermostBlock(expression);
        if (block == null) {
            return null;
        }
        final Map<String, Integer> numbers = numbers(block);
        final StringBuilder call = new StringBuilder(message.substring(start, closes));
        int from = 0;
        for (final String name : variablesRead(expression)) {
            final DataType type = types.get(name);
            final Integer number = numbers.get(name.toUpperCase(Locale.ROOT));
            final String held = name.toUpperCase(Locale.ROOT);
            if (type == null || number == null || !values.containsKey(held)) {
                return null;
            }
            final String bind = bindOf(type, held + "_" + number);
            final Expression valueForm = BoundValueRendering.of(type, values.get(held), false);
            if (bind == null || valueForm == null) {
                return null;
            }
            final String value = AstPrinterVisitor.print(valueForm);
            final int at = call.indexOf(value, from);
            if (at < 0) {
                return null;
            }
            call.replace(at, at + value.length(), bind);
            from = at + bind.length();
        }
        return message.substring(0, start) + call + message.substring(closes);
    }

    /** The bind a variable of {@code type} is read as, or null for a type whose form is not known. */
    private static String bindOf(final DataType type, final String bind) {
        final String read = ":" + bind;
        if (type instanceof StringType) {
            return read;
        }
        if (type instanceof NumericType && !NumericType.isApproximate(type)) {
            final NumericType number = (NumericType) type;
            return number.getPrecision() == WHOLE_PRECISION && number.getScale() == 0 ? "TO_NUMBER(" + read + ")"
                : "TO_NUMBER(" + read + ", " + number.getPrecision() + ", " + number.getScale() + ")";
        }
        if (type instanceof NumericType) {
            return "CAST(" + read + " AS FLOAT)";
        }
        if (type instanceof BooleanType) {
            return "CAST(" + read + " AS BOOLEAN)";
        }
        if (type instanceof DateTimeType && "DATE".equals(type.getName())) {
            return "CAST(" + read + " AS DATE)";
        }
        return null;
    }

    /** The block furthest out that holds the expression, or null. */
    private static FrostlakeParser.BeginEndBlockContext outermostBlock(final ParserRuleContext expression) {
        FrostlakeParser.BeginEndBlockContext found = null;
        for (ParseTree at = expression; at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.BeginEndBlockContext) {
                found = (FrostlakeParser.BeginEndBlockContext) at;
            }
        }
        return found;
    }

    /** The numbers the block's compilation draws for its variables, by upper-cased name: the first declaration's. */
    private static Map<String, Integer> numbers(final FrostlakeParser.BeginEndBlockContext block) {
        final Map<String, Integer> numbers = new HashMap<>();
        final int[] counter = {FIRST_NUMBER + PER_LITERAL * literals(block)};
        declare(block, numbers, counter);
        return numbers;
    }

    private static int literals(final ParseTree node) {
        if (node instanceof FrostlakeParser.LiteralExprContext) {
            return 1;
        }
        int count = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += literals(node.getChild(i));
        }
        return count;
    }

    /** Walks the block in its order, drawing each declaration's value and variable and each assignment's value. */
    private static void declare(final ParseTree node, final Map<String, Integer> numbers, final int[] counter) {
        if (node instanceof FrostlakeParser.DeclarationItemContext) {
            final FrostlakeParser.DeclarationItemContext item = (FrostlakeParser.DeclarationItemContext) node;
            if (item.dataTypeName() != null) {
                variable(item.identifier().getText(), item.booleanExpr(), numbers, counter);
            }
            return;
        }
        if (node instanceof FrostlakeParser.UntypedDeclarationItemContext) {
            final FrostlakeParser.UntypedDeclarationItemContext item =
                (FrostlakeParser.UntypedDeclarationItemContext) node;
            if (item.booleanExpr() != null) {
                variable(item.identifier().getText(), item.booleanExpr(), numbers, counter);
            }
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.CURSOR() == null && let.RESULTSET() == null) {
                variable(let.variableName().getText(), let.booleanExpr(), numbers, counter);
            }
            return;
        }
        if (node instanceof FrostlakeParser.AssignmentStatementContext) {
            final FrostlakeParser.AssignmentStatementContext assignment =
                (FrostlakeParser.AssignmentStatementContext) node;
            if (assignment.booleanExpr() != null) {
                counter[0] += computed(assignment.booleanExpr());
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            declare(node.getChild(i), numbers, counter);
        }
    }

    private static void variable(final String written, final ParseTree value, final Map<String, Integer> numbers,
                                 final int[] counter) {
        if (value != null) {
            counter[0] += computed(value);
        }
        final String name = SqlIdentifiers.canonicalText(written).toUpperCase(Locale.ROOT);
        if (!numbers.containsKey(name)) {
            numbers.put(name, Integer.valueOf(counter[0]));
        }
        counter[0] += PER_VARIABLE;
    }

    /** What a value computes beyond its literals: names and signs, calls and casts, operators. */
    private static int computed(final ParseTree node) {
        int cost = 0;
        if (node instanceof FrostlakeParser.QualifiedNameExprContext
                || node instanceof FrostlakeParser.BindVarExprContext) {
            cost += PER_NAME;
        } else if (node instanceof FrostlakeParser.UnaryExprContext
                || node instanceof FrostlakeParser.NotExprContext) {
            cost += PER_SIGN;
        } else if (node instanceof FrostlakeParser.FunctionCallExprContext
                || node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                || node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                || node instanceof FrostlakeParser.CastExprContext
                || node instanceof FrostlakeParser.CastExpr2Context) {
            cost += PER_CALL;
        } else if (node instanceof FrostlakeParser.AdditiveExprContext
                || node instanceof FrostlakeParser.MultiplicativeExprContext
                || node instanceof FrostlakeParser.ComparisonExprContext
                || node instanceof FrostlakeParser.AndExprContext
                || node instanceof FrostlakeParser.OrExprContext) {
            cost += PER_OPERATOR;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            cost += computed(node.getChild(i));
        }
        return cost;
    }

    /** The names an expression reads as variables, by name or as a bind, in the order written. */
    private static List<String> variablesRead(final ParseTree expression) {
        final List<String> names = new ArrayList<>();
        collect(expression, names);
        return names;
    }

    private static void collect(final ParseTree node, final List<String> names) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name = ((FrostlakeParser.QualifiedNameExprContext) node)
                .qualifiedName();
            if (name.nameStartPart() != null && name.namePart().isEmpty()) {
                names.add(SqlIdentifiers.canonicalText(name.getText()));
            }
            return;
        }
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.variableName() != null) {
                names.add(SqlIdentifiers.canonicalText(bind.variableName().getText()));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), names);
        }
    }
}
