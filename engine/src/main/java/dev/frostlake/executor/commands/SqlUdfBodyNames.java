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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.DateTimeUnitSlot;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.datetime.DateUnitVocabulary;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The names a SQL UDF's EXPRESSION body may use. Such a body has no FROM clause, so every bare name in
 * it is one the signature declares or nothing at all, and a real account resolves them at CREATE: it
 * refuses the first unknown one with the positioned invalid-identifier sentence, in the body's own
 * frame - line 1 counts from position 1, every later line from 0 - and a comment before the name does
 * not move it (live-verified).
 *
 * <p>Only a name written as ONE part is judged. A dotted name is something else - {@code s1.nextval}
 * reaches a sequence, and a real account creates that function - and the keyword constructs that look
 * like bare names ({@code CURRENT_DATE}, {@code CURRENT_TIMESTAMP}, {@code LOCALTIME}) are their own
 * expressions in the grammar, so they never reach here.
 *
 * <p>Parameters match without regard to case: a body over {@code (x INT)} may write {@code x + 1} or
 * {@code X + 1}, and both are created.
 *
 * <p>Two kinds of bare word are no names of the signature's at all. The UNIT of a date or time function —
 * {@code DATEADD(day, 1, t)}, {@code LAST_DAY(d, month)} — is a word the function reads: one that is no unit is
 * refused in the function's own sentence, and one that is a parameter's name is refused too, a unit being an
 * identifier or a string literal and never a value. And the parameters a LAMBDA binds — {@code FILTER(a, x -> x >
 * 1)}, {@code REDUCE(a, 0, (acc, v) -> acc + v)} — are names inside that lambda's body, beside the signature's
 * (live-verified).
 */
final class SqlUdfBodyNames {

    private SqlUdfBodyNames() {
    }

    /**
     * Refuse the first name in the body that the signature does not declare.
     *
     * @param body       the parsed expression body
     * @param parameters the canonical names the signature declares, upper-cased
     */
    static void rejectUnknownNames(final ParseTree body, final Set<String> parameters) {
        rejectUnknownNames(body, parameters, 1);
    }

    /**
     * Refuse the first name in the body that the signature does not declare, for a text whose first line stands
     * {@code firstLineShift} characters from the frame's: 1 for the body as written, 0 for a statement that already
     * opens with the frame's own parenthesis.
     *
     * @param body           the parsed expression body
     * @param parameters     the canonical names the signature declares, upper-cased
     * @param firstLineShift how far the text's first line stands from the frame's
     */
    static void rejectUnknownNames(final ParseTree body, final Set<String> parameters, final int firstLineShift) {
        final FrostlakeParser.QualifiedNameContext unknown = firstUnknown(body, parameters);
        if (unknown == null) {
            return;
        }
        final Token at = unknown.getStart();
        throw new RuntimeException(SqlCompilationError.at(at.getLine(),
            at.getCharPositionInLine() + (at.getLine() == 1 ? firstLineShift : 0),
            "invalid identifier '" + SqlIdentifiers.spellAsWritten(unknown.getText()) + "'"));
    }

    /**
     * Whether a single-part name is one the signature declares: an unquoted one by its upper-cased spelling, a quoted
     * one, {@code "V"}, by the exact name it spells, as any identifier is read. A quoted spelling of a parameter was
     * an unknown name, so a body reading its parameter as {@code "V"} was refused.
     */
    private static boolean declares(final Set<String> parameters, final String written) {
        return parameters.contains(written.toUpperCase(Locale.ROOT)) || parameters.contains(SqlIdentifiers.canonicalText(written));
    }

    /** The first single-part name the signature does not declare, in source order, or null. */
    private static FrostlakeParser.QualifiedNameContext firstUnknown(final ParseTree node,
                                                                     final Set<String> parameters) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // A SUBQUERY inside the body brings its own FROM, so the names under it are that relation's
            // and not the signature's. They are resolved where the subquery runs, as they always were.
            return null;
        }
        if (node instanceof FrostlakeParser.LambdaFunctionContext) {
            // The lambda's own parameters are names of its body, with the signature's.
            final FrostlakeParser.LambdaFunctionContext lambda = (FrostlakeParser.LambdaFunctionContext) node;
            final Set<String> inside = new HashSet<String>(parameters);
            for (final FrostlakeParser.LambdaParamContext bound : lambda.lambdaParams().lambdaParam()) {
                inside.add(SqlIdentifiers.canonicalText(bound.identifier().getText()));
            }
            return firstUnknown(lambda.booleanExpr(), inside);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final FrostlakeParser.QualifiedNameContext unit = unitWordOf(call);
            if (unit != null) {
                requireUnit(call.functionName().getText().toUpperCase(Locale.ROOT), unit, parameters);
                final List<FrostlakeParser.FunctionArgContext> arguments = call.functionArgList().functionArg();
                final int slot = DateTimeUnitSlot.positionIn(call.functionName().getText().toUpperCase(Locale.ROOT));
                for (int i = 0; i < arguments.size(); i++) {
                    final FrostlakeParser.QualifiedNameContext found =
                        i == slot ? null : firstUnknown(arguments.get(i), parameters);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            if (name != null && name.namePart().isEmpty() && !declares(parameters, name.getText())) {
                return name;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.QualifiedNameContext found = firstUnknown(node.getChild(i), parameters);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The bare word a call writes where its function reads a date or time unit, or null when it writes none there. */
    private static FrostlakeParser.QualifiedNameContext unitWordOf(final FrostlakeParser.FunctionCallExprContext call) {
        final int slot = DateTimeUnitSlot.positionIn(call.functionName().getText().toUpperCase(Locale.ROOT));
        if (slot < 0 || call.functionArgList() == null || slot >= call.functionArgList().functionArg().size()) {
            return null;
        }
        ParseTree written = call.functionArgList().functionArg(slot);
        while (written.getChildCount() == 1 && !(written instanceof FrostlakeParser.QualifiedNameExprContext)) {
            written = written.getChild(0);
        }
        if (!(written instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final FrostlakeParser.QualifiedNameContext name =
            ((FrostlakeParser.QualifiedNameExprContext) written).qualifiedName();
        return name != null && name.namePart().isEmpty() ? name : null;
    }

    /**
     * Refuse a unit word that is a parameter's name — a unit is a word or a string literal, never a value — and one
     * its function does not read, each in the sentence a query answers with.
     */
    private static void requireUnit(final String function, final FrostlakeParser.QualifiedNameContext unit,
                                    final Set<String> parameters) {
        if (declares(parameters, unit.getText())) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " "
                + DateTimeUnitSlot.notAUnitRefusal("CORRELATION(NULL)", function));
        }
        final String word = unit.getText().toUpperCase(Locale.ROOT);
        if (word.startsWith("\"")) {
            return;
        }
        if ("DATE_PART".equals(function)) {
            if (!SharedFunctionHelpers.isDatePartUnit(word)) {
                throw SharedFunctionHelpers.invalidDatePart(word, function);
            }
        } else if (DateUnitVocabulary.intervalUnit(word) == null) {
            throw SharedFunctionHelpers.notADateTimeComponent(word, function);
        }
    }
}
