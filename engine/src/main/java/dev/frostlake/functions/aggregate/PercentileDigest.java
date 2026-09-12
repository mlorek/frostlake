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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The percentile digest behind APPROX_PERCENTILE_ACCUMULATE, _COMBINE and _ESTIMATE: a list of
 * centroids (mean, weight) serialised as {@code {"state":[mean, weight, mean, weight, …],
 * "type":"tdigest","version":1}}, means ascending, every number in the fifteen-decimal exponent form
 * a DOUBLE takes inside a VARIANT. The engine never compresses: every input value is its own centroid
 * of weight 1, exactly what the account returns below roughly a thousand values, where it begins to
 * merge neighbours.
 *
 * <p>The estimate, live-verified in both regimes: over centroids that ALL weigh 1 it is the exact
 * interpolated percentile — position {@code fraction × (n − 1)} between the sorted values, the same
 * number APPROX_PERCENTILE gives — while a state holding any other weight is read as a digest: the
 * target rank {@code fraction × total weight} is placed among the centroids' mean positions
 * ({@code weight before + weight / 2}) and interpolated linearly, extrapolated along the end segments
 * past the first or last centroid, so {@code [1 ×1, 9 ×3]} estimates 3 at 0.25, 0.6 at 0.1 and 13.4
 * at 0.9. A single centroid is its own mean; an empty digest estimates NULL.
 */
public class PercentileDigest {

    /** The {@code type} member a state carries. */
    public static final String STATE_TYPE = "tdigest";

    private static final String NOT_A_STATE =
        "First argument of the function must be an object which maps the key 'state' to an array";

    private final List<Double> means = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    public boolean isEmpty() {
        return means.isEmpty();
    }

    /** Adds one input value as a centroid of weight 1. */
    public void add(final double value) {
        addCentroid(value, 1.0);
    }

    public void addCentroid(final double mean, final double weight) {
        means.add(Double.valueOf(mean));
        weights.add(Double.valueOf(weight));
    }

    /** Takes every centroid of another digest. */
    public void absorb(final PercentileDigest other) {
        means.addAll(other.means);
        weights.addAll(other.weights);
    }

    /**
     * Reads a state, or refuses the argument as live does when it is not one.
     *
     * @param value the argument
     * @return the digest it holds
     */
    public static PercentileDigest parse(final Object value) {
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node == null || !node.isObject()) {
            throw new RuntimeException(NOT_A_STATE);
        }
        final JsonNode state = node.get("state");
        if (state == null || !state.isArray()) {
            throw new RuntimeException(NOT_A_STATE);
        }
        final PercentileDigest digest = new PercentileDigest();
        for (int i = 0; i < state.size(); i += 2) {
            final JsonNode mean = state.get(i);
            final JsonNode weight = i + 1 < state.size() ? state.get(i + 1) : null;
            if (mean == null || !mean.isNumber() || (weight != null && !weight.isNumber())) {
                throw new RuntimeException(NOT_A_STATE);
            }
            digest.addCentroid(mean.doubleValue(), weight == null ? 1.0 : weight.doubleValue());
        }
        return digest;
    }

    /** The state as an OBJECT value, centroids sorted by mean. */
    public VariantValue toState() {
        final ArrayNode state = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final int index : sortedIndexes()) {
            state.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, means.get(index)));
            state.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, weights.get(index)));
        }
        final ObjectNode object = ArrayFunctionHelper.MAPPER.createObjectNode();
        object.set("state", state);
        object.put("type", STATE_TYPE);
        object.put("version", 1);
        return ArrayFunctionHelper.toCanonicalVariant(object);
    }

    /**
     * The estimated value at a fraction between 0 and 1, or null over an empty digest.
     *
     * @param fraction the percentile
     * @return the estimate
     */
    public Double estimate(final double fraction) {
        if (means.isEmpty()) {
            return null;
        }
        final List<Integer> order = sortedIndexes();
        final int count = order.size();
        boolean allUnit = true;
        double total = 0;
        for (final int index : order) {
            final double weight = weights.get(index).doubleValue();
            allUnit = allUnit && weight == 1.0;
            total += weight;
        }
        if (count == 1) {
            return means.get(order.get(0));
        }
        if (allUnit) {
            final double position = fraction * (count - 1);
            final int low = (int) Math.floor(position);
            final int high = Math.min(low + 1, count - 1);
            final double lowMean = means.get(order.get(low)).doubleValue();
            final double highMean = means.get(order.get(high)).doubleValue();
            return Double.valueOf(lowMean * (1 - (position - low)) + highMean * (position - low));
        }
        final double[] positions = new double[count];
        double before = 0;
        for (int i = 0; i < count; i++) {
            final double weight = weights.get(order.get(i)).doubleValue();
            positions[i] = before + weight / 2;
            before += weight;
        }
        final double target = fraction * total;
        int left = 0;
        while (left < count - 2 && target > positions[left + 1]) {
            left++;
        }
        final double leftMean = means.get(order.get(left)).doubleValue();
        final double rightMean = means.get(order.get(left + 1)).doubleValue();
        final double span = positions[left + 1] - positions[left];
        if (span == 0) {
            return Double.valueOf(leftMean);
        }
        return Double.valueOf(leftMean + (target - positions[left]) / span * (rightMean - leftMean));
    }

    /**
     * A value an accumulating call was given, as the double a centroid records — a number by its
     * value, a VARIANT by its member, text by what it spells — or the row-time refusal live gives.
     *
     * @param value the input, not null
     * @return the double
     */
    public static double numericInput(final Object value) {
        if (value instanceof Double || value instanceof Float) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).doubleValue();
        }
        if (value instanceof VariantValue) {
            final JsonNode member = ((VariantValue) value).node();
            if (member != null && member.isNumber()) {
                return member.doubleValue();
            }
            if (member != null && member.isTextual()) {
                final BigDecimal spelled = spelledNumber(member.asText());
                if (spelled != null) {
                    return spelled.doubleValue();
                }
            }
            throw new RuntimeException("Failed to cast variant value " + ((VariantValue) value).text() + " to REAL");
        }
        final String text = String.valueOf(value);
        final BigDecimal spelled = spelledNumber(text);
        if (spelled == null) {
            throw new RuntimeException("Numeric value '" + text + "' is not recognized");
        }
        return spelled.doubleValue();
    }

    private static BigDecimal spelledNumber(final String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }

    /** The centroid indexes by ascending mean, equal means in insertion order. */
    private List<Integer> sortedIndexes() {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < means.size(); i++) {
            order.add(Integer.valueOf(i));
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer left, final Integer right) {
                return Double.compare(means.get(left).doubleValue(), means.get(right).doubleValue());
            }
        });
        return order;
    }
}
