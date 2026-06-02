/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db;

import org.springframework.context.ApplicationEvent;

/**
 * Represents an event object raised whenever a search session is used to create a query. Events are
 * fired via the Spring application event mechanism; listeners have to implement
 * {@link org.springframework.context.ApplicationListener} with the type parameter set to
 * FullTextQueryCreatedEvent. Listeners MUST be registered as Spring beans in order to be
 * discovered.
 *
 * @see FullTextQueryAndEntityClass
 * @since 2.3.0
 */
public class FullTextQueryCreatedEvent extends ApplicationEvent {

	/**
	 * @see ApplicationEvent#ApplicationEvent(java.lang.Object)
	 */
	public FullTextQueryCreatedEvent(FullTextQueryAndEntityClass queryAndClass) {
		super(queryAndClass);
	}

}
