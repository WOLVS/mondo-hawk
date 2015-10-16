/*******************************************************************************
 * Copyright (c) 2015 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Antonio Garcia-Dominguez - initial API and implementation
 *******************************************************************************/
package org.hawk.ui.emf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Factory;
import org.hawk.osgiserver.HModel;
import org.hawk.ui.emf.impl.LocalHawkResourceImpl;
import org.hawk.ui2.util.HUIManager;

public class LocalHawkResourceFactoryImpl implements Factory {
	@Override
	public Resource createResource(URI uri) {
		String hawkInstance;
		if ("hawk+local".equals(uri.scheme())) {
			hawkInstance = uri.host();
		} else {
			final String filePath = CommonPlugin.resolve(uri).toFileString();
			try (final BufferedReader br = new BufferedReader(new FileReader(filePath))) {
				hawkInstance = br.readLine();
			} catch (IOException e) {
				Activator.logError("Could not read " + filePath, e);
				return new LocalHawkResourceImpl(null);
			}
		}

		final HModel hawkModel = HUIManager.getInstance().getHawkByName(hawkInstance);
		return new LocalHawkResourceImpl(hawkModel.getIndexer());
	}
}
;