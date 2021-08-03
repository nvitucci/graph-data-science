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
package org.neo4j.gds.config;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.graphdb.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public interface SourceNodesConfig {

    @Value.Default
    @Configuration.ConvertWith("org.neo4j.gds.config.SourceNodesConfig#parseNodeIds")
    default List<Long> sourceNodes() {
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    static List<Long> parseNodeIds(Object input) {
        var nodeIds = new ArrayList<Long>();

        if (input instanceof List) {
            ((List<Object>) input).forEach(e -> nodeIds.add(parseNodeId(e)));
        } else {
            nodeIds.add(parseNodeId(input));
        }

        return nodeIds;
    }

    static Long parseNodeId(Object input) {
        if (input instanceof Node) {
            return ((Node) input).getId();
        } else if (input instanceof Number) {
            return ((Number) input).longValue();
        }

        throw new IllegalArgumentException(formatWithLocale(
            "Expected List of Nodes or Numbers for `sourceNodes`. Got %s.",
            input.getClass().getSimpleName()
        ));
    }
}
