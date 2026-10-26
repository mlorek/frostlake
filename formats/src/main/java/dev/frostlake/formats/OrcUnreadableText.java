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
 * An ORC string that is no UTF-8, refused in the account's words: {@code Error parsing the ORC file: Error parsing the
 * ORC file: Invalid UTF8 detected while decoding 'caf0xE9'}, the prefix twice and each byte that is no part of UTF-8
 * shown by its value (live-verified). It leaves the reader as it is, not as a malformed file's refusal.
 */
public class OrcUnreadableText extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param shown the string, each unreadable byte spelled as its value
     */
    public OrcUnreadableText(final String shown) {
        super("Error parsing the ORC file: Error parsing the ORC file: Invalid UTF8 detected while decoding '" + shown
            + "'");
    }
}
