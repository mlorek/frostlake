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
 * Snowflake's UUID type: a 128-bit value held and shown as its canonical lower-case text,
 * {@code 1b4e28ba-2fa1-11d2-883f-0016d3cca427}. Every piece of metadata names it as a type of its own —
 * SYSTEM$TYPEOF reads {@code UUID[SB16]}, DESCRIBE, GET_DDL and INFORMATION_SCHEMA say {@code UUID}
 * with no length, SHOW COLUMNS {@code {"type":"UUID","nullable":true}} — while a string function reads
 * it as its text and JDBC reports a VARCHAR (live-verified). It takes no parameters: {@code UUID(36)}
 * is a syntax error.
 */
public class UuidType extends StringType {

    /** The width of the canonical text. */
    private static final int TEXT_WIDTH = 36;

    public static final UuidType UUID = new UuidType();

    public UuidType() {
        super("UUID", TEXT_WIDTH);
    }
}
