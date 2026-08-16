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
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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
 * <p>The same walk refuses an ASSIGNMENT to a FOR loop's counter, which is read-only for as long as
 * it is in scope — nested blocks, branches and inner loops included — with "Assignment to variable 'I'
 * is not permitted." at the {@code :=}. An integer range makes one; a cursor loop's record does not,
 * and a LET or DECLARE of the name hides it. For a procedure's own block it also hands each direct
 * statement to a {@link DeclaredReturnJudge}, because live reports whichever of these faults comes
 * FIRST in the body, so they cannot be separate passes.
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
        validate(block, outerNames, null);
    }

    /**
     * {@link #validate(FrostlakeParser.BeginEndBlockContext, Set)} for a block whose direct RETURNs
     * {@code returns} judges in the same source-order pass.
     *
     * @param returns the judge of a procedure's declared RETURNS type, or null when the block has none
     */
    static void validate(final FrostlakeParser.BeginEndBlockContext block, final Set<String> outerNames,
                         final DeclaredReturnJudge returns) {
        final Set<String> scope = new HashSet<String>(SCRIPT_SUPPLIED);
        for (final String name : outerNames) {
            scope.add(canonical(name));
        }
        walkBlock(block, scope, new HashSet<String>(), new HashSet<String>(), returns);
    }

    /**
     * A block: its declarations in order, then its statements, then its exception handlers.
     * {@code counters} holds the FOR counters in scope, which no statement may assign.
     */
    private static void walkBlock(final FrostlakeParser.BeginEndBlockContext block,
                                  final Set<String> scope, final Set<String> counters,
                                  final Set<String> records, final DeclaredReturnJudge returns) {
        if (block.declareSection() != null) {
            for (final ParserRuleContext item : declarationsInSourceOrder(block.declareSection())) {
                if (item instanceof FrostlakeParser.UntypedDeclarationItemContext) {
                    final FrostlakeParser.UntypedDeclarationItemContext inferred =
                        (FrostlakeParser.UntypedDeclarationItemContext) item;
                    rejectUntypableInitialiser(inferred.expression(), inferred.identifier(),
                        inferred.identifier().getStart(), scope, records);
                }
                // The initializer is checked BEFORE the name is added: a declaration cannot see itself.
                checkExpressions(item, scope);
                if (item instanceof FrostlakeParser.DeclarationItemContext) {
                    final FrostlakeParser.DeclarationItemContext typed = (FrostlakeParser.DeclarationItemContext) item;
                    declare(typed.identifier(), scope, counters);
                    if (returns != null) {
                        returns.declared(typed);
                    }
                } else {
                    final FrostlakeParser.UntypedDeclarationItemContext untyped =
                        (FrostlakeParser.UntypedDeclarationItemContext) item;
                    declare(untyped.identifier(), scope, counters);
                    if (returns != null) {
                        returns.declared(untyped);
                    }
                }
            }
        }
        walkStatementList(block.statementList(), scope, counters, records, returns);
        if (block.exceptionSection() != null) {
            for (final FrostlakeParser.ExceptionHandlerContext handler
                    : block.exceptionSection().exceptionHandler()) {
                // A handler's RETURN keeps its own type, so the declared one never judges it.
                walkStatementList(handler.statementList(), nested(scope), nested(counters), nested(records), null);
            }
        }
    }

    /** A statement list; {@code returns}, when present, judges its DIRECT statements as they pass. */
    private static void walkStatementList(final FrostlakeParser.StatementListContext list,
                                          final Set<String> scope, final Set<String> counters,
                                          final Set<String> records, final DeclaredReturnJudge returns) {
        if (list == null) {
            return;
        }
        for (final FrostlakeParser.StatementContext statement : list.statement()) {
            walkStatement(statement, scope, counters, records);
            if (returns != null) {
                returns.after(statement);
            }
        }
    }

    /**
     * One statement. Declarations mutate {@code scope} so the next statement sees them; every nested
     * body gets a COPY, so what it declares disappears with it.
     */
    private static void walkStatement(final ParseTree node, final Set<String> scope,
                                      final Set<String> counters, final Set<String> records) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.BeginEndBlockContext) {
            walkBlock((FrostlakeParser.BeginEndBlockContext) node, nested(scope), nested(counters),
                nested(records), null);
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.dataTypeName() == null && let.CURSOR() == null && let.RESULTSET() == null) {
                rejectUntypableInitialiser(let.expression(), let.identifier(), let.getStart(), scope, records);
            }
            checkExpressions(let, scope);
            declare(let.identifier(), scope, counters);
            return;
        }
        if (node instanceof FrostlakeParser.AssignmentStatementContext) {
            // The TARGET must already exist and must not be a FOR counter: live refuses
            // `missing_name := 1` and a counter's `i := 5` alike, each anchored on the ':='.
            final FrostlakeParser.AssignmentStatementContext set =
                (FrostlakeParser.AssignmentStatementContext) node;
            final Token assign = set.COLON_EQ().getSymbol();
            requireName(text(set.identifier()), assign, scope);
            refuseCounterAssignment(set.identifier(), assign, counters);
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
            final Set<String> bodyCounters = nested(counters);
            final Set<String> bodyRecords = nested(records);
            if (loop.identifier() != null) {
                final String counter = canonical(text(loop.identifier()));
                body.add(counter);
                // An integer range's counter is read-only inside its loop; a cursor loop's record is
                // not, and hides any counter of the same name.
                if (loop.TO() != null) {
                    bodyCounters.add(counter);
                    bodyRecords.remove(counter);
                } else {
                    bodyCounters.remove(counter);
                    bodyRecords.add(counter);
                }
            }
            walkStatementList(loop.statementList(), body, bodyCounters, bodyRecords, null);
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
            walkStatementList((FrostlakeParser.StatementListContext) node, nested(scope), nested(counters),
                nested(records), null);
            return;
        }
        if (node instanceof FrostlakeParser.ExpressionContext
                || node instanceof FrostlakeParser.BooleanExprContext) {
            checkExpressions(node, scope);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walkStatement(node.getChild(i), scope, counters, records);
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
        final String detail = invalidIdentifier(written, asWritten);
        throw new RuntimeException(at == null
            ? SqlCompilationError.of(detail)
            : SqlCompilationError.at(at.getStart().getLine(), at.getStart().getCharPositionInLine(),
                detail));
    }

    /** {@link #requireName} anchored on a token of the statement's own: an assignment's ':='. */
    private static void requireName(final String written, final Token at, final Set<String> scope) {
        if (written.isEmpty() || scope.contains(canonical(written))) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            invalidIdentifier(written, false)));
    }

    private static String invalidIdentifier(final String written, final boolean asWritten) {
        final String shown = asWritten || isQuoted(written)
            ? written
            : written.toUpperCase(Locale.ROOT);
        return "invalid identifier '" + shown + "'";
    }

    /**
     * Refuse an assignment to a FOR counter, at the ':=', naming the counter as it is known:
     * {@code 'I'}, or {@code 'i'} for a quoted {@code "i"}.
     */
    private static void refuseCounterAssignment(final FrostlakeParser.IdentifierContext target,
                                                final Token at, final Set<String> counters) {
        final String name = canonical(text(target));
        if (counters.contains(name)) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                " Assignment to variable '" + name + "' is not permitted."));
        }
    }

    /** Declare a name in {@code scope}; a declaration of a counter's name hides the counter. */
    private static void declare(final FrostlakeParser.IdentifierContext identifier,
                                final Set<String> scope, final Set<String> counters) {
        if (identifier != null) {
            scope.add(canonical(text(identifier)));
            counters.remove(canonical(text(identifier)));
        }
    }

    /**
     * A DECLARE section's items in SOURCE order. The typed and untyped shapes are separate grammar
     * alternatives, so their two lists are merged back: a later declaration's initializer sees every
     * name declared above it, whichever shape each one parsed as.
     */
    private static List<ParserRuleContext> declarationsInSourceOrder(
            final FrostlakeParser.DeclareSectionContext section) {
        final List<ParserRuleContext> items = new ArrayList<ParserRuleContext>();
        items.addAll(section.declarationItem());
        items.addAll(section.untypedDeclarationItem());
        Collections.sort(items, new Comparator<ParserRuleContext>() {
            @Override
            public int compare(final ParserRuleContext a, final ParserRuleContext b) {
                return Integer.compare(a.getStart().getStartIndex(), b.getStart().getStartIndex());
            }
        });
        return items;
    }

    /**
     * Refuse a name introduced twice in one scope: live's "Variable with name 'X' declared twice.",
     * anchored at the second introduction (live-verified). A scope is one block's DECLARE section and
     * its own statement list. Every nested block, branch, loop body and handler opens a fresh one, where
     * an outer name may be introduced again to hide it. A DECLARE item, a LET of any form and, in a
     * procedure's own block, a parameter all introduce a name. An EXCEPTION lives in a namespace of its
     * own, so it collides only with another EXCEPTION.
     *
     * @param parameters       the names already in the outermost scope: a procedure's parameters
     * @param declarationsOnly skip the LETs. CREATE PROCEDURE compiles the DECLARE sections and leaves
     *                         the statements to CALL, so a repeated LET is refused only when called
     */
    static void rejectRedeclaration(final FrostlakeParser.BeginEndBlockContext block,
                                    final Set<String> parameters, final boolean declarationsOnly) {
        redeclarationsInBlock(block, new HashSet<String>(parameters), declarationsOnly);
    }

    private static void redeclarationsInBlock(final FrostlakeParser.BeginEndBlockContext block,
                                              final Set<String> names, final boolean declarationsOnly) {
        if (block.declareSection() != null) {
            final Set<String> exceptions = new HashSet<String>();
            for (final ParserRuleContext item : declarationsInSourceOrder(block.declareSection())) {
                if (item instanceof FrostlakeParser.DeclarationItemContext) {
                    final FrostlakeParser.DeclarationItemContext typed = (FrostlakeParser.DeclarationItemContext) item;
                    introduce(typed.EXCEPTION() != null ? exceptions : names, typed.identifier(), item.getStart());
                } else {
                    introduce(names, ((FrostlakeParser.UntypedDeclarationItemContext) item).identifier(),
                        item.getStart());
                }
            }
        }
        redeclarationsInList(block.statementList(), names, declarationsOnly);
        if (block.exceptionSection() != null) {
            for (final FrostlakeParser.ExceptionHandlerContext handler
                    : block.exceptionSection().exceptionHandler()) {
                redeclarationsInList(handler.statementList(), new HashSet<String>(), declarationsOnly);
            }
        }
    }

    private static void redeclarationsInList(final FrostlakeParser.StatementListContext list,
                                             final Set<String> names, final boolean declarationsOnly) {
        if (list == null) {
            return;
        }
        for (final FrostlakeParser.StatementContext statement : list.statement()) {
            redeclarationsIn(statement, names, declarationsOnly);
        }
    }

    /** One node of the current scope: a LET introduces a name in it, and anything nested opens a new one. */
    private static void redeclarationsIn(final ParseTree node, final Set<String> names,
                                         final boolean declarationsOnly) {
        if (node == null || isSqlSubtree(node)) {
            return;
        }
        if (node instanceof FrostlakeParser.BeginEndBlockContext) {
            redeclarationsInBlock((FrostlakeParser.BeginEndBlockContext) node, new HashSet<String>(),
                declarationsOnly);
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            if (!declarationsOnly) {
                final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
                introduce(names, let.identifier(), let.getStart());
            }
            return;
        }
        if (node instanceof FrostlakeParser.StatementListContext) {
            redeclarationsInList((FrostlakeParser.StatementListContext) node, new HashSet<String>(),
                declarationsOnly);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            redeclarationsIn(node.getChild(i), names, declarationsOnly);
        }
    }

    /**
     * An untyped declaration whose initialiser gives it no type is refused while the block compiles —
     * " variable 'A' cannot have its type inferred from initializer", at the LET or at a DECLARE item's
     * name, ahead of anything later in the body. A bare name that resolves to nothing, a bare NULL and a
     * cursor record's field are such initialisers; an unknown name inside a larger expression is the
     * ordinary invalid identifier instead (live-verified).
     */
    private static void rejectUntypableInitialiser(final FrostlakeParser.ExpressionContext initialiser,
                                                   final FrostlakeParser.IdentifierContext name,
                                                   final Token at, final Set<String> scope,
                                                   final Set<String> records) {
        if (initialiser == null || name == null || !untypable(initialiser, scope, records)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            " variable '" + canonical(text(name)) + "' cannot have its type inferred from initializer"));
    }

    private static boolean untypable(final FrostlakeParser.ExpressionContext initialiser,
                                     final Set<String> scope, final Set<String> records) {
        FrostlakeParser.ExpressionContext value = initialiser;
        while (value instanceof FrostlakeParser.ParenExprContext
                && ((FrostlakeParser.ParenExprContext) value).booleanExpr() instanceof FrostlakeParser.ValueExprContext) {
            value = ((FrostlakeParser.ValueExprContext) ((FrostlakeParser.ParenExprContext) value).booleanExpr())
                .expression();
        }
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            return ((FrostlakeParser.LiteralExprContext) value).literal().NULL() != null;
        }
        if (value instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            // A scalar subquery gives the declaration a type only when it reads NO relation: live takes
            // (SELECT 1) and (SELECT 1 + 1), and refuses every subquery with a FROM — even one whose
            // columns all resolve, and even MAX over them (live-verified, cell by cell).
            return readsARelation(((FrostlakeParser.ScalarSubqueryExprContext) value).selectStatement());
        }
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext qualified =
                ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName();
            if (qualified.nameStartPart() == null) {
                return false;
            }
            final String root = canonical(text(qualified.nameStartPart()));
            return qualified.namePart().isEmpty() ? !scope.contains(root) : records.contains(root);
        }
        return false;
    }

    /** Whether a query reads a relation anywhere inside it — the FROM the type inference cannot see past. */
    private static boolean readsARelation(final ParseTree node) {
        if (node instanceof FrostlakeParser.TableExpressionContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsARelation(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * An unnamed bind inside a block is refused while the block COMPILES, not when the statement that
     * carries it runs, and the account has two sentences for it — both with a leading space on their
     * own line, and both positioned on the {@code ?} itself:
     *
     * <ul>
     *   <li>{@code Unexpected unnamed bind in SQL stored procedure.} where the {@code ?} is a scripting
     *       expression's own — a LET's initialiser, a RETURN's value;</li>
     *   <li>{@code Unexpected unnamed bind in {2}.}, the literal braces included, where it sits inside an
     *       embedded SQL statement — a SELECT, or a RESULTSET's query.</li>
     * </ul>
     *
     * <p>A CURSOR declaration is the exception — written DECLARE or LET alike: the account CREATES the
     * block and answers, because a cursor's query is bound when it is OPENed. A stored PROCEDURE's body is not held to this rule
     * either — only a block being compiled to run (live-verified, cell by cell).
     *
     * @param node the block, or any part of it
     */
    static void rejectUnnamedBinds(final ParseTree node) {
        rejectUnnamedBinds(node, false);
    }

    /** The walk, carrying whether the current subtree sits inside an embedded SQL statement. */
    private static void rejectUnnamedBinds(final ParseTree node, final boolean insideSqlStatement) {
        if (node instanceof FrostlakeParser.CursorDeclarationContext) {
            return;
        }
        if (node instanceof FrostlakeParser.DeclarationItemContext
                && ((FrostlakeParser.DeclarationItemContext) node).CURSOR() != null) {
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext
                && ((FrostlakeParser.LetStatementContext) node).CURSOR() != null) {
            // LET c CURSOR FOR … is a cursor declaration like the DECLARE one: its query's binds are
            // supplied by OPEN … USING, so the account takes it.
            return;
        }
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            if (token.getType() == FrostlakeLexer.QUESTION) {
                throw new RuntimeException(SqlCompilationError.at(token.getLine(),
                    token.getCharPositionInLine(), " Unexpected unnamed bind in "
                    + (insideSqlStatement ? "{2}" : "SQL stored procedure") + "."));
            }
            return;
        }
        final boolean nowInsideSql = insideSqlStatement
            || node instanceof FrostlakeParser.SelectStatementContext
            || node instanceof FrostlakeParser.DmlStatementContext;
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectUnnamedBinds(node.getChild(i), nowInsideSql);
        }
    }

    /**
     * A bind variable cannot name a field: {@code :r.a} is a syntax error at the '.' wherever it is
     * written — an embedded statement or a scripting expression, a cursor record's field or any other
     * name, spaced or not — and the whole block is refused before any of it runs, a stored procedure at
     * CREATE (live-verified). The coordinates are the body's own. Live goes on to list its parser's
     * recovery lines after this one; the first line is the one reproduced.
     *
     * @param node the block, or any part of it
     */
    static void rejectDottedBindVariables(final ParseTree node) {
        if (node instanceof FrostlakeParser.FieldAccessExprContext) {
            final FrostlakeParser.FieldAccessExprContext access = (FrostlakeParser.FieldAccessExprContext) node;
            if (access.expression() instanceof FrostlakeParser.BindVarExprContext && access.DOT() != null) {
                final Token dot = access.DOT().getSymbol();
                throw new RuntimeException(SqlCompilationError.of("syntax error line " + dot.getLine()
                    + " at position " + dot.getCharPositionInLine() + " unexpected '.'."));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectDottedBindVariables(node.getChild(i));
        }
    }

    private static void introduce(final Set<String> names, final FrostlakeParser.IdentifierContext identifier,
                                  final Token at) {
        if (identifier == null) {
            return;
        }
        final String name = canonical(text(identifier));
        if (!names.add(name)) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                " Variable with name '" + name + "' declared twice."));
        }
    }

    private static Set<String> nested(final Set<String> scope) {
        return new HashSet<String>(scope);
    }

    /**
     * The comparison key. A quoted name keeps its case and an unquoted one folds, the same rule the
     * rest of the engine applies to identifiers.
     */
    static String canonical(final String written) {
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
