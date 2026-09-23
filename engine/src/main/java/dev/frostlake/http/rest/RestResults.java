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

package dev.frostlake.http.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Asynchronous execution: an operation that does not finish within the synchronous wait — or that the request
 * asks to run asynchronously with {@code asyncExec=true} — is answered {@code 202 Accepted} with a
 * {@code Location} of {@code /api/v2/results/<handle>} and the {@code SuccessAcceptedResponse} body, and keeps
 * running. {@code GET /api/v2/results/<handle>} answers {@code 202} again until it completes, then the
 * operation's own answer; a handle is forgotten once it is older than the retention time.
 */
public final class RestResults {

    /** The code the account answers a request still in progress with. */
    public static final String CODE_IN_PROGRESS = "392604";
    /** The path results are fetched under. */
    public static final String RESULTS_PATH = "/api/v2/results/";
    /** The code the account answers a well-formed handle that names no result with. */
    public static final String CODE_NO_RESULT = "390404";

    /** A handle is a query id followed by its result-type digit. */
    private static final Pattern HANDLE = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
        + "[0-9a-fA-F]{12}[0-9]*");
    /** The result-type digit the account appends to the job id to form the handle. */
    private static final String RESULT_TYPE = "0";

    private static final Logger logger = LoggerFactory.getLogger(RestResults.class);
    private static final String IN_PROGRESS = "Request execution in progress. Use provided Location header or result "
        + "handler id to perform query monitoring and management.";

    private final ExecutorService executor;
    private final long syncWaitMillis;
    private final long retentionMillis;
    private final Map<String, RestPendingResult> pending = new ConcurrentHashMap<>();

    /**
     * @param syncWaitMillis how long a request waits for its operation before it is answered {@code 202}
     * @param retentionMillis how long a handle stays fetchable after it was issued
     */
    public RestResults(final long syncWaitMillis, final long retentionMillis) {
        this.syncWaitMillis = syncWaitMillis;
        this.retentionMillis = retentionMillis;
        final AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final Thread thread = new Thread(runnable, "frostlake-rest-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /**
     * Runs an operation, answering its own response when it completes within the synchronous wait and
     * {@code 202} otherwise.
     *
     * @param work the operation
     * @param async whether the request asked for asynchronous execution, which answers {@code 202} at once
     */
    public RestResponse submit(final Callable<RestResponse> work, final boolean async) {
        purgeExpired();
        final Future<RestResponse> future = executor.submit(work);
        if (!async) {
            try {
                return future.get(syncWaitMillis, TimeUnit.MILLISECONDS);
            } catch (final TimeoutException stillRunning) {
                logger.debug("REST operation exceeded the {} ms synchronous wait; answering 202", syncWaitMillis);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return RestResponse.error(500, "Request interrupted.", null);
            } catch (final ExecutionException failed) {
                return failure(failed);
            }
        }
        final String handle = UUID.randomUUID().toString() + RESULT_TYPE;
        pending.put(handle, new RestPendingResult(future, System.currentTimeMillis()));
        return accepted(handle);
    }

    /**
     * The state of an operation answered {@code 202}: {@code 202} while it runs, its own answer once it has
     * completed. A handle that is not a query id is {@code 400}; one that names no operation — unknown, or
     * expired — {@code 404} with the account's does-not-exist code {@code 390404}.
     */
    public RestResponse fetch(final String handle) {
        purgeExpired();
        if (handle == null || !HANDLE.matcher(handle).matches()) {
            return RestResponse.error(400, "Invalid resultHandler: " + handle, null);
        }
        final RestPendingResult result = pending.get(handle);
        if (result == null) {
            return RestResponse.error(404, "Specified object does not exist or not authorized.", CODE_NO_RESULT);
        }
        if (!result.future().isDone()) {
            return accepted(handle);
        }
        try {
            return result.future().get();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return RestResponse.error(500, "Request interrupted.", null);
        } catch (final ExecutionException failed) {
            return failure(failed);
        }
    }

    /** How many handles are held. */
    public int pendingCount() {
        return pending.size();
    }

    /** Stops the worker threads; operations still running are interrupted. */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * The {@code 202} body as the account sends it: the handle under {@code result_handler} (the specification
     * spells it {@code resultHandler}) and the job id — the handle without its result-type digit.
     */
    private RestResponse accepted(final String handle) {
        final ObjectNode body = RestJson.object();
        body.put("code", CODE_IN_PROGRESS);
        body.put("message", IN_PROGRESS);
        body.put("result_handler", handle);
        body.put("job_id", handle.endsWith(RESULT_TYPE) ? handle.substring(0, handle.length() - 1) : handle);
        return RestResponse.json(202, body).withHeader("Location", RESULTS_PATH + handle);
    }

    private static RestResponse failure(final ExecutionException failed) {
        final Throwable cause = failed.getCause() != null ? failed.getCause() : failed;
        if (cause instanceof RestException) {
            return RestResponse.error((RestException) cause);
        }
        logger.error("REST operation failed", cause);
        return RestResponse.error(500, cause.getMessage() != null ? cause.getMessage() : cause.toString(), null);
    }

    private void purgeExpired() {
        final long cutoff = System.currentTimeMillis() - retentionMillis;
        final List<String> expired = new ArrayList<>();
        for (final Map.Entry<String, RestPendingResult> entry : pending.entrySet()) {
            if (entry.getValue().acceptedAtMillis() < cutoff) {
                expired.add(entry.getKey());
            }
        }
        for (final String handle : expired) {
            final RestPendingResult gone = pending.remove(handle);
            if (gone != null && !gone.future().isDone()) {
                gone.future().cancel(true);
            }
        }
    }
}
