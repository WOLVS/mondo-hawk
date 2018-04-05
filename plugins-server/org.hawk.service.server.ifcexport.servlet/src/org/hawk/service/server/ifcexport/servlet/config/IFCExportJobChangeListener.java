/*******************************************************************************
 * Copyright (c) 2015-2016 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * Contributors:
 *    Ran Wei - initial API and implementation
 *******************************************************************************/
package org.hawk.service.server.ifcexport.servlet.config;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.hawk.service.api.IFCExportJob;
import org.hawk.service.api.IFCExportStatus;

public class IFCExportJobChangeListener extends JobChangeAdapter {

	protected IFCExportJob job;
	
	public IFCExportJobChangeListener(IFCExportJob job) {
		this.job = job;
	}

	public void setJob(IFCExportJob job) {
		this.job = job;
	}

	@Override
	public void done(IJobChangeEvent event) {
		job.setStatus(IFCExportStatus.DONE);
	}

	@Override
	public void running(IJobChangeEvent event) {
		job.setStatus(IFCExportStatus.RUNNING);
	}

	@Override
	public void scheduled(IJobChangeEvent event) {
		job.setStatus(IFCExportStatus.SCHEDULED);
	}
}
