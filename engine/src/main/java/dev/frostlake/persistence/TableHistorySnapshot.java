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
 * A table's time-travel history, as a snapshot of the engine's state keeps it beside the table's rows: when the
 * history starts, whether its window had dropped snapshots, and each snapshot it keeps. A snapshot's row is either a
 * row stored now — its place among the saved rows, written as {@code -(place + 1)} — or one of the past rows kept
 * here once however many snapshots hold it, with the identity it had.
 *
 * <p>The table's shapes over its history are kept too, and each snapshot names the one it was taken in, with its rows
 * as it took them where a column change has rewritten its rows since: a CLONE ... AT a point before the change takes
 * the shape and the rows of the point.
 */
public class TableHistorySnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public long startMillis;
    public long startSequence;
    public boolean evicted;
    /** The identity of each row stored now, in the order its values are saved. */
    public List<Long> currentRowIds = new ArrayList<>();
    /** The rows only the history holds: their identities and their values. */
    public List<Long> pastRowIds = new ArrayList<>();
    public List<List<Object>> pastRows = new ArrayList<>();
    public List<TableHistoryPoint> points = new ArrayList<>();
    /** The table's shapes over its history, oldest first; null in a snapshot written before shapes were kept. */
    public List<TableStructureSnapshot> structures;
    /** The rows snapshots took in a shape a column change has rewritten since, each kept once. */
    public List<List<Object>> takenRows;
}
