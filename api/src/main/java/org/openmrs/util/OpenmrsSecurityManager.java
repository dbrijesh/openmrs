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

import java.lang.StackWalker.StackFrame;
import java.util.Optional;

import org.openmrs.api.APIException;

/**
 * Helper class for call-stack inspection, replacing the removed SecurityManager API (JEP 486).
 * Uses Java 9+ StackWalker API to inspect the call stack.
 */
public class OpenmrsSecurityManager {

	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/**
	 * Returns the class on the current execution stack at the given depth.
	 * 0 is the most recently called class (excluding this method itself).
	 *
	 * @param callStackDepth depth into the call stack (0 = caller of getCallerClass)
	 * @return the class at the given depth
	 * @throws APIException if given a callStackDepth less than zero
	 */
	public Class<?> getCallerClass(int callStackDepth) {
		if (callStackDepth < 0) {
			throw new APIException("call.stack.depth.error", (Object[]) null);
		}

		// +1 to skip getCallerClass itself, +1 to skip the lambda/stream frame
		final int targetDepth = callStackDepth + 2;

		Optional<StackFrame> frame = STACK_WALKER.walk(frames ->
			frames.skip(targetDepth).findFirst()
		);

		if (frame.isPresent()) {
			return frame.get().getDeclaringClass();
		}

		throw new APIException("Call stack depth " + callStackDepth + " is out of range");
	}

}
