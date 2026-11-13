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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SecurityPropertySpec;
import dev.frostlake.metastore.model.SecurityPropertySpecs;
import dev.frostlake.metastore.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The password policy a new password is held to (live-verified). It is the user's own policy, else the account's,
 * else the built-in one, whose rules are a policy's defaults: at least 14 characters and at most 256, a digit, an
 * upper and a lower case letter, the last five passwords not used again.
 *
 * <p>The rules are judged in a fixed order and the first unmet one is named: MIN_LENGTH, MAX_LENGTH, MIN_DIGITS,
 * MIN_UPPERCASE, MIN_LOWERCASE, MIN_SPECIAL, then MIN_AGE and PRIOR_USE (the current password, or one of the
 * PASSWORD_HISTORY held before it). Digits and letters count in ASCII alone, and a special character is ASCII
 * punctuation: a space, a tab or an accented letter is none of them.
 *
 * <p>Only a password that replaces one is judged: CREATE USER, and ALTER USER … SET PASSWORD over a user without a
 * password, take any. An empty password takes the user's password off, and is not judged either.
 */
final class PasswordPolicyCheck {

    /** How an ALTER USER's refusal opens. */
    private static final String NEW_PASSWORD = "SQL execution error:\nNew password rejected by current password "
        + "policy. Reason: '";

    /** The punctuation a policy counts as special characters. */
    private static final String SPECIAL = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    private PasswordPolicyCheck() {
    }

    /**
     * Refuses the password an ALTER USER … SET PASSWORD gives a user, when the policy that governs the user does not
     * take it.
     *
     * @param catalog  the catalog, for the policy that governs the user
     * @param user     the user
     * @param password the new password
     */
    static void requireAccepted(final Catalog catalog, final User user, final String password) {
        if (password == null || password.isEmpty() || !user.hasPassword()) {
            return;
        }
        final SecurityObject policy = SecurityObjectCommandHandler.passwordPolicyOf(catalog, user.getName());
        final String reason = unmetRule(policy, password, user);
        if (reason != null) {
            throw new RuntimeException(NEW_PASSWORD + reason + "'");
        }
    }

    /**
     * Refuses the administrator's password a CREATE MANAGED ACCOUNT gives the new account, which the built-in policy
     * governs, in that statement's own words.
     *
     * @param password the password
     */
    static void requireAcceptedForNewAccount(final String password) {
        final String reason = unmetRule(null, password, null);
        if (reason != null) {
            throw new RuntimeException("Proposed password rejected by current password policy. Reason: " + reason
                + ".");
        }
    }

    /** The first rule of the policy the password does not meet, or null when it meets them all. */
    private static String unmetRule(final SecurityObject policy, final String password, final User user) {
        final int length = password.codePointCount(0, password.length());
        int digits = 0;
        int upper = 0;
        int lower = 0;
        int special = 0;
        for (int i = 0; i < password.length(); i++) {
            final char c = password.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c >= 'A' && c <= 'Z') {
                upper++;
            } else if (c >= 'a' && c <= 'z') {
                lower++;
            } else if (SPECIAL.indexOf(c) >= 0) {
                special++;
            }
        }
        if (length < rule(policy, "PASSWORD_MIN_LENGTH")) {
            return "MIN_LENGTH";
        }
        if (length > rule(policy, "PASSWORD_MAX_LENGTH")) {
            return "MAX_LENGTH";
        }
        if (digits < rule(policy, "PASSWORD_MIN_NUMERIC_CHARS")) {
            return "MIN_DIGITS";
        }
        if (upper < rule(policy, "PASSWORD_MIN_UPPER_CASE_CHARS")) {
            return "MIN_UPPERCASE";
        }
        if (lower < rule(policy, "PASSWORD_MIN_LOWER_CASE_CHARS")) {
            return "MIN_LOWERCASE";
        }
        if (special < rule(policy, "PASSWORD_MIN_SPECIAL_CHARS")) {
            return "MIN_SPECIAL";
        }
        if (user != null && tooSoon(user, rule(policy, "PASSWORD_MIN_AGE_DAYS"))) {
            return "MIN_AGE";
        }
        if (user != null && usedBefore(user, password, rule(policy, "PASSWORD_HISTORY"))) {
            return "PRIOR_USE";
        }
        return null;
    }

    /** Whether the password is the user's current one, or one of the {@code history} held before it. */
    private static boolean usedBefore(final User user, final String password, final long history) {
        if (password.equals(user.getPassword())) {
            return true;
        }
        final List<String> previous = user.getPreviousPasswords();
        for (int i = previous.size() - 1; i >= 0 && previous.size() - i <= history; i--) {
            if (password.equals(previous.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the user's current password was set less than {@code days} days ago. */
    private static boolean tooSoon(final User user, final long days) {
        final Instant setAt = user.getPasswordSetAt();
        return days > 0 && setAt != null && StatementClock.instant().isBefore(setAt.plus(Duration.ofDays(days)));
    }

    /** A rule's value: the policy's own where it set one, else the built-in default. */
    private static long rule(final SecurityObject policy, final String name) {
        final String own = policy == null ? null : policy.text(name);
        if (own != null) {
            return Long.parseLong(own.trim());
        }
        for (final SecurityPropertySpec spec : SecurityPropertySpecs.of(SecurityObjectKind.PASSWORD_POLICY)) {
            if (spec.getName().equals(name)) {
                return Long.parseLong(spec.getDefaultValue());
            }
        }
        throw new IllegalStateException("No password policy rule " + name);
    }
}
