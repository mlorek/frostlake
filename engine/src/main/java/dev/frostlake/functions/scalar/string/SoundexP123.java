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

package dev.frostlake.functions.scalar.string;

/**
 * SOUNDEX_P123(text) — the Soundex variant that leaves the first letter's own code unwritten, so a letter after it
 * that shares the code is written: {@code SOUNDEX_P123('Pfister')} is {@code P123} and
 * {@code SOUNDEX_P123('Lloyd')} is {@code L430}, where {@link Soundex} answers {@code P236} and {@code L300}. Every
 * other rule is SOUNDEX's (live-verified).
 */
public class SoundexP123 extends Soundex {

    public SoundexP123() {
        super("SOUNDEX_P123", true);
    }
}
