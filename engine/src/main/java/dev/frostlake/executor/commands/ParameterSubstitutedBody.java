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

import dev.frostlake.executor.SqlIdentifierSubstitution;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A SQL UDF query body with each reference to the routine's own parameters replaced by a NULL of the parameter's
 * declared type: the body as a call runs it, where the argument takes the parameter's place in the text. Compiled
 * so at CREATE, the body is typed with its parameters in scope, and a parameter wins over a same-named column —
 * {@code (n DATE) RETURNS INT AS 'SELECT n FROM t'} is refused as returning a DATE over an INT column n, while
 * {@code t.n} still reads the column (live-verified).
 */
final class ParameterSubstitutedBody {

    /** Where a positioned refusal points. */
    private static final Pattern ERROR_POSITION = Pattern.compile("error line (\\d+) at position (\\d+)");

    /** What opens the operand a conversion refusal quotes. */
    private static final String CONVERTED_OPERAND = "Can not convert parameter '";

    /** What closes the operand a conversion refusal quotes. */
    private static final String OPERAND_TYPE = "' of type [";

    /** How the account names a routine's parameter inside a refusal's operand. */
    private static final String PARAMETER_IN_PLAN = "CORRELATION(null)";

    private final String written;
    private final String text;
    private final List<int[]> replaced;
    private final Map<String, String> standIns;

    private ParameterSubstitutedBody(final String written, final String text, final List<int[]> replaced,
                                     final Map<String, String> standIns) {
        this.written = written;
        this.text = text;
        this.replaced = replaced;
        this.standIns = standIns;
    }

    /**
     * The body with each parameter reference replaced by a NULL of the parameter's declared type, spelled as the
     * account types a parameter: a bare VARCHAR as the widest text and a bare BINARY as the widest binary.
     *
     * @param body       the body as written
     * @param parameters the routine's parameters
     * @return the substituted body
     */
    static ParameterSubstitutedBody typed(final String body, final List<Parameter> parameters) {
        final Map<String, String> placeholders = new HashMap<>();
        if (parameters != null) {
            for (final Parameter parameter : parameters) {
                if (parameter.getDataType() != null) {
                    placeholders.put(parameter.getName().toUpperCase(Locale.ROOT),
                        "CAST(NULL AS " + typeText(parameter.getDataType()) + ")");
                }
            }
        }
        return of(body, placeholders);
    }

    /**
     * The body with each reference to a parameter named in {@code names} replaced by a NULL of a type any use
     * reads, for a compile that judges the body's names alone.
     *
     * @param body  the body as written
     * @param names the parameters' canonical names
     * @return the substituted body
     */
    static ParameterSubstitutedBody untyped(final String body, final Set<String> names) {
        final Map<String, String> placeholders = new HashMap<>();
        for (final String name : names) {
            placeholders.put(name.toUpperCase(Locale.ROOT), "CAST(NULL AS VARIANT)");
        }
        return of(body, placeholders);
    }

    private static ParameterSubstitutedBody of(final String body, final Map<String, String> placeholders) {
        final List<int[]> replaced = new ArrayList<>();
        final String text = placeholders.isEmpty() ? body
            : SqlIdentifierSubstitution.substituteAll(body, placeholders, replaced);
        // Each stand-in used, with the one parameter it stands for, or an empty name when it stands for several.
        final Map<String, String> standIns = new LinkedHashMap<>();
        for (final int[] replacement : replaced) {
            final String name = body.substring(replacement[0], replacement[1]).toUpperCase(Locale.ROOT);
            final String standIn = placeholders.get(name);
            final String known = standIns.get(standIn);
            standIns.put(standIn, known == null || known.equals(name) ? name : "");
        }
        return new ParameterSubstitutedBody(body, text, replaced, standIns);
    }

    /** A parameter's declared type as a cast to it spells it. */
    private static String typeText(final DataType type) {
        if (type instanceof BinaryType && ((BinaryType) type).getMaxLength() == BinaryType.BINARY.getMaxLength()) {
            return "BINARY(67108864)";
        }
        return SqlTypeNames.routineType(type);
    }

    /**
     * The substituted body.
     *
     * @return the body with its parameter references replaced
     */
    String text() {
        return text;
    }

    /**
     * Whether any parameter reference was replaced.
     *
     * @return whether the text differs from the body as written
     */
    boolean substitutedAny() {
        return !replaced.isEmpty();
    }

    /**
     * {@code message} with each parameter the operand of a conversion refusal reads named as the account names a
     * routine's parameter there, {@code CORRELATION(null)}, where the compile of {@link #text()} quoted the
     * parameter's stand-in: {@code WHERE d = x} over an INT x is refused naming 'CORRELATION(null)', and
     * {@code WHERE d = x + 1} naming 'CORRELATION(null) + 1' — the parameter reads bare, as a column does, where the
     * plan groups a typed NULL — and a subquery selecting the parameter alone names its column after it,
     * {@code (SELECT CORRELATION(null) AS "X" …)} (live-verified). Any other message is returned unchanged.
     *
     * @param message  a refusal from compiling {@link #text()}
     * @param registry the functions the plan's spelling of a stand-in is printed with
     * @param catalog  the catalog the plan's spelling of a stand-in is printed with
     * @return the refusal as the account words it
     */
    String inAccountWords(final String message, final FunctionRegistry registry, final Catalog catalog) {
        final int opens = message.indexOf(CONVERTED_OPERAND);
        if (opens < 0 || standIns.isEmpty()) {
            return message;
        }
        final int from = opens + CONVERTED_OPERAND.length();
        final int to = message.indexOf(OPERAND_TYPE, from);
        if (to < 0) {
            return message;
        }
        final ExpressionEvaluatorVisitor printer = new ExpressionEvaluatorVisitor(
            new Table("$ROUTINE_PARAMETERS", new ArrayList<TableColumn>(), true), null, registry, catalog);
        String operand = message.substring(from, to);
        for (final Map.Entry<String, String> standIn : standIns.entrySet()) {
            final String printed;
            try {
                printed = printer.strictPlanText(AntlrExpressionParser.parse(standIn.getKey()));
            } catch (final RuntimeException unprintable) {
                continue;
            }
            operand = namedAsParameter(operand, printed);
            if (!standIn.getValue().isEmpty()) {
                // An item that is the parameter alone is named by its text, which is the parameter's name.
                operand = operand.replace("AS \"" + standIn.getKey() + "\"", "AS \"" + standIn.getValue() + "\"");
            }
        }
        return message.substring(0, from) + operand + message.substring(to);
    }

    /**
     * {@code operand} with every {@code printed} stand-in named {@link #PARAMETER_IN_PLAN}, the parentheses the plan
     * groups a typed NULL operand in dropped with it: {@code (SYSTEM$NULL_TO_FIXED(null)) * 2} is
     * {@code CORRELATION(null) * 2}, while a call's own parentheses stay, {@code ABS(CORRELATION(null))}.
     */
    private static String namedAsParameter(final String operand, final String printed) {
        final StringBuilder named = new StringBuilder();
        int cursor = 0;
        int found = operand.indexOf(printed);
        while (found >= 0) {
            final int end = found + printed.length();
            final boolean grouped = found > 0 && operand.charAt(found - 1) == '('
                && (found == 1 || !isNameCharacter(operand.charAt(found - 2)))
                && end < operand.length() && operand.charAt(end) == ')';
            named.append(operand, cursor, grouped ? found - 1 : found).append(PARAMETER_IN_PLAN);
            cursor = grouped ? end + 1 : end;
            found = operand.indexOf(printed, cursor);
        }
        return named.append(operand.substring(cursor)).toString();
    }

    /** Whether a character can end a function's name, so a parenthesis after it opens the call's arguments. */
    private static boolean isNameCharacter(final char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * {@code message} with the line and position it names moved from the substituted text back to the body as
     * written, or the message unchanged when it names none.
     *
     * @param message a refusal from compiling {@link #text()}
     * @return the refusal in the written body's positions
     */
    String inWrittenPositions(final String message) {
        final Matcher at = ERROR_POSITION.matcher(message);
        if (!at.find()) {
            return message;
        }
        final int offset = offsetOf(text, Integer.parseInt(at.group(1)), Integer.parseInt(at.group(2)));
        int shift = 0;
        int original = -1;
        for (final int[] replacement : replaced) {
            final int start = replacement[0] + shift;
            if (offset < start) {
                break;
            }
            if (offset < start + replacement[2]) {
                original = replacement[0];
                break;
            }
            shift += replacement[2] - (replacement[1] - replacement[0]);
        }
        if (original < 0) {
            original = offset - shift;
        }
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < original && i < written.length(); i++) {
            if (written.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return message.substring(0, at.start()) + "error line " + line + " at position " + (original - lineStart)
            + message.substring(at.end());
    }

    /** The character offset of a line and position in {@code text}. */
    private static int offsetOf(final String text, final int line, final int position) {
        int current = 1;
        int start = 0;
        for (int i = 0; i < text.length() && current < line; i++) {
            if (text.charAt(i) == '\n') {
                current++;
                start = i + 1;
            }
        }
        return start + position;
    }
}
