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
package org.hawk.graph.updater;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;

import org.hawk.core.IModelIndexer;
import org.hawk.core.VcsCommitItem;
import org.hawk.core.graph.IGraphChange;
import org.hawk.core.graph.IGraphChangeDescriptor;
import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphNode;
import org.hawk.core.graph.IGraphNodeIndex;
import org.hawk.core.graph.IGraphTransaction;
import org.hawk.core.graph.IGraphIterable;
import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClass;
import org.hawk.core.model.IHawkModelResource;
import org.hawk.core.model.IHawkObject;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.query.IAccess;
import org.hawk.core.query.IAccessListener;
import org.hawk.core.query.IQueryEngine;
import org.hawk.graph.util.GraphUtil;

/**
 * creates a database with the input xmi file (in args[0]) or reads it if the
 * database exists; and provides debugging information
 * 
 */
public class GraphModelInserter {

	// private ResourceSet modelResourceSet; // model resource set
	// private File f; // xmi file to import model from
	// private EmbeddedGraphDatabase graph; // database
	// private Hashtable<EObject, Long> hash; // temporary hash for new database
	// // references insertion
	// private Resource metamodelResource; // metamodel resource

	private static final int maxTransactionalAcceptableLoad = Integer.MAX_VALUE;

	@SuppressWarnings("unused")
	private int unset = 0; // number of unset references (used for logging)

	private IHawkModelResource resource;
	private HashMap<String, IHawkObject> delta = new HashMap<>();
	private HashMap<String, IHawkObject> added = new HashMap<>();
	private HashMap<String, IHawkObject> unchanged = new HashMap<>();

	private IModelIndexer indexer;
	GraphModelBatchInjector inj;

	VcsCommitItem s;

	HashMap<String, IGraphNode> nodes = new HashMap<>();

	public GraphModelInserter(IModelIndexer hawk) {
		indexer = hawk;
	}

	public LinkedList<IGraphChange> run(IHawkModelResource res, VcsCommitItem s) {

		resource = res;
		this.s = s;

		LinkedList<IGraphChange> ret = null;

		// indexer = i;
		inj = new GraphModelBatchInjector(indexer.getGraph());

		try {

			// f = new File(dir + "/" + s.getPath());

			int delta = calculateModelDeltaSize();
			if (delta != -1) {

				System.err
						.println("file already present, calculating deltas with respect to graph storage");
				//
				if (delta > maxTransactionalAcceptableLoad) {
					System.err.print("[" + delta + ">"
							+ maxTransactionalAcceptableLoad + "] ");
					ret = batchUpdate();
				} else {
					System.err.print("[" + delta + "<"
							+ maxTransactionalAcceptableLoad + "] ");
					ret = transactionalUpdate();
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
				ret = addnodes();
			}

			System.out
					.print("\nProgram ending with no errors, shutting down database...");

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			long l = System.nanoTime();
			// t.success();
			// t.finish();
			// graph.shutdown();
			System.out.println("(took ~" + (System.nanoTime() - l) / 1000000000
					+ "sec to commit changes)");
		}

		// i.setGraph(BatchUtil.createGraphService(loc));
		return ret;
	}

	private LinkedList<IGraphChange> transactionalUpdate() throws Exception {
		System.err.println("transactional update called");

		System.err.println("unchanged: " + unchanged.size());
		System.err.println("delta: " + delta.size());
		System.err.println("added: " + added.size());

		LinkedList<IGraphChange> ret = new LinkedList<>();

		IGraphDatabase graph = indexer.getGraph();

		graph.exitBatchMode();

		try (IGraphTransaction t = graph.beginTransaction()) {

			IGraphNode fileNode = indexer.getGraph().getFileIndex()
					.get("id", s.getPath()).iterator().next();

			// add new nodes
			HashMap<IGraphNode, IHawkObject> addedNodes = new HashMap<>();
			HashMap<String, IGraphNode> addedNodesHash = new HashMap<>();

			for (String o : added.keySet()) {

				// add new node
				// System.err.println("adding model element: " + added.get(o));

				IHawkObject object = added.get(o);

				IGraphNode node = inj.addEObject(fileNode, object);

				addedNodes.put(node, object);
				addedNodesHash.put(node.getProperty("id").toString(), node);

				// track change new node
				ret.add(new GraphChangeImpl(true, IGraphChange.INSTANCE, node
						.getId() + "", null, false));
				for (String key : node.getPropertyKeys()) {
					ret.add(new GraphChangeImpl(true, IGraphChange.PROPERTY,
							node.getId() + "::" + key, node.getProperty(key),
							false));

				}
				for (IGraphEdge e : node.getOutgoingWithType("typeOf")) {
					ret.add(new GraphChangeImpl(true, IGraphChange.REFERENCE,
							node.getId() + "::" + "typeOf", e.getEndNode()
									.getId() + "", false));
				}
				for (IGraphEdge e : node.getOutgoingWithType("kindOf")) {
					ret.add(new GraphChangeImpl(true, IGraphChange.REFERENCE,
							node.getId() + "::" + "kindOf", e.getEndNode()
									.getId() + "", false));
				}
				for (IGraphEdge e : node.getOutgoingWithType("file")) {
					ret.add(new GraphChangeImpl(true, IGraphChange.REFERENCE,
							node.getId() + "::" + "file", e.getEndNode()
									.getId() + "", false));
				}

			}

			// references of added object and tracking of changes
			for (IGraphNode node : addedNodes.keySet()) {
				ret.addAll(inj.addEReference(node, addedNodes.get(node),
						addedNodesHash, nodes));
			}

			// delete obsolete nodes and change attributes
			for (String s : nodes.keySet()) {

				IGraphNode node = nodes.get(s);

				if (unchanged.containsKey(node.getProperty("id"))) {
					// do nothing
				} else if (delta.containsKey(node.getProperty("id"))) {
					IHawkObject o = delta.get(node.getProperty("id"));
					//
					// System.err.println("changing node "
					// + node.getId()
					// + " : "
					// + node.getProperty("id")
					// + " : "
					// + node.getOutgoingWithType("typeOf").iterator()
					// .next().getEndNode().getProperty("id")
					// + " :: as new model has altered it!");

					// for (String ss : node.getPropertyKeys()) {
					// reset properties - deprecated
					//
					// System.err.println("previous attribute:\t" + ss);
					// System.err.println("previous value:\t\t" +
					// node.getProperty(ss));
					//
					// node.removeProperty(ss);
					// }

					// node.setProperty("id", o.getUriFragment());
					node.setProperty("hashCode", o.hashCode());
					updateNodeProperties(node, o, ret);
					//
					// for (String ss : node.getPropertyKeys()) {
					// System.err.println("new attribute:\t" + ss);
					// System.err.println("new value:\t" +
					// node.getProperty(ss)); }
					//

				} else {
					// not in unchanged or updated so its deleted
					//
					// System.err.println("deleting node " + node +
					// " as new model does not contain it!");
					//

					// track change deleted node
					ret.add(new GraphChangeImpl(false, IGraphChange.INSTANCE,
							node.getId() + "", null, false));
					for (String key : node.getPropertyKeys()) {
						ret.add(new GraphChangeImpl(false,
								IGraphChange.PROPERTY, node.getId() + "::"
										+ key, node.getProperty(key), false));
					}
					for (IGraphEdge e : node.getOutgoing()) {
						if (e.getProperty("isDerived") == null) {
							ret.add(new GraphChangeImpl(false,
									IGraphChange.REFERENCE, node.getId() + "::"
											+ e.getType(), e.getEndNode()
											.getId() + "", false));
						}
					}
					//

					remove(node, fileNode);
					// new DeletionUtils(graph).delete(node);

				}

			}

			// change references
			for (String o : delta.keySet()) {

				IHawkObject ob = delta.get(o);

				IGraphNode node = nodes.get(ob.getUriFragment());

				// its null if it was just inserted (above), this is fine.
				if (node != null) {

					for (IHawkReference r : ((IHawkClass) ob.getType())
							.getAllReferences()) {

						if (ob.isSet(r)) {

							Object targets = ob.get(r, false);
							String refname = r.getName();
							boolean isContainment = r.isContainment();
							HashSet<String> targetids = new HashSet<>();

							//

							if (targets instanceof Iterable<?>) {

								for (IHawkObject h : ((Iterable<IHawkObject>) targets)) {
									if (!h.isProxy())
										targetids.add(h.getUriFragment());
									else {
										addProxyRef(node, h, refname);
									}
								}
							} else {
								if (!((IHawkObject) targets).isProxy())
									targetids.add(((IHawkObject) targets)
											.getUriFragment());
								else {
									addProxyRef(node, (IHawkObject) targets,
											refname);
								}
							}

							//
							Iterable<IGraphEdge> graphtargets = node
									.getOutgoingWithType(refname);

							for (IGraphEdge e : graphtargets) {

								IGraphNode n = e.getEndNode();

								if (targetids.contains(n.getProperty("id"))) {
									// update changed list
									targetids.remove(n.getProperty("id"));
								} else {
									// System.err.println("reference deleted in model, deleting from graph");
									// delete removed reference

									// track change deleted reference
									ret.add(new GraphChangeImpl(false,
											IGraphChange.REFERENCE, node
													.getId()
													+ "::"
													+ e.getType(), n.getId()
													+ "", false));
									e.delete();
								}
							}

							for (String s : targetids) {

								IGraphNode dest = nodes.get(s);
								if (dest != null) {
									// System.err.println("new reference added to :"+
									// s);
									IGraphEdge e = graph.createRelationship(
											node, dest, refname);
									if (isContainment)
										e.setProperty("isContainment", "true");
									// track change new reference
									ret.add(new GraphChangeImpl(true,
											IGraphChange.REFERENCE, node
													.getId()
													+ "::"
													+ e.getType(), dest.getId()
													+ "", false));

								} else {
									// proxy reference, handled above
								}
							}

						} else {
							// delete unset references which may have been
							// previously set
							String refname = r.getName();
							Iterable<IGraphEdge> graphtargets = node
									.getOutgoingWithType(refname);
							// track change deleted references
							for (IGraphEdge e : graphtargets) {
								ret.add(new GraphChangeImpl(false,
										IGraphChange.REFERENCE, node.getId()
												+ "::" + e.getType(), e
												.getEndNode().getId() + "",
										false));
								e.delete();
							}

						}

					}
				} else {
					// System.out.println("WARNING: null node: " + o + " ::: " +
					// ob.getUriFragment());
				}
			}

			fileNode.setProperty("revision", s.getCommit().getRevision());

			t.success();
		} catch (Exception e) {
			System.err.println("exception in transactionalUpdate()");
			e.printStackTrace();
			ret = null;
		}

		return ret;
	}

	private boolean addProxyRef(IGraphNode from, IHawkObject destinationObject,
			String edgelabel) {

		try {

			IGraphNodeIndex proxydictionary = indexer.getGraph()
					.getOrCreateNodeIndex("proxydictionary");
			// proxydictionary.add(graph.getNodeById(hash.get((from))),
			// edgelabel,
			// ((EObject)destinationObject).eIsProxy());

			String uri = destinationObject.getUri();

			// System.err.println(uri.toString().substring(uri.toString().indexOf(".metadata/.plugins/com.google.code.hawk.neo4j/temp/m/")+53));
			// System.err.println(uri.);

			String relativeURI = new DeletionUtils(indexer.getGraph())
					.getRelativeURI(uri.toString());
			String relativeFileURI = relativeURI;
			try {
				relativeFileURI = relativeURI.substring(0,
						relativeURI.indexOf("#/"));
			} catch (Exception e) {
				//
			}

			IGraphNode withProxy = from;
			Object proxies = null;
			// if (withProxy.hasProperty("_proxyRef:" + relativeFileURI)) {
			// proxies = withProxy.getProperty("_proxyRef:" + relativeFileURI);
			// }
			// System.err.println(">>>>>>>"+relativeFileURI);

			proxies = withProxy.getProperty("_proxyRef:" + relativeFileURI);
			proxies = new DeletionUtils(indexer.getGraph()).add(
					(String[]) proxies, relativeURI, edgelabel);

			withProxy.setProperty("_proxyRef:" + relativeFileURI, proxies);

			HashMap<String, Object> m = new HashMap<>();
			m.put("_proxyRef", relativeFileURI);

			proxydictionary.add(withProxy, m);

		} catch (Exception e) {
			System.err.println("proxydictionary error:");
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private void updateNodeProperties(IGraphNode node, IHawkObject eObject,
			LinkedList<IGraphChange> changes) {

		LinkedList<IHawkAttribute> normalattributes = new LinkedList<IHawkAttribute>();
		LinkedList<IHawkAttribute> indexedattributes = new LinkedList<IHawkAttribute>();
		// LinkedList<IHawkAttribute> derivedattributes = new
		// LinkedList<IHawkAttribute>();

		IGraphNode typenode = node.getOutgoingWithType("typeOf").iterator()
				.next().getEndNode();

		for (final IHawkAttribute eAttribute : ((IHawkClass) eObject.getType())
				.getAllAttributes()) {

			if (eObject.isSet(eAttribute)) {

				if (((String[]) typenode.getProperty(eAttribute.getName()))[5]
						.equals("t"))
					indexedattributes.add(eAttribute);

				normalattributes.add(eAttribute);

			} else
			// depracatedTODO currently unset items are not included to may
			// crash eol etc
			{
				// node.setProperty(eAttribute.getName(), "UNSET");
			}
		}

		for (IHawkAttribute a : normalattributes) {

			Object oldproperty = node.getProperty(a.getName());

			String type = GraphUtil.toJavaType(a.getType().getName());

			if (!a.isMany()) {

				if (type.equals("String") || type.equals("Boolean")
						|| type.equals("Integer") || type.equals("Real")) {
					if (!eObject.get(a).equals(oldproperty)) {
						// track changed property (primitive)
						changes.add(new GraphChangeImpl(false,
								IGraphChange.PROPERTY, node.getId() + "::"
										+ a.getName(), oldproperty, false));
						changes.add(new GraphChangeImpl(true,
								IGraphChange.PROPERTY, node.getId() + "::"
										+ a.getName(), eObject.get(a), false));
					}
					node.setProperty(a.getName(), eObject.get(a));
				} else {
					if (!eObject.get(a).toString().equals(oldproperty)) {
						// track changed property (non-primitive)
						changes.add(new GraphChangeImpl(false,
								IGraphChange.PROPERTY, node.getId() + "::"
										+ a.getName(), oldproperty, false));
						changes.add(new GraphChangeImpl(true,
								IGraphChange.PROPERTY, node.getId() + "::"
										+ a.getName(), eObject.get(a)
										.toString(), false));
					}
					node.setProperty(a.getName(), eObject.get(a).toString());
				}

			}

			else {

				Collection<Object> collection = null;

				if (a.isOrdered() && a.isUnique())
					collection = new LinkedHashSet<Object>();
				else if (a.isOrdered())
					collection = new LinkedList<Object>();
				else if (a.isUnique())
					collection = new HashSet<Object>();
				else
					collection = new LinkedList<Object>();

				for (Object o : (Collection<?>) eObject.get(a)) {

					if (type.equals("String") || type.equals("Boolean")
							|| type.equals("Integer") || type.equals("Real"))
						collection.add(o);

					else
						collection.add(o.toString());

				}

				Object r = null;

				if (type.equals("Integer")) {
					r = Array.newInstance(Integer.class, 1);
				} else if (type.equals("Real")) {
					r = Array.newInstance(Double.class, 1);
				} else if (type.equals("Boolean")) {
					r = Array.newInstance(Boolean.class, 1);
				} else {
					r = Array.newInstance(String.class, 1);
				}

				Object ret = collection.toArray((Object[]) r);

				if (!ret.equals(oldproperty)) {
					// track changed property (collection)
					changes.add(new GraphChangeImpl(false,
							IGraphChange.PROPERTY, node.getId() + "::"
									+ a.getName(), oldproperty, false));
					changes.add(new GraphChangeImpl(true,
							IGraphChange.PROPERTY, node.getId() + "::"
									+ a.getName(), ret, false));
				}

				node.setProperty(a.getName(), ret);

			}

		}

		// deferred for later
		// for (IHawkAttribute a : derivedattributes) {
		//
		// HashSet<IHawkAnnotation> ean = a.getAnnotations();
		//
		// for (IHawkAnnotation e : ean) {
		//
		// HashMap<String, String> map = e.getDetails();
		//
		// if (map != null && map.containsKey("EOLCode")) {
		//
		// node.setProperty(a.getName(), "_NYD##" + map.get("EOLCode"));
		// inj.addToProxyAttributes(node);
		//
		// } else if (map != null && map.containsKey("String")) {
		//
		// // BatchInserterIndex i = index.nodeIndex(eObject
		// // .eClass().getEPackageNSURI()
		// // + "##"
		// // + eObject.eClass().getName()
		// // + "##"
		// // + a.getName(), MapUtil
		// // .stringMap(IndexManager.PROVIDER,
		// // "lucene", "type", "exact"));
		// String prop = map.get("String").toString();
		// // i.add(node, "value", prop);
		// node.setProperty(a.getName(), prop);
		//
		// } else {
		//
		// System.err
		// .println("derived feature has no runnable eol code or static string, hence is ignored");
		//
		// }
		//
		// }
		// }

		for (IHawkAttribute a : indexedattributes) {

			// FIXME update property index on changes not just insert them
			IGraphNodeIndex i = indexer.getGraph().getOrCreateNodeIndex(
					eObject.getType().getPackageNSURI() + "##"
							+ eObject.getType().getName() + "##" + a.getName());

			String type = GraphUtil.toJavaType(a.getType().getName());

			if (!a.isMany()) {

				if (type.equals("String") || type.equals("Boolean")
						|| type.equals("Integer") || type.equals("Real"))
					i.add(node, a.getName(), eObject.get(a));

				else
					i.add(node, a.getName(), eObject.get(a).toString());

			}

			else {

				Collection<Object> collection = null;

				if (a.isOrdered() && a.isUnique())
					collection = new LinkedHashSet<Object>();
				else if (a.isOrdered())
					collection = new LinkedList<Object>();
				else if (a.isUnique())
					collection = new HashSet<Object>();
				else
					collection = new LinkedList<Object>();

				for (Object o : (Collection<?>) eObject.get(a)) {

					if (type.equals("String") || type.equals("Boolean")
							|| type.equals("Integer") || type.equals("Real"))
						collection.add(o);

					else
						collection.add(o.toString());

				}

				Object r = null;

				if (type.equals("Integer")) {
					r = Array.newInstance(Integer.class, 1);
				} else if (type.equals("Real")) {
					r = Array.newInstance(Double.class, 1);
				} else if (type.equals("Boolean")) {
					r = Array.newInstance(Boolean.class, 1);
				} else {
					r = Array.newInstance(String.class, 1);
				}

				Object ret = collection.toArray((Object[]) r);

				i.add(node, a.getName(), ret);

			}

		}

	}

	private LinkedList<IGraphChange> batchUpdate() throws Exception {
		System.err.println("batch update called");

		LinkedList<IGraphChange> ret = new LinkedList<>();

		IGraphDatabase graph = indexer.getGraph();

		graph.exitBatchMode();

		new DeletionUtils(graph).deleteAll(s.getPath());
		ret.addAll(inj.getChanges());
		inj.clearChanges();

		graph.enterBatchMode();

		GraphModelBatchInjector batch = new GraphModelBatchInjector(graph, s,
				resource);
		ret.addAll(batch.getChanges());
		batch.clearChanges();

		graph.exitBatchMode();

		return ret;

	}

	private int calculateModelDeltaSize() throws Exception {

		System.err.println("calculateModelDeltaSize() called:");

		IGraphDatabase graph = indexer.getGraph();

		try (IGraphTransaction t = graph.beginTransaction()) {

			boolean modelfilealreadypresent = graph.getFileIndex()
					.get("id", s.getPath()).iterator().hasNext();

			if (modelfilealreadypresent) {
				int delta = 0;
				HashMap<String, Integer> hashCodes = new HashMap<>();

				for (IGraphEdge e : graph.getFileIndex().get("id", s.getPath())
						.getSingle().getIncomingWithType("file")) {
					IGraphNode n = e.getStartNode();
					// nodeIds.put((String) n.getProperty("id"), n);
					nodes.put(n.getProperty("id").toString(), n);
					hashCodes.put((String) n.getProperty("id"),
							(int) n.getProperty("hashCode"));
					// if (prev != null) {
					// System.out.println(">" + prev);
					// System.out.println(">>" + n);
					// System.out.println();
					// }
				}
				System.err.println("file contains: " + nodes.size() + " ("
						+ hashCodes.size() + ") nodes in store");

				int newo = 0;

				Iterator<IHawkObject> iterator = resource.getAllContents();
				while (iterator.hasNext()) {
					IHawkObject o = iterator.next();
					Integer hash = hashCodes.get(o.getUriFragment());
					if (hash != null) {
						if (hash != o.hashCode()) {
							delta++;
							this.delta.put(o.getUriFragment(), o);
						} else {
							this.unchanged.put(o.getUriFragment(), o);

						}
					} else {
						newo++;
						delta++;
						this.added.put(o.getUriFragment(), o);
					}
				}
				t.success();
				System.err.println("delta is of size: " + (delta - newo) + "+"
						+ newo + "+" + +(nodes.size() - unchanged.size()));
				return delta + nodes.size() - unchanged.size();
			} else {
				t.success();
				System.err
						.println("file not in store, performing initial batch file insertion");
				return -1;
			}

		}
	}

	/**
	 * Populates the database with the model, using util.parseresource
	 * 
	 * @return
	 * 
	 * @throws Exception
	 */
	private LinkedList<IGraphChange> addnodes() throws Exception {

		LinkedList<IGraphChange> ret = new LinkedList<>();

		if (resource != null) {
			GraphModelBatchInjector batch = new GraphModelBatchInjector(
					indexer.getGraph(), s, resource);
			unset = batch.getUnset();
			ret.addAll(batch.getChanges());
			batch.clearChanges();
			// ret = true;
		} else {
			System.err
					.println("model insertion aborted, see above error (maybe you need to register the metamodel?)");
			ret = null;
		}

		try {
			// modelResource.unload();
			System.gc();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * adds the metamodel to the database using util.pareresource, for debugging
	 * or future use
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	private void addmeta() {

		// new util.Neo4JParseResource().parseResource(3, rs2, g, f, hash);
		// new util.Neo4JParseResource().parseResource(4, rs2, g, f, hash);

	}

	private void remove(IGraphNode modelElement, IGraphNode fileNode) {

		DeletionUtils del = new DeletionUtils(indexer.getGraph());

		del.dereference(modelElement);

		del.makeProxyRefs(modelElement, fileNode);

		del.delete(modelElement);
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
			config.put("neostore.relationshipstore.db.mapped_memory", 14 * x
					+ "M");
			config.put("neostore.propertystore.db.mapped_memory", x + "M");
			config.put("neostore.propertystore.db.strings.mapped_memory", 2 * x
					+ "M");
			config.put("neostore.propertystore.db.arrays.mapped_memory", x
					+ "M");

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

	public static int resolveProxies(IGraphDatabase graph,
			IGraphChangeDescriptor ret) throws Exception {

		long start = System.currentTimeMillis();

		int proxiesLeft = -1;

		graph.exitBatchMode();

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...

			IGraphNodeIndex proxydictionary = graph
					.getOrCreateNodeIndex("proxydictionary");

			IGraphIterable<IGraphNode> resolvedProxies = proxydictionary.query(
					"_proxyRef", "*");

			if (resolvedProxies != null && resolvedProxies.size() > 0) {

				for (IGraphNode n : resolvedProxies) {

					HashSet<String[]> allProxies = new HashSet<String[]>();

					for (String s : n.getPropertyKeys()) {

						if (s.contains("_proxyRef:"))

							allProxies.add((String[]) n.getProperty(s));

					}

					// System.err.println("----");
					// for (String[] proxies : allProxies) {
					// Arrays.toString(proxies);
					// }
					// System.err.println("--------");

					for (String[] proxies : allProxies) {

						// System.out
						// .println(new
						// com.google.code.hawk.neo4j.emc.toString()
						// .tostring(proxies));

						int sub = proxies[0].indexOf("#/");
						String fileName = proxies[0].substring(0,
								sub == -1 ? proxies[0].indexOf("#") : sub);

						for (int i = 0; i < proxies.length; i = i + 2) {

							// boolean found = false;

							// System.err.println(Arrays.toString(proxies));

							Iterable<IGraphEdge> rels = allNodesWithFile(graph,
									fileName);

							if (rels != null) {

								HashSet<IGraphNode> nodes = new HashSet<IGraphNode>();
								for (IGraphEdge r : rels)
									nodes.add(r.getStartNode());

								for (IGraphNode no : nodes) {

									String nodeURI = fileName + "#"
											+ no.getProperty("id").toString();

									// System.out.println(nodeURI);

									if (nodeURI.equals(proxies[i])) {

										boolean change = new GraphModelBatchInjector(
												graph).resolveProxyRef(n, no,
												proxies[i + 1]);

										if (!change)
											System.err
													.println("resolving proxy ref returned false, edge already existed: "
															+ n.getId()
															+ " - "
															+ proxies[i + 1]
															+ " -> "
															+ no.getId());

										// track change resolved proxy ref
										ret.addChanges(new GraphChangeImpl(
												true, IGraphChange.REFERENCE, n
														.getId()
														+ "::"
														+ proxies[i + 1], no
														.getId() + "", false));

										// found = true;

										break;

									}

								}

								// throw new Exception("not found: " +
								// proxies[i]);
							}
						}

						n.removeProperty("_proxyRef:" + fileName);
						proxydictionary.remove(n);

					}
				}

			}

			proxiesLeft = proxydictionary.query("_proxyRef", "*").size();

			System.out.println(proxiesLeft
					+ " - sets of proxy references left in the store");

			tx.success();
			tx.close();
		}

		System.out.println("proxy resolution took: ~"
				+ (System.currentTimeMillis() - start) / 1000 + "s");

		return proxiesLeft;

	}

	public int resolveDerivedAttributeProxies(IGraphDatabase graph,
			IModelIndexer m, String type) throws Exception {

		int derivedLeft = -1;
		Iterable<IGraphNode> allUnresolved = null;
		IGraphNodeIndex derivedProxyDictionary = null;
		int size = 0;

		System.err.println("deriving attributes...");

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...

			// doneFIXME (a) discuss strategy for class level annotations

			derivedProxyDictionary = graph
					.getOrCreateNodeIndex("derivedproxydictionary");

			allUnresolved = derivedProxyDictionary.query("derived", "*");

			size = ((IGraphIterable<IGraphNode>) allUnresolved).size();

			tx.success();
		}

		if (size > 0) {

			// GraphEpsilonModel model = new GraphEpsilonModel();
			// model.hawkContainer = this;
			// System.err.println(indexer.getKnownQueryLanguages());
			// System.err.println(type);

			IQueryEngine q = indexer.getKnownQueryLanguages().get(type);
			//
			//
			IAccessListener accessListener = q.calculateDerivedAttributes(
					graph, allUnresolved);

			// dump access to lucene and add hooks on updates

			try (IGraphTransaction tx = graph.beginTransaction()) {
				// operations on the graph
				// ...

				IGraphNodeIndex derivedAccessDictionary = graph
						.getOrCreateNodeIndex("derivedaccessdictionary");

				// reset accesses of nodes updated
				// ...

				for (IAccess a : accessListener.getAccesses()) {

					IGraphNode sourceNode = graph.getNodeById(a
							.getSourceObjectID());

					if (sourceNode != null)
						derivedAccessDictionary.remove(sourceNode);

				}

				for (IAccess a : accessListener.getAccesses()) {

					IGraphNode sourceNode = graph.getNodeById(a
							.getSourceObjectID());

					if (sourceNode != null)
						derivedAccessDictionary.add(sourceNode,
								a.getAccessObjectID(), a.getProperty());

					// System.out.println(sourceNode.getId()
					// + "("
					// + sourceNode.getIncoming().iterator().next()
					// .getStartNode()
					// .getOutgoingWithType("typeOf").iterator()
					// .next().getEndNode().getProperty("id")
					// + ") :: "
					// + a.getAccessObjectID()
					// + "("
					// + graph.getNodeById(a.getAccessObjectID())
					// .getOutgoingWithType("typeOf").iterator()
					// .next().getEndNode().getProperty("id")
					// + ") :: " + a.getProperty());
				}

				System.err.println("accesses: "
						+ accessListener.getAccesses().size() + " ("
						+ derivedAccessDictionary.query("*", "*").size()
						+ " nodes)");

				tx.success();
			}

			accessListener.resetAccesses();

		}

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...

			derivedLeft = ((IGraphIterable<IGraphNode>) derivedProxyDictionary
					.query("derived", "*")).size();
			tx.success();
		}

		System.out
				.println(derivedLeft
						+ " - sets of proxy [derived] attributes left incomplete in the store");

		return derivedLeft;
	}

	public void updateDerivedAttributes(IGraphChangeDescriptor ret, String type)
			throws Exception {

		HashSet<IGraphNode> nodesToBeUpdated = new HashSet<>();

		IGraphDatabase graph = indexer.getGraph();

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...

			IGraphNodeIndex derivedAccessDictionary = graph
					.getOrCreateNodeIndex("derivedaccessdictionary");

			for (IGraphChange c : ret.getChanges()) {

				if (!c.isTransient()) {

					// c.getElementType();
					// c.getIdentifier();

					if (c.getElementType().equals(IGraphChange.FILE)
							|| c.getElementType().equals(IGraphChange.INSTANCE)) {

						for (IGraphNode node : derivedAccessDictionary.query(
								c.getIdentifier(), "*")) {

							String derivedPropertyName = node.getIncoming()
									.iterator().next().getType();

							if (node.getPropertyKeys().contains(
									derivedPropertyName)) {

								node.setProperty(derivedPropertyName, "_NYD##"
										+ node.getProperty("derivationlogic"));

								nodesToBeUpdated.add(node);

							} else
								throw new Exception(
										"Exception in updateDerivedAttributes() -- derived attribute node did not contain property: "
												+ derivedPropertyName);
						}

					} else if (c.getElementType().equals(IGraphChange.PROPERTY)
							|| c.getElementType()
									.equals(IGraphChange.REFERENCE)) {

						String[] split = c.getIdentifier().split("::");
						String id = split[0];
						String prop = split[1];

						for (IGraphNode node : derivedAccessDictionary.query(
								id, prop)) {

							String derivedPropertyName = node.getIncoming()
									.iterator().next().getType();

							if (node.getPropertyKeys().contains(
									derivedPropertyName)) {

								node.setProperty(derivedPropertyName, "_NYD##"
										+ node.getProperty("derivationlogic"));

								nodesToBeUpdated.add(node);

							} else
								throw new Exception(
										"Exception in updateDerivedAttributes() -- derived attribute node did not contain property: "
												+ derivedPropertyName);
						}

					} else if (c.getElementType()
							.equals(IGraphChange.METAMODEL)
							|| c.getElementType().equals(IGraphChange.TYPE)) {

						// metamodel change do nothing

					} else
						throw new Exception(
								"Exception in updateDerivedAttributes() -- change of type: "
										+ c.getElementType());

				}
			}

			IQueryEngine q = indexer.getKnownQueryLanguages().get(type);
			//
			//
			IAccessListener accessListener = q.calculateDerivedAttributes(
					graph, nodesToBeUpdated);

			for (IAccess a : accessListener.getAccesses()) {

				IGraphNode sourceNode = graph
						.getNodeById(a.getSourceObjectID());

				if (sourceNode != null)
					derivedAccessDictionary.remove(sourceNode);

			}

			for (IAccess a : accessListener.getAccesses()) {

				IGraphNode sourceNode = graph
						.getNodeById(a.getSourceObjectID());

				if (sourceNode != null)
					derivedAccessDictionary.add(sourceNode,
							a.getAccessObjectID(), a.getProperty());
			}

			tx.success();

		} catch (Exception e) {
			throw e;
		}
	}

	private static Iterable<IGraphEdge> allNodesWithFile(IGraphDatabase graph,
			String file) {

		IGraphNodeIndex filedictionary = graph.getFileIndex();

		String[] path = file.replaceAll("\\\\", "/").split("/");
		String relative = path[path.length - 1];

		// System.out.println(">>>>>"+file);
		// for(Node n:filedictionary.query("id",
		// "*"))System.out.println(n.getProperty("id"));

		// System.out.println(">>>>>>>>>>>>>>");
		// System.out.println(file);
		// System.out.println("<<<<<<<<<<<<<<<<");

		IGraphNode fileNode = filedictionary.get("id", relative).getSingle();

		if (fileNode != null)
			return fileNode.getIncomingWithType("file");
		else
			return null;

	}

	public void updateDerivedAttribute(String metamodeluri, String typename,
			String attributename, String attributetype, boolean isMany,
			boolean isOrdered, boolean isUnique, String derivationlanguage,
			String derivationlogic) {

		IGraphDatabase graph = indexer.getGraph();

		HashSet<IGraphNode> derivedPropertyNodes = new HashSet<>();

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...
			IGraphNode metamodelNode = graph.getMetamodelIndex()
					.get("id", metamodeluri).getSingle();

			IGraphNode typeNode = null;

			for (IGraphEdge e : metamodelNode.getIncomingWithType("epackage")) {
				IGraphNode othernode = e.getStartNode();
				if (othernode.getProperty("id").equals(typename)) {
					typeNode = othernode;
					break;
				}
			}

			HashSet<IGraphNode> nodes = new HashSet<>();
			for (IGraphEdge e : typeNode.getIncomingWithType("typeOf")) {
				nodes.add(e.getStartNode());
			}
			for (IGraphEdge e : typeNode.getIncomingWithType("KindOf")) {
				nodes.add(e.getStartNode());
			}

			for (IGraphNode instanceNode : nodes) {

				Iterator<IGraphEdge> derived = instanceNode
						.getOutgoingWithType(attributename).iterator();

				HashMap<String, Object> m = new HashMap<>();
				m.put("isMany", isMany);
				m.put("isOrdered", isOrdered);
				m.put("isUnique", isUnique);
				m.put("attributetype", attributetype);
				m.put("derivationlanguage", derivationlanguage);
				m.put("derivationlogic", derivationlogic);
				m.put(attributename, "_NYD##" + derivationlogic);

				// derived node exists -- update derived property
				if (derived.hasNext()) {

					IGraphNode derivedPropertyNode = derived.next()
							.getEndNode();

					for (String s : m.keySet())
						derivedPropertyNode.setProperty(s, m.get(s));

					derivedPropertyNodes.add(derivedPropertyNode);

				} else// derived node does not exist -- create derived property
				{

					IGraphNode derivedPropertyNode = graph.createNode(m,
							"derivedattribute");

					m.clear();
					m.put("isDerived", true);

					graph.createRelationship(instanceNode, derivedPropertyNode,
							attributename, m);

					derivedPropertyNodes.add(derivedPropertyNode);

				}

			}

			// derive the new property
			IQueryEngine q = indexer.getKnownQueryLanguages().get(
					derivationlanguage);
			//
			//
			IAccessListener accessListener = q.calculateDerivedAttributes(
					graph, derivedPropertyNodes);

			IGraphNodeIndex derivedAccessDictionary = graph
					.getOrCreateNodeIndex("derivedaccessdictionary");

			for (IAccess a : accessListener.getAccesses()) {

				IGraphNode sourceNode = graph
						.getNodeById(a.getSourceObjectID());

				if (sourceNode != null)
					derivedAccessDictionary.remove(sourceNode);

			}

			for (IAccess a : accessListener.getAccesses()) {

				IGraphNode sourceNode = graph
						.getNodeById(a.getSourceObjectID());

				if (sourceNode != null)
					derivedAccessDictionary.add(sourceNode,
							a.getAccessObjectID(), a.getProperty());
			}

			tx.success();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void updateIndexedAttribute(String metamodeluri, String typename,
			String attributename) {

		IGraphDatabase graph = indexer.getGraph();

		try (IGraphTransaction tx = graph.beginTransaction()) {
			// operations on the graph
			// ...

			IGraphNodeIndex i = graph.getOrCreateNodeIndex(metamodeluri + "##"
					+ typename + "##" + attributename);

			IGraphNode typeNode = null;

			for (IGraphEdge r : graph.getMetamodelIndex()
					.get("id", metamodeluri).getSingle()
					.getIncomingWithType("epackage")) {

				IGraphNode othernode = r.getStartNode();
				if (othernode.getProperty("id").equals(typename)) {
					typeNode = othernode;
					break;
				}

			}

			// a isMany isOrdered isUnique attrType isIndexed
			String[] metadata = (String[]) typeNode.getProperty(attributename);

			String type = GraphUtil.toJavaType(metadata[4]);

			HashSet<IGraphNode> nodes = new HashSet<>();
			for (IGraphEdge e : typeNode.getIncomingWithType("typeOf")) {
				nodes.add(e.getStartNode());
			}
			for (IGraphEdge e : typeNode.getIncomingWithType("KindOf")) {
				nodes.add(e.getStartNode());
			}

			for (IGraphNode node : nodes) {

				Map<String, Object> m = new HashMap<>();

				if (!(metadata[1] == "t")) {

					if (type.equals("String") || type.equals("Boolean")
							|| type.equals("Integer") || type.equals("Real"))
						m.put(attributename, node.getProperty(attributename));

					else
						m.put(attributename, node.getProperty(attributename)
								.toString());

				}

				else {

					Collection<Object> collection = null;

					if (metadata[2] == "t" && metadata[3] == "t")
						collection = new LinkedHashSet<Object>();
					else if (metadata[2] == "t")
						collection = new LinkedList<Object>();
					else if (metadata[3] == "t")
						collection = new HashSet<Object>();
					else
						collection = new LinkedList<Object>();

					for (Object o : (Collection<?>) node
							.getProperty(attributename)) {

						if (type.equals("String") || type.equals("Boolean")
								|| type.equals("Integer")
								|| type.equals("Real"))
							collection.add(o);

						else
							collection.add(o.toString());

					}

					Object r = null;

					if (type.equals("Integer")) {
						r = Array.newInstance(Integer.class, 1);
					} else if (type.equals("Real")) {
						r = Array.newInstance(Double.class, 1);
					} else if (type.equals("Boolean")) {
						r = Array.newInstance(Boolean.class, 1);
					} else {
						r = Array.newInstance(String.class, 1);
					}

					Object ret = collection.toArray((Object[]) r);

					m.put(attributename, ret);

				}

				i.add(node, m);

			}

			tx.success();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

// end
