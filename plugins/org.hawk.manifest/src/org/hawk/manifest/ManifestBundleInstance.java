/*******************************************************************************
 * Copyright (c) 2011-2014 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * Contributors:
 *     Konstantinos Barmpis - initial API and implementation
 ******************************************************************************/
package org.hawk.manifest;

import java.util.HashSet;
import java.util.Set;

import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClass;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.model.IHawkStructuralFeature;
import org.hawk.manifest.utils.Utils;

public class ManifestBundleInstance extends ManifestClass {

	final static String CLASSNAME = "ManifestBundleInstance";

	private IHawkAttribute version;
	private Set<IHawkReference> references;
	private IHawkAttribute otherproperties;

	public ManifestBundleInstance(ManifestMetamodel p) {
		ep = p;
		version = new ManifestAttribute("version");
		otherproperties = new ManifestAttribute("otherProperties");
		references = new HashSet<>();
		references.add(new ManifestReference("provides", false, new ManifestBundle(p)));
		references.add(new ManifestReference("requires", true, new ManifestRequires(p)));
		references.add(new ManifestReference("imports", true, new ManifestImport(p)));
		references.add(new ManifestReference("exports", true, new ManifestPackageInstance(p)));
	}

	@Override
	public String getInstanceType() {
		return ep.getNsURI() + "#" + CLASSNAME + "Object";
	}

	@Override
	public String getUri() {
		return ep.getNsURI() + "#" + CLASSNAME;
	}

	@Override
	public String getUriFragment() {
		return CLASSNAME;
	}

	@Override
	public String getName() {
		return CLASSNAME;
	}

	@Override
	public String getPackageNSURI() {
		return ep.getNsURI();
	}

	@Override
	public Set<IHawkAttribute> getAllAttributes() {
		Set<IHawkAttribute> ret = new HashSet<>();
		ret.add(version);
		ret.add(otherproperties);
		return ret;
	}

	@Override
	public Set<IHawkClass> getAllSuperTypes() {
		return new HashSet<>();
	}

	@Override
	public Set<IHawkReference> getAllReferences() {
		return references;
	}

	@Override
	public IHawkStructuralFeature getStructuralFeature(String name) {
		if (name.equals("version"))
			return version;
		if (name.equals("otherProperties"))
			return otherproperties;
		if (name.equals("provides"))
			return new Utils().getReference("provides", references);
		if (name.equals("requires"))
			return new Utils().getReference("requires", references);
		if (name.equals("imports"))
			return new Utils().getReference("imports", references);
		if (name.equals("exports"))
			return new Utils().getReference("exports", references);
		return null;
	}

}
