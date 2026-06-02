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
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.OnExecutionGenerator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.ServiceRegistry;

/**
 * <b>native-if-not-assigned</b><br>
 * Allows a programmer to override the database-generated identity with an assigned id at save time.
 *
 * <p>Hibernate 6 requires a strict split between {@link BeforeExecutionGenerator} (Java-side value)
 * and {@link OnExecutionGenerator} (database-side identity column). This class implements BOTH and
 * uses the per-entity {@code generatedOnExecution(Object, Session)} override to route:
 * <ul>
 *   <li>entity has no ID → {@code true}  → database generates via identity column (OnExecution path)</li>
 *   <li>entity has an ID already → {@code false} → return that ID before SQL (BeforeExecution path)</li>
 * </ul>
 */
public class NativeIfNotAssignedIdentityGenerator implements BeforeExecutionGenerator, OnExecutionGenerator {

	private static final String ENTITY_NAME_PARAM = "entity_name";

	private String entityName;

	public NativeIfNotAssignedIdentityGenerator() {
	}

	public void configure(Properties params, ServiceRegistry serviceRegistry) throws MappingException {
		this.entityName = params.getProperty(ENTITY_NAME_PARAM);
	}

	// ---- Generator routing ----

	@Override
	public boolean generatedOnExecution() {
		return true; // safe default: let database generate
	}

	@Override
	public boolean generatedOnExecution(Object entity, SharedSessionContractImplementor session) {
		if (entity != null && entityName != null) {
			try {
				EntityPersister persister = session.getEntityPersister(entityName, entity);
				Object id = persister.getIdentifier(entity, session);
				if (id != null) {
					return false; // ID already assigned → BeforeExecution path
				}
			}
			catch (Exception e) {
				// fall through to database identity generation
			}
		}
		return true; // No ID → OnExecution path (database generates)
	}

	@Override
	public EnumSet<EventType> getEventTypes() {
		return EnumSet.of(EventType.INSERT);
	}

	// ---- BeforeExecutionGenerator (used when generatedOnExecution returns false) ----

	@Override
	public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
	        EventType eventType) throws HibernateException {
		if (entityName != null) {
			try {
				EntityPersister persister = session.getEntityPersister(entityName, owner);
				return persister.getIdentifier(owner, session);
			}
			catch (Exception e) {
				// fall through
			}
		}
		return null;
	}

	// ---- OnExecutionGenerator (used when generatedOnExecution returns true) ----

	@Override
	public boolean referenceColumnsInSql(Dialect dialect) {
		return false; // don't put anything in the INSERT column list for the ID
	}

	@Override
	public boolean writePropertyValue() {
		return false; // don't write the property value in the INSERT
	}

	@Override
	public String[] getReferencedColumnValues(Dialect dialect) {
		return new String[0];
	}
}
