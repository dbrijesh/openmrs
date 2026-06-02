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

import org.apache.velocity.app.event.MethodExceptionEventHandler;
import org.apache.velocity.context.Context;
import org.apache.velocity.util.introspection.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to safely catch velocity exceptions. Velocity 2.x updated the MethodExceptionEventHandler
 * interface to include additional parameters.
 */
public class VelocityExceptionHandler implements MethodExceptionEventHandler {

	private static final Logger log = LoggerFactory.getLogger(VelocityExceptionHandler.class);

	@Override
	public Object methodException(Context context, Class<?> claz, String method, Exception e, Info info) {
		log.debug("Claz: " + claz.getName() + " method: " + method, e);
		if ("format".equals(method)) {
			return null;
		}
		// Velocity 2.x: can't throw checked exceptions - wrap in RuntimeException
		throw new RuntimeException(e);
	}
}
