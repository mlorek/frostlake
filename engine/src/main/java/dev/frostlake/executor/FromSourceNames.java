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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Set;

/**
 * The names a query level's sources are registered under, and the first one registered twice, which
 * live refuses as {@code duplicate alias 'A'} (live-verified). A source registers its alias when one is
 * written. Without one, a table, view or CTE registers its own name however it is qualified; a subquery
 * the moniker {@code values}; a VALUES list {@code VALUES}; and a table function its function's name
 * (FLATTEN, GENERATOR, SPLIT_TO_TABLE). A PIVOT's alias stands for its source. An unquoted name folds
 * to upper case and a quoted one keeps its own, so {@code "RE"} and {@code re} collide while {@code "a"}
 * and {@code a} do not. A parenthesised join registers its own sources in the enclosing scope, and the
 * target of an UPDATE, a DELETE or a MERGE registers the same way, ahead of its sources. Each query
 * level is its own scope: a subquery's sources never collide with the enclosing query's.
 */
final class FromSourceNames {

    /** The name an unaliased subquery registers under. */
    private static final String SUBQUERY_MONIKER = "values";

    /** The name an unaliased VALUES list registers under. */
    private static final String VALUES_MONIKER = "VALUES";

    private final QueryExecutor executor;
    private final Set<String> taken = new HashSet<>();
    private String duplicate;

    FromSourceNames(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Register one name; the first name registered twice is kept. A null name, a shape whose name is not
     * known, registers nothing.
     *
     * @param name the canonical name
     */
    void register(final String name) {
        if (name != null && !taken.add(name) && duplicate == null) {
            duplicate = name;
        }
    }

    /** @return the first name registered twice, or null */
    String duplicate() {
        return duplicate;
    }

    /** Refuse the first name registered twice, if there is one. */
    void rejectDuplicate() {
        if (duplicate != null) {
            throw new RuntimeException(SqlCompilationError.of("duplicate alias '" + duplicate + "'"));
        }
    }

    /**
     * Register every source a clause lists directly, in written order: the FROM of a query, an UPDATE's
     * FROM or a DELETE's USING.
     *
     * @param clause the clause whose table references and joins are read
     */
    void registerSources(final ParserRuleContext clause) {
        if (clause.children == null) {
            return;
        }
        for (final ParseTree child : clause.children) {
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                registerReference((FrostlakeParser.TableReferenceContext) child);
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                registerReference(((FrostlakeParser.JoinClauseContext) child).tableReference());
            }
        }
    }

    /**
     * The names a clause's sources register, as {@link #registerSources} registers them, without registering
     * them: null when a source's name is not known here, or when a PIVOT or an UNPIVOT reshapes one.
     *
     * @param clause the clause whose table references and joins are read
     * @return the names, or null
     */
    Set<String> sourceNames(final ParserRuleContext clause) {
        final Set<String> names = new HashSet<>();
        return collectSourceNames(clause, names) ? names : null;
    }

    private boolean collectSourceNames(final ParserRuleContext clause, final Set<String> names) {
        if (clause.children == null) {
            return true;
        }
        for (final ParseTree child : clause.children) {
            final FrostlakeParser.TableReferenceContext ref = child instanceof FrostlakeParser.TableReferenceContext
                ? (FrostlakeParser.TableReferenceContext) child
                : child instanceof FrostlakeParser.JoinClauseContext
                    ? ((FrostlakeParser.JoinClauseContext) child).tableReference() : null;
            if (ref == null) {
                continue;
            }
            if (ref.pivotClause() != null || ref.unpivotClause() != null) {
                return false;
            }
            final FrostlakeParser.TableSourceContext source = ref.tableSource();
            if (source != null && source.tableReference() != null && ref.aliasName() == null
                    && ref.nonJoinKeywordIdentifier() == null && ref.pivotAlias() == null) {
                if (!collectSourceNames(source, names)) {
                    return false;
                }
                continue;
            }
            final String name = nameOf(ref);
            if (name == null) {
                return false;
            }
            names.add(name);
        }
        return true;
    }

    /** Register one source as {@link #registerSources} registers each: a parenthesised join's sources, else its name. */
    void registerReference(final FrostlakeParser.TableReferenceContext ref) {
        final FrostlakeParser.TableSourceContext source = ref.tableSource();
        if (source != null && source.tableReference() != null && ref.aliasName() == null
                && ref.nonJoinKeywordIdentifier() == null && ref.pivotAlias() == null) {
            // A parenthesised join is pure grouping: its sources belong to this scope.
            registerSources(source);
            return;
        }
        register(nameOf(ref));
    }

    /** The name one source registers under, or null for a shape whose name is not known. */
    private String nameOf(final FrostlakeParser.TableReferenceContext ref) {
        if (ref.pivotAlias() != null) {
            return ParseTreeText.getIdentifier(ref.pivotAlias().identifier());
        }
        if (ref.aliasName() != null) {
            return ParseTreeText.getIdentifier(ref.aliasName());
        }
        if (ref.nonJoinKeywordIdentifier() != null) {
            final FrostlakeParser.NonJoinKeywordIdentifierContext alias = ref.nonJoinKeywordIdentifier();
            return alias.identifier() != null ? ParseTreeText.getIdentifier(alias.identifier())
                : alias.getText().toUpperCase();
        }
        final FrostlakeParser.TableSourceContext source = ref.tableSource();
        if (source.tableQualifiedName() != null) {
            return lastPart(ParseTreeText.qualifiedNameParts(source.tableQualifiedName()));
        }
        if (source.selectStatement() != null) {
            return SUBQUERY_MONIKER;
        }
        if (source.VALUES() != null) {
            return source.identifier() != null ? ParseTreeText.getIdentifier(source.identifier()) : VALUES_MONIKER;
        }
        if (source.FLATTEN() != null) {
            return "FLATTEN";
        }
        if (source.tableFunctionExpr() != null) {
            return lastPart(ParseTreeText.functionNameParts(source.tableFunctionExpr().functionName()));
        }
        if (source.TABLE() != null && source.expression() != null) {
            final FrostlakeParser.FunctionNameContext function = firstFunctionName(source.expression());
            // A table literal registers the name its value resolves to, as an IDENTIFIER() source does.
            return function == null ? tableLiteralName(source.expression())
                : lastPart(ParseTreeText.functionNameParts(function));
        }
        if (source.identifierArgument() != null) {
            return identifierReferenceName(source.identifierArgument());
        }
        return null;
    }

    /**
     * The name a {@code TABLE(<value>)} table literal resolves to, or null when it cannot be read here.
     * A name that reaches no relation registers nothing: live resolves each source before it weighs the
     * aliases, so {@code FROM TABLE('nosuch'), TABLE('nosuch')} names the missing object, not a duplicate.
     */
    private String tableLiteralName(final FrostlakeParser.ExpressionContext expr) {
        try {
            final Object value = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                executor).evaluate(executor.getOriginalText(expr), null);
            if (value == null || !SqlIdentifiers.isIdentifierReference(value.toString())) {
                return null;
            }
            final String written = value.toString().trim();
            return executor.namesARelation(SqlIdentifiers.identifierReferenceText(written))
                ? lastPart(SqlIdentifiers.identifierReferenceParts(written)) : null;
        } catch (final RuntimeException unreadable) {
            // The source's own resolution reports it.
            return null;
        }
    }

    /** The name an IDENTIFIER(...) source resolves to, or null when it cannot be read here. */
    private String identifierReferenceName(final FrostlakeParser.IdentifierArgumentContext argument) {
        try {
            final Object value = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                executor).evaluate(executor.getOriginalText(argument), null);
            return value == null ? null : lastPart(SqlIdentifiers.identifierReferenceParts(value.toString().trim()));
        } catch (final RuntimeException unreadable) {
            // The source's own resolution reports it.
            return null;
        }
    }

    /** The name a MERGE's source registers under when it carries no alias. */
    static String mergeSourceName(final FrostlakeParser.MergeSourceContext source) {
        if (source instanceof FrostlakeParser.MergeSourceTableContext) {
            return lastPart(ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.MergeSourceTableContext) source).qualifiedName()));
        }
        return source instanceof FrostlakeParser.MergeSourceValuesContext ? VALUES_MONIKER : SUBQUERY_MONIKER;
    }

    /** The first function name under a node, reading left to right. */
    private static FrostlakeParser.FunctionNameContext firstFunctionName(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionNameContext) {
            return (FrostlakeParser.FunctionNameContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.FunctionNameContext found = firstFunctionName(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The last of a name's parts, or null when there are none. */
    static String lastPart(final String[] parts) {
        return parts == null || parts.length == 0 ? null : parts[parts.length - 1];
    }
}
