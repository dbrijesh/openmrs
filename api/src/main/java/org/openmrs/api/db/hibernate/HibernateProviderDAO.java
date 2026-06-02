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
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.ProviderDAO;
import org.openmrs.util.OpenmrsConstants;

/**
 * Hibernate specific Provider related functions. This class should not be used directly. All calls
 * should go through the {@link org.openmrs.api.ProviderService} methods.
 *
 * @since 1.9
 */
public class HibernateProviderDAO implements ProviderDAO {

	private SessionFactory sessionFactory;

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getAllProviders(boolean)
	 */
	@Override
	public List<Provider> getAllProviders(boolean includeRetired) {
		return getAll(includeRetired, Provider.class);
	}

	private Session getSession() {
		return sessionFactory.getCurrentSession();
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#saveProvider(org.openmrs.Provider)
	 */
	@Override
	public Provider saveProvider(Provider provider) {
		getSession().saveOrUpdate(provider);
		return provider;
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#deleteProvider(org.openmrs.Provider)
	 */
	@Override
	public void deleteProvider(Provider provider) {
		getSession().delete(provider);
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProvider(java.lang.Integer)
	 */
	@Override
	public Provider getProvider(Integer id) {
		return (Provider) getSession().get(Provider.class, id);
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProviderByUuid(java.lang.String)
	 */
	@Override
	public Provider getProviderByUuid(String uuid) {
		return getByUuid(uuid, Provider.class);
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProvidersByPerson(org.openmrs.Person, boolean)
	 */
	@Override
	public Collection<Provider> getProvidersByPerson(Person person, boolean includeRetired) {
		StringBuilder hql = new StringBuilder("from Provider p where p.person = :person");
		if (!includeRetired) {
			hql.append(" and p.retired = false");
		}
		hql.append(" order by p.retired asc, p.providerId asc");
		Query<Provider> query = getSession().createQuery(hql.toString(), Provider.class);
		query.setParameter("person", person);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProviderAttribute(Integer)
	 */
	@Override
	public ProviderAttribute getProviderAttribute(Integer providerAttributeID) {
		return (ProviderAttribute) getSession().get(ProviderAttribute.class, providerAttributeID);
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProviderAttributeByUuid(String)
	 */

	@Override
	public ProviderAttribute getProviderAttributeByUuid(String uuid) {
		return getByUuid(uuid, ProviderAttribute.class);
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProviders(String, Map, Integer, Integer, boolean)
	 */
	@Override
	public List<Provider> getProviders(String name, Map<ProviderAttributeType, String> serializedAttributeValues,
	        Integer start, Integer length, boolean includeRetired) {
		Query<Provider> query = prepareProviderQuery(name, includeRetired);
		if (start != null) {
			query.setFirstResult(start);
		}
		if (length != null) {
			query.setMaxResults(length);
		}

		List<Provider> providers = query.list();
		if (serializedAttributeValues != null) {
			CollectionUtils.filter(providers, new AttributeMatcherPredicate<Provider, ProviderAttributeType>(
			        serializedAttributeValues));
		}
		return providers;
	}

	private String getMatchModePattern(String name) {
		String matchMode = Context.getAdministrationService().getGlobalProperty(
		    OpenmrsConstants.GLOBAL_PROPERTY_PROVIDER_SEARCH_MATCH_MODE);

		if ("START".equalsIgnoreCase(matchMode)) {
			return name + "%";
		}
		if ("ANYWHERE".equalsIgnoreCase(matchMode)) {
			return "%" + name + "%";
		}
		if ("END".equalsIgnoreCase(matchMode)) {
			return "%" + name;
		}
		return name; // EXACT
	}

	/**
	 * Creates a Provider Query based on name
	 *
	 * @param name represents provider name
	 * @param includeRetired
	 * @return Query represents the HQL query to search
	 */
	private Query<Provider> prepareProviderQuery(String name, boolean includeRetired) {
		if (StringUtils.isBlank(name)) {
			name = "%";
		}

		String[] splitNames = name.split(" ");
		String identifierPattern = getMatchModePattern(name);
		String namePattern = "%" + name + "%";

		StringBuilder hql = new StringBuilder(
		    "select distinct p from Provider p left join p.person per left join per.names personName where ");

		if (!includeRetired) {
			hql.append("p.retired = false and ");
		}

		// identifier like ... OR name like ... OR (personName conditions)
		hql.append("(lower(p.identifier) like :identifierPattern or lower(p.name) like :namePattern");

		for (int i = 0; i < splitNames.length; i++) {
			hql.append(" or (personName.voided = false and (");
			hql.append("lower(personName.givenName) like :splitName").append(i);
			hql.append(" or lower(personName.middleName) like :splitName").append(i);
			hql.append(" or lower(personName.familyName) like :splitName").append(i);
			hql.append(" or lower(personName.familyName2) like :splitName").append(i);
			hql.append("))");
		}
		hql.append(")");

		if (includeRetired) {
			hql.append(" order by p.retired asc");
		}

		Query<Provider> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Provider.class);
		query.setParameter("identifierPattern", identifierPattern.toLowerCase());
		query.setParameter("namePattern", namePattern.toLowerCase());
		for (int i = 0; i < splitNames.length; i++) {
			query.setParameter("splitName" + i, "%" + splitNames[i].toLowerCase() + "%");
		}

		return query;
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getCountOfProviders(String, boolean)
	 */
	@Override
	public Long getCountOfProviders(String name, boolean includeRetired) {
		return (long) prepareProviderQuery(name, includeRetired).list().size();
	}

	/* (non-Javadoc)
	 * @see org.openmrs.api.db.ProviderDAO#getAllProviderAttributeTypes(boolean)
	 */
	@Override
	public List<ProviderAttributeType> getAllProviderAttributeTypes(boolean includeRetired) {
		return getAll(includeRetired, ProviderAttributeType.class);
	}

	private <T> List<T> getAll(boolean includeRetired, Class<T> clazz) {
		StringBuilder hql = new StringBuilder("from ").append(clazz.getSimpleName()).append(" e");
		if (!includeRetired) {
			hql.append(" where e.retired = false");
		}
		hql.append(" order by");
		if (includeRetired) {
			hql.append(" e.retired asc,");
		}
		hql.append(" e.name asc");
		return getSession().createQuery(hql.toString(), clazz).list();
	}

	private <T> T getByUuid(String uuid, Class<T> clazz) {
		return getSession().createQuery("from " + clazz.getSimpleName() + " e where e.uuid = :uuid", clazz)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/* (non-Javadoc)
	 * @see org.openmrs.api.db.ProviderDAO#getProviderAttributeType(java.lang.Integer)
	 */
	@Override
	public ProviderAttributeType getProviderAttributeType(Integer providerAttributeTypeId) {
		return (ProviderAttributeType) getSession().get(ProviderAttributeType.class, providerAttributeTypeId);
	}

	/* (non-Javadoc)
	 * @see org.openmrs.api.db.ProviderDAO#getProviderAttributeTypeByUuid(java.lang.String)
	 */
	@Override
	public ProviderAttributeType getProviderAttributeTypeByUuid(String uuid) {
		return getByUuid(uuid, ProviderAttributeType.class);
	}

	/* (non-Javadoc)
	 * @see org.openmrs.api.db.ProviderDAO#saveProviderAttributeType(org.openmrs.ProviderAttributeType)
	 */
	@Override
	public ProviderAttributeType saveProviderAttributeType(ProviderAttributeType providerAttributeType) {
		getSession().saveOrUpdate(providerAttributeType);
		return providerAttributeType;
	}

	/* (non-Javadoc)
	 * @see org.openmrs.api.db.ProviderDAO#deleteProviderAttributeType(org.openmrs.ProviderAttributeType)
	 */
	@Override
	public void deleteProviderAttributeType(ProviderAttributeType providerAttributeType) {
		getSession().delete(providerAttributeType);
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProviderByIdentifier(java.lang.String)
	 */
	@Override
	public boolean isProviderIdentifierUnique(Provider provider) throws DAOException {
		StringBuilder hql = new StringBuilder(
		    "select count(distinct p.providerId) from Provider p where p.identifier = :identifier");
		if (provider.getProviderId() != null) {
			hql.append(" and p.providerId <> :providerId");
		}
		Query<Long> query = getSession().createQuery(hql.toString(), Long.class);
		query.setParameter("identifier", provider.getIdentifier());
		if (provider.getProviderId() != null) {
			query.setParameter("providerId", provider.getProviderId());
		}
		return query.uniqueResult() == 0L;
	}

	/**
	 * @see org.openmrs.api.db.ProviderDAO#getProviderByIdentifier(java.lang.String)
	 */
	@Override
	public Provider getProviderByIdentifier(String identifier) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Provider p where lower(p.identifier) = lower(:identifier)", Provider.class)
		        .setParameter("identifier", identifier).uniqueResult();
	}
}
