/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate.search.bridge;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeFromIndexedValueContext;
import org.openmrs.OpenmrsObject;

/**
 * Indexes {@link OpenmrsObject} instances as their integer ID string.
 */
public class OpenmrsObjectFieldBridge implements ValueBridge<OpenmrsObject, String> {

	@Override
	public String toIndexedValue(OpenmrsObject value, ValueBridgeToIndexedValueContext context) {
		if (value == null) {
			return null;
		}
		return value.getId().toString();
	}

	@Override
	public OpenmrsObject fromIndexedValue(String value, ValueBridgeFromIndexedValueContext context) {
		// Reading back an OpenmrsObject from a plain ID string is not supported here.
		throw new UnsupportedOperationException("Cannot reconstruct an OpenmrsObject from a stored ID string");
	}

}
