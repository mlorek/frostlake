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
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses a bare identifier in a Snowflake Scripting block that names nothing in scope.
 *
 * <p>Frostlake used to resolve such a name to NULL, so a typo'd variable silently produced a NULL
 * result instead of failing — the accepted-but-invalid class of divergence this engine exists to
 * catch. Live raises {@code SQL compilation error: … invalid identifier 'MISSING_NAME'}.
 *
 * <p>The single fact that shapes this class: live compiles the WHOLE block before running any of it,
 * so an unreachable reference fails too — {@code IF (1 = 2) THEN RETURN missing_name; END IF; RETURN
 * 99} is refused, and so is the body of a WHILE whose condition is false at entry. A check made when
 * the expression is evaluated could never reproduce that, which is why this is a pass over the parse
 * tree rather than a test inside the evaluator.
 *
 * <p>What counts as declared, measured on a real account: DECLARE and LET variables, procedure
 * parameters, a FOR loop's counter or record INSIDE the loop, cursor / RESULTSET / exception names.
 * Scope is ordered and nested exactly as one would hope and is worth stating because each was a
 * separate measurement — a name is invisible BEFORE its own declaration, a loop variable is gone after
 * its {@code END FOR}, and an inner block's variables are gone after its {@code END}.
 *
 * <p>Three things are deliberately NOT checked here, because live leaves each of them to run time:
 * <ul>
 *   <li>a FIELD of a record — {@code r.nope} raises {@code Given column name/index does not exist}
 *       when the row is read, so only the ROOT of a dotted name is resolved here (live names just the
 *       root too: {@code invalid identifier 'R'});</li>
 *   <li>anything inside an embedded SQL statement — {@code (SELECT nope FROM t)} is an
 *       EXPRESSION_ERROR raised by the query layer, so SQL subtrees are skipped whole;</li>
 *   <li>an unknown function — {@code missing_fn()} reports {@code Unknown function}, a different
 *       error from a different layer — and a function's ARGUMENTS are left alone with it, because the
 *       grammar admits bare keywords there and the function reads them itself:
 *       {@code DATEADD(MINUTE, 30, ts)} passes a date part, not a variable, and refusing it would
 *       break a script Snowflake runs.</li>
 * </ul>
 *
 * <p>Like the rest of the routine-body checking this fails OPEN: a construct this class cannot read
 * confidently is left alone, because a false refusal here breaks a script that Snowflake runs, which
 * is worse than the leniency being removed.
 */
final class ScriptingNameValidator {

    /**
     * Names a script may use without declaring them. Frostlake supplies all four; live resolves
     * SQLCODE and SQLROWCOUNT anywhere (both answering NULL outside a handler) but refuses a bare
     * SQLERRM, a divergence left alone here rather than widened into a new refusal.
     */
    private static final Set<String> SCRIPT_SUPPLIED = new HashSet<String>();

    static {
        SCRIPT_SUPPLIED.add("SQLCODE");
        SCRIPT_SUPPLIED.add("SQLERRM");
        SCRIPT_SUPPLIED.add("SQLSTATE");
        SCRIPT_SUPPLIED.add("SQLROWCOUNT");
        SCRIPT_SUPPLIED.add("SQLFOUND");
        SCRIPT_SUPPLIED.add("SQLNOTFOUND");
        SCRIPT_SUPPLIED.add("ACTIVITY_COUNT");
    }

    private ScriptingNameValidator() {
    }

    /**
     * Check {@code block}, where {@code outerNames} are already in scope — a stored procedure's
     * parameters, or the variables of an enclosing block.
     */
    static void validate(final FrostlakeParser.BeginEndBlockContext block, final Set<String> outerNames) {
        final Set<String> scope = new HashSet<String>(SCRIPT_SUPPLIED);
        for (final String name : outerNames) {
            scope.add(canonical(name));
        }
        walkBlock(block, scope);
    }

    /** A block: its declarations in order, then its statements, then its exception handlers. */
    private static void walkBlock(final FrostlakeParser.BeginEndBlockContext block,
                                  final Set<String> scope) {
        if (block.declareSection() != null) {
            // The typed and untyped declaration shapes are separate grammar alternatives, so the
            // two lists must be merged back into SOURCE order — a later declaration's initializer
            // sees every name declared above it, whichever shape each one parsed as.
            final List<ParserRuleContext> items = new ArrayList<ParserRuleContext>();
            items.addAll(block.declareSection().declarationItem());
            items.addAll(block.declareSection().untypedDeclarationItem());
            Collections.sort(items, new Comparator<ParserRuleContext>() {
                @Override
                public int compare(final ParserRuleContext a, final ParserRuleContext b) {
                    return Integer.compare(a.getStart().getStartIndex(), b.getStart().getStartIndex());
                }
            });
            for (final ParserRuleContext item : items) {
                // The initializer is checked BEFORE the name is added: a declaration cannot see itself.
                checkExpressions(item, scope);
                if (item instanceof FrostlakeParser.DeclarationItemContext) {
                    declare(((FrostlakeParser.DeclarationItemContext) item).identifier(), scope);
                } else {
                    declare(((FrostlakeParser.UntypedDeclarationItemContext) item).identifier(), scope);
                }
            }
        }
        walkStatementList(block.statementList(), scope);
        if (block.exceptionSection() != null) {
            for (final FrostlakeParser.ExceptionHandlerContext handler
                    : block.exceptionSection().exceptionHandler()) {
                walkStatementList(handler.statementList(), nested(scope));
            }
        }
    }

    private static void walkStatementList(final FrostlakeParser.StatementListContext list,
                                          final Set<String> scope) {
        if (list == null) {
            return;
        }
        for (final FrostlakeParser.StatementContext statement : list.statement()) {
            walkStatement(statement, scope);
        }
    }

    /**
     * One statement. Declarations mutate {@code scope} so the next statement sees them; every nested
     * body gets a COPY, so what it declares disappears with it.
     */
    private static void walkStatement(final ParseTree node, final Set<String> scope) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.BeginEndBlockContext) {
            walkBlock((FrostlakeParser.BeginEndBlockContext) node, nested(scope));
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            checkExpressions(let, scope);
            declare(let.identifier(), scope);
            return;
        }
        if (node instanceof FrostlakeParser.AssignmentStatementContext) {
            // The TARGET must already exist: live refuses `missing_name := 1`.
            final FrostlakeParser.AssignmentStatementContext set =
                (FrostlakeParser.AssignmentStatementContext) node;
            require(set.identifier(), scope);
            checkExpressions(set, scope);
            return;
        }
        if (node instanceof FrostlakeParser.ForStatementContext) {
            final FrostlakeParser.ForStatementContext loop = (FrostlakeParser.ForStatementContext) node;
            // The bounds are evaluated OUTSIDE the loop, so they cannot see the loop variable.
            for (final FrostlakeParser.ExpressionContext bound : loop.expression()) {
                checkExpressions(bound, scope);
            }
            final Set<String> body = nested(scope);
            if (loop.identifier() != null) {
                body.add(canonical(text(loop.identifier())));
            }
            walkStatementList(loop.statementList(), body);
            return;
        }
        if (node instanceof FrostlakeParser.OpenStatementContext
                || node instanceof FrostlakeParser.CloseStatementContext
                || node instanceof FrostlakeParser.FetchStatementContext
                || node instanceof FrostlakeParser.RaiseStatementContext) {
            requireNamedTargets(node, scope);
            checkExpressions(node, scope);
            return;
        }
        if (isSqlSubtree(node)) {
            // Embedded SQL resolves against tables at run time — not this pass's business.
            return;
        }
        if (node instanceof FrostlakeParser.StatementListContext) {
            walkStatementList((FrostlakeParser.StatementListContext) node, nested(scope));
            return;
        }
        if (node instanceof FrostlakeParser.ExpressionContext
                || node instanceof FrostlakeParser.BooleanExprContext) {
            checkExpressions(node, scope);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walkStatement(node.getChild(i), scope);
        }
    }

    /**
     * The identifiers a cursor / exception statement names directly — {@code OPEN c}, {@code CLOSE c},
     * {@code FETCH c INTO v}, {@code RAISE e} — each of which live requires to resolve.
     */
    private static void requireNamedTargets(final ParseTree node, final Set<String> scope) {
        if (node instanceof FrostlakeParser.OpenStatementContext) {
            require(((FrostlakeParser.OpenStatementContext) node).identifier(), scope);
        } else if (node instanceof FrostlakeParser.CloseStatementContext) {
            require(((FrostlakeParser.CloseStatementContext) node).identifier(), scope);
        } else if (node instanceof FrostlakeParser.FetchStatementContext) {
            final FrostlakeParser.FetchStatementContext fetch = (FrostlakeParser.FetchStatementContext) node;
            require(fetch.identifier(), scope);
            if (fetch.identifierList() != null) {
                for (final FrostlakeParser.IdentifierContext target : fetch.identifierList().identifier()) {
                    require(target, scope);
                }
            }
        } else if (node instanceof FrostlakeParser.RaiseStatementContext) {
            final FrostlakeParser.RaiseStatementContext raise = (FrostlakeParser.RaiseStatementContext) node;
            if (raise.identifier() != null) {
                require(raise.identifier(), scope);
            }
        }
    }

    /**
     * Every bare identifier reference under {@code node}, skipping the subtrees whose names belong to
     * another layer: embedded SQL, function names, and {@code :bind} references.
     */
    private static void checkExpressions(final ParseTree node, final Set<String> scope) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // A subquery inside a SCRIPTING expression still binds its :names from the block, and live
            // resolves them with the scripting compiler rather than the DML binder — the giveaway is
            // the case: `BEGIN RETURN (SELECT :NoPe); END` reports 'NoPe' as written, exactly like a
            // bare scripting :bind, where the DML paths report 'NOPE'. So the COLUMN names in here are
            // still the query layer's business at run time, but the binds are ours.
            checkBindsOnly(node, scope);
            return;
        }
        if (isSqlSubtree(node)) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            requireRootOf(name, scope);
            return;
        }
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            // A :name in a SCRIPTING expression names a block variable, and live refuses an unknown
            // one exactly like a bare identifier — at the COLON, which is this context's own start.
            // A numeric :1 is a client bind, not a scripting name, and is left alone. Bind variables
            // inside EMBEDDED SQL never reach here: those subtrees are skipped whole and answered at
            // run time, where the two-wording rule applies.
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.identifier() != null) {
                requireName(text(bind.identifier()), bind, scope, true);
            }
            return;
        }
        if (node instanceof FrostlakeParser.FunctionNameContext
                || node instanceof FrostlakeParser.FunctionArgListContext) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkExpressions(node.getChild(i), scope);
        }
    }

    /**
     * Walk a SQL subtree for {@code :bind} references ALONE, leaving every other name to the query
     * layer. Used for a subquery that sits inside a scripting expression, where the binds belong to
     * the block but the columns belong to the tables.
     */
    private static void checkBindsOnly(final ParseTree node, final Set<String> scope) {
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.identifier() != null) {
                requireName(text(bind.identifier()), bind, scope, true);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkBindsOnly(node.getChild(i), scope);
        }
    }

    /**
     * Subtrees this pass must not enter. A SELECT, a DML statement, an EXECUTE IMMEDIATE and a
     * subquery all resolve their names against the catalog when they run, and live reports those as
     * run-time errors of a different type.
     */
    private static boolean isSqlSubtree(final ParseTree node) {
        return node instanceof FrostlakeParser.SelectStatementContext
            || node instanceof FrostlakeParser.DmlStatementContext
            || node instanceof FrostlakeParser.SelectIntoStatementContext
            || node instanceof FrostlakeParser.ExecuteImmediateStatementContext
            || node instanceof FrostlakeParser.DdlStatementContext;
    }

    /** Resolve only the ROOT of a dotted name; the field is a run-time concern. */
    private static void requireRootOf(final FrostlakeParser.QualifiedNameContext name,
                                      final Set<String> scope) {
        if (name == null || name.nameStartPart() == null) {
            return;
        }
        requireName(text(name.nameStartPart()), name.nameStartPart(), scope);
    }

    private static void require(final FrostlakeParser.IdentifierContext identifier,
                                final Set<String> scope) {
        if (identifier != null) {
            requireName(text(identifier), identifier, scope);
        }
    }

    /**
     * Refuse {@code written} unless it is in scope, reporting the position of {@code at}.
     *
     * <p>No {@code ExpressionSource} origin is needed here, unlike a refusal raised from extracted
     * text: this pass walks the PARSE TREE of the statement as submitted, so every token already
     * carries its absolute line and column.
     */
    private static void requireName(final String written, final ParserRuleContext at,
                                    final Set<String> scope) {
        requireName(written, at, scope, false);
    }

    /**
     * @param asWritten echo the name verbatim instead of folding it. Live upper-cases a bare
     *                  identifier ({@code 'MISSING_NAME'}) but echoes a {@code :bind} exactly as it
     *                  was typed ({@code :NoPe} reports {@code 'NoPe'}) — measured on both.
     */
    private static void requireName(final String written, final ParserRuleContext at,
                                    final Set<String> scope, final boolean asWritten) {
        if (written.isEmpty() || scope.contains(canonical(written))) {
            return;
        }
        final String shown = asWritten || isQuoted(written)
            ? written
            : written.toUpperCase(Locale.ROOT);
        final String detail = "invalid identifier '" + shown + "'";
        throw new RuntimeException(at == null
            ? SqlCompilationError.of(detail)
            : SqlCompilationError.at(at.getStart().getLine(), at.getStart().getCharPositionInLine(),
                detail));
    }

    private static void declare(final FrostlakeParser.IdentifierContext identifier,
                                final Set<String> scope) {
        if (identifier != null) {
            scope.add(canonical(text(identifier)));
        }
    }

    private static Set<String> nested(final Set<String> scope) {
        return new HashSet<String>(scope);
    }

    /**
     * The comparison key. A quoted name keeps its case and an unquoted one folds, the same rule the
     * rest of the engine applies to identifiers.
     */
    private static String canonical(final String written) {
        if (written == null) {
            return "";
        }
        if (isQuoted(written)) {
            return written.substring(1, written.length() - 1);
        }
        return written.toUpperCase(Locale.ROOT);
    }

    private static boolean isQuoted(final String written) {
        return written.length() > 1 && written.charAt(0) == '"'
            && written.charAt(written.length() - 1) == '"';
    }

    private static String text(final ParserRuleContext ctx) {
        return ctx == null ? "" : ctx.getText();
    }
}
