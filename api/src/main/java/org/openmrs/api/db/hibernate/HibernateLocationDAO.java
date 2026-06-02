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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.LocationTag;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.LocationDAO;

/**
 * Hibernate location-related database functions
 */
public class HibernateLocationDAO implements LocationDAO {

	private SessionFactory sessionFactory;

	/**
	 * @see org.openmrs.api.db.LocationDAO#setSessionFactory(org.hibernate.SessionFactory)
	 */
	@Override
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#saveLocation(org.openmrs.Location)
	 */
	@Override
	public Location saveLocation(Location location) {
		if (location.getChildLocations() != null && location.getLocationId() != null) {
			// hibernate has a problem updating child collections
			// if the parent object was already saved so we do it
			// explicitly here
			for (Location child : location.getChildLocations()) {
				if (child.getLocationId() == null) {
					saveLocation(child);
				}
			}
		}

		sessionFactory.getCurrentSession().saveOrUpdate(location);
		return location;
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocation(java.lang.Integer)
	 */
	@Override
	public Location getLocation(Integer locationId) {
		return sessionFactory.getCurrentSession().get(Location.class, locationId);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocation(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Location getLocation(String name) {
		List<Location> locations = sessionFactory.getCurrentSession()
				.createQuery("FROM Location l WHERE l.name = :name", Location.class)
				.setParameter("name", name)
				.list();
		if (null == locations || locations.isEmpty()) {
			return null;
		}
		return locations.get(0);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getAllLocations(boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Location> getAllLocations(boolean includeRetired) {
		StringBuilder hql = new StringBuilder("FROM Location l");
		if (!includeRetired) {
			hql.append(" WHERE l.retired = false ORDER BY l.name ASC");
		} else {
			//push retired locations to the end of the returned list
			hql.append(" ORDER BY l.retired ASC, l.name ASC");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), Location.class).list();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#deleteLocation(org.openmrs.Location)
	 */
	@Override
	public void deleteLocation(Location location) {
		sessionFactory.getCurrentSession().delete(location);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#saveLocation(org.openmrs.Location)
	 */
	@Override
	public LocationTag saveLocationTag(LocationTag tag) {
		sessionFactory.getCurrentSession().saveOrUpdate(tag);
		return tag;
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationTag(java.lang.Integer)
	 */
	@Override
	public LocationTag getLocationTag(Integer locationTagId) {
		return sessionFactory.getCurrentSession().get(LocationTag.class, locationTagId);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationTagByName(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public LocationTag getLocationTagByName(String tag) {
		List<LocationTag> tags = sessionFactory.getCurrentSession()
				.createQuery("FROM LocationTag lt WHERE lt.name = :name", LocationTag.class)
				.setParameter("name", tag)
				.list();
		if (null == tags || tags.isEmpty()) {
			return null;
		}
		return tags.get(0);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getAllLocationTags(boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<LocationTag> getAllLocationTags(boolean includeRetired) {
		StringBuilder hql = new StringBuilder("FROM LocationTag lt");
		if (!includeRetired) {
			hql.append(" WHERE lt.retired = false");
		}
		hql.append(" ORDER BY lt.name ASC");
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), LocationTag.class).list();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationTags(String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<LocationTag> getLocationTags(String search) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM LocationTag lt WHERE lower(lt.name) LIKE :search ORDER BY lt.name ASC",
						LocationTag.class)
				.setParameter("search", search.toLowerCase() + "%")
				.list();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#deleteLocationTag(org.openmrs.LocationTag)
	 */
	@Override
	public void deleteLocationTag(LocationTag tag) {
		sessionFactory.getCurrentSession().delete(tag);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationByUuid(java.lang.String)
	 */
	@Override
	public Location getLocationByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Location l WHERE l.uuid = :uuid", Location.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationTagByUuid(java.lang.String)
	 */
	@Override
	public LocationTag getLocationTagByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM LocationTag lt WHERE lt.uuid = :uuid", LocationTag.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getCountOfLocations(String, Boolean)
	 */
	@Override
	public Long getCountOfLocations(String nameFragment, Boolean includeRetired) {
		StringBuilder hql = new StringBuilder("SELECT COUNT(l) FROM Location l WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND l.retired = false");
		}
		if (StringUtils.isNotBlank(nameFragment)) {
			hql.append(" AND lower(l.name) LIKE :nameFragment");
		}

		Query<Long> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
		if (StringUtils.isNotBlank(nameFragment)) {
			query.setParameter("nameFragment", nameFragment.toLowerCase() + "%");
		}
		return query.uniqueResult();
	}

	/**
	 * @see LocationDAO#getLocations(String, org.openmrs.Location, java.util.Map, boolean, Integer, Integer)
	 */
	@Override
	public List<Location> getLocations(String nameFragment, Location parent,
	        Map<LocationAttributeType, String> serializedAttributeValues, boolean includeRetired, Integer start,
	        Integer length) {

		StringBuilder hql = new StringBuilder("FROM Location l WHERE 1=1");

		if (StringUtils.isNotBlank(nameFragment)) {
			hql.append(" AND lower(l.name) LIKE :nameFragment");
		}
		if (parent != null) {
			hql.append(" AND l.parentLocation = :parent");
		}
		if (!includeRetired) {
			hql.append(" AND l.retired = false");
		}
		hql.append(" ORDER BY l.name ASC");

		Query<Location> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Location.class);

		if (StringUtils.isNotBlank(nameFragment)) {
			query.setParameter("nameFragment", nameFragment.toLowerCase() + "%");
		}
		if (parent != null) {
			query.setParameter("parent", parent);
		}
		if (start != null) {
			query.setFirstResult(start);
		}
		if (length != null && length > 0) {
			query.setMaxResults(length);
		}

		List<Location> locations = query.list();

		if (serializedAttributeValues != null) {
			CollectionUtils.filter(locations, new AttributeMatcherPredicate<Location, LocationAttributeType>(
			        serializedAttributeValues));
		}

		return locations;
	}

	/**
	 * @see LocationDAO#getRootLocations(boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Location> getRootLocations(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM Location l WHERE l.parentLocation IS NULL");
		if (!includeRetired) {
			hql.append(" AND l.retired = false");
		}
		hql.append(" ORDER BY l.name ASC");
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), Location.class).list();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getAllLocationAttributeTypes()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<LocationAttributeType> getAllLocationAttributeTypes() {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM LocationAttributeType", LocationAttributeType.class)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationAttributeType(java.lang.Integer)
	 */
	@Override
	public LocationAttributeType getLocationAttributeType(Integer id) {
		return sessionFactory.getCurrentSession().get(LocationAttributeType.class, id);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationAttributeTypeByUuid(java.lang.String)
	 */
	@Override
	public LocationAttributeType getLocationAttributeTypeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM LocationAttributeType t WHERE t.uuid = :uuid", LocationAttributeType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#saveLocationAttributeType(org.openmrs.LocationAttributeType)
	 */
	@Override
	public LocationAttributeType saveLocationAttributeType(LocationAttributeType locationAttributeType) {
		sessionFactory.getCurrentSession().saveOrUpdate(locationAttributeType);
		return locationAttributeType;
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#deleteLocationAttributeType(org.openmrs.LocationAttributeType)
	 */
	@Override
	public void deleteLocationAttributeType(LocationAttributeType locationAttributeType) {
		sessionFactory.getCurrentSession().delete(locationAttributeType);
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationAttributeByUuid(java.lang.String)
	 */
	@Override
	public LocationAttribute getLocationAttributeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM LocationAttribute a WHERE a.uuid = :uuid", LocationAttribute.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationAttributeTypeByName(java.lang.String)
	 */
	@Override
	public LocationAttributeType getLocationAttributeTypeByName(String name) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM LocationAttributeType t WHERE t.name = :name", LocationAttributeType.class)
				.setParameter("name", name)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.LocationDAO#getLocationsHavingAllTags(java.util.List)
	 */
	@Override
	public List<Location> getLocationsHavingAllTags(List<LocationTag> tags) {
		tags.removeAll(Collections.singleton(null));

		List<Integer> tagIds = getLocationTagIds(tags);

		// Use a GROUP BY / HAVING subquery: find location IDs that have all requested tags
		String hql = "SELECT l FROM Location l WHERE l.retired = false"
				+ " AND l.locationId IN ("
				+ "   SELECT loc.locationId FROM Location loc"
				+ "   JOIN loc.tags tag"
				+ "   WHERE tag.locationTagId IN :tagIds"
				+ "   GROUP BY loc.locationId"
				+ "   HAVING COUNT(DISTINCT tag.locationTagId) = :tagCount"
				+ " )";

		return sessionFactory.getCurrentSession()
				.createQuery(hql, Location.class)
				.setParameterList("tagIds", tagIds)
				.setParameter("tagCount", (long) tags.size())
				.list();
	}

	/**
	 * Extract locationTagIds from the list of LocationTag objects provided.
	 *
	 * @param tags
	 * @return
	 */
	private List<Integer> getLocationTagIds(List<LocationTag> tags) {
		List<Integer> locationTagIds = new ArrayList<>();
		for (LocationTag tag : tags) {
			locationTagIds.add(tag.getLocationTagId());
		}
		return locationTagIds;
	}
}
