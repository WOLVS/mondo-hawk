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

import java.util.HashSet;
import java.util.Set;

import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClass;
import org.hawk.core.model.IHawkClassifier;
import org.hawk.core.model.IHawkMetaModelResource;
import org.hawk.core.model.IHawkPackage;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.model.IHawkStructuralFeature;
import org.hawk.manifest.metamodel.ManifestMetaModelResource;

public class ManifestMetamodel extends ManifestObject implements IHawkPackage {

	private final static String CLASSNAME = "ManifestMetamodel";
	
	private Set<IHawkClassifier> classes = new HashSet<>();
	ManifestMetaModelResource manifestMetaModelResource;

	public ManifestMetamodel(ManifestMetaModelResource manifestMetaModelResource) {
		this.manifestMetaModelResource = manifestMetaModelResource;
	}

	public void add(IHawkClass mc) {
		classes.add(mc);
	}

	@Override
	public String getUri() {
		return getNsURI();
	}

	@Override
	public String getUriFragment() {
		return "/";
	}

	@Override
	public boolean isFragmentUnique() {
		return false;
	}

	@Override
	public IHawkClassifier getType() {
		return null;
	}

	@Override
	public boolean isSet(IHawkStructuralFeature hsf) {
		return false;
	}

	@Override
	public Object get(IHawkAttribute attr) {
		return null;
	}

	@Override
	public Object get(IHawkReference ref, boolean b) {
		return null;
	}

	@Override
	public String getName() {
		return CLASSNAME;
	}

	@Override
	public IHawkClass getClassifier(String string) throws Exception {
		for (IHawkClassifier c : classes)
			if (c.getName().equals(string))
				return ((IHawkClass) c);
		return null;
	}

	@Override
	public String getNsURI() {
		return "org.hawk.MANIFEST";
	}

	@Override
	public Set<IHawkClassifier> getClasses() {
		return classes;
	}

	@Override
	public IHawkMetaModelResource getResource() {
		return manifestMetaModelResource;
	}

}
