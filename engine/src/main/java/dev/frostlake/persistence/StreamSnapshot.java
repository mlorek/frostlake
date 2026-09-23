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
import java.util.Map;

/**
 * Serializable snapshot of a STREAM — its definition plus the pending (unconsumed) change records, which
 * are restored at offset 0 so consuming after a reload yields the same rows. Already-consumed history is
 * intentionally dropped (it has no effect on future reads).
 */
public class StreamSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String sourceTableName;
    public String baseTableName;
    // All base tables of a VIEW-sourced stream (one per UNION ALL branch). Older snapshots have only
    // baseTableName; the reader falls back to it when this list is null/empty.
    public List<String> baseTableNames = new ArrayList<>();
    public String sourceType;
    public String streamType;
    public boolean showInitialRows;
    public boolean stale;
    public String comment;
    public String owner;
    public List<StreamRecordSnapshot> records = new ArrayList<>();
    // The initial rows a SHOW_INITIAL_ROWS stream reports before its first consumption, and a dynamic-table
    // stream's image of the table at its last refresh. Null when the stream has none, and on older snapshots.
    public List<StreamRecordSnapshot> initialRecords;
    public List<List<Object>> refreshImage;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
