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

package dev.frostlake.executor.expressions;

import java.text.Collator;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A collation specification — the text COLLATE names in a column definition or an expression — parsed
 * into the rules a string comparison runs by.
 *
 * <p>The text is hyphen-separated specifiers, read case-insensitively and reported lower-cased:
 * {@code COLLATE 'EN-CI'} is {@code 'en-ci'} to COLLATION, DESCRIBE and GET_DDL. A LOCALE may come
 * first and is any word that is not an option — {@code 'xx'}, {@code 'bogus'} and {@code 'fr_CA'} all
 * parse, an unknown one comparing by the root rules. Without one the collation is the UTF8
 * pseudo-locale (spelled {@code utf8} or {@code bin} as well), which compares code units. The options:
 *
 * <pre>
 *   cs / ci                  case-sensitive (the default) or not
 *   as / ai                  accent-sensitive (the default) or not
 *   ps / pi                  punctuation-sensitive or not
 *   fl / fu                  lower- or upper-case first when two differ only in case
 *   upper / lower            convert the case before comparing
 *   trim / ltrim / rtrim     drop leading and/or trailing spaces before comparing
 * </pre>
 *
 * <p>The refusals, all live-verified and all worded {@code Invalid value '<spec>' for COLLATION.
 * Reason: ...}, come in this order: an unknown option, the first in the text ({@code Unknown option:
 * zz} — and a locale anywhere but first is one); {@code upper} beside {@code lower}; and a
 * sensitivity option without a locale ({@code Case sensitivity option not allowed for the UTF8
 * collation}, then accent, punctuation and upper/lower preference). Repeating or contradicting a
 * sensitivity option is no refusal — the last one wins — while the trims add up. Empty specifiers
 * at the END are dropped, so {@code '-'} is the UTF8 collation and {@code 'en-ci-'} is
 * {@code 'en-ci'}; one anywhere else is an unknown option, or no locale when it comes first.
 *
 * <p>A locale compares through the JDK's {@link Collator}, whose letter order, case tiers and accents
 * agree with the account's: {@code a < A < b} under {@code 'en'}, {@code ä} after {@code z} under
 * {@code 'sv'}, {@code 'é' = 'e'} only under {@code ai}. Punctuation and spaces weigh differently in
 * the two rule sets, and live's case-insensitive level also folds full-width forms and the
 * non-breaking space, which the JDK's does not.
 */
public final class CollationSpec {

    private static final int CACHE_CAPACITY = 256;
    private static final Map<String, CollationSpec> PARSED = new ConcurrentHashMap<>();

    /** The modifier the JDK's French rules end with, which sorts accents from the END of the string. */
    private static final String BACKWARD_ACCENTS = "@";
    /** A weight of their own for the two separators the JDK's default rules treat as accent-level. */
    private static final String SEPARATORS_FIRST = "<' '<'-'";
    /** The JDK's accent-level entries for those two separators, dropped before they are re-weighted. */
    private static final String IGNORABLE_SPACE = ";' '";
    private static final String IGNORABLE_HYPHEN = ";'-'";

    private static final String REFUSAL_HEAD = "Invalid value '";
    private static final String REFUSAL_MIDDLE = "' for COLLATION. Reason: ";

    private final String text;
    private final boolean caseInsensitive;
    private final boolean accentInsensitive;
    private final boolean punctuationInsensitive;
    private final boolean upperFirst;
    private final boolean toUpper;
    private final boolean toLower;
    private final boolean trimLeading;
    private final boolean trimTrailing;
    private final Collator collator;
    private final Collator caseBlind;
    private final Collator baseLetters;

    private CollationSpec(final String text) {
        this.text = text;
        final String[] parts = text.split("-");
        int first = 0;
        String locale = null;
        if (parts.length > 0 && !isOption(parts[0])) {
            locale = parts[0];
            first = 1;
        }
        boolean ci = false;
        boolean ai = false;
        boolean pi = false;
        boolean fu = false;
        boolean upper = false;
        boolean lower = false;
        boolean ltrim = false;
        boolean rtrim = false;
        boolean caseGiven = false;
        boolean accentGiven = false;
        boolean punctuationGiven = false;
        boolean preferenceGiven = false;
        for (int i = first; i < parts.length; i++) {
            final String part = parts[i];
            if ("cs".equals(part) || "ci".equals(part)) {
                caseGiven = true;
                ci = "ci".equals(part);
            } else if ("as".equals(part) || "ai".equals(part)) {
                accentGiven = true;
                ai = "ai".equals(part);
            } else if ("ps".equals(part) || "pi".equals(part)) {
                punctuationGiven = true;
                pi = "pi".equals(part);
            } else if ("fl".equals(part) || "fu".equals(part)) {
                preferenceGiven = true;
                fu = "fu".equals(part);
            } else if ("upper".equals(part)) {
                upper = true;
            } else if ("lower".equals(part)) {
                lower = true;
            } else if ("trim".equals(part)) {
                ltrim = true;
                rtrim = true;
            } else if ("ltrim".equals(part)) {
                ltrim = true;
            } else if ("rtrim".equals(part)) {
                rtrim = true;
            } else {
                throw refusal(text, "Unknown option: " + part);
            }
        }
        if (upper && lower) {
            throw refusal(text, "Options 'upper' and 'lower' are mutually exclusive");
        }
        final boolean utf8 = locale == null || locale.isEmpty() || "utf8".equals(locale) || "bin".equals(locale);
        if (utf8) {
            if (caseGiven) {
                throw refusal(text, "Case sensitivity option not allowed for the UTF8 collation");
            }
            if (accentGiven) {
                throw refusal(text, "Accent sensitivity option not allowed for the UTF8 collation");
            }
            if (punctuationGiven) {
                throw refusal(text, "Punctuation sensitivity option not allowed for the UTF8 collation");
            }
            if (preferenceGiven) {
                throw refusal(text, "Upper/lower preference option not allowed for the UTF8 collation");
            }
        }
        this.caseInsensitive = ci;
        this.accentInsensitive = ai;
        this.punctuationInsensitive = pi;
        this.upperFirst = fu;
        this.toUpper = upper;
        this.toLower = lower;
        this.trimLeading = ltrim;
        this.trimTrailing = rtrim;
        if (utf8) {
            this.collator = null;
            this.caseBlind = null;
            this.baseLetters = null;
            return;
        }
        final Collator rules = tailored(Locale.forLanguageTag(locale.trim().replace('_', '-')));
        if (ci) {
            rules.setStrength(ai ? Collator.PRIMARY : Collator.SECONDARY);
        } else {
            rules.setStrength(Collator.TERTIARY);
        }
        this.collator = rules;
        if (fu && !ci) {
            final Collator letters = (Collator) rules.clone();
            letters.setStrength(Collator.SECONDARY);
            this.caseBlind = letters;
        } else {
            this.caseBlind = null;
        }
        if (ai && !ci) {
            // Accent-insensitive but case-sensitive is a comparison of BASE LETTERS with the case still
            // told apart. Stripping the marks and comparing at full strength is not the same rule: a
            // letter a locale tailors as its own (Polish a-ogonek) differs from its base at the FIRST
            // level, which stripping erases, and an expansion (German sharp s to ss) is one letter at
            // that level, which stripping keeps apart.
            final Collator letters = (Collator) rules.clone();
            letters.setStrength(Collator.PRIMARY);
            this.baseLetters = letters;
        } else {
            this.baseLetters = null;
        }
    }

    /**
     * The locale's collator, tailored where the JDK's default rules part from the ordering Snowflake
     * uses. Two tailorings, both live-measured: a space and a hyphen carry a weight of their own, BELOW
     * every letter and digit, where the JDK makes them a mere accent-level difference ('a b' sorts
     * before 'ab', and 'a-b' before 'ab'); and accents are compared FORWARD, from the start of the
     * string, in every locale — the JDK still marks French as sorting them backwards, which puts
     * 'c&ocirc;te' before 'cot&eacute;' where Snowflake puts it after. A locale whose rules cannot be
     * re-parsed keeps its untailored collator.
     */
    private static Collator tailored(final Locale locale) {
        final Collator base = Collator.getInstance(locale);
        if (!(base instanceof RuleBasedCollator)) {
            return base;
        }
        String rules = ((RuleBasedCollator) base).getRules();
        if (rules.endsWith(BACKWARD_ACCENTS)) {
            rules = rules.substring(0, rules.length() - BACKWARD_ACCENTS.length());
        }
        final int firstPrimary = firstPrimaryRule(rules);
        if (firstPrimary < 0) {
            return base;
        }
        // The re-weighting goes where the FIRST primary letter is declared, never at the front: the
        // rules open with the ignorable and accent-level entries, and a letter placed before them
        // would make every one of them a variant of THAT letter instead.
        final String head = rules.substring(0, firstPrimary)
            .replace(IGNORABLE_SPACE, "").replace(IGNORABLE_HYPHEN, "");
        try {
            return new RuleBasedCollator(head + SEPARATORS_FIRST + rules.substring(firstPrimary));
        } catch (final ParseException untailorable) {
            return base;
        }
    }

    /** Where a rule set declares its first primary difference — the first unquoted {@code <}. */
    private static int firstPrimaryRule(final String rules) {
        boolean quoted = false;
        for (int i = 0; i < rules.length(); i++) {
            final char at = rules.charAt(i);
            if (at == '\'') {
                quoted = !quoted;
            } else if (at == '<' && !quoted) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The rules {@code spec} names, refusing a specification live refuses.
     *
     * @param spec the specification as written, in any case
     * @return its rules
     */
    public static CollationSpec parse(final String spec) {
        final String text = spec.toLowerCase(Locale.ROOT);
        final CollationSpec known = PARSED.get(text);
        if (known != null) {
            return known;
        }
        final CollationSpec parsed = new CollationSpec(text);
        if (PARSED.size() >= CACHE_CAPACITY) {
            PARSED.clear();
        }
        PARSED.put(text, parsed);
        return parsed;
    }

    /**
     * Whether {@code message} is this class's refusal, which a caller swallowing ordinary failures
     * must let through: it is worded as neither a compilation nor an execution error.
     *
     * @param message a failure's message
     * @return whether it refuses a collation specification
     */
    public static boolean isRefusal(final String message) {
        return message != null && message.startsWith(REFUSAL_HEAD) && message.contains(REFUSAL_MIDDLE);
    }

    /** The specification lower-cased, the spelling COLLATION reports. */
    public String getText() {
        return text;
    }

    /** Whether the comparison ignores case. */
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    /** Whether LIKE and ILIKE can match under these rules: not when they ignore accents or punctuation. */
    public boolean supportsLike() {
        return !accentInsensitive && !punctuationInsensitive;
    }

    /** Whether LIKE ANY and LIKE ALL can: only without a locale. */
    public boolean supportsLikeAnyAll() {
        return collator == null;
    }

    /**
     * Orders two strings.
     *
     * @param left  the first string
     * @param right the second
     * @return negative, zero or positive, as {@code left} sorts before, with or after {@code right}
     */
    public int compare(final String left, final String right) {
        final String a = comparable(left);
        final String b = comparable(right);
        if (collator == null) {
            return a.compareTo(b);
        }
        if (baseLetters != null) {
            final int letters = baseLetters.compare(a, b);
            return letters != 0 ? letters : Integer.compare(caseSignature(a).compareTo(caseSignature(b)), 0);
        }
        if (caseBlind != null) {
            final int letters = caseBlind.compare(a, b);
            return letters != 0 ? letters : -collator.compare(a, b);
        }
        return collator.compare(a, b);
    }

    /**
     * A value as the comparison sees it: converted as the options say, then folded to the form the
     * collation's own strength reads. A collation that ignores case or accents also ignores WIDTH and
     * the difference between a space and a non-breaking one — both live-measured, and both compatibility
     * differences that the compatibility normal form erases; a fully sensitive collation keeps them.
     */
    private String comparable(final String value) {
        String text = converted(value);
        if (collator == null) {
            return text;
        }
        if (caseInsensitive || accentInsensitive) {
            text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        }
        if (punctuationInsensitive) {
            text = lettersAndDigits(text);
        }
        return text;
    }

    /**
     * The case pattern a case-sensitive, accent-insensitive comparison falls back on when the base
     * letters are equal: {@code L} when every cased character is lower, {@code U} when every one is
     * upper, the empty string when none is cased, and the per-character pattern for a mixture. Uniform
     * case is one symbol whatever the LENGTH, so a letter and the pair it expands to (German sharp s
     * against ss) still compare equal, while a case difference at any position still tells them apart.
     */
    private static String caseSignature(final String value) {
        final StringBuilder perCharacter = new StringBuilder();
        boolean anyLower = false;
        boolean anyUpper = false;
        for (int i = 0; i < value.length(); i++) {
            final char at = value.charAt(i);
            if (Character.isLowerCase(at)) {
                perCharacter.append('0');
                anyLower = true;
            } else if (Character.isUpperCase(at)) {
                perCharacter.append('1');
                anyUpper = true;
            }
        }
        if (perCharacter.length() == 0) {
            return "";
        }
        if (anyLower && anyUpper) {
            return perCharacter.toString();
        }
        return anyUpper ? "U" : "L";
    }

    /**
     * The key two strings share exactly when these rules call them equal — what a grouping, a DISTINCT
     * or a set operation buckets by. Derived from the same conversions {@link #compare} applies, so
     * two values land in one bucket precisely when comparing them returns zero.
     *
     * @param value the string
     * @return an object equal to another string's key exactly when the two compare equal
     */
    public Object equalityKey(final String value) {
        final String text = comparable(value);
        if (collator == null) {
            return text;
        }
        if (baseLetters != null) {
            return List.of(baseLetters.getCollationKey(text), caseSignature(text));
        }
        return collator.getCollationKey(text);
    }

    /**
     * A LIKE operand, subject or pattern, as the match must see it: trimmed and case-converted as the
     * rules say, and folded to lower case when they ignore case.
     *
     * @param value the operand's text
     * @return the text to match
     */
    public String likeOperand(final String value) {
        final String converted = converted(value);
        return caseInsensitive ? converted.toLowerCase(Locale.ROOT) : converted;
    }

    private String converted(final String value) {
        String result = value;
        if (trimLeading) {
            int start = 0;
            while (start < result.length() && result.charAt(start) == ' ') {
                start++;
            }
            result = result.substring(start);
        }
        if (trimTrailing) {
            int end = result.length();
            while (end > 0 && result.charAt(end - 1) == ' ') {
                end--;
            }
            result = result.substring(0, end);
        }
        if (toUpper) {
            return result.toUpperCase(Locale.ROOT);
        }
        return toLower ? result.toLowerCase(Locale.ROOT) : result;
    }

    private static String lettersAndDigits(final String value) {
        final StringBuilder kept = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            final int codePoint = value.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) {
                kept.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return kept.toString();
    }

    private static String withoutAccents(final String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static boolean isOption(final String part) {
        return "cs".equals(part) || "ci".equals(part) || "as".equals(part) || "ai".equals(part)
            || "ps".equals(part) || "pi".equals(part) || "fl".equals(part) || "fu".equals(part)
            || "upper".equals(part) || "lower".equals(part) || "trim".equals(part)
            || "ltrim".equals(part) || "rtrim".equals(part);
    }

    private static RuntimeException refusal(final String text, final String reason) {
        return new RuntimeException(REFUSAL_HEAD + text + REFUSAL_MIDDLE + reason);
    }
}
