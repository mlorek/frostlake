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

package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;

import java.util.List;

/**
 * A relation's rows produced once, by the provider wrapped, when first asked, and answered again to every
 * later stage that reads the same relation: a set operation's arm, a join's right side read by more than one
 * stage.
 */
public final class MemoizedRows implements RowsProvider {

    private final RowsProvider source;
    private List<Row> rows;

    /**
     * @param source the provider producing the rows
     */
    public MemoizedRows(final RowsProvider source) {
        this.source = source;
    }

    @Override
    public List<Row> rows() {
        if (rows == null) {
            rows = source.rows();
        }
        return rows;
    }

    @Override
    public String describe() {
        return source.describe();
    }

    /**
     * Whether the rows have been produced.
     *
     * @return whether {@link #rows()} has run
     */
    public boolean isProduced() {
        return rows != null;
    }
}
