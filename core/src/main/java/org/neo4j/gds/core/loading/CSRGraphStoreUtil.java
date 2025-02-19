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
package org.neo4j.gds.core.loading;

import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.NodeProperty;
import org.neo4j.gds.api.NodePropertyStore;
import org.neo4j.gds.api.RelationshipProperty;
import org.neo4j.gds.api.RelationshipPropertyStore;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.api.ValueTypes;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.api.schema.NodeSchema;
import org.neo4j.gds.api.schema.PropertySchema;
import org.neo4j.gds.api.schema.RelationshipPropertySchema;
import org.neo4j.gds.api.schema.RelationshipSchema;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.values.storable.NumberType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Collections.singletonMap;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public final class CSRGraphStoreUtil {

    public static CSRGraphStore createFromGraph(
        NamedDatabaseId databaseId,
        HugeGraph graph,
        String relationshipTypeString,
        Optional<String> relationshipProperty,
        int concurrency
    ) {
        Relationships relationships = graph.relationships();

        var relationshipType = RelationshipType.of(relationshipTypeString);
        var topology = Map.of(relationshipType, relationships.topology());

        var nodeProperties = constructNodePropertiesFromGraph(graph);
        var relationshipProperties = constructRelationshipPropertiesFromGraph(
            graph,
            relationshipProperty,
            relationships,
            relationshipType
        );

        RelationshipSchema.Builder relationshipSchemaBuilder = RelationshipSchema.builder().addRelationshipType(relationshipType);
        relationshipProperty.ifPresent(property -> relationshipSchemaBuilder.addProperty(relationshipType, property, ValueType.DOUBLE));

        var schema = GraphSchema.of(graph.schema().nodeSchema(), relationshipSchemaBuilder.build());

        return new CSRGraphStore(
            databaseId,
            schema,
            graph.idMap(),
            nodeProperties,
            topology,
            relationshipProperties,
            concurrency
        );
    }

    @NotNull
    private static NodePropertyStore constructNodePropertiesFromGraph(HugeGraph graph) {
        var nodePropertyStoreBuilder = NodePropertyStore.builder();

        graph
            .schema()
            .nodeSchema()
            .unionProperties()
            .forEach((propertyKey, propertySchema) -> nodePropertyStoreBuilder.putIfAbsent(
                propertyKey,
                NodeProperty.of(propertyKey,
                    propertySchema.state(),
                    graph.nodeProperties(propertyKey),
                    propertySchema.defaultValue()
                )
            ));

        return nodePropertyStoreBuilder.build();
    }

    @NotNull
    private static Map<RelationshipType, RelationshipPropertyStore> constructRelationshipPropertiesFromGraph(
        Graph graph,
        Optional<String> relationshipProperty,
        Relationships relationships,
        RelationshipType relationshipType
    ) {
        Map<RelationshipType, RelationshipPropertyStore> relationshipProperties = Collections.emptyMap();
        if (relationshipProperty.isPresent() && relationships.properties().isPresent()) {
            Map<String, RelationshipPropertySchema> relationshipPropertySchemas = graph
                .schema()
                .relationshipSchema()
                .properties()
                .get(relationshipType);

            if (relationshipPropertySchemas.size() != 1) {
                throw new IllegalStateException(formatWithLocale(
                    "Relationship schema is expected to have exactly one property but had %s",
                    relationshipPropertySchemas.size()
                ));
            }

            RelationshipPropertySchema relationshipPropertySchema = relationshipPropertySchemas
                .values()
                .stream()
                .findFirst()
                .get();

            String propertyKey = relationshipProperty.get();
            relationshipProperties = singletonMap(
                relationshipType,
                RelationshipPropertyStore.builder().putIfAbsent(
                    propertyKey,
                    RelationshipProperty.of(
                        propertyKey,
                        NumberType.FLOATING_POINT,
                        relationshipPropertySchema.state(),
                        relationships.properties().get(),
                        relationshipPropertySchema.defaultValue().isUserDefined()
                            ? relationshipPropertySchema.defaultValue()
                            : ValueTypes.fromNumberType(NumberType.FLOATING_POINT).fallbackValue(),
                        relationshipPropertySchema.aggregation()
                    )
                ).build()
            );
        }
        return relationshipProperties;
    }

    public static void extractNodeProperties(
        GraphStoreBuilder graphStoreBuilder,
        Function<String, PropertySchema> nodeSchema,
        Map<String, NodeProperties> nodeProperties
    ) {
        NodePropertyStore.Builder propertyStoreBuilder = NodePropertyStore.builder();
        nodeProperties.forEach((propertyKey, propertyValues) -> {
            var propertySchema = nodeSchema.apply(propertyKey);
            propertyStoreBuilder.putIfAbsent(
                propertyKey,
                NodeProperty.of(
                    propertyKey,
                    propertySchema.state(),
                    propertyValues,
                    propertySchema.defaultValue()
                )
            );
        });
        graphStoreBuilder.nodePropertyStore(propertyStoreBuilder.build());
    }

    public static RelationshipPropertyStore buildRelationshipPropertyStore(
        List<Relationships> relationships,
        List<RelationshipPropertySchema> relationshipPropertySchemas
    ) {
        assert relationships.size() >= relationshipPropertySchemas.size();

        var propertyStoreBuilder = RelationshipPropertyStore.builder();

        for (int i = 0; i < relationshipPropertySchemas.size(); i++) {
            var relationship = relationships.get(i);
            var relationshipPropertySchema = relationshipPropertySchemas.get(i);
            relationship.properties().ifPresent(properties -> {

                propertyStoreBuilder.putIfAbsent(relationshipPropertySchema.key(), RelationshipProperty.of(
                        relationshipPropertySchema.key(),
                        NumberType.FLOATING_POINT,
                        relationshipPropertySchema.state(),
                        properties,
                        relationshipPropertySchema.defaultValue(),
                        relationshipPropertySchema.aggregation()
                    )
                );
            });
        }

        return propertyStoreBuilder.build();
    }

    public static GraphSchema computeGraphSchema(
        IdMapAndProperties idMapAndProperties,
        RelationshipsAndProperties relationshipsAndProperties
    ) {
        return computeGraphSchema(
            idMapAndProperties,
            (__) -> idMapAndProperties.properties().keySet(),
            relationshipsAndProperties
        );
    }

    public static GraphSchema computeGraphSchema(
        IdMapAndProperties idMapAndProperties,
        Function<NodeLabel, Collection<String>> propertiesByLabel,
        RelationshipsAndProperties relationshipsAndProperties
    ) {
        var properties = idMapAndProperties.properties().nodeProperties();

        var nodeSchemaBuilder = NodeSchema.builder();
        for (var label : idMapAndProperties.idMap().availableNodeLabels()) {
            for (var propertyKey : propertiesByLabel.apply(label)) {
                nodeSchemaBuilder.addProperty(
                    label,
                    propertyKey,
                    properties.get(propertyKey).propertySchema()
                );
            }
        }
        idMapAndProperties.idMap().availableNodeLabels().forEach(nodeSchemaBuilder::addLabel);

        var relationshipSchemaBuilder = RelationshipSchema.builder();
        relationshipsAndProperties
            .properties()
            .forEach((relType, propertyStore) -> propertyStore
                .relationshipProperties()
                .forEach((propertyKey, propertyValues) -> relationshipSchemaBuilder.addProperty(
                    relType,
                    propertyKey,
                    propertyValues.propertySchema()
                )));
        relationshipsAndProperties.relationships().keySet().forEach(relationshipSchemaBuilder::addRelationshipType);

        return GraphSchema.of(
            nodeSchemaBuilder.build(),
            relationshipSchemaBuilder.build()
        );
    }


    private CSRGraphStoreUtil() {}
}
