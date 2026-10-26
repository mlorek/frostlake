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

import dev.frostlake.executor.FileFormatSurfaces;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StageCopyOptionKinds;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SecurityPropertySpec;
import dev.frostlake.metastore.model.SecurityPropertySpecs;
import dev.frostlake.metastore.model.SecurityPropertyType;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.values.BinaryValue;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A session variable is a property's value where a DDL statement gives one (live-verified): the COMMENT of every
 * kind, {@code ALTER TABLE t SET COMMENT = $x}, a table's, a schema's and a database's parameters, a task's options
 * and its SCHEDULE, a file format's options, a stage's format, copy and encryption options and a table's
 * STAGE_FILE_FORMAT and STAGE_COPY_OPTIONS, the properties of a password and a session policy, and every property of a
 * network rule, a secret, a packages and an authentication policy, a list's items among them - the name matched
 * without regard to case. Before the statement runs each such variable is read as the literal of what it holds, so
 * the statement takes it as it takes the literal, once the property has judged the value by its type:
 *
 * <ul>
 *   <li>a COMMENT takes a text, and a NULL or a Boolean leaves no comment; a number is refused, {@code invalid type
 *       of property '$n' for 'COMMENT'};</li>
 *   <li>a task's SCHEDULE takes a text, and a NULL or a Boolean leaves no schedule; a number is no type of it, and
 *       any other value is read as its text, which is no schedule;</li>
 *   <li>a switch takes no variable: a text or a number is no type of it, {@code invalid type of property '$x' for
 *       'CHANGE_TRACKING'}, a Boolean no value of it, {@code invalid value '$b' for property 'CHANGE_TRACKING'},
 *       which a task's ALLOW_OVERLAPPING_EXECUTION words {@code invalid value 'null'}, and a date {@code invalid
 *       value [$d] for parameter 'CHANGE_TRACKING'};</li>
 *   <li>a parameter takes a value of the type the account lists for it and refuses any other by the value, {@code
 *       invalid value [zz] for parameter 'DATA_RETENTION_TIME_IN_DAYS'}, a DECFLOAT among them; a NULL, a date, a
 *       time, a timestamp or a binary value is a type none takes, {@code Unsupported type DATE for parameter
 *       DATA_RETENTION_TIME_IN_DAYS specified with session variable}, and a variable that does not exist is placed
 *       nowhere, {@code error line -1 at position -1};</li>
 *   <li>a file format's option, or a stage's, or one of a table's stage options, takes a text alone and refuses by
 *       the variable's name: a number, or a text where the option wants a number or a switch, is {@code invalid type
 *       [$n] for parameter 'SKIP_HEADER'}, and a Boolean or a NULL {@code invalid value [$b] for parameter
 *       'PURGE'};</li>
 *   <li>a stage's FILE_FORMAT reads a variable as the name of a format: a number or a date as its text, a Boolean
 *       as TRUE or FALSE, and a NULL is {@code invalid parameter '$v'};</li>
 *   <li>a stage's encryption TYPE reads a text or a number as the type's text, and a stage's DIRECTORY takes no
 *       variable for ENABLE, {@code invalid value [$b] for parameter 'ENABLE'};</li>
 *   <li>a password or a session policy takes a variable for its COMMENT and the items of its lists alone: any other
 *       property is {@code invalid type of property '$n' for 'PASSWORD_MIN_LENGTH'}, and a list given to it {@code
 *       Invalid value specified for property 'PASSWORD_MIN_LENGTH'};</li>
 *   <li>a network rule's, a secret's, a packages or an authentication policy's property takes a text: a number is no
 *       type of it, {@code invalid type of property '$n' for 'SECRET_STRING'}, and a NULL or a Boolean a value of
 *       none, {@code invalid value 'null' for property 'SECRET_STRING'}, which one of its choices reads as no choice
 *       instead — no TYPE or MODE of a rule, and an authentication policy's MFA_ENROLLMENT as it was;</li>
 *   <li>a list's item takes a text alone and refuses any other value by the variable's name, {@code invalid value
 *       '$n' for property 'VALUE_LIST'}.</li>
 * </ul>
 *
 * <p>A variable among options no property is known to take - a COPY's, a policy's of another kind than these - is
 * refused where it stands, as the grammar refused it before. A task's body and a scripting block are left to be read
 * when they run.
 */
public final class PropertyVariables {

    private static final String COMMENT = "COMMENT";

    /** The width of the BINARY a session variable holds a binary value as. */
    private static final int BINARY_VARIABLE_WIDTH = 67108864;

    /** A table's and a view's switches. */
    private static final Set<String> SWITCHES = new HashSet<String>(Arrays.asList(
        "CHANGE_TRACKING", "ENABLE_SCHEMA_EVOLUTION", "ROW_TIMESTAMP"));

    private PropertyVariables() {
    }

    /**
     * Read each property's session variable in a statement as the literal of its value.
     *
     * @param statement     the statement
     * @param queryExecutor the executor whose session holds the variables
     * @return what was read, to hand to {@link #unbind} once the statement has run
     */
    public static List<BoundPropertyVariable> bind(final ParseTree statement, final QueryExecutor queryExecutor) {
        if (!(statement instanceof ParserRuleContext)
                || ParseTreeText.getOriginalText((ParserRuleContext) statement).indexOf('$') < 0) {
            return Collections.<BoundPropertyVariable>emptyList();
        }
        final List<TerminalNode> variables = new ArrayList<TerminalNode>();
        collect(statement, variables);
        final List<BoundPropertyVariable> bound = new ArrayList<BoundPropertyVariable>();
        try {
            for (final TerminalNode variable : variables) {
                final VariablePropertyPlace place = placeOf(variable);
                if (place == null) {
                    continue;
                }
                final Token written = variable.getSymbol();
                final Object value = TagValues.sessionVariable(written, queryExecutor);
                final TerminalNodeImpl node = (TerminalNodeImpl) variable;
                final Token literal = literal(written, value, holdsDecfloat(written, queryExecutor),
                    keptPrecision(written, queryExecutor), place);
                bound.add(new BoundPropertyVariable(node, written));
                node.symbol = literal;
            }
        } catch (final RuntimeException refused) {
            unbind(bound);
            throw refused;
        }
        return bound;
    }

    /**
     * Give the variables {@link #bind} read back their written tokens.
     *
     * @param bound what {@link #bind} answered
     */
    public static void unbind(final List<BoundPropertyVariable> bound) {
        for (final BoundPropertyVariable variable : bound) {
            variable.restore();
        }
    }

    /**
     * Whether a variable stands as a parameter's value, where a variable that does not exist is placed nowhere.
     *
     * @param variable the variable
     * @return whether a parameter takes it
     */
    public static boolean readsAsParameter(final TerminalNode variable) {
        try {
            final VariablePropertyPlace place = placeOf(variable);
            return place != null && place.kind() == VariablePropertyKind.PARAMETER;
        } catch (final RuntimeException refused) {
            return false;
        }
    }

    /** Whether SET gave a variable a DECFLOAT, which no parameter takes. */
    private static boolean holdsDecfloat(final Token written, final QueryExecutor queryExecutor) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        return security != null && security.getSessionContext() != null
            && security.getSessionContext().isDecfloatSessionVariable(written.getText().substring(1));
    }

    /** The precision SET gave a time or a timestamp variable, or null for any other. */
    private static Integer keptPrecision(final Token written, final QueryExecutor queryExecutor) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        return security == null || security.getSessionContext() == null ? null
            : security.getSessionContext().getSessionVariablePrecision(written.getText().substring(1));
    }

    private static void collect(final ParseTree node, final List<TerminalNode> into) {
        if (node instanceof TerminalNode) {
            if (((TerminalNode) node).getSymbol().getType() == FrostlakeLexer.SESSION_VAR_REF
                    && node instanceof TerminalNodeImpl) {
                into.add((TerminalNode) node);
            }
            return;
        }
        if (node instanceof FrostlakeParser.TaskBodyContext || node instanceof FrostlakeParser.BeginEndBlockContext) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }

    /**
     * The property a variable is the value of, or null for a variable that is no property's value; one among options
     * no property is known to take it for is refused as a syntax error at the variable.
     */
    private static VariablePropertyPlace placeOf(final TerminalNode variable) {
        final ParseTree parent = variable.getParent();
        if (parent instanceof FrostlakeParser.CopyOptionValueContext) {
            return optionPlace(variable, (FrostlakeParser.CopyOptionValueContext) parent);
        }
        if (parent instanceof FrostlakeParser.TaskPropertyValueContext) {
            return new VariablePropertyPlace(upper(((FrostlakeParser.TaskOptionContext) parent.getParent())
                .identifier()), VariablePropertyKind.PARAMETER);
        }
        if (parent instanceof FrostlakeParser.PolicyScalarValueContext) {
            final FrostlakeParser.PolicyPropertyContext property =
                (FrostlakeParser.PolicyPropertyContext) parent.getParent().getParent();
            if (!COMMENT.equals(upper(property.identifier()))) {
                throw unexpected(variable, (ParserRuleContext) parent);
            }
            return new VariablePropertyPlace(COMMENT, VariablePropertyKind.COMMENT);
        }
        if (parent instanceof FrostlakeParser.ScheduleClauseContext) {
            return new VariablePropertyPlace("SCHEDULE", parent.getParent() instanceof FrostlakeParser.TaskOptionContext
                ? VariablePropertyKind.SCHEDULE : VariablePropertyKind.TEXT);
        }
        if (parent instanceof FrostlakeParser.SecurityPropertyValueContext) {
            return securityPlace(variable, (FrostlakeParser.SecurityPropertyValueContext) parent);
        }
        final String key = keyBefore(variable);
        if (key == null) {
            return null;
        }
        if (parent instanceof FrostlakeParser.CopyFormatOptionContext) {
            final FrostlakeParser.CopyFormatOptionContext option = (FrostlakeParser.CopyFormatOptionContext) parent;
            requireFileFormatStatement(variable, option);
            if (option.LPAREN() != null) {
                // A variable among a list's strings is no value of it, whatever it holds (live-verified).
                throw new RuntimeException(SqlCompilationError.invalidValueForProperty(variable.getText(),
                    upper(option.identifier())));
            }
            return new VariablePropertyPlace(key, VariablePropertyKind.OPTION);
        }
        if (isStage(parent) && "FILE_FORMAT".equals(key)) {
            return new VariablePropertyPlace(key, VariablePropertyKind.NAME);
        }
        if (parent instanceof FrostlakeParser.TaskOptionContext) {
            return new VariablePropertyPlace(key, COMMENT.equals(key) ? VariablePropertyKind.COMMENT
                : "ALLOW_OVERLAPPING_EXECUTION".equals(key) ? VariablePropertyKind.TASK_SWITCH
                    : VariablePropertyKind.PARAMETER);
        }
        if (parent instanceof FrostlakeParser.DatabasePropertyContext) {
            return new VariablePropertyPlace(key, VariablePropertyKind.PARAMETER);
        }
        // Every other place the grammar gives a variable is a COMMENT's.
        return COMMENT.equals(key) ? new VariablePropertyPlace(COMMENT, VariablePropertyKind.COMMENT) : null;
    }

    /** The property of a variable that is an option's value, judged by the option's owner. */
    private static VariablePropertyPlace optionPlace(final TerminalNode variable,
                                                     final FrostlakeParser.CopyOptionValueContext value) {
        final ParseTree owner = value.getParent();
        final String key = keyBefore(value);
        if (key == null) {
            throw unexpected(variable, value);
        }
        if (owner instanceof FrostlakeParser.TableSetPropertyContext
                || owner instanceof FrostlakeParser.TableTailOptionContext
                || owner instanceof FrostlakeParser.ViewSetItemContext
                || owner instanceof FrostlakeParser.ViewPropertyContext
                || owner instanceof FrostlakeParser.DatabasePropertyContext) {
            return new VariablePropertyPlace(key, COMMENT.equals(key) ? VariablePropertyKind.COMMENT
                : SWITCHES.contains(key) ? VariablePropertyKind.SWITCH : VariablePropertyKind.PARAMETER);
        }
        if (owner instanceof FrostlakeParser.StageSettingContext && COMMENT.equals(key)) {
            return new VariablePropertyPlace(COMMENT, VariablePropertyKind.COMMENT);
        }
        if (owner instanceof FrostlakeParser.CopyFormatOptionContext) {
            requireFileFormatStatement(variable, (FrostlakeParser.CopyFormatOptionContext) owner);
            return new VariablePropertyPlace(key, COMMENT.equals(key) ? VariablePropertyKind.COMMENT
                : VariablePropertyKind.OPTION);
        }
        if (owner instanceof FrostlakeParser.ParenOptionContext && (stageOptionList(owner.getParent(), "FILE_FORMAT")
                || tableStageList(owner.getParent()))
                || owner instanceof FrostlakeParser.StageCopyOptionContext
                    && stageOptionList(owner.getParent(), "COPY_OPTIONS")) {
            return new VariablePropertyPlace(key, VariablePropertyKind.OPTION);
        }
        if (owner instanceof FrostlakeParser.ParenOptionContext && "TYPE".equals(key)
                && stageOptionList(owner.getParent(), "ENCRYPTION")) {
            return new VariablePropertyPlace(key, VariablePropertyKind.ENCRYPTION_TYPE);
        }
        if (owner instanceof FrostlakeParser.ParenOptionContext && "ENABLE".equals(key)
                && stageOptionList(owner.getParent(), "DIRECTORY")) {
            return new VariablePropertyPlace(key, VariablePropertyKind.DIRECTORY_SWITCH);
        }
        throw unexpected(variable, value);
    }

    /** Whether an option list is a table's STAGE_FILE_FORMAT or STAGE_COPY_OPTIONS, of CREATE or ALTER TABLE. */
    private static boolean tableStageList(final ParseTree list) {
        final ParseTree owner = list == null ? null : list.getParent();
        if (!(owner instanceof FrostlakeParser.TableTailOptionContext)
                && !(owner instanceof FrostlakeParser.TableSetPropertyContext)) {
            return false;
        }
        final String property = keyBefore(list);
        return "STAGE_FILE_FORMAT".equals(property) || "STAGE_COPY_OPTIONS".equals(property);
    }

    /**
     * The property of a variable among a security object's properties (see the class comment). A network policy takes
     * none where it stands.
     */
    private static VariablePropertyPlace securityPlace(final TerminalNode variable,
                                                       final FrostlakeParser.SecurityPropertyValueContext value) {
        final ParseTree property = value.getParent();
        final ParseTree statement = property == null ? null : property.getParent() instanceof
            FrostlakeParser.SecurityPropertyChangeContext ? property.getParent().getParent() : property.getParent();
        if (!(property instanceof FrostlakeParser.SecurityPropertyContext)
                || !(statement instanceof FrostlakeParser.SecurityObjectStatementContext)) {
            throw unexpected(variable, value);
        }
        final SecurityObjectKind kind =
            SecurityObjectCommandHandler.kindOf((FrostlakeParser.SecurityObjectStatementContext) statement);
        if (kind == SecurityObjectKind.NETWORK_POLICY) {
            throw unexpected(variable, value);
        }
        final String key = upper(((FrostlakeParser.SecurityPropertyContext) property).optionKey());
        final SecurityPropertySpec spec = SecurityPropertySpecs.find(kind, key);
        final boolean list = spec != null && (spec.getType() == SecurityPropertyType.TEXT_LIST
            || spec.getType() == SecurityPropertyType.CHOICE_LIST);
        if (value.LPAREN() != null) {
            if (spec != null && !list) {
                throw new RuntimeException("Invalid value specified for property '" + key + "'");
            }
            return new VariablePropertyPlace(key, VariablePropertyKind.LIST_ITEM);
        }
        if (COMMENT.equals(key)) {
            return new VariablePropertyPlace(COMMENT, VariablePropertyKind.COMMENT);
        }
        if (kind == SecurityObjectKind.PASSWORD_POLICY || kind == SecurityObjectKind.SESSION_POLICY || list) {
            return new VariablePropertyPlace(key, VariablePropertyKind.POLICY_PROPERTY);
        }
        return new VariablePropertyPlace(key, spec != null && spec.getType() == SecurityPropertyType.CHOICE
            ? VariablePropertyKind.CHOICE : VariablePropertyKind.TEXT);
    }

    /** Whether an option list is a stage's FILE_FORMAT or COPY_OPTIONS, of CREATE or ALTER STAGE. */
    private static boolean stageOptionList(final ParseTree list, final String property) {
        final ParseTree owner = list == null ? null : list.getParent();
        return isStage(owner) && property.equals(keyBefore(list));
    }

    /** Whether a place is one of CREATE STAGE's options or one of ALTER STAGE's settings. */
    private static boolean isStage(final ParseTree place) {
        return place instanceof FrostlakeParser.StageOptionContext
            || place instanceof FrostlakeParser.StageSettingContext;
    }

    /** A file format's option takes a variable in CREATE and ALTER FILE FORMAT; a COPY's options take none. */
    private static void requireFileFormatStatement(final TerminalNode variable,
                                                   final FrostlakeParser.CopyFormatOptionContext option) {
        final ParseTree owner = option.getParent();
        if (!(owner instanceof FrostlakeParser.CreateStatementContext)
                && !(owner instanceof FrostlakeParser.FileFormatActionContext)) {
            throw unexpected(variable, option);
        }
    }

    /** The name a value is given to: the word before the '=' that stands before it, upper-cased, or null. */
    private static String keyBefore(final ParseTree value) {
        final ParseTree owner = value.getParent();
        int at = -1;
        for (int i = 0; i < owner.getChildCount(); i++) {
            if (owner.getChild(i) == value) {
                at = i;
            }
        }
        int key = at - 1;
        if (key >= 0 && owner.getChild(key) instanceof TerminalNode
                && ((TerminalNode) owner.getChild(key)).getSymbol().getType() == FrostlakeLexer.EQ) {
            key--;
        }
        if (key < 0) {
            return null;
        }
        final ParseTree word = owner.getChild(key);
        return word instanceof TerminalNode ? ((TerminalNode) word).getText().toUpperCase(Locale.ROOT)
            : upper((ParserRuleContext) word);
    }

    private static String upper(final ParserRuleContext name) {
        return ParseTreeText.getOriginalText(name).toUpperCase(Locale.ROOT);
    }

    private static SqlSyntaxException unexpected(final TerminalNode variable, final ParserRuleContext place) {
        final Token at = variable.getSymbol();
        final String line = "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
            + " unexpected '" + at.getText() + "'.";
        return new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line),
            ParseTreeText.getOriginalText(place));
    }

    /** The literal a variable is read as for its property, or the property's refusal of the value. */
    private static Token literal(final Token written, final Object value, final boolean decfloat,
                                 final Integer precision, final VariablePropertyPlace place) {
        final String name = place.name();
        final String spelled = written.getText();
        final boolean text = value instanceof String;
        final boolean number = isWhole(value) || value instanceof BigDecimal || value instanceof Double;
        final boolean nothing = value == null || value instanceof Boolean;
        switch (place.kind()) {
            case COMMENT:
                if (text) {
                    return text(written, (String) value);
                }
                if (nothing) {
                    return token(written, FrostlakeLexer.NULL, "NULL");
                }
                throw invalidType(spelled, name);
            case TEXT:
                if (text) {
                    return text(written, (String) value);
                }
                throw nothing ? new RuntimeException(SqlCompilationError.invalidValueForProperty("null", name))
                    : invalidType(spelled, name);
            case SCHEDULE:
                if (text) {
                    return text(written, (String) value);
                }
                if (nothing) {
                    return token(written, FrostlakeLexer.NULL, "NULL");
                }
                if (number) {
                    throw invalidType(spelled, name);
                }
                return text(written, plain(value));
            case SWITCH:
                if (!text && !number && !nothing) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(spelled, name));
                }
                throw nothing ? new RuntimeException(SqlCompilationError.invalidValueForProperty(spelled, name))
                    : invalidType(spelled, name);
            case TASK_SWITCH:
                throw nothing ? new RuntimeException(SqlCompilationError.invalidValueForProperty("null", name))
                    : invalidType(spelled, name);
            case PARAMETER:
                if (decfloat) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(plain(value), name));
                }
                return parameter(written, value, precision, name);
            case ENCRYPTION_TYPE:
                if (nothing) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(spelled, name));
                }
                return text(written, text ? (String) value : plain(value));
            case DIRECTORY_SWITCH:
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(spelled, name));
            case POLICY_PROPERTY:
                throw invalidType(spelled, name);
            case CHOICE:
                if (text) {
                    return text(written, (String) value);
                }
                if (nothing) {
                    return token(written, FrostlakeLexer.NULL, "NULL");
                }
                throw invalidType(spelled, name);
            case LIST_ITEM:
                if (text) {
                    return text(written, (String) value);
                }
                throw new RuntimeException(SqlCompilationError.invalidValueForProperty(spelled, name));
            case NAME:
                if (value == null) {
                    throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + spelled + "'"));
                }
                if (value instanceof Boolean) {
                    return text(written, String.valueOf(value).toUpperCase(Locale.ROOT));
                }
                if (text || number || value instanceof LocalDate) {
                    return text(written, text ? (String) value : plain(value));
                }
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(spelled, name));
            case OPTION:
                if (nothing) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(spelled, name));
                }
                if (text && takesText(name)) {
                    return text(written, (String) value);
                }
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid type [" + spelled + "] for parameter '" + name + "'"));
            default:
                if (text) {
                    return text(written, (String) value);
                }
                if (number) {
                    return text(written, plain(value));
                }
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(spelled, name));
        }
    }

    /**
     * A parameter's value: a number, a text or a switch value where the account lists the parameter of that type,
     * and a parameter it does not list, which its statement refuses by name, read as what the variable holds.
     */
    private static Token parameter(final Token written, final Object value, final Integer precision,
                                   final String name) {
        final String unsupported = value == null ? "NULL" : unsupportedType(value, precision);
        if (unsupported != null) {
            throw new RuntimeException(SqlCompilationError.of("Unsupported type " + unsupported + " for parameter "
                + name + " specified with session variable"));
        }
        final String type = CreatedObjectProperties.accountParameterType(name);
        if (isWhole(value) && (type == null || "NUMBER".equals(type) || "FLOAT".equals(type))) {
            return token(written, FrostlakeLexer.INTEGER_LITERAL, plain(value));
        }
        if ((value instanceof BigDecimal || value instanceof Double)
                && (type == null || "NUMBER".equals(type) || "FLOAT".equals(type))) {
            return token(written, FrostlakeLexer.FLOAT_LITERAL, plain(value));
        }
        if (value instanceof String && (type == null || "STRING".equals(type))) {
            return text(written, (String) value);
        }
        if (value instanceof Boolean && (type == null || "BOOLEAN".equals(type))) {
            return ((Boolean) value).booleanValue() ? token(written, FrostlakeLexer.TRUE, "TRUE")
                : token(written, FrostlakeLexer.FALSE, "FALSE");
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value instanceof Boolean
            ? String.valueOf(value).toUpperCase(Locale.ROOT) : plain(value), name));
    }

    /**
     * The type a variable's value is named by where no parameter takes it: a date, a time, a timestamp of each kind,
     * at the precision its SET kept — {@code SET t = '10:00:00'::TIME(3)} is named TIME(3) and one SET from a TIME
     * column TIME(9) — and a binary value; null for a value of another type, which the parameter judges.
     */
    private static String unsupportedType(final Object value, final Integer precision) {
        final String digits = "(" + (precision == null ? 9 : precision.intValue()) + ")";
        if (value instanceof LocalDate) {
            return "DATE";
        }
        if (value instanceof LocalTime) {
            return "TIME" + digits;
        }
        if (value instanceof LocalDateTime) {
            return "TIMESTAMP_NTZ" + digits;
        }
        if (value instanceof OffsetDateTime) {
            return "TIMESTAMP_LTZ" + digits;
        }
        if (value instanceof ZonedDateTime) {
            return "TIMESTAMP_TZ" + digits;
        }
        return value instanceof BinaryValue ? "BINARY(" + BINARY_VARIABLE_WIDTH + ")" : null;
    }

    /** Whether an option of a file format or of a stage takes a text: a number's or a switch's option takes none. */
    private static boolean takesText(final String name) {
        final String declared = FileFormatSurfaces.optionType(name);
        final String type = declared != null ? declared : StageCopyOptionKinds.typeOf(name);
        return type == null || "String".equals(type);
    }

    private static RuntimeException invalidType(final String spelled, final String name) {
        return new RuntimeException(SqlCompilationError.of("invalid type of property '" + spelled + "' for '"
            + name + "'"));
    }

    private static boolean isWhole(final Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof BigInteger
            || value instanceof BigDecimal && ((BigDecimal) value).scale() <= 0;
    }

    private static String plain(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }

    private static Token text(final Token written, final String value) {
        return token(written, FrostlakeLexer.STRING_LITERAL,
            "'" + value.replace("\\", "\\\\").replace("'", "''") + "'");
    }

    private static Token token(final Token written, final int type, final String text) {
        return new BoundVariableToken(written, type, text);
    }
}
