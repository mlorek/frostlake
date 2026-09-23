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
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The name a statement goes by in the refusal a session with no current database gets, and which
 * statements that session refuses before anything is looked up.
 *
 * <pre>
 *   Cannot perform CREATE TABLE. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.
 * </pre>
 *
 * <p>The names are live's own, and most of them are the statement's verb:
 * <ul>
 *   <li>a CREATE is named with the object's words, leaving out SECURE, TRANSIENT, LOCAL and GLOBAL:
 *       CREATE TABLE, CREATE MATERIALIZED VIEW, CREATE FILE FORMAT. HYBRID stays, as in CREATE HYBRID
 *       TABLE. A temporary object glues TEMP to its first word: CREATE TEMPTABLE, CREATE TEMPFILE FORMAT.
 *       An UNDROP is named as the CREATE of its object;</li>
 *   <li>an ALTER that renames its object, or swaps it, is a RENAME;</li>
 *   <li>any other ALTER, and COMMENT ON, is an ALTER, except for the actions live manages under names of
 *       their own: ALTER SET TAG, ALTER UNSET TAG, ALTER SET MASKING POLICY, ALTER MANAGE ROW ACCESS
 *       POLICY and the other policies, CREATE SEARCH INDEX for ADD SEARCH OPTIMIZATION, a task's SUSPEND
 *       (OPERATE) and RESUME (RESOLVE), and most of what a dynamic table can be told (ALTER DYNAMIC TABLE
 *       OPERATE PROPERTY);</li>
 *   <li>DROP, TRUNCATE, INSERT and DESCRIBE name themselves, while UPDATE, DELETE and MERGE are named
 *       SELECT, after the way they read their tables.</li>
 * </ul>
 * Every name here is live-verified.
 */
final class StatementKindText {

    /** The modifiers that make an object a TEMP one. */
    private static final Set<Integer> TEMPORARY = new HashSet<Integer>();
    /** The CREATE modifiers the name leaves out. */
    private static final Set<Integer> UNNAMED = new HashSet<Integer>();
    /** The words that mark an action live manages under a name of its own, beside that name. */
    private static final int[] MANAGED_WORDS = {
        FrostlakeParser.ACCESS, FrostlakeParser.PROJECTION, FrostlakeParser.AGGREGATION,
        FrostlakeParser.JOIN, FrostlakeParser.CONTACT, FrostlakeParser.METRIC,
    };
    private static final String[] MANAGED_KINDS = {
        "ALTER MANAGE ROW ACCESS POLICY", "ALTER MANAGE PROJECTION POLICY", "ALTER MANAGE AGGREGATION POLICY",
        "ALTER MANAGE JOIN POLICY", "ALTER MANAGE CONTACT", "ALTER MANAGE DATA METRIC FUNCTION SELECT CHECK",
    };

    static {
        TEMPORARY.add(FrostlakeParser.TEMPORARY);
        TEMPORARY.add(FrostlakeParser.TEMP);
        TEMPORARY.add(FrostlakeParser.VOLATILE);
        UNNAMED.add(FrostlakeParser.TRANSIENT);
        UNNAMED.add(FrostlakeParser.LOCAL);
        UNNAMED.add(FrostlakeParser.GLOBAL);
        UNNAMED.add(FrostlakeParser.SECURE);
    }

    private StatementKindText() {
    }

    /** The name live gives this statement. */
    static String of(final FrostlakeParser.StatementContext statement) {
        if (statement.queryStatement() != null) {
            return "SELECT";
        }
        if (statement.showStatement() != null && statement.showStatement().GRANTS() != null) {
            return "SHOW GRANTS";
        }
        final FrostlakeParser.AccessControlStatementContext access = statement.accessControlStatement();
        if (access != null) {
            if (access.GRANTS() != null) {
                return "SHOW GRANTS";
            }
            final String verb = upper(statement.getStart().getText());
            if (access.MANAGED() != null) {
                return verb + " MANAGED ACCOUNT" + (access.ACCOUNTS() != null ? "S" : "");
            }
            if (access.ACCOUNT() != null) {
                return verb + " ACCOUNT";
            }
            return verb + " DATABASE ROLE" + (access.ROLES() != null ? "S" : "");
        }
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl == null) {
            final String verb = upper(statement.getStart().getText());
            if ("DESC".equals(verb)) {
                return "DESCRIBE";
            }
            return "UPDATE".equals(verb) || "DELETE".equals(verb) || "MERGE".equals(verb) ? "SELECT" : verb;
        }
        if (ddl.createStatement() != null) {
            return createKind(ddl.createStatement());
        }
        if (ddl.undropStatement() != null) {
            return "CREATE " + upper(ddl.undropStatement().getChild(1).getText());
        }
        if (ddl.alterStatement() != null) {
            return alterKind(ddl.alterStatement());
        }
        if (ddl.commentStatement() != null) {
            return "ALTER";
        }
        return upper(ddl.getStart().getText());
    }

    /**
     * The name a DDL statement's target is written under when that name does not place it: fewer than
     * three parts, two for a schema itself and four for a column. Null when the target is placed, when it
     * is an account object (a database, a warehouse, a user, a role, all written as a plain identifier),
     * when it comes from IDENTIFIER(), and for anything but DDL. USE is never refused this way.
     */
    static FrostlakeParser.QualifiedNameContext underQualifiedTarget(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl == null || ddl.useStatement() != null) {
            return null;
        }
        final ParseTree rule = ddl.getChild(0);
        int required = 3;
        for (int i = 0; i < rule.getChildCount(); i++) {
            final ParseTree child = rule.getChild(i);
            if (child instanceof TerminalNode) {
                final int type = ((TerminalNode) child).getSymbol().getType();
                if (type == FrostlakeParser.SCHEMA) {
                    required = 2;
                } else if (type == FrostlakeParser.COLUMN) {
                    required = 4;
                } else if (type == FrostlakeParser.DATABASE || type == FrostlakeParser.WAREHOUSE
                        || type == FrostlakeParser.ROLE || type == FrostlakeParser.USER
                        || type == FrostlakeParser.POOL) {
                    // An ACCOUNT-scoped name is whole on its own, so it never wants a current
                    // database. These kinds used to reach that answer by being spelled `identifier`
                    // and leaving below; they read an objectName now, so they say it here instead.
                    required = 1;
                }
                continue;
            }
            final FrostlakeParser.QualifiedNameContext name;
            if (child instanceof FrostlakeParser.IdentifierContext) {
                return null;
            } else if (child instanceof FrostlakeParser.ObjectNameContext) {
                name = ((FrostlakeParser.ObjectNameContext) child).qualifiedName();
            } else if (child instanceof FrostlakeParser.QualifiedNameContext) {
                name = (FrostlakeParser.QualifiedNameContext) child;
            } else {
                continue;
            }
            return name != null && ParseTreeText.qualifiedNameParts(name).length < required ? name : null;
        }
        return null;
    }

    /** Whether this is ALTER TABLE … RENAME COLUMN or RENAME CONSTRAINT, which look their table up. */
    static boolean renamesColumnOrConstraint(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl == null || ddl.alterStatement() == null || ddl.alterStatement().tableAction() == null) {
            return false;
        }
        final FrostlakeParser.TableActionContext action = ddl.alterStatement().tableAction();
        return action.RENAME() != null && (action.COLUMN() != null || action.CONSTRAINT() != null);
    }

    /** CREATE and the object's words, a temporary object's first word glued to TEMP. */
    private static String createKind(final FrostlakeParser.CreateStatementContext create) {
        final List<String> words = new ArrayList<String>();
        boolean temporary = false;
        for (int i = 1; i < create.getChildCount(); i++) {
            final ParseTree child = create.getChild(i);
            if (!(child instanceof TerminalNode)) {
                if (words.isEmpty()) {
                    continue;   // OR REPLACE comes before the object's words
                }
                break;          // IF NOT EXISTS, or the name, comes after them
            }
            final int type = ((TerminalNode) child).getSymbol().getType();
            if (TEMPORARY.contains(type)) {
                temporary = true;
            } else if (!UNNAMED.contains(type)) {
                words.add(upper(child.getText()));
            }
        }
        return "CREATE " + (temporary ? "TEMP" : "") + String.join(" ", words);
    }

    private static String alterKind(final FrostlakeParser.AlterStatementContext alter) {
        final ParserRuleContext action = actionOf(alter);
        if (action == null) {
            return "ALTER";
        }
        final int first = action.getStart().getType();
        if (first == FrostlakeParser.SWAP
                || first == FrostlakeParser.RENAME && typeOfChild(action, 1) == FrostlakeParser.TO) {
            return "RENAME";
        }
        final int object = typeOfChild(alter, 1);
        if (object == FrostlakeParser.DYNAMIC) {
            // A dynamic table's COMMENT and retention, set or unset, are plain ALTERs; the rest operate
            // the table (live-verified).
            final String property = action.getChildCount() > 1 ? firstPropertyName(action.getChild(1)) : "";
            return (first == FrostlakeParser.SET || first == FrostlakeParser.UNSET)
                    && ("COMMENT".equals(property) || "DATA_RETENTION_TIME_IN_DAYS".equals(property))
                ? "ALTER" : "ALTER DYNAMIC TABLE OPERATE PROPERTY";
        }
        if (object == FrostlakeParser.TASK && first == FrostlakeParser.SUSPEND) {
            return "OPERATE";
        }
        if (object == FrostlakeParser.TASK && first == FrostlakeParser.RESUME) {
            return "RESOLVE";
        }
        return managedKind(action);
    }

    /** The action an ALTER applies: the rule after the object's name, or null when there is none. */
    private static ParserRuleContext actionOf(final ParserRuleContext alter) {
        for (int i = alter.getChildCount() - 1; i > 0; i--) {
            final ParseTree child = alter.getChild(i);
            if (child instanceof ParserRuleContext) {
                return child instanceof FrostlakeParser.QualifiedNameContext
                    || child instanceof FrostlakeParser.IdentifierContext ? null : (ParserRuleContext) child;
            }
        }
        return null;
    }

    /**
     * ALTER, or the name of its own an action is managed under. Only the action's own words are read, never
     * the names inside it, so a column called CONTACT is an ALTER like any other.
     */
    private static String managedKind(final ParserRuleContext action) {
        final Set<Integer> words = new HashSet<Integer>();
        for (int i = 0; i < action.getChildCount(); i++) {
            final ParseTree child = action.getChild(i);
            if (child instanceof TerminalNode) {
                words.add(((TerminalNode) child).getSymbol().getType());
            } else if (child instanceof FrostlakeParser.TagSetContext) {
                return "ALTER SET TAG";
            } else if (child instanceof FrostlakeParser.TagUnsetContext) {
                return "ALTER UNSET TAG";
            } else if (child instanceof FrostlakeParser.ColumnTagActionContext) {
                return ((FrostlakeParser.ColumnTagActionContext) child).tagSet() != null
                    ? "ALTER SET TAG" : "ALTER UNSET TAG";
            } else if (child instanceof FrostlakeParser.AggregationPolicyClauseContext) {
                words.add(FrostlakeParser.AGGREGATION);
            } else if (child instanceof FrostlakeParser.JoinPolicyClauseContext) {
                words.add(FrostlakeParser.JOIN);
            }
        }
        if (words.contains(FrostlakeParser.MASKING)) {
            return words.contains(FrostlakeParser.UNSET) ? "ALTER UNSET MASKING POLICY" : "ALTER SET MASKING POLICY";
        }
        for (int i = 0; i < MANAGED_WORDS.length; i++) {
            if (words.contains(MANAGED_WORDS[i])) {
                return MANAGED_KINDS[i];
            }
        }
        return words.contains(FrostlakeParser.SEARCH) && action.getStart().getType() == FrostlakeParser.ADD
            ? "CREATE SEARCH INDEX" : "ALTER";
    }

    /** The property an ALTER DYNAMIC TABLE SET or UNSET names first, upper-case. */
    private static String firstPropertyName(final ParseTree named) {
        if (named instanceof FrostlakeParser.DynamicTableSettingContext) {
            return upper(((FrostlakeParser.DynamicTableSettingContext) named).dynamicTableProperty().getText());
        }
        return named instanceof FrostlakeParser.DynamicTablePropertyContext ? upper(named.getText()) : "";
    }

    private static int typeOfChild(final ParserRuleContext context, final int index) {
        return index < context.getChildCount() && context.getChild(index) instanceof TerminalNode
            ? ((TerminalNode) context.getChild(index)).getSymbol().getType() : -1;
    }

    private static String upper(final String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}
