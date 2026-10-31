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

import dev.frostlake.metastore.model.DataMetricAttachment;
import dev.frostlake.metastore.model.DataMetricAttachments;

import java.util.ArrayList;
import java.util.List;

/** The data metric functions of a table or a view, written to a snapshot and read back. */
final class DataMetricSnapshots {

    private DataMetricSnapshots() {
    }

    /**
     * @param attachments the object's attachments
     * @return each attachment as a snapshot holds it, in the order they were added
     */
    static List<DataMetricSnapshot> of(final DataMetricAttachments attachments) {
        final List<DataMetricSnapshot> metrics = new ArrayList<>();
        for (final DataMetricAttachment attachment : attachments.list()) {
            final DataMetricSnapshot saved = new DataMetricSnapshot();
            saved.metricName = attachment.getMetricName();
            saved.columns = new ArrayList<>(attachment.getColumns());
            saved.suspended = attachment.isSuspended();
            metrics.add(saved);
        }
        return metrics;
    }

    /**
     * Puts saved attachments and their schedule back.
     *
     * @param metrics     the saved attachments, or null on a snapshot written before they were saved
     * @param schedule    the saved schedule, or null
     * @param attachments the restored object's attachments
     */
    static void restore(final List<DataMetricSnapshot> metrics, final String schedule,
                        final DataMetricAttachments attachments) {
        if (metrics != null) {
            for (final DataMetricSnapshot metric : metrics) {
                final DataMetricAttachment attachment = new DataMetricAttachment(metric.metricName, metric.columns);
                attachment.setSuspended(metric.suspended);
                attachments.add(attachment);
            }
        }
        if (schedule != null) {
            attachments.setSchedule(schedule);
        }
    }
}
