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

package dev.frostlake.formats;

/**
 * An Avro string that is no UTF-8, refused in the account's words: {@code Error parsing AVRO: error parsing AVRO
 * data: Invalid UTF8 detected while decoding 'caf0xE9'}, each byte that is no part of UTF-8 shown by its value
 * (live-verified). It leaves the reader as it is, not as a malformed file's refusal.
 */
public class AvroUnreadableText extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String shown;

    /**
     * @param shown the string, each unreadable byte spelled as its value
     */
    public AvroUnreadableText(final String shown) {
        super("Error parsing AVRO: error parsing AVRO data: Invalid UTF8 detected while decoding '" + shown + "'");
        this.shown = shown;
    }

    /** @return the string, each unreadable byte spelled as its value */
    public String getShown() {
        return shown;
    }
}
