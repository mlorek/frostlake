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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.PropertyValueKind;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shapes an integration's URLs and ARNs must have, and the sentences a value out of shape is refused in, all
 * live-verified. An API integration's prefixes follow its provider: a gateway's are that cloud's hosts, the Git
 * provider's any https host with a name, and a list with no provider to read it by is refused at its first URL. The
 * account also reaches for an Azure host and refuses one that does not answer; that cannot be told here, so its shape
 * is all an Azure prefix is held to. The private providers need an edition the measured account lacks, so nothing is
 * known of their prefixes and none is judged.
 */
final class IntegrationValueShapes {

    private static final String REST_OF_GATEWAY_URL = "(?::[0-9]+)?(?:/[^?#]*)?";
    private static final Pattern AWS_GATEWAY = Pattern.compile("[A-Za-z]+://[a-z0-9]+\\.execute-api\\."
        + "([a-z]{2}(?:-gov)?-[a-z]+-[0-9]+)\\.amazonaws\\.com(\\.cn)?" + REST_OF_GATEWAY_URL);
    private static final Pattern GOOGLE_GATEWAY = Pattern.compile("[A-Za-z]+://[a-z0-9-]+\\.[a-z]{2}\\.gateway\\.dev"
        + REST_OF_GATEWAY_URL);
    private static final Pattern AZURE_GATEWAY = Pattern.compile("[A-Za-z]+://[a-z0-9-]+\\.azure-api\\.net"
        + REST_OF_GATEWAY_URL);
    private static final Pattern GIT_URL = Pattern.compile("https://(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?::[0-9]+)?"
        + "(?:[/?].*)?");
    /** What reads as a URL at all; anything else is no value a webhook takes. */
    private static final Pattern ANY_URL = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://.*");
    private static final List<Pattern> WEBHOOKS = Arrays.asList(
        Pattern.compile("(?i:https://hooks\\.slack\\.com)/services/T[A-Z0-9]+/B[A-Z0-9]+/[A-Za-z0-9_]+"),
        Pattern.compile("(?i:https://[a-z0-9-]+\\.webhook\\.office\\.com)/webhookb2/[^/@]+@[^/]+/IncomingWebhook/[^/]+"
            + "/[^/]+"),
        Pattern.compile("(?i:https://events\\.pagerduty\\.com)/v2/enqueue(?:\\?.*)?"));
    private static final Pattern IAM_ROLE = Pattern.compile("arn:aws[a-z0-9-]*:iam::[0-9]+:role(/.*)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SNS_TOPIC = Pattern.compile("arn:(aws[a-z0-9-]*):sns:[a-z0-9-]+:[0-9]+:.+");
    private static final String GOV_PROVIDER = "AWS_GOV_API_GATEWAY";

    private IntegrationValueShapes() {
    }

    /** Whether a text names an IAM role: {@code arn:aws[-partition]:iam::<digits>:role}, with any path after it. */
    static boolean isIamRole(final String arn) {
        return IAM_ROLE.matcher(arn).matches();
    }

    /**
     * Judges the prefixes an API integration's CREATE or ALTER writes: the allowed ones and then the blocked ones, each
     * list in its order, the first one out of shape refused.
     *
     * @param provider   the integration's provider, or null when none is written
     * @param properties the properties written
     * @param region     the account's region, as {@code aws-eu-west-1}
     */
    static void requirePrefixes(final String provider, final Map<String, PropertyValue> properties,
                                final String region) {
        for (final String key : Arrays.asList("API_ALLOWED_PREFIXES", "API_BLOCKED_PREFIXES")) {
            final PropertyValue prefixes = properties.get(key);
            if (prefixes == null || prefixes.getKind() != PropertyValueKind.LIST) {
                continue;
            }
            if (prefixes.getItems().isEmpty()) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter("TOK_CONSTANT_LIST", key));
            }
            for (final PropertyValue prefix : prefixes.getItems()) {
                if (prefix.getText() != null) {
                    requirePrefix(provider, prefix.getText(), region);
                }
            }
        }
    }

    private static void requirePrefix(final String provider, final String url, final String region) {
        if (provider == null || "GIT_HTTPS_API".equals(provider)) {
            if (provider == null || !GIT_URL.matcher(url).matches()) {
                throw new RuntimeException(SqlCompilationError.inline("URL '" + url + "' is invalid or not allowed."));
            }
        } else if ("AWS_API_GATEWAY".equals(provider) || GOV_PROVIDER.equals(provider)) {
            requireAwsGateway(provider, url, region);
        } else if ("GOOGLE_API_GATEWAY".equals(provider) && !GOOGLE_GATEWAY.matcher(url.trim()).matches()) {
            throw notAGateway(url, "Google API Gateway");
        } else if ("AZURE_API_MANAGEMENT".equals(provider) && !AZURE_GATEWAY.matcher(url.trim()).matches()) {
            throw notAGateway(url, "Azure API Management");
        }
    }

    /** An Amazon gateway's host names its region, which must be one of the provider's partition. */
    private static void requireAwsGateway(final String provider, final String url, final String accountRegion) {
        final boolean gov = GOV_PROVIDER.equals(provider);
        final Matcher gateway = AWS_GATEWAY.matcher(url.trim());
        if (!gateway.matches()) {
            throw notAGateway(url, gov ? "Amazon Gov API Gateway" : "Amazon API Gateway");
        }
        final String region = gateway.group(1);
        final boolean ofGov = region.contains("-gov-");
        if (!gov && gateway.group(2) != null) {
            throw new RuntimeException(SqlCompilationError.of("'" + region + "' in URL '" + url
                + "' is not supported in current region '" + accountRegion + "'."));
        }
        if (gov != ofGov || region.startsWith("cn-")) {
            throw new RuntimeException(SqlCompilationError.of("'" + region + "' in URL '" + url
                + "' is not a supported cloud region."));
        }
    }

    private static RuntimeException notAGateway(final String url, final String service) {
        return new RuntimeException(SqlCompilationError.of("URL '" + url + "' is invalid. Make sure the URL host is"
            + " reachable and that the URL is for " + service + " service."));
    }

    /**
     * Judges what a notification integration's CREATE or ALTER writes of a webhook's URL and an SNS topic and role. A
     * webhook goes to Slack, Teams or PagerDuty alone, on the paths those services hand out.
     *
     * @param properties the properties written
     */
    static void requireNotificationTargets(final Map<String, PropertyValue> properties) {
        final PropertyValue webhook = properties.get("WEBHOOK_URL");
        if (webhook != null) {
            requireWebhook(webhook);
        }
        final PropertyValue topic = properties.get("AWS_SNS_TOPIC_ARN");
        if (topic != null) {
            requireSnsTopic(topic);
        }
        final PropertyValue role = properties.get("AWS_SNS_ROLE_ARN");
        if (role != null && (role.getText() == null || !isIamRole(role.getText()))) {
            throw IntegrationRules.invalidValue("AWS_SNS_ROLE_ARN", role);
        }
    }

    private static void requireWebhook(final PropertyValue webhook) {
        final String url = webhook.getText();
        if (url == null || !ANY_URL.matcher(url).matches()) {
            throw IntegrationRules.invalidValue("WEBHOOK_URL", webhook);
        }
        for (final Pattern service : WEBHOOKS) {
            if (service.matcher(url).matches()) {
                return;
            }
        }
        throw new RuntimeException("WEBHOOK_URL: " + url
            + " is invalid. Verify whether secret replacement makes it invalid");
    }

    private static void requireSnsTopic(final PropertyValue topic) {
        final Matcher arn = topic.getText() == null ? null : SNS_TOPIC.matcher(topic.getText());
        if (arn == null || !arn.matches()) {
            throw IntegrationRules.invalidValue("AWS_SNS_TOPIC_ARN", topic);
        }
        if (arn.group(1).toLowerCase(Locale.ROOT).endsWith("-cn")) {
            throw new RuntimeException("AWS China resources are supported only in China Snowflake deployments.");
        }
    }
}
