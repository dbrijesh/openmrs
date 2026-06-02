/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate;

import java.util.EnumSet;
import java.util.Properties;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.ServiceRegistry;

/**
 * <b>native-if-not-assigned</b><br>
 * Allows a programmer to override the database-generated identity with an assigned id at save time.
 * Compatible with Hibernate 6.x (uses BeforeExecutionGenerator API).
 */
public class NativeIfNotAssignedIdentityGenerator implements BeforeExecutionGenerator {

	private static final String ENTITY_NAME_PARAM = "entity_name";

	private String entityName;

	public NativeIfNotAssignedIdentityGenerator() {
	}

	public void configure(Properties params, ServiceRegistry serviceRegistry) throws MappingException {
		this.entityName = params.getProperty(ENTITY_NAME_PARAM);
	}

	@Override
	public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
	        EventType eventType) throws HibernateException {
		if (entityName != null) {
			try {
				EntityPersister persister = session.getEntityPersister(entityName, owner);
				Object id = persister.getIdentifier(owner, session);
				if (id != null) {
					return id;
				}
			} catch (Exception e) {
				// fall through to database-generated identity
			}
		}
		// Return null to let the database generate the ID via identity column
		return null;
	}

	@Override
	public EnumSet<EventType> getEventTypes() {
		return EnumSet.of(EventType.INSERT);
	}

	@Override
	public boolean generatedOnExecution() {
		// Return true for identity generators (database generates on INSERT)
		return true;
	}
}
