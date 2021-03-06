/*******************************************************************************
 * Copyright (c) 2011-2017 The University of York, Aston University.
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
 *     Antonio Garcia-Dominguez - cleanup and use covariant return types
 ******************************************************************************/
package org.hawk.emf;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.hawk.core.model.IHawkAttribute;
import org.hawk.core.model.IHawkClassifier;

public class EMFAttribute extends EMFModelElement implements IHawkAttribute {

	private EAttribute emfAttribute;

	public EMFAttribute(EAttribute att, EMFWrapperFactory wf) {
		super(att, wf);
		this.emfAttribute = att;
	}

	@Override
	public boolean isDerived() {
		return emfAttribute.isDerived();
	}

	@Override
	public String getName() {
		return emfAttribute.getName();
	}

	@Override
	public EAttribute getEObject() {
		return emfAttribute;
	}

	@Override
	public boolean isMany() {
		return emfAttribute.isMany();
	}

	@Override
	public boolean isUnique() {
		return emfAttribute.isUnique();
	}

	@Override
	public boolean isOrdered() {
		return emfAttribute.isOrdered();
	}

	@Override
	public IHawkClassifier getType() {
		EClassifier type = emfAttribute.getEType();
		if (type instanceof EClass)
			return wf.createClass((EClass) emfAttribute.getEType());
		else if (type instanceof EDataType)
			return wf.createDataType((EDataType) emfAttribute.getEType());
		else {
			return null;
		}
	}

	@Override
	public int hashCode() {
		return emfAttribute.hashCode();

	}

}
