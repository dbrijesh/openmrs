/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.web.filter.initialization;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.openmrs.util.DatabaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseDetective {

	private static final Logger log = LoggerFactory.getLogger(DatabaseDetective.class);

	/** Table names that should be ignored when checking if the database is empty. */
	private static final Set<String> IGNORED_TABLES = new HashSet<>(Arrays.asList(
	        "liquibasechangelog",
	        "liquibasechangeloglock",
	        "dual"          // H2 MySQL-compatibility pseudo-table
	));
	
	private static final String CONNECTION_URL = "connection.url";
	
	private static final String CONNECTION_DRIVER_CLASS = "connection.driver_class";
	
	private static final String CONNECTION_USERNAME = "connection.username";
	
	private static final String CONNECTION_PASSWORD = "connection.password";
	
	/**
	 * Check whether openmrs database is empty. Having just one non-liquibase table in the given
	 * database qualifies this as a non-empty database.
	 *
	 * @param props the runtime properties
	 * @return true if the openmrs database is empty or does not exist yet
	 */
	public boolean isDatabaseEmpty(Properties props) {
		if (props == null) {
			return true;
		}
		
		Connection connection = null;
		
		try {
			DatabaseUtil.loadDatabaseDriver(props.getProperty(CONNECTION_URL), props.getProperty(CONNECTION_DRIVER_CLASS,
			    null));
			
			connection = DriverManager.getConnection(props.getProperty(CONNECTION_URL), props
			        .getProperty(CONNECTION_USERNAME), props.getProperty(CONNECTION_PASSWORD));
			
			DatabaseMetaData dbMetaData = connection.getMetaData();

			String[] types = { "TABLE" };

			// Restrict to the PUBLIC (user) schema to avoid H2's INFORMATION_SCHEMA tables
			// (H2 2.x exposes INFORMATION_SCHEMA.CONSTANTS etc. as type "BASE TABLE").
			// For MySQL/MariaDB/PostgreSQL null schema returns only user tables anyway.
			String catalog = connection.getCatalog();
			String userSchema = connection.getSchema();   // "PUBLIC" on H2, db-name on MySQL
			ResultSet tbls = dbMetaData.getTables(catalog, userSchema, null, types);
			
			while (tbls.next()) {
				String tableName = tbls.getString("TABLE_NAME");
				String schema    = tbls.getString("TABLE_SCHEM");
				String lower     = tableName.toLowerCase();
				if (!IGNORED_TABLES.contains(lower)) {
					log.warn("isDatabaseEmpty: found non-liquibase table '{}.{}' (type={}) → DB is not empty",
					        schema, tableName, tbls.getString("TABLE_TYPE"));
					return false;
				}
				log.info("isDatabaseEmpty: ignoring known table '{}.{}'", schema, tableName);
			}
			log.info("isDatabaseEmpty: no user tables found → DB is empty");
			return true;
		}
		catch (Exception e) {
			// consider the database to be empty
			return true;
		}
		finally {
			try {
				if (connection != null) {
					connection.close();
				}
			}
			catch (Exception e) {
				// consider the database to be empty
				return true;
			}
		}
	}
}
