/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
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
package org.hawk.bpmn;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EReference;
import org.hawk.core.model.IHawkClassifier;
import org.hawk.core.model.IHawkReference;

public class BPMNReference extends BPMNObject implements IHawkReference {

	EReference r;

	public BPMNReference(EReference re) {
		super(re);
		r = re;

	}

	@Override
	public String getName() {
		return r.getName();
	}

	// @Override
	// public EStructuralFeature getEMFreference() {
	// return r;
	// }

	@Override
	public boolean isContainment() {
		return r.isContainment();
	}

	@Override
	public boolean isContainer() {
		return r.isContainer();
	}

	@Override
	public boolean isMany() {

		return r.isMany();
	}

	// @Override
	// public boolean isChangeable() {
	//
	// return r.isChangeable();
	// }

	// @Override
	// public int getUpperBound() {
	//
	// return r.getUpperBound();
	// }

	// @Override
	// public HawkClass getType() {
	//
	// return new EMFclass((EClass) r.getEType());
	// }

	@Override
	public boolean isOrdered() {
		return r.isOrdered();
	}

	@Override
	public boolean isUnique() {
		return r.isUnique();
	}

	@Override
	public IHawkClassifier getType() {
		EClassifier type = r.getEType();
		if (type instanceof EClass)
			return new BPMNClass((EClass) r.getEType());
		else if (type instanceof EDataType)
			return new BPMNDataType((EDataType) r.getEType());
		else {
			System.err.println("ref: " + r.getEType());
			return null;
		}
	}

	@Override
	public int hashCode() {
		return r.hashCode();

	}
	
}
