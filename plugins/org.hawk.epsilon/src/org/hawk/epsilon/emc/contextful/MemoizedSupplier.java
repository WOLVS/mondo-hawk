/*******************************************************************************
 * Copyright (c) 2018-2019 Aston University.
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
package org.hawk.epsilon.emc.contextful;

import java.util.function.Supplier;

/**
 * Wraps around an existing supplier, which will be used once and then have its
 * value stored for faster access later.
 */
class MemoizedSupplier<T> implements Supplier<T> {
	private Supplier<T> delegate;
	private boolean initialized;

	public MemoizedSupplier(Supplier<T> original) {
		this.delegate = original;
	}

	@Override
	public T get() {
		if (!initialized) {
			T value = delegate.get();
			delegate = () -> value;
			initialized = true;
		}
		return delegate.get();
	}
}