/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Konstantinos Barmpis - initial API and implementation
 ******************************************************************************/
package org.hawk.core.graph;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.hawk.core.IConsole;

/**
 * Assumes all iterators returned will provide a consistent ordering of results
 * as long as the database state has not changed
 * 
 * @author kb
 *
 */
public interface IGraphDatabase {

	String fileIndexName = "FILEINDEX";
	String metamodelIndexName = "METAMODELINDEX";
	String databaseName = "db";

	String getPath();

	void run(File parentfolder, IConsole c);

	void shutdown() throws Exception;

	void delete() throws Exception;

	// can be used to get any index not exposed here or created by developers.
	IGraphNodeIndex getOrCreateNodeIndex(String name);

	IGraphNodeIndex getMetamodelIndex();

	IGraphNodeIndex getFileIndex();

	IGraphTransaction beginTransaction() throws Exception;

	// returns whether this store supports a transactional mode
	boolean isTransactional();

	// any non-transactional mode
	void enterBatchMode();

	void exitBatchMode();

	IGraphIterable<IGraphNode> allNodes(String label);

	IGraphNode createNode(Map<String, Object> props, String label);

	IGraphEdge createRelationship(IGraphNode start, IGraphNode end, String type);

	IGraphEdge createRelationship(IGraphNode start, IGraphNode end, String type, Map<String, Object> props);

	Object getGraph();

	IGraphNode getNodeById(Object id);

	boolean nodeIndexExists(String name);

	String getType();

	String getHumanReadableName();

	String getTempDir();

	public enum Mode {
		TX_MODE, NO_TX_MODE, UNKNOWN
	};

	/**
	 * Returns whether the current database is in transactional (update) or
	 * non-transactional (batch insert) mode.
	 */
	Mode currentMode();

	Set<String> getNodeIndexNames();

	Set<String> getKnownMMUris();

}
