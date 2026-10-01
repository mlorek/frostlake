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

package dev.frostlake.parser;

import org.antlr.v4.runtime.Token;

/**
 * Which single unquoted words live reads as the name of a class — in {@code SHOW <class>},
 * {@code DROP <class> <instance>} and a class-instance scope, {@code SHOW <objects> IN <class> <instance>}.
 * A keyword never is: live refuses {@code SHOW TABLES IN USER x} at the {@code x} and {@code SHOW T} at the
 * {@code T} (live-verified).
 */
public final class ClassNameWords {

    /**
     * Words live's lexer keeps as keywords where Frostlake's reads a plain name: the ODBC escape words of
     * {@code {d '…'}}, {@code {t '…'}}, {@code {ts '…'}}, {@code {fn …}} and {@code {oj …}}, object kinds
     * Frostlake has no statements for, and words of statements it has none for: {@code SHOW ALIAS} is refused at
     * ALIAS, as {@code SHOW SOURCE} and {@code SHOW BRANCH} are at theirs.
     */
    private static final String[] KEYWORDS = {
        "D", "T", "TS", "FN", "OJ",
        "ALERT", "DATASET", "EXTERNAL", "FAILOVER", "LISTING", "MODEL", "REPLICATION",
        "ALIAS", "BRANCH", "SOURCE"
    };

    /** A keyword of live's that opens a kind named by two words, GIT REPOSITORY, which Frostlake reads as a name. */
    private static final String GIT = "GIT";

    /**
     * Words Frostlake's lexer keeps as keywords of its own statements, functions and properties where live's reads a
     * plain name: {@code SHOW FUNCTIONS IN FLATTEN x} misses the class FLATTEN and {@code SHOW TABLES IN FLATTEN x y}
     * is refused at the {@code y}, as for any class and its instance, while every other keyword of Frostlake's is one
     * of live's too, and refuses the {@code x} (live-verified, word by word).
     */
    private static final String[] NAME_WORDS = {
        "ABORT_ALL_QUERIES", "ADMIN_NAME", "ADMIN_PASSWORD", "ADMIN_RSA_PUBLIC_KEY", "ADMIN_USER_TYPE",
        "ALLOWED_VALUES_SEQUENCE", "ALLOW_OVERLAPPING_EXECUTION", "AUTO_INGEST", "AUTO_RESUME", "AUTO_SUSPEND",
        "AUTO_SUSPEND_SECS", "AWS_SNS_TOPIC", "BACKUP_INSTANCE_FAMILIES", "COMPRESSION", "COMPUTE_POOL",
        "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "CURRVAL",
        "DATA_RETENTION_TIME_IN_DAYS", "DATE_FORMAT", "DAYS_TO_EXPIRY", "DEFAULT_NAMESPACE", "DEFAULT_ROLE",
        "DEFAULT_SECONDARY_ROLES", "DEFAULT_WAREHOUSE", "DELIMITER", "DISABLED", "DISPLAY_NAME", "DOWNSTREAM",
        "ECONOMY", "EMAIL",
        "EMBEDDING_MODEL", "ENABLE_QUERY_ACCELERATION", "ENCRYPTION", "EXTERNAL_ACCESS_INTEGRATIONS",
        "FIELD_DELIMITER", "FILE_FORMAT", "FILE_URL", "FIRST_NAME", "FLATTEN", "GENERATION", "GENERATOR", "HANDLER",
        "HEADER", "IDLE_AUTO_SHUTDOWN_TIME_SECONDS", "INCREMENTAL", "INITIALIZE", "INITIALLY_SUSPENDED",
        "INSTANCE_FAMILY", "JAVA", "JAVASCRIPT", "LAST_NAME", "LOCALTIME", "LOCALTIMESTAMP", "LOGIN_NAME",
        "MAIN_FILE", "MASKING_POLICY", "MATCH_BY_COLUMN_NAME", "MAX_BATCH_ROWS", "MAX_CLUSTER_COUNT",
        "MAX_CONCURRENCY_LEVEL", "MAX_FILE_SIZE", "MAX_NODES", "MIDDLE_NAME", "MINS_TO_BYPASS_MFA", "MINS_TO_UNLOCK",
        "MIN_CLUSTER_COUNT", "MIN_NODES",
        "MULTI_STATEMENT_COUNT", "MUST_CHANGE_PASSWORD", "NETWORK_POLICY", "NEXTVAL", "ON_CONFLICT", "ON_CREATE",
        "ON_ERROR", "ON_SCHEDULE", "OTHER", "PATH", "PAUSE", "PLACEMENT_GROUP", "PROPAGATE", "PYTHON",
        "QUERY_ACCELERATION_MAX_SCALE_FACTOR", "QUERY_WAREHOUSE", "READ_ONLY", "RECORD_DELIMITER", "REFRESH_MODE",
        "RESOURCE_CONSTRAINT", "RESOURCE_MONITOR", "RESUME_IF_SUSPENDED", "REVOKE_CURRENT_GRANTS", "ROWCOUNT",
        "ROW_ACCESS_POLICY", "RSA_PUBLIC_KEY", "RSA_PUBLIC_KEY_2", "RUNBOOK", "RUNTIME_NAME", "RUNTIME_VERSION",
        "SCALA", "SCALING_POLICY", "SCHEDULE",
        "SERVERLESS_TASK_MAX_STATEMENT_SIZE", "SESSIONS", "SESSION_POLICY", "SHOW_INITIAL_ROWS", "SINGLE",
        "SIZE_LIMIT", "SKIP_HEADER", "SPLIT_TO_TABLE", "SQL", "STANDARD", "STATEMENT_QUEUED_TIMEOUT_IN_SECONDS",
        "STATEMENT_TIMEOUT_IN_SECONDS", "SUSPEND_ALERT_AFTER_NUM_FAILURES", "SUSPEND_TASK_AFTER_NUM_FAILURES",
        "TARGET_COMPLETION_INTERVAL", "TARGET_LAG", "TASK_AUTO_RETRY_ATTEMPTS", "TIMELIMIT", "TITLE",
        "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE", "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS",
        "USER_TASK_TIMEOUT_MS", "VALIDATION_MODE", "WAIT_FOR_COMPLETION", "WAREHOUSE_SIZE", "WAREHOUSE_TYPE"
    };

    /**
     * Keywords that open a two-word kind live drops, so {@code DROP EXTERNAL y} is refused at the word after the
     * keyword, where the kind's second word should stand (live-verified).
     */
    private static final String[] KIND_PREFIXES = {"EXTERNAL", "FAILOVER", "REPLICATION"};

    /**
     * The plural words live lists kinds by, which its lexer keeps as keywords: {@code DROP SECRETS s} is refused at
     * SECRETS, while {@code SHOW SECRETS} is a listing (live-verified).
     */
    private static final String[] LISTING_WORDS = {"ALERTS", "LISTINGS", "MODELS"};

    private ClassNameWords() {
    }

    /**
     * Whether a word is one of the plural listing words live keeps as keywords.
     *
     * @param word the token
     * @return whether it is such a word
     */
    public static boolean isListingWord(final Token word) {
        if (word == null || word.getType() != FrostlakeLexer.IDENTIFIER) {
            return false;
        }
        for (final String listing : LISTING_WORDS) {
            if (listing.equalsIgnoreCase(word.getText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a word opens a two-word kind live drops, which Frostlake has no statements for.
     *
     * @param word the token
     * @return whether it opens such a kind
     */
    public static boolean opensDropKind(final Token word) {
        // EXTERNAL is a keyword of its own (EXTERNAL VOLUME, EXTERNAL ACCESS integrations); the other prefixes are
        // plain words.
        if (word == null || word.getType() != FrostlakeLexer.IDENTIFIER && word.getType() != FrostlakeLexer.EXTERNAL) {
            return false;
        }
        for (final String prefix : KIND_PREFIXES) {
            if (prefix.equalsIgnoreCase(word.getText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether two words spell one of the two-word kinds live drops that Frostlake has no statements for:
     * {@code EXTERNAL TABLE}, {@code FAILOVER GROUP} or {@code REPLICATION GROUP}.
     *
     * @param first  the kind's first word, or null
     * @param second the token after it, or null
     * @return whether the pair spells such a kind
     */
    public static boolean completesDropKind(final Token first, final Token second) {
        if (!opensDropKind(first) || second == null) {
            return false;
        }
        final boolean external = "EXTERNAL".equalsIgnoreCase(first.getText());
        return second.getType() == (external ? FrostlakeLexer.TABLE : FrostlakeLexer.GROUP);
    }

    /**
     * Whether a word, written alone, can name a class: an unquoted word live's lexer does not keep as a keyword — a
     * plain name to Frostlake's lexer that is none of live's keywords, or one of Frostlake's keywords that live reads
     * as a name.
     *
     * @param word the token
     * @return whether it names a class
     */
    public static boolean isClassWord(final Token word) {
        if (word == null) {
            return false;
        }
        if (word.getType() != FrostlakeLexer.IDENTIFIER) {
            for (final String name : NAME_WORDS) {
                if (name.equalsIgnoreCase(word.getText())) {
                    return true;
                }
            }
            return false;
        }
        for (final String keyword : KEYWORDS) {
            if (keyword.equalsIgnoreCase(word.getText())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a word, written alone, is a name where live takes a plain name and nothing else, as a version's alias:
     * a word that names a class ({@link #isClassWord}) — none of live's keywords, GIT among them — or a quoted name.
     * {@code ADD VERSION LAST FROM …} is refused at the LAST, as are VERSION, COMMENT, NAME and TYPE, while EMAIL and
     * TITLE are aliases (live-verified).
     *
     * @param word the token
     * @return whether it is such a name
     */
    public static boolean isPlainName(final Token word) {
        if (word != null && word.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return true;
        }
        return isClassWord(word) && !GIT.equalsIgnoreCase(word.getText());
    }
}
