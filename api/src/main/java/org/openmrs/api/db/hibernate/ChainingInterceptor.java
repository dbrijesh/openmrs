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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.hibernate.CallbackException;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by the {@link HibernateSessionFactoryBean} to keep track of multiple interceptors.
 * Compatible with Hibernate 6.x (EntityMode removed, id parameters changed to Object).
 */
public class ChainingInterceptor implements Interceptor {

	private static final Logger log = LoggerFactory.getLogger(ChainingInterceptor.class);

	public Collection<Interceptor> interceptors = new LinkedHashSet<>();

	public void addInterceptor(Interceptor interceptor) {
		if (interceptor == this) {
			log.error("Attempting to add self to chain. This would result in epic failures.");
			return;
		}
		if (interceptors == null) {
			interceptors = new LinkedHashSet<>();
		}
		interceptors.add(interceptor);
	}

	@Override
	public void onDelete(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
		for (Interceptor i : interceptors) {
			i.onDelete(entity, id, state, propertyNames, types);
		}
	}

	@Override
	public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
	        String[] propertyNames, Type[] types) {
		boolean objectChanged = false;
		for (Interceptor i : interceptors) {
			objectChanged = i.onFlushDirty(entity, id, currentState, previousState, propertyNames, types) || objectChanged;
		}
		return objectChanged;
	}

	@Override
	public boolean onLoad(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
		boolean objectChanged = false;
		for (Interceptor i : interceptors) {
			objectChanged = i.onLoad(entity, id, state, propertyNames, types) || objectChanged;
		}
		return objectChanged;
	}

	@Override
	public boolean onSave(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
		boolean objectChanged = false;
		for (Interceptor i : interceptors) {
			objectChanged = i.onSave(entity, id, state, propertyNames, types) || objectChanged;
		}
		return objectChanged;
	}

	@Override
	public void postFlush(Iterator<Object> entities) {
		for (Interceptor i : interceptors) {
			i.postFlush(entities);
		}
	}

	@Override
	public void preFlush(Iterator<Object> entities) {
		for (Interceptor i : interceptors) {
			i.preFlush(entities);
		}
	}

	@Override
	public Boolean isTransient(Object entity) {
		Boolean returnValue = null;
		for (Interceptor i : interceptors) {
			Boolean tmpReturnValue = i.isTransient(entity);
			if (tmpReturnValue != null) {
				if (returnValue == null) {
					returnValue = tmpReturnValue;
				} else {
					returnValue = returnValue && tmpReturnValue;
				}
			}
		}
		return returnValue;
	}

	// instantiate() method removed in Hibernate 6 - EntityRepresentationStrategy parameter required but rarely needed

	@Override
	public int[] findDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
	        String[] propertyNames, Type[] types) {
		List<Integer> uniqueIndices = new LinkedList<>();
		for (Interceptor i : interceptors) {
			int[] indices = i.findDirty(entity, id, currentState, previousState, propertyNames, types);
			if (indices != null) {
				for (int index : indices) {
					if (!uniqueIndices.contains(index)) {
						uniqueIndices.add(index);
					}
				}
			}
		}
		if (uniqueIndices.isEmpty()) {
			return null;
		}
		int[] uniquePrimitiveIndices = new int[uniqueIndices.size()];
		for (int x = 0; x < uniqueIndices.size(); x++) {
			uniquePrimitiveIndices[x] = uniqueIndices.get(x);
		}
		return uniquePrimitiveIndices;
	}

	// getEntityName removed from Interceptor interface in Hibernate 6

	@Override
	public Object getEntity(String entityName, Object id) {
		for (Interceptor i : interceptors) {
			Object o = i.getEntity(entityName, id);
			if (o != null) {
				return o;
			}
		}
		return null;
	}

	@Override
	public void afterTransactionBegin(Transaction tx) {
		for (Interceptor i : interceptors) {
			i.afterTransactionBegin(tx);
		}
	}

	@Override
	public void afterTransactionCompletion(Transaction tx) {
		for (Interceptor i : interceptors) {
			i.afterTransactionCompletion(tx);
		}
	}

	@Override
	public void beforeTransactionCompletion(Transaction tx) {
		for (Interceptor i : interceptors) {
			i.beforeTransactionCompletion(tx);
		}
	}

	// onPrepareStatement() removed from Interceptor interface in Hibernate 6

	@Override
	public void onCollectionRemove(Object collection, Object key) throws CallbackException {
		for (Interceptor i : interceptors) {
			i.onCollectionRemove(collection, key);
		}
	}

	@Override
	public void onCollectionRecreate(Object collection, Object key) throws CallbackException {
		for (Interceptor i : interceptors) {
			i.onCollectionRecreate(collection, key);
		}
	}

	@Override
	public void onCollectionUpdate(Object collection, Object key) throws CallbackException {
		for (Interceptor i : interceptors) {
			i.onCollectionUpdate(collection, key);
		}
	}
}
