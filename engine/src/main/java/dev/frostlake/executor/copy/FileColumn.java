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
 * One column as a single staged file gives it to INFER_SCHEMA: its name, its place in the file, the type its
 * values settled on and whether the file lets it be NULL.
 */
final class FileColumn {

    private final String name;
    private final int position;
    private InferredType type;
    private final boolean nullable;

    /**
     * A column of one file.
     *
     * @param name the name it is reported under, case already folded when IGNORE_CASE asks for it
     * @param position its place in the file, from 0
     * @param type its type so far, or null before a value is read
     * @param nullable whether the file lets it be NULL
     */
    FileColumn(final String name, final int position, final InferredType type, final boolean nullable) {
        this.name = name;
        this.position = position;
        this.type = type;
        this.nullable = nullable;
    }

    String name() {
        return name;
    }

    int position() {
        return position;
    }

    InferredType type() {
        return type;
    }

    void setType(final InferredType settled) {
        this.type = settled;
    }

    boolean nullable() {
        return nullable;
    }
}
