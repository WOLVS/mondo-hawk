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

public class ManifestBundle extends ManifestClass {

	final static String CLASSNAME = "ManifestBundle";

	private IHawkAttribute symNameSF;

	public ManifestBundle(ManifestMetamodel p) {
		ep = p;
		symNameSF = new ManifestAttribute("symbolicName");
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
		ret.add(symNameSF);
		return ret;
	}

	@Override
	public Set<IHawkClass> getAllSuperTypes() {
		return new HashSet<>();
	}

	@Override
	public Set<IHawkReference> getAllReferences() {
		return new HashSet<>();
	}

	@Override
	public IHawkStructuralFeature getStructuralFeature(String name) {
		if (name.equals("symbolicName"))
			return symNameSF;
		return null;
	}

}
