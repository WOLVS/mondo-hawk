/*******************************************************************************
 * Copyright (c) 2015-2017 The University of York, Aston University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.integration.tests.bpmn;

import org.hawk.bpmn.metamodel.BPMNMetaModelResourceFactory;
import org.hawk.bpmn.model.BPMNModelResourceFactory;
import org.hawk.core.IMetaModelResourceFactory;
import org.hawk.core.IModelResourceFactory;
import org.hawk.integration.tests.ModelIndexingTest.IModelSupportFactory;

/**
 * Factory for the instances needed to parse EMF file-based models.
 */
public class BPMNModelSupportFactory implements IModelSupportFactory {
	@Override
	public IModelResourceFactory createModelResourceFactory() {
		return new BPMNModelResourceFactory();
	}

	@Override
	public IMetaModelResourceFactory createMetaModelResourceFactory() {
		return new BPMNMetaModelResourceFactory();
	}
}