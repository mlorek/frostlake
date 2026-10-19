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

package dev.frostlake.persistence;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** One snapshot of a table's time-travel history (see {@link TableHistorySnapshot}). */
public class TableHistoryPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    public long epochMillis;
    public long sequence;
    /** Each row: a stored row's place as {@code -(place + 1)}, or a past row's index. */
    public List<Integer> rows = new ArrayList<>();
    /** The shape the snapshot was taken in, as its index among the history's structures; null for the current one. */
    public Integer layout;
    /** The rows as the snapshot took them, as indexes into the history's taken rows; null where they are its rows. */
    public List<Integer> takenRows;
}
