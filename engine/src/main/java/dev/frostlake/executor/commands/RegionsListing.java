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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * SHOW REGIONS: the regions accounts are hosted in, a public list every account answers alike — the region's
 * identifier, its cloud, the cloud's own name for it and the name it is shown by (live-verified, transcribed to
 * {@code regions.tsv}).
 *
 * <p>LIKE matches the region's identifier, in any case; TERSE, HISTORY, STARTS WITH and LIMIT are read and change
 * nothing. The listing takes no scope: any IN is refused, before a LIMIT 0 or a WITH PRIVILEGES is judged, by a
 * sentence that writes the statement back in upper case:
 *
 * <pre>
 *   SHOW REGIONS IN ACCOUNT LIMIT 1    Unsupported feature 'SHOW REGIONS IN ACCOUNT LIMIT 1'; use 'SHOW REGIONS IN
 *                                      DATA EXCHANGE &lt;exchange_name&gt;' instead.
 *   SHOW REGIONS IN ACCOUNT x LIMIT 1  … 'SHOW REGIONS IN ACCOUNT' — what a scope keyword names, and the rest
 *   SHOW REGIONS IN a.b                … 'SHOW REGIONS IN A.B'
 *   SHOW REGIONS IN APPLICATION a      … 'SHOW REGIONS IN TOK_APPLICATION_INSTANCE'; TOK_APPLICATION_PACKAGE and
 *                                      TOK_COMPUTE_POOL alike
 * </pre>
 */
final class RegionsListing {

    private static final List<String[]> REGIONS = load();

    private RegionsListing() {
    }

    /**
     * The listing, or the refusal of its scope.
     *
     * @param ctx the statement
     * @return the regions
     */
    static ResultSet show(final FrostlakeParser.ShowStatementContext ctx) {
        final List<Row> rows = new ArrayList<>();
        for (final String[] region : REGIONS) {
            rows.add(new Row(new ArrayList<Object>(Arrays.asList(region))));
        }
        return new ResultSet(UnmodelledListing.REGIONS.columns(false), rows);
    }

    /**
     * The refusal of a scope.
     *
     * @param ctx the statement, written with an IN
     * @return the refusal
     */
    static RuntimeException scopeRefusal(final FrostlakeParser.ShowStatementContext ctx) {
        return new RuntimeException("Unsupported feature '" + written(ctx)
            + "'; use 'SHOW REGIONS IN DATA EXCHANGE <exchange_name>' instead.");
    }

    /**
     * The statement as the refusal writes it back: in upper case, and up to the keyword of a scope that names its
     * object — the name and all that follows it are left out, and an application's keyword is written as the
     * account's parser calls it.
     */
    private static String written(final FrostlakeParser.ShowStatementContext ctx) {
        final Token first = ctx.getStart();
        final TerminalNode keyword = scopeKeyword(ctx);
        if (keyword != null) {
            final boolean named = ctx.objectName() != null || ctx.openedIdentifierReference() != null;
            return text(first, named ? keyword.getSymbol() : last(ctx));
        }
        final ShowScopeKind kind = ShowScopeKind.of(ctx.objectName(), ctx.showInstanceName());
        if (kind == null || ctx.showInstanceName() == null) {
            return text(first, last(ctx));
        }
        if (kind == ShowScopeKind.APPLICATION) {
            return text(first, ctx.IN().getSymbol()) + " TOK_APPLICATION_INSTANCE";
        }
        if (kind == ShowScopeKind.APPLICATION_PACKAGE || kind == ShowScopeKind.COMPUTE_POOL) {
            return text(first, ctx.IN().getSymbol()) + " TOK_" + kind.name();
        }
        final Token second = ctx.showInstanceName().showKindWord;
        return text(first, second != null ? second : ctx.objectName().getStop());
    }

    /** The keyword a scope is written with — ACCOUNT, DATABASE, SCHEMA or TABLE — or null. */
    private static TerminalNode scopeKeyword(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.ACCOUNT() != null) {
            return ctx.ACCOUNT();
        }
        if (ctx.DATABASE() != null) {
            return ctx.DATABASE();
        }
        return ctx.SCHEMA() != null ? ctx.SCHEMA() : ctx.TABLE();
    }

    /** The statement's last token, a closing semicolon apart. */
    private static Token last(final FrostlakeParser.ShowStatementContext ctx) {
        for (int i = ctx.getChildCount() - 1; i >= 0; i--) {
            final ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode) {
                if (child != ctx.SEMI()) {
                    return ((TerminalNode) child).getSymbol();
                }
            } else if (child.getChildCount() > 0) {
                return ((ParserRuleContext) child).getStop();
            }
        }
        return ctx.getStop();
    }

    private static String text(final Token first, final Token last) {
        return first.getInputStream().getText(Interval.of(first.getStartIndex(), last.getStopIndex()))
            .toUpperCase(Locale.ROOT);
    }

    private static List<String[]> load() {
        final List<String[]> regions = new ArrayList<>();
        try (InputStream in = RegionsListing.class.getResourceAsStream("/regions.tsv")) {
            if (in == null) {
                throw new IllegalStateException("missing region list");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.isEmpty()) {
                        regions.add(line.split("\t", -1));
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load region list", e);
        }
        return regions;
    }
}
