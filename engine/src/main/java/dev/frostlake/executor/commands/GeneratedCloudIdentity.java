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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The identity an account generates for an object that reaches into a cloud account: the IAM user the account acts as,
 * one for every object of the account, and an external ID of the object's own, which the cloud role's trust policy
 * names. DESCRIBE shows both beside the role an integration or a storage location was given (live-verified:
 * {@code API_AWS_IAM_USER_ARN} and {@code API_AWS_EXTERNAL_ID}, the {@code GLUE_AWS_}, the {@code SF_AWS_} and the
 * {@code STORAGE_AWS_} pairs; for Google Cloud a service account, {@code API_GCP_SERVICE_ACCOUNT},
 * {@code GCP_PUBSUB_SERVICE_ACCOUNT} and {@code STORAGE_GCP_SERVICE_ACCOUNT}). Frostlake reaches no cloud account, so
 * the user is a fixed one and the external ID is derived from the object's kind, its name and the creation it comes
 * from: an ID of the account's shape, {@code <ACCOUNT>_SFCRole=1_<28 characters>}, that stays what it is for as long as
 * the object stands — through an ALTER and through a DROP undone — and is another once the object is created again, by
 * CREATE OR REPLACE or after a DROP (live-verified).
 */
final class GeneratedCloudIdentity {

    /** The IAM user every object of the account is reached as. */
    static final String USER_ARN = "arn:aws:iam::000000000000:user/frostlake";

    /** The Google Cloud service account every object of the account is reached as. */
    static final String SERVICE_ACCOUNT = "frostlake@frostlake.iam.gserviceaccount.com";

    /**
     * The application an Azure tenant consents to for every object of the account, of the account's shape: 21
     * lower-case letters or digits holding {@code snowflake}, an underscore and 13 digits (live-verified).
     */
    static final String APPLICATION_NAME = "frostysnowflakepacint_0000000000000";

    /** The application's client id in the consent URL. */
    private static final String APPLICATION_ID = "00000000-0000-0000-0000-000000000000";

    private static final String ACCOUNT = "FROSTLAKE";

    private GeneratedCloudIdentity() {
    }

    /**
     * The URL an Azure tenant's administrator visits to consent to the account's application.
     *
     * @param tenant the tenant's id, as the integration was written with it
     * @return the URL
     */
    static String consentUrl(final String tenant) {
        return "https://login.microsoftonline.com/" + (tenant == null ? "" : tenant)
            + "/oauth2/authorize?client_id=" + APPLICATION_ID + "&response_type=code";
    }

    /**
     * The external ID of one object.
     *
     * @param account the account's locator, or null
     * @param kind    the kind of object the ID is generated for
     * @param name    the object's name
     * @param seed    what tells this creation of the object from another, or null when none was kept
     * @return the ID
     */
    static String externalId(final String account, final String kind, final String name, final String seed) {
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-1").digest((kind + ':' + name + (seed == null ? "" : ':' + seed))
                .getBytes(StandardCharsets.UTF_8));
        } catch (final NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
        return (account == null ? ACCOUNT : account) + "_SFCRole=1_" + Base64.getEncoder().encodeToString(digest);
    }
}
