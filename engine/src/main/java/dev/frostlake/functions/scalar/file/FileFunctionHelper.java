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

import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Shared argument handling for the fourteen {@code FL_*} accessors, mirroring the family-local helper
 * pattern of {@code VectorFunctionHelper}.
 *
 * <p>Live, an accessor VALIDATES a caller-supplied metadata object exactly as
 * {@code TO_FILE} does — {@code FL_GET_SIZE(OBJECT_CONSTRUCT('RELATIVE_PATH','hello.txt','SIZE',24))}
 * fails "Invalid file metadata. Missing required fields: LAST_MODIFIED, CONTENT_TYPE, ETAG." and
 * {@code FL_GET_SIZE(42)} fails "Unsupported cast to FILE." A VARCHAR argument never reaches here: it
 * is rejected at COMPILE time as "Invalid argument types for function 'FL_GET_RELATIVE_PATH':
 * (VARCHAR(14))", which the expression layer raises from its strict-argument map.
 *
 * <p>The NULL rules split the family in two, and are the least guessable thing about it. The eight
 * plain getters propagate NULL. {@code FL_GET_FILE_TYPE} and all five {@code FL_IS_*} DO NOT: live,
 * {@code FL_GET_FILE_TYPE(NULL)} is the string {@code 'unknown'} and {@code FL_IS_IMAGE(NULL)} is
 * FALSE, never NULL — measured both for a literal NULL and for a NULL FILE column.
 */
public final class FileFunctionHelper {

    private FileFunctionHelper() {
    }

    /**
     * The validated descriptor behind an accessor's argument.
     *
     * @param value the argument value
     * @return the descriptor node, or null when the argument is SQL NULL
     */
    public static JsonNode descriptorOf(final Object value) {
        if (value == null) {
            return null;
        }
        final VariantValue validated = FileDescriptor.fromMetadataObject(value, false);
        return validated == null ? null : validated.node();
    }

    /**
     * A descriptor field as a string, NULL-propagating.
     *
     * @param value the accessor's argument
     * @param field the descriptor field name
     * @return the field's text, or null when the argument or the field is absent
     */
    public static String textField(final Object value, final String field) {
        final JsonNode descriptor = descriptorOf(value);
        if (descriptor == null) {
            return null;
        }
        final JsonNode found = descriptor.get(field);
        return found == null || found.isNull() ? null : found.asString();
    }

    /**
     * A descriptor's {@code SIZE} as a number, NULL-propagating.
     *
     * @param value the accessor's argument
     * @return the size, or null
     */
    public static BigDecimal numberField(final Object value, final String field) {
        final JsonNode descriptor = descriptorOf(value);
        if (descriptor == null) {
            return null;
        }
        final JsonNode found = descriptor.get(field);
        if (found == null || found.isNull()) {
            return null;
        }
        return new BigDecimal(found.asString());
    }

    /**
     * The {@code CONTENT_TYPE} the classifiers key off, WITHOUT propagating NULL — a NULL file simply
     * has no content type, which {@link FileContentTypes} maps to {@code unknown} / FALSE.
     *
     * @param value the accessor's argument
     * @return the content type, or null
     */
    public static String contentTypeOf(final Object value) {
        if (value == null) {
            return null;
        }
        return textField(value, FileDescriptor.CONTENT_TYPE);
    }
}
