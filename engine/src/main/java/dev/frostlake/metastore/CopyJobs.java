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

package dev.frostlake.metastore;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The COPY INTO table statements VALIDATE may validate again, by query ID, and each session's last one: VALIDATE
 * takes a COPY's query ID or {@code '_last'}, "the last load executed during the current session, regardless of the
 * specified target table" (documented). Kept in memory for the most recent loads, as the account keeps its load
 * metadata for a while: one older than that, or one a restart forgot, is no longer found.
 */
public final class CopyJobs {

    /** How many loads are kept. */
    private static final int KEPT = 1000;

    private final LinkedHashMap<String, CopyJob> byQueryId = new LinkedHashMap<String, CopyJob>();
    private final Map<String, String> lastBySession = new HashMap<String, String>();

    /**
     * Records a completed COPY.
     *
     * @param job the COPY
     */
    public synchronized void add(final CopyJob job) {
        if (job.getQueryId() == null) {
            return;
        }
        byQueryId.put(job.getQueryId(), job);
        lastBySession.put(job.getSessionId(), job.getQueryId());
        final Iterator<Map.Entry<String, CopyJob>> oldest = byQueryId.entrySet().iterator();
        while (byQueryId.size() > KEPT && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * The COPY a query ID names.
     *
     * @param queryId the query ID
     * @return the COPY, or null when none of that ID is kept
     */
    public synchronized CopyJob byQueryId(final String queryId) {
        return byQueryId.get(queryId);
    }

    /**
     * The last COPY a session ran.
     *
     * @param sessionId the session
     * @return the COPY, or null when the session ran none that is kept
     */
    public synchronized CopyJob lastOf(final String sessionId) {
        final String queryId = lastBySession.get(sessionId);
        return queryId == null ? null : byQueryId.get(queryId);
    }
}
