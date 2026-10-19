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

package dev.frostlake.executor.udf;

/**
 * What a handler's {@code Variant} was made as, by the word a refusal names it with. The kind decides which
 * readers the value answers (live-verified): a value made from a text answers every one of them, every value
 * answers as a text, a number answers as either kind of number and as a timestamp, a boolean as either kind of
 * number, and any other value as its own kind only.
 */
public enum SnowparkVariantKind {

    REAL_NUMBER("RealNumber"),
    FIXED_NUMBER("FixedNumber"),
    BOOLEAN("Boolean"),
    STRING("String"),
    BINARY("Binary"),
    TIME("Time"),
    DATE("Date"),
    TIMESTAMP("Timestamp"),
    ARRAY("Array"),
    OBJECT("Object");

    private final String spelled;

    SnowparkVariantKind(final String spelled) {
        this.spelled = spelled;
    }

    /**
     * @return the kind as a refusal spells it
     */
    public String spelled() {
        return spelled;
    }

    /**
     * @param target the kind a reader asks for
     * @return whether a value made as this kind is read as that one
     */
    public boolean readsAs(final SnowparkVariantKind target) {
        if (this == target || this == STRING || target == STRING) {
            return true;
        }
        if (this == REAL_NUMBER) {
            return target == FIXED_NUMBER || target == TIMESTAMP;
        }
        if (this == FIXED_NUMBER) {
            return target == REAL_NUMBER || target == TIMESTAMP;
        }
        return this == BOOLEAN && (target == REAL_NUMBER || target == FIXED_NUMBER);
    }
}
