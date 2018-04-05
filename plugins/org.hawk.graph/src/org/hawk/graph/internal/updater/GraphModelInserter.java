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
package org.hawk.graph.internal.updater;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawk.core.IModelIndexer;
import org.hawk.core.IVcsManager;
import org.hawk.core.VcsCommitItem;
import org.hawk.core.graph.IGraphChangeListener;
import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphIterable;
import org.hawk.core.graph.IGraphNode;
import org.hawk.core.graph.IGraphNodeIndex;
import org.hawk.core.graph.IGraphTransaction;
import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClass;
import org.hawk.core.model.IHawkModelResource;
import org.hawk.core.model.IHawkObject;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.query.IAccess;
import org.hawk.core.query.IAccessListener;
import org.hawk.core.query.IQueryEngine;
import org.hawk.core.query.InvalidQueryException;
import org.hawk.core.query.QueryExecutionException;
import org.hawk.graph.ModelElementNode;
import org.hawk.graph.internal.util.GraphUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * creates a database with the input xmi file (in args[0]) or reads it if the
 * database exists; and provides debugging information
 * 
 */
public class GraphModelInserter {
	private static final Logger LOGGER = LoggerFactory.getLogger(GraphModelInserter.class);

	// toggle to enabled detailed output of update process
	private static final boolean enableDebug = false;

	private static final int PROXY_RESOLVE_NOTIFY_INTERVAL = 25000;
	private static final int PROXY_RESOLVE_TX_SIZE = 5000;
	private static final int DERIVED_PNODE_TX_SIZE = 1000;

	private static final double maxTransactionalAcceptableLoadRatio = 0.5;

	@SuppressWarnings("unused")
	private int unset = 0; // number of unset references (used for logging)

	private String repoURL;
	private String tempDirURI;

	private IHawkModelResource resource;
	private Map<String, IHawkObject> updated = new HashMap<>();
	private Map<String, IHawkObject> added = new HashMap<>();
	private Map<String, IHawkObject> unchanged = new HashMap<>();
	private Map<String, IHawkObject> retyped = new HashMap<>();
	private double currentDeltaRatio;

	private IModelIndexer indexer;
	private IGraphDatabase graph;
	private GraphModelBatchInjector inj;
	private VcsCommitItem commitItem;

	private Map<String, IGraphNode> nodes = new HashMap<>();
	private TypeCache typeCache;

	public GraphModelInserter(IModelIndexer hawk, TypeCache typeCache) {
		this.indexer = hawk;
		this.graph = indexer.getGraph();
		this.typeCache = typeCache;
	}

	public boolean run(IHawkModelResource res, VcsCommitItem s, final boolean verbose) throws Exception {
		if (verbose) {
			indexer.getCompositeStateListener().info("Calculating model delta for file: " + s.getPath() + "...");
		}

		this.resource = res;
		this.commitItem = s;
		this.inj = new GraphModelBatchInjector(graph, typeCache, this.commitItem, indexer.getCompositeGraphChangeListener());

		final int delta = calculateModelDeltaSize(verbose);
		if (delta != -1) {
			this.tempDirURI = new File(graph.getTempDir()).toURI().toString();

			if (verbose) {
				LOGGER.debug("File already present, calculating deltas with respect to graph storage");
			}
			if (currentDeltaRatio > maxTransactionalAcceptableLoadRatio) {
				// System.err.print("[" + currentDeltaRatio + ">" +
				// maxTransactionalAcceptableLoadRatio + "] ");
				return batchUpdate(verbose);
			} else {
				// System.err.print("[" + currentDeltaRatio + "<=" +
				// maxTransactionalAcceptableLoadRatio + "] ");
				return transactionalUpdate(delta, verbose);
			}

			//
			// FIXMEdone -- at end of all updates similar to derived proxy
			// // for each change see if any derived attributes need
			// re-calculation - if they do add em to derived proxy
			// dictionary for re-calculation!
			//
		} else {
			// populate the database from scratch (for this file) -- this
			// will trigger calculation of all derived attrs
			return addNodes(verbose);
		}
	}

	@SuppressWarnings("unchecked")
	private boolean transactionalUpdate(int delta, final boolean verbose) throws Exception {
		graph.exitBatchMode();
		if (verbose) {
			indexer.getCompositeStateListener()
					.info("Performing transactional update (delta:" + delta + ") on file: " + commitItem.getPath() + "...");
			LOGGER.debug("transactional update called");
		}

		final IGraphChangeListener listener = indexer.getCompositeGraphChangeListener();
		try (IGraphTransaction t = graph.beginTransaction()) {
			listener.changeStart();

			repoURL = commitItem.getCommit().getDelta().getManager().getLocation();
			IGraphNode fileNode = graph.getFileIndex()
					.get("id", repoURL + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR + commitItem.getPath()).iterator().next();

			// manage retyped nodes
			for (final Map.Entry<String, IHawkObject> entry : retyped.entrySet()) {
				final String uriFragment = entry.getKey();
				final IHawkObject o = entry.getValue();

				final IGraphNode node = nodes.remove(uriFragment);
				remove(node, fileNode, listener);
				added.put(uriFragment, o);
			}

			// add new nodes
			final Map<IGraphNode, IHawkObject> addedNodes = new HashMap<>();
			final Map<String, IGraphNode> addedNodesHash = new HashMap<>();

			for (final String o : added.keySet()) {
				final IHawkObject object = added.get(o);
				final IGraphNode fileNode1 = fileNode;
				final IGraphNode node1 = inj.addEObject(fileNode1, object, resource.providesSingletonElements());
				addedNodes.put(node1, object);
				final String newID = node1.getProperty(IModelIndexer.IDENTIFIER_PROPERTY).toString();
				addedNodesHash.put(newID, node1);

				// track change new node
				for (final String transientLabelEdge : ModelElementNode.TRANSIENT_EDGE_LABELS) {
					for (final IGraphEdge e : node1.getOutgoingWithType(transientLabelEdge)) {
						listener.referenceAddition(commitItem, node1, e.getEndNode(), transientLabelEdge, true);
					}
				}
			}

			// references of added object and tracking of changes
			for (IGraphNode node : addedNodes.keySet()) {
				inj.addEReferences(fileNode, node, addedNodes.get(node), addedNodesHash, nodes);
			}

			// delete obsolete nodes and change attributes
			for (String s : nodes.keySet()) {

				IGraphNode node = nodes.get(s);

				if (unchanged.containsKey(node.getProperty(IModelIndexer.IDENTIFIER_PROPERTY))) {
					// do nothing as node is identical to current model element
				} else if (updated.containsKey(node.getProperty(IModelIndexer.IDENTIFIER_PROPERTY))) {
					// remove all old proxies of this node to other nodes (as
					// any new ones will be re-created)
					cleanupNode(node);
					// change properties of node to the new values
					final IHawkObject o = updated.get(node.getProperty(IModelIndexer.IDENTIFIER_PROPERTY));
					node.setProperty(IModelIndexer.SIGNATURE_PROPERTY, o.signature());
					updateNodeProperties(fileNode, node, o);
				} else {
					remove(node, fileNode, listener);
				}

			}

			// change references (including adding new proxies as required)
			for (String o : updated.keySet()) {
				IHawkObject source = updated.get(o);
				IGraphNode node = nodes.get(source.getUriFragment());

				// its null if it was just inserted (above), this is fine.
				if (node == null)
					continue;

				for (IHawkReference r : ((IHawkClass) source.getType()).getAllReferences()) {

					if (source.isSet(r)) {

						Object targets = source.get(r, false);
						String refname = r.getName();
						boolean isContainment = r.isContainment();
						boolean isContainer = r.isContainer();
						Set<String> targetids = new HashSet<>();

						if (targets instanceof Iterable<?>) {
							for (IHawkObject h : ((Iterable<IHawkObject>) targets)) {
								if (!h.isInDifferentResourceThan(source))
									targetids.add(h.getUriFragment());
								else {
									addProxyRef(node, h, refname, isContainment, isContainer);
								}
							}
						} else {
							if (!((IHawkObject) targets).isInDifferentResourceThan(source))
								targetids.add(((IHawkObject) targets).getUriFragment());
							else {
								addProxyRef(node, (IHawkObject) targets, refname, isContainment, isContainer);
							}
						}

						//
						Iterable<IGraphEdge> graphtargets = node.getOutgoingWithType(refname);

						for (IGraphEdge e : graphtargets) {
							IGraphNode n = e.getEndNode();

							final Object id = n.getProperty(IModelIndexer.IDENTIFIER_PROPERTY);
							final boolean targetIdPresent = targetids.remove(id);
							if (!targetIdPresent) {
								// need to store this before we delete the edge:
								// once we delete() it might be impossible to
								// retrieve it
								final String edgeType = e.getType();

								// delete removed reference
								e.delete();

								// track change deleted reference
								listener.referenceRemoval(this.commitItem, node, n, edgeType, false);
							}
						}

						for (String s : targetids) {
							IGraphNode dest = nodes.get(s);
							if (dest == null) {
								dest = addedNodesHash.get(s);
							}

							if (dest == null)
								dest = addedNodesHash.get(s);
							if (dest != null) {
								// add new reference
								final Map<String, Object> props = new HashMap<>();
								if (isContainment) {
									props.put(ModelElementNode.EDGE_PROPERTY_CONTAINMENT, "true");
								}
								if (isContainer) {
									props.put(ModelElementNode.EDGE_PROPERTY_CONTAINER, "true");
								}
								graph.createRelationship(node, dest, refname, props);

								// track change new reference
								listener.referenceAddition(this.commitItem, node, dest, refname, false);
							} else {
								// proxy reference, handled above
							}
						}

					} else {
						// delete unset references which may have been
						// previously set
						String refname = r.getName();
						Iterable<IGraphEdge> graphtargets = node.getOutgoingWithType(refname);
						// track change deleted references
						for (IGraphEdge e : graphtargets) {
							final IGraphNode endNode = e.getEndNode();
							final String type = e.getType();
							e.delete();
							listener.referenceRemoval(this.commitItem, node, endNode, type, false);
						}
					}

				} // for (IHawkReference r)

			} // for (String o)

			fileNode.setProperty("revision", commitItem.getCommit().getRevision());
			t.success();
			listener.changeSuccess();
			return true;
		} catch (Exception e) {
			LOGGER.error("exception in transactionalUpdate()", e);
			listener.changeFailure();
			return false;
		} finally {
			if (verbose) {
				indexer.getCompositeStateListener()
						.info("Performed transactional update on file: " + commitItem.getPath() + ".");
			}
		}

	}

	private void cleanupNode(IGraphNode node) {

		IGraphNodeIndex proxyDictionary = graph.getOrCreateNodeIndex(GraphModelBatchInjector.PROXY_DICT_NAME);
		proxyDictionary.remove(node);

		for (String propertyKey : node.getPropertyKeys()) {
			if (propertyKey.startsWith(GraphModelUpdater.PROXY_REFERENCE_PREFIX))
				node.removeProperty(propertyKey);
		}

	}

	protected void remove(IGraphNode node, IGraphNode fileNode, final IGraphChangeListener listener) {
		// not in unchanged or updated so its deleted
		//
		// System.err.println("deleting node " + node +
		// " as new model does not contain it!");
		//

		// track change deleted node
		for (String key : node.getPropertyKeys()) {
			listener.modelElementAttributeRemoval(this.commitItem, null, key, node,
					ModelElementNode.TRANSIENT_ATTRIBUTES.contains(key));
		}
		for (IGraphEdge e : node.getOutgoing()) {
			if (e.getProperty("isDerived") == null) {
				final boolean isTransient = ModelElementNode.TRANSIENT_EDGE_LABELS.contains(e.getType());
				listener.referenceRemoval(this.commitItem, node, e.getEndNode(), e.getType(), isTransient);
			}
		}

		remove(node, repoURL, fileNode, listener);
		// new DeletionUtils(graph).delete(node);
	}

	private boolean addProxyRef(final IGraphNode node, final IHawkObject destinationObject, final String edgelabel,
			boolean isContainment, boolean isContainer) {

		try {
			// TODO this seems duplicated (see GraphModelBatchInjector#addProxyRef)

			// proxydictionary.add(graph.getNodeById(hash.get((from))),
			// edgelabel, ((EObject)destinationObject).eIsProxy());

			final String uri = destinationObject.getUri();

			String destinationObjectRelativePathURI = uri;
			if (!destinationObject.URIIsRelative()) {
				if (destinationObjectRelativePathURI.startsWith(tempDirURI)) {
					destinationObjectRelativePathURI = destinationObjectRelativePathURI.substring(tempDirURI.length());
				} else {
					final IVcsManager vcs = commitItem.getCommit().getDelta().getManager();
					destinationObjectRelativePathURI = vcs.getRepositoryPath(destinationObjectRelativePathURI);
				}
			}

			String destinationObjectRelativeFileURI = destinationObjectRelativePathURI;

			destinationObjectRelativeFileURI = destinationObjectRelativePathURI
					.substring(destinationObjectRelativePathURI.indexOf("#"));

			String destinationObjectFullPathURI = repoURL + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
					+ destinationObjectRelativePathURI;

			String destinationObjectFullFileURI = repoURL + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR
					+ destinationObjectRelativeFileURI;

			Object proxies = null;
			// if
			// (withProxy.hasProperty(GraphModelUpdater.PROXY_REFERENCE_PREFIX +
			// relativeFileURI)) {
			// proxies =
			// withProxy.getProperty(GraphModelUpdater.PROXY_REFERENCE_PREFIX +
			// relativeFileURI);
			// }
			// System.err.println(">>>>>>>"+relativeFileURI);

			proxies = node.getProperty(GraphModelUpdater.PROXY_REFERENCE_PREFIX + destinationObjectFullFileURI);
			proxies = new Utils().addToElementProxies((String[]) proxies, destinationObjectFullPathURI, edgelabel,
					isContainment, isContainer);

			node.setProperty(GraphModelUpdater.PROXY_REFERENCE_PREFIX + destinationObjectFullFileURI, proxies);

			HashMap<String, Object> m = new HashMap<>();
			m.put(GraphModelUpdater.PROXY_REFERENCE_PREFIX, destinationObjectFullFileURI);

			IGraphNodeIndex proxyDictionary = graph.getOrCreateNodeIndex(GraphModelBatchInjector.PROXY_DICT_NAME);
			proxyDictionary.add(node, m);

		} catch (Exception e) {
			LOGGER.error("proxydictionary error", e);
			return false;
		}
		return true;
	}

	private void updateNodeProperties(IGraphNode fileNode, IGraphNode node, IHawkObject eObject) {

		List<IHawkAttribute> normalattributes = new LinkedList<IHawkAttribute>();
		List<IHawkAttribute> indexedattributes = new LinkedList<IHawkAttribute>();
		IGraphNode typenode = node.getOutgoingWithType(ModelElementNode.EDGE_LABEL_OFTYPE).iterator().next()
				.getEndNode();
		final IGraphChangeListener listener = indexer.getCompositeGraphChangeListener();

		for (final IHawkAttribute eAttribute : ((IHawkClass) eObject.getType()).getAllAttributes()) {
			final String attrName = eAttribute.getName();
			if (eObject.isSet(eAttribute)) {
				final String[] propValue = (String[]) typenode.getProperty(attrName);
				if (propValue != null && propValue[5].equals("t")) {
					indexedattributes.add(eAttribute);
				}

				normalattributes.add(eAttribute);
			} else if (node.getProperty(attrName) != null) {
				node.removeProperty(attrName);
				indexer.getCompositeGraphChangeListener().modelElementAttributeRemoval(commitItem, eObject, eAttribute.getName(),
						node, false);
			}
		}

		for (IHawkAttribute a : normalattributes) {
			final Object oldproperty = node.getProperty(a.getName());
			final Object newproperty = eObject.get(a);

			if (!a.isMany()) {
				Object newValue = newproperty;
				if (!GraphUtil.isPrimitiveOrWrapperType(newproperty.getClass())) {
					newValue = newValue.toString();
				}

				if (!newValue.equals(oldproperty)) {
					// track changed property (primitive)
					listener.modelElementAttributeUpdate(this.commitItem, eObject, a.getName(), oldproperty, newValue, node,
							false);
					node.setProperty(a.getName(), newValue);
				}
			} else {
				Collection<Object> collection = null;

				if (a.isUnique())
					collection = new LinkedHashSet<Object>();
				else
					collection = new LinkedList<Object>();

				final Collection<?> srcCollection = (Collection<?>) newproperty;
				Class<?> elemClass = null;
				boolean primitiveOrWrapperClass = false;
				if (!srcCollection.isEmpty()) {
					final Object first = srcCollection.iterator().next();
					elemClass = first.getClass();
					primitiveOrWrapperClass = GraphUtil.isPrimitiveOrWrapperType(elemClass);
					if (primitiveOrWrapperClass) {
						for (Object o : srcCollection) {
							collection.add(o);
						}
					} else {
						for (Object o : srcCollection) {
							collection.add(o.toString());
						}
					}
				}

				Object r = null;
				if (primitiveOrWrapperClass && elemClass != null) {
					r = Array.newInstance(elemClass, collection.size());
				} else {
					r = Array.newInstance(String.class, collection.size());
				}
				Object ret = collection.toArray((Object[]) r);

				if (!ret.equals(oldproperty)) {
					listener.modelElementAttributeUpdate(this.commitItem, eObject, a.getName(), oldproperty, ret, node, false);
					node.setProperty(a.getName(), ret);
				}
			}

		}

		for (IHawkAttribute a : indexedattributes) {

			IGraphNodeIndex i = graph.getOrCreateNodeIndex(
					eObject.getType().getPackageNSURI() + "##" + eObject.getType().getName() + "##" + a.getName());

			Object v = eObject.get(a);

			if (!a.isMany()) {
				if (GraphUtil.isPrimitiveOrWrapperType(v.getClass())) {
					i.add(node, a.getName(), v);
				} else {
					i.add(node, a.getName(), v.toString());
				}
			} else {
				Collection<Object> collection = null;

				if (a.isUnique())
					collection = new LinkedHashSet<Object>();
				else
					collection = new LinkedList<Object>();

				final Collection<?> srcCollection = (Collection<?>) v;
				Class<?> elemClass = null;
				boolean primitiveOrWrapperClass = false;
				if (!srcCollection.isEmpty()) {
					final Object first = srcCollection.iterator().next();
					elemClass = first.getClass();
					primitiveOrWrapperClass = GraphUtil.isPrimitiveOrWrapperType(elemClass);
					if (primitiveOrWrapperClass) {
						for (Object o : srcCollection) {
							collection.add(o);
						}
					} else {
						for (Object o : srcCollection) {
							collection.add(o.toString());
						}
					}
				}

				Object r = null;
				if (primitiveOrWrapperClass && elemClass != null) {
					r = Array.newInstance(elemClass, 1);
				} else {
					r = Array.newInstance(String.class, 1);
				}
				Object ret = collection.toArray((Object[]) r);

				i.add(node, a.getName(), ret);

			}

		}

		final IGraphNodeIndex rootDictionary = graph.getOrCreateNodeIndex(GraphModelBatchInjector.ROOT_DICT_NAME);
		if (eObject.isRoot()) {
			rootDictionary.add(node, GraphModelBatchInjector.ROOT_DICT_FILE_KEY, fileNode.getId());
		} else {
			rootDictionary.remove(node);
		}
	}

	private boolean batchUpdate(final boolean verbose) throws Exception {
		if (verbose) {
			indexer.getCompositeStateListener().info("Performing batch update of file: " + commitItem.getPath() + "...");
		}
		final IGraphChangeListener listener = indexer.getCompositeGraphChangeListener();
		listener.changeStart();
		try {
			IGraphNode g = new Utils().getFileNodeFromVCSCommitItem(graph, commitItem);

			if (g != null) {
				try (IGraphTransaction t = graph.beginTransaction()) {
					new DeletionUtils(graph).deleteAll(g, commitItem, listener);
					t.success();
				}
			}
			graph.enterBatchMode();
			new GraphModelBatchInjector(indexer, typeCache, commitItem, resource, listener, verbose);
			listener.changeSuccess();
			return true;
		} catch (Exception ex) {
			listener.changeFailure();
			return false;
		} finally {
			if (verbose) {
				indexer.getCompositeStateListener().info("Performed batch update of file: " + commitItem.getPath() + ".");
			}
		}
	}

	private int calculateModelDeltaSize(boolean verbose) throws Exception {
		if (verbose) {
			LOGGER.info("calculateModelDeltaSize() called");
		}

		if (new Utils().getFileNodeFromVCSCommitItem(graph, commitItem) != null) {

			try (IGraphTransaction t = graph.beginTransaction()) {

				final String repositoryURL = commitItem.getCommit().getDelta().getManager().getLocation();

				HashMap<String, byte[]> signatures = new HashMap<>();

				// Get existing nodes from the store (and their signatures)
				for (IGraphEdge e : graph.getFileIndex()
						.get("id", repositoryURL + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR + commitItem.getPath()).getSingle()
						.getIncomingWithType(ModelElementNode.EDGE_LABEL_FILE)) {
					IGraphNode n = e.getStartNode();

					nodes.put(n.getProperty(IModelIndexer.IDENTIFIER_PROPERTY).toString(), n);

					signatures.put((String) n.getProperty(IModelIndexer.IDENTIFIER_PROPERTY),
							(byte[]) n.getProperty(IModelIndexer.SIGNATURE_PROPERTY));
				}
				if (verbose) {
					LOGGER.info("File contains: {} ({}) nodes in store", nodes.size(), signatures.size());
				}

				// Get the model elements from the resource and use signatures
				// and URI
				for (IHawkObject o : resource.getAllContents()) {
					final String uriFragment = o.getUriFragment();
					byte[] hash = signatures.get(uriFragment);
					if (hash != null) {
						if (!Arrays.equals(hash, o.signature())) {
							final String actualType = o.getType().getName();

							final IGraphNode node = nodes.get(uriFragment);
							final Iterator<IGraphEdge> typeEdges = node
									.getOutgoingWithType(ModelElementNode.EDGE_LABEL_OFTYPE).iterator();
							final IGraphNode typeNode = typeEdges.next().getEndNode();
							final String nodeType = typeNode.getProperty(IModelIndexer.IDENTIFIER_PROPERTY).toString();
							if (actualType.equals(nodeType)) {
								this.updated.put(uriFragment, o);
							} else {
								// The model element with this URI fragment has
								// changed type
								// from the previous version of the model to the
								// current version.
								this.retyped.put(uriFragment, o);
							}
						} else {
							this.unchanged.put(uriFragment, o);
						}
					} else {
						this.added.put(uriFragment, o);
					}
				}
				t.success();
				int addedn = added.size();
				int retypedn = retyped.size();
				int updatedn = updated.size();
				int deletedn = nodes.size() - unchanged.size() - updated.size() - retyped.size();
				currentDeltaRatio = (addedn + retypedn + updatedn + deletedn) / ((double) nodes.size());
				if (verbose) {
					LOGGER.info("Update contains | a:{} u:{} d:{} ratio: {}",
						(addedn + retypedn), updatedn, deletedn, currentDeltaRatio);
				}

				return addedn + retypedn + updatedn + deletedn;
			}
		} else {
			if (verbose) {
				LOGGER.info("File not in store, performing initial batch file insertion");
			}
			currentDeltaRatio = -1;
			return -1;
		}
	}

	/**
	 * Populates the database with the model, using util.parseresource
	 * 
	 * @param verbose
	 * 
	 * @return
	 * 
	 * @throws Exception
	 */
	private boolean addNodes(boolean verbose) throws Exception {
		if (verbose) {
			indexer.getCompositeStateListener().info("Performing batch insert on file: " + commitItem.getPath() + "...");
		}
		boolean success = true;
		if (resource != null) {
			GraphModelBatchInjector batch = new GraphModelBatchInjector(indexer, typeCache, commitItem, resource,
					indexer.getCompositeGraphChangeListener(), verbose);
			unset = batch.getUnset();
			success = batch.getSuccess();
			if (!success) {
				LOGGER.error(
						"model insertion aborted: see above error (maybe you need to register the metamodel?)");
			}
		} else {
			LOGGER.error("model insertion aborted, see above error (maybe you need to register the metamodel?)");
		}

		if (verbose) {
			indexer.getCompositeStateListener().info("Performed batch insert on file: " + commitItem.getPath() + ".");
		}
		return success;
	}

	private void remove(IGraphNode modelElement, String repositoryURL, IGraphNode fileNode, IGraphChangeListener l) {
		DeletionUtils del = new DeletionUtils(graph);
		del.dereference(modelElement, l, commitItem);
		del.makeProxyRefs(commitItem, modelElement, repositoryURL, fileNode, l);
		if (del.delete(modelElement))
			l.modelElementRemoval(this.commitItem, modelElement, false);
	}

	/*
	 * Deprected
	 */
	@SuppressWarnings("unused")
	private void init(File dbloc) {

		try {

			// create resource set
			// modelResourceSet = new ResourceSetImpl();
			// modelResourceSet.getResourceFactoryRegistry()
			// .getExtensionToFactoryMap()
			// .put("ecore", new EcoreResourceFactoryImpl());
			// modelResourceSet.getResourceFactoryRegistry()
			// .getExtensionToFactoryMap()
			// .put("*", new XMIResourceFactoryImpl());

			long cpu = System.nanoTime();

			// String db = System.getProperty("user.dir").replaceAll("\\\\",
			// "/")
			// + "/svntest";
			String db = dbloc.getPath();

			long x = Runtime.getRuntime().maxMemory() / 1000000 / 60;

			Map<String, String> config = new HashMap<String, String>();
			config.put("neostore.nodestore.db.mapped_memory", 3 * x + "M");
			config.put("neostore.relationshipstore.db.mapped_memory", 14 * x + "M");
			config.put("neostore.propertystore.db.mapped_memory", x + "M");
			config.put("neostore.propertystore.db.strings.mapped_memory", 2 * x + "M");
			config.put("neostore.propertystore.db.arrays.mapped_memory", x + "M");

			// File fi = new File(db);

			// deletion of database each time used for debugging, remove to
			// make
			// it reference a static db
			// NB: DELETE ALL DATABASES BEFORE COMMITING ELSE YOU
			// WILL MAKE THE SVN ANGRY!!!
			// if (fi.exists()) {
			// if (!deleteDir(fi)) {
			// System.err
			// .println("Cannot delete database, exiting program, io error!");
			// oldSystemdotexit(1);
			// }
			// }

			// graph = new EmbeddedGraphDatabase(db, config);

			// registerShutdownHook(graph);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int resolveProxies(IGraphDatabase graph) throws Exception {
		final long start = System.currentTimeMillis();
		int proxiesLeft = -1;

		final IGraphChangeListener listener = indexer.getCompositeGraphChangeListener();
		List<IGraphNode> proxiesToBeResolved = new LinkedList<>();
		try (IGraphTransaction tx = graph.beginTransaction()) {
			IGraphNodeIndex proxyDictionary = graph.getOrCreateNodeIndex(GraphModelBatchInjector.PROXY_DICT_NAME);
			IGraphIterable<IGraphNode> proxies = proxyDictionary.query(GraphModelUpdater.PROXY_REFERENCE_PREFIX, "*");
			for (IGraphNode n : proxies) {
				proxiesToBeResolved.add(n);
			}
			tx.success();
		}

		final int nToBeResolved = proxiesToBeResolved.size();
		final long startMillis = System.currentTimeMillis();
		int totalProcessed = 0, currentProcessed = 0;
		if (proxiesToBeResolved != null && nToBeResolved > 0) {
			Iterator<IGraphNode> itProxies = proxiesToBeResolved.iterator();

			// Resolve proxies in batches, to keep the size of the tx bounded
			// and avoid excessive memory usage
			while (itProxies.hasNext()) {
				int nBatch = 0;
				try (IGraphTransaction tx = graph.beginTransaction()) {
					listener.changeStart();
					while (itProxies.hasNext() && nBatch < PROXY_RESOLVE_TX_SIZE) {
						IGraphNode n = itProxies.next();
						itProxies.remove();
						IGraphNodeIndex proxyDictionary = graph.getOrCreateNodeIndex(GraphModelBatchInjector.PROXY_DICT_NAME);
						resolveProxies(graph, listener, n, proxyDictionary);
						++nBatch;
					}
					tx.success();
					listener.changeSuccess();
				} catch (Throwable ex) {
					listener.changeFailure();
					throw ex;
				}

				currentProcessed += nBatch;
				if (currentProcessed >= PROXY_RESOLVE_NOTIFY_INTERVAL) {
					totalProcessed += currentProcessed;
					currentProcessed = 0;
					final long elapsedSeconds = (System.currentTimeMillis() - startMillis) / 1000;
					indexer.getCompositeStateListener()
							.info(String.format("Processed %d/%d nodes with proxies (%dsec total)", totalProcessed,
									nToBeResolved, elapsedSeconds));
				}
			}
		}

		try (IGraphTransaction tx = graph.beginTransaction()) {
			IGraphNodeIndex proxyDictionary = graph.getOrCreateNodeIndex(GraphModelBatchInjector.PROXY_DICT_NAME);

			proxiesLeft = proxyDictionary.query(GraphModelUpdater.PROXY_REFERENCE_PREFIX, "*").size();

			LOGGER.info("{} - sets of proxy references left in the store", proxiesLeft);
			tx.success();

		}

		LOGGER.info("proxy resolution took: ~{}s", (System.currentTimeMillis() - start) / 1000.0);
		return proxiesLeft;
	}

	private void resolveProxies(IGraphDatabase graph, final IGraphChangeListener listener, IGraphNode n,
			IGraphNodeIndex proxyDictionary) throws Exception {
		Set<String[]> allProxies = new HashSet<>();
		for (String propertyKey : n.getPropertyKeys()) {
			if (propertyKey.startsWith(GraphModelUpdater.PROXY_REFERENCE_PREFIX)) {
				final String[] propertyValue = (String[]) n.getProperty(propertyKey);
				allProxies.add(propertyValue);
			}
		}

		for (String[] proxies : allProxies) {
			final String fullPathURI = proxies[0].substring(0, proxies[0].indexOf("#"));

			int seploc = fullPathURI.indexOf(GraphModelUpdater.FILEINDEX_REPO_SEPARATOR);
			String[] repoFile = { fullPathURI.substring(0, seploc),
					fullPathURI.substring(seploc + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR.length()) };

			final String repoURL = repoFile[0];
			String filePath = repoFile[1];
			//NB: we forced file paths to start with a slash
			if (!filePath.startsWith("/")) {
				filePath = "/" + filePath;
			}

			final Set<IGraphNode> nodes = new HashSet<IGraphNode>();
			final IGraphNode fileNode = getFileNode(graph, repoURL, filePath);
			Iterable<IGraphEdge> rels = allNodesWithFile(fileNode);
			if (rels != null) {
				for (IGraphEdge r : rels) {
					nodes.add(r.getStartNode());
				}
			}

			final boolean isFragmentBased = filePath.equals("/" + GraphModelUpdater.PROXY_FILE_WILDCARD);
			if (nodes.size() != 0 || isFragmentBased) {
				String[] remainingProxies = proxies.clone();

				// for each proxy
				for (int i = 0; i < proxies.length; i = i + 4) {
					boolean resolved = false;

					final String uri = proxies[i];
					final String fragment = uri.substring(uri.indexOf("#") + 1);
					final String edgeLabel = proxies[i + 1];
					final boolean isContainment = Boolean.valueOf(proxies[i + 2]);
					final boolean isContainer = Boolean.valueOf(proxies[i + 3]);

					for (IGraphNode no : nodes) {
						String nodeURI = fullPathURI + "#"
								+ no.getProperty(IModelIndexer.IDENTIFIER_PROPERTY).toString();

						if (nodeURI.equals(uri)) {
							boolean change = new GraphModelBatchInjector(graph, typeCache, null, listener)
									.resolveProxyRef(n, no, edgeLabel, isContainment, isContainer);

							if (!change) {
								// System.err.println("resolving
								// proxy ref returned false, edge
								// already existed: "+
								// n.getId()+ " - "+ edgeLabel+
								// " -> "+ no.getId());
							} else {
								resolved = true;
								listener.referenceAddition(this.commitItem, n, no, edgeLabel, false);
							}
							break;
						}
					}

					if (!resolved && isFragmentBased) {
						// fragment-based proxy resolution (e.g. for Modelio)
						IGraphNodeIndex fragDictionary = graph
								.getOrCreateNodeIndex(GraphModelBatchInjector.FRAGMENT_DICT_NAME);
						Iterator<IGraphNode> targetNodes = fragDictionary.get("id", fragment).iterator();
						if (targetNodes.hasNext()) {
							final IGraphNode no = targetNodes.next();
							boolean change = new GraphModelBatchInjector(graph, typeCache, null, listener)
									.resolveProxyRef(n, no, edgeLabel, isContainment, isContainer);
							if (change) {
								resolved = true;
								listener.referenceAddition(this.commitItem, n, no, edgeLabel, false);
							}
						}

					}

					if (resolved) {
						remainingProxies = new Utils().removeFromElementProxies(remainingProxies, uri, edgeLabel,
								isContainment, isContainer);
					}

				}

				if (remainingProxies == null || remainingProxies.length == 0) {
					n.removeProperty(GraphModelUpdater.PROXY_REFERENCE_PREFIX + fullPathURI);
					proxyDictionary.remove(n, GraphModelUpdater.PROXY_REFERENCE_PREFIX, fullPathURI);
				} else {
					n.setProperty(GraphModelUpdater.PROXY_REFERENCE_PREFIX + fullPathURI, remainingProxies);
				}
			}
		}
	}

	public int resolveDerivedAttributeProxies(String type) throws Exception {
		IGraphIterable<IGraphNode> allUnresolved = null;
		IGraphNodeIndex derivedProxyDictionary = null;
		int size = 0;

		LOGGER.info("Deriving attributes...");
		try (IGraphTransaction tx = graph.beginTransaction()) {
			derivedProxyDictionary = graph.getOrCreateNodeIndex("derivedproxydictionary");
			allUnresolved = derivedProxyDictionary.query("derived", "*");
			size = allUnresolved.size();
			tx.success();
		}

		if (size > 0) {
			processDerivedFeatureNodes(type, allUnresolved, size);
		}

		int derivedLeft = -1;
		try (IGraphTransaction tx = graph.beginTransaction()) {
			derivedLeft = ((IGraphIterable<IGraphNode>) derivedProxyDictionary.query("derived", "*")).size();
			tx.success();
		}
		LOGGER.info("{} - sets of proxy [derived] attributes left incomplete in the store", derivedLeft);

		return derivedLeft;
	}

	protected void processDerivedFeatureNodes(final String type, final Iterable<IGraphNode> derivedFeatureNodes, final int nNodes)
			throws InvalidQueryException, QueryExecutionException, Exception {
		final long startMillis = System.currentTimeMillis();
		final IQueryEngine q = indexer.getKnownQueryLanguages().get(type);

		// Process derived nodes in chunks, to keep the size of the access listener and the transactions bounded
		int count = 0;
		final Iterator<IGraphNode> itUnresolved = derivedFeatureNodes.iterator();
		boolean done = false;
		while (!done) {
			try (IGraphTransaction tx = graph.beginTransaction()) {
				final long startChunkMillis = System.currentTimeMillis();

				final List<IGraphNode> chunk = new ArrayList<>(DERIVED_PNODE_TX_SIZE);
				for (int i = 0; i < DERIVED_PNODE_TX_SIZE && itUnresolved.hasNext(); i++) {
					chunk.add(itUnresolved.next());
				}
				done = !itUnresolved.hasNext();

				final IGraphNodeIndex derivedAccessDictionary = graph.getOrCreateNodeIndex("derivedaccessdictionary");
				final IAccessListener accessListener = q.calculateDerivedAttributes(indexer, chunk);

				// dump access to Lucene and add hooks on updates
				// TODO - break transactions by accesses, not by derived nodes
				for (IAccess a : accessListener.getAccesses()) {
					final IGraphNode sourceNode = graph.getNodeById(a.getSourceObjectID());
					derivedAccessDictionary.remove(sourceNode);
				}
				for (IAccess a : accessListener.getAccesses()) {
					final IGraphNode sourceNode = graph.getNodeById(a.getSourceObjectID());
					derivedAccessDictionary.add(sourceNode, a.getAccessObjectID(), a.getProperty());
				}

				if (enableDebug) {
					/* high overhead in certain corner cases (modelio -- large workspace -- only enable for debugging) */
					LOGGER.info("accesses: {} ({} nodes)", accessListener.getAccesses().size(), derivedAccessDictionary.query("*", "*").size());
				}

				accessListener.resetAccesses();
				tx.success();

				count += chunk.size();
				final long now = System.currentTimeMillis();
				final long chunkMillis = now - startChunkMillis;
				final long totalMillis = now - startMillis;
				indexer.getCompositeStateListener().info(String.format(
						"Processed %d/%d derived feature nodes of type '%s' (%d s, %d s total)",
								count, nNodes, type, chunkMillis / 1000, totalMillis / 1000));
			}
		}
	}

	public void updateDerivedAttributes(String type, Set<IGraphNode> nodesToBeUpdated) throws Exception {

		final IGraphChangeListener listener = indexer.getCompositeGraphChangeListener();

		// This is done outside any other tx, as we need to be able to break up
		// derivation into smaller tx
		IQueryEngine q = indexer.getKnownQueryLanguages().get(type);
		IAccessListener accessListener = q.calculateDerivedAttributes(indexer, nodesToBeUpdated);

		try (IGraphTransaction tx = graph.beginTransaction()) {
			listener.changeStart();
			// operations on the graph
			// ...

			// not needed as indexes should be up to date
			// nodesToBeUpdated = graph.retainExisting(nodesToBeUpdated);

			IGraphNodeIndex derivedAccessDictionary = graph.getOrCreateNodeIndex("derivedaccessdictionary");
			for (IAccess a : accessListener.getAccesses()) {
				IGraphNode sourceNode = graph.getNodeById(a.getSourceObjectID());

				if (sourceNode != null)
					derivedAccessDictionary.remove(sourceNode);
			}

			for (IAccess a : accessListener.getAccesses()) {
				IGraphNode sourceNode = graph.getNodeById(a.getSourceObjectID());

				if (sourceNode != null)
					derivedAccessDictionary.add(sourceNode, a.getAccessObjectID(), a.getProperty());
			}

			tx.success();
			listener.changeSuccess();
		} catch (Exception e) {
			listener.changeFailure();
			throw e;
		}
	}

	private static Iterable<IGraphEdge> allNodesWithFile(final IGraphNode fileNode) {
		if (fileNode != null)
			return fileNode.getIncomingWithType(ModelElementNode.EDGE_LABEL_FILE);
		else
			return null;
	}

	private IGraphNode getFileNode(IGraphDatabase graph, String repositoryURL, String file) {
		final IGraphNodeIndex filedictionary = graph.getFileIndex();
		IGraphNode fileNode = null;

		try {
			final String idSameRepo = repositoryURL + GraphModelUpdater.FILEINDEX_REPO_SEPARATOR + file;
			final IGraphIterable<IGraphNode> itNodes = filedictionary.get("id", idSameRepo);
			if (itNodes.size() > 0) {
				fileNode = itNodes.getSingle();
			} else {
				// TODO do this with just the graph - new index?

				// Try looking in another repository
				file = file.replaceFirst("^/", "");
				for (IVcsManager vcs : indexer.getRunningVCSManagers()) {
					if (file.startsWith(vcs.getLocation())) {
						String subpath = file.substring(vcs.getLocation().length());
						if (!subpath.startsWith("/")) {
							subpath = "/" + subpath;
						}
						fileNode = getFileNode(graph, vcs.getLocation(), subpath);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}

		return fileNode;
	}

	public void updateDerivedAttribute(String metamodelUri, String typeName, String attributeName, String attributeType,
			boolean isMany, boolean isOrdered, boolean isUnique, String derivationlanguage, String derivationlogic) {

		final long startMillis = System.currentTimeMillis();
		LOGGER.info("Creating / updating derived attribute {}::{}#{}", metamodelUri, typeName, attributeName);

		// Add the new derived property nodes
		Set<IGraphNode> derivedPropertyNodes = new HashSet<>();
		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...
			IGraphNode metamodelNode = graph.getMetamodelIndex().get("id", metamodelUri).getSingle();

			IGraphNode typeNode = null;

			for (IGraphEdge e : metamodelNode.getIncomingWithType("epackage")) {
				IGraphNode othernode = e.getStartNode();
				if (othernode.getProperty(IModelIndexer.IDENTIFIER_PROPERTY).equals(typeName)) {
					typeNode = othernode;
					break;
				}
			}

			HashSet<IGraphNode> nodes = new HashSet<>();
			for (IGraphEdge e : typeNode.getIncomingWithType(ModelElementNode.EDGE_LABEL_OFTYPE)) {
				nodes.add(e.getStartNode());
			}
			for (IGraphEdge e : typeNode.getIncomingWithType(ModelElementNode.EDGE_LABEL_OFKIND)) {
				nodes.add(e.getStartNode());
			}

			for (IGraphNode instanceNode : nodes) {

				Iterator<IGraphEdge> derived = instanceNode.getOutgoingWithType(attributeName).iterator();

				HashMap<String, Object> m = new HashMap<>();
				m.put("isMany", isMany);
				m.put("isOrdered", isOrdered);
				m.put("isUnique", isUnique);
				m.put("attributetype", attributeType);
				m.put("derivationlanguage", derivationlanguage);
				m.put("derivationlogic", derivationlogic);
				m.put(attributeName, DirtyDerivedAttributesListener.NOT_YET_DERIVED_PREFIX + derivationlogic);

				// derived node exists -- update derived property
				if (derived.hasNext()) {

					IGraphNode derivedPropertyNode = derived.next().getEndNode();

					for (String s : m.keySet())
						derivedPropertyNode.setProperty(s, m.get(s));

					derivedPropertyNodes.add(derivedPropertyNode);

				} else// derived node does not exist -- create derived property
				{

					IGraphNode derivedPropertyNode = graph.createNode(m, "derivedattribute");

					m.clear();
					m.put("isDerived", true);

					graph.createRelationship(instanceNode, derivedPropertyNode, attributeName, m);

					derivedPropertyNodes.add(derivedPropertyNode);

				}

			}

			if (enableDebug) {
				LOGGER.info("{} instances found.\ncalculating derived attribute for instances...", derivedPropertyNodes.size());
			}

			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		// derive the new property
		try {
			processDerivedFeatureNodes(derivationlanguage, derivedPropertyNodes, derivedPropertyNodes.size());
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		LOGGER.info("Finished adding derived feature in {}s", (System.currentTimeMillis() - startMillis) / 1000.0);
	}

	public void updateIndexedAttribute(String metamodeluri, String typename, String attributename) {

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...

			IGraphNodeIndex i = graph.getOrCreateNodeIndex(metamodeluri + "##" + typename + "##" + attributename);

			IGraphNode typeNode = null;

			for (IGraphEdge r : graph.getMetamodelIndex().get("id", metamodeluri).getSingle()
					.getIncomingWithType("epackage")) {

				IGraphNode othernode = r.getStartNode();
				if (othernode.getProperty(IModelIndexer.IDENTIFIER_PROPERTY).equals(typename)) {
					typeNode = othernode;
					break;
				}

			}

			// a isMany isOrdered isUnique attrType isIndexed
			String[] metadata = (String[]) typeNode.getProperty(attributename);

			boolean isPrimitiveOrWrapperType = false;
			Class<?> c = String.class;

			try {
				c = Class.forName(metadata[4]);
				isPrimitiveOrWrapperType = GraphUtil.isPrimitiveOrWrapperType(c);
			} catch (Exception e) {
				//
				e.printStackTrace();
			}

			HashSet<IGraphNode> nodes = new HashSet<>();
			for (IGraphEdge e : typeNode.getIncomingWithType(ModelElementNode.EDGE_LABEL_OFTYPE)) {
				nodes.add(e.getStartNode());
			}
			for (IGraphEdge e : typeNode.getIncomingWithType(ModelElementNode.EDGE_LABEL_OFKIND)) {
				nodes.add(e.getStartNode());
			}

			for (IGraphNode node : nodes) {

				Map<String, Object> m = new HashMap<>();

				if (!"t".equals(metadata[1])) {

					if (isPrimitiveOrWrapperType)
						m.put(attributename, node.getProperty(attributename));

					else
						m.put(attributename, node.getProperty(attributename).toString());

				}

				else {

					Collection<Object> collection = null;

					if ("t".equals(metadata[3]))
						collection = new LinkedHashSet<Object>();
					else
						collection = new LinkedList<Object>();

					for (Object o : (Collection<?>) node.getProperty(attributename)) {

						if (isPrimitiveOrWrapperType)
							collection.add(o);

						else
							collection.add(o.toString());

					}

					if (collection.size() > 0) {

						Object r = Array.newInstance(c, collection.size());

						Object ret = collection.toArray((Object[]) r);

						m.put(attributename, ret);

					}
				}

				i.add(node, m);

			}

			tx.success();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
