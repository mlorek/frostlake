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
 * LPAD(expr, len [, pad]) — a VARCHAR padded to len characters, or a BINARY padded to len BYTES.
 *
 * <p>{@code LPAD(TO_BINARY('4845','HEX'), 5, TO_BINARY('0102','HEX'))} is {@code 0102014845}: the pad
 * pattern repeats byte-wise and the result is BINARY.
 */
public class LPad extends PadFunction {
    public LPad() {
        super("LPAD", true);
    }
}
