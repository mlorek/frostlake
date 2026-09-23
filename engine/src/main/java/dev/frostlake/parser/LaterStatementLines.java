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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * What live reports of the statements after a script's fault, judged one line at a time against the last line the
 * report kept (all live-verified). Past most faults live reads the next statement on its own, and a fault of that
 * statement is one more line — its end of the input as much as any token: {@code ALTER TABLE t1 RENAME TO; SELECT 1
 * x y} is 'RENAME' at 15 and 'y' at 37, {@code ALTER TABLE t1 RENAME TO; SELECT 1 FROM} 'RENAME' and '&lt;EOF&gt;' at
 * 39, and so after an operator, a FROM list, a WHERE, a bracket, a DROP, a DELETE, a GRANT, a SHOW, an EXPLAIN or a
 * SET left unfinished. The exceptions:
 *
 * <ul>
 *   <li>A SEMICOLON WHERE A HEAD'S NAME BELONGS passes over the next statement, and the one after it is read on: the
 *       target of a CREATE TABLE, an INSERT INTO, a MERGE INTO, an UPDATE or a CALL, an UPDATE's SET item, the AS, the
 *       '(' or the query of a WITH's common table expression, and the format of an EXPLAIN USING — {@code CREATE
 *       TABLE; SELECT 1 x y} is the ';' alone, {@code CREATE TABLE; SELECT 2; SELECT 3 x y} names the 'y'. A SET's
 *       name followed by anything but its '=' does the same. Any other fault of those statements is read past, the
 *       explained statement's too: {@code CREATE TABLE t; SELECT 1 x y} names the 'y'.</li>
 *   <li>A TOKEN RUN IN after a finished statement or clause ends the report — a word, a bracket after an alias, a
 *       comma met as the script's first fault, a '(' refused where no signature reads it, and anything after a
 *       routine's quoted body: {@code SELECT 1 x y; SELECT 2 x y z} is the first 'y' alone, {@code DELETE FROM t
 *       ('x'); SELECT 1 x y} the '(' alone and {@code CREATE FUNCTION f() RETURNS INT AS '1' x; SELECT 1 x y} the 'x'
 *       alone — except in the places {@code runInReadPast} lists, which read on: after a DROP's name, a SHOW's kind,
 *       an UNSET's name, BEGIN WORK or TRANSACTION, a table's alias in a query's FROM list, and, for a '(', a
 *       membership or null test, a VALUES row and a plain CREATE DATABASE or SCHEMA's name.</li>
 *   <li>A WORD RUN INTO ANOTHER inside a bracket of a SELECT list — a scalar subquery, a parenthesized expression, a
 *       call's arguments, but not an EXISTS — ends the report, unless it stands in a FROM list or a GROUP BY there:
 *       {@code SELECT ABS((1 x y)); SELECT 1 x y} is the 'x' alone, where {@code SELECT (1 x); SELECT 1 x y} names
 *       the later 'y' too.</li>
 *   <li>A STATEMENT THAT CANNOT OPEN — its first token opens no statement a script may hold: a name, a number, FROM,
 *       ')', or a scripting word such as LET or RETURN. Straight after a statement with a fault it ends the report
 *       unnamed ({@code SELECT 1 +; x; SELECT 1 x y} is the ';' alone); after a statement read without one it is
 *       named at that first token, and the report ends there ({@code SELECT 1 +; SELECT 2; x; SELECT 3 x y} is the
 *       ';' and 'x' at 22) — a line inside such a statement is the report's last.</li>
 *   <li>A statement ending inside a join's ON or USING condition is refused at that keyword, and the report ends
 *       (see {@link JoinConditionEnd}).</li>
 *   <li>A text holding a scripting block is not judged here: its statements have rules of their own.</li>
 * </ul>
 */
final class LaterStatementLines {

    /** The tokens a statement a script may hold opens with: every statement's but a scripting-only one's. */
    private static final IntervalSet OPENERS = openers();

    /** The tokens that can be a name. */
    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private final List<Token> spoken;

    /** Per token index, the token's position in {@link #spoken}, or -1 for a token off the default channel. */
    private final int[] positions;

    private Token replacement;

    /** The separator after which the report ends, once a statement's fault gave the input up, or -1. */
    private int givenUpAfter = -1;

    private LaterStatementLines(final List<Token> spoken) {
        this.spoken = spoken;
        int last = 0;
        for (final Token token : spoken) {
            last = Math.max(last, token.getTokenIndex());
        }
        this.positions = new int[last + 1];
        Arrays.fill(positions, -1);
        for (int p = 0; p < spoken.size(); p++) {
            if (spoken.get(p).getTokenIndex() >= 0) {
                positions[spoken.get(p).getTokenIndex()] = p;
            }
        }
    }

    private static IntervalSet openers() {
        final int[] rules = {
            FrostlakeParser.RULE_ddlStatement, FrostlakeParser.RULE_dmlStatement, FrostlakeParser.RULE_queryStatement,
            FrostlakeParser.RULE_explainStatement, FrostlakeParser.RULE_transactionStatement,
            FrostlakeParser.RULE_listStatement, FrostlakeParser.RULE_getStatement, FrostlakeParser.RULE_putStatement,
            FrostlakeParser.RULE_removeStatement, FrostlakeParser.RULE_sessionSetStatement,
            FrostlakeParser.RULE_sessionUnsetStatement, FrostlakeParser.RULE_securityObjectListing,
            FrostlakeParser.RULE_showStatement, FrostlakeParser.RULE_showClassStatement,
            FrostlakeParser.RULE_describeStatement, FrostlakeParser.RULE_securityStatement,
            FrostlakeParser.RULE_taskStatement, FrostlakeParser.RULE_accessControlStatement,
            FrostlakeParser.RULE_containerServicesStatement, FrostlakeParser.RULE_beginEndBlock,
            FrostlakeParser.RULE_setStatement, FrostlakeParser.RULE_callStatement,
            FrostlakeParser.RULE_executeImmediateStatement, FrostlakeParser.RULE_selectIntoStatement,
        };
        final IntervalSet set = new IntervalSet();
        for (final int rule : rules) {
            set.addAll(FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[rule]));
        }
        return set;
    }

    /**
     * The judge of a text's later lines, or null when these rules do not judge it: when it holds a scripting block — a
     * DECLARE, or a BEGIN that opens no transaction ({@code BEGIN;}, {@code BEGIN WORK}, {@code BEGIN TRANSACTION} and
     * {@code BEGIN NAME} do).
     *
     * @param spoken the text's default-channel tokens, its end of input last
     * @return the judge, or null
     */
    static LaterStatementLines over(final List<Token> spoken) {
        if (spoken == null || spoken.isEmpty()) {
            return null;
        }
        for (int i = 0; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.DECLARE) {
                return null;
            }
            if (type == FrostlakeLexer.BEGIN) {
                final int next = i + 1 < spoken.size() ? spoken.get(i + 1).getType() : Token.EOF;
                if (next != FrostlakeLexer.WORK && next != FrostlakeLexer.TRANSACTION && next != FrostlakeLexer.NAME
                        && next != FrostlakeLexer.SEMI) {
                    return null;
                }
            }
        }
        return new LaterStatementLines(spoken);
    }

    /**
     * The token the last {@link LaterLineVerdict#REPLACED} verdict names: the first token of a statement that cannot
     * open, or the ON or USING of a join condition its statement ended in.
     *
     * @return the token
     */
    Token replacement() {
        return replacement;
    }

    /**
     * What becomes of a line after the last kept one. Lines are judged in the order of the text: a statement's fault
     * that gives the input up ends the report after that statement, whichever of its lines comes last.
     *
     * @param from   the token index of the last kept line's token
     * @param opened whether that line names a token run in after a complete statement: a word that opened a statement
     *               the parse could not finish, an alias's '(', or a token refused as opening a statement run into the
     *               one before it
     * @param at     the token index of the line's token, the end of input's for a line at the end of the input, or -1
     * @return the verdict
     */
    LaterLineVerdict judge(final int from, final boolean opened, final int at) {
        replacement = null;
        final int f = position(from);
        final int a = at < 0 ? -1 : position(at);
        if (f < 0 || a <= f) {
            return LaterLineVerdict.AS_BEFORE;
        }
        final int opener = type(statementStart(f));
        if (opener != FrostlakeLexer.SEMI && opener != Token.EOF && !OPENERS.contains(opener)) {
            return LaterLineVerdict.END;
        }
        final int own = separator(f, 1);
        if (givenUpAfter < 0 && (opened ? !runInReadPast(f) : givesUpInSelectListBracket(f))) {
            givenUpAfter = own < 0 ? spoken.size() - 1 : own;
        }
        if (givenUpAfter >= 0) {
            return a > givenUpAfter ? LaterLineVerdict.END : LaterLineVerdict.AS_BEFORE;
        }
        final LaterLineVerdict kind = kindOf(f);
        final int resume;
        if (kind == LaterLineVerdict.SKIPPED) {
            final int passed = separator(f, 2);
            if (passed < 0 || a <= passed) {
                return LaterLineVerdict.SKIPPED;
            }
            resume = passed;
        } else {
            if (own < 0 || a <= own || kind != LaterLineVerdict.READ) {
                return LaterLineVerdict.AS_BEFORE;
            }
            resume = own;
        }
        return later(resume, a);
    }

    /** The verdict for a line at {@code a} in a statement after the separator at {@code resume}, read on its own. */
    private LaterLineVerdict later(final int resume, final int a) {
        int first = resume + 1;
        while (first < spoken.size() && type(first) == FrostlakeLexer.SEMI) {
            first++;
        }
        if (first >= spoken.size() || type(first) == Token.EOF || first > a) {
            return LaterLineVerdict.AS_BEFORE;
        }
        if (!OPENERS.contains(type(first))) {
            return LaterLineVerdict.END;
        }
        for (int p = first + 1; p <= a; p++) {
            final int type = type(p);
            if (type(p - 1) == FrostlakeLexer.SEMI && type != FrostlakeLexer.SEMI && type != Token.EOF
                    && !OPENERS.contains(type)) {
                if (p == a) {
                    return LaterLineVerdict.READ;
                }
                replacement = spoken.get(p);
                return LaterLineVerdict.REPLACED;
            }
        }
        if (type(a) == FrostlakeLexer.SEMI || type(a) == Token.EOF) {
            final Token keyword = JoinConditionEnd.openKeyword(spoken, statementStart(a), a);
            if (keyword != null) {
                replacement = keyword;
                return LaterLineVerdict.REPLACED;
            }
        }
        return LaterLineVerdict.READ;
    }

    /**
     * How the statements after a fault at {@code f} are read: {@link LaterLineVerdict#SKIPPED} when the next one is
     * passed over, {@link LaterLineVerdict#READ} when they are read on, and {@link LaterLineVerdict#AS_BEFORE} — their
     * lines judged as any other — for a WITH's fault in a column list, at a semicolon inside a body, or where a later
     * common table expression's name belongs.
     */
    private LaterLineVerdict kindOf(final int f) {
        int s = statementStart(f);
        final boolean semicolon = type(f) == FrostlakeLexer.SEMI;
        if (type(s) == FrostlakeLexer.EXPLAIN) {
            if (type(s + 1) == FrostlakeLexer.USING) {
                if (s + 2 == f) {
                    return semicolon ? LaterLineVerdict.SKIPPED : LaterLineVerdict.READ;
                }
                s += 3;
            } else {
                s++;
            }
            if (s >= f) {
                return LaterLineVerdict.READ;
            }
        }
        final int kind = type(s);
        if (kind == FrostlakeLexer.SET) {
            return s + 2 == f && WORDS.contains(type(s + 1)) ? LaterLineVerdict.SKIPPED : LaterLineVerdict.READ;
        }
        if (kind == FrostlakeLexer.WITH) {
            return withFault(s, f, semicolon);
        }
        return semicolon && atHead(kind, s, f) ? LaterLineVerdict.SKIPPED : LaterLineVerdict.READ;
    }

    /** Whether {@code f} stands where the target's name or an UPDATE's SET item belongs in the statement at {@code s}. */
    private boolean atHead(final int kind, final int s, final int f) {
        if (kind == FrostlakeLexer.CALL) {
            return s + 1 == f;
        }
        if (kind == FrostlakeLexer.MERGE) {
            return type(s + 1) == FrostlakeLexer.INTO && s + 2 == f;
        }
        if (kind == FrostlakeLexer.INSERT) {
            final int into = type(s + 1) == FrostlakeLexer.OVERWRITE ? s + 2 : s + 1;
            return type(into) == FrostlakeLexer.INTO && into + 1 == f;
        }
        if (kind == FrostlakeLexer.CREATE) {
            int j = s + 1;
            if (type(j) == FrostlakeLexer.OR && type(j + 1) == FrostlakeLexer.REPLACE) {
                j += 2;
            }
            if (type(j) == FrostlakeLexer.LOCAL || type(j) == FrostlakeLexer.GLOBAL) {
                j++;
            }
            if (type(j) == FrostlakeLexer.TEMP || type(j) == FrostlakeLexer.TEMPORARY
                    || type(j) == FrostlakeLexer.VOLATILE || type(j) == FrostlakeLexer.TRANSIENT) {
                j++;
            }
            if (type(j) != FrostlakeLexer.TABLE) {
                return false;
            }
            j++;
            if (type(j) == FrostlakeLexer.IF && type(j + 1) == FrostlakeLexer.NOT
                    && type(j + 2) == FrostlakeLexer.EXISTS) {
                j += 3;
            }
            return j == f;
        }
        if (kind == FrostlakeLexer.UPDATE) {
            if (s + 1 == f) {
                return true;
            }
            boolean setList = false;
            int depth = 0;
            for (int p = s + 1; p < f; p++) {
                final int type = type(p);
                if (type == FrostlakeLexer.LPAREN) {
                    depth++;
                } else if (type == FrostlakeLexer.RPAREN) {
                    depth--;
                } else if (depth == 0 && type == FrostlakeLexer.SET) {
                    setList = true;
                } else if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE)) {
                    setList = false;
                }
            }
            return setList && depth == 0 && (type(f - 1) == FrostlakeLexer.SET || type(f - 1) == FrostlakeLexer.COMMA);
        }
        return false;
    }

    /** How a fault at {@code f} in the WITH statement opened at {@code with} is read past — see {@link #kindOf}. */
    private LaterLineVerdict withFault(final int with, final int f, final boolean semicolon) {
        final LaterLineVerdict head = semicolon ? LaterLineVerdict.SKIPPED : LaterLineVerdict.READ;
        int j = type(with + 1) == FrostlakeLexer.RECURSIVE ? with + 2 : with + 1;
        while (j < f) {
            j++;
            if (type(j) == FrostlakeLexer.LPAREN && j < f) {
                final int close = closing(j);
                if (close < 0 || close >= f) {
                    return LaterLineVerdict.AS_BEFORE;
                }
                j = close + 1;
            }
            if (j == f) {
                return head;
            }
            if (type(j) != FrostlakeLexer.AS) {
                return LaterLineVerdict.AS_BEFORE;
            }
            if (++j == f) {
                return head;
            }
            if (type(j) != FrostlakeLexer.LPAREN) {
                return LaterLineVerdict.AS_BEFORE;
            }
            if (++j == f) {
                return head;
            }
            final int close = closing(j - 1);
            if (close < 0 || close > f) {
                return semicolon ? LaterLineVerdict.AS_BEFORE : LaterLineVerdict.READ;
            }
            if (close == f) {
                return LaterLineVerdict.READ;
            }
            j = close + 1;
            if (type(j) != FrostlakeLexer.COMMA) {
                return LaterLineVerdict.READ;
            }
            j++;
            if (j == f) {
                return LaterLineVerdict.AS_BEFORE;
            }
        }
        return j == f ? LaterLineVerdict.READ : LaterLineVerdict.AS_BEFORE;
    }

    /**
     * Whether a token run in at {@code f} after a complete statement still lets the later statements be read, where
     * any other run-in gives the input up (all live-verified):
     *
     * <ul>
     *   <li>any token straight after a DROP's object name — {@code DROP TABLE t x; SELECT 1 x y} names the 'y' — but
     *       not after a stage's name, a CASCADE or RESTRICT, or a signature: {@code DROP STAGE s x}, {@code DROP TABLE
     *       t CASCADE x} and {@code DROP FUNCTION f(INT) x} are the 'x' alone;</li>
     *   <li>any token straight after a SHOW's one-word kind, TERSE or not — {@code SHOW TABLES x}, {@code SHOW TERSE
     *       TABLES ('x')} — but not after a kind of two words, a class's name or a clause: {@code SHOW MATERIALIZED
     *       VIEWS x}, {@code SHOW x y} and {@code SHOW TABLES LIKE 'a' x} end the report;</li>
     *   <li>any token but a comma straight after an UNSET's one name: {@code UNSET x y} and {@code UNSET x ('y')} read
     *       on, {@code UNSET x, y} and {@code UNSET (x) y} do not;</li>
     *   <li>any token straight after BEGIN WORK or BEGIN TRANSACTION, where START TRANSACTION and a transaction's NAME
     *       end the report;</li>
     *   <li>a '(' after a membership test, a null test or a quantified LIKE — not after an EXISTS — after a VALUES
     *       row, and after the name of a CREATE DATABASE or a CREATE SCHEMA written without OR REPLACE or
     *       TRANSIENT;</li>
     *   <li>a word after a table's alias in a query's FROM list.</li>
     * </ul>
     */
    private boolean runInReadPast(final int f) {
        final int s = statementStart(f);
        final int kind = type(s);
        if (kind == FrostlakeLexer.DROP) {
            return afterDropName(s, f);
        }
        if (kind == FrostlakeLexer.SHOW) {
            return afterShowKind(s, f);
        }
        if (kind == FrostlakeLexer.UNSET) {
            return f == s + 2 && WORDS.contains(type(s + 1)) && type(f) != FrostlakeLexer.COMMA;
        }
        if (kind == FrostlakeLexer.BEGIN) {
            return f == s + 2 && (type(s + 1) == FrostlakeLexer.WORK || type(s + 1) == FrostlakeLexer.TRANSACTION);
        }
        if (type(f) == FrostlakeLexer.LPAREN && parenReadPast(s, f)) {
            return true;
        }
        final List<int[]> levels = new ArrayList<>();
        levels.add(new int[] {-1, 0});
        for (int p = s; p < f; p++) {
            final int type = type(p);
            if (type == FrostlakeLexer.LPAREN) {
                levels.add(new int[] {-1, 0});
            } else if (type == FrostlakeLexer.RPAREN) {
                if (levels.size() > 1) {
                    levels.remove(levels.size() - 1);
                }
            } else if (isClause(type)) {
                final int[] level = levels.get(levels.size() - 1);
                level[0] = type;
                if (type == FrostlakeLexer.SELECT) {
                    level[1] = 1;
                }
            }
        }
        final int[] level = levels.get(levels.size() - 1);
        return (level[0] == FrostlakeLexer.FROM || level[0] == FrostlakeLexer.JOIN) && level[1] == 1;
    }

    /**
     * Whether {@code f} stands straight after the object name of the DROP opened at {@code s} — a word, a quoted name
     * or an IDENTIFIER() reference, not a CASCADE, a RESTRICT or a signature's ')' — and the DROP is not a stage's.
     */
    private boolean afterDropName(final int s, final int f) {
        if (type(s + 1) == FrostlakeLexer.STAGE) {
            return false;
        }
        final int before = type(f - 1);
        if (before == FrostlakeLexer.CASCADE || before == FrostlakeLexer.RESTRICT) {
            return false;
        }
        if (before != FrostlakeLexer.RPAREN) {
            return true;
        }
        final int open = opening(f - 1);
        return open > s && (type(open - 1) == FrostlakeParser.KW_IDENTIFIER_REF
            || type(open - 1) == FrostlakeParser.KW_IDENTIFIER_OPEN);
    }

    /** Whether {@code f} stands straight after the one keyword naming the kind the SHOW opened at {@code s} lists. */
    private boolean afterShowKind(final int s, final int f) {
        final int kind = type(s + 1) == FrostlakeLexer.TERSE ? s + 2 : s + 1;
        return f == kind + 1 && type(kind) != FrostlakeLexer.IDENTIFIER
            && type(kind) != FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /**
     * Whether a '(' at {@code f}, run in after the complete statement opened at {@code s}, still lets the later
     * statements be read: after IS [NOT] NULL, after the bracket of an IN, ANY, ALL or SOME, after a VALUES row, or
     * after the name of a plain CREATE DATABASE or CREATE SCHEMA.
     */
    private boolean parenReadPast(final int s, final int f) {
        final int before = type(f - 1);
        if (before == FrostlakeLexer.NULL) {
            return type(f - 2) == FrostlakeLexer.IS
                || type(f - 2) == FrostlakeLexer.NOT && type(f - 3) == FrostlakeLexer.IS;
        }
        if (before == FrostlakeLexer.RPAREN) {
            int open = opening(f - 1);
            if (open <= s) {
                return false;
            }
            final int test = type(open - 1);
            if (test == FrostlakeLexer.IN || test == FrostlakeLexer.ANY || test == FrostlakeLexer.ALL
                    || test == FrostlakeLexer.SOME) {
                return true;
            }
            while (type(open - 1) == FrostlakeLexer.COMMA && type(open - 2) == FrostlakeLexer.RPAREN) {
                open = opening(open - 2);
                if (open <= s) {
                    return false;
                }
            }
            return type(open - 1) == FrostlakeLexer.VALUES;
        }
        if (type(s) != FrostlakeLexer.CREATE
                || type(s + 1) != FrostlakeLexer.DATABASE && type(s + 1) != FrostlakeLexer.SCHEMA) {
            return false;
        }
        int name = s + 2;
        if (type(name) == FrostlakeLexer.IF && type(name + 1) == FrostlakeLexer.NOT
                && type(name + 2) == FrostlakeLexer.EXISTS) {
            name += 3;
        }
        if (!WORDS.contains(type(name))) {
            return false;
        }
        while (type(name + 1) == FrostlakeLexer.DOT && WORDS.contains(type(name + 2))) {
            name += 2;
        }
        return f == name + 1;
    }

    /**
     * Whether the statement holding {@code f} runs a word into another at or after it, inside a bracket opened in a
     * SELECT list — other than an EXISTS subquery's — and outside a FROM list or a GROUP BY there.
     */
    private boolean givesUpInSelectListBracket(final int f) {
        final int s = statementStart(f);
        final int own = separator(f, 1);
        final int stop = own < 0 ? spoken.size() - 1 : own;
        final List<int[]> levels = new ArrayList<>();
        levels.add(new int[] {-1, 0});
        for (int p = s; p < stop; p++) {
            final int type = type(p);
            final int[] level = levels.get(levels.size() - 1);
            if (type == FrostlakeLexer.LPAREN) {
                levels.add(new int[] {level[0],
                    level[0] == FrostlakeLexer.SELECT && type(p - 1) != FrostlakeLexer.EXISTS ? 1 : 0});
            } else if (type == FrostlakeLexer.RPAREN) {
                if (levels.size() > 1) {
                    levels.remove(levels.size() - 1);
                }
            } else if (isClause(type)) {
                level[0] = type;
            } else if (p >= f && level[1] == 1 && level[0] != FrostlakeLexer.FROM && level[0] != FrostlakeLexer.JOIN
                    && level[0] != FrostlakeLexer.GROUP && runIn(p)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the word at {@code p} is run into the word before it, which follows an operand or an AS after one. */
    private boolean runIn(final int p) {
        if (!WORDS.contains(type(p)) || !WORDS.contains(type(p - 1))) {
            return false;
        }
        final int before = type(p - 2);
        return endsOperand(before) || before == FrostlakeLexer.AS && endsOperand(type(p - 3));
    }

    private static boolean endsOperand(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING
            || type == FrostlakeLexer.HEX_LITERAL || type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE
            || type == FrostlakeLexer.NULL || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET;
    }

    /** Whether a token opens one of a query's clauses. */
    private static boolean isClause(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM || type == FrostlakeLexer.JOIN
            || type == FrostlakeLexer.ON || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
            || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.OFFSET || type == FrostlakeLexer.FETCH;
    }

    /** The position of the '(' the ')' at {@code close} closes, or -1. */
    private int opening(final int close) {
        int depth = 0;
        for (int p = close; p >= 0; p--) {
            if (type(p) == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type(p) == FrostlakeLexer.LPAREN && --depth == 0) {
                return p;
            }
        }
        return -1;
    }

    /** The position of the ')' closing the '(' at {@code open}, or -1. */
    private int closing(final int open) {
        int depth = 0;
        for (int p = open; p < spoken.size(); p++) {
            if (type(p) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type(p) == FrostlakeLexer.RPAREN && --depth == 0) {
                return p;
            }
        }
        return -1;
    }

    /** The position of the first token of the statement holding position {@code p}, a separator ending its own. */
    private int statementStart(final int p) {
        int s = p;
        while (s > 0 && type(s - 1) != FrostlakeLexer.SEMI) {
            s--;
        }
        return s;
    }

    /** The position of the {@code nth} separator at or after position {@code from}, or -1. */
    private int separator(final int from, final int nth) {
        int seen = 0;
        for (int p = from; p < spoken.size(); p++) {
            if (type(p) == FrostlakeLexer.SEMI && ++seen == nth) {
                return p;
            }
        }
        return -1;
    }

    /** The position in the spoken tokens of the token with the given token index, or -1. */
    private int position(final int tokenIndex) {
        return tokenIndex < 0 || tokenIndex >= positions.length ? -1 : positions[tokenIndex];
    }

    private int type(final int p) {
        return p < 0 || p >= spoken.size() ? Token.EOF : spoken.get(p).getType();
    }
}
