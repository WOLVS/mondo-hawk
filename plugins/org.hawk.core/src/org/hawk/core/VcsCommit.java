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
 * 	   Nikolas Matragkas, James Williams, Dimitris Kolovos - initial API and implementation
 *     Konstantinos Barmpis - adaption for use in Hawk
 ******************************************************************************/
package org.hawk.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VcsCommit implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected Date date;
	protected String author;
	protected String message;
	protected List<VcsCommitItem> items = new ArrayList<VcsCommitItem>();
	protected VcsRepositoryDelta repositoryDelta;
	protected String revision;

	public Date getJavaDate() {
		return date;
	}

	public void setJavaDate(Date date) {
		this.date = date;
	}

	public String getRevision() {
		return revision;
	}

	public void setRevision(String revision) {
		this.revision = revision;
	}

	public VcsRepositoryDelta getDelta() {
		return repositoryDelta;
	}

	public void setDelta(VcsRepositoryDelta delta) {
		this.repositoryDelta = delta;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<VcsCommitItem> getItems() {
		return items;
	}

	public String toString() {

		return revision + " : " + items;

	}

}
