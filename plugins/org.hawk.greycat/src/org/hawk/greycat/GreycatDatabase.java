/*******************************************************************************
 * Copyright (c) 2018 Aston University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.greycat;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.hawk.core.IConsole;
import org.hawk.core.IModelIndexer;
import org.hawk.core.graph.IGraphDatabase;
import org.hawk.core.graph.IGraphEdge;
import org.hawk.core.graph.IGraphIterable;
import org.hawk.core.graph.IGraphNode;
import org.hawk.core.graph.IGraphNodeIndex;
import org.hawk.core.graph.IGraphTransaction;
import org.hawk.greycat.GreycatNode.NodeReader;
import org.hawk.greycat.lucene.GreycatLuceneIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import greycat.Callback;
import greycat.Graph;
import greycat.GraphBuilder;
import greycat.Node;
import greycat.NodeIndex;
import greycat.Type;
import greycat.rocksdb.RocksDBStorage;

public class GreycatDatabase implements IGraphDatabase {

	private static final class NodeKey {
		public final long world, time, id;

		public NodeKey(long world2, long time2, long id2) {
			this.world = world2;
			this.time = time2;
			this.id = id2;
		}

		public NodeKey(GreycatNode gn) {
			this(gn.getWorld(), gn.getTime(), gn.getId());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (id ^ (id >>> 32));
			result = prime * result + (int) (time ^ (time >>> 32));
			result = prime * result + (int) (world ^ (world >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			NodeKey other = (NodeKey) obj;
			if (id != other.id)
				return false;
			if (time != other.time)
				return false;
			if (world != other.world)
				return false;
			return true;
		}
	}

	protected static final class NodeCacheWrapper {
		public final Node node;

		/** This node is currently in use - do not allow the cache to free it upon LRU removal. */
		public boolean inUse = false;

		public NodeCacheWrapper(Node n) {
			this.node = n;
		}
	}

	/**
	 * Apparently deletion is the one thing we cannot roll back from through a
	 * forced reconnection to the DB. Instead, we will set a property marking this
	 * node as deleted, and all querying will ignore this node. After the next
	 * proper save, we will go through the DB and do some garbage collection.
	 */
	protected static final String SOFT_DELETED_KEY = "h_softDeleted";

	/**
	 * Greycat doesn't seem to have node labels by itself, but we can easily emulate
	 * this with a custom index.
	 */
	protected static final String NODE_LABEL_IDX = "h_nodeLabel";

	/**
	 * In batch mode, we save every time we reach an X number of dirty nodes.
	 */
	protected static final int SAVE_EVERY = 10_000;

	private static final Logger LOGGER = LoggerFactory.getLogger(GreycatDatabase.class);

	private Cache<NodeKey, NodeCacheWrapper> nodeCache;
	private File storageFolder, tempFolder;
	private IConsole console;
	private Graph graph;
	private NodeIndex nodeLabelIndex, softDeleteIndex;
	private Mode mode = Mode.TX_MODE;
	private GreycatLuceneIndexer luceneIndexer;

	/**
	 * Keeps nodes modified so far, so we can free them after we save.
	 */
	private Set<GreycatNode> currentDirtyNodes = new HashSet<>();

	/**
	 * Keeps nodes opened right now, so we can avoid doing a periodic
	 * save in the middle of some modifications.
	 */
	private Set<GreycatNode> currentOpenNodes = new HashSet<>();
	

	private int world = 0;
	private int time = 0;

	public int getWorld() {
		return world;
	}

	public void setWorld(int world) {
		this.world = world;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	@Override
	public String getPath() {
		return storageFolder.getAbsolutePath();
	}

	@Override
	public void run(File parentFolder, IConsole c) {
		this.storageFolder = parentFolder;
		this.tempFolder = new File(storageFolder, "temp");
		this.console = c;

		reconnect();
	}

	@Override
	public void shutdown() throws Exception {
		shutdownHelpers();

		if (graph != null) {
			graph.disconnect(result -> {
				console.println("Disconnected from GreyCat graph");
			});

			graph = null;
		}
	}

	@Override
	public void delete() throws Exception {
		shutdownHelpers();

		if (graph != null) {
			CompletableFuture<Boolean> done = new CompletableFuture<>();
			graph.disconnect(result -> {
				try {
					deleteRecursively(storageFolder);
				} catch (IOException e) {
					LOGGER.error("Error while deleting Greycat storage", e);
				}
				done.complete(true);
			});
			done.join();
			graph = null;
		}
	}

	/**
	 * Shuts down any auxiliary facilities (Guava node cache, Lucene index).
	 */
	protected void shutdownHelpers() {
		if (nodeCache != null) {
			nodeCache.invalidateAll();
			nodeCache = null;
		}

		if (luceneIndexer != null) {
			luceneIndexer.shutdown();
			luceneIndexer = null;
		}
	}

	private static void deleteRecursively(File f) throws IOException {
		if (!f.exists()) return;

		Files.walkFileTree(f.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}

		});
	}

	@Override
	public IGraphNodeIndex getOrCreateNodeIndex(String name) {
		return luceneIndexer.getIndex(name);
	}

	@Override
	public IGraphNodeIndex getMetamodelIndex() {
		return getOrCreateNodeIndex("_hawkMetamodelIndex");
	}

	@Override
	public IGraphNodeIndex getFileIndex() {
		return getOrCreateNodeIndex("_hawkFileIndex");
	}

	@Override
	public IGraphTransaction beginTransaction() throws Exception {
		if (mode == Mode.NO_TX_MODE) {
			exitBatchMode();
		}
		return new GreycatTransaction(this);
	}

	@Override
	public boolean isTransactional() {
		return true;
	}

	@Override
	public void enterBatchMode() {
		if (mode == Mode.TX_MODE) {
			commitLuceneIndex();
			save();
			mode = Mode.NO_TX_MODE;
		}
	}

	@Override
	public void exitBatchMode() {
		if (mode == Mode.NO_TX_MODE) {
			commitLuceneIndex();
			save();
			mode = Mode.TX_MODE;
		}
	}

	@Override
	public IGraphIterable<IGraphNode> allNodes(String label) {
		return new GreycatNodeIterable(this, () -> {
			CompletableFuture<Node[]> nodes = new CompletableFuture<>();
			nodeLabelIndex.find(result -> {
				nodes.complete(result);
			}, world, time, label);
			return nodes.get();
		});
	}

	@Override
	public GreycatNode createNode(Map<String, Object> props, String label) {
		final Node node = graph.newNode(world, time);
		node.set(NODE_LABEL_IDX, Type.STRING, label);
		nodeLabelIndex.update(node);

		final GreycatNode n = new GreycatNode(this, node);
		if (props != null) {
			n.setProperties(props);
		}

		return n;
	}

	/**
	 * Marks a certain node as being dirty: on batch mode, a periodic save will be
	 * triggered when the set of dirty nodes reaches {@link #SAVE_EVERY} and there
	 * are no opened nodes.
	 */
	protected void markDirty(GreycatNode n) {
		currentDirtyNodes.add(n);
	}

	/**
	 * Marks a certain node as being currently opened: a periodic save should not be
	 * triggered until all currently opened nodes are closed.
	 */
	protected void markOpen(GreycatNode n) {
		currentOpenNodes.add(n);
	}

	/**
	 * Removes a node from being currently opened, and triggers a periodic
	 * save request if that results in the open set to be empty.
	 */
	protected void markClosed(GreycatNode n) {
		if (currentOpenNodes.remove(n) && currentOpenNodes.isEmpty()) {
			if (mode == Mode.NO_TX_MODE && currentDirtyNodes.size() > SAVE_EVERY) {
				save();
			}
		}
	}

	protected void save() {
		final CompletableFuture<Boolean> result = new CompletableFuture<>();

		// First stage save
		graph.save(saved -> {
			softDeleteIndex.find(results -> {

				Semaphore sem = new Semaphore(-results.length + 1);
				for (Node n : results) {
					hardDelete(new GreycatNode(this, n), dropped -> sem.release());
				}

				// Wait until all nodes have been dropped
				try {
					sem.acquire();
				} catch (InterruptedException e) {
					LOGGER.error(e.getMessage(), e);
				}

				if (results.length > 0) {
					// Second stage (for fully dropping those nodes)
					graph.save(savedAgain -> result.complete(savedAgain));
				} else {
					result.complete(saved);
				}
			}, world, time);
		});
		result.join();

		// Free nodes after having saved them 
		for (GreycatNode dirtyNode : currentDirtyNodes) {
			dirtyNode.free();
			nodeCache.invalidate(new NodeKey(dirtyNode));
		}
		currentDirtyNodes.clear();

		// useful for finding GreyCat Node leaks
		//System.out.println("-- SAVED: available is " + graph.space().available());
	}

	@Override
	public IGraphEdge createRelationship(IGraphNode start, IGraphNode end, String type) {
		return createRelationship(start, end, type, Collections.emptyMap());
	}

	@Override
	public IGraphEdge createRelationship(IGraphNode start, IGraphNode end, String type, Map<String, Object> props) {
		final GreycatNode gStart = (GreycatNode)start;
		final GreycatNode gEnd = (GreycatNode)end;
		return gStart.addEdge(type, gEnd, props);
	}

	@Override
	public Graph getGraph() {
		return graph;
	}

	@Override
	public GreycatNode getNodeById(Object id) {
		if (id instanceof String) {
			id = Long.valueOf((String) id);
		}

		return new GreycatNode(this, world, time, (long)id);
	}

	@Override
	public boolean nodeIndexExists(String name) {
		return luceneIndexer.indexExists(name);
	}

	@Override
	public String getType() {
		return getClass().getCanonicalName();
	}

	@Override
	public String getHumanReadableName() {
		return "GreyCat Database";
	}

	@Override
	public String getTempDir() {
		return tempFolder.getAbsolutePath();
	}

	@Override
	public Mode currentMode() {
		return mode;
	}

	@Override
	public Set<String> getNodeIndexNames() {
		return luceneIndexer.getIndexNames();
	}

	@Override
	public Set<String> getKnownMMUris() {
		final Set<String> mmURIs = new HashSet<>();
		for (IGraphNode node : getMetamodelIndex().query("*", "*")) {
			String mmURI = (String)node.getProperty(IModelIndexer.IDENTIFIER_PROPERTY);
			mmURIs.add(mmURI);
		}
		return mmURIs;
	}

	public boolean reconnect() {
		CompletableFuture<Boolean> connected = new CompletableFuture<>();

		if (nodeCache != null) {
			nodeCache.invalidateAll();
		}

		if (graph != null) {
			try {
				luceneIndexer.rollback();
			} catch (IOException e) {
				LOGGER.error("Could not rollback Lucene", e);
			}

			/*
			 * Only disconnect storage - we want to release locks on the storage *without*
			 * saving. Seems to be the only simple way to do a rollback to the latest saved
			 * state.
			 */
			graph.storage().disconnect(disconnectedStorage -> {
				connect(connected);
			});
		} else {
			try {
				this.luceneIndexer = new GreycatLuceneIndexer(this, new File(storageFolder, "lucene"));
			} catch (IOException e) {
				LOGGER.error("Could not set up Lucene indexing", e);
			}
			connect(connected);
		}

		try {
			return connected.get();
		} catch (InterruptedException | ExecutionException e) {
			LOGGER.error(e.getMessage(), e);
			return false;
		}
	}

	protected void connect(CompletableFuture<Boolean> cConnected) {
		this.nodeCache = CacheBuilder.newBuilder()
			.maximumSize(1_000)
			.removalListener(new RemovalListener<NodeKey, NodeCacheWrapper>() {
				@Override
				public void onRemoval(RemovalNotification<NodeKey, NodeCacheWrapper> notification) {
					final NodeCacheWrapper wrapper = notification.getValue();
					if (!wrapper.inUse) {
						wrapper.node.free();
					}
				}
			})
			.build();

		this.graph = new GraphBuilder()
			.withMemorySize(1_000_000)
			.withStorage(new RocksDBStorage(storageFolder.getAbsolutePath()))
			.build();

		exitBatchMode();
		graph.connect((connected) -> {
			if (connected) {
				console.println("Connected to Greycat DB at " + storageFolder);
				graph.declareIndex(world, NODE_LABEL_IDX, nodeIndex -> {
					this.nodeLabelIndex = nodeIndex;
					graph.declareIndex(world, SOFT_DELETED_KEY, softDeleteIndex -> {
						this.softDeleteIndex = softDeleteIndex;
						cConnected.complete(true);
					}, SOFT_DELETED_KEY);
				}, NODE_LABEL_IDX);
			} else {
				LOGGER.error("Could not connect to Greycat DB");
				cConnected.complete(false);
			}
		});
	}

	protected void hardDelete(GreycatNode gn, Callback<?> callback) {
		unlink(gn);

		try (GreycatNode.NodeReader rn = gn.getNodeReader()) {
			final Node node = rn.get();
			node.drop(callback);
		}
		nodeCache.invalidate(new NodeKey(gn));
	}

	/**
	 * Marks a node to be ignored within this transaction, and deleted after the
	 * next save.
	 */
	protected void softDelete(GreycatNode gn) {
		unlink(gn);

		// Soft delete, to make definitive after next save
		try (GreycatNode.NodeReader rn = gn.getNodeReader()) {
			final Node node = rn.get();
			node.set(SOFT_DELETED_KEY, Type.BOOL, true);
			softDeleteIndex.update(node);

			rn.markDirty();
		}
	}

	/**
	 * Changes a node so it is disconnected from the graph and cannot be found
	 * through the internal indices.
	 */
	private void unlink(GreycatNode gn) {
		for (IGraphEdge e : gn.getEdges()) {
			e.delete();
		}

		try (NodeReader rn = gn.getNodeReader()) {
			final Node n = rn.get();
			softDeleteIndex.unindex(n);
			nodeLabelIndex.unindex(n);
			luceneIndexer.remove(gn);
		}
	}

	protected void commitLuceneIndex() {
		try {
			luceneIndexer.commit();
		} catch (IOException ex) {
			LOGGER.error("Failed to commit Lucene index", ex);
		}
	}

	/**
	 * Looks up a node, using the Guava LRU cache in the middle.
	 */
	protected NodeCacheWrapper lookup(long world, long time, long id) {
		try {
			return nodeCache.get(new NodeKey(world, time, id), () -> {
				CompletableFuture<Node> result = new CompletableFuture<>();
				graph.lookup(world, time, id, node -> result.complete(node));
				return new NodeCacheWrapper(result.join());
			});
		} catch (ExecutionException e) {
			LOGGER.error(String.format("Failed to lookup node %d:%d:%d", world, time, id), e);
			return null;
		}
	}
}
