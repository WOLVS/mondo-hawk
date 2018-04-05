/*******************************************************************************
 * Copyright (c) 2011-2016 The University of York.
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
package org.hawk.manifest.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.osgi.util.ManifestElement;
import org.hawk.core.IFileImporter;
import org.hawk.core.IModelResourceFactory;
import org.osgi.framework.Constants;

public class ManifestModelResourceFactory implements IModelResourceFactory {

	private String type = "org.hawk.manifest.metamodel.ManifestModelParser";
	private String hrn = "Manifest Model Resource Factory";

	private Set<String> modelExtensions;

	public ManifestModelResourceFactory() {
		modelExtensions = new HashSet<String>();
		modelExtensions.add("MANIFEST.MF".toLowerCase());
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getHumanReadableName() {
		return hrn;
	}

	@Override
	public ManifestModelResource parse(IFileImporter importer, File f) {
		final Map<String, String> map = getManifestContents(f);
		ManifestModelResource ret = null;
		try {
			ret = new ManifestModelResource(f.toURI().toString(), this, map);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ret;
	}

	@Override
	public void shutdown() {
	}

	@Override
	public Set<String> getModelExtensions() {
		return modelExtensions;
	}

	@Override
	public boolean canParse(File f) {

		Map<String, String> map = getManifestContents(f);
		return map != null && map.get(Constants.BUNDLE_SYMBOLICNAME) != null;

	}

	private Map<String, String> getManifestContents(File f) {

		try {
			InputStream i = new FileInputStream(f);
			return ManifestElement.parseBundleManifest(i, null);
		} catch (Exception e) {
			System.err.println("error in parsing manifest file: " + f.getPath()
					+ ", returning null for getManifestContents");
			return null;
		}
	}

}
