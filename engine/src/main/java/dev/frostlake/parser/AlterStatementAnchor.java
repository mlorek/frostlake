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

package dev.frostlake.parser;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Where live refuses a fault in an ALTER statement, or in whatever runs straight on from one without a semicolon,
 * for the kinds whose statement live's parser gives up on further back than the token that failed (all
 * live-verified):
 *
 * <pre>
 *   ALTER TABLE t1 RENAME TO             'RENAME' at 15       every fault after the RENAME of a table, a view, a
 *   ALTER TABLE t1 RENAME COLUMN a       'RENAME' at 15       materialized view, a schema, a database, a
 *   ALTER TABLE t1 RENAME TO t3 x        'RENAME' at 15       warehouse, a user, a tag, a stage, a masking or a
 *                                                            row access policy names the RENAME
 *   ALTER SEQUENCE s1 RENAME TO;         'TO' at 25           a sequence's names the TO, and a fault after the
 *   ALTER SEQUENCE s1 RENAME TO x y      'TO', then 'y'       complete statement still speaks after it
 *   ALTER ROLE r RENAME                  'ROLE', then 'r'     any fault at all in a role's, a stream's or a file
 *   ALTER FILE FORMAT ff SET             'FILE', 'FORMAT'     format's statement names its kind, then the token
 *   ALTER STREAM st SET COMMENT = 'x' x  'STREAM', then 'st'  after it
 *   ALTER MASKING POLICY mp UNSET        'UNSET' at 24        a policy's UNSET names the UNSET for any fault in
 *   ALTER MASKING POLICY mp SET COMMENT  'COMMENT' at 28      its list, a SET the first word after it for any
 *   ALTER MASKING POLICY mp SET TAG x    'TAG' at 28          fault in its TAG or property list — the word after
 *   ALTER MASKING POLICY mp SET BODY = 1 '=' at 33            BODY when that is no arrow — and a fault in a new
 *   … SET BODY -> val COMMENT = 'x'      'COMMENT' at 40      body stays where it falls, up to the first token
 *                                                            after the body, which nothing may follow
 *   ALTER USER u SET EMAIL = 'x' 'y'     'EMAIL' at 17        a user's SET or UNSET names the first word after it
 *   ALTER USER u UNSET EMAIL COMMENT     'EMAIL' at 19        for any fault in its property list
 * </pre>
 *
 * <p>Nothing more of the statement is reported after these lines. A sequence's RENAME followed by any word but TO
 * is refused at that word, as this parser already does.
 */
public final class AlterStatementAnchor {

    private AlterStatementAnchor() {
    }

    /**
     * The tokens live reports for a fault inside an ALTER statement, or straight after a complete one: the refused
     * token first, then the token of the line live stacks after it, if any.
     *
     * @param recognizer the parser that met the fault
     * @param reported the token the fault is currently reported at
     * @return the tokens, or null when the fault is no such case
     */
    static List<Token> refusal(final Recognizer<?, ?> recognizer, final Token reported) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        FrostlakeParser.AlterStatementContext alter = null;
        for (ParserRuleContext context = parser.getContext(); context != null && alter == null;
                context = context.getParent()) {
            if (context instanceof FrostlakeParser.AlterStatementContext) {
                alter = (FrostlakeParser.AlterStatementContext) context;
            }
        }
        boolean after = false;
        if (alter == null) {
            final FrostlakeParser.StatementContext runOn = UnseparatedStatementOpener.runOnStatement(parser, reported);
            if (runOn == null || runOn.ddlStatement() == null || runOn.ddlStatement().alterStatement() == null) {
                return null;
            }
            alter = runOn.ddlStatement().alterStatement();
            after = true;
        }
        final TokenStream stream = parser.getInputStream();
        final List<Token> lines = new ArrayList<>();
        final TerminalNode kind = alter.ROLE() != null ? alter.ROLE()
            : alter.STREAM() != null ? alter.STREAM() : alter.FILE();
        if (kind != null) {
            final Token next = nextSpoken(stream, kind.getSymbol().getTokenIndex());
            lines.add(kind.getSymbol());
            if (next != null) {
                lines.add(next);
            }
            return lines;
        }
        final Token policyWord = after && alter.policyAction() != null && alter.policyAction().BODY() != null
            ? nextSpoken(stream, alter.getStop().getTokenIndex()) : policyListFault(alter, stream, reported);
        if (policyWord != null) {
            lines.add(policyWord);
            return lines;
        }
        final Token userWord = userListFault(alter, stream, reported);
        if (userWord != null) {
            lines.add(userWord);
            return lines;
        }
        final Token rename = renameStart(alter);
        if (rename == null || rename.getTokenIndex() > reported.getTokenIndex()) {
            return null;
        }
        if (refusedAtRename(alter)) {
            lines.add(rename);
            return lines;
        }
        if (alter.SEQUENCE() != null) {
            final Token to = nextSpoken(stream, rename.getTokenIndex());
            if (to != null && to.getType() == FrostlakeLexer.TO && to.getTokenIndex() < reported.getTokenIndex()) {
                lines.add(to);
                if (after) {
                    final Token opener = UnseparatedStatementOpener.refused(recognizer, reported, false);
                    lines.add(opener != null ? opener : reported);
                }
                return lines;
            }
        }
        return null;
    }

    /** The RENAME a statement's action opens with, or null when its action is another. */
    private static Token renameStart(final FrostlakeParser.AlterStatementContext alter) {
        for (int i = 0; i < alter.getChildCount(); i++) {
            final ParseTree child = alter.getChild(i);
            if (!(child instanceof ParserRuleContext) || child instanceof FrostlakeParser.If_existsContext
                    || child instanceof FrostlakeParser.QualifiedNameContext
                    || child instanceof FrostlakeParser.ObjectNameContext
                    || child instanceof FrostlakeParser.OpenedIdentifierReferenceContext
                    || child instanceof FrostlakeParser.IdentifierContext) {
                continue;
            }
            final Token start = ((ParserRuleContext) child).getStart();
            return start != null && start.getType() == FrostlakeLexer.RENAME ? start : null;
        }
        return null;
    }

    /** Whether live names the RENAME itself for a fault after it: the kinds whose rename it reads as one piece. */
    private static boolean refusedAtRename(final FrostlakeParser.AlterStatementContext alter) {
        return alter.TABLE() != null && alter.DYNAMIC() == null
            || alter.VIEW() != null
            || alter.SCHEMA() != null
            || alter.DATABASE() != null
            || alter.WAREHOUSE() != null
            || alter.USER() != null
            || alter.TAG() != null
            || alter.STAGE() != null
            || alter.policyAction() != null;
    }

    /**
     * The word live refuses a fault in a policy's UNSET list, TAG list or property list at, or null when the fault
     * lies elsewhere: a RENAME and a new body read on as the other kinds do.
     */
    private static Token policyListFault(final FrostlakeParser.AlterStatementContext alter, final TokenStream stream,
                                         final Token reported) {
        final FrostlakeParser.PolicyActionContext action = alter.policyAction();
        if (action == null || action.getStart() == null
                || action.getStart().getTokenIndex() > reported.getTokenIndex()) {
            return null;
        }
        final Token verb = action.getStart();
        if (verb.getType() == FrostlakeLexer.UNSET) {
            return verb;
        }
        if (verb.getType() != FrostlakeLexer.SET) {
            return null;
        }
        final Token first = nextSpoken(stream, verb.getTokenIndex());
        if (first == null || first.getType() != FrostlakeLexer.BODY) {
            return first;
        }
        final Token second = nextSpoken(stream, first.getTokenIndex());
        return second == null || second.getType() == FrostlakeLexer.THIN_ARROW ? null : second;
    }

    /**
     * The word live refuses a fault in a user's SET or UNSET property list at: the first word after the SET or the
     * UNSET, whichever property the fault lies in — {@code ALTER USER u SET EMAIL = 'x' DAYS_TO_EXPIRY} is refused at
     * EMAIL — or null when the fault lies elsewhere or the SET opens a TAG list.
     */
    private static Token userListFault(final FrostlakeParser.AlterStatementContext alter, final TokenStream stream,
                                       final Token reported) {
        final FrostlakeParser.UserActionContext action = alter.userAction();
        if (alter.USER() == null || action == null || action.getStart() == null
                || action.getStart().getTokenIndex() > reported.getTokenIndex()) {
            return null;
        }
        final Token verb = action.getStart();
        if (verb.getType() != FrostlakeLexer.SET && verb.getType() != FrostlakeLexer.UNSET) {
            return null;
        }
        final Token first = nextSpoken(stream, verb.getTokenIndex());
        return first == null || first.getType() == FrostlakeLexer.TAG ? null : first;
    }

    /** The next default-channel token after token index {@code after}, or null past the end. */
    private static Token nextSpoken(final TokenStream stream, final int after) {
        for (int i = after + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
