/*******************************************************************************
 * Copyright (c) 2015 The University of York.
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
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.modelio.exml.model;

import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkReference;
import org.hawk.core.model.IHawkStructuralFeature;
import org.hawk.modelio.exml.metamodel.AbstractModelioObject;
import org.hawk.modelio.exml.metamodel.ModelioClass;
import org.hawk.modelio.exml.model.parser.ExmlReference;

public class ModelioProxy extends AbstractModelioObject {

	private final ModelioClass mc;
	private final ExmlReference exml;

	public ModelioProxy(ModelioClass mc, ExmlReference r) {
		this.mc = mc;
		this.exml = r;
	}

	@Override
	public boolean isRoot() {
		// There's no way to know from here!
		return false;
	}

	@Override
	public String getUri() {
		return "*#" + getUriFragment();
	}

	@Override
	public String getUriFragment() {
		return exml.getUID();
	}

	@Override
	public boolean isFragmentUnique() {
		return true;
	}

	@Override
	public ModelioClass getType() {
		return mc;
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
	public String getExml() {
		return "*";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exml == null) ? 0 : exml.hashCode());
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
		ModelioProxy other = (ModelioProxy) obj;
		if (exml == null) {
			if (other.exml != null)
				return false;
		} else if (!exml.equals(other.exml))
			return false;
		return true;
	}
}
