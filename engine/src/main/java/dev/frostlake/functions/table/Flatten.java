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

package dev.frostlake.functions.table;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FLATTEN — expands semi-structured data into one row per element.
 *
 * <p>Returns SEQ, KEY, PATH, INDEX, VALUE and THIS. <b>INPUT must be VARIANT, OBJECT or ARRAY</b>:
 * Snowflake will not read JSON out of a VARCHAR, and answers
 * {@code invalid type [VARCHAR(7)] for parameter 'INPUT'} for {@code FLATTEN(INPUT => '[1,2,3]')} —
 * {@code PARSE_JSON} is the supported spelling. That refusal, the argument names and the boolean-ness
 * of OUTER / RECURSIVE are all settled while the statement is compiled (see
 * {@code TableFunctionArguments}); what is left here is what a value decides.
 *
 * <p>The measured row rules:
 * <ul>
 *   <li>SEQ numbers the INPUT RECORD, not the element: every row of one call carries the same SEQ,
 *       starting at 1.</li>
 *   <li>Only a container expands. A VARIANT holding a scalar — a string, a number, a JSON null —
 *       contributes NO rows, so {@code FLATTEN(PARSE_JSON('"abc"'))} is empty rather than one row, and
 *       a string that merely LOOKS like JSON is not re-read as JSON.</li>
 *   <li>RECURSIVE descends only through what the MODE emitted. Under {@code MODE => 'ARRAY'} an
 *       object's members are neither emitted nor followed, which is why
 *       {@code FLATTEN(PARSE_JSON('{"arr":[1,2,3]}'), MODE => 'ARRAY', RECURSIVE => TRUE)} is
 *       empty.</li>
 *   <li>PATH prefixes the reported path rather than replacing it: {@code PATH => 'a.b'} reports
 *       {@code a.b[0]}. A path that matches nothing, or matches a scalar, yields no rows.</li>
 *   <li>OUTER's stand-in row is all NULL. For an empty container it still reports the container as
 *       THIS and an empty PATH; for a scalar or NULL input both are NULL.</li>
 * </ul>
 */
public class Flatten extends TableFunction {

    private static final ObjectMapper JACKSON = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /**
     * Snowflake's SEQ is a sequence number for the INPUT RECORD, so one call numbers all its rows
     * alike. Live increments it across the rows of a scanned column; a single call always reports 1.
     */
    private static final long INPUT_SEQUENCE = 1L;

    /** The order the positional form fills FLATTEN's parameters in. */
    private static final String[] POSITIONAL_PARAMETERS = {"INPUT", "PATH", "OUTER", "RECURSIVE", "MODE"};

    public Flatten() {
        super("FLATTEN");
    }

    /**
     * The positional form fills the same five parameters in order — {@code FLATTEN(input, path, outer,
     * recursive, mode)} — and each one it is given counts, so a positional PATH selects a sub-element
     * exactly as the named one does.
     */
    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        final Map<String, Object> namedArgs = new HashMap<>();
        for (int i = 0; i < positionalArgs.size() && i < POSITIONAL_PARAMETERS.length; i++) {
            namedArgs.put(POSITIONAL_PARAMETERS[i], positionalArgs.get(i));
        }
        return execute(namedArgs);
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        validateArgs(namedArgs);

        // Get INPUT parameter (required). A present-but-NULL input is NOT an error in Snowflake: NULL is
        // simply not expandable, so the row contributes no output (OUTER => FALSE, the default) or a single
        // all-NULL row (OUTER => TRUE). Only an ABSENT INPUT argument is an error, which validateArgs()
        // above already rejects. Throwing here aborted the WHOLE statement — the table function is expanded
        // for every left row before WHERE can filter, so one NULL in a scanned column killed the query.
        final Object input = namedArgs.get("INPUT");

        // Get optional parameters
        final String path = namedArgs.containsKey("PATH") ? namedArgs.get("PATH").toString() : null;
        final boolean outer = namedArgs.containsKey("OUTER") &&
                       Boolean.parseBoolean(namedArgs.get("OUTER").toString());
        final boolean recursive = namedArgs.containsKey("RECURSIVE") &&
                           Boolean.parseBoolean(namedArgs.get("RECURSIVE").toString());
        final FlattenMode mode = namedArgs.containsKey("MODE")
                ? FlattenMode.fromString(namedArgs.get("MODE").toString()) : FlattenMode.BOTH;

        // Create result columns
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("SEQ", NumericType.INTEGER));
        columns.add(new ResultSetColumn("KEY", StringType.VARCHAR));
        columns.add(new ResultSetColumn("PATH", StringType.VARCHAR));
        columns.add(new ResultSetColumn("INDEX", NumericType.INTEGER));
        columns.add(new ResultSetColumn("VALUE", StringType.VARCHAR));
        columns.add(new ResultSetColumn("THIS", StringType.VARCHAR));

        final List<Row> rows = new ArrayList<>();

        // Parse input as JSON (a semi-structured wrapper contributes its CANONICAL text — toString
        // renders XML-shaped variants as XML text, which is not parseable JSON)
        JsonNode jsonInput = parseInput(input);
        final String inputStr;
        if (input == null) {
            inputStr = null;
        } else if (input instanceof VariantValue) {
            inputStr = ((VariantValue) input).text();
        } else {
            inputStr = input.toString();
        }

        // Apply path filter if specified. The path also PREFIXES every reported path — live answers
        // "a.b[0]" for PATH => 'a.b', not "[0]".
        String pathPrefix = "";
        if (path != null && !path.isEmpty()) {
            jsonInput = navigatePath(jsonInput, path);
            pathPrefix = path;
        }

        // Flatten the JSON structure
        final boolean expandable = jsonInput != null && (jsonInput.isObject() || jsonInput.isArray());
        if (expandable) {
            flattenElement(jsonInput, pathPrefix, inputStr, rows, recursive, mode, outer);
        }

        // If outer is true and no rows were generated, add a single null row. It reports the input as
        // THIS only when the input WAS a container that simply held nothing — a scalar, a NULL, and a
        // path that matched nothing all leave THIS and PATH null too.
        if (outer && rows.isEmpty()) {
            final List<Object> values = new ArrayList<>();
            values.add(INPUT_SEQUENCE);              // SEQ
            values.add(null);                        // KEY
            values.add(expandable ? pathPrefix : null);   // PATH
            values.add(null);                        // INDEX
            values.add(null);                        // VALUE
            values.add(expandable ? inputStr : null);     // THIS
            rows.add(new Row(values));
        }

        return new ResultSet(columns, rows);
    }

    private JsonNode parseInput(final Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof VariantValue) {
            return ((VariantValue) input).node();
        }

        final String inputStr = input.toString();

        // Try to parse as JSON
        try {
            // Array text can carry Snowflake's bare `undefined` element token — see VariantUndefined.
            return VariantUndefined.readTree(JACKSON, inputStr);
        } catch (final Exception e) {
            // If not valid JSON, treat as string
            return new StringNode(inputStr);
        }
    }

    private JsonNode navigatePath(final JsonNode element, final String path) {
        if (path == null || path.isEmpty() || element == null) {
            return element;
        }

        // Simple path navigation (e.g., "field1.field2" or "array[0]")
        final String[] parts = path.split("\\.");
        JsonNode current = element;

        for (final String part : parts) {
            if (current == null || current.isNull()) {
                return null;
            }

            // Handle array index
            if (part.matches(".*\\[\\d+\\]")) {
                final String fieldName = part.substring(0, part.indexOf('['));
                final int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));

                if (!fieldName.isEmpty() && current.isObject()) {
                    current = current.get(fieldName);
                }

                if (current != null && current.isArray()) {
                    if (index >= 0 && index < current.size()) {
                        current = current.get(index);
                    } else {
                        return null;
                    }
                }
            } else if (current.isObject()) {
                current = current.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Walk one container, emitting the rows its MODE calls for.
     *
     * <p>Recursion follows emission: a member the mode did not emit is not descended into either, which
     * is why {@code MODE => 'ARRAY'} over an object yields nothing at all however deep the arrays
     * underneath it are. A scalar is never expanded — only a container reaches here.
     */
    private void flattenElement(final JsonNode element, final String currentPath, final String thisValue,
                                final List<Row> rows, final boolean recursive,
                                final FlattenMode mode, final boolean outer) {
        if (element == null || element.isNull()) {
            return;
        }

        if (element.isObject()) {
            if (element.size() == 0 && outer) {
                // Empty object with outer=true
                addRow(rows, null, currentPath, null, null, thisValue);
            } else {
                final Set<Map.Entry<String, JsonNode>> fields = element.properties();
                for(final Map.Entry<String, JsonNode> entry : fields) {
                    final String key = entry.getKey();
                    final JsonNode value = entry.getValue();
                    final String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;
                    final boolean emitted = mode == FlattenMode.OBJECT || mode == FlattenMode.BOTH;

                    if (emitted) {
                        addRow(rows, key, newPath, null, nodeToValue(value), thisValue);
                    }

                    // Recursive flattening
                    if (recursive && emitted && (value.isObject() || value.isArray())) {
                        flattenElement(value, newPath, thisValue, rows, true, mode, outer);
                    }
                }
            }
        } else if (element.isArray()) {
            // FLATTEN SKIPS a VARIANT `undefined` element entirely — live-verified: over
            // ARRAY_CONSTRUCT(1,NULL,2) it yields 2 rows whose INDEX values are 0 and 2 (the ORIGINAL
            // positions, not renumbered), while over PARSE_JSON('[1,null,2]') it yields 3 rows with
            // TYPEOF(value) INTEGER / NULL_VALUE / INTEGER. An array whose elements are ALL `undefined`
            // behaves like an empty one: 0 rows, or the single OUTER row when outer => TRUE.
            int visible = 0;
            for (final JsonNode candidate : element) {
                if (!VariantUndefined.isUndefined(candidate)) visible++;
            }
            if (visible == 0 && outer) {
                // Empty array with outer=true
                addRow(rows, null, currentPath, null, null, thisValue);
            } else {
                for (int i = 0; i < element.size(); i++) {
                    final JsonNode value = element.get(i);
                    final String newPath = currentPath + "[" + i + "]";
                    if (VariantUndefined.isUndefined(value)) {
                        continue;
                    }
                    final boolean emitted = mode == FlattenMode.ARRAY || mode == FlattenMode.BOTH;

                    if (emitted) {
                        addRow(rows, null, newPath, (long) i, nodeToValue(value), thisValue);
                    }

                    // Recursive flattening
                    if (recursive && emitted && (value.isObject() || value.isArray())) {
                        flattenElement(value, newPath, thisValue, rows, true, mode, outer);
                    }
                }
            }
        }
    }

    private void addRow(final List<Row> rows, final String key, final String path, final Long index,
                       final Object value, final String thisValue) {
        final List<Object> values = new ArrayList<>();
        values.add(INPUT_SEQUENCE);
        values.add(key);
        // An empty path stays EMPTY rather than becoming NULL: the OUTER stand-in row for a container
        // that held nothing is the only row that has one, and live reports '' there.
        values.add(path);
        values.add(index);
        values.add(value);
        values.add(thisValue);
        rows.add(new Row(values));
    }

    /**
     * The VALUE column keeps a scalar node's TYPE — a JSON boolean/number element stays a Boolean/Number
     * (Snowflake's FLATTEN value is a typed VARIANT), so re-aggregating it (OBJECT_AGG, ARRAY_AGG,
     * OBJECT_CONSTRUCT) does not turn {@code true} into the string {@code "true"}. Textual nodes unwrap to
     * their text; objects/arrays stay JSON text for path access.
     */
    private Object nodeToValue(final JsonNode node) {
        if (node == null) {
            return null;
        }
        // An `undefined` ELEMENT never reaches here (the array walk skips it) and would read as SQL NULL.
        if (VariantUndefined.isUndefined(node)) {
            return null;
        }
        // A JSON null MEMBER is flattened as the typed VARIANT NULL_VALUE, not as SQL NULL — live
        // FLATTEN over PARSE_JSON('[1,null,2]') yields TYPEOF(value)
        // INTEGER / NULL_VALUE / INTEGER, and over PARSE_JSON('{"a":1,"b":null}') the b row's
        // TYPEOF(value) is 'NULL_VALUE'.
        if (node.isNull()) {
            return VariantValue.of("null");
        }
        if (node.isTextual()) {
            // Same disambiguation as path extraction: a string element whose content looks like JSON
            // structure keeps its quoted form so it is not mistaken for a real array/object downstream.
            final String text = node.asText();
            final String trimmedText = text.trim();
            if (trimmedText.startsWith("[") || trimmedText.startsWith("{")) {
                return VariantValue.ofNode(node);
            }
            return text;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isLong() || node.isInt()) {
            return node.asLong();
        }
        if (node.isBigInteger() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        return VariantValue.ofNode(node);
    }

    /**
     * What is left once the statement has compiled: the argument NAMES and the missing-INPUT refusal
     * are raised from the parse tree, where they carry a position, so only the MODE value is judged
     * here — live spells it {@code Bad flattening mode 'NOPE' (not 'BOTH', 'ARRAY', or 'OBJECT')}.
     */
    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        if (namedArgs.containsKey("MODE")
                && FlattenMode.fromString(namedArgs.get("MODE").toString()) == null) {
            throw new RuntimeException(SqlCompilationError.of("Bad flattening mode '"
                + namedArgs.get("MODE") + "' (not 'BOTH', 'ARRAY', or 'OBJECT')"));
        }
    }
}
