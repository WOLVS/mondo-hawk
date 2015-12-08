/*******************************************************************************
 * Copyright (c) 2015 The University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Antonio Garcia-Dominguez - initial API and implementation
 ******************************************************************************/
package org.hawk.orientdb.indexes;

import java.util.Collections;
import java.util.Iterator;

import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.index.OCompositeKey;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.index.OIndexCursor;

final class SingleKeyOIndexCursorFactory implements OIndexCursorFactory {
	private final Object valueExpr;
	private final AbstractOrientIndex idx;
	private final String fieldName;

	SingleKeyOIndexCursorFactory(Object valueExpr, AbstractOrientIndex idx, String fieldName) {
		this.valueExpr = valueExpr;
		this.idx = idx;
		this.fieldName = fieldName;
	}

	@Override
	public Iterator<OIdentifiable> query() {
		final OIndex<?> index = idx.getIndex(valueExpr.getClass());
		if (index == null) {
			return Collections.emptyListIterator();
		}

		if (!(valueExpr instanceof String)) {
			// Not a string: go straight to the value
			return index.iterateEntries(Collections.singleton(new OCompositeKey(fieldName, valueExpr)), false);
		}

		final String sValueExpr = valueExpr.toString();
		final int starPosition = sValueExpr.indexOf('*');
		if (starPosition < 0) {
			// No '*' found: go straight to the value
			return index.iterateEntries(Collections.singleton(new OCompositeKey(fieldName, valueExpr)), false);
		}
		else if (starPosition == 0) {
			// value expr starts with "*"
			if (sValueExpr.length() == 1) {
				// value expr is "*": iterate over everything
				return index.iterateEntriesBetween(
					new OCompositeKey(fieldName, AbstractOrientIndex.getMinValue(valueExpr.getClass())), true,
					new OCompositeKey(fieldName, AbstractOrientIndex.getMaxValue(valueExpr.getClass())), true,
					false);
			} else {
				// value expr starts with "*": filter all entries based on the fragments between the *
				final String[] fragments = sValueExpr.split("[*]");
				final OIndexCursor cursor = index.cursor();
				return new FragmentFilteredIndexCursor(fragments, cursor);
			}
		}
		else if (starPosition == sValueExpr.length() - 1) {
			final String prefix = sValueExpr.substring(0, starPosition);
			return prefixCursor(index, prefix);
		} else {
			// value expr has one or more "*" inside: use prefix to first * as a filter and
			// then wrap with a proper filter for the rest
			final String[] fragments = sValueExpr.split("[*]");
			final OIndexCursor cursor = prefixCursor(index, sValueExpr.substring(0, starPosition));
			return new FragmentFilteredIndexCursor(fragments, cursor);
		}
	}

	private OIndexCursor prefixCursor(OIndex<?> index, String prefix) {
		// prefix is S + C + "*", where S is a substring and C is a character:
		// do ranged query between S + C and S + (C+1) (the next Unicode code point)
		final char lastChar = prefix.charAt(prefix.length() - 1);
		final String rangeStart = prefix;
		final String rangeEnd = prefix.substring(0, rangeStart.length() - 1) + Character.toString((char)(lastChar+1));
		return index.iterateEntriesBetween(new OCompositeKey(fieldName, rangeStart), true,
				new OCompositeKey(fieldName, rangeEnd), true, false);
	}
}