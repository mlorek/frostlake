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

package dev.frostlake.executor;

import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.storage.Row;

import java.util.AbstractList;
import java.util.List;

/**
 * A relation's rows as a list that produces them when first touched: a planned source's rows, which the
 * source stage reads when the pipeline runs and which anything reading them while planning produces early.
 * Every operation reaches the produced list, so the rows can be read, replaced and appended as any list's.
 */
final class DeferredRows extends AbstractList<Row> {

    private final RowsProvider rows;

    /**
     * @param rows the provider producing the rows
     */
    DeferredRows(final RowsProvider rows) {
        this.rows = rows;
    }

    private List<Row> produced() {
        return rows.rows();
    }

    @Override
    public Row get(final int index) {
        return produced().get(index);
    }

    @Override
    public int size() {
        return produced().size();
    }

    @Override
    public Row set(final int index, final Row row) {
        return produced().set(index, row);
    }

    @Override
    public void add(final int index, final Row row) {
        produced().add(index, row);
    }

    @Override
    public Row remove(final int index) {
        return produced().remove(index);
    }
}
