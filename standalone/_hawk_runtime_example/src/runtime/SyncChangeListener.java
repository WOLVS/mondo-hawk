/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
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
 *     Konstantinos Barmpis - initial API and implementation
 ******************************************************************************/
package runtime;

import java.io.File;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hawk.core.IModelIndexer;
import org.hawk.core.VcsCommitItem;
import org.hawk.core.graph.IGraphChangeListener;
import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphNode;
import org.hawk.core.graph.IGraphTransaction;
import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClass;
import org.hawk.core.model.IHawkModelResource;
import org.hawk.core.model.IHawkObject;
import org.hawk.core.model.IHawkPackage;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.runtime.ModelIndexerImpl;
import org.hawk.graph.FileNode;
import org.hawk.graph.ModelElementNode;
import org.hawk.graph.internal.updater.GraphModelUpdater;

public class SyncChangeListener implements IGraphChangeListener {

	private Git git;
	private LinkedList<RevCommit> orderedCommits = new LinkedList<>();
	ModelIndexerImpl hawk;
	private int removedProxies;

	private int deleted = 0;
	private Set<String> changed = new HashSet<>();

	public SyncChangeListener(Git git,
			LinkedHashMap<String, RevCommit> commits, IModelIndexer hawk) {
		this.hawk = (ModelIndexerImpl) hawk;
		this.git = git;
		for (RevCommit c : commits.values())
			orderedCommits.add(0, c);
		System.err
				.println("SyncValidationListener: hawk.setSyncMetricsEnabled(true) called, performance will suffer!");
		hawk.setSyncMetricsEnabled(true);
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public void synchroniseStart() {
		// System.out.println("a");

	}

	@Override
	public void synchroniseEnd() {

		validateChanges();
		//
		deleted = 0;
		changed.clear();
		//
		if (orderedCommits.size() > 0) {
			RevCommit first = orderedCommits.getFirst();
			orderedCommits.remove();
			System.out.println("changing repo to commit with time: "
					+ first.getCommitTime());
			// update git to this revision
			CheckoutCommand c = git.checkout();
			c.setName(first.getId().getName());
			try {
				c.call();
			} catch (Exception e) {
				System.err.println(e.getMessage());
				// e.printStackTrace();
			}
		} else {
			System.out.println("done -- we are at latest marked commit");
		}
	}

	@SuppressWarnings("unchecked")
	private void validateChanges() {

		System.err.println("sync metrics:");
		System.err.println("interesting\t" + hawk.getInterestingFiles());
		System.err.println("deleted\t\t" + hawk.getDeletedFiles());
		System.err.println("changed\t\t" + hawk.getCurrChangedItems());
		System.err.println("loaded\t\t" + hawk.getLoadedResources());
		System.err.println("c elems\t\t" + latestChangedElements());
		System.err.println("d elems\t\t" + latestDeletedElements());
		System.err.println("time\t\t~" + hawk.getLatestSynctime() / 1000 + "s");

		System.err.println("validating changes...");

		removedProxies = 0;

		int totalErrors = 0;
		int malformed = 0;

		int totalResourceSizes = 0;
		int totalGraphSize = 0;

		String temp = new File(hawk.getGraph().getTempDir()).toURI().toString();

		// for all non-null resources
		if (hawk.getFileToResourceMap() != null)
			for (VcsCommitItem c : hawk.getFileToResourceMap().keySet()) {

				String repoURL = c.getCommit().getDelta().getManager()
						.getLocation();

				IHawkModelResource r = hawk.getFileToResourceMap().get(c);

				if (r == null) {
					// file didnt get parsed so no changes are made -- any way
					// to
					// verify this further?
				}

				else {
					System.out.println("validating file " + c.getChangeType()
							+ " for " + c.getPath());

					totalResourceSizes += r.getAllContentsSet().size();

					IGraphDatabase graph = hawk.getGraph();

					try (IGraphTransaction t = graph.beginTransaction()) {
						String file = null;
						IGraphNode filenode = null;
						try {

							file = c.getCommit().getDelta().getManager()
									.getLocation()
									+ GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
									+ c.getPath();

							filenode = graph.getFileIndex().get("id", file)
									.getSingle();
						} catch (Exception ee) {
							System.err.println("expected file " + file
									+ " but it did not exist");
							totalErrors++;
							continue;
						}

						Iterable<IGraphEdge> instancesEdges = filenode.getIncomingWithType(FileNode.FILE_NODE_LABEL);

						// cache model elements in current resource
						HashMap<String, IHawkObject> eobjectCache = new HashMap<>();
						// cache of malformed object identifiers (to ignore
						// references)
						Set<String> malformedObjectCache = new HashSet<>();
						for (IHawkObject content : r.getAllContentsSet()) {
							IHawkObject old = eobjectCache.put(
									content.getUriFragment(), content);
							if (old != null) {
								System.err.println("warning (" + c.getPath()
										+ ") eobjectCache replaced:");
								System.err.println(old.getUri() + " | "
										+ old.getUriFragment() + " | ofType: "
										+ old.getType().getName());
								System.err.println("with:");
								System.err.println(content.getUri() + " | "
										+ content.getUriFragment()
										+ " | ofType: "
										+ content.getType().getName());
								malformed++;
								malformedObjectCache.add(old.getUri());
								System.err
										.println("WARNING: MALFORMED MODEL RESOURCE (multiple identical identifiers for:\n"
												+ old.getUri()
												+ "),\nexpect "
												+ malformed
												+ " errors in validation.");

							}
						}

						// go through all nodes in graph from the file the
						// resource
						// is in
						for (IGraphEdge instanceEdge : instancesEdges) {
							IGraphNode instance = instanceEdge.getStartNode();
							totalGraphSize++;

							IHawkObject eobject = eobjectCache
									.get(instance
											.getProperty(IModelIndexer.IDENTIFIER_PROPERTY));

							// if a node cannot be found in the model cache
							if (eobject == null) {
								System.err
										.println("error in validating: graph contains node with identifier:"
												+ instance
														.getProperty(IModelIndexer.IDENTIFIER_PROPERTY)
												+ " but resource does not!");
								// this triggers when a malformed model has 2
								// identifiers the same
								totalErrors++;
							} else {
								eobjectCache
										.remove(instance
												.getProperty(IModelIndexer.IDENTIFIER_PROPERTY));

								if (!malformedObjectCache.contains(eobject
										.getUri())) {

									// cache model element attributes and
									// references
									// by
									// name
									HashMap<String, Object> modelAttributes = new HashMap<>();
									for (IHawkAttribute a : ((IHawkClass) eobject
											.getType()).getAllAttributes())
										if (eobject.isSet(a))
											modelAttributes.put(a.getName(),
													eobject.get(a));

									// full reference uri in order to properly
									// compare with hawk proxies
									HashMap<String, Set<String>> modelReferences = new HashMap<>();

									for (IHawkReference ref : ((IHawkClass) eobject
											.getType()).getAllReferences()) {
										if (eobject.isSet(ref)) {
											Object refval = eobject.get(ref,
													false);
											HashSet<String> vals = new HashSet<>();
											String ret;
											if (refval instanceof Iterable<?>) {
												for (Object val : ((Iterable<IHawkObject>) refval)) {
													// if (!((IHawkObject)
													// val).isProxy())
													ret = ((IHawkObject) val)
															.getUri().replace(
																	temp, "");
													ret = URLDecoder.decode(
															ret.replace("+",
																	"%2B"),
															"UTF-8");
													vals.add(repoURL
															+ GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
															+ (ret.startsWith("/") ? ret
																	.substring(1)
																	: ret));
												}

											} else {
												// if (!((IHawkObject)
												// refval).isProxy())
												ret = ((IHawkObject) refval)
														.getUri().replace(temp,
																"");
												ret = URLDecoder
														.decode(ret.replace(
																"+", "%2B"),
																"UTF-8");
												vals.add(repoURL
														+ GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
														+ (ret.startsWith("/") ? ret
																.substring(1)
																: ret));

											}
											if (vals.size() > 0)
												modelReferences.put(
														ref.getName(), vals);
										}
									}

									// compare db attributes to model ones
									for (String propertykey : instance
											.getPropertyKeys()) {
										if (!propertykey
												.equals(IModelIndexer.SIGNATURE_PROPERTY)
												&& !propertykey
														.equals(IModelIndexer.IDENTIFIER_PROPERTY)
												&& !propertykey
														.startsWith(GraphModelUpdater.PROXY_REFERENCE_PREFIX)) {
											//
											Object dbattr = instance
													.getProperty(propertykey);
											Object attr = modelAttributes
													.get(propertykey);

											if (!flattenedStringEquals(dbattr,
													attr)) {
												totalErrors++;
												System.err
														.println("error in validating, attribute: "
																+ propertykey
																+ " has values:");
												String cla1 = dbattr != null ? dbattr
														.getClass().toString()
														: "null attr";
												System.err
														.println("database:\t"
																+ (dbattr instanceof Object[] ? (Arrays
																		.asList((Object[]) dbattr))
																		: dbattr)
																+ " JAVATYPE: "
																+ cla1
																+ " IN NODE: "
																+ instance
																		.getId()
																+ " WITH ID: "
																+ instance
																		.getProperty(IModelIndexer.IDENTIFIER_PROPERTY));
												String cla2 = attr != null ? attr
														.getClass().toString()
														: "null attr";
												System.err
														.println("model:\t\t"
																+ (attr instanceof Object[] ? (Arrays
																		.asList((Object[]) attr))
																		: attr)
																+ " JAVATYPE: "
																+ cla2
																+ " IN ELEMENT WITH ID "
																+ eobject
																		.getUriFragment());
												//
											}

											modelAttributes.remove(propertykey);
										}
									}

									if (modelAttributes.size() > 0) {
										System.err
												.println("error in validating, the following attributes were not found in the graph node:");
										System.err.println(modelAttributes
												.keySet());
										totalErrors++;
									}

									HashMap<String, Set<String>> nodereferences = new HashMap<>();
									// cache db references
									for (IGraphEdge reference : instance
											.getOutgoing()) {

										if (reference.getType().equals(FileNode.FILE_NODE_LABEL)
												|| reference.getType().equals(ModelElementNode.EDGE_LABEL_OFTYPE)
												|| reference.getType().equals(ModelElementNode.EDGE_LABEL_OFKIND)
												|| reference.getPropertyKeys().contains("isDerived")) {
											// ignore
										} else {
											//
											HashSet<String> refvals = new HashSet<>();
											if (nodereferences
													.containsKey(reference
															.getType())) {
												refvals.addAll(nodereferences
														.get(reference
																.getType()));
											}
											final IGraphNode refEndNode = reference
													.getEndNode();
											refvals.add(repoURL
													+ GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
													+ refEndNode
															.getOutgoingWithType(
																	FileNode.FILE_NODE_LABEL)
															.iterator()
															.next()
															.getEndNode()
															.getProperty(
																	IModelIndexer.IDENTIFIER_PROPERTY)
													+ "#"
													+ refEndNode
															.getProperty(
																	IModelIndexer.IDENTIFIER_PROPERTY)
															.toString());
											nodereferences.put(
													reference.getType(),
													refvals);
											//
										}
									}
									// compare model and graph reference maps
									Iterator<Entry<String, Set<String>>> rci = modelReferences
											.entrySet().iterator();
									while (rci.hasNext()) {
										Entry<String, Set<String>> modelRef = rci
												.next();
										String modelRefName = modelRef.getKey();

										if (!nodereferences
												.containsKey(modelRefName)) {
											// no need for this?
											// System.err.println("error in
											// validating: reference "+
											// modelRefName+
											// " had targets in the model but
											// none in the graph: ");
											// System.err.println(modelRef.getValue());
											// allValid = false;
										} else {
											Set<String> noderefvalues = new HashSet<>();
											noderefvalues.addAll(nodereferences
													.get(modelRefName));

											Set<String> modelrefvalues = new HashSet<>();
											modelrefvalues
													.addAll(modelReferences
															.get(modelRefName));

											Set<String> noderefvaluesclone = new HashSet<>();
											noderefvaluesclone
													.addAll(nodereferences
															.get(modelRefName));
											noderefvaluesclone
													.removeAll(modelrefvalues);

											Set<String> modelrefvaluesclone = new HashSet<>();
											modelrefvaluesclone
													.addAll(modelReferences
															.get(modelRefName));
											modelrefvaluesclone
													.removeAll(noderefvalues);
											modelrefvaluesclone = removeHawkProxies(
													instance,
													modelrefvaluesclone);

											if (noderefvaluesclone.size() > 0) {
												System.err
														.println("error in validating: reference "
																+ modelRefName
																+ " of node: "
																+ instance
																		.getProperty(IModelIndexer.IDENTIFIER_PROPERTY)
																+ "\nlocated: "
																+ instance
																		.getOutgoingWithType(
																				FileNode.FILE_NODE_LABEL)
																		.iterator()
																		.next()
																		.getEndNode()
																		.getProperty(
																				IModelIndexer.IDENTIFIER_PROPERTY));
												System.err
														.println(noderefvaluesclone);
												System.err
														.println("the above ids were found in the graph but not the model");
												totalErrors++;
											}

											if (modelrefvaluesclone.size() > 0) {

												System.err
														.println("error in validating: reference "
																+ modelRefName
																+ " of model element: "
																+ eobject
																		.getUriFragment()
																+ "\nlocated: "
																+ eobject
																		.getUri());
												System.err
														.println(modelrefvaluesclone);
												System.err
														.println("the above ids were found in the model but not the graph");
												totalErrors++;
											}

											nodereferences.remove(modelRefName);
											// rci.remove();
										}
									}

									if (nodereferences.size() > 0) {
										System.err
												.println("error in validating: references "
														+ nodereferences
																.keySet()
														+ " had targets in the graph but not in the model: ");
										System.err.println(nodereferences);
										totalErrors++;
									}

								}
							}
						}
						// if there are model elements not found in nodes
						if (eobjectCache.size() > 0) {
							System.err
									.println("error in validating: the following objects were not found in the graph:");
							System.err.println(eobjectCache.keySet());
							totalErrors++;
						}

						//

						t.success();
					} catch (Exception e) {
						System.err
								.println("syncChangeListener transaction error:");
						e.printStackTrace();
					}

				}

			}

		System.err.println("changed resource size: " + totalResourceSizes);

		System.err.println("relevant graph size: " + totalGraphSize);

		if (totalGraphSize != totalResourceSizes)
			totalErrors++;

		System.err
				.println("validated changes... "
						+ (totalErrors == 0 ? "true"
								: ((totalErrors == malformed) + " (with "
										+ totalErrors + " total and "
										+ malformed + " malformed errors)"))
						+ (removedProxies == 0 ? "" : " [" + removedProxies
								+ "] unresolved hawk proxies matched"));

	}

	private int latestChangedElements() {
		return changed.size();
	}

	private int latestDeletedElements() {
		return deleted;
	}

	private Set<String> removeHawkProxies(IGraphNode instance,
			Set<String> modelrefvaluesclone) {

		// String repoURL = "";
		// String destinationObjectRelativePathURI = "";
		//
		// String destinationObjectRelativeFileURI =
		// destinationObjectRelativePathURI
		// .substring(0, destinationObjectRelativePathURI.indexOf("#"));
		// String destinationObjectFullFileURI = repoURL
		// + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
		// + destinationObjectRelativeFileURI;

		for (String propertykey : instance.getPropertyKeys()) {

			if (propertykey
					.startsWith(GraphModelUpdater.PROXY_REFERENCE_PREFIX)) {

				String[] proxies = (String[]) instance.getProperty(propertykey);

				for (int i = 0; i < proxies.length; i = i + 4)
					if (modelrefvaluesclone.remove(proxies[i]))
						removedProxies++;
			}
		}

		return modelrefvaluesclone;
	}

	private boolean flattenedStringEquals(Object dbattr, Object attr) {

		String newdbattr = dbattr == null ? "null" : dbattr.toString();
		if (dbattr instanceof int[])
			newdbattr = Arrays.toString((int[]) dbattr);
		else if (dbattr instanceof long[])
			newdbattr = Arrays.toString((long[]) dbattr);
		else if (dbattr instanceof String[])
			newdbattr = Arrays.toString((String[]) dbattr);
		else if (dbattr instanceof boolean[])
			newdbattr = Arrays.toString((boolean[]) dbattr);
		else if (dbattr instanceof Object[])
			newdbattr = Arrays.toString((Object[]) dbattr);

		String newattr = attr == null ? "null" : attr.toString();
		if (attr instanceof int[])
			newattr = Arrays.toString((int[]) attr);
		else if (attr instanceof long[])
			newattr = Arrays.toString((long[]) attr);
		else if (attr instanceof String[])
			newattr = Arrays.toString((String[]) attr);
		else if (attr instanceof boolean[])
			newattr = Arrays.toString((boolean[]) attr);
		else if (attr instanceof Object[])
			newattr = Arrays.toString((Object[]) attr);

		return newdbattr.equals(newattr);

	}

	@Override
	public void changeStart() {

	}

	@Override
	public void changeSuccess() {

	}

	@Override
	public void changeFailure() {

	}

	@Override
	public void metamodelAddition(IHawkPackage pkg, IGraphNode pkgNode) {

	}

	@Override
	public void classAddition(IHawkClass cls, IGraphNode clsNode) {

	}

	@Override
	public void fileAddition(VcsCommitItem s, IGraphNode fileNode) {

	}

	@Override
	public void fileRemoval(VcsCommitItem s, IGraphNode fileNode) {

	}

	@Override
	public void modelElementAddition(VcsCommitItem s, IHawkObject element,
			IGraphNode elementNode, boolean isTransient) {
		changed.add(elementNode.getId().toString());
	}

	@Override
	public void modelElementRemoval(VcsCommitItem s, IGraphNode elementNode,
			boolean isTransient) {
		deleted++;
	}

	@Override
	public void modelElementAttributeUpdate(VcsCommitItem s,
			IHawkObject eObject, String attrName, Object oldValue,
			Object newValue, IGraphNode elementNode, boolean isTransient) {
		changed.add(elementNode.getId().toString());
	}

	@Override
	public void modelElementAttributeRemoval(VcsCommitItem s,
			IHawkObject eObject, String attrName, IGraphNode elementNode,
			boolean isTransient) {
		changed.add(elementNode.getId().toString());
	}

	@Override
	public void referenceAddition(VcsCommitItem s, IGraphNode source,
			IGraphNode destination, String edgelabel, boolean isTransient) {
		changed.add(source.getId().toString());
	}

	@Override
	public void referenceRemoval(VcsCommitItem s, IGraphNode source,
			IGraphNode destination, String edgelabel, boolean isTransient) {
		changed.add(source.getId().toString());
	}

	@Override
	public void setModelIndexer(IModelIndexer m) {
		System.err
				.println("use: SyncChangeListener(Git git, LinkedHashMap<String, RevCommit> commits, IModelIndexer hawk) constructor instead!");

	}

}
