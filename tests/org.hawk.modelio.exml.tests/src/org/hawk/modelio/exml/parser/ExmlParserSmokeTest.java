/*******************************************************************************
 * Copyright (c) 2015 The University of York.
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
package org.hawk.modelio.exml.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.hawk.modelio.exml.model.parser.ExmlObject;
import org.hawk.modelio.exml.model.parser.ExmlParser;
import org.junit.Test;

/**
 * Smoke test for the {@link ExmlParser} that tries to parse collections of
 * <code>.exml</code> files and see if we run into any errors.
 */
public class ExmlParserSmokeTest {

	private final class ExmlVisitor extends SimpleFileVisitor<Path> {
		private final ExmlParser parser;
		public int nClasses = 0;

		private ExmlVisitor(ExmlParser parser) {
			this.parser = parser;
		}

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
			final File f = path.toFile();
			final String fileName = f.getName();
			if (fileName.endsWith(".exml")) {
				try (final InputStream is = new BufferedInputStream(new FileInputStream(f))) {
					ExmlObject o = parser.getObject(f, is);
					if (o.getMClassName().equals("Class")) {
						nClasses++;
					}
				} catch (Exception e) {
					fail("Failed to parse " + fileName + ": " + e.getMessage());
				}
			}
			return FileVisitResult.CONTINUE;
		}
	}

	@Test
	public void zoo() throws Exception {
		final ExmlParser parser = new ExmlParser();

		final ExmlVisitor fv = new ExmlVisitor(parser);
		Files.walkFileTree(new File("resources").toPath(), fv);
		assertEquals(6, fv.nClasses);
	}

	@SuppressWarnings("unused")
	@Test
	public void jenkinsArchive() throws Exception {
		final ExmlParser parser = new ExmlParser();
		final File f = new File("resources/jenkins/jenkins_1.540.0.ramc");
		Iterable<ExmlObject> objects = parser.getObjects(f);

		final long millis = System.currentTimeMillis();
		long parsed = 0;
		for (ExmlObject o : objects) {
			++parsed;
		}
		System.out.println("Parsed " + parsed + " fragments in " + (System.currentTimeMillis() - millis) + "ms");
	}
}
