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

import dev.frostlake.executor.AssignedStatement;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QuotedBindName;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
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
 *   <li>the tables and columns of an embedded SQL statement — {@code (SELECT nope FROM t)} is an
 *       EXPRESSION_ERROR raised by the query layer, so an SQL statement's own names are left alone. Its
 *       {@code :binds} name the block's variables, though, and compile with it: one nothing declares is
 *       refused here, its name folded as an unquoted identifier is ({@code invalid identifier 'NOPE'});</li>
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
 * FIRST in the body, so they cannot be separate passes. The bare names of the block's expressions are
 * the exception: live resolves them after everything else, so their refusals are held back and raised
 * at the end of the walk in live's order (see {@link DeferredNameRefusals}).
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

    /**
     * The script-supplied names an EXECUTE IMMEDIATE's USING may name only inside an exception handler, where the
     * handler sets them (live-verified: USING (sqlstate) elsewhere is "invalid identifier 'SQLSTATE'", while USING
     * (sqlcode) binds NULL).
     */
    private static final Set<String> HANDLER_SUPPLIED = new HashSet<String>();

    static {
        HANDLER_SUPPLIED.add("SQLSTATE");
        HANDLER_SUPPLIED.add("SQLERRM");
    }

    private ScriptingNameValidator() {
    }

    /** Whether a script may use {@code name}, a canonical name, without declaring it (see {@code SCRIPT_SUPPLIED}). */
    static boolean isScriptSupplied(final String name) {
        return SCRIPT_SUPPLIED.contains(name);
    }

    /**
     * Check {@code block}, where {@code outerNames} are already in scope — a stored procedure's
     * parameters, or the variables of an enclosing block.
     */
    static void validate(final FrostlakeParser.BeginEndBlockContext block, final Set<String> outerNames) {
        validate(block, outerNames, null, null, null);
    }

    /**
     * {@link #validate(FrostlakeParser.BeginEndBlockContext, Set)} for a block whose direct RETURNs
     * {@code returns} judges in the same source-order pass.
     *
     * @param returns    the judge of a procedure's declared RETURNS type, or null when the block has none
     * @param returnKind for a procedure's own block, what EVERY RETURN in it must return — TRUE a table,
     *                   FALSE a scalar — judged in the same pass; null for any other block
     * @param compiler   compiles the FROM-less subqueries an untyped declaration infers its type from, or null
     */
    static void validate(final FrostlakeParser.BeginEndBlockContext block, final Set<String> outerNames,
                         final DeclaredReturnJudge returns, final Boolean returnKind,
                         final InferredInitialiserCompiler compiler) {
        final Set<String> scope = new HashSet<String>(SCRIPT_SUPPLIED);
        for (final String name : outerNames) {
            scope.add(canonical(name));
        }
        // A bare name of an expression is resolved after everything else the walk judges (see
        // DeferredNameRefusals), so the walk holds its refusal back and the first is raised at the end.
        final DeferredNameRefusals enclosing = DeferredNameRefusals.begin();
        try {
            walkBlock(block, scope, new HashSet<String>(), new HashSet<String>(), returns, returnKind, compiler);
            DeferredNameRefusals.current().raiseFirst(block);
        } finally {
            DeferredNameRefusals.end(enclosing);
        }
    }

    /**
     * A block: its declarations in order, then its statements, then its exception handlers.
     * {@code counters} holds the FOR counters in scope, which no statement may assign.
     */
    private static void walkBlock(final FrostlakeParser.BeginEndBlockContext block,
                                  final Set<String> scope, final Set<String> counters,
                                  final Set<String> records, final DeclaredReturnJudge returns,
                                  final Boolean returnKind, final InferredInitialiserCompiler compiler) {
        if (block.declareSection() != null) {
            for (final ParserRuleContext item : declarationsInSourceOrder(block.declareSection())) {
                if (item instanceof FrostlakeParser.UntypedDeclarationItemContext) {
                    final FrostlakeParser.UntypedDeclarationItemContext inferred =
                        (FrostlakeParser.UntypedDeclarationItemContext) item;
                    rejectUntypableInitialiser(inferred.booleanExpr(), inferred.identifier(),
                        inferred.identifier().getStart(), scope, records, compiler);
                }
                // The initializer is checked BEFORE the name is added: a declaration cannot see itself.
                if (item instanceof FrostlakeParser.DeclarationItemContext
                        && ((FrostlakeParser.DeclarationItemContext) item).resultSetSource() != null) {
                    checkResultSetSource(((FrostlakeParser.DeclarationItemContext) item).resultSetSource(), scope);
                } else {
                    checkExpressions(item, scope);
                }
                if (item instanceof FrostlakeParser.DeclarationItemContext) {
                    final FrostlakeParser.DeclarationItemContext typed = (FrostlakeParser.DeclarationItemContext) item;
                    declare(typed.identifier(), scope, counters);
                    if (compiler != null && typed.dataTypeName() != null && typed.identifier() != null) {
                        compiler.declare(canonical(text(typed.identifier())), typed.dataTypeName(), typed.typeParameters());
                    }
                    if (returns != null) {
                        returns.declared(typed);
                    }
                } else {
                    final FrostlakeParser.UntypedDeclarationItemContext untyped =
                        (FrostlakeParser.UntypedDeclarationItemContext) item;
                    if (compiler != null && untyped.identifier() != null && untyped.booleanExpr() != null) {
                        compiler.infer(canonical(text(untyped.identifier())), untyped.booleanExpr(),
                            untyped.identifier().getStart());
                    }
                    declare(untyped.identifier(), scope, counters);
                    if (returns != null) {
                        returns.declared(untyped);
                    }
                }
            }
        }
        walkStatementList(block.statementList(), scope, counters, records, returns, returnKind, compiler);
        if (block.exceptionSection() != null) {
            for (final FrostlakeParser.ExceptionHandlerContext handler
                    : block.exceptionSection().exceptionHandler()) {
                // A handler's RETURN keeps its own type, so the declared one never judges it.
                openScope(compiler);
                walkStatementList(handler.statementList(), nested(scope), nested(counters), nested(records), null,
                    returnKind, compiler);
                closeScope(compiler);
            }
        }
    }

    /** A nested body opens: the types it declares end with it (see {@link InferredInitialiserCompiler}). */
    private static void openScope(final InferredInitialiserCompiler compiler) {
        if (compiler != null) {
            compiler.enterScope();
        }
    }

    /** The innermost nested body closes. */
    private static void closeScope(final InferredInitialiserCompiler compiler) {
        if (compiler != null) {
            compiler.exitScope();
        }
    }

    /** A statement list; {@code returns}, when present, judges its DIRECT statements as they pass. */
    private static void walkStatementList(final FrostlakeParser.StatementListContext list,
                                          final Set<String> scope, final Set<String> counters,
                                          final Set<String> records, final DeclaredReturnJudge returns,
                                          final Boolean returnKind, final InferredInitialiserCompiler compiler) {
        if (list == null) {
            return;
        }
        for (final FrostlakeParser.StatementContext statement : list.statement()) {
            walkStatement(statement, scope, counters, records, returnKind, compiler);
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
                                      final Set<String> counters, final Set<String> records,
                                      final Boolean returnKind, final InferredInitialiserCompiler compiler) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.BeginEndBlockContext) {
            openScope(compiler);
            walkBlock((FrostlakeParser.BeginEndBlockContext) node, nested(scope), nested(counters),
                nested(records), null, returnKind, compiler);
            closeScope(compiler);
            return;
        }
        if (node instanceof FrostlakeParser.ReturnStatementContext) {
            final FrostlakeParser.ReturnStatementContext ret = (FrostlakeParser.ReturnStatementContext) node;
            rejectReturnKind(ret, returnKind);
            if (ret.TABLE() != null && ret.expression() instanceof FrostlakeParser.QualifiedNameExprContext) {
                // RETURN TABLE(rs) names a RESULTSET, and live reports a missing one at the RETURN.
                final FrostlakeParser.QualifiedNameContext named =
                    ((FrostlakeParser.QualifiedNameExprContext) ret.expression()).qualifiedName();
                if (named.nameStartPart() != null) {
                    requireName(text(named.nameStartPart()), ret, scope);
                }
                return;
            }
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.dataTypeName() == null && let.CURSOR() == null && let.RESULTSET() == null) {
                rejectUntypableInitialiser(let.booleanExpr(), let.identifier(), let.getStart(), scope, records,
                    compiler);
            }
            if (let.resultSetSource() != null) {
                checkResultSetSource(let.resultSetSource(), scope);
            } else {
                checkExpressions(let, scope);
            }
            if (compiler != null && let.identifier() != null) {
                // Its type is compiled with the block: the one written, or the one its initialiser infers.
                if (let.dataTypeName() != null) {
                    compiler.declare(canonical(text(let.identifier())), let.dataTypeName(), let.typeParameters());
                } else if (let.CURSOR() == null && let.RESULTSET() == null && let.booleanExpr() != null) {
                    compiler.infer(canonical(text(let.identifier())), let.booleanExpr(), let.getStart());
                }
            }
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
            final String target = canonical(text(set.identifier()));
            final ParserRuleContext declaration = DeclarationLookup.declarationOf(set, target);
            refuseUnassignable(set.identifier(), assign, declaration);
            final boolean resultSet = DeclarationLookup.RESULTSET.equals(DeclarationLookup.misuse(declaration));
            if (set.resultSetStatement() != null) {
                // A statement fills a RESULTSET; assigned to anything else, a command is refused while the block
                // compiles and any other statement when the assignment runs (see AssignedStatement).
                if (!resultSet && AssignedStatement.refusedWhileCompiling(set.resultSetStatement())) {
                    throw AssignedStatement.invalidValue(ParseTreeText.getOriginalText(set.resultSetStatement()));
                }
                checkResultSetStatement(set.resultSetStatement(), scope);
            } else if (set.resultSetBlock() != null) {
                // A block fills a RESULTSET, and compiles when it runs.
                if (!resultSet) {
                    throw AssignedStatement.invalidValue(AssignedStatement.BLOCK);
                }
            } else if (set.executeImmediateStatement() != null && !resultSet) {
                throw AssignedStatement.invalidValue(AssignedStatement.EXECUTE_IMMEDIATE);
            } else if (set.booleanExpr() instanceof FrostlakeParser.ValueExprContext
                    && ((FrostlakeParser.ValueExprContext) set.booleanExpr()).expression()
                        instanceof FrostlakeParser.ScalarSubqueryExprContext
                    && resultSet) {
                // A RESULTSET assigned a query holds its rows: the query is SQL the block runs, whose binds fold as
                // an SQL statement's do — r := (SELECT :nosuch) names 'NOSUCH', where a scalar's x := (SELECT
                // :nosuch) names 'nosuch' (live-verified).
                checkEmbeddedBinds(set.booleanExpr(), scope);
            } else if (set.callStatement() != null) {
                checkCall(set.callStatement(), scope);
            } else {
                checkExpressions(set, scope);
            }
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
            openScope(compiler);
            if (loop.identifier() != null) {
                final String counter = canonical(text(loop.identifier()));
                body.add(counter);
                // An integer range's counter is read-only inside its loop; a cursor loop's record is
                // not, and hides any counter of the same name.
                if (loop.TO() != null) {
                    bodyCounters.add(counter);
                    bodyRecords.remove(counter);
                    if (compiler != null) {
                        compiler.declareCounter(counter);
                    }
                } else {
                    bodyCounters.remove(counter);
                    bodyRecords.add(counter);
                }
            }
            walkStatementList(loop.statementList(), body, bodyCounters, bodyRecords, null, returnKind, compiler);
            closeScope(compiler);
            return;
        }
        if (node instanceof FrostlakeParser.CaseStatementContext && compiler != null) {
            // A simple CASE's operand compiles with the block, a branch that never runs included.
            final FrostlakeParser.CaseStatementContext cased = (FrostlakeParser.CaseStatementContext) node;
            if (cased.booleanExpr().size() > cased.WHEN().size()) {
                compiler.judge(cased.booleanExpr(0));
            }
        }
        if (node instanceof FrostlakeParser.OpenStatementContext
                || node instanceof FrostlakeParser.CloseStatementContext
                || node instanceof FrostlakeParser.FetchStatementContext
                || node instanceof FrostlakeParser.RaiseStatementContext) {
            requireNamedTargets(node, scope);
            checkExpressions(node, scope);
            return;
        }
        if (node instanceof FrostlakeParser.SelectIntoStatementContext) {
            // A SELECT … INTO's own SQL compiles with the block, its INTO clause set aside, so a bind the block
            // never declared is refused here, before a repeated INTO target is — the names of its tables still
            // resolve when it runs.
            final FrostlakeParser.SelectIntoStatementContext into = (FrostlakeParser.SelectIntoStatementContext) node;
            checkIntoStatementBinds(into, into, scope);
            return;
        }
        if (node instanceof FrostlakeParser.ExecuteImmediateStatementContext) {
            checkImmediate((FrostlakeParser.ExecuteImmediateStatementContext) node, scope);
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext || isBindingDml(node)) {
            // A query or DML statement the block runs binds the block's variables, and those compile with the
            // block; its tables and columns resolve when it runs.
            checkEmbeddedBinds(node, scope);
            return;
        }
        if (isSqlSubtree(node)) {
            // Embedded SQL resolves against tables at run time — not this pass's business.
            return;
        }
        if (node instanceof FrostlakeParser.CallStatementContext) {
            checkCall((FrostlakeParser.CallStatementContext) node, scope);
            return;
        }
        if (node instanceof FrostlakeParser.StatementListContext) {
            openScope(compiler);
            walkStatementList((FrostlakeParser.StatementListContext) node, nested(scope), nested(counters),
                nested(records), null, returnKind, compiler);
            closeScope(compiler);
            return;
        }
        if (node instanceof FrostlakeParser.ExpressionContext
                || node instanceof FrostlakeParser.BooleanExprContext) {
            checkExpressions(node, scope);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walkStatement(node.getChild(i), scope, counters, records, returnKind, compiler);
        }
    }

    /**
     * Refuse a RETURN of the other kind than its procedure declares — a scalar in a RETURNS TABLE
     * procedure, a table in any other — wherever in the body it is written and whatever branch it is on,
     * at the RETURN (live-verified).
     *
     * @param returnKind TRUE when the procedure returns a table, FALSE a scalar, null when no procedure
     *                   declaration governs the block
     */
    private static void rejectReturnKind(final FrostlakeParser.ReturnStatementContext ret, final Boolean returnKind) {
        if (returnKind == null || (ret.TABLE() != null) == returnKind.booleanValue()) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.at(ret.getStart().getLine(), ret.getStart().getCharPositionInLine(),
            " Declared return type '" + (returnKind.booleanValue() ? "TABLE" : "SCALAR")
                + "' is incompatible with actual return type '" + (returnKind.booleanValue() ? "SCALAR" : "TABLE") + "'"));
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
        if (node instanceof FrostlakeParser.ExecuteImmediateStatementContext) {
            checkImmediate((FrostlakeParser.ExecuteImmediateStatementContext) node, scope);
            return;
        }
        if (isSqlSubtree(node)) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            final DeferredNameRefusals deferred = DeferredNameRefusals.current();
            if (deferred == null) {
                requireRootOf(name, scope);
            } else {
                try {
                    requireRootOf(name, scope);
                } catch (final RuntimeException unresolved) {
                    deferred.defer(name, unresolved.getMessage());
                }
            }
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
     * The {@code :bind} references of a SELECT … INTO, each judged against the block's names. Live compiles the
     * statement without its INTO clause: a bind it cannot place is an identifier of that text, its name folded as
     * one is, and its position is where it stands once the clause and the space after it are cut out (all
     * live-verified: {@code SELECT 1, 2 INTO :x, :x WHERE :nosuch = 1} names 'NOSUCH' twelve places back).
     */
    private static void checkIntoStatementBinds(final FrostlakeParser.SelectIntoStatementContext into,
                                                final ParseTree node, final Set<String> scope) {
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.identifier() == null || scope.contains(canonical(text(bind.identifier())))) {
                return;
            }
            final Token at = bind.getStart();
            throw new RuntimeException(SqlCompilationError.at(at.getLine(),
                at.getCharPositionInLine() - intoClauseWidthBefore(into, at),
                invalidIdentifier(text(bind.identifier()), false)));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkIntoStatementBinds(into, node.getChild(i), scope);
        }
    }

    /**
     * The {@code :bind} references of an SQL statement the block runs, each judged against the block's names: live
     * compiles them with the block, on a branch that never runs too, and refuses one nothing declares as an identifier
     * whose name is folded as an unquoted one is, at the bind — {@code SELECT :NoSuch} names 'NOSUCH' (live-verified).
     * A numeric bind is a client's, and a block or task body inside the statement binds names of its own.
     */
    private static void checkEmbeddedBinds(final ParseTree node, final Set<String> scope) {
        if (node instanceof FrostlakeParser.BeginEndBlockContext || node instanceof FrostlakeParser.TaskBodyContext) {
            return;
        }
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.identifier() != null) {
                requireName(text(bind.identifier()), bind, scope, false);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkEmbeddedBinds(node.getChild(i), scope);
        }
    }

    /**
     * Whether {@code node} is a DML statement whose binds compile with the block: an INSERT, UPDATE, DELETE or MERGE.
     * A COPY is left to run time.
     */
    private static boolean isBindingDml(final ParseTree node) {
        return node instanceof FrostlakeParser.DmlStatementContext
            && ((FrostlakeParser.DmlStatementContext) node).copyIntoStatement() == null;
    }

    /**
     * What a RESULTSET is filled from. A query, a CALL and a DML statement are SQL the block runs, whose binds are
     * judged as an SQL statement's (live-verified: {@code LET r RESULTSET := (SELECT :nosuch)} names 'NOSUCH'); an
     * EXECUTE IMMEDIATE's text and USING names are the block's own expressions.
     */
    private static void checkResultSetSource(final FrostlakeParser.ResultSetSourceContext source,
                                             final Set<String> scope) {
        if (source.selectStatement() != null) {
            checkEmbeddedBinds(source.selectStatement(), scope);
        } else if (source.callStatement() != null) {
            checkCall(source.callStatement(), scope);
        } else if (source.executeImmediateStatement() != null) {
            checkImmediate(source.executeImmediateStatement(), scope);
        } else if (source.resultSetStatement() != null) {
            checkResultSetStatement(source.resultSetStatement(), scope);
        }
        // A block the RESULTSET is filled from compiles when it runs, as an EXECUTE IMMEDIATE's text does.
    }

    /**
     * A CALL the block runs. Its binds are an SQL statement's and compile with the block. A procedure's arguments are
     * the block's expressions, whose names are judged here; a SYSTEM$ function's are SQL, judged when the CALL runs
     * (live-verified: {@code IF (1 = 2) THEN CALL SYSTEM$TYPEOF(nosuch); END IF;} compiles).
     */
    private static void checkCall(final FrostlakeParser.CallStatementContext call, final Set<String> scope) {
        checkEmbeddedBinds(call, scope);
        if (call.systemFunctionName() == null) {
            checkExpressions(call, scope);
        }
    }

    /** A RESULTSET's statement other than a query, a CALL or an EXECUTE IMMEDIATE: a DML statement's binds compile. */
    private static void checkResultSetStatement(final FrostlakeParser.ResultSetStatementContext statement,
                                                final Set<String> scope) {
        if (isBindingDml(statement.dmlStatement())) {
            checkEmbeddedBinds(statement.dmlStatement(), scope);
        } else {
            checkExpressions(statement, scope);
        }
    }

    /**
     * An EXECUTE IMMEDIATE compiles with the block: the expression that gives its text is a scripting expression,
     * whose bare names fold and whose binds are echoed as written ({@code EXECUTE IMMEDIATE :nosuch} names 'nosuch'),
     * and then its USING names (all live-verified). The text itself is judged when it runs.
     */
    private static void checkImmediate(final FrostlakeParser.ExecuteImmediateStatementContext statement,
                                       final Set<String> scope) {
        if (statement.expression() != null) {
            checkExpressions(statement.expression(), scope);
        }
        requireUsingNames(statement, scope);
    }

    /**
     * An EXECUTE IMMEDIATE's USING arguments name variables of the block, and compile with it, on a branch that never
     * runs too, each refused at the argument (all live-verified): a name nothing declares, an exception's, and SQLSTATE
     * or SQLERRM outside an exception handler are "invalid identifier"; a cursor is " Invalid use of cursor 'C'." and a
     * RESULTSET " Invalid use of resultset 'R'.". SQLCODE, SQLROWCOUNT, SQLFOUND and SQLNOTFOUND bind anywhere, NULL
     * until something sets them, and a FOR loop's variable binds too. The text itself is judged when it runs. An
     * invalid identifier waits with the block's other bare names (see DeferredNameRefusals), while a cursor or a
     * RESULTSET is refused where the compile meets it (live-verified).
     */
    private static void requireUsingNames(final FrostlakeParser.ExecuteImmediateStatementContext statement,
                                          final Set<String> scope) {
        for (final FrostlakeParser.UsingArgumentContext argument : statement.usingArgument()) {
            final String written = text(argument);
            final String name = canonical(written);
            final Token at = argument.getStart();
            final ParserRuleContext declaration = DeclarationLookup.declarationOf(argument, name);
            final boolean known;
            if (declaration != null) {
                known = !DeclarationLookup.isException(declaration);
            } else if (SCRIPT_SUPPLIED.contains(name)) {
                known = !HANDLER_SUPPLIED.contains(name) || DeclarationLookup.insideExceptionHandler(argument);
            } else {
                known = scope.contains(name);
            }
            if (!known) {
                final String refusal = SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    invalidIdentifier(written, false));
                final DeferredNameRefusals deferred = DeferredNameRefusals.current();
                if (deferred == null) {
                    throw new RuntimeException(refusal);
                }
                deferred.defer(argument, refusal);
                continue;
            }
            final String misuse = DeclarationLookup.misuse(declaration);
            if (misuse != null) {
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    " Invalid use of " + misuse + " '" + name + "'."));
            }
        }
    }

    /**
     * An assignment's target must be a variable (all live-verified, at the {@code :=}, while the block compiles): a
     * cursor is " Assignment to variable 'C' is not permitted." whatever it is assigned, and an exception, which
     * lives in a namespace of its own, is "invalid identifier 'E'".
     *
     * @param target      the target as written
     * @param assign      the {@code :=}
     * @param declaration the target's declaration in sight, or null
     */
    private static void refuseUnassignable(final FrostlakeParser.IdentifierContext target, final Token assign,
                                           final ParserRuleContext declaration) {
        if (DeclarationLookup.isException(declaration)) {
            throw new RuntimeException(SqlCompilationError.at(assign.getLine(), assign.getCharPositionInLine(),
                invalidIdentifier(text(target), false)));
        }
        if (DeclarationLookup.CURSOR.equals(DeclarationLookup.misuse(declaration))) {
            throw new RuntimeException(SqlCompilationError.at(assign.getLine(), assign.getCharPositionInLine(),
                " Assignment to variable '" + canonical(text(target)) + "' is not permitted."));
        }
    }

    /**
     * How many characters of the INTO clause, with the space after it, stand before {@code at} on its line: the
     * clause's whole width when {@code at} follows it on the line the clause ends on, and none otherwise.
     */
    private static int intoClauseWidthBefore(final FrostlakeParser.SelectIntoStatementContext into, final Token at) {
        final Token intoWord = into.INTO().getSymbol();
        final Token lastTarget = into.intoTargetList().getStop();
        if (at.getTokenIndex() <= lastTarget.getTokenIndex() || at.getLine() != lastTarget.getLine()
                || intoWord.getLine() != lastTarget.getLine()) {
            return 0;
        }
        final String line = at.getInputStream().getText(Interval.of(lastTarget.getStopIndex() + 1, at.getStartIndex() - 1));
        int space = 0;
        while (space < line.length() && Character.isWhitespace(line.charAt(space))) {
            space++;
        }
        return lastTarget.getStopIndex() + 1 + space - intoWord.getStartIndex();
    }

    /**
     * One variable may stand in a SELECT … INTO clause only once. Naming it twice is refused while the
     * block COMPILES — before any statement of it runs, so no statement-error wrapper carries it — at
     * the statement's own SELECT and with the name upper-cased (live-verified).
     *
     * @param ctx the block being compiled
     */
    static void rejectRepeatedIntoTargets(final ParseTree ctx) {
        if (ctx instanceof FrostlakeParser.ResultSetBlockContext) {
            // A RESULTSET's block source compiles when it runs.
            return;
        }
        if (ctx instanceof FrostlakeParser.SelectIntoStatementContext) {
            final FrostlakeParser.SelectIntoStatementContext into =
                (FrostlakeParser.SelectIntoStatementContext) ctx;
            final Set<String> seen = new HashSet<String>();
            for (final FrostlakeParser.IntoTargetContext target : into.intoTargetList().intoTarget()) {
                final String name = canonical(target.identifier().getText());
                if (!seen.add(name)) {
                    final Token select = into.SELECT().getSymbol();
                    throw new RuntimeException(SqlCompilationError.at(select.getLine(),
                        select.getCharPositionInLine(),
                        " Repeated variable with name '" + name + "' inside the INTO clause."));
                }
            }
            return;
        }
        for (int i = 0; i < ctx.getChildCount(); i++) {
            rejectRepeatedIntoTargets(ctx.getChild(i));
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
        if (node == null || isSqlSubtree(node) || node instanceof FrostlakeParser.ResultSetBlockContext) {
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
     * name, ahead of anything later in the body. A bare name that resolves to nothing, a bare NULL, a
     * cursor record's field and an initialiser that reads a bind (see {@link #readsABindUntyped}) are such
     * initialisers; an unknown name inside a larger expression is the ordinary invalid identifier instead,
     * and a bind no variable declares is refused ahead of all of them (live-verified).
     */
    private static void rejectUntypableInitialiser(final FrostlakeParser.BooleanExprContext initialiser,
                                                   final FrostlakeParser.IdentifierContext name,
                                                   final Token at, final Set<String> scope,
                                                   final Set<String> records,
                                                   final InferredInitialiserCompiler compiler) {
        if (initialiser != null) {
            // Its binds resolve before its type is inferred: `LET b := missing + :nosuch` names 'nosuch'.
            checkBindsOnly(initialiser, scope);
        }
        // A NOT, an AND or an OR always types its name a BOOLEAN — unless it reads a bind.
        if (name != null && initialiser != null && (readsABindUntyped(initialiser)
                || initialiser instanceof FrostlakeParser.ValueExprContext
                    && untypable(((FrostlakeParser.ValueExprContext) initialiser).expression(), scope, records))) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                " variable '" + canonical(text(name)) + "' cannot have its type inferred from initializer"));
        }
        if (compiler != null && initialiser != null) {
            compileFromlessSubqueries(initialiser, compiler);
        }
    }

    /**
     * The account infers an untyped declaration's type by COMPILING its initialiser, so every FROM-less
     * scalar subquery in it compiles with the block: a name it cannot resolve is refused before anything
     * runs, on a branch that never runs too, at the name's place in the block. {@code LET a := (SELECT
     * missing)} is that compilation error, where a typed {@code LET a NUMBER := (SELECT missing)} fails only
     * when it runs (live-verified).
     */
    private static void compileFromlessSubqueries(final ParseTree node, final InferredInitialiserCompiler compiler) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            final FrostlakeParser.SelectStatementContext query =
                ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement();
            if (query != null && !readsARelation(query)) {
                compiler.compile(query);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            compileFromlessSubqueries(node.getChild(i), compiler);
        }
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

    /**
     * Whether an initialiser reads a bind the type inference cannot type. The account takes the type of a bind
     * written alone ({@code LET b := :a}, parenthesised or not) from its variable, and a cast's from its target
     * whatever it casts ({@code :a::NUMBER}, {@code CAST((SELECT :a) AS VARCHAR)}, {@code TRY_CAST(:s AS
     * NUMBER)}); any other initialiser holding a bind anywhere — an operand, a call's argument, a NOT, AND or
     * OR, a subquery — gives the declaration no type (live-verified).
     */
    private static boolean readsABindUntyped(final FrostlakeParser.BooleanExprContext initialiser) {
        if (!holdsABind(initialiser)) {
            return false;
        }
        if (!(initialiser instanceof FrostlakeParser.ValueExprContext)) {
            return true;
        }
        FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) initialiser).expression();
        while (value instanceof FrostlakeParser.ParenExprContext
                && ((FrostlakeParser.ParenExprContext) value).booleanExpr() instanceof FrostlakeParser.ValueExprContext) {
            value = ((FrostlakeParser.ValueExprContext) ((FrostlakeParser.ParenExprContext) value).booleanExpr())
                .expression();
        }
        return !(value instanceof FrostlakeParser.BindVarExprContext
            || value instanceof FrostlakeParser.CastExprContext
            || value instanceof FrostlakeParser.TryCastExprContext
            || value instanceof FrostlakeParser.CastExpr2Context);
    }

    /** Whether a named {@code :bind} stands anywhere under {@code node}. */
    private static boolean holdsABind(final ParseTree node) {
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            return ((FrostlakeParser.BindVarExprContext) node).identifier() != null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsABind(node.getChild(i))) {
                return true;
            }
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
        if (node instanceof FrostlakeParser.CursorDeclarationContext || node instanceof FrostlakeParser.ResultSetBlockContext) {
            // A RESULTSET's block source compiles when it runs.
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
        // A bind with a quoted name is the same kind of fault, judged in the same order of the text.
        final Token quoted = QuotedBindName.of(node);
        if (quoted != null) {
            throw QuotedBindName.refusal(quoted);
        }
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
        // So is a semicolon closing a parenthesized statement: its last token, after every fault inside it.
        final Token semicolon = ParenthesizedStatementEnd.of(node);
        if (semicolon != null) {
            throw ParenthesizedStatementEnd.refusal(semicolon);
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
