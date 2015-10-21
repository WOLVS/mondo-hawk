/*******************************************************************************
 * Copyright (c) 2015 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Antonio Garcia-Dominguez - initial API and implementation
 *******************************************************************************/
package org.hawk.emfresource.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.hawk.emfresource.HawkResource;
import org.hawk.emfresource.HawkResourceChangeListener;
import org.hawk.graph.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Surrogate resource to be created by a {@link HawkResource} to represent that
 * a particular model element is within a certain file in the graph. It delegates
 * most of its operations to the main resource, except for handling URI fragments,
 * as they are only valid within a particular file.
 */
public class HawkFileResourceImpl extends ResourceImpl implements HawkResource {

	private static final Logger LOGGER = LoggerFactory.getLogger(HawkFileResourceImpl.class);
	private final HawkResource mainResource;

	private final FileNode fileNode;
	private final BiMap<String, String> nodeIdToFragment = HashBiMap.create();

	/** Only to be used from Exeed (from the createExecutableExtension Eclipse call). */
	public HawkFileResourceImpl() {
		this.fileNode = null;
		this.mainResource = null;
	}

	/**
	 * Creates a resource as a subordinate of another. Used to indicate the
	 * repository URL and file of an {@link EObject}.
	 */
	public HawkFileResourceImpl(final FileNode fileNode, final HawkResource mainResource) {
		super(URI.createURI(fileNode.getRepositoryURL() + fileNode.getFilePath()));
		this.fileNode = fileNode;
		this.mainResource = mainResource;
	}

	public FileNode getFileNode() {
		return fileNode;
	}

	@Override
	public boolean hasChildren(final EObject o) {
		if (mainResource != null) {
			return mainResource.hasChildren(o);
		} else {
			return o.eAllContents().hasNext();
		}
	}

	@Override
	public Map<EObject, Object> fetchValuesByEStructuralFeature(final EStructuralFeature feature) throws Exception {
		return mainResource.fetchValuesByEStructuralFeature(feature);
	}

	@Override
	public EList<EObject> fetchNodes(final EClass eClass) throws Exception {
		return mainResource.fetchNodes(eClass);
	}

	@Override
	public EList<EObject> fetchNodes(final List<String> ids) throws Exception {
		return mainResource.fetchNodes(ids);
	}

	@Override
	public List<Object> fetchValuesByEClassifier(final EClassifier dataType) throws Exception {
		return mainResource.fetchValuesByEClassifier(dataType);
	}

	@Override
	public Map<EClass, List<EStructuralFeature>> fetchTypesWithEClassifier(final EClassifier dataType) throws Exception {
		return mainResource.fetchTypesWithEClassifier(dataType);
	}

	@Override
	public boolean addSyncEndListener(final Runnable r) {
		return mainResource.addSyncEndListener(r);
	}

	@Override
	public boolean removeSyncEndListener(final Runnable r) {
		return mainResource.removeSyncEndListener(r);
	}

	@Override
	public boolean addChangeListener(final HawkResourceChangeListener l) {
		return mainResource.addChangeListener(l);
	}

	@Override
	public boolean removeChangeListener(final HawkResourceChangeListener l) {
		return mainResource.removeChangeListener(l);
	}

	@Override
	protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
		// saving is not supported by Hawk (it's a read-only index)
		throw new UnsupportedOperationException();
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		// do nothing - resource is populated from the main Hawk resource
	}

	@Override
	public boolean isLoaded() {
		// We don't want this resource to be unloaded unless unloading the main
		// Hawk resource, so we never report it as loaded. This is needed to make
		// on-the-fly updating work, as EcoreEditor keeps track of resources that
		// have been changed outside it and reloads them if they are loaded.
		return false;
	}

	@Override
	public List<String> getRegisteredMetamodels() throws Exception {
		return mainResource.getRegisteredMetamodels();
	}

	@Override
	public List<String> getRegisteredTypes(String metamodelURI) throws Exception {
		return mainResource.getRegisteredTypes(metamodelURI);
	}

	@Override
	public String getEObjectNodeID(EObject obj) {
		return mainResource.getEObjectNodeID(obj);
	}

	public void addFragment(String nodeId, String fragment) {
		nodeIdToFragment.forcePut(nodeId, fragment);
	}

	public void removeFragment(String nodeId) {
		nodeIdToFragment.remove(nodeId);
	}

	@Override
	public String getURIFragment(EObject eObject) {
		final String nodeId = getEObjectNodeID(eObject);
		return nodeIdToFragment.get(nodeId);
	}

	@Override
	public EObject getEObject(String uriFragment) {
		String nodeId = nodeIdToFragment.inverse().get(uriFragment);
		try {
			if (nodeId == null) {
				/**
				 * We don't have that fragment: ask the main resource for the
				 * EObject.
				 */
				return fetchNode(this, uriFragment);
			} else {
				return fetchNode(nodeId);
			}
		} catch (Exception e) {
			LOGGER.error("Could not retrieve EObject by fragment", e);
			return null;
		}
	}

	@Override
	public EObject fetchNode(HawkResource containerResource, String uriFragment) throws Exception {
		return mainResource.fetchNode(containerResource, uriFragment);
	}

	@Override
	public EObject fetchNode(String id) throws Exception {
		return mainResource.fetchNode(id);
	}
}
