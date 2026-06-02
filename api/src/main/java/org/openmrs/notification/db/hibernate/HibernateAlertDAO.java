/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.notification.db.hibernate;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.User;
import org.openmrs.api.db.DAOException;
import org.openmrs.notification.Alert;
import org.openmrs.notification.db.AlertDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateAlertDAO implements AlertDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateAlertDAO.class);

	private SessionFactory sessionFactory;

	public HibernateAlertDAO() {
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public Alert saveAlert(Alert alert) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(alert);
		return alert;
	}

	@Override
	public Alert getAlert(Integer alertId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Alert.class, alertId);
	}

	@Override
	public void deleteAlert(Alert alert) throws DAOException {
		sessionFactory.getCurrentSession().delete(alert);
	}

	@Override
	public List<Alert> getAllAlerts(boolean includeExpired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM Alert a WHERE 1=1");
		if (!includeExpired) {
			hql.append(" AND (a.dateToExpire IS NULL OR a.dateToExpire > :now)");
		}
		Query<Alert> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Alert.class);
		if (!includeExpired) {
			query.setParameter("now", new Date());
		}
		return query.list();
	}

	@Override
	public List<Alert> getAlerts(User user, boolean includeRead, boolean includeExpired) throws DAOException {
		log.debug("Getting alerts for user " + user + " read? " + includeRead + " expired? " + includeExpired);

		if (user == null || user.getUserId() == null) {
			return Collections.emptyList();
		}

		StringBuilder hql = new StringBuilder(
			"SELECT DISTINCT a FROM Alert a JOIN a.recipients r WHERE r.recipient = :user");

		if (!includeExpired) {
			hql.append(" AND (a.dateToExpire IS NULL OR a.dateToExpire > :now)");
		}
		if (!includeRead) {
			hql.append(" AND a.alertRead = false AND r.alertRead = false");
		}
		hql.append(" ORDER BY a.dateChanged DESC");

		Query<Alert> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Alert.class);
		query.setParameter("user", user);
		if (!includeExpired) {
			query.setParameter("now", new Date());
		}
		return query.list();
	}
}
