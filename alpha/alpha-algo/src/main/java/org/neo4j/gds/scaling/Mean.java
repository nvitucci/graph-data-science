/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.scaling;

import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.utils.partition.PartitionUtils;

import java.util.concurrent.ExecutorService;

final class Mean implements Scaler {

    private final NodeProperties properties;
    final double avg;
    final double maxMinDiff;

    private Mean(NodeProperties properties, double avg, double maxMinDiff) {
        this.properties = properties;
        this.avg = avg;
        this.maxMinDiff = maxMinDiff;
    }

    static Mean create(NodeProperties properties, long nodeCount, int concurrency, ExecutorService executor) {
        if (nodeCount == 0) {
            return new Mean(properties, 0, 0);
        }

        var tasks = PartitionUtils.rangePartition(
            concurrency,
            nodeCount,
            partition -> new ComputeAggregates(partition.startNode(), partition.nodeCount(), properties)
        );

        ParallelUtil.runWithConcurrency(concurrency, tasks, executor);

        var min = tasks.stream().mapToDouble(ComputeAggregates::min).min().orElse(Double.MAX_VALUE);
        var max = tasks.stream().mapToDouble(ComputeAggregates::max).max().orElse(Double.MIN_VALUE);
        var sum = tasks.stream().mapToDouble(ComputeAggregates::sum).sum();

        return new Mean(properties, sum / nodeCount, max - min);
    }

    @Override
    public double scaleProperty(long nodeId) {
        if (Math.abs(maxMinDiff) < CLOSE_TO_ZERO) {
            return 0D;
        }
        return (properties.doubleValue(nodeId) - avg) / maxMinDiff;
    }

    static class ComputeAggregates implements Runnable {

        private final long start;
        private final long length;
        private final NodeProperties properties;
        private double max;
        private double min;
        private double sum;

        ComputeAggregates(long start, long length, NodeProperties property) {
            this.start = start;
            this.length = length;
            this.properties = property;
            this.min = Double.MAX_VALUE;
            this.max = Double.MIN_VALUE;
            this.sum = 0D;
        }

        @Override
        public void run() {
            for (long nodeId = start; nodeId < (start + length); nodeId++) {
                var propertyValue = properties.doubleValue(nodeId);
                sum += propertyValue;
                if (propertyValue < min) {
                    min = propertyValue;
                }
                if (propertyValue > max) {
                    max = propertyValue;
                }
            }
        }

        double max() {
            return max;
        }

        double min() {
            return min;
        }

        double sum() {
            return sum;
        }
    }

}
