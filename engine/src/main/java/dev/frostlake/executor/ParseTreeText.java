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

import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.parser.FrostlakeParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Stateless helpers that read text and names out of ANTLR parse-tree nodes. Extracted from
 * {@link QueryExecutor} so query stages can share the same parse-tree text extraction without
 * depending on engine instance state — every method here is a pure function of its arguments.
 */
public final class ParseTreeText {

    private ParseTreeText() {
    }

    /** Get the whereClause of a selectClause, or null when absent. */
    public static FrostlakeParser.WhereClauseContext getWhereClause(final FrostlakeParser.SelectClauseContext ctx) {
        return ctx.whereClause();
    }

    /** Extract SelectClauseContext from a selectOperand (bare or parenthesised). */
    public static FrostlakeParser.SelectClauseContext getSelectClause(final FrostlakeParser.SelectOperandContext op) {
        if (op.selectClause() != null) return op.selectClause();
        return null; // parenthesised sub-select — caller handles via op.selectStatement()
    }

    /** Collect all top-level SelectClauseContexts from selectOperands (non-parenthesised ones). */
    public static List<FrostlakeParser.SelectClauseContext> getSelectClauses(final FrostlakeParser.SelectStatementContext ctx) {
        final List<FrostlakeParser.SelectClauseContext> result = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext op : ctx.selectOperand()) {
            if (op.selectClause() != null) result.add(op.selectClause());
        }
        return result;
    }

    public static String getIdentifier(final FrostlakeParser.IdentifierContext ctx) {
        return SqlIdentifiers.canonical(ctx);
    }

    /** The canonical text of an ALIAS, which may be an ordinary identifier or one of the words legal
     *  only in a name position (CASE, CAST, CONSTRAINT, CROSS, DEFAULT, INNER, JOIN, WHEN). The
     *  keyword form is unquoted by construction, so it upper-cases directly — the same split
     *  {@link #namePartText} makes. */
    public static String getIdentifier(final FrostlakeParser.AliasNameContext ctx) {
        return ctx.identifier() != null ? getIdentifier(ctx.identifier())
            : ctx.getText().toUpperCase();
    }

    public static String getQualifiedName(final FrostlakeParser.QualifiedNameContext ctx) {
        return QualifiedName.join(qualifiedNameParts(ctx));
    }

    /** The canonical text of one name part: a regular identifier folds through
     *  {@link SqlIdentifiers#canonical}; a keyword-as-name part (INNER, JOIN, LEFT, CROSS, CASE)
     *  is unquoted by construction and upper-cases directly. */
    public static String namePartText(final FrostlakeParser.NameStartPartContext part) {
        return part.identifier() != null ? getIdentifier(part.identifier())
            : part.getText().toUpperCase();
    }

    /** See {@link #namePartText(FrostlakeParser.NameStartPartContext)} — the after-dot flavour. */
    public static String namePartText(final FrostlakeParser.NamePartContext part) {
        return part.columnDefName() != null ? namePartText(part.columnDefName())
            : part.getText().toUpperCase();
    }

    /** The COLUMN-DEFINITION flavour — the same vocabulary less CONSTRAINT, which leads a constraint. */
    public static String namePartText(final FrostlakeParser.ColumnDefNameContext part) {
        return part.identifier() != null ? getIdentifier(part.identifier())
            : part.getText().toUpperCase();
    }

    /** The FROM-position flavour: a {@code tableQualifiedName}'s parts. */
    public static String[] qualifiedNameParts(final FrostlakeParser.TableQualifiedNameContext ctx) {
        final List<FrostlakeParser.NamePartContext> rest = ctx.namePart();
        final String[] parts = new String[1 + rest.size()];
        parts[0] = namePartText(ctx.nameStartPart());
        for (int i = 0; i < rest.size(); i++) {
            parts[1 + i] = namePartText(rest.get(i));
        }
        return withEmptySchemaPart(parts, ctx.DOT().size());
    }

    /** The FROM-position flavour of {@link #getQualifiedName}. */
    public static String getQualifiedName(final FrostlakeParser.TableQualifiedNameContext ctx) {
        return QualifiedName.join(qualifiedNameParts(ctx));
    }

    /** The {@code <name>.*} flavour: a {@code starQualifiedName}'s parts. */
    public static String[] qualifiedNameParts(final FrostlakeParser.StarQualifiedNameContext ctx) {
        final List<FrostlakeParser.NamePartContext> rest = ctx.namePart();
        final String[] parts = new String[1 + rest.size()];
        parts[0] = namePartText(ctx.nameStartPart());
        for (int i = 0; i < rest.size(); i++) {
            parts[1 + i] = namePartText(rest.get(i));
        }
        return withEmptySchemaPart(parts, ctx.DOT().size());
    }

    /** The {@code <name>.*} flavour of {@link #getQualifiedName}. */
    public static String getQualifiedName(final FrostlakeParser.StarQualifiedNameContext ctx) {
        return QualifiedName.join(qualifiedNameParts(ctx));
    }

    /** The name parts of a qualified name, read from the parse tree instead of splitting its
     *  flattened text on '.' — correct even for a quoted identifier containing a dot. */
    public static String[] qualifiedNameParts(final FrostlakeParser.QualifiedNameContext ctx) {
        final List<FrostlakeParser.NamePartContext> rest = ctx.namePart();
        final String[] parts = new String[1 + rest.size()];
        parts[0] = namePartText(ctx.nameStartPart());
        for (int i = 0; i < rest.size(); i++) {
            parts[1 + i] = namePartText(rest.get(i));
        }
        return withEmptySchemaPart(parts, ctx.DOT().size());
    }

    /**
     * The canonical parts of a function's name, or null for a name that is a keyword or an
     * {@code IDENTIFIER(...)}; an empty middle part, {@code db..f}, is the database's PUBLIC schema.
     */
    public static String[] functionNameParts(final FrostlakeParser.FunctionNameContext ctx) {
        final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        final String[] parts = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            parts[i] = getIdentifier(ids.get(i));
        }
        return withEmptySchemaPart(parts, ctx.DOT().size());
    }

    /**
     * A star qualifier as written, with an empty middle part ({@code db..t.*}) spelled out as the
     * PUBLIC schema it names; any other qualifier exactly as written.
     */
    public static String writtenText(final FrostlakeParser.StarQualifiedNameContext ctx) {
        if (ctx.DOT().size() <= ctx.namePart().size()) {
            return ctx.getText();
        }
        final StringBuilder written = new StringBuilder(ctx.nameStartPart().getText()).append(".PUBLIC");
        for (final FrostlakeParser.NamePartContext part : ctx.namePart()) {
            written.append('.').append(part.getText());
        }
        return written.toString();
    }

    /**
     * Whether a name leaves its middle part empty, {@code db..t}: it then has as many dots as written
     * parts, where every other name has one dot fewer.
     */
    public static boolean hasEmptySchemaPart(final FrostlakeParser.QualifiedNameContext ctx) {
        return ctx.DOT().size() > ctx.namePart().size();
    }

    /**
     * The parts of a name whose middle part was left empty, {@code db..t}, with that part read as the
     * database's PUBLIC schema, which is how the account reads it everywhere but a SHOW scope; any
     * other name's parts unchanged.
     */
    private static String[] withEmptySchemaPart(final String[] written, final int dots) {
        if (dots < written.length) {
            return written;
        }
        final String[] parts = new String[written.length + 1];
        parts[0] = written[0];
        parts[1] = "PUBLIC";
        System.arraycopy(written, 1, parts, 2, written.length - 1);
        return parts;
    }

    /**
     * The plain (non-lambda) boolean-expression arguments of a function call. {@code functionArgList} now
     * admits lambda arguments (for TRANSFORM/FILTER/REDUCE); aggregate/window/grouping call sites only ever
     * have boolean-expression arguments, so this unwraps each {@code functionArg} to its {@code booleanExpr}.
     */
    public static List<FrostlakeParser.BooleanExprContext> functionBooleanArgs(final FrostlakeParser.FunctionArgListContext list) {
        final List<FrostlakeParser.BooleanExprContext> out = new ArrayList<>();
        if (list != null) {
            for (final FrostlakeParser.FunctionArgContext arg : list.functionArg()) {
                if (arg.booleanExpr() != null) {
                    out.add(arg.booleanExpr());
                }
            }
        }
        return out;
    }

    public static String getOriginalText(final ParserRuleContext ctx) {
        // Get original text with whitespace preserved
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex())
        );
    }

    public static String extractStringLiteral(final TerminalNode node) {
        return SqlStringLiterals.decode(node.getText());
    }

    public static String getIdentifier(final TerminalNode node) {
        final String text = node.getText();
        if (text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    public static Object parseLiteral(final FrostlakeParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            final String intText = ctx.INTEGER_LITERAL().getText();
            try {
                return Long.parseLong(intText);
            } catch (final NumberFormatException tooWide) {
                return new BigDecimal(intText);   // wider than a long — keep it exact (NUMBER(38,0))
            }
        } else if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            final String text = ctx.STRING_LITERAL().getText();
            return text.substring(1, text.length() - 1);
        } else if (ctx.TRUE() != null) {
            return true;
        } else if (ctx.FALSE() != null) {
            return false;
        } else if (ctx.NULL() != null) {
            return null;
        }
        return null;
    }
}
