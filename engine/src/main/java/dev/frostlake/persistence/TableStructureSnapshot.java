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

/**
 * One of a table's shapes over its history, as a snapshot of the engine's state keeps it beside the table's
 * time-travel history (see {@link TableHistorySnapshot}): when the shape took effect, its columns, and the highest
 * position the table had handed out by then.
 */
public class TableStructureSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public long fromMillis;
    public long fromSequence;
    public List<ColumnSnapshot> columns = new ArrayList<>();
    public int highestOrdinal;
}
