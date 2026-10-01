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

import java.util.ArrayList;
import java.util.List;

/**
 * One column as the files read so far give it together: its place in the first file that has it, its merged
 * type, the files it appears in and whether any lets it be NULL.
 */
final class MergedColumn {

    private final String name;
    private final int orderId;
    private InferredType type;
    private boolean nullable;
    private final List<String> fileNames = new ArrayList<String>();

    MergedColumn(final String name, final int orderId, final InferredType type, final boolean nullable) {
        this.name = name;
        this.orderId = orderId;
        this.type = type;
        this.nullable = nullable;
    }

    String name() {
        return name;
    }

    int orderId() {
        return orderId;
    }

    InferredType type() {
        return type;
    }

    boolean nullable() {
        return nullable;
    }

    List<String> fileNames() {
        return fileNames;
    }

    /**
     * Take in one more file's column of the same name.
     *
     * @param column the file's column
     * @param fileName how FILENAMES lists the file
     * @param selfDescribing whether the files declare their types
     * @param iceberg whether a type conflict is refused
     */
    void add(final FileColumn column, final String fileName, final boolean selfDescribing, final boolean iceberg) {
        type = InferredType.merge(type, column.type(), selfDescribing, iceberg);
        nullable = nullable || column.nullable();
        if (!fileNames.contains(fileName)) {
            fileNames.add(fileName);
        }
    }
}
