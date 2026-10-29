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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A stage's ENCRYPTION group, judged as CREATE STAGE and ALTER STAGE … SET judge it (live-verified).
 *
 * <p>The group is a property list: a value that is none, TRUE say, is "invalid value [Not a property list: TRUE] for
 * parameter '{1}'", and an empty pair of parentheses is the same refusal of {@code TOK_CONSTANT_LIST}. Its parameters
 * are read in the order written: a TYPE that is no text or names no type is "invalid value [BOGUS] for parameter
 * 'TYPE'", a MASTER_KEY or a KMS_KEY_ID that is no text is the same refusal of that parameter, and any other name is
 * "invalid parameter 'FOO'". A parameter given twice counts once and keeps its last value; without a TYPE the type is
 * AWS_CSE.
 *
 * <p>Then the group as a whole. Each type takes its own number of parameters, TYPE among them - NONE, SNOWFLAKE_SSE,
 * SNOWFLAKE_FULL and AWS_SSE_S3 one, AWS_CSE, AWS_SSE_KMS and GCS_SSE_KMS one or two, AZURE_CSE two - else
 * "Incompatible number of parameters for value [TYPE]", and the second is the type's own key, a MASTER_KEY for
 * client-side encryption and a KMS_KEY_ID for a KMS one, else "value [AWS_SSE_KMS] for parameter 'TYPE' conflict with
 * parameter 'MASTER_KEY'". A MASTER_KEY is Base64 of 128, 192 or 256 bits. Last, the type must fit the location: an
 * internal or temporary stage takes Snowflake's own types alone ("Cannot set URL, credentials, or encryption key of an
 * internal or temporary stage.", which an ALTER of one says of any group), and an external one its cloud's types and
 * NONE, any other type an invalid value of TYPE. What SHOW STAGES reads as {@code has_encryption_key} is then whether
 * an external stage encrypts at all.
 */
final class StageEncryption {

    /** The type a group without a TYPE takes. */
    private static final String DEFAULT_TYPE = "AWS_CSE";

    /** Every type a group may name, whatever its stage. */
    private static final Set<String> TYPES = Set.of("SNOWFLAKE_FULL", "SNOWFLAKE_SSE", "AWS_CSE", "AWS_SSE_S3",
        "AWS_SSE_KMS", "GCS_SSE_KMS", "AZURE_CSE", "NONE");

    /** The types that take no parameter beside TYPE. */
    private static final Set<String> ALONE = Set.of("NONE", "SNOWFLAKE_SSE", "SNOWFLAKE_FULL", "AWS_SSE_S3");

    /** The types an internal stage takes, and the types of each cloud's stages. */
    private static final Set<String> INTERNAL = Set.of("SNOWFLAKE_SSE", "SNOWFLAKE_FULL");
    private static final Set<String> S3 = Set.of("AWS_CSE", "AWS_SSE_S3", "AWS_SSE_KMS", "NONE");
    private static final Set<String> AZURE = Set.of("AZURE_CSE", "AWS_CSE", "NONE");
    private static final Set<String> GCS = Set.of("GCS_SSE_KMS", "NONE");

    private static final String MASTER_KEY = "MASTER_KEY";
    private static final String KMS_KEY_ID = "KMS_KEY_ID";
    private static final String TYPE = "TYPE";

    private final String type;
    private final boolean key;

    private StageEncryption(final String type, final boolean key) {
        this.type = type;
        this.key = key;
    }

    /** @return the type the group names, or takes by default */
    String type() {
        return type;
    }

    /** @return whether the stage encrypts with a type of its cloud: what SHOW STAGES reads as has_encryption_key */
    boolean key() {
        return key;
    }

    /**
     * Judge a group.
     *
     * @param written  the group's value when it is no property list (TRUE, FALSE, a text), else null
     * @param group    the property list, else null
     * @param url      the stage's location, or null for an internal stage
     * @param internal whether the stage is internal or temporary
     * @param create   whether a CREATE STAGE writes the group, which an internal stage takes of Snowflake's types
     * @return the group judged
     */
    static StageEncryption judge(final String written, final FrostlakeParser.ParenOptionListContext group,
                                 final String url, final boolean internal, final boolean create) {
        if (group == null || group.parenOption().isEmpty()) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter("Not a property list: "
                + (group == null ? written : "TOK_CONSTANT_LIST"), "{1}"));
        }
        final Map<String, String> read = new LinkedHashMap<>();
        for (final FrostlakeParser.ParenOptionContext option : group.parenOption()) {
            final String name = option.optionKey().getText().toUpperCase(Locale.ROOT);
            final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
            final boolean text = value != null && value.STRING_LITERAL() != null;
            if (!TYPE.equals(name) && !MASTER_KEY.equals(name) && !KMS_KEY_ID.equals(name)) {
                throw new RuntimeException(SqlCompilationError.of("invalid parameter '"
                    + option.optionKey().getText() + "'"));
            }
            final String given = text ? ParseTreeText.extractStringLiteral(value.STRING_LITERAL()) : null;
            if (!text || TYPE.equals(name) && !TYPES.contains(given.toUpperCase(Locale.ROOT))) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text ? given
                    : value != null ? ParseTreeText.getOriginalText(value) : listed(option), name));
            }
            read.put(name, TYPE.equals(name) ? given.toUpperCase(Locale.ROOT) : given);
        }
        final boolean typed = read.containsKey(TYPE);
        final String chosen = typed ? read.get(TYPE) : DEFAULT_TYPE;
        final int count = read.size() + (typed ? 0 : 1);
        if (ALONE.contains(chosen) ? count != 1 : "AZURE_CSE".equals(chosen) ? count != 2 : count > 2) {
            throw new RuntimeException(SqlCompilationError.of("Incompatible number of parameters for value [TYPE]"));
        }
        final String own = chosen.endsWith("_CSE") ? MASTER_KEY : KMS_KEY_ID;
        for (final String name : read.keySet()) {
            if (!TYPE.equals(name) && !own.equals(name)) {
                throw new RuntimeException(SqlCompilationError.of("value [" + chosen
                    + "] for parameter 'TYPE' conflict with parameter '" + name + "'"));
            }
        }
        if (read.containsKey(MASTER_KEY)) {
            requireMasterKey(read.get(MASTER_KEY));
        }
        if (internal) {
            if (!create || !INTERNAL.contains(chosen)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Cannot set URL, credentials, or encryption key of an internal or temporary stage."));
            }
            return new StageEncryption(chosen, false);
        }
        final Set<String> fitting = typesAt(url);
        if (fitting != null && !fitting.contains(chosen)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(chosen, TYPE));
        }
        return new StageEncryption(chosen, !"NONE".equals(chosen));
    }

    /** A value written as a parenthesized list, as written. */
    private static String listed(final FrostlakeParser.ParenOptionContext option) {
        final FrostlakeParser.StringLiteralListContext values = option.stringLiteralList();
        return "(" + (values == null ? "" : ParseTreeText.getOriginalText(values)) + ")";
    }

    /** The types a location's cloud takes, or null for a location of no cloud the account knows. */
    private static Set<String> typesAt(final String url) {
        final int colon = url == null ? -1 : url.indexOf(':');
        final String scheme = colon <= 0 ? "" : url.substring(0, colon).toLowerCase(Locale.ROOT);
        if (scheme.startsWith("s3")) {
            return S3;
        }
        return "azure".equals(scheme) ? AZURE : "gcs".equals(scheme) ? GCS : null;
    }

    /**
     * A master key is Base64 - "The provided master key is not properly Base64-encoded." - of 128, 192 or 256 bits:
     * "The provided master key has invalid length. It must be either 128 bits, 192 bits, or 256 bits long.", which is
     * what a key too short to hold a whole byte reads too.
     */
    private static void requireMasterKey(final String key) {
        int digits = 0;
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            final boolean base64 = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                || c == '+' || c == '/';
            if (!base64 && c != '=') {
                throw new RuntimeException("The provided master key is not properly Base64-encoded.");
            }
            digits += base64 ? 1 : 0;
        }
        final int bytes = digits * 6 / 8;
        if (bytes != 16 && bytes != 24 && bytes != 32) {
            throw new RuntimeException("The provided master key has invalid length. It must be either 128 bits, 192"
                + " bits, or 256 bits long.");
        }
    }
}
