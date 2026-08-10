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

import java.util.ArrayList;
import java.util.List;

/**
 * One DATA METRIC FUNCTION attached to a table, over the columns it measures — or over none, for the
 * table-level metrics (ROW_COUNT, FRESHNESS and their kind).
 *
 * <p>Frostlake records the attachment and never evaluates the metric: nothing computes a value, and
 * no scheduled run happens. What it does model is the SUSPEND / RESUME state, which is per
 * attachment rather than per table (live-verified).
 */
public class DataMetricAttachment {

    private final String metricName;
    private final List<String> columns;
    private boolean suspended;

    public DataMetricAttachment(final String metricName, final List<String> columns) {
        this.metricName = metricName;
        this.columns = new ArrayList<>(columns);
    }

    public String getMetricName() { return metricName; }
    public List<String> getColumns() { return new ArrayList<>(columns); }

    public boolean isSuspended() { return suspended; }
    public void setSuspended(final boolean suspended) { this.suspended = suspended; }

    /** Whether this attachment is the one a statement names: same metric, same columns. */
    public boolean matches(final String otherMetric, final List<String> otherColumns) {
        if (!metricName.equalsIgnoreCase(otherMetric) || columns.size() != otherColumns.size()) {
            return false;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (!columns.get(i).equalsIgnoreCase(otherColumns.get(i))) {
                return false;
            }
        }
        return true;
    }
}
