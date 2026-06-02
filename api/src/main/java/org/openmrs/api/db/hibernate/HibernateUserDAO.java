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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Person;
import org.openmrs.Privilege;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.LoginCredential;
import org.openmrs.api.db.UserDAO;
import org.openmrs.patient.impl.LuhnIdentifierValidator;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.Security;
import org.openmrs.util.UserByNameComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate specific database methods for the UserService
 *
 * @see org.openmrs.api.context.Context
 * @see org.openmrs.api.db.UserDAO
 * @see org.openmrs.api.UserService
 */
public class HibernateUserDAO implements UserDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateUserDAO.class);

	/**
	 * Hibernate session factory
	 */
	private SessionFactory sessionFactory;

	/**
	 * Set session factory
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.UserService#saveUser(org.openmrs.User)
	 */
	@Override
	public User saveUser(User user, String password) {

		// only change the user's password when creating a new user
		boolean isNewUser = user.getUserId() == null;

		sessionFactory.getCurrentSession().saveOrUpdate(user);

		if (isNewUser && password != null) {
			/* In OpenMRS, we are using generation strategy as native which will convert to IDENTITY
			 for MySQL and SEQUENCE for PostgreSQL. When using IDENTITY strategy, hibernate directly
			 issues insert statements where as with  SEQUENCE strategy hibernate only increments
			 sequences and issues insert on session flush ( batching is possible) .
			 PostgreSQL behaves differently than MySQL because it makes use of SEQUENCE strategy.
			*/
			sessionFactory.getCurrentSession().flush();

			//update the new user with the password
			String salt = Security.getRandomToken();
			String hashedPassword = Security.encodeString(password + salt);

			updateUserPassword(hashedPassword, salt, Context.getAuthenticatedUser().getUserId(), new Date(), user
			        .getUserId());
		}

		return user;
	}

	/**
	 * @see org.openmrs.api.UserService#getUserByUsername(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public User getUserByUsername(String username) {
		Query<User> query = sessionFactory.getCurrentSession().createQuery(
		    "from User u where u.retired = false and (u.username = :username or u.systemId = :systemId)", User.class);
		query.setParameter("username", username);
		query.setParameter("systemId", username);
		List<User> users = query.list();

		if (users == null || users.isEmpty()) {
			log.warn("request for username '" + username + "' not found");
			return null;
		}

		return users.get(0);
	}

	@Override
	@SuppressWarnings("unchecked")
	public User getUserByEmail(String email) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from User u where lower(u.email) = lower(:email)", User.class)
		        .setParameter("email", email).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getLoginCredentialByActivationKey(java.lang.String)
	 */
	@Override
	public LoginCredential getLoginCredentialByActivationKey(String activationKey) {
		String key = Security.encodeString(activationKey);
		LoginCredential loginCred = sessionFactory.getCurrentSession()
		        .createQuery("from LoginCredential lc where lc.activationKey like :keyPattern", LoginCredential.class)
		        .setParameter("keyPattern", key + "%").uniqueResult();
		if (loginCred != null) {
			String[] credTokens = loginCred.getActivationKey().split(":");
			if (credTokens[0].equals(key)) {
				return loginCred;
			}
		}
		return null;
	}

	/**
	 * @see org.openmrs.api.UserService#hasDuplicateUsername(org.openmrs.User)
	 */
	@Override
	public boolean hasDuplicateUsername(String username, String systemId, Integer userId) {
		if (username == null || username.length() == 0) {
			username = "-";
		}
		if (systemId == null || systemId.length() == 0) {
			systemId = "-";
		}

		if (userId == null) {
			userId = -1;
		}

		String usernameWithCheckDigit = username;
		try {
			//Hardcoding in Luhn since past user IDs used this validator.
			usernameWithCheckDigit = new LuhnIdentifierValidator().getValidIdentifier(username);
		}
		catch (Exception e) {}

		Query<Long> query = sessionFactory
		        .getCurrentSession()
		        .createQuery(
		            "select count(*) from User u where (u.username = :uname1 or u.systemId = :uname2 or u.username = :sysid1 or u.systemId = :sysid2 or u.systemId = :uname3) and u.userId <> :uid",
		            Long.class);
		query.setParameter("uname1", username);
		query.setParameter("uname2", username);
		query.setParameter("sysid1", systemId);
		query.setParameter("sysid2", systemId);
		query.setParameter("uname3", usernameWithCheckDigit);
		query.setParameter("uid", userId);

		Long count = query.uniqueResult();

		log.debug("# users found: " + count);
		return (count != null && count != 0);
	}

	/**
	 * @see org.openmrs.api.UserService#getUser(java.lang.Integer)
	 */
	@Override
	public User getUser(Integer userId) {
		return (User) sessionFactory.getCurrentSession().get(User.class, userId);
	}

	/**
	 * @see org.openmrs.api.UserService#getAllUsers()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<User> getAllUsers() throws DAOException {
		return sessionFactory.getCurrentSession()
		        .createQuery("from User where not uuid = :daemonUserUuid order by userId", User.class)
		        .setParameter("daemonUserUuid", Daemon.getDaemonUserUuid()).list();

	}

	/**
	 * @see org.openmrs.api.UserService#purgeUser(org.openmrs.User)
	 */
	@Override
	public void deleteUser(User user) {
		sessionFactory.getCurrentSession().delete(user);
	}

	/**
	 * @see org.openmrs.api.UserService#getUsersByRole(org.openmrs.Role)
	 */
	@SuppressWarnings("unchecked")
	public List<User> getUsersByRole(Role role) throws DAOException {
		return sessionFactory.getCurrentSession()
		        .createQuery(
		            "select distinct u from User u inner join u.roles r where r.role = :role and u.uuid <> :daemonUuid order by u.username asc",
		            User.class)
		        .setParameter("role", role.getRole())
		        .setParameter("daemonUuid", Daemon.getDaemonUserUuid())
		        .list();
	}

	/**
	 * @see org.openmrs.api.UserService#getAllPrivileges()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Privilege> getAllPrivileges() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("from Privilege p order by p.privilege", Privilege.class)
		        .list();
	}

	/**
	 * @see org.openmrs.api.UserService#getPrivilege(String)
	 */
	@Override
	public Privilege getPrivilege(String p) throws DAOException {
		return (Privilege) sessionFactory.getCurrentSession().get(Privilege.class, p);
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#deletePrivilege(org.openmrs.Privilege)
	 */
	@Override
	public void deletePrivilege(Privilege privilege) throws DAOException {
		sessionFactory.getCurrentSession().delete(privilege);
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#savePrivilege(org.openmrs.Privilege)
	 */
	@Override
	public Privilege savePrivilege(Privilege privilege) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(privilege);
		return privilege;
	}

	/**
	 * @see org.openmrs.api.UserService#purgeRole(org.openmrs.Role)
	 */
	@Override
	public void deleteRole(Role role) throws DAOException {
		sessionFactory.getCurrentSession().delete(role);
	}

	/**
	 * @see org.openmrs.api.UserService#saveRole(org.openmrs.Role)
	 */
	@Override
	public Role saveRole(Role role) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(role);
		return role;
	}

	/**
	 * @see org.openmrs.api.UserService#getAllRoles()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Role> getAllRoles() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("from Role r order by r.role", Role.class).list();
	}

	/**
	 * @see org.openmrs.api.UserService#getRole(String)
	 */
	@Override
	public Role getRole(String r) throws DAOException {
		return (Role) sessionFactory.getCurrentSession().get(Role.class, r);
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#changePassword(org.openmrs.User, java.lang.String)
	 */
	@Override
	public void changePassword(User u, String pw) throws DAOException {
		User authUser = Context.getAuthenticatedUser();

		if (authUser == null) {
			authUser = u;
		}

		log.debug("updating password");
		String salt = getLoginCredential(u).getSalt();
		if (StringUtils.isBlank(salt)) {
			salt = Security.getRandomToken();
		}
		String newHashedPassword = Security.encodeString(pw + salt);

		updateUserPassword(newHashedPassword, salt, authUser.getUserId(), new Date(), u.getUserId());

	}

	/**
	 * @see org.openmrs.api.db.UserDAO#changeHashedPassword(User, String, String)
	 */
	@Override
	public void changeHashedPassword(User user, String hashedPassword, String salt) throws DAOException {
		User authUser = Context.getAuthenticatedUser();
		updateUserPassword(hashedPassword, salt, authUser.getUserId(), new Date(), user.getUserId());
	}

	/**
	 * @param newHashedPassword
	 * @param salt
	 * @param userId
	 * @param date
	 * @param userId2
	 */
	private void updateUserPassword(String newHashedPassword, String salt, Integer changedBy, Date dateChanged,
	        Integer userIdToChange) {
		User changeForUser = getUser(userIdToChange);
		if (changeForUser == null) {
			throw new DAOException("Couldn't find user to set password for userId=" + userIdToChange);
		}
		User changedByUser = getUser(changedBy);
		LoginCredential credentials = getLoginCredential(changeForUser);
		credentials.setUserId(userIdToChange);
		credentials.setHashedPassword(newHashedPassword);
		credentials.setSalt(salt);
		credentials.setChangedBy(changedByUser);
		credentials.setDateChanged(dateChanged);
		credentials.setUuid(changeForUser.getUuid());

		sessionFactory.getCurrentSession().merge(credentials);

		// reset lockout
		changeForUser.setUserProperty(OpenmrsConstants.USER_PROPERTY_LOCKOUT_TIMESTAMP, "");
		changeForUser.setUserProperty(OpenmrsConstants.USER_PROPERTY_LOGIN_ATTEMPTS, "0");
		saveUser(changeForUser, null);
	}

	/**
	 * @see org.openmrs.api.UserService#changePassword(java.lang.String, java.lang.String)
	 */
	@Override
	public void changePassword(String oldPassword, String newPassword) throws DAOException {
		User u = Context.getAuthenticatedUser();
		LoginCredential credentials = getLoginCredential(u);
		if (!credentials.checkPassword(oldPassword)) {
			log.error("Passwords don't match");
			throw new DAOException("Passwords don't match");
		}

		log.info("updating password for {}", u.getUsername());

		// update the user with the new password
		String salt = credentials.getSalt();
		String newHashedPassword = Security.encodeString(newPassword + salt);
		updateUserPassword(newHashedPassword, salt, u.getUserId(), new Date(), u.getUserId());
	}

	/**
	 * @see org.openmrs.api.UserService#changeQuestionAnswer(java.lang.String, java.lang.String,
	 *      java.lang.String)
	 */
	@Override
	public void changeQuestionAnswer(String pw, String question, String answer) throws DAOException {
		User u = Context.getAuthenticatedUser();

		LoginCredential credentials = getLoginCredential(u);
		if (!credentials.checkPassword(pw)) {
			log.error("Passwords don't match");
			throw new DAOException("Passwords don't match");
		}

		changeQuestionAnswer(u, question, answer);
	}

	/**
	 * @see org.openmrs.api.UserService#changeQuestionAnswer(User, String, String)
	 */
	@Override
	public void changeQuestionAnswer(User u, String question, String answer) throws DAOException {
		log.info("Updating secret question and answer for " + u.getUsername());

		LoginCredential credentials = getLoginCredential(u);
		credentials.setSecretQuestion(question);
		String hashedAnswer = Security.encodeString(answer.toLowerCase() + credentials.getSalt());
		credentials.setSecretAnswer(hashedAnswer);
		credentials.setDateChanged(new Date());
		credentials.setChangedBy(u);

		updateLoginCredential(credentials);
	}

	/**
	 * @see org.openmrs.api.UserService#isSecretAnswer(User, java.lang.String)
	 */
	@Override
	public boolean isSecretAnswer(User u, String answer) throws DAOException {

		if (StringUtils.isEmpty(answer)) {
			return false;
		}

		LoginCredential credentials = getLoginCredential(u);
		String answerOnRecord = credentials.getSecretAnswer();
		String hashedAnswer = Security.encodeString(answer.toLowerCase() + credentials.getSalt());
		return (hashedAnswer.equals(answerOnRecord));
	}

	/**
	 * @see UserDAO#getUsers(String, List, boolean, Integer, Integer)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<User> getUsers(String name, List<Role> roles, boolean includeRetired, Integer start, Integer length) {

		String hqlSelectStart = "select distinct user from User as user inner join user.person.names as name ";
		Query<User> query = createUserSearchQuery(name, roles, includeRetired, hqlSelectStart, User.class);

		if (start != null) {
			query.setFirstResult(start);
		}
		if (length != null && length > 0) {
			query.setMaxResults(length);
		}

		List<User> returnList = query.list();

		if (!CollectionUtils.isEmpty(returnList)) {
			returnList.sort(new UserByNameComparator());
		}

		return returnList;
	}

	/**
	 * @see org.openmrs.api.UserService#generateSystemId()
	 */
	@Override
	public Integer generateSystemId() {

		String hql = "select max(userId) from User";

		Query<Number> query = sessionFactory.getCurrentSession().createQuery(hql, Number.class);

		Number object = query.uniqueResult();

		Integer id;
		if (object instanceof Number) {
			id = object.intValue() + 1;
		} else {
			log.warn("What is being returned here? Definitely nothing expected object value: '" + object + "'");
			id = 1;
		}

		return id;
	}

	/**
	 * @see org.openmrs.api.UserService#getUsersByName(java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public List<User> getUsersByName(String givenName, String familyName, boolean includeRetired) {
		StringBuilder hql = new StringBuilder(
		    "select distinct u from User u inner join u.person.names names where names.givenName = :givenName and names.familyName = :familyName and u.uuid <> :daemonUuid");
		if (!includeRetired) {
			hql.append(" and u.retired = false");
		}
		Query<User> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), User.class);
		query.setParameter("givenName", givenName);
		query.setParameter("familyName", familyName);
		query.setParameter("daemonUuid", Daemon.getDaemonUserUuid());
		return new ArrayList<>(query.list());
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getPrivilegeByUuid(java.lang.String)
	 */
	@Override
	public Privilege getPrivilegeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Privilege p where p.uuid = :uuid", Privilege.class).setParameter("uuid", uuid)
		        .uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getRoleByUuid(java.lang.String)
	 */
	@Override
	public Role getRoleByUuid(String uuid) {
		return sessionFactory.getCurrentSession().createQuery("from Role r where r.uuid = :uuid", Role.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getUserByUuid(java.lang.String)
	 */
	@Override
	public User getUserByUuid(String uuid) {
		User ret = null;

		if (uuid != null) {
			uuid = uuid.trim();
			ret = sessionFactory.getCurrentSession().createQuery("from User u where u.uuid = :uuid", User.class)
			        .setParameter("uuid", uuid).uniqueResult();
		}

		return ret;
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getLoginCredential(org.openmrs.User)
	 */
	@Override
	public LoginCredential getLoginCredential(User user) {
		return (LoginCredential) sessionFactory.getCurrentSession().get(LoginCredential.class, user.getUserId());
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getLoginCredential(org.openmrs.User)
	 */
	@Override
	public LoginCredential getLoginCredentialByUuid(String uuid) {
		if (uuid == null) {
			return null;
		} else {
			return sessionFactory.getCurrentSession()
			        .createQuery("from LoginCredential where uuid = :uuid", LoginCredential.class)
			        .setParameter("uuid", uuid.trim()).uniqueResult();
		}
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#updateLoginCredential(LoginCredential)
	 */
	@Override
	public void updateLoginCredential(LoginCredential credential) {
		sessionFactory.getCurrentSession().update(credential);
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getUsersByPerson(org.openmrs.Person, boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<User> getUsersByPerson(Person person, boolean includeRetired) {
		StringBuilder hql = new StringBuilder("from User u where u.uuid <> :daemonUuid");
		if (person != null) {
			hql.append(" and u.person = :person");
		}
		if (!includeRetired) {
			hql.append(" and u.retired = false");
		}
		Query<User> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), User.class);
		query.setParameter("daemonUuid", Daemon.getDaemonUserUuid());
		if (person != null) {
			query.setParameter("person", person);
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#getCountOfUsers(String, List, boolean)
	 */
	@Override
	public Integer getCountOfUsers(String name, List<Role> roles, boolean includeRetired) {
		String hqlSelectStart = "select count(distinct user) from User as user inner join user.person.names as name ";
		Query<Long> query = createUserSearchQuery(name, roles, includeRetired, hqlSelectStart, Long.class);

		return query.uniqueResult().intValue();
	}

	/**
	 * Utility methods that creates a hibernate query object from the specified arguments
	 *
	 * @param name The name of the user to search against
	 * @param roles the roles to match against
	 * @param includeRetired Specifies if retired users should be included or not
	 * @param hqlSelectStart The starting phrase of the select statement that includes the joined
	 *            tables
	 * @param resultClass the expected result type
	 * @return the created hibernate query object
	 */
	private <R> Query<R> createUserSearchQuery(String name, List<Role> roles, boolean includeRetired,
	        String hqlSelectStart, Class<R> resultClass) {

		log.debug("name: " + name);

		name = HibernateUtil.escapeSqlWildcards(name, sessionFactory);

		List<String> criteria = new ArrayList<>();
		int counter = 0;
		Map<String, String> namesMap = new HashMap<>();
		if (name != null) {
			name = name.replace(", ", " ");
			String[] names = name.split(" ");
			for (String n : names) {
				if (n != null && n.length() > 0) {
					String key = "name" + ++counter;
					String value = n + "%";
					namesMap.put(key, value);
					criteria.add("(user.username like :" + key + " or user.systemId like :" + key
					        + " or name.givenName like :" + key + " or name.middleName like :" + key
					        + " or name.familyName like :" + key + " or name.familyName2 like :" + key + ")");
				}
			}
		}

		if (!includeRetired) {
			criteria.add("user.retired = false");
		}

		// build the hql query
		StringBuilder hql = new StringBuilder(hqlSelectStart);
		boolean searchOnRoles = false;

		if (CollectionUtils.isNotEmpty(roles)) {
			hql.append("inner join user.roles as role ");
			searchOnRoles = true;
		}
		hql.append("where user.uuid != :DAEMON_USER_UUID ");

		if (!criteria.isEmpty() || searchOnRoles) {
			hql.append("and ");
		}
		for (Iterator<String> i = criteria.iterator(); i.hasNext();) {
			hql.append(i.next()).append(" ");
			if (i.hasNext()) {
				hql.append("and ");
			}
		}

		//Match against the specified roles
		if (searchOnRoles) {
			if (!criteria.isEmpty()) {
				hql.append(" and ");
			}
			hql.append(" role in (:roleList)");
		}

		Query<R> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), resultClass);
		query.setParameter("DAEMON_USER_UUID", Daemon.getDaemonUserUuid());
		for (Map.Entry<String, String> e : namesMap.entrySet()) {
			query.setParameter(e.getKey(), e.getValue());
		}

		if (searchOnRoles) {
			query.setParameterList("roleList", roles);
		}

		return query;
	}

	/**
	 * @see org.openmrs.api.db.UserDAO#setUserActivationKey(org.openmrs.api.db.LoginCredential)
	 */
	@Override
	public void setUserActivationKey(LoginCredential credentials) {
		sessionFactory.getCurrentSession().merge(credentials);
	}
}
