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
 * The DATA METRIC FUNCTIONS attached to a table or a view, in the order they were added, and the DATA_METRIC_SCHEDULE
 * they run on as written ({@code '60 MINUTE'}), which the references listing renders as cron. Recorded and never
 * evaluated: nothing computes a value, and no scheduled run happens.
 */
public final class DataMetricAttachments {

    private final List<DataMetricAttachment> attachments = new ArrayList<>();
    private String schedule;

    /** @return the schedule as written, or null when none is set */
    public String getSchedule() {
        return schedule;
    }

    /** @param schedule the schedule as written, or null to unset it */
    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    /** @return the attachments, in the order they were added */
    public List<DataMetricAttachment> list() {
        return new ArrayList<>(attachments);
    }

    /**
     * Attach a metric unless the same one over the same columns is already there (a repeat adds nothing).
     *
     * @param attachment the metric and its columns
     */
    public void add(final DataMetricAttachment attachment) {
        if (find(attachment.getMetricName(), attachment.getColumns()) == null) {
            attachments.add(attachment);
        }
    }

    /**
     * @param metricName the metric's name
     * @param columns    the columns it measures
     * @return the attachment for that metric over those columns, or null when there is none
     */
    public DataMetricAttachment find(final String metricName, final List<String> columns) {
        for (final DataMetricAttachment attachment : attachments) {
            if (attachment.matches(metricName, columns)) {
                return attachment;
            }
        }
        return null;
    }

    /**
     * Detach a metric.
     *
     * @param metricName the metric's name
     * @param columns    the columns it measures
     * @return whether there was one to detach
     */
    public boolean drop(final String metricName, final List<String> columns) {
        final DataMetricAttachment attachment = find(metricName, columns);
        return attachment != null && attachments.remove(attachment);
    }

    /**
     * Rename a measured column in every attachment, matched as a column is (ignoring case).
     *
     * @param oldName the column's name before
     * @param newName its name after
     */
    public void renameColumn(final String oldName, final String newName) {
        for (int i = 0; i < attachments.size(); i++) {
            final DataMetricAttachment metric = attachments.get(i);
            final List<String> renamed = new ArrayList<>();
            for (final String column : metric.getColumns()) {
                renamed.add(column.equalsIgnoreCase(oldName) ? newName : column);
            }
            final DataMetricAttachment replaced = new DataMetricAttachment(metric.getMetricName(), renamed);
            replaced.setSuspended(metric.isSuspended());
            attachments.set(i, replaced);
        }
    }

    /**
     * Copy every attachment, its suspension included, and the schedule into another holder.
     *
     * @param target the holder of the copy
     */
    public void copyInto(final DataMetricAttachments target) {
        for (final DataMetricAttachment metric : attachments) {
            final DataMetricAttachment copied = new DataMetricAttachment(metric.getMetricName(), metric.getColumns());
            copied.setSuspended(metric.isSuspended());
            target.attachments.add(copied);
        }
        target.schedule = schedule;
    }
}
