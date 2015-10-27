/*******************************************************************************
 * Copyright (c) 2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.orientdb;

import org.hawk.core.graph.IGraphTransaction;

import com.tinkerpop.blueprints.impls.orient.OrientGraph;

public class OrientTransaction implements IGraphTransaction {

	private OrientGraph graph;

	public OrientTransaction(OrientGraph orientGraph) {
		this.graph = orientGraph;
	}

	@Override
	public void success() {
		graph.commit();
	}

	@Override
	public void failure() {
		graph.rollback();
	}

	@Override
	public void close() {
		// graph.shutdown();
	}

	public OrientGraph getOrientGraph() {
		return graph;
	}
}