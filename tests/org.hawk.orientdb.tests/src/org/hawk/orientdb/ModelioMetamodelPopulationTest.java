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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.hawk.core.IModelIndexer.ShutdownRequestType;
import org.hawk.core.model.IHawkClassifier;
import org.hawk.core.runtime.ModelIndexerImpl;
import org.hawk.core.security.FileBasedCredentialsStore;
import org.hawk.core.util.DefaultConsole;
import org.hawk.epsilon.emc.EOLQueryEngine;
import org.hawk.graph.GraphWrapper;
import org.hawk.graph.MetamodelNode;
import org.hawk.graph.TypeNode;
import org.hawk.graph.internal.updater.GraphMetaModelUpdater;
import org.hawk.graph.internal.updater.GraphModelUpdater;
import org.hawk.graph.syncValidationListener.SyncValidationListener;
import org.hawk.localfolder.LocalFolder;
import org.hawk.modelio.exml.listeners.ModelioGraphChangeListener;
import org.hawk.modelio.exml.metamodel.ModelioMetaModelResourceFactory;
import org.hawk.modelio.exml.metamodel.ModelioPackage;
import org.hawk.modelio.exml.metamodel.register.MetamodelRegister;
import org.hawk.modelio.exml.model.ModelioModelResourceFactory;
import org.hawk.orientdb.util.FileUtils;
import org.hawk.orientdb.util.SyncEndListener;
import org.junit.After;
import org.junit.Test;

/**
 * Integration test case that indexes a simple model and performs a query.
 */
public class ModelioMetamodelPopulationTest {

	private DefaultConsole console;
	protected OrientDatabase db;
	private ModelIndexerImpl indexer;
	private EOLQueryEngine queryEngine;
	private SyncValidationListener validationListener;

	private final String METAMODEL_PATH = "resources/metamodels/";

	
	public void setup(String testCaseName, boolean doValidation) throws Exception {
		final File dbFolder = new File("testdb" + testCaseName);
		FileUtils.deleteRecursively(dbFolder);
		dbFolder.mkdir();

		final File indexerFolder = new File("testindexer" + testCaseName);
		FileUtils.deleteRecursively(indexerFolder);
		indexerFolder.mkdir();

		console = new DefaultConsole();
		createDB(dbFolder);

		final FileBasedCredentialsStore credStore = new FileBasedCredentialsStore(
				new File("keystore"), "admin".toCharArray());

		indexer = new ModelIndexerImpl("test", indexerFolder, credStore, console);
		indexer.addMetaModelResourceFactory(new ModelioMetaModelResourceFactory());
		indexer.addModelResourceFactory(new ModelioModelResourceFactory());
		queryEngine = new EOLQueryEngine();
		indexer.addQueryEngine(queryEngine);
		indexer.setMetaModelUpdater(new GraphMetaModelUpdater());
		indexer.addModelUpdater(new GraphModelUpdater());
		indexer.addGraphChangeListener(new ModelioGraphChangeListener(indexer));
		indexer.setDB(db, true);
		indexer.init(0, 0);
		queryEngine.load(indexer);

		File file = new File( METAMODEL_PATH ,"metamodel_descriptor.xml");
		indexer.registerMetamodels(file);
		
		if (doValidation) {
			validationListener = new SyncValidationListener();
			indexer.addGraphChangeListener(validationListener);
			validationListener.setModelIndexer(indexer);
		}
	}

	protected void createDB(final File dbFolder) throws Exception {
		db = new OrientDatabase();
		db.run("plocal:" + dbFolder.getAbsolutePath(), dbFolder, console);
	}

	@After
	public void teardown() throws Exception {
		indexer.shutdown(ShutdownRequestType.ALWAYS);
		indexer.removeGraphChangeListener(validationListener);
	}

	@Test
	public void metamodel() throws Exception {
		setup("modeliomm", true);

		int nTypes = 0;
		final Collection<ModelioPackage> pkgs = MetamodelRegister.INSTANCE.getRegisteredPackages();
		nTypes = visitPackages(nTypes, pkgs);

		// From 'grep -c MClass MMetamodel.java' on modelio-metamodel-lib
		assertEquals(409, nTypes);
	}

	protected int visitPackages(int nTypes, final Collection<ModelioPackage> pkgs) {
		final GraphWrapper gw = new GraphWrapper(db);

		for (ModelioPackage mpkg : pkgs) {
			MetamodelNode mmNode = gw.getMetamodelNodeByNsURI(mpkg.getNsURI());

			final Set<String> types = new HashSet<>();
			for (TypeNode typeNode : mmNode.getTypes()) {
				types.add(typeNode.getTypeName());
				++nTypes;
			}

			for (IHawkClassifier mc : mpkg.getClasses()) {
				assertTrue(types.contains(mc.getName()));
			}
		}

		return nTypes;
	}

	@Test
	public void zoo() throws Throwable {
		setup("modeliozoo", true);

		final LocalFolder vcs = new LocalFolder();
		vcs.init(new File("resources/models/zoo").toURI().toASCIIString(), indexer);
		vcs.run();
		indexer.addVCSManager(vcs, true);
		indexer.requestImmediateSync();

		SyncEndListener.waitForSync(indexer, 200, new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertEquals(0, validationListener.getTotalErrors());
				assertEquals(6, queryEngine.getAllOfKind("Class").size());
				assertEquals(6, queryEngine.query(indexer, "return Class.all.size;", null));
				return null;
			}
		});
	}
}
