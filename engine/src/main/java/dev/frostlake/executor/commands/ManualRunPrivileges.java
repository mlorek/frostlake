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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.security.SecurityManager;

/**
 * What a run on demand asks: "Executing a task requires either the OWNERSHIP or OPERATE privilege on the task"
 * (documented, EXECUTE TASK), and an alert's run on demand asks the same of the alert, and EXECUTE ALERT on the
 * account (documented, EXECUTE ALERT). A task or an alert the session cannot see is refused as one that is not
 * there.
 */
public final class ManualRunPrivileges {

    private ManualRunPrivileges() {
    }

    /**
     * Refuses an EXECUTE TASK the session may not run. A task that is not there is left to the run, which refuses
     * it.
     *
     * @param queryExecutor the executor, for the session's rights
     * @param parts         the task's name parts, as written
     */
    public static void requireTask(final QueryExecutor queryExecutor, final String[] parts) {
        final Schema schema = queryExecutor.getCatalog().requireOwningSchema(QualifiedName.of(parts));
        final Task task = schema.hasTaskExact(parts[parts.length - 1]) ? schema.getTaskExact(parts[parts.length - 1])
            : null;
        if (task == null) {
            return;
        }
        requireSeen(queryExecutor, "TASK", "Task", schema, task.getName(), task.getOwner());
        queryExecutor.requirePrivilege(Privilege.OPERATE, "TASK", parts, null);
    }

    /**
     * Refuses an EXECUTE ALERT the session may not run.
     *
     * @param queryExecutor the executor, for the session's rights
     * @param schema        the alert's schema
     * @param name          the alert's name
     * @param owner         the role that owns the alert
     * @param parts         the alert's name parts, as written
     */
    public static void requireAlert(final QueryExecutor queryExecutor, final Schema schema, final String name,
                                    final String owner, final String[] parts) {
        requireSeen(queryExecutor, "ALERT", "Alert", schema, name, owner);
        queryExecutor.requirePrivilege(Privilege.OPERATE, "ALERT", parts, null);
        // The caller also needs EXECUTE ALERT on the account (documented, EXECUTE ALERT).
        AccountPrivileges.requireOnAccount(queryExecutor, queryExecutor.getCatalog(), Privilege.EXECUTE_ALERT);
    }

    /** Refuses an object the session cannot see as one that is not there. */
    private static void requireSeen(final QueryExecutor queryExecutor, final String kind, final String spelled,
                                    final Schema schema, final String name, final String owner) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        if (security != null && !security.sees(kind, schema.qualifiedName(name), owner)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(spelled, schema.qualifiedName(name)));
        }
    }
}
