/*******************************************************************************
 * Copyright (c) 2018 Aston University.
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
package org.hawk.timeaware.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawk.core.VcsCommitItem;
import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.graph.IGraphNode;
import org.hawk.core.graph.IGraphTransaction;
import org.hawk.core.graph.timeaware.ITimeAwareGraphDatabase;
import org.hawk.core.graph.timeaware.ITimeAwareGraphNode;
import org.hawk.core.model.IHawkModelResource;
import org.hawk.graph.GraphWrapper;
import org.hawk.graph.MetamodelNode;
import org.hawk.graph.ModelElementNode;
import org.hawk.graph.TypeNode;
import org.hawk.graph.updater.DeletionUtils;
import org.hawk.graph.updater.GraphModelInserter;
import org.hawk.graph.updater.GraphModelUpdater;
import org.hawk.timeaware.graph.annotators.VersionAnnotator;
import org.hawk.timeaware.graph.annotators.VersionAnnotatorSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refines the {@link GraphModelUpdater} to keep the full history of the project
 * into the graph.
 */
public class TimeAwareModelUpdater extends GraphModelUpdater {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimeAwareModelUpdater.class);

	/**
	 * Variant of {@link DeletionUtils} which ends node timelines instead of
	 * deleting their entire history.
	 */
	public class SoftDeletionUtils extends DeletionUtils {
		public SoftDeletionUtils(IGraphDatabase graph) {
			super(graph);
		}

		@Override
		protected boolean delete(IGraphNode modelElement) {
			if (!modelElement.getEdges().iterator().hasNext()) {
				try {
					removeFromIndexes(modelElement);

					/*
					 * end() means that the node is still alive at this precise moment, while
					 * delete() means that the node is not available from this moment. We only need
					 * to end this at the timepoint straight before this one.
					 */
					ITimeAwareGraphNode taModelElement = (ITimeAwareGraphNode) modelElement;
					if (taModelElement.getTime() > 0) {
						taModelElement.travelInTime(taModelElement.getTime() - 1).end();
					} else {
						taModelElement.delete();
					}

					return true;
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
			}

			return false;
		}
	}

	@Override
	public GraphModelInserter createInserter() {
		return new GraphModelInserter(indexer, this::createDeletionUtils, typeCache) {
			@Override
			protected double calculateModelDeltaRatio(IGraphNode fileNode, boolean verbose) throws Exception {
				super.calculateModelDeltaRatio(fileNode, verbose);

				/*
				 * We want to always do a transactional update - batch update starts by removing
				 * every existing node, so we would lose track of the various versions of each
				 * model element.
				 */
				return 0;
			}
		};
	}

	@Override
	public boolean updateStore(VcsCommitItem f, IHawkModelResource res) {
		boolean success = super.updateStore(f, res);

		if (success) {
			try (IGraphTransaction tx = indexer.getGraph().beginTransaction()) {
				final VersionAnnotator ann = new VersionAnnotator(indexer);
				final long time = ((ITimeAwareGraphDatabase) indexer.getGraph()).getTime();
				final GraphWrapper gw = new GraphWrapper(indexer.getGraph());

				for (VersionAnnotatorSpec spec : new TimeAwareMetaModelUpdater().listVersionAnnotators(indexer)) {
					final MetamodelNode mn = gw.getMetamodelNodeByNsURI(spec.getMetamodelURI());
					final TypeNode tn = mn.getTypeNode(spec.getTypeName());
					final TypeNode timedTN = new TypeNode(((ITimeAwareGraphNode) tn.getNode()).travelInTime(time));

					for (ModelElementNode instance : timedTN.getAll()) {
						ITimeAwareGraphNode taInstance = ((ITimeAwareGraphNode) instance.getNode()).travelInTime(time);
						ann.annotateVersion(spec, taInstance);
					}
				}
				tx.success();
			} catch (Exception e) {
				LOGGER.error("Could not annotate nodes", e);
				success = false;
			}
		}

		return success;
	}

	protected Map<String, List<VersionAnnotatorSpec>> collectSpecifications(
			final Collection<VersionAnnotatorSpec> annotators) {
		final Map<String, List<VersionAnnotatorSpec>> specMap = new HashMap<>();
		for (VersionAnnotatorSpec spec : annotators) {
			specMap.computeIfAbsent(
				String.format("%s##%s", spec.getMetamodelURI(), spec.getTypeName()),
				(ignore) -> new ArrayList<VersionAnnotatorSpec>()
			).add(spec);
		}
		return specMap;
	}

	@Override
	protected DeletionUtils createDeletionUtils() {
		return new SoftDeletionUtils(indexer.getGraph());
	}

	@Override
	public String getHumanReadableName() {
		return "Default Hawk Time Aware Model Updater (v1.0)";
	}

}
