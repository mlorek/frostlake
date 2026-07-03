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

package dev.frostlake.metastore.model;


import java.time.LocalDateTime;
import java.util.List;

public class StreamRecord {
    private final List<Object> values;
    private final ChangeType changeType;
    private final boolean isUpdate;
    private final long rowId;
    private final LocalDateTime timestamp;

    public StreamRecord(final List<Object> values, final ChangeType changeType, final boolean isUpdate, final long rowId) {
        this.values = values;
        this.changeType = changeType;
        this.isUpdate = isUpdate;
        this.rowId = rowId;
        this.timestamp = LocalDateTime.now();
    }

    public List<Object> getValues() {
        return values;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public boolean isUpdate() {
        return isUpdate;
    }

    public long getRowId() {
        return rowId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
