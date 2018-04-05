/*******************************************************************************
 * Copyright (c) 2017 Aston University
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
 *     Orjuwan Al-Wadeai - Initial Implementation of Hawk Server Configuration
 ******************************************************************************/
package org.hawk.core.util;

public class IndexedAttributeParameters {

	private String metamodelUri;
	private String typeName;
	private String attributeName;

	public IndexedAttributeParameters(String metamodelUri, String typeName,
			String attributeName) {
		this.metamodelUri = metamodelUri;
		this.typeName = typeName;
		this.attributeName = attributeName;
	}

	public IndexedAttributeParameters() {
		// TODO Auto-generated constructor stub
	}

	public String getMetamodelUri() {
		return metamodelUri;
	}
	public void setMetamodelUri(String metamodelUri) {
		this.metamodelUri = metamodelUri;
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	public String getAttributeName() {
		return attributeName;
	}
	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((attributeName == null) ? 0 : attributeName.hashCode());
		result = prime * result
				+ ((metamodelUri == null) ? 0 : metamodelUri.hashCode());
		result = prime * result
				+ ((typeName == null) ? 0 : typeName.hashCode());
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
		IndexedAttributeParameters other = (IndexedAttributeParameters) obj;
		if (attributeName == null) {
			if (other.attributeName != null)
				return false;
		} else if (!attributeName.equals(other.attributeName))
			return false;
		if (metamodelUri == null) {
			if (other.metamodelUri != null)
				return false;
		} else if (!metamodelUri.equals(other.metamodelUri))
			return false;
		if (typeName == null) {
			if (other.typeName != null)
				return false;
		} else if (!typeName.equals(other.typeName))
			return false;
		return true;
	}
}

