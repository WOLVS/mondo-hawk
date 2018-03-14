/**
 * Copyright (c) 2018 Aston University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Antonio Garcia-Dominguez - initial API and implementation
 */
package org.hawk.examples.docgen.model.document;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Collection</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.hawk.examples.docgen.model.document.Collection#getAuthors <em>Authors</em>}</li>
 *   <li>{@link org.hawk.examples.docgen.model.document.Collection#getDocuments <em>Documents</em>}</li>
 *   <li>{@link org.hawk.examples.docgen.model.document.Collection#getTags <em>Tags</em>}</li>
 * </ul>
 *
 * @see org.hawk.examples.docgen.model.document.DocumentPackage#getCollection()
 * @model
 * @generated
 */
public interface Collection extends EObject {
	/**
	 * Returns the value of the '<em><b>Authors</b></em>' containment reference list.
	 * The list contents are of type {@link org.hawk.examples.docgen.model.document.Author}.
	 * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Authors</em>' containment reference list isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Authors</em>' containment reference list.
	 * @see org.hawk.examples.docgen.model.document.DocumentPackage#getCollection_Authors()
	 * @model containment="true"
	 * @generated
	 */
	EList<Author> getAuthors();

	/**
	 * Returns the value of the '<em><b>Documents</b></em>' containment reference list.
	 * The list contents are of type {@link org.hawk.examples.docgen.model.document.Document}.
	 * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Documents</em>' containment reference list isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Documents</em>' containment reference list.
	 * @see org.hawk.examples.docgen.model.document.DocumentPackage#getCollection_Documents()
	 * @model containment="true"
	 * @generated
	 */
	EList<Document> getDocuments();

	/**
	 * Returns the value of the '<em><b>Tags</b></em>' containment reference list.
	 * The list contents are of type {@link org.hawk.examples.docgen.model.document.Tag}.
	 * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Tags</em>' containment reference list isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Tags</em>' containment reference list.
	 * @see org.hawk.examples.docgen.model.document.DocumentPackage#getCollection_Tags()
	 * @model containment="true"
	 * @generated
	 */
	EList<Tag> getTags();

} // Collection