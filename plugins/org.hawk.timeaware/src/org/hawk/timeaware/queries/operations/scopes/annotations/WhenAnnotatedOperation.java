/*******************************************************************************
 * Copyright (c) 2019 Aston University.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 3.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-3.0
 *
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.timeaware.queries.operations.scopes.annotations;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.hawk.core.graph.timeaware.ITimeAwareGraphNode;
import org.hawk.core.graph.timeaware.ITimeAwareGraphNodeVersionIndex;
import org.hawk.epsilon.emc.EOLQueryEngine;
import org.hawk.timeaware.queries.operations.scopes.WhenNodeWrapper;
import org.hawk.timeaware.queries.operations.scopes.predicates.WhenOperation;

/**
 * Variant of {@link WhenOperation} which uses a predefined derived Boolean attribute.
 */
public class WhenAnnotatedOperation extends AbstractAnnotatedOperation {

	public WhenAnnotatedOperation(Supplier<EOLQueryEngine> containerModelSupplier) {
		super(containerModelSupplier);
	}

	@Override
	protected ITimeAwareGraphNode useAnnotations(ITimeAwareGraphNodeVersionIndex index, ITimeAwareGraphNode taNode, String derivedAttrName) {
		final List<Long> versions = new ArrayList<>();
		for (ITimeAwareGraphNode version : index.getAllVersions(taNode)) {
			versions.add(version.getTime());
		}

		if (versions.isEmpty()) {
			return null;
		} else {
			final Long earliestTimepoint = versions.get(versions.size() - 1);
			return new WhenNodeWrapper(taNode.travelInTime(earliestTimepoint), versions);
		}
	}

}
