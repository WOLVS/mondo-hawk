/*******************************************************************************
 * Copyright (c) 2015 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * Contributors:
 *    Antonio Garcia-Dominguez - initial API and implementation
 *******************************************************************************/
package org.hawk.service.servlet.servlets;

import org.apache.thrift.protocol.TJSONProtocol;
import org.hawk.service.api.utils.APIUtils.ThriftProtocol;
import org.hawk.service.servlet.processors.HawkThriftProcessorFactory;

/**
 * Servlet that exposes {@link HawkThriftIface} through the {@link TJSONProtocol}.
 */
public class HawkThriftJSONServlet extends HawkThriftServlet {
	private static final long serialVersionUID = 1L;

	public HawkThriftJSONServlet() throws Exception {
		super(new HawkThriftProcessorFactory(ThriftProtocol.JSON));
	}
}
