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

import org.apache.commons.lang3.StringUtils;
import org.hibernate.FlushMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.CareSetting;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Order;
import org.openmrs.OrderAttribute;
import org.openmrs.OrderAttributeType;
import org.openmrs.OrderFrequency;
import org.openmrs.OrderGroup;
import org.openmrs.OrderGroupAttribute;
import org.openmrs.OrderGroupAttributeType;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.OrderDAO;
import org.openmrs.parameter.OrderSearchCriteria;
import org.openmrs.User;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * This class should not be used directly. This is just a common implementation of the OrderDAO that
 * is used by the OrderService. This class is injected by spring into the desired OrderService
 * class. This injection is determined by the xml mappings and elements in the spring application
 * context: /metadata/api/spring/applicationContext.xml.<br>
 * <br>
 * The OrderService should be used for all Order related database manipulation.
 *
 * @see org.openmrs.api.OrderService
 * @see org.openmrs.api.db.OrderDAO
 */
public class HibernateOrderDAO implements OrderDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateOrderDAO.class);

	/**
	 * Hibernate session factory
	 */
	private SessionFactory sessionFactory;

	public HibernateOrderDAO() {
	}

	/**
	 * Set session factory
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#saveOrder(org.openmrs.Order)
	 * @see org.openmrs.api.OrderService#saveOrder(org.openmrs.Order, org.openmrs.api.OrderContext)
	 */
	@Override
	public Order saveOrder(Order order) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(order);

		return order;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#deleteOrder(org.openmrs.Order)
	 * @see org.openmrs.api.OrderService#purgeOrder(org.openmrs.Order)
	 */
	@Override
	public void deleteOrder(Order order) throws DAOException {
		sessionFactory.getCurrentSession().delete(order);
	}

	/**
	 * @see org.openmrs.api.OrderService#getOrder(java.lang.Integer)
	 */
	@Override
	public Order getOrder(Integer orderId) throws DAOException {
		log.debug("getting order #{}", orderId);

		return sessionFactory.getCurrentSession().get(Order.class, orderId);
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrders(org.openmrs.OrderType, java.util.List,
	 *      java.util.List, java.util.List, java.util.List)
	 */
	@Override
	public List<Order> getOrders(OrderType orderType, List<Patient> patients, List<Concept> concepts, List<User> orderers,
	        List<Encounter> encounters) {

		StringBuilder hql = new StringBuilder("FROM Order o WHERE 1=1");

		if (orderType != null) {
			hql.append(" AND o.orderType = :orderType");
		}
		if (!patients.isEmpty()) {
			hql.append(" AND o.patient IN :patients");
		}
		if (!concepts.isEmpty()) {
			hql.append(" AND o.concept IN :concepts");
		}
		if (!orderers.isEmpty()) {
			hql.append(" AND o.orderer IN :orderers");
		}
		if (!encounters.isEmpty()) {
			hql.append(" AND o.encounter IN :encounters");
		}
		hql.append(" ORDER BY o.dateActivated DESC");

		Query<Order> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Order.class);

		if (orderType != null) {
			query.setParameter("orderType", orderType);
		}
		if (!patients.isEmpty()) {
			query.setParameterList("patients", patients);
		}
		if (!concepts.isEmpty()) {
			query.setParameterList("concepts", concepts);
		}
		if (!orderers.isEmpty()) {
			query.setParameterList("orderers", orderers);
		}
		if (!encounters.isEmpty()) {
			query.setParameterList("encounters", encounters);
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrders(OrderSearchCriteria)
	 */
	@Override
	public List<Order> getOrders(OrderSearchCriteria searchCriteria) {
		StringBuilder hql = new StringBuilder("FROM Order o WHERE 1=1");

		if (searchCriteria.getPatient() != null && searchCriteria.getPatient().getPatientId() != null) {
			hql.append(" AND o.patient = :patient");
		}
		if (searchCriteria.getCareSetting() != null && searchCriteria.getCareSetting().getId() != null) {
			hql.append(" AND o.careSetting = :careSetting");
		}
		if (searchCriteria.getConcepts() != null && !searchCriteria.getConcepts().isEmpty()) {
			hql.append(" AND o.concept IN :concepts");
		}
		if (searchCriteria.getOrderTypes() != null && !searchCriteria.getOrderTypes().isEmpty()) {
			hql.append(" AND o.orderType IN :orderTypes");
		}
		if (searchCriteria.getOrderNumber() != null) {
			hql.append(" AND lower(o.orderNumber) = lower(:orderNumber)");
		}
		if (searchCriteria.getAccessionNumber() != null) {
			hql.append(" AND lower(o.accessionNumber) = lower(:accessionNumber)");
		}
		if (searchCriteria.getActivatedOnOrBeforeDate() != null) {
			hql.append(" AND o.dateActivated <= :activatedOnOrBeforeDate");
		}
		if (searchCriteria.getActivatedOnOrAfterDate() != null) {
			hql.append(" AND o.dateActivated >= :activatedOnOrAfterDate");
		}
		if (searchCriteria.isStopped()) {
			hql.append(" AND o.dateStopped IS NOT NULL");
		}
		if (searchCriteria.getAutoExpireOnOrBeforeDate() != null) {
			hql.append(" AND o.autoExpireDate <= :autoExpireOnOrBeforeDate");
		}
		if (searchCriteria.getAction() != null) {
			hql.append(" AND o.action = :action");
		}
		if (searchCriteria.getExcludeDiscontinueOrders()) {
			hql.append(" AND (o.action != :discontinueAction OR o.action IS NULL)");
		}

		// fulfillerStatus logic
		boolean hasFulfillerStatusExpr = searchCriteria.getFulfillerStatus() != null;
		boolean hasFulfillerStatusCriteria = searchCriteria.getIncludeNullFulfillerStatus() != null;

		if (hasFulfillerStatusExpr && hasFulfillerStatusCriteria) {
			if (searchCriteria.getIncludeNullFulfillerStatus()) {
				hql.append(" AND (o.fulfillerStatus = :fulfillerStatus OR o.fulfillerStatus IS NULL)");
			} else {
				hql.append(" AND (o.fulfillerStatus = :fulfillerStatus OR o.fulfillerStatus IS NOT NULL)");
			}
		} else if (hasFulfillerStatusExpr) {
			hql.append(" AND o.fulfillerStatus = :fulfillerStatus");
		} else if (hasFulfillerStatusCriteria) {
			if (searchCriteria.getIncludeNullFulfillerStatus()) {
				hql.append(" AND o.fulfillerStatus IS NULL");
			} else {
				hql.append(" AND o.fulfillerStatus IS NOT NULL");
			}
		}

		if (searchCriteria.getExcludeCanceledAndExpired()) {
			// exclude expired orders
			hql.append(" AND (o.autoExpireDate IS NULL OR o.autoExpireDate > :nowForExpired)");
			// exclude Canceled Orders
			hql.append(" AND (o.dateStopped IS NULL OR o.dateStopped > :nowForStopped)");
		}
		if (searchCriteria.getCanceledOrExpiredOnOrBeforeDate() != null) {
			hql.append(" AND ((o.dateStopped IS NOT NULL AND o.dateStopped <= :canceledOrExpiredDate)"
					+ " OR (o.autoExpireDate IS NOT NULL AND o.autoExpireDate <= :canceledOrExpiredDate2))");
		}
		if (!searchCriteria.getIncludeVoided()) {
			hql.append(" AND o.voided = false");
		}

		hql.append(" ORDER BY o.dateActivated DESC");

		Query<Order> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Order.class);

		if (searchCriteria.getPatient() != null && searchCriteria.getPatient().getPatientId() != null) {
			query.setParameter("patient", searchCriteria.getPatient());
		}
		if (searchCriteria.getCareSetting() != null && searchCriteria.getCareSetting().getId() != null) {
			query.setParameter("careSetting", searchCriteria.getCareSetting());
		}
		if (searchCriteria.getConcepts() != null && !searchCriteria.getConcepts().isEmpty()) {
			query.setParameterList("concepts", searchCriteria.getConcepts());
		}
		if (searchCriteria.getOrderTypes() != null && !searchCriteria.getOrderTypes().isEmpty()) {
			query.setParameterList("orderTypes", searchCriteria.getOrderTypes());
		}
		if (searchCriteria.getOrderNumber() != null) {
			query.setParameter("orderNumber", searchCriteria.getOrderNumber());
		}
		if (searchCriteria.getAccessionNumber() != null) {
			query.setParameter("accessionNumber", searchCriteria.getAccessionNumber());
		}
		if (searchCriteria.getActivatedOnOrBeforeDate() != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(searchCriteria.getActivatedOnOrBeforeDate());
			query.setParameter("activatedOnOrBeforeDate", OpenmrsUtil.getLastMomentOfDay(cal.getTime()));
		}
		if (searchCriteria.getActivatedOnOrAfterDate() != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(searchCriteria.getActivatedOnOrAfterDate());
			query.setParameter("activatedOnOrAfterDate", OpenmrsUtil.firstSecondOfDay(cal.getTime()));
		}
		if (searchCriteria.getAutoExpireOnOrBeforeDate() != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(searchCriteria.getAutoExpireOnOrBeforeDate());
			query.setParameter("autoExpireOnOrBeforeDate", OpenmrsUtil.getLastMomentOfDay(cal.getTime()));
		}
		if (searchCriteria.getAction() != null) {
			query.setParameter("action", searchCriteria.getAction());
		}
		if (searchCriteria.getExcludeDiscontinueOrders()) {
			query.setParameter("discontinueAction", Order.Action.DISCONTINUE);
		}
		if (hasFulfillerStatusExpr) {
			query.setParameter("fulfillerStatus", searchCriteria.getFulfillerStatus());
		}
		if (searchCriteria.getExcludeCanceledAndExpired()) {
			Calendar cal = Calendar.getInstance();
			query.setParameter("nowForExpired", cal.getTime());
			query.setParameter("nowForStopped", cal.getTime());
		}
		if (searchCriteria.getCanceledOrExpiredOnOrBeforeDate() != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(searchCriteria.getCanceledOrExpiredOnOrBeforeDate());
			Date cutoff = OpenmrsUtil.getLastMomentOfDay(cal.getTime());
			query.setParameter("canceledOrExpiredDate", cutoff);
			query.setParameter("canceledOrExpiredDate2", cutoff);
		}

		return query.list();
	}

	/**
	 * @see OrderDAO#getOrders(org.openmrs.Patient, org.openmrs.CareSetting, java.util.List,
	 *      boolean, boolean)
	 */
	@Override
	public List<Order> getOrders(Patient patient, CareSetting careSetting, List<OrderType> orderTypes,
	        boolean includeVoided, boolean includeDiscontinuationOrders) {
		return createOrderQuery(patient, careSetting, orderTypes, includeVoided, includeDiscontinuationOrders).list();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderByUuid(java.lang.String)
	 */
	@Override
	public Order getOrderByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Order o WHERE o.uuid = :uuid", Order.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getRevisionOrder(org.openmrs.Order)
	 */
	@Override
	public Order getDiscontinuationOrder(Order order) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Order o WHERE o.previousOrder = :previousOrder"
						+ " AND o.action = :action AND o.voided = false", Order.class)
				.setParameter("previousOrder", order)
				.setParameter("action", Order.Action.DISCONTINUE)
				.uniqueResult();
	}

	@Override
	public Order getRevisionOrder(Order order) throws APIException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Order o WHERE o.previousOrder = :previousOrder"
						+ " AND o.action = :action AND o.voided = false", Order.class)
				.setParameter("previousOrder", order)
				.setParameter("action", Order.Action.REVISE)
				.uniqueResult();
	}

	@Override
	public List<Object[]> getOrderFromDatabase(Order order, boolean isOrderADrugOrder) throws APIException {
		String sql = "SELECT patient_id, care_setting, concept_id FROM orders WHERE order_id = :orderId";

		if (isOrderADrugOrder) {
			sql = " SELECT o.patient_id, o.care_setting, o.concept_id, d.drug_inventory_id "
			        + " FROM orders o, drug_order d WHERE o.order_id = d.order_id AND o.order_id = :orderId";
		}
		Query<Object[]> query = sessionFactory.getCurrentSession().createNativeQuery(sql, Object[].class);
		query.setParameter("orderId", order.getOrderId());

		//prevent hibernate from flushing before fetching the list
		query.setHibernateFlushMode(FlushMode.MANUAL);

		return query.list();
	}

	/**
	 * @see OrderDAO#saveOrderGroup(OrderGroup)
	 */
	@Override
	public OrderGroup saveOrderGroup(OrderGroup orderGroup) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(orderGroup);
		return orderGroup;
	}

	/**
	 * @see OrderDAO#getOrderGroupByUuid(String)
	 * @see org.openmrs.api.OrderService#getOrderGroupByUuid(String)
	 */
	@Override
	public OrderGroup getOrderGroupByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroup o WHERE o.uuid = :uuid", OrderGroup.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see OrderDAO#getOrderGroupById(Integer)
	 * @see org.openmrs.api.OrderService#getOrderGroup(Integer)
	 */
	@Override
	public OrderGroup getOrderGroupById(Integer orderGroupId) throws DAOException {
		return sessionFactory.getCurrentSession().get(OrderGroup.class, orderGroupId);
	}

	/**
	 * Delete Obs that references (deleted) Order
	 */
	@Override
	public void deleteObsThatReference(Order order) {
		if (order != null) {
			sessionFactory.getCurrentSession().createQuery("DELETE Obs WHERE order = :order")
			        .setParameter("order", order)
			        .executeUpdate();
		}
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderByOrderNumber(java.lang.String)
	 */
	@Override
	public Order getOrderByOrderNumber(String orderNumber) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Order o WHERE o.orderNumber = :orderNumber", Order.class)
				.setParameter("orderNumber", orderNumber)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getNextOrderNumberSeedSequenceValue()
	 */
	@Override
	public Long getNextOrderNumberSeedSequenceValue() {
		GlobalProperty globalProperty = sessionFactory.getCurrentSession().get(GlobalProperty.class,
		    OpenmrsConstants.GP_NEXT_ORDER_NUMBER_SEED, LockOptions.UPGRADE);

		if (globalProperty == null) {
			throw new APIException("GlobalProperty.missing", new Object[] { OpenmrsConstants.GP_NEXT_ORDER_NUMBER_SEED });
		}

		String gpTextValue = globalProperty.getPropertyValue();
		if (StringUtils.isBlank(gpTextValue)) {
			throw new APIException("GlobalProperty.invalid.value",
			        new Object[] { OpenmrsConstants.GP_NEXT_ORDER_NUMBER_SEED });
		}

		Long gpNumericValue;
		try {
			gpNumericValue = Long.parseLong(gpTextValue);
		}
		catch (NumberFormatException ex) {
			throw new APIException("GlobalProperty.invalid.value",
			        new Object[] { OpenmrsConstants.GP_NEXT_ORDER_NUMBER_SEED });
		}

		globalProperty.setPropertyValue(String.valueOf(gpNumericValue + 1));

		sessionFactory.getCurrentSession().save(globalProperty);

		return gpNumericValue;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getActiveOrders(org.openmrs.Patient, java.util.List,
	 *      org.openmrs.CareSetting, java.util.Date)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Order> getActiveOrders(Patient patient, List<OrderType> orderTypes, CareSetting careSetting, Date asOfDate) {
		StringBuilder hql = new StringBuilder();
		hql.append("FROM Order o WHERE 1=1");

		if (patient != null) {
			hql.append(" AND o.patient = :patient");
		}
		if (careSetting != null) {
			hql.append(" AND o.careSetting = :careSetting");
		}
		if (orderTypes != null && !orderTypes.isEmpty()) {
			hql.append(" AND o.orderType IN :orderTypes");
		}
		hql.append(" AND o.voided = false");
		hql.append(" AND o.action != :discontinueAction");
		hql.append(" AND o.dateActivated <= :asOfDate");
		hql.append(" AND (");
		hql.append("  (o.dateStopped IS NULL AND o.autoExpireDate IS NULL)");
		hql.append("  OR (o.dateStopped IS NULL AND o.autoExpireDate >= :asOfDate2)");
		hql.append("  OR o.dateStopped >= :asOfDate3");
		hql.append(" )");

		Query<Order> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Order.class);

		if (patient != null) {
			query.setParameter("patient", patient);
		}
		if (careSetting != null) {
			query.setParameter("careSetting", careSetting);
		}
		if (orderTypes != null && !orderTypes.isEmpty()) {
			query.setParameterList("orderTypes", orderTypes);
		}
		query.setParameter("discontinueAction", Order.Action.DISCONTINUE);
		query.setParameter("asOfDate", asOfDate);
		query.setParameter("asOfDate2", asOfDate);
		query.setParameter("asOfDate3", asOfDate);

		return query.list();
	}

	/**
	 * Creates and returns a typed Query filtering on the specified parameters
	 *
	 * @param patient
	 * @param careSetting
	 * @param orderTypes
	 * @param includeVoided
	 * @param includeDiscontinuationOrders
	 * @return
	 */
	private Query<Order> createOrderQuery(Patient patient, CareSetting careSetting, List<OrderType> orderTypes,
	        boolean includeVoided, boolean includeDiscontinuationOrders) {
		StringBuilder hql = new StringBuilder("FROM Order o WHERE 1=1");

		if (patient != null) {
			hql.append(" AND o.patient = :patient");
		}
		if (careSetting != null) {
			hql.append(" AND o.careSetting = :careSetting");
		}
		if (orderTypes != null && !orderTypes.isEmpty()) {
			hql.append(" AND o.orderType IN :orderTypes");
		}
		if (!includeVoided) {
			hql.append(" AND o.voided = false");
		}
		if (!includeDiscontinuationOrders) {
			hql.append(" AND o.action != :discontinueAction");
		}

		Query<Order> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Order.class);

		if (patient != null) {
			query.setParameter("patient", patient);
		}
		if (careSetting != null) {
			query.setParameter("careSetting", careSetting);
		}
		if (orderTypes != null && !orderTypes.isEmpty()) {
			query.setParameterList("orderTypes", orderTypes);
		}
		if (!includeDiscontinuationOrders) {
			query.setParameter("discontinueAction", Order.Action.DISCONTINUE);
		}

		return query;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getCareSetting(Integer)
	 */
	@Override
	public CareSetting getCareSetting(Integer careSettingId) {
		return sessionFactory.getCurrentSession().get(CareSetting.class, careSettingId);
	}

	/**
	 * @see OrderDAO#getCareSettingByUuid(String)
	 */
	@Override
	public CareSetting getCareSettingByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM CareSetting cs WHERE cs.uuid = :uuid", CareSetting.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see OrderDAO#getCareSettingByName(String)
	 */
	@Override
	public CareSetting getCareSettingByName(String name) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM CareSetting cs WHERE lower(cs.name) = lower(:name)", CareSetting.class)
				.setParameter("name", name)
				.uniqueResult();
	}

	/**
	 * @see OrderDAO#getCareSettings(boolean)
	 */
	@Override
	public List<CareSetting> getCareSettings(boolean includeRetired) {
		StringBuilder hql = new StringBuilder("FROM CareSetting cs");
		if (!includeRetired) {
			hql.append(" WHERE cs.retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), CareSetting.class).list();
	}

	/**
	 * @see OrderDAO#getOrderTypeByName
	 */
	@Override
	public OrderType getOrderTypeByName(String orderTypeName) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderType ot WHERE ot.name = :name", OrderType.class)
				.setParameter("name", orderTypeName)
				.uniqueResult();
	}

	/**
	 * @see OrderDAO#getOrderFrequency
	 */
	@Override
	public OrderFrequency getOrderFrequency(Integer orderFrequencyId) {
		return sessionFactory.getCurrentSession().get(OrderFrequency.class, orderFrequencyId);
	}

	/**
	 * @see OrderDAO#getOrderFrequencyByUuid
	 */
	@Override
	public OrderFrequency getOrderFrequencyByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderFrequency o WHERE o.uuid = :uuid", OrderFrequency.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see OrderDAO#getOrderFrequencies(boolean)
	 */
	@Override
	public List<OrderFrequency> getOrderFrequencies(boolean includeRetired) {
		StringBuilder hql = new StringBuilder("FROM OrderFrequency of");
		if (!includeRetired) {
			hql.append(" WHERE of.retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), OrderFrequency.class).list();
	}

	/**
	 * @see OrderDAO#getOrderFrequencies(String, java.util.Locale, boolean, boolean)
	 */
	@Override
	public List<OrderFrequency> getOrderFrequencies(String searchPhrase, Locale locale, boolean exactLocale,
	        boolean includeRetired) {

		StringBuilder hql = new StringBuilder(
				"SELECT DISTINCT of FROM OrderFrequency of"
				+ " JOIN of.concept concept"
				+ " JOIN concept.names conceptName"
				+ " WHERE lower(conceptName.name) LIKE :searchPhrase");

		if (locale != null) {
			List<Locale> locales = new ArrayList<>(2);
			locales.add(locale);
			if (!exactLocale && StringUtils.isNotBlank(locale.getCountry())) {
				locales.add(new Locale(locale.getLanguage()));
			}
			hql.append(" AND conceptName.locale IN :locales");
		}

		if (!includeRetired) {
			hql.append(" AND of.retired = false");
		}

		Query<OrderFrequency> query = sessionFactory.getCurrentSession()
				.createQuery(hql.toString(), OrderFrequency.class);
		query.setParameter("searchPhrase", "%" + searchPhrase.toLowerCase() + "%");

		if (locale != null) {
			List<Locale> locales = new ArrayList<>(2);
			locales.add(locale);
			if (!exactLocale && StringUtils.isNotBlank(locale.getCountry())) {
				locales.add(new Locale(locale.getLanguage()));
			}
			query.setParameterList("locales", locales);
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#saveOrderFrequency(org.openmrs.OrderFrequency)
	 */
	@Override
	public OrderFrequency saveOrderFrequency(OrderFrequency orderFrequency) {
		sessionFactory.getCurrentSession().saveOrUpdate(orderFrequency);
		return orderFrequency;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#purgeOrderFrequency(org.openmrs.OrderFrequency)
	 */
	@Override
	public void purgeOrderFrequency(OrderFrequency orderFrequency) {
		sessionFactory.getCurrentSession().delete(orderFrequency);
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#isOrderFrequencyInUse(org.openmrs.OrderFrequency)
	 */
	@Override
	public boolean isOrderFrequencyInUse(OrderFrequency orderFrequency) {

		Set<EntityType<?>> entities = sessionFactory.getMetamodel().getEntities();

		for (EntityType<?> entityTpe : entities) {
			Class<?> entityClass = entityTpe.getJavaType();
			if (Order.class.equals(entityClass)) {
				//ignore the org.openmrs.Order class itself
				continue;
			}

			if (!Order.class.isAssignableFrom(entityClass)) {
				//not a sub class of Order
				continue;
			}

			for (Attribute<?,?> attribute : entityTpe.getDeclaredAttributes()) {
				if (attribute.getJavaType().equals(OrderFrequency.class)) {
					String hql = "FROM " + entityClass.getName() + " e WHERE e." + attribute.getName() + " = :orderFrequency";
					List<?> results = sessionFactory.getCurrentSession()
							.createQuery(hql)
							.setParameter("orderFrequency", orderFrequency)
							.setMaxResults(1)
							.list();
					if (!results.isEmpty()) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderFrequencyByConcept(org.openmrs.Concept)
	 */
	@Override
	public OrderFrequency getOrderFrequencyByConcept(Concept concept) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderFrequency of WHERE of.concept = :concept", OrderFrequency.class)
				.setParameter("concept", concept)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderType(Integer)
	 */
	@Override
	public OrderType getOrderType(Integer orderTypeId) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderType ot WHERE ot.orderTypeId = :orderTypeId", OrderType.class)
				.setParameter("orderTypeId", orderTypeId)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderTypeByUuid(String)
	 */
	@Override
	public OrderType getOrderTypeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderType o WHERE o.uuid = :uuid", OrderType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderTypes(boolean)
	 */
	@Override
	public List<OrderType> getOrderTypes(boolean includeRetired) {
		StringBuilder hql = new StringBuilder("FROM OrderType ot");
		if (!includeRetired) {
			hql.append(" WHERE ot.retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), OrderType.class).list();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderTypeByConceptClass(org.openmrs.ConceptClass)
	 */
	@Override
	public OrderType getOrderTypeByConceptClass(ConceptClass conceptClass) {
		return (OrderType) sessionFactory.getCurrentSession().createQuery(
		    "FROM OrderType WHERE :conceptClass IN elements(conceptClasses)")
				.setParameter("conceptClass", conceptClass)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.OrderService#saveOrderType(org.openmrs.OrderType)
	 */
	@Override
	public OrderType saveOrderType(OrderType orderType) {
		sessionFactory.getCurrentSession().saveOrUpdate(orderType);
		return orderType;
	}

	/**
	 * @see org.openmrs.api.OrderService#purgeOrderType(org.openmrs.OrderType)
	 */
	@Override
	public void purgeOrderType(OrderType orderType) {
		sessionFactory.getCurrentSession().delete(orderType);
	}

	/**
	 * @see org.openmrs.api.OrderService#getSubtypes(org.openmrs.OrderType, boolean)
	 */
	@Override
	public List<OrderType> getOrderSubtypes(OrderType orderType, boolean includeRetired) {
		StringBuilder hql = new StringBuilder("FROM OrderType ot WHERE ot.parent = :parent");
		if (!includeRetired) {
			hql.append(" AND ot.retired = false");
		}
		return sessionFactory.getCurrentSession()
				.createQuery(hql.toString(), OrderType.class)
				.setParameter("parent", orderType)
				.list();
	}

	@Override
	public boolean isOrderTypeInUse(OrderType orderType) {
		return !sessionFactory.getCurrentSession()
				.createQuery("FROM Order o WHERE o.orderType = :orderType", Order.class)
				.setParameter("orderType", orderType)
				.list()
				.isEmpty();
	}

	/**
	 * @see OrderDAO#getOrderGroupsByPatient(Patient)
	 */
	@Override
	public List<OrderGroup> getOrderGroupsByPatient(Patient patient) throws DAOException {
		if (patient == null) {
			throw new APIException("Patient cannot be null");
		}
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroup og WHERE og.patient = :patient", OrderGroup.class)
				.setParameter("patient", patient)
				.list();
	}

	/**
	 * @see OrderDAO#getOrderGroupsByEncounter(Encounter)
	 */
	@Override
	public List<OrderGroup> getOrderGroupsByEncounter(Encounter encounter) throws DAOException {
		if (encounter == null) {
			throw new APIException("Encounter cannot be null");
		}
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroup og WHERE og.encounter = :encounter", OrderGroup.class)
				.setParameter("encounter", encounter)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getAllOrderGroupAttributeTypes()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<OrderGroupAttributeType> getAllOrderGroupAttributeTypes() throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroupAttributeType", OrderGroupAttributeType.class)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderGroupAttributeType(java.lang.Integer)
	 */
	@Override
	public OrderGroupAttributeType getOrderGroupAttributeType(Integer orderGroupAttributeTypeId) throws DAOException {
		return sessionFactory.getCurrentSession().get(OrderGroupAttributeType.class, orderGroupAttributeTypeId);
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderGroupAttributeTypeByUuid(java.lang.String)
	 */
	@Override
	public OrderGroupAttributeType getOrderGroupAttributeTypeByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroupAttributeType t WHERE t.uuid = :uuid", OrderGroupAttributeType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#saveOrderGroupAttributeType(org.openmrs.OrderGroupAttributeType)
	 */
	@Override
	public OrderGroupAttributeType saveOrderGroupAttributeType(OrderGroupAttributeType orderGroupAttributeType) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(orderGroupAttributeType);
		return orderGroupAttributeType;
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#deleteOrderGroupAttributeType(org.openmrs.OrderGroupAttributeType)
	 */
	@Override
	public void deleteOrderGroupAttributeType(OrderGroupAttributeType orderGroupAttributeType) throws DAOException {
		sessionFactory.getCurrentSession().delete(orderGroupAttributeType);
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderGroupAttributeByUuid(String)
	 */
	@Override
	public OrderGroupAttribute getOrderGroupAttributeByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroupAttribute d WHERE d.uuid = :uuid", OrderGroupAttribute.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getOrderGroupAttributeTypeByName(String)
	 */
	@Override
	public OrderGroupAttributeType getOrderGroupAttributeTypeByName(String name) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderGroupAttributeType t WHERE t.name = :name", OrderGroupAttributeType.class)
				.setParameter("name", name)
				.uniqueResult();
	}

	/**
	 * @param uuid The uuid associated with the order attribute to retrieve.
	 * @see org.openmrs.api.db.OrderDAO#getOrderAttributeByUuid(String)
	 */
	@Override
	public OrderAttribute getOrderAttributeByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderAttribute a WHERE a.uuid = :uuid", OrderAttribute.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.OrderDAO#getAllOrderAttributeTypes()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<OrderAttributeType> getAllOrderAttributeTypes() throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderAttributeType", OrderAttributeType.class)
				.list();
	}

	/**
	 * @param orderAttributeTypeId The orderAttributeTypeId for the order attribute type to retrieve.
	 * @see org.openmrs.api.db.OrderDAO#getOrderAttributeTypeById(Integer)
	 */
	@Override
	public OrderAttributeType getOrderAttributeTypeById(Integer orderAttributeTypeId) throws DAOException {
		return sessionFactory.getCurrentSession().get(OrderAttributeType.class, orderAttributeTypeId);
	}

	/**
	 * @param uuid The uuid associated with the order attribute type to retrieve
	 * @see org.openmrs.api.db.OrderDAO#getOrderAttributeTypeByUuid(String)
	 */
	@Override
	public OrderAttributeType getOrderAttributeTypeByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderAttributeType t WHERE t.uuid = :uuid", OrderAttributeType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @param orderAttributeType The orderAttributeType to save
	 * @see org.openmrs.api.db.OrderDAO#saveOrderAttributeType(OrderAttributeType)
	 */
	@Override
	public OrderAttributeType saveOrderAttributeType(OrderAttributeType orderAttributeType) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(orderAttributeType);
		return orderAttributeType;
	}

	/**
	 * @param orderAttributeType The orderAttributeType to retire
	 * @see org.openmrs.api.OrderService#purgeOrderAttributeType(OrderAttributeType)
	 */
	@Override
	public void deleteOrderAttributeType(OrderAttributeType orderAttributeType) throws DAOException {
		sessionFactory.getCurrentSession().delete(orderAttributeType);
	}

	/**
	 * @param name The name of the order attribute type to retrieve
	 * @see org.openmrs.api.db.OrderDAO#getOrderAttributeTypeByName(String)
	 */
	@Override
	public OrderAttributeType getOrderAttributeTypeByName(String name) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM OrderAttributeType t WHERE t.name = :name", OrderAttributeType.class)
				.setParameter("name", name)
				.uniqueResult();
	}
}
