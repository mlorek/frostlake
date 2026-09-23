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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.DataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A function whose first argument names a NAMED stage: GET_PRESIGNED_URL, BUILD_SCOPED_FILE_URL,
 * BUILD_STAGE_FILE_URL, GET_ABSOLUTE_PATH, GET_RELATIVE_PATH and GET_STAGE_LOCATION. The stage is written bare
 * ({@code @st}) or as a string ({@code '@st'}, {@code '@db.schema.st'}, {@code '@"my stage"'}), and each function
 * refuses the same stages the same way, in this order (live-verified, the function's name in each sentence):
 *
 * <pre>
 *   NULL, ''             SQL compilation error: Argument 1 to function 'F' cannot be null or empty.
 *   'st', '  @st'        … Argument 1 to function 'F' does not start with '@'. Please specify stage name as '@&lt;stage_name&gt;'.
 *   '@'                  SQL compilation error:\nmissing stage name in URL: @
 *   '@st/d', '@~/x'      … Argument 1 to function 'F' should only contain the stage name and not a path.
 *   '@~', '@%t'          … Argument 1 to function 'F' provides a user or table stage. These stage kinds are not supported by this function.
 *   '@st.', '@.st'       SQL compilation error:\nsyntax error line 1 at position 3 unexpected '&lt;EOF&gt;'.
 *   '@nosuch', '@"st"'   … Stage '@nosuch' provided to the function 'F' does not exist or is not authorized.
 * </pre>
 *
 * <p>Blanks after the name are allowed ({@code '@st  '} is the stage ST). The name's parts are resolved exactly: an
 * unquoted part folds to upper case, a quoted one keeps its case, so {@code '@"st"'} does not reach the stage
 * {@code ST}.
 */
public abstract class StageUrlFunction extends BuiltInFunction {

    /** A stage name has a database, a schema and its own name at most. */
    private static final int MAX_NAME_PARTS = 3;

    /** The stages the first argument resolves against. */
    private final NamedStageLocator stages;
    /** The engine's configuration, which names the HTTP server the URLs point at. */
    private final EngineConfig config;

    protected StageUrlFunction(final String name, final DataType returnType, final NamedStageLocator stages,
                               final EngineConfig config) {
        super(name, returnType);
        this.stages = stages;
        this.config = config;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return call(args, true);
    }

    /**
     * The function over its argument values.
     *
     * @param args       the values
     * @param pathFolded whether the second argument was a constant the statement folds while it compiles, which
     *                   decides how BUILD_STAGE_FILE_URL spells a dot in it
     * @return the result
     */
    public abstract Object call(List<Object> args, boolean pathFolded);

    /**
     * The named stage the first argument's value names, refused as the account refuses it.
     *
     * @param argument the first argument's value
     * @return the stage
     */
    public NamedStage requireStage(final Object argument) {
        final String written = argument == null ? "" : argument.toString();
        if (written.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Argument 1 to function '" + getName() + "' cannot be null or empty."));
        }
        if (!written.startsWith("@")) {
            throw new RuntimeException(SqlCompilationError.inline("Argument 1 to function '" + getName()
                + "' does not start with '@'. Please specify stage name as '@<stage_name>'."));
        }
        final String body = stripTrailingBlanks(written.substring(1));
        if (body.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("missing stage name in URL: " + written));
        }
        if (body.indexOf('/') >= 0) {
            throw new RuntimeException(SqlCompilationError.inline("Argument 1 to function '" + getName()
                + "' should only contain the stage name and not a path."));
        }
        final List<String> parts = nameParts(body);
        for (final String part : parts) {
            if (part.startsWith("~") || part.startsWith("%")) {
                throw new RuntimeException(SqlCompilationError.inline("Argument 1 to function '" + getName()
                    + "' provides a user or table stage. These stage kinds are not supported by this function."));
            }
        }
        final NamedStage stage = stages == null ? null : stages.namedStage(parts.toArray(new String[0]));
        if (stage == null) {
            throw new RuntimeException(SqlCompilationError.inline("Stage '" + written + "' provided to the function '"
                + getName() + "' does not exist or is not authorized."));
        }
        return stage;
    }

    /**
     * A stage name's parts, each canonical: an unquoted part upper-cased, a quoted one as written without its quotes.
     * An empty part, and a dot after a third part, is the syntax error the account reports for it, positioned within
     * the name ({@code '@st.x.y.z'} at position 6); a part that opens with {@code ~} or {@code %} is kept as written so
     * the caller can refuse its stage kind.
     */
    private static List<String> nameParts(final String body) {
        final List<String> parts = new ArrayList<>();
        int i = 0;
        while (true) {
            if (i >= body.length()) {
                throw syntaxError(i, "<EOF>");
            }
            final char first = body.charAt(i);
            if (first == '.') {
                throw syntaxError(i, ".");
            }
            final StringBuilder part = new StringBuilder();
            if (first == '"') {
                int j = i + 1;
                boolean closed = false;
                while (j < body.length()) {
                    final char c = body.charAt(j);
                    if (c == '"' && j + 1 < body.length() && body.charAt(j + 1) == '"') {
                        part.append('"');
                        j += 2;
                    } else if (c == '"') {
                        closed = true;
                        j++;
                        break;
                    } else {
                        part.append(c);
                        j++;
                    }
                }
                if (!closed) {
                    throw syntaxError(body.length(), "<EOF>");
                }
                parts.add(part.toString());
                i = j;
            } else {
                int j = i;
                while (j < body.length() && body.charAt(j) != '.') {
                    j++;
                }
                final String bare = body.substring(i, j);
                parts.add(bare.startsWith("~") || bare.startsWith("%") ? bare : bare.toUpperCase(Locale.ROOT));
                i = j;
            }
            if (i >= body.length()) {
                return parts;
            }
            if (body.charAt(i) != '.' || parts.size() == MAX_NAME_PARTS) {
                throw syntaxError(i, String.valueOf(body.charAt(i)));
            }
            i++;
        }
    }

    private static RuntimeException syntaxError(final int position, final String token) {
        return new RuntimeException(SqlCompilationError.of(
            "syntax error line 1 at position " + position + " unexpected '" + token + "'."));
    }

    private static String stripTrailingBlanks(final String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * The engine's HTTP server as a client reaches it; a wildcard bind address is reached as localhost.
     *
     * @return {@code http://host:port}
     */
    protected String baseUrl() {
        return serverUrl(config);
    }

    /**
     * The engine's HTTP server as a client reaches it, as the configuration names it; a wildcard bind address is
     * reached as localhost.
     *
     * @param config the engine's configuration, or null for the defaults
     * @return {@code http://host:port}
     */
    public static String serverUrl(final EngineConfig config) {
        String host = config == null ? null : config.getHttpHost();
        if (host == null || host.isBlank() || "0.0.0.0".equals(host) || "::".equals(host)) {
            host = "localhost";
        }
        return "http://" + host + ":" + (config == null ? new EngineConfig().getHttpPort() : config.getHttpPort());
    }

    /** @return the engine's configuration, or null */
    protected EngineConfig config() {
        return config;
    }

    /**
     * Text percent-encoded the way the file URLs spell it: every byte of its UTF-8 form as {@code %xx} in lower-case
     * hex, except the letters, the digits, {@code -}, {@code _} and {@code ~}, and the dot when {@code keepDot} holds —
     * so a slash inside the path is {@code %2f} and a blank {@code %20}.
     *
     * @param text    the text
     * @param keepDot whether a dot stays as it is
     * @return the encoded text
     */
    public static String urlEncoded(final String text, final boolean keepDot) {
        final StringBuilder out = new StringBuilder(text.length() + 16);
        for (final byte b : text.getBytes(StandardCharsets.UTF_8)) {
            final int c = b & 0xFF;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_'
                    || c == '~' || c == '.' && keepDot) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 0xF, 16));
            }
        }
        return out.toString();
    }
}
