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
package org.neo4j.gds.ml.decisiontree;

import org.assertj.core.data.Offset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GiniIndexTest {

    private static final LocalIdMap CLASS_MAPPING = LocalIdMap.of(5, 1);

    private static Stream<Arguments> giniParameters() {
        return Stream.of(
            Arguments.of(
                new int[]{1, 5, 1, 5},
                new long[][]{new long[]{0, 1}, new long[]{2, 3}},
                ImmutableGroupSizes.of(2, 2),
                0.5D
            ),
            Arguments.of(
                new int[]{5, 5, 1, 1},
                new long[][]{new long[]{0, 1}, new long[]{2, 3}},
                ImmutableGroupSizes.of(2, 2),
                0.0D
            ),
            Arguments.of(
                new int[]{1, 5, 5, 5},
                new long[][]{new long[]{0}, new long[]{1, 2, 3}},
                ImmutableGroupSizes.of(1, 3),
                0.0D
            ),
            Arguments.of(
                new int[]{1, 5, 5, 5},
                new long[][]{new long[]{0, 1}, new long[]{2, 3}},
                ImmutableGroupSizes.of(2, 2),
                0.25D
            ),
            Arguments.of(
                new int[]{1, 5, 5, 5},
                new long[][]{new long[]{0, 1, 0, 0}, new long[]{2, 3, 1, 1}},
                ImmutableGroupSizes.of(2, 2),
                0.25D
            )
        );
    }

    @ParameterizedTest
    @MethodSource("giniParameters")
    void shouldComputeCorrectLoss(int[] allLabels, long[][] groups, GroupSizes groupSizes, double expectedLoss) {
        var hugeLabels = HugeLongArray.newArray(allLabels.length);
        for (int i = 0; i < allLabels.length; i++) {
            hugeLabels.set(i, allLabels[i]);
        }

        var leftGroup = HugeLongArray.newArray(groups[0].length);
        for (int i = 0; i < groups[0].length; i++) {
            leftGroup.set(i, groups[0][i]);
        }

        var rightGroup = HugeLongArray.newArray(groups[1].length);
        for (int i = 0; i < groups[1].length; i++) {
            rightGroup.set(i, groups[1][i]);
        }

        var giniIndexLoss = GiniIndex.fromOriginalLabels(hugeLabels, CLASS_MAPPING);

        assertThat(giniIndexLoss.splitLoss(ImmutableGroups.of(leftGroup, rightGroup), groupSizes))
            .isCloseTo(expectedLoss, Offset.offset(0.00001D));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "  10,  104",
        " 100,  464"
    })
    void memoryEstimationShouldScaleWithSampleCount(long numberOfTrainingSamples, long expectedBytes) {
        assertThat(GiniIndex.memoryEstimation(numberOfTrainingSamples))
            .isEqualTo(MemoryRange.of(expectedBytes));
    }
}
