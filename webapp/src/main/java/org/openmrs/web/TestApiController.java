/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.web;

import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Test REST controller that exercises real OpenMRS business services.
 * Accessible at /openmrs/test-api/* — no webservices.rest module needed.
 */
@RestController
@RequestMapping("/test-api")
@CrossOrigin(origins = "*")
public class TestApiController {

	private static final String ADMIN = "admin";
	// Initial password from core-data Liquibase changeset; wizard changes it to Admin123
	// but that step requires ContextLoader which conflicts with Spring Boot's root context.
	// Use the endpoint /test-api/change-admin-password to set it to Admin123.
	private static final String PASS  = "test";

	// ── auth helper ─────────────────────────────────────────────────────────

	@FunctionalInterface
	interface ServiceCall<T> { T call() throws Exception; }

	private <T> Map<String, Object> run(ServiceCall<T> fn) {
		Map<String, Object> out = new LinkedHashMap<>();
		Context.openSession();
		try {
			Context.authenticate(ADMIN, PASS);
			out.put("ok",   true);
			out.put("data", fn.call());
		} catch (org.openmrs.api.APIAuthenticationException e) {
			out.put("ok",    false);
			out.put("error", "Authentication failed — check admin/Admin123 credentials");
		} catch (Exception e) {
			out.put("ok",    false);
			out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
		} finally {
			Context.closeSession();
		}
		return out;
	}

	// ── 0. Change admin password ────────────────────────────────────────────

	@PostMapping("/change-admin-password")
	public Map<String, Object> changeAdminPassword(
	        @RequestParam(defaultValue = "test")    String oldPassword,
	        @RequestParam(defaultValue = "Admin123") String newPassword) {
		Map<String, Object> out = new LinkedHashMap<>();
		Context.openSession();
		try {
			Context.authenticate(ADMIN, oldPassword);
			Context.getUserService().changePassword(oldPassword, newPassword);
			Context.logout();
			out.put("ok",      true);
			out.put("message", "Admin password changed to '" + newPassword + "'. Update PASS constant if needed.");
		} catch (Exception e) {
			out.put("ok",    false);
			out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
		} finally {
			Context.closeSession();
		}
		return out;
	}

	// ── 1. Health / runtime info (no auth) ──────────────────────────────────

	@GetMapping("/health")
	public Map<String, Object> health() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("status",       "UP");
		m.put("java",         System.getProperty("java.version"));
		m.put("openmrs",      "2.5.15");
		m.put("springBoot",   "3.4.5");
		m.put("hibernate",    "6.6.4.Final");
		m.put("timestamp",    new Date().toString());
		try {
			Properties p = Context.getRuntimeProperties();
			m.put("dbUrl",  p.getProperty("connection.url", "N/A"));
			m.put("dbUser", p.getProperty("connection.username", "N/A"));
		} catch (Exception e) {
			m.put("dbUrl", "N/A (" + e.getMessage() + ")");
		}
		return m;
	}

	// ── 2. All services smoke-test ───────────────────────────────────────────

	@GetMapping("/services")
	public Map<String, Object> services() {
		return run(() -> {
			Map<String, Object> results = new LinkedHashMap<>();
			// PatientService
			try {
				long c = Context.getPatientService().getAllPatients(false).size();
				results.put("PatientService",      "OK — " + c + " patients");
			} catch (Exception e) { results.put("PatientService", "FAIL: " + e.getMessage()); }
			// ConceptService
			try {
				long c = Context.getConceptService().getAllConcepts(null, true, true).size();
				results.put("ConceptService",      "OK — " + c + " concepts");
			} catch (Exception e) { results.put("ConceptService", "FAIL: " + e.getMessage()); }
			// UserService
			try {
				long c = Context.getUserService().getAllUsers().size();
				results.put("UserService",         "OK — " + c + " users");
			} catch (Exception e) { results.put("UserService", "FAIL: " + e.getMessage()); }
			// LocationService
			try {
				long c = Context.getLocationService().getAllLocations(false).size();
				results.put("LocationService",     "OK — " + c + " locations");
			} catch (Exception e) { results.put("LocationService", "FAIL: " + e.getMessage()); }
			// PersonService
			try {
				long c = Context.getPersonService().getPeople("", false).size();
				results.put("PersonService",       "OK — " + c + " persons");
			} catch (Exception e) { results.put("PersonService", "FAIL: " + e.getMessage()); }
			// EncounterService
			try {
				EncounterService es = Context.getEncounterService();
				results.put("EncounterService",    "OK — " + es.getAllEncounterTypes(false).size() + " encounter types");
			} catch (Exception e) { results.put("EncounterService", "FAIL: " + e.getMessage()); }
			// ObsService
			try {
				ObsService os = Context.getObsService();
				results.put("ObsService",          "OK — loaded");
			} catch (Exception e) { results.put("ObsService", "FAIL: " + e.getMessage()); }
			// OrderService
			try {
				OrderService ors = Context.getOrderService();
				results.put("OrderService",        "OK — " + ors.getOrderTypes(false).size() + " order types");
			} catch (Exception e) { results.put("OrderService", "FAIL: " + e.getMessage()); }
			// ProgramWorkflowService
			try {
				long c = Context.getProgramWorkflowService().getAllPrograms(false).size();
				results.put("ProgramWorkflowService", "OK — " + c + " programs");
			} catch (Exception e) { results.put("ProgramWorkflowService", "FAIL: " + e.getMessage()); }
			// AdministrationService
			try {
				AdministrationService as = Context.getAdministrationService();
				results.put("AdministrationService",  "OK — " + as.getAllGlobalProperties().size() + " global properties");
			} catch (Exception e) { results.put("AdministrationService", "FAIL: " + e.getMessage()); }
			return results;
		});
	}

	// ── 3. Patients ──────────────────────────────────────────────────────────

	@GetMapping("/patients")
	public Map<String, Object> patients(@RequestParam(defaultValue = "20") int limit) {
		return run(() -> {
			List<Patient> all = Context.getPatientService().getAllPatients(false);
			List<Map<String, Object>> rows = new ArrayList<>();
			for (Patient p : all.subList(0, Math.min(all.size(), limit))) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("id",          p.getPatientId());
				r.put("uuid",        p.getUuid());
				r.put("name",        p.getPersonName() != null ? p.getPersonName().getFullName() : "—");
				r.put("gender",      p.getGender());
				r.put("birthdate",   p.getBirthdate() != null ? p.getBirthdate().toString() : null);
				r.put("identifiers", p.getActiveIdentifiers().stream()
				        .map(i -> i.getIdentifierType().getName() + ": " + i.getIdentifier())
				        .collect(Collectors.toList()));
				rows.add(r);
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("total", all.size());
			m.put("limit", limit);
			m.put("patients", rows);
			return m;
		});
	}

	@PostMapping("/patient")
	public Map<String, Object> createPatient(@RequestBody(required = false) Map<String, String> body) {
		return run(() -> {
			String givenName  = body != null ? body.getOrDefault("givenName",  "TestFirst")  : "TestFirst";
			String familyName = body != null ? body.getOrDefault("familyName", "TestLast")   : "TestLast";
			String gender     = body != null ? body.getOrDefault("gender",     "M")           : "M";

			Patient p = new Patient();
			p.setGender(gender);
			PersonName name = new PersonName(givenName, null, familyName);
			p.addName(name);
			PatientIdentifierType idType =
			        Context.getPatientService().getAllPatientIdentifierTypes(false).get(0);
			PatientIdentifier id = new PatientIdentifier("TEST-" + System.currentTimeMillis(), idType,
			        Context.getLocationService().getAllLocations(false).get(0));
			p.addIdentifier(id);
			Patient saved = Context.getPatientService().savePatient(p);

			Map<String, Object> m = new LinkedHashMap<>();
			m.put("message",  "Patient created");
			m.put("id",       saved.getPatientId());
			m.put("uuid",     saved.getUuid());
			m.put("name",     givenName + " " + familyName);
			m.put("deleteAt", "/openmrs/test-api/patient/" + saved.getUuid());
			return m;
		});
	}

	@DeleteMapping("/patient/{uuid}")
	public Map<String, Object> deletePatient(@PathVariable String uuid) {
		return run(() -> {
			Patient p = Context.getPatientService().getPatientByUuid(uuid);
			if (p == null) throw new IllegalArgumentException("Patient not found: " + uuid);
			Context.getPatientService().purgePatient(p);
			return "Patient " + uuid + " deleted";
		});
	}

	// ── 4. Concepts ──────────────────────────────────────────────────────────

	@GetMapping("/concepts")
	public Map<String, Object> concepts(@RequestParam(defaultValue = "20") int limit,
	                                     @RequestParam(defaultValue = "")   String search) {
		return run(() -> {
			ConceptService cs = Context.getConceptService();
			List<Concept> all;
			if (search.isEmpty()) {
				all = cs.getAllConcepts(null, true, true);
			} else {
				all = cs.getConcepts(search, Context.getLocale(), false).stream()
				        .map(ConceptSearchResult::getConcept)
				        .collect(Collectors.toList());
			}
			List<Map<String, Object>> rows = new ArrayList<>();
			for (Concept c : all.subList(0, Math.min(all.size(), limit))) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("id",       c.getConceptId());
				r.put("uuid",     c.getUuid());
				r.put("name",     c.getName() != null ? c.getName().getName() : "—");
				r.put("datatype", c.getDatatype() != null ? c.getDatatype().getName() : "—");
				r.put("class",    c.getConceptClass() != null ? c.getConceptClass().getName() : "—");
				rows.add(r);
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("total", all.size());
			m.put("limit", limit);
			m.put("concepts", rows);
			return m;
		});
	}

	// ── 5. Users ─────────────────────────────────────────────────────────────

	@GetMapping("/users")
	public Map<String, Object> users() {
		return run(() -> {
			List<User> all = Context.getUserService().getAllUsers();
			List<Map<String, Object>> rows = new ArrayList<>();
			for (User u : all) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("id",       u.getUserId());
				r.put("uuid",     u.getUuid());
				r.put("username", u.getUsername());
				r.put("systemId", u.getSystemId());
				r.put("roles",    u.getRoles().stream().map(Role::getRole).collect(Collectors.toList()));
				r.put("retired",  u.getRetired());
				rows.add(r);
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("total", all.size());
			m.put("users", rows);
			return m;
		});
	}

	// ── 6. Locations ─────────────────────────────────────────────────────────

	@GetMapping("/locations")
	public Map<String, Object> locations() {
		return run(() -> {
			List<Location> all = Context.getLocationService().getAllLocations(false);
			List<Map<String, Object>> rows = new ArrayList<>();
			for (Location l : all) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("id",   l.getLocationId());
				r.put("uuid", l.getUuid());
				r.put("name", l.getName());
				r.put("description", l.getDescription());
				r.put("parent", l.getParentLocation() != null ? l.getParentLocation().getName() : null);
				rows.add(r);
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("total", all.size());
			m.put("locations", rows);
			return m;
		});
	}

	// ── 7. Global Properties ─────────────────────────────────────────────────

	@GetMapping("/global-properties")
	public Map<String, Object> globalProperties(@RequestParam(defaultValue = "") String prefix) {
		return run(() -> {
			List<GlobalProperty> all = Context.getAdministrationService().getAllGlobalProperties();
			List<Map<String, Object>> rows = all.stream()
			        .filter(gp -> prefix.isEmpty() || gp.getProperty().startsWith(prefix))
			        .limit(50)
			        .map(gp -> {
				        Map<String, Object> r = new LinkedHashMap<>();
				        r.put("property", gp.getProperty());
				        r.put("value", gp.getPropertyValue());
				        r.put("description", gp.getDescription());
				        return r;
			        }).collect(Collectors.toList());
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("total", all.size());
			m.put("shown", rows.size());
			m.put("properties", rows);
			return m;
		});
	}

	// ── 8. Encounter Types ───────────────────────────────────────────────────

	@GetMapping("/encounter-types")
	public Map<String, Object> encounterTypes() {
		return run(() -> {
			List<EncounterType> all = Context.getEncounterService().getAllEncounterTypes(false);
			List<Map<String, Object>> rows = new ArrayList<>();
			for (EncounterType et : all) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("id",   et.getEncounterTypeId());
				r.put("uuid", et.getUuid());
				r.put("name", et.getName());
				r.put("description", et.getDescription());
				rows.add(r);
			}
			return Map.of("total", all.size(), "encounterTypes", rows);
		});
	}

	// ── 9. Programs ──────────────────────────────────────────────────────────

	@GetMapping("/programs")
	public Map<String, Object> programs() {
		return run(() -> {
			List<Program> all = Context.getProgramWorkflowService().getAllPrograms(false);
			List<Map<String, Object>> rows = new ArrayList<>();
			for (Program prog : all) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("id",   prog.getProgramId());
				r.put("uuid", prog.getUuid());
				r.put("name", prog.getName());
				r.put("workflows", prog.getWorkflows().size());
				rows.add(r);
			}
			return Map.of("total", all.size(), "programs", rows);
		});
	}
}
