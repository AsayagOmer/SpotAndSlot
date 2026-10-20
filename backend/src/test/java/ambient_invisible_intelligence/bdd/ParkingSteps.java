package ambient_invisible_intelligence.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for the Gherkin features in src/test/resources/features.
 * Scenarios exercise the REST API over MockMvc (black-box), with direct
 * service access used only to seed users and wipe state between scenarios.
 */
public class ParkingSteps {

	private static final String BASE = "/ambient-invisible-intelligence";
	private static final String API_VERSION = "1.4";

	@Autowired private MockMvc mvc;
	@Autowired private UsersService usersService;
	@Autowired private ObjectsService objectsService;
	@Autowired private CommandsService commandsService;
	@Value("${spring.application.name}") private String sys;

	private final ObjectMapper mapper = new ObjectMapper();

	// scenario state
	private int lastStatus;
	private String lastBody;
	private String lotId;
	private String sectionId;
	private final Map<String, String> slotIds = new HashMap<>();
	// email -> password, so steps can attach the Sprint 4 credentials
	private final Map<String, String> passwords = new HashMap<>();
	// the END_USER used to invoke commands (set when an end user is seeded)
	private String commandInvoker = "driver@bdd.org";

	@Before
	public void resetSystem() {
		objectsService.deleteAllObjects();
		commandsService.deleteAllCommands();
		usersService.deleteAllUsers();
		lastStatus = 0;
		lastBody = null;
		lotId = null;
		sectionId = null;
		slotIds.clear();
		passwords.clear();
		commandInvoker = "driver@bdd.org";
	}

	// ─── users & auth ─────────────────────────────────────────────────────────

	@Given("an administrator {string} with password {string} exists")
	public void adminExists(String email, String password) {
		seedUser(email, password, "ADMIN");
	}

	@Given("an end user {string} with password {string} exists")
	public void endUserExists(String email, String password) {
		seedUser(email, password, "END_USER");
		commandInvoker = email; // commands are invoked by an END_USER
	}

	@Given("an operator {string} with password {string} exists")
	public void operatorExists(String email, String password) {
		seedUser(email, password, "OPERATOR");
	}

	@When("a visitor signs up with email {string}, password {string} and role {string}")
	public void visitorSignsUp(String email, String password, String role) throws Exception {
		perform(post(BASE + "/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(userJson(email, password, role)));
	}

	@When("{string} creates a user {string} with role {string} and password {string}")
	public void createsUserAs(String actingEmail, String email, String role, String password) throws Exception {
		perform(post(BASE + "/users")
				.param("actingEmail", actingEmail)
				.contentType(MediaType.APPLICATION_JSON)
				.content(userJson(email, password, role)));
	}

	@When("a visitor signs up with email {string} and password {string} but no username")
	public void visitorSignsUpNoUsername(String email, String password) throws Exception {
		perform(post(BASE + "/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s", "role": "END_USER", "avatar": ":-)"}
						""".formatted(email, password)));
	}

	@When("a visitor signs up with email {string} and password {string} but a blank avatar")
	public void visitorSignsUpBlankAvatar(String email, String password) throws Exception {
		perform(post(BASE + "/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s", "role": "END_USER", "username": "BDD User", "avatar": ""}
						""".formatted(email, password)));
	}

	@When("the user {string} logs in with password {string}")
	public void logsIn(String email, String password) throws Exception {
		perform(get(BASE + "/users/login/{sys}/{email}", sys, email).param("password", password));
	}

	@Then("the user {string} can log in with password {string}")
	public void canLogIn(String email, String password) throws Exception {
		logsIn(email, password);
		assertThat(lastStatus).isEqualTo(200);
	}

	@Then("the logged-in user has role {string}")
	public void loggedInUserHasRole(String role) throws Exception {
		assertThat(mapper.readTree(lastBody).get("role").asText()).isEqualTo(role);
	}

	// ─── generic assertions ───────────────────────────────────────────────────

	@Then("the request succeeds")
	public void requestSucceeds() {
		assertThat(lastStatus).isBetween(200, 299);
	}

	@Then("the request is rejected with status {int}")
	public void requestRejected(int status) {
		assertThat(lastStatus).isEqualTo(status);
	}

	// ─── lots, sections, slots ────────────────────────────────────────────────

	@When("{string} creates a parking lot named {string} in {string}")
	public void createsLot(String actingEmail, String alias, String city) throws Exception {
		perform(post(BASE + "/objects")
				.param("userPassword", passwords.getOrDefault(actingEmail, ""))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type": "PARKING_LOT",
						  "alias": "%s",
						  "status": "OPEN",
						  "active": true,
						  "createdBy": {"userId": {"email": "%s", "systemID": "%s"}},
						  "objectDetails": {"address": "%s"}
						}
						""".formatted(alias, actingEmail, sys, city)));
		if (lastStatus == 200) lotId = mapper.readTree(lastBody).get("id").get("objectId").asText();
	}

	@When("{string} adds a section {string} to the lot")
	public void addsSection(String actingEmail, String alias) throws Exception {
		perform(post(BASE + "/objects")
				.param("userPassword", passwords.getOrDefault(actingEmail, ""))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type": "PARKING_SECTION",
						  "alias": "%s",
						  "status": "OPEN",
						  "active": true,
						  "createdBy": {"userId": {"email": "%s", "systemID": "%s"}},
						  "objectDetails": {"parentLotId": "%s"}
						}
						""".formatted(alias, actingEmail, sys, lotId)));
		if (lastStatus == 200) sectionId = mapper.readTree(lastBody).get("id").get("objectId").asText();
	}

	@When("{string} adds a {string} slot {string} with status {string} to the section")
	public void addsSlot(String actingEmail, String kind, String alias, String status) throws Exception {
		perform(post(BASE + "/objects")
				.param("userPassword", passwords.getOrDefault(actingEmail, ""))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type": "PARKING_SLOT",
						  "alias": "%s",
						  "status": "%s",
						  "active": true,
						  "createdBy": {"userId": {"email": "%s", "systemID": "%s"}},
						  "objectDetails": {
						    "parentLotId": "%s",
						    "parentSectionId": "%s",
						    "slotKind": "%s",
						    "healthStatus": "OK"
						  }
						}
						""".formatted(alias, status, actingEmail, sys, lotId, sectionId, kind)));
		if (lastStatus == 200) slotIds.put(alias, mapper.readTree(lastBody).get("id").get("objectId").asText());
	}

	@When("{string} creates a parking lot named {string} with a blank status")
	public void createsLotWithBlankStatus(String actingEmail, String alias) throws Exception {
		perform(post(BASE + "/objects")
				.param("userPassword", passwords.getOrDefault(actingEmail, ""))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type": "PARKING_LOT",
						  "alias": "%s",
						  "status": "",
						  "active": true,
						  "createdBy": {"userId": {"email": "%s", "systemID": "%s"}},
						  "objectDetails": {}
						}
						""".formatted(alias, actingEmail, sys)));
	}

	@When("a blank command is invoked on the lot")
	public void invokeBlankCommand() throws Exception {
		invokeCommand("", lotId, "{}", true);
	}

	@When("{string} renames the lot to {string}")
	public void renamesLot(String actingEmail, String newAlias) throws Exception {
		perform(put(BASE + "/objects/{sys}/{id}", sys, lotId)
				.param("userEmail", actingEmail)
				.param("userPassword", passwords.getOrDefault(actingEmail, ""))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"alias\": \"" + newAlias + "\"}"));
	}

	@When("{string} deletes the lot")
	public void deletesLot(String actingEmail) throws Exception {
		perform(delete(BASE + "/objects/{sys}/{id}", sys, lotId)
				.param("userEmail", actingEmail)
				.param("userPassword", passwords.getOrDefault(actingEmail, "")));
	}

	@Then("the lot is named {string}")
	public void lotIsNamed(String alias) throws Exception {
		perform(get(BASE + "/objects/{sys}/{id}", sys, lotId).param("userEmail", "owner@bdd.org").param("userPassword", passwords.getOrDefault("owner@bdd.org", "")));
		assertThat(lastStatus).isEqualTo(200);
		assertThat(mapper.readTree(lastBody).get("alias").asText()).isEqualTo(alias);
	}

	@Then("the lot has {int} section(s) and {int} slot(s)")
	public void lotHasChildren(int sections, int slots) throws Exception {
		perform(get(BASE + "/objects")
				.param("userEmail", "owner@bdd.org")
				.param("userPassword", passwords.getOrDefault("owner@bdd.org", ""))
				.param("size", "200"));
		assertThat(lastStatus).isEqualTo(200);
		int foundSections = 0, foundSlots = 0;
		for (JsonNode o : mapper.readTree(lastBody)) {
			JsonNode det = o.get("objectDetails");
			boolean inLot = det != null && det.hasNonNull("parentLotId")
					&& lotId.equals(det.get("parentLotId").asText());
			if (!inLot) continue;
			if ("PARKING_SECTION".equals(o.get("type").asText())) foundSections++;
			if ("PARKING_SLOT".equals(o.get("type").asText())) foundSlots++;
		}
		assertThat(foundSections).isEqualTo(sections);
		assertThat(foundSlots).isEqualTo(slots);
	}

	@Then("the lot no longer exists")
	public void lotGone() throws Exception {
		perform(get(BASE + "/objects/{sys}/{id}", sys, lotId).param("userEmail", "owner@bdd.org").param("userPassword", passwords.getOrDefault("owner@bdd.org", "")));
		assertThat(lastStatus).isEqualTo(404);
	}

	@Then("the section no longer exists")
	public void sectionGone() throws Exception {
		perform(get(BASE + "/objects/{sys}/{id}", sys, sectionId).param("userEmail", "owner@bdd.org").param("userPassword", passwords.getOrDefault("owner@bdd.org", "")));
		assertThat(lastStatus).isEqualTo(404);
	}

	@Then("the slot {string} no longer exists")
	public void slotGone(String alias) throws Exception {
		perform(get(BASE + "/objects/{sys}/{id}", sys, slotIds.get(alias)).param("userEmail", "owner@bdd.org").param("userPassword", passwords.getOrDefault("owner@bdd.org", "")));
		assertThat(lastStatus).isEqualTo(404);
	}

	// ─── commands ─────────────────────────────────────────────────────────────

	@When("the command {string} is invoked on the lot")
	public void invokeOnLot(String command) throws Exception {
		invokeCommand(command, lotId, "{}", true);
	}

	@When("the command {string} with filter {string} is invoked on the lot")
	public void invokeOnLotWithFilter(String command, String filter) throws Exception {
		invokeCommand(command, lotId, "{\"slotKindFilter\": \"" + filter + "\"}", true);
	}

	@When("the command {string} with new state {string} is invoked on slot {string}")
	public void invokeOnSlot(String command, String newState, String alias) throws Exception {
		invokeCommand(command, slotIds.get(alias), "{\"newState\": \"" + newState + "\"}", true);
	}

	@When("the command {string} with new state {string} is invoked on the lot")
	public void invokeSlotCommandOnLot(String command, String newState) throws Exception {
		invokeCommand(command, lotId, "{\"newState\": \"" + newState + "\"}", true);
	}

	@When("the command {string} is invoked on the lot without an invoker")
	public void invokeWithoutInvoker(String command) throws Exception {
		invokeCommand(command, lotId, "{}", false);
	}

	@Then("the availability result reports {int} free slot(s)")
	public void availabilityReports(int free) throws Exception {
		assertThat(lastStatus).isEqualTo(200);
		JsonNode result = mapper.readTree(lastBody);
		assertThat(result.isArray()).isTrue();
		assertThat(result.get(0).get("freeSlots").asLong()).isEqualTo(free);
	}

	@Then("slot {string} has status {string}")
	public void slotHasStatus(String alias, String status) throws Exception {
		perform(get(BASE + "/objects/{sys}/{id}", sys, slotIds.get(alias)).param("userEmail", "owner@bdd.org").param("userPassword", passwords.getOrDefault("owner@bdd.org", "")));
		assertThat(lastStatus).isEqualTo(200);
		assertThat(mapper.readTree(lastBody).get("status").asText()).isEqualTo(status);
	}

	// ─── Sprint 4 search queries ──────────────────────────────────────────────

	private void search(String email, String path, String password, Integer size) throws Exception {
		var req = get(BASE + path)
				.param("userEmail", email)
				.param("userPassword", password != null ? password : passwords.getOrDefault(email, ""));
		if (size != null) req = req.param("size", String.valueOf(size));
		perform(req);
	}

	@When("{string} searches objects by type {string}")
	public void searchesByType(String email, String type) throws Exception {
		search(email, "/objects/search/byType/" + type, null, null);
	}

	@When("{string} searches objects by alias {string}")
	public void searchesByAlias(String email, String alias) throws Exception {
		search(email, "/objects/search/byAlias/" + alias, null, null);
	}

	@When("{string} searches objects by alias pattern {string}")
	public void searchesByAliasPattern(String email, String pattern) throws Exception {
		search(email, "/objects/search/byAliasPattern/" + pattern, null, null);
	}

	@When("{string} searches objects by status {string}")
	public void searchesByStatus(String email, String status) throws Exception {
		search(email, "/objects/search/byStatus/" + status, null, null);
	}

	@When("{string} searches objects by type {string} with page size {int}")
	public void searchesByTypePaged(String email, String type, int size) throws Exception {
		search(email, "/objects/search/byType/" + type, null, size);
	}

	@When("{string} searches objects by type {string} with a wrong password")
	public void searchesWithWrongPassword(String email, String type) throws Exception {
		search(email, "/objects/search/byType/" + type, "definitely-wrong", null);
	}

	@Then("the search returns {int} object(s)")
	public void searchReturns(int count) throws Exception {
		assertThat(lastStatus).isEqualTo(200);
		assertThat(mapper.readTree(lastBody).size()).isEqualTo(count);
	}

	// ─── helpers ──────────────────────────────────────────────────────────────

	private void seedUser(String email, String password, String role) {
		passwords.put(email, password);
		UserBoundary user = new UserBoundary();
		user.setUserId(new UserId(email, null));
		user.setRole(role);
		user.setUsername("BDD " + role);
		user.setAvatar(":-)");
		usersService.createUser(user, password);
	}

	private String userJson(String email, String password, String role) {
		return """
				{
				  "email": "%s",
				  "password": "%s",
				  "role": "%s",
				  "username": "BDD User",
				  "avatar": ":-)"
				}
				""".formatted(email, password, role);
	}

	private void invokeCommand(String command, String targetId, String attrs, boolean withInvoker) throws Exception {
		String invokedBy = withInvoker
				? "\"invokedBy\": {\"userId\": {\"email\": \"" + commandInvoker + "\", \"systemID\": \"" + sys + "\"}},"
				: "";
		perform(post(BASE + "/commands")
				.param("userPassword", passwords.getOrDefault(commandInvoker, ""))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "command": "%s",
						  "targetObject": {"id": {"objectId": "%s", "systemID": "%s"}},
						  %s
						  "commandAttributes": %s
						}
						""".formatted(command, targetId, sys, invokedBy, attrs)));
	}

	private void perform(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult result = mvc.perform(request.header("API-Version", API_VERSION)).andReturn();
		lastStatus = result.getResponse().getStatus();
		lastBody = result.getResponse().getContentAsString();
	}
}
