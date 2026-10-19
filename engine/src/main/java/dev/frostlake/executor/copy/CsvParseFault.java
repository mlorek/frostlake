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

package dev.frostlake.executor.copy;

/**
 * A CSV record that does not parse, as {@link CsvRecordReader} refuses it: a character after a closing enclosure, or an
 * enclosure still open at the end of the file. The message is the refusal a staged-file query or a schema scan
 * raises, the file and the place named; a load reports the same fault through its own result, so the parts are kept
 * apart too — the sentence, the place, and the field it was found in.
 */
public final class CsvParseFault extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String detail;
    private final int line;
    private final int character;
    private final int field;

    /**
     * A fault.
     *
     * @param message   the refusal, as a query raises it
     * @param detail    its first sentence, as a load reports it
     * @param line      the line the fault is on, from 1
     * @param character the character within that line, from 1
     * @param field     the field it was found in, from 1
     */
    CsvParseFault(final String message, final String detail, final int line, final int character, final int field) {
        super(message);
        this.detail = detail;
        this.line = line;
        this.character = character;
        this.field = field;
    }

    /**
     * The fault's sentence, without the place.
     *
     * @return the sentence
     */
    public String getDetail() {
        return detail;
    }

    /**
     * The line the fault is on.
     *
     * @return the line, from 1
     */
    public int getLine() {
        return line;
    }

    /**
     * The character within the line.
     *
     * @return the character, from 1
     */
    public int getCharacter() {
        return character;
    }

    /**
     * The field the fault was found in.
     *
     * @return the field, from 1
     */
    public int getField() {
        return field;
    }
}
