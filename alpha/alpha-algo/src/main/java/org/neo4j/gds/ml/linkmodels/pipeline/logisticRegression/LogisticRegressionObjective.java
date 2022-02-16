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
package org.neo4j.gds.ml.linkmodels.pipeline.logisticRegression;

import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.ml.Objective;
import org.neo4j.gds.ml.Trainer;
import org.neo4j.gds.ml.core.Variable;
import org.neo4j.gds.ml.core.batch.Batch;
import org.neo4j.gds.ml.core.functions.Constant;
import org.neo4j.gds.ml.core.functions.ConstantScale;
import org.neo4j.gds.ml.core.functions.CrossEntropyLoss;
import org.neo4j.gds.ml.core.functions.ElementSum;
import org.neo4j.gds.ml.core.functions.L2NormSquared;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;
import org.neo4j.gds.ml.core.tensor.Scalar;
import org.neo4j.gds.ml.core.tensor.Tensor;
import org.neo4j.gds.ml.core.tensor.Vector;
import org.neo4j.gds.ml.logisticregression.LogisticRegressionClassifier;
import org.neo4j.gds.ml.logisticregression.LogisticRegressionTrainer;

import java.util.List;
import java.util.Optional;

public class LogisticRegressionObjective implements Objective<LogisticRegressionTrainer.LogisticRegressionData> {
    private final LogisticRegressionClassifier classifier;
    private final double penalty;
    private final Trainer.Features features;
    private final HugeLongArray labels;

    public LogisticRegressionObjective(
        LogisticRegressionClassifier classifier,
        double penalty,
        Trainer.Features features,
        HugeLongArray labels
    ) {
        this.classifier = classifier;
        this.penalty = penalty;
        this.features = features;
        this.labels = labels;

        assert features.size() > 0;
    }

    @Override
    public List<Weights<? extends Tensor<?>>> weights() {
        Optional<Weights<Scalar>> bias = classifier.data().bias();
        if (bias.isPresent()) {
            return List.of(classifier.data().weights(), bias.get());
        } else {
            return List.of(classifier.data().weights());
        }
    }

    @Override
    public Variable<Scalar> loss(Batch batch, long trainSize) {
        var batchLabels = batchLabelVector(batch, classifier.classIdMap());
        var predictions = classifier.predictionsVariable(batch, features);
        var unpenalizedLoss = new CrossEntropyLoss(
            predictions,
            batchLabels
        );
        var penaltyVariable = new ConstantScale<>(new L2NormSquared(modelData().weights()), batch.size() * penalty / trainSize);
        return new ElementSum(List.of(unpenalizedLoss, penaltyVariable));
    }

    @Override
    public LogisticRegressionTrainer.LogisticRegressionData modelData() {
        return classifier.data();
    }

    private Constant<Vector> batchLabelVector(Batch batch, LocalIdMap localIdMap) {
        var batchedTargets = new Vector(batch.size());
        var batchOffset = new MutableInt();

        batch.nodeIds().forEach(
            relationshipId -> batchedTargets.setDataAt(batchOffset.getAndIncrement(), localIdMap.toMapped(labels.get(relationshipId)))
        );

        return new Constant<>(batchedTargets);
    }
}
