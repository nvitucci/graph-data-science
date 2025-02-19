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
package org.neo4j.gds.ml.metrics;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.ml.models.TrainerConfig;

import java.util.List;
import java.util.Map;

@ValueClass
public interface BestModelStats {

    /**
     * The average of the metric of the winning model
     * @return
     */
    double avg();
    /**
     * The minimum of the metric of the winning model
     * @return
     */
    double min();
    /**
     * The maximum of the metric of the winning model
     * @return
     */
    double max();

    @Value.Derived
    default Map<String, Object> toMap() {
        return Map.of(
            "avg", avg(),
            "min", min(),
            "max", max()
        );
    }

    static BestModelStats of(ModelStats modelStats) {
        return ImmutableBestModelStats.of(modelStats.avg(), modelStats.min(), modelStats.max());
    }

    static BestModelStats findBestModelStats(
        List<ModelStats> metricStatsForModels,
        TrainerConfig bestParams
    ) {
        return metricStatsForModels.stream()
            .filter(metricStatsForModel -> metricStatsForModel.params() == bestParams)
            .findFirst()
            .map(BestModelStats::of)
            .orElseThrow();
    }
}
