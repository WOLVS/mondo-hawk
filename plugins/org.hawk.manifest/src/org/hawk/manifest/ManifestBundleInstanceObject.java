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
package org.hawk.manifest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClassifier;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.model.IHawkStructuralFeature;
import org.hawk.manifest.model.ManifestModelResource;

public class ManifestBundleInstanceObject extends ManifestObject {

	byte[] signature;
	private String version;

	private List<String> otherProperties;

	private ManifestBundleObject bundle;

	private Collection<ManifestRequiresObject> requires = new HashSet<>();
	private Collection<ManifestImportObject> imports = new HashSet<>();
	private Collection<ManifestPackageInstanceObject> exports = new HashSet<>();

	public ManifestBundleInstanceObject(String version, ManifestModelResource res, ManifestBundleObject bundle,
			List<String> otherProperties) {
		this.version = version;
		this.res = res;
		this.bundle = bundle;
		this.otherProperties = otherProperties;
	}

	@Override
	public boolean URIIsRelative() {
		return false;
	}

	@Override
	public String getUriFragment() {
		return "bundleinstance";
	}

	@Override
	public boolean isFragmentUnique() {
		return false;
	}

	@Override
	public IHawkClassifier getType() {
		return res.getType(ManifestBundleInstance.CLASSNAME);
	}

	@Override
	public boolean isSet(IHawkStructuralFeature hsf) {
		String name = hsf.getName();
		switch (name) {
		case "version":
			return version != null;
		case "provides":
			return bundle != null;
		case "imports":
			return imports != null;
		case "exports":
			return exports != null;
		case "requires":
			return requires != null;
		case "otherProperties":
			return otherProperties != null;
		default:
			return false;
		}
	}

	@Override
	public Object get(IHawkAttribute attr) {
		String name = attr.getName();
		switch (name) {
		case "version":
			return version;
		case "otherProperties":
			return otherProperties;
		default:
			System.err.println("attribute: " + name + " not found for ManifestBundleObject, returning null");
			return null;
		}
	}

	@Override
	public Object get(IHawkReference ref, boolean b) {

		String name = ref.getName();
		switch (name) {
		case "provides":
			return bundle;
		case "imports": {
			return imports;
		}
		case "exports": {
			return exports;
		}
		case "requires": {
			return requires;
		}
		default:
			System.err.println("attribute: " + name + " not found for ManifestBundleObject, returning null");
			return null;
		}
	}

	@Override
	public String getUri() {
		return res.getUri() + "#" + getUriFragment();
	}

	public void addRequires(ManifestRequiresObject req) {
		requires.add(req);
	}

	public void addImport(ManifestImportObject imp) {
		imports.add(imp);
	}

	public void addExport(ManifestPackageInstanceObject pe) {
		exports.add(pe);
	}

}
