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
package org.neo4j.gds.similarity.knn.metrics;

import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.nodeproperties.ValueType;

final class DoubleArrayPropertySimilarityComputer implements SimilarityComputer {
    private final NodeProperties nodeProperties;
    private final DoubleArraySimilarityMetric metric;

    DoubleArrayPropertySimilarityComputer(NodeProperties nodeProperties, DoubleArraySimilarityMetric metric) {
        this.metric = metric;
        if (nodeProperties.valueType() != ValueType.DOUBLE_ARRAY) {
            throw new IllegalArgumentException("The property is not of type DOUBLE_ARRAY");
        }
        this.nodeProperties = nodeProperties;
    }

    @Override
    public double similarity(long firstNodeId, long secondNodeId) {
        var left = nodeProperties.doubleArrayValue(firstNodeId);
        var right = nodeProperties.doubleArrayValue(secondNodeId);
        return metric.compute(left, right);
    }
}
