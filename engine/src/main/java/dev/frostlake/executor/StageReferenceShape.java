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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A stage reference's name parts, and the refusals of a name no stage can live under, whatever reads it — a query,
 * COPY, LIST, REMOVE, PUT or GET (live-verified). A name of five parts or more is no identifier at all, four parts
 * are one too many, and an empty part among at most three resolves the way an object name's containers do — the
 * empty one names no database or schema:
 *
 * <pre>
 *   &#64;db....st, &#64;a.b.c.d.e     Invalid identifier db....st      the name as written, without its path
 *   &#64;db...st, &#64;a.b.c.d        Object does not exist, or operation cannot be performed.
 *   &#64;..st, &#64;.public.st        Database '""' does not exist or not authorized.
 *   &#64;db..st, &#64;db..%t          Schema 'DB.""' does not exist or not authorized.
 *   &#64;.st                    Schema 'CURRENT_DB.""' does not exist or not authorized.
 *   FROM &#64;db..%t            Object 'DB."".T' does not exist or not authorized.
 * </pre>
 *
 * <p>An object name reads an empty middle part as PUBLIC; a stage's is an empty schema.
 */
final class StageReferenceShape {

    private StageReferenceShape() {
    }

    /**
     * Refuse a stage reference whose name has too many parts, or an empty one.
     *
     * @param ctx the stage reference
     * @param catalog the catalog its containers resolve in
     */
    static void refuseEmptyParts(final FrostlakeParser.StageRefContext ctx, final Catalog catalog) {
        if (ctx.TILDE() != null) {
            return;
        }
        final List<String> parts = nameParts(ctx);
        if (parts.size() > 4) {
            throw new RuntimeException(SqlCompilationError.of("Invalid identifier " + writtenName(ctx)));
        }
        if (parts.size() == 4) {
            if (ctx.PERCENT() != null && !parts.contains("")) {
                throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Object",
                    QualifiedName.join(parts.toArray(new String[0]))));
            }
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        if (parts.contains("")) {
            catalog.requireOwningSchema(QualifiedName.of(parts.toArray(new String[0])));
        }
    }

    /**
     * A table stage read in a query's FROM with its schema left empty, {@code @db..%t} or {@code @.%t}, is refused
     * as the table it names — {@code Object 'DB."".T' does not exist or not authorized.}, {@code Object '"".T'} —
     * before its database is resolved.
     *
     * @param ctx the stage reference
     */
    static void refuseEmptyTableStageSchema(final FrostlakeParser.StageRefContext ctx) {
        if (ctx.PERCENT() == null) {
            return;
        }
        final List<String> parts = nameParts(ctx);
        if (parts.size() < 2 || parts.size() > 3 || !parts.get(parts.size() - 2).isEmpty()) {
            return;
        }
        final String table = parts.get(parts.size() - 1);
        final String database = parts.size() == 3 ? SqlIdentifiers.spellCanonical(parts.get(0)) + "." : "";
        throw new RuntimeException(SqlCompilationError.of("Object '" + database + "\"\"."
            + SqlIdentifiers.spellCanonical(table) + "' does not exist or not authorized."));
    }

    /**
     * The named stage a reference names, matched exactly — a quoted name keeps its case — or a refusal naming the
     * database, schema or stage that is missing.
     *
     * @param ctx the reference to a named stage
     * @param catalog the catalog it resolves in
     * @return the stage
     */
    static Stage namedStage(final FrostlakeParser.StageRefContext ctx, final Catalog catalog) {
        final List<String> parts = nameParts(ctx);
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts.toArray(new String[0])));
        final String name = parts.get(parts.size() - 1);
        if (!owner.hasStageExact(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", owner.qualifiedName(name)));
        }
        return owner.getStage(name);
    }

    /**
     * The canonical parts of a stage reference's name, an empty part as the empty string. A table stage's last
     * part is its table's name.
     *
     * @param ctx a reference to a named or a table stage
     * @return the parts, in order
     */
    static List<String> nameParts(final FrostlakeParser.StageRefContext ctx) {
        final List<String> parts = new ArrayList<>();
        String part = "";
        String percent = "";
        int percentAt = -1;
        for (int i = 1; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (child instanceof FrostlakeParser.StagePathContext) {
                break;
            }
            if (child instanceof FrostlakeParser.IdentifierContext) {
                part = ParseTreeText.getIdentifier((FrostlakeParser.IdentifierContext) child);
                if (!percent.isEmpty()) {
                    percentAt = parts.size();
                    percent = "";
                }
            } else if (child instanceof TerminalNode) {
                final int type = ((TerminalNode) child).getSymbol().getType();
                if (type == FrostlakeLexer.DOT) {
                    parts.add(part);
                    part = "";
                } else if (type == FrostlakeLexer.TABLE) {
                    part = "TABLE";
                    if (!percent.isEmpty()) {
                        percentAt = parts.size();
                        percent = "";
                    }
                } else if (type == FrostlakeLexer.PERCENT) {
                    // A table stage's '%' belongs to the NAME: where parts follow it, the account
                    // spells that part "%T" and quotes it, as any name holding a '%' must be.
                    percent = "%";
                }
            }
        }
        parts.add(part);
        // A table stage's '%' belongs to its name only where the name is a CONTAINER of what follows:
        // @%t is the stage itself and keeps the bare table name, while @%t.x reads %T as the schema
        // and the account spells it "%T".
        if (percentAt >= 0 && percentAt < parts.size() - 1) {
            parts.set(percentAt, "%" + parts.get(percentAt));
        }
        return parts;
    }

    /**
     * A stage reference's name as written, from the token after its {@code @} to the last one before its path.
     *
     * @param ctx the stage reference
     * @return the name's text
     */
    static String writtenName(final FrostlakeParser.StageRefContext ctx) {
        final Token first = firstToken(ctx.getChild(1));
        Token last = ctx.getStop();
        for (int i = 1; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof FrostlakeParser.StagePathContext) {
                last = lastToken(ctx.getChild(i - 1));
                break;
            }
        }
        return first.getInputStream().getText(Interval.of(first.getStartIndex(), last.getStopIndex()));
    }

    private static Token firstToken(final ParseTree node) {
        return node instanceof TerminalNode ? ((TerminalNode) node).getSymbol() : ((ParserRuleContext) node).getStart();
    }

    private static Token lastToken(final ParseTree node) {
        return node instanceof TerminalNode ? ((TerminalNode) node).getSymbol() : ((ParserRuleContext) node).getStop();
    }
}
