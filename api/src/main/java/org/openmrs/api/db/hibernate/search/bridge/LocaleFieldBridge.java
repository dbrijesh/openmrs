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

import java.util.Locale;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeFromIndexedValueContext;

/**
 * Indexes {@link Locale} values as their {@link Locale#toString()} string representation.
 */
public class LocaleFieldBridge implements ValueBridge<Locale, String> {

	@Override
	public String toIndexedValue(Locale value, ValueBridgeToIndexedValueContext context) {
		if (value == null) {
			return null;
		}
		return value.toString();
	}

	@Override
	public Locale fromIndexedValue(String value, ValueBridgeFromIndexedValueContext context) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		return new Locale(value);
	}

}
