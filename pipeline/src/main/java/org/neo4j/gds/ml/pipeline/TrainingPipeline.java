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
package org.neo4j.gds.ml.pipeline;

import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.core.utils.TimeUtil;
import org.neo4j.gds.ml.models.TrainerConfig;
import org.neo4j.gds.ml.models.TrainingMethod;
import org.neo4j.gds.ml.models.automl.TunableTrainerConfig;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.gds.config.MutatePropertyConfig.MUTATE_PROPERTY_KEY;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public abstract class TrainingPipeline<FEATURE_STEP extends FeatureStep> implements Pipeline<FEATURE_STEP> {

    protected final List<ExecutableNodePropertyStep> nodePropertySteps;
    protected final List<FEATURE_STEP> featureSteps;
    private final ZonedDateTime creationTime;

    protected Map<TrainingMethod, List<TunableTrainerConfig>> trainingParameterSpace;

    public static Map<String, List<Map<String, Object>>> toMapParameterSpace(Map<TrainingMethod, List<TunableTrainerConfig>> parameterSpace) {
        return parameterSpace.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().stream().map(TunableTrainerConfig::toMap).collect(Collectors.toList())
            ));
    }

    protected TrainingPipeline() {
        this.nodePropertySteps = new ArrayList<>();
        this.featureSteps = new ArrayList<>();
        this.creationTime = TimeUtil.now();

        this.trainingParameterSpace = new EnumMap<>(TrainingMethod.class);

        Arrays.stream(TrainingMethod.values()).forEach(method -> trainingParameterSpace.put(method, new ArrayList<>()));
    }

    @Override
    public Map<String, Object> toMap() {
        // The pipeline's type and creation is not part of the map.
        Map<String, Object> map = new HashMap<>();
        map.put("featurePipeline", featurePipelineDescription());
        map.put(
            "trainingParameterSpace",
            toMapParameterSpace(trainingParameterSpace)
        );
        map.putAll(additionalEntries());
        return map;
    }

    public abstract String type();

    protected abstract Map<String, List<Map<String, Object>>> featurePipelineDescription();

    protected abstract Map<String, Object> additionalEntries();

    public void validateBeforeExecution(GraphStore graphStore, AlgoBaseConfig config) {
        Set<String> invalidProperties = featurePropertiesMissingFromGraph(graphStore, config);

        this.nodePropertySteps.stream()
            .flatMap(step -> Stream.ofNullable((String) step.config().get(MUTATE_PROPERTY_KEY)))
            .forEach(invalidProperties::remove);

        if (!invalidProperties.isEmpty()) {
            throw Pipeline.missingNodePropertiesFromFeatureSteps(invalidProperties);
        }
    }

    public int numberOfModelCandidates() {
        return this.trainingParameterSpace()
            .values()
            .stream()
            .mapToInt(List::size)
            .sum();
    }

    public void addNodePropertyStep(NodePropertyStep step) {
        validateUniqueMutateProperty(step);
        this.nodePropertySteps.add(step);
    }

    public void addFeatureStep(FEATURE_STEP featureStep) {
        this.featureSteps.add(featureStep);
    }

    @Override
    public List<ExecutableNodePropertyStep> nodePropertySteps() {
        return this.nodePropertySteps;
    }

    @Override
    public List<FEATURE_STEP> featureSteps() {
        return this.featureSteps;
    }

    public Map<TrainingMethod, List<TunableTrainerConfig>> trainingParameterSpace() {
        return trainingParameterSpace;
    }

    public void setTrainingParameterSpace(TrainingMethod method, List<TunableTrainerConfig> trainingConfigs) {
        this.trainingParameterSpace.put(method, trainingConfigs);
    }

    public void setConcreteTrainingParameterSpace(TrainingMethod method, List<TrainerConfig> trainingConfigs) {
        var tunableTrainerConfigs = trainingConfigs
            .stream()
            .map(TrainerConfig::toTunableConfig)
            .collect(Collectors.toList());

        this.trainingParameterSpace.put(method, tunableTrainerConfigs);
    }

    public void addTrainerConfig(TrainingMethod method, TunableTrainerConfig trainingConfig) {
        this.trainingParameterSpace.get(method).add(trainingConfig);
    }

    public void addTrainerConfig(TrainingMethod method, TrainerConfig trainingConfig) {
        this.trainingParameterSpace.get(method).add(trainingConfig.toTunableConfig());
    }

    private void validateUniqueMutateProperty(NodePropertyStep step) {
        this.nodePropertySteps.forEach(nodePropertyStep -> {
            var newMutatePropertyName = step.config().get(MUTATE_PROPERTY_KEY);
            var existingMutatePropertyName = nodePropertyStep.config().get(MUTATE_PROPERTY_KEY);
            if (newMutatePropertyName.equals(existingMutatePropertyName)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "The value of `%s` is expected to be unique, but %s was already specified in the %s procedure.",
                    MUTATE_PROPERTY_KEY,
                    newMutatePropertyName,
                    nodePropertyStep.procName()
                ));
            }
        });
    }

    public ZonedDateTime creationTime() {
        return creationTime;
    }
}
