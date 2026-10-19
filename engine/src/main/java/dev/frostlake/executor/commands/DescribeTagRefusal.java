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

/**
 * DESCRIBE TAG is a statement the account reads and does not run: whether or not the tag exists, by a bare name or
 * a qualified one and with or without properties, it answers {@code Unsupported feature 'TAG'.} — no compilation
 * prefix — and SHOW TAGS is how a tag is read back. A block that holds one is refused the same way while it
 * compiles, before any of it runs and whatever handler it has, in its turn among the refusals a SHOW statement's
 * shape decides ({@link ShowShapeCompiler}; live-verified).
 */
final class DescribeTagRefusal {

    /** The account's sentence. */
    static final String SENTENCE = "Unsupported feature 'TAG'.";

    private DescribeTagRefusal() {
    }
}
