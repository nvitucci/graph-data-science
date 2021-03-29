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
package org.neo4j.graphalgo.core.utils.export.file;

import org.immutables.builder.Builder;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.IdMapping;
import org.neo4j.graphalgo.api.NodeMapping;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.api.Relationships;
import org.neo4j.graphalgo.api.schema.NodeSchema;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.loading.construction.GraphFactory;
import org.neo4j.graphalgo.core.loading.construction.NodesBuilder;
import org.neo4j.graphalgo.core.loading.construction.RelationshipsBuilder;
import org.neo4j.graphalgo.core.utils.export.GraphStoreExporter;
import org.neo4j.graphalgo.core.utils.export.ImmutableImportedProperties;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.internal.batchimport.input.Collector;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public final class CsvToGraphStoreExporter {

    private final GraphStoreNodeVisitor.Builder nodeVisitorBuilder;
    private final Path importPath;
    private final GraphStoreToFileExporterConfig config;

    private final GraphBuilder graphBuilder;

    public static CsvToGraphStoreExporter create(
        GraphStoreToFileExporterConfig config,
        Path importPath
    ) {
        return new CsvToGraphStoreExporter(
            new GraphStoreNodeVisitor.Builder(),
            config,
            importPath
        );
    }

    private CsvToGraphStoreExporter(
        GraphStoreNodeVisitor.Builder nodeVisitorBuilder,
        GraphStoreToFileExporterConfig config,
        Path importPath
    ) {
        this.config = config;
        this.nodeVisitorBuilder = nodeVisitorBuilder.withReverseIdMapping(config.includeMetaData());
        this.importPath = importPath;
        this.graphBuilder = new GraphBuilder();
    }

    public Graph graph() {
        return graphBuilder.build();
    }

    public GraphStoreExporter.ImportedProperties run(AllocationTracker tracker) {
        var fileInput = new FileInput(importPath);
        graphBuilder.tracker(tracker);
        return export(fileInput, tracker);
    }

    private GraphStoreExporter.ImportedProperties export(FileInput fileInput, AllocationTracker tracker) {
        var nodes = exportNodes(fileInput, tracker);
        // FIXME: get the `nodes` parameter from the previous step
        var exportedRelationships = exportRelationships(fileInput, nodes, AllocationTracker.empty());
        return ImmutableImportedProperties.of(nodes.nodeCount(), exportedRelationships);
    }

    private NodeMapping exportNodes(
        FileInput fileInput,
        AllocationTracker tracker
    ) {
        NodeSchema nodeSchema = fileInput.nodeSchema();
        graphBuilder.nodeSchema(nodeSchema);

        GraphInfo graphInfo = fileInput.graphInfo();
        int concurrency = config.writeConcurrency();
        NodesBuilder nodesBuilder = GraphFactory.initNodesBuilder(nodeSchema)
            .maxOriginalId(graphInfo.maxOriginalId())
            .concurrency(concurrency)
            .nodeCount(graphInfo.nodeCount())
            .tracker(tracker)
            .build();
        nodeVisitorBuilder.withNodeSchema(nodeSchema);
        nodeVisitorBuilder.withNodesBuilder(nodesBuilder);

        var nodesIterator = fileInput.nodes(Collector.EMPTY).iterator();
        Collection<Runnable> tasks = ParallelUtil.tasks(
            concurrency,
            (index) -> new ElementImportRunner(nodeVisitorBuilder.build(), nodesIterator)
        );

        ParallelUtil.run(tasks, Pools.DEFAULT);

        var nodeMappingAndProperties = nodesBuilder.build();
        graphBuilder.idMap(nodeMappingAndProperties.nodeMapping())
            .nodeProperties(nodeMappingAndProperties.nodeProperties());

        return nodeMappingAndProperties.nodeMapping();
    }

    private long exportRelationships(FileInput fileInput, IdMapping nodes, AllocationTracker tracker) {
        var relationshipSchema = fileInput.relationshipSchema();
        int concurrency = config.writeConcurrency();
        RelationshipsBuilder relationshipsBuilder = GraphFactory.initRelationshipsBuilder()
            .concurrency(concurrency)
            .nodes(nodes)
            .tracker(tracker)
            .build();

        var relationshipVisitor = new GraphStoreRelationshipVisitor(relationshipSchema, relationshipsBuilder);
        var relationshipsIterator = fileInput.relationships(Collector.EMPTY).iterator();
        Collection<Runnable> tasks = ParallelUtil.tasks(
            concurrency,
            (index) -> new ElementImportRunner(relationshipVisitor, relationshipsIterator)
        );

        ParallelUtil.run(tasks, Pools.DEFAULT);

        // FIXME: need to get the relationship type dynamically!!!
        graphBuilder.relationshipType(RelationshipType.ALL_RELATIONSHIPS);

        var relationships = relationshipsBuilder.build();
        graphBuilder.relationships(relationships);
        return relationships.topology().elementCount();
    }

    @Builder.Factory
    static Graph graph(
        NodeMapping idMap,
        NodeSchema nodeSchema,
        Optional<Map<String, NodeProperties>> nodeProperties,
        RelationshipType relationshipType,
        Relationships relationships,
        AllocationTracker tracker
    ) {
        return nodeProperties
            .map(properties -> GraphFactory.create(
                idMap,
                nodeSchema,
                properties,
                relationshipType,
                relationships,
                tracker
            ))
            .orElseGet(() -> GraphFactory.create(idMap, relationships, tracker));
    }
}
