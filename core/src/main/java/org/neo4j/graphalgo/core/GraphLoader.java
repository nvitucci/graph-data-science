/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.core;

import org.immutables.value.Value;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphLoaderContext;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.api.GraphStoreFactory;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.config.GraphCreateFromCypherConfig;
import org.neo4j.graphalgo.config.GraphCreateFromStoreConfig;
import org.neo4j.graphalgo.core.loading.CypherFactory;
import org.neo4j.graphalgo.core.loading.NativeFactory;

import static org.neo4j.graphalgo.utils.ExceptionUtil.throwIfUnchecked;

@ValueClass
public interface GraphLoader {

    GraphLoaderContext context();

    @Value.Default
    default String username() {
        return createConfig().username();
    }

    GraphCreateConfig createConfig();

    default Graph graph(Class<? extends GraphStoreFactory> factoryType) {
        return load(factoryType);
    }

    default GraphStore graphStore(Class<? extends GraphStoreFactory> factoryType) {
        return build(factoryType).build().graphStore();
    }

    /**
     * Returns an instance of the factory that can be used to load the graph.
     */
    default <T extends GraphStoreFactory> T build(final Class<T> factoryType) {
        try {
            GraphStoreFactory factory;

            if (CypherFactory.class.isAssignableFrom(factoryType)) {
                factory = new CypherFactory((GraphCreateFromCypherConfig) createConfig(), context());
            } else {
                factory = new NativeFactory((GraphCreateFromStoreConfig) createConfig(), context());
            }

            return factoryType.cast(factory);
        } catch (Throwable throwable) {
            throwIfUnchecked(throwable);
            throw new RuntimeException(throwable.getMessage(), throwable);
        }
    }

    /**
     * Loads the graph using the provided GraphFactory, passing the built
     * configuration as parameters.
     * <p>
     * The chosen implementation determines the performance characteristics
     * during load and usage of the Graph.
     *
     * @return the freshly loaded graph
     */
    default Graph load(Class<? extends GraphStoreFactory> factoryType) {
        return build(factoryType).build().graphStore().getUnion();
    }

}
