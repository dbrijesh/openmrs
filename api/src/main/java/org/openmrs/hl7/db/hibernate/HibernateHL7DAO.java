/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.hl7.db.hibernate;

import java.util.Calendar;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.hl7.HL7Constants;
import org.openmrs.hl7.HL7InArchive;
import org.openmrs.hl7.HL7InError;
import org.openmrs.hl7.HL7InQueue;
import org.openmrs.hl7.HL7Source;
import org.openmrs.hl7.Hl7InArchivesMigrateThread;
import org.openmrs.hl7.db.HL7DAO;

public class HibernateHL7DAO implements HL7DAO {

	private SessionFactory sessionFactory;

	public HibernateHL7DAO() {
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public HL7Source saveHL7Source(HL7Source hl7Source) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(hl7Source);
		return hl7Source;
	}

	@Override
	public HL7Source getHL7Source(Integer hl7SourceId) throws DAOException {
		return sessionFactory.getCurrentSession().get(HL7Source.class, hl7SourceId);
	}

	@Override
	public HL7Source getHL7SourceByName(String name) throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7Source WHERE name = :name", HL7Source.class)
			.setParameter("name", name)
			.uniqueResult();
	}

	@Override
	public List<HL7Source> getAllHL7Sources() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("FROM HL7Source", HL7Source.class).list();
	}

	@Override
	public void deleteHL7Source(HL7Source hl7Source) throws DAOException {
		sessionFactory.getCurrentSession().delete(hl7Source);
	}

	@Override
	public HL7InQueue saveHL7InQueue(HL7InQueue hl7InQueue) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(hl7InQueue);
		return hl7InQueue;
	}

	@Override
	public HL7InQueue getHL7InQueue(Integer hl7InQueueId) throws DAOException {
		return sessionFactory.getCurrentSession().get(HL7InQueue.class, hl7InQueueId);
	}

	@Override
	public HL7InQueue getHL7InQueueByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InQueue WHERE uuid = :uuid", HL7InQueue.class)
			.setParameter("uuid", uuid)
			.uniqueResult();
	}

	@Override
	public List<HL7InQueue> getAllHL7InQueues() throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InQueue WHERE messageState = :state ORDER BY HL7InQueueId", HL7InQueue.class)
			.setParameter("state", HL7Constants.HL7_STATUS_PENDING)
			.list();
	}

	@SuppressWarnings("unchecked")
	private <T> Query<T> buildHL7SearchQuery(Class clazz, Integer messageState, String searchQuery) {
		if (clazz == null) {
			throw new DAOException("no class defined for HL7 search");
		}
		StringBuilder hql = new StringBuilder("FROM ").append(clazz.getSimpleName()).append(" h WHERE 1=1");
		if (searchQuery != null && !searchQuery.isEmpty()) {
			String likeVal = "%" + searchQuery + "%";
			if (clazz == HL7InError.class) {
				hql.append(" AND (lower(h.HL7Data) like :sq OR lower(h.errorDetails) like :sq OR lower(h.error) like :sq)");
			} else {
				hql.append(" AND lower(h.HL7Data) like :sq");
			}
		}
		if (messageState != null) {
			hql.append(" AND h.messageState = :state");
		}
		Query<T> query = (Query<T>) sessionFactory.getCurrentSession().createQuery(hql.toString(), clazz);
		if (searchQuery != null && !searchQuery.isEmpty()) {
			query.setParameter("sq", "%" + searchQuery.toLowerCase() + "%");
		}
		if (messageState != null) {
			query.setParameter("state", messageState);
		}
		return query;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> getHL7Batch(Class clazz, int start, int length, Integer messageState, String query) throws DAOException {
		Query<T> q = (Query<T>) buildHL7SearchQuery(clazz, messageState, query);
		q.setFirstResult(start);
		q.setMaxResults(length);
		return q.list();
	}

	@Override
	public Long countHL7s(Class clazz, Integer messageState, String query) {
		StringBuilder hql = new StringBuilder("SELECT COUNT(h) FROM ").append(clazz.getSimpleName()).append(" h WHERE 1=1");
		if (query != null && !query.isEmpty()) {
			if (clazz == HL7InError.class) {
				hql.append(" AND (lower(h.HL7Data) like :sq OR lower(h.errorDetails) like :sq OR lower(h.error) like :sq)");
			} else {
				hql.append(" AND lower(h.HL7Data) like :sq");
			}
		}
		if (messageState != null) {
			hql.append(" AND h.messageState = :state");
		}
		Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
		if (query != null && !query.isEmpty()) {
			q.setParameter("sq", "%" + query.toLowerCase() + "%");
		}
		if (messageState != null) {
			q.setParameter("state", messageState);
		}
		return q.uniqueResult();
	}

	@Override
	public HL7InQueue getNextHL7InQueue() throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InQueue WHERE messageState = :state ORDER BY HL7InQueueId", HL7InQueue.class)
			.setParameter("state", HL7Constants.HL7_STATUS_PENDING)
			.setMaxResults(1)
			.uniqueResult();
	}

	@Override
	public void deleteHL7InQueue(HL7InQueue hl7InQueue) throws DAOException {
		sessionFactory.getCurrentSession().delete(hl7InQueue);
	}

	@Override
	public HL7InArchive saveHL7InArchive(HL7InArchive hl7InArchive) throws DAOException {
		sessionFactory.getCurrentSession().save(hl7InArchive);
		return hl7InArchive;
	}

	@Override
	public HL7InArchive getHL7InArchive(Integer hl7InArchiveId) throws DAOException {
		return sessionFactory.getCurrentSession().get(HL7InArchive.class, hl7InArchiveId);
	}

	@Override
	public List<HL7InArchive> getHL7InArchiveByState(Integer state) throws DAOException {
		return getHL7InArchiveByState(state, null);
	}

	private List<HL7InArchive> getHL7InArchiveByState(Integer state, Integer maxResults) throws DAOException {
		Query<HL7InArchive> q = sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InArchive WHERE messageState = :state", HL7InArchive.class)
			.setParameter("state", state);
		if (maxResults != null) {
			q.setMaxResults(maxResults);
		}
		return q.list();
	}

	@Override
	public List<HL7InQueue> getHL7InQueueByState(Integer state) throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InQueue WHERE messageState = :state", HL7InQueue.class)
			.setParameter("state", state)
			.list();
	}

	@Override
	public List<HL7InArchive> getAllHL7InArchives() throws DAOException {
		return getAllHL7InArchives(null);
	}

	@Override
	public List<HL7InArchive> getAllHL7InArchives(Integer maxResults) {
		Query<HL7InArchive> q = sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InArchive ORDER BY HL7InArchiveId", HL7InArchive.class);
		if (maxResults != null) {
			q.setMaxResults(maxResults);
		}
		return q.list();
	}

	@Override
	public void deleteHL7InArchive(HL7InArchive hl7InArchive) throws DAOException {
		sessionFactory.getCurrentSession().delete(hl7InArchive);
	}

	@Override
	public HL7InError saveHL7InError(HL7InError hl7InError) throws DAOException {
		sessionFactory.getCurrentSession().save(hl7InError);
		return hl7InError;
	}

	@Override
	public HL7InError getHL7InError(Integer hl7InErrorId) throws DAOException {
		return sessionFactory.getCurrentSession().get(HL7InError.class, hl7InErrorId);
	}

	@Override
	public HL7InError getHL7InErrorByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InError WHERE uuid = :uuid", HL7InError.class)
			.setParameter("uuid", uuid)
			.uniqueResult();
	}

	@Override
	public List<HL7InError> getAllHL7InErrors() throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InError ORDER BY HL7InErrorId", HL7InError.class)
			.list();
	}

	@Override
	public void deleteHL7InError(HL7InError hl7InError) throws DAOException {
		sessionFactory.getCurrentSession().delete(hl7InError);
	}

	@Override
	public void garbageCollect() {
		Context.clearSession();
	}

	@Override
	public HL7InArchive getHL7InArchiveByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
			.createQuery("FROM HL7InArchive WHERE uuid = :uuid", HL7InArchive.class)
			.setParameter("uuid", uuid)
			.uniqueResult();
	}

	@Override
	public List<HL7InArchive> getHL7InArchivesToMigrate() {
		Integer daysToKeep = Hl7InArchivesMigrateThread.getDaysKept();
		StringBuilder hql = new StringBuilder("FROM HL7InArchive h WHERE h.messageState = :state");
		if (daysToKeep != null) {
			hql.append(" AND h.dateCreated < :cutoff");
		}
		Query<HL7InArchive> q = sessionFactory.getCurrentSession()
			.createQuery(hql.toString(), HL7InArchive.class)
			.setParameter("state", HL7Constants.HL7_STATUS_PROCESSED)
			.setMaxResults(HL7Constants.MIGRATION_MAX_BATCH_SIZE);
		if (daysToKeep != null) {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, -1 * daysToKeep);
			q.setParameter("cutoff", cal.getTime());
		}
		return q.list();
	}
}
