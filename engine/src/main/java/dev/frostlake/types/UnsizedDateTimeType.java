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

package dev.frostlake.types;

/**
 * A time or a timestamp a routine's signature writes with no digits of its own — {@code DROP FUNCTION f(TIME)} —
 * which names a parameter of any digits, where one written with them names a parameter declared with the same
 * (see {@link WrittenSignatureType}). It is held at the default digits like every other.
 */
public final class UnsizedDateTimeType extends DateTimeType {

    private static final long serialVersionUID = 1L;

    /**
     * @param written the type as parsed from its bare name
     */
    public UnsizedDateTimeType(final DateTimeType written) {
        super(written.getName(), written.getPrecision(), written.hasTimeZone());
    }
}
