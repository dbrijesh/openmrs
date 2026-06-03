/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import liquibase.resource.AbstractResourceAccessor;
import liquibase.resource.Resource;
import liquibase.resource.URIResource;

/**
 * Implementation of liquibase FileOpener interface so that the {@link OpenmrsClassLoader} will be
 * used to find files (or any other classloader that is passed into the constructor). This allows
 * liquibase xml files in modules to be found.
 */
public class ClassLoaderFileOpener extends AbstractResourceAccessor {

	/**
	 * The classloader to read from
	 */
	private final ClassLoader cl;

	/**
	 * @param cl the {@link ClassLoader} to use for finding files.
	 */
	public ClassLoaderFileOpener(ClassLoader cl) {
		this.cl = cl;
	}

	@Override
	public List<Resource> getAll(String path) throws IOException {
		List<Resource> result = new ArrayList<>();

		if (path == null || path.isEmpty()) {
			return result;
		}

		URL url = cl.getResource(path);
		if (url != null) {
			try {
				result.add(new URIResource(path, url.toURI()));
			}
			catch (URISyntaxException e) {
				throw new IOException(e);
			}
		}

		return result;
	}

	@Override
	public List<Resource> search(String path, boolean recursive) throws IOException {
		return new ArrayList<>();
	}

	@Override
	public SortedSet<String> list(String s, String s1, boolean b, boolean b1, boolean b2) throws IOException {
		return new TreeSet<>();
	}

	@Override
	public List<String> describeLocations() {
		return new ArrayList<>();
	}

	@Override
	public void close() throws Exception {
	}
}
