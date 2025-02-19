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
package org.neo4j.gds.storageengine;

import org.neo4j.configuration.Config;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.gds.compat.GraphDatabaseApiProxy;
import org.neo4j.gds.compat.StorageEngineProxy;
import org.neo4j.gds.core.cypher.CypherGraphStore;
import org.neo4j.gds.core.loading.CatalogRequest;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.neo4j.gds.core.cypher.CypherGraphStoreCatalogHelper.setWrappedGraphStore;

public class InMemoryDatabaseCreator {

    public static void createDatabase(GraphDatabaseAPI api, String username, NamedDatabaseId databaseId, String graphName, String dbName) {
        var dbms = GraphDatabaseApiProxy.resolveDependency(api, DatabaseManagementService.class);
        var graphStoreWithConfig = GraphStoreCatalog.get(CatalogRequest.of(username, databaseId), graphName);
        var cypherGraphStore = new CypherGraphStore(graphStoreWithConfig.graphStore());
        setWrappedGraphStore(graphStoreWithConfig.config(), cypherGraphStore);
        StorageEngineProxy.createInMemoryDatabase(dbms, dbName, graphName, Config.defaults());
    }

}
