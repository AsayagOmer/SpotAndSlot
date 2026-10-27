package ambient_invisible_intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.CreatedBy;
import ambient_invisible_intelligence.boundaries.InvokedBy;
import ambient_invisible_intelligence.boundaries.Location;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;
import ambient_invisible_intelligence.boundaries.ObjectId;
import ambient_invisible_intelligence.boundaries.TargetObject;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;
import ambient_invisible_intelligence.smartparking.ObjectType;
import ambient_invisible_intelligence.smartparking.ParkingLotDetails;
import ambient_invisible_intelligence.smartparking.ParkingSectionDetails;
import ambient_invisible_intelligence.smartparking.ParkingSlotDetails;
import ambient_invisible_intelligence.smartparking.VehicleDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class SmartParkingIntegrationTests {

	@Autowired private MockMvc mvc;
	@Autowired private ObjectsService objectsService;
	@Autowired private UsersService usersService;
	@Autowired private CommandsService commandsService;
	@Value("${spring.application.name}") private String sys;

	private final ObjectMapper mapper = new ObjectMapper();
	private static final String BASE = "/ambient-invisible-intelligence";

	// ids populated by setupLot()
	private String lotId;
	private String sectionAId;
	private String sectionBId;
	private String slotA1Id; // REGULAR, FREE
	private String slotA2Id; // REGULAR, OCCUPIED
	private String slotB1Id; // HANDICAP, FREE
	private String slotB2Id; // REGULAR, FREE

	@BeforeEach
	void resetAndSeed() throws Exception {
		objectsService.deleteAllObjects();
		commandsService.deleteAllCommands();
		usersService.deleteAllUsers();

		// Sprint 4 roles: ADMIN uses only the Admin API; OPERATOR builds/owns
		// objects and reads without limit; END_USER reads active objects and
		// invokes commands.
		seedUser("admin@test.org",    "ADMIN",    "Test@1");
		seedUser("operator@test.org", "OPERATOR", "Test@2");
		seedUser("enduser@test.org",  "END_USER", "Test@3");

		setupLot();
	}

	private void seedUser(String email, String role, String password) {
		UserBoundary u = new UserBoundary();
		u.setUserId(new UserId(email, null));
		u.setRole(role);
		u.setUsername("Test " + role);
		u.setAvatar(":-]");
		usersService.createUser(u, password);
	}

	private void setupLot() throws Exception {
		lotId     = createObject(ObjectType.PARKING_LOT,     "Test Lot",  "OPEN", true,
				new ParkingLotDetails("Test City").toMap());
		sectionAId = createObject(ObjectType.PARKING_SECTION, "Section A", "OPEN", true,
				new ParkingSectionDetails(lotId).toMap());
		sectionBId = createObject(ObjectType.PARKING_SECTION, "Section B", "OPEN", true,
				new ParkingSectionDetails(lotId).toMap());
		slotA1Id = createSlot(lotId, sectionAId, "A-1", "REGULAR", "FREE");
		slotA2Id = createSlot(lotId, sectionAId, "A-2", "REGULAR", "OCCUPIED");
		slotB1Id = createSlot(lotId, sectionBId, "B-1", "HANDICAP", "FREE");
		slotB2Id = createSlot(lotId, sectionBId, "B-2", "REGULAR",  "FREE");
	}

	// objects are created by the OPERATOR who owns the lot
	private String createObject(String type, String alias, String status, boolean active,
			Map<String, Object> details) throws Exception {
		ObjectBoundary b = new ObjectBoundary();
		b.setType(type);
		b.setAlias(alias);
		b.setStatus(status);
		b.setActive(active);
		b.setObjectDetails(details);
		b.setCreatedBy(new CreatedBy(new UserId("operator@test.org", sys)));
		MvcResult r = mvc.perform(post(BASE + "/objects")
						.header("API-Version", "1.4")
						.param("userPassword", "Test@2")
						.contentType(MediaType.APPLICATION_JSON)
						.content(mapper.writeValueAsString(b)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode node = mapper.readTree(r.getResponse().getContentAsString());
		return node.get("id").get("objectId").asText();
	}

	private String createSlot(String lotId, String sectionId, String alias, String kind, String slotStatus)
			throws Exception {
		ParkingSlotDetails det = new ParkingSlotDetails(lotId, sectionId, kind, "OK", System.currentTimeMillis());
		return createObject(ObjectType.PARKING_SLOT, alias, slotStatus, true, det.toMap());
	}

	// commands are invoked by an END_USER
	private ResultActions invokeCommandResult(String command, String targetId, Map<String, Object> attrs)
			throws Exception {
		CommandBoundary cmd = new CommandBoundary();
		cmd.setCommand(command);
		cmd.setTargetObject(new TargetObject(new ObjectId(targetId, sys)));
		cmd.setInvokedBy(new InvokedBy(new UserId("enduser@test.org", sys)));
		cmd.setCommandAttributes(attrs);
		return mvc.perform(post(BASE + "/commands")
				.header("API-Version", "1.4")
				.param("userPassword", "Test@3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(cmd)));
	}

	private JsonNode invokeCommandJson(String command, String targetId, Map<String, Object> attrs)
			throws Exception {
		MvcResult r = invokeCommandResult(command, targetId, attrs)
				.andExpect(status().isOk())
				.andReturn();
		return mapper.readTree(r.getResponse().getContentAsString());
	}

	// ───────────────────────── USER TESTS ─────────────────────────

	@Nested
	@DisplayName("User API")
	class UserTests {

		@Test
		@DisplayName("POST /users creates a user and returns boundary without password")
		void createUser() throws Exception {
			String body = """
					{
						"email": "new@test.org",
						"password": "New@123",
						"role": "END_USER",
						"username": "New User",
						"avatar": ":)"
					}
					""";
			mvc.perform(post(BASE + "/users")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.userId.email").value("new@test.org"))
					.andExpect(jsonPath("$.role").value("END_USER"))
					.andExpect(jsonPath("$.username").value("New User"))
					.andExpect(jsonPath("$.password").doesNotExist());
		}

		@Test
		@DisplayName("POST /users with invalid role returns 400")
		void createUserInvalidRole() throws Exception {
			String body = """
					{
						"email": "bad@test.org",
						"password": "Bad@123",
						"role": "DRIVER",
						"username": "Bad User"
					}
					""";
			mvc.perform(post(BASE + "/users")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /users with weak password returns 400")
		void createUserWeakPassword() throws Exception {
			String body = """
					{
						"email": "weak@test.org",
						"password": "abc",
						"role": "END_USER"
					}
					""";
			mvc.perform(post(BASE + "/users")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /users with no role returns 400 (role is required)")
		void createUserNullRole() throws Exception {
			String body = """
					{
						"email": "norole@test.org",
						"password": "Pwd@1",
						"username": "No Role"
					}
					""";
			mvc.perform(post(BASE + "/users")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("GET /users/login succeeds with correct credentials")
		void loginSuccess() throws Exception {
			mvc.perform(get(BASE + "/users/login/{sys}/{email}", sys, "admin@test.org")
							.header("API-Version", "1.4")
							.param("password", "Test@1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.userId.email").value("admin@test.org"))
					.andExpect(jsonPath("$.role").value("ADMIN"));
		}

		@Test
		@DisplayName("GET /users/login fails with wrong password and returns 401")
		void loginFail() throws Exception {
			mvc.perform(get(BASE + "/users/login/{sys}/{email}", sys, "admin@test.org")
							.header("API-Version", "1.4")
							.param("password", "wrong"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("PUT /users updates user details")
		void updateUser() throws Exception {
			String body = """
					{
						"username": "Updated Admin",
						"avatar": ":-O"
					}
					""";
			mvc.perform(put(BASE + "/users/{sys}/{email}", sys, "admin@test.org")
							.header("API-Version", "1.4")
							.param("password", "Test@1")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk());

			mvc.perform(get(BASE + "/users/login/{sys}/{email}", sys, "admin@test.org")
							.header("API-Version", "1.4")
							.param("password", "Test@1"))
					.andExpect(jsonPath("$.username").value("Updated Admin"))
					.andExpect(jsonPath("$.avatar").value(":-O"));
		}

		@Test
		@DisplayName("POST /users without username returns 400")
		void createUserMissingUsername() throws Exception {
			String body = """
					{
						"email": "nouser@test.org",
						"password": "Pwd@1",
						"role": "END_USER",
						"avatar": ":)"
					}
					""";
			mvc.perform(post(BASE + "/users")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /users with blank avatar returns 400")
		void createUserBlankAvatar() throws Exception {
			String body = """
					{
						"email": "blankavatar@test.org",
						"password": "Pwd@1",
						"role": "END_USER",
						"username": "No Face",
						"avatar": ""
					}
					""";
			mvc.perform(post(BASE + "/users")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("PUT /users with blank username returns 400")
		void updateUserBlankUsername() throws Exception {
			mvc.perform(put(BASE + "/users/{sys}/{email}", sys, "admin@test.org")
							.header("API-Version", "1.4")
							.param("password", "Test@1")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"username\": \"\"}"))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("PUT /users with blank avatar returns 400")
		void updateUserBlankAvatar() throws Exception {
			mvc.perform(put(BASE + "/users/{sys}/{email}", sys, "admin@test.org")
							.header("API-Version", "1.4")
							.param("password", "Test@1")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"avatar\": \"\"}"))
					.andExpect(status().isBadRequest());
		}
	}

	// ───────────────────────── OBJECT TESTS ─────────────────────────

	@Nested
	@DisplayName("Object API")
	class ObjectTests {

		// reads use an OPERATOR, who sees every object (active or not)
		private org.springframework.test.web.servlet.ResultActions getObj(String id) throws Exception {
			return mvc.perform(get(BASE + "/objects/{sys}/{id}", sys, id)
					.header("API-Version", "1.4")
					.param("userEmail", "operator@test.org").param("userPassword", "Test@2"));
		}

		@Test
		@DisplayName("GET /objects returns all seeded objects (OPERATOR)")
		void getAllObjects() throws Exception {
			mvc.perform(get(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(7));
		}

		@Test
		@DisplayName("GET /objects by an ADMIN returns 403 (Admin API only)")
		void getAllObjectsByAdmin() throws Exception {
			mvc.perform(get(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("GET /objects/{sys}/{id} returns specific object")
		void getSpecificObject() throws Exception {
			getObj(lotId)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.type").value("PARKING_LOT"))
					.andExpect(jsonPath("$.alias").value("Test Lot"));
		}

		@Test
		@DisplayName("GET /objects/{sys}/{id} returns 404 for missing object")
		void getByIdNotFound() throws Exception {
			mvc.perform(get(BASE + "/objects/{sys}/{id}", sys, "nonexistent")
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("END_USER cannot see an inactive object (404, as if absent)")
		void endUserInactiveObjectHidden() throws Exception {
			// operator deactivates slot A-1
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, slotA1Id)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"active\": false}"))
					.andExpect(status().isOk());
			// the operator still sees it, the end user does not
			getObj(slotA1Id).andExpect(status().isOk());
			mvc.perform(get(BASE + "/objects/{sys}/{id}", sys, slotA1Id)
							.header("API-Version", "1.4")
							.param("userEmail", "enduser@test.org").param("userPassword", "Test@3"))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("PUT /objects/{sys}/{id} updates object fields (OPERATOR owner)")
		void updateObject() throws Exception {
			String body = """
					{
						"alias": "Renamed Lot",
						"status": "FULL"
					}
					""";
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk());

			getObj(lotId)
					.andExpect(jsonPath("$.alias").value("Renamed Lot"))
					.andExpect(jsonPath("$.status").value("FULL"));
		}

		@Test
		@DisplayName("PUT /objects on a lot without credentials returns 401")
		void updateObjectWithoutIdentity() throws Exception {
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, lotId)
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"alias\": \"Hijacked\"}"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("PUT /objects on a lot by a non-owner OPERATOR returns 403")
		void updateObjectByNonOwner() throws Exception {
			seedUser("other-operator@test.org", "OPERATOR", "Test@5");
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "other-operator@test.org").param("userPassword", "Test@5")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"alias\": \"Hijacked\"}"))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("PUT /objects by an END_USER returns 403 (updates need OPERATOR)")
		void updateObjectByEndUser() throws Exception {
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "enduser@test.org").param("userPassword", "Test@3")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"alias\": \"Hijacked\"}"))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("DELETE /objects on a lot cascades to its sections and slots")
		void deleteLotCascades() throws Exception {
			mvc.perform(delete(BASE + "/objects/{sys}/{id}", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(status().isOk());

			getObj(lotId).andExpect(status().isNotFound());
			getObj(sectionAId).andExpect(status().isNotFound());
			getObj(slotA1Id).andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("DELETE /objects on a section removes only its slots")
		void deleteSectionCascades() throws Exception {
			mvc.perform(delete(BASE + "/objects/{sys}/{id}", sys, sectionAId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(status().isOk());

			getObj(slotA1Id).andExpect(status().isNotFound());
			// slots in section B and the lot itself survive
			getObj(slotB1Id).andExpect(status().isOk());
			getObj(lotId).andExpect(status().isOk());
		}

		@Test
		@DisplayName("DELETE /objects by a non-owner OPERATOR returns 403")
		void deleteByNonOwner() throws Exception {
			seedUser("other-operator2@test.org", "OPERATOR", "Test@6");
			mvc.perform(delete(BASE + "/objects/{sys}/{id}", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "other-operator2@test.org").param("userPassword", "Test@6"))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("POST /objects by a non-OPERATOR returns 403")
		void createByNonOperator() throws Exception {
			ObjectBoundary b = new ObjectBoundary();
			b.setType(ObjectType.PARKING_LOT);
			b.setAlias("Rogue Lot");
			b.setStatus("OPEN");
			b.setActive(true);
			b.setObjectDetails(new ParkingLotDetails("Nowhere").toMap());
			b.setCreatedBy(new CreatedBy(new UserId("enduser@test.org", sys)));
			mvc.perform(post(BASE + "/objects")
							.param("userPassword", "Test@3")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(b)))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("POST /objects creates a PARKING_SLOT with correct details")
		void createParkingSlot() throws Exception {
			ParkingSlotDetails det = new ParkingSlotDetails(lotId, sectionAId, "REGULAR", "OK", System.currentTimeMillis());
			ObjectBoundary b = new ObjectBoundary();
			b.setType(ObjectType.PARKING_SLOT);
			b.setAlias("A-3");
			b.setStatus("FREE");
			b.setActive(true);
			b.setObjectDetails(det.toMap());
			b.setCreatedBy(new CreatedBy(new UserId("operator@test.org", sys)));

			mvc.perform(post(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(b)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.type").value("PARKING_SLOT"))
					.andExpect(jsonPath("$.alias").value("A-3"))
					.andExpect(jsonPath("$.id.objectId").isNotEmpty())
					.andExpect(jsonPath("$.objectDetails.parentLotId").value(lotId))
					.andExpect(jsonPath("$.objectDetails.slotKind").value("REGULAR"));
		}

		@Test
		@DisplayName("POST /objects with no type returns 400")
		void createObjectMissingType() throws Exception {
			ObjectBoundary b = new ObjectBoundary();
			b.setAlias("No Type");
			b.setStatus("OPEN");
			b.setActive(true);
			b.setCreatedBy(new CreatedBy(new UserId("operator@test.org", sys)));
			mvc.perform(post(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(b)))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /objects with a blank status returns 400")
		void createObjectBlankStatus() throws Exception {
			ObjectBoundary b = new ObjectBoundary();
			b.setType(ObjectType.PARKING_LOT);
			b.setAlias("No Status");
			b.setStatus("");
			b.setActive(true);
			b.setObjectDetails(new ParkingLotDetails("Nowhere").toMap());
			b.setCreatedBy(new CreatedBy(new UserId("operator@test.org", sys)));
			mvc.perform(post(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(b)))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /objects with no createdBy returns 400")
		void createObjectMissingCreatedBy() throws Exception {
			ObjectBoundary b = new ObjectBoundary();
			b.setType(ObjectType.PARKING_LOT);
			b.setAlias("Orphan Lot");
			b.setStatus("OPEN");
			b.setActive(true);
			b.setObjectDetails(new ParkingLotDetails("Nowhere").toMap());
			mvc.perform(post(BASE + "/objects")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(b)))
					.andExpect(status().isBadRequest());
		}
	}

	// ───────────────────────── COMMAND TESTS ─────────────────────────

	@Nested
	@DisplayName("Command API")
	class CommandTests {

		@Test
		@DisplayName("POST /commands executes command and stores it in history")
		void invokeCommandExecutesAndStores() throws Exception {
			JsonNode result = invokeCommandJson("availabilityInLot", lotId, Map.of());
			assertThat(result).hasSize(1);
			assertThat(result.get(0).get("freeSlots").asInt()).isGreaterThanOrEqualTo(0);

			// verify it was also persisted in history
			mvc.perform(get(BASE + "/admin/commands").header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].command").value("availabilityInLot"));
		}

		@Test
		@DisplayName("POST /commands returns 400 when invokedBy is missing")
		void invokeCommandMissingInvoker() throws Exception {
			String body = """
					{
						"command": "test",
						"targetObject": {"id": {"objectId": "1", "systemID": "sys"}}
					}
					""";
			mvc.perform(post(BASE + "/commands")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /commands returns 400 when targetObject is missing")
		void invokeCommandMissingTarget() throws Exception {
			String body = """
					{
						"command": "test",
						"invokedBy": {"userId": {"email": "enduser@test.org", "systemID": "%s"}}
					}
					""".formatted(sys);
			mvc.perform(post(BASE + "/commands")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@3")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /commands by a non-END_USER returns 403")
		void invokeCommandByNonEndUser() throws Exception {
			String body = """
					{
						"command": "availabilityInLot",
						"targetObject": {"id": {"objectId": "%s", "systemID": "%s"}},
						"invokedBy": {"userId": {"email": "operator@test.org", "systemID": "%s"}},
						"commandAttributes": {}
					}
					""".formatted(lotId, sys, sys);
			mvc.perform(post(BASE + "/commands")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isForbidden());
		}
	}

	// ─────────────────── OBJECT RELATIONSHIP TESTS ───────────────────

	@Nested
	@DisplayName("Object Relationship API")
	class RelationshipTests {

		@Test
		@DisplayName("PUT /objects/{parent}/children binds child to parent")
		void bindChild() throws Exception {
			ObjectChildIdBoundary body = new ObjectChildIdBoundary();
			body.setChildId(new ObjectId(sectionAId, sys));

			mvc.perform(put(BASE + "/objects/{sys}/{id}/children", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(body)))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("GET /objects/{parent}/children returns bound children")
		void getChildren() throws Exception {
			// bind first
			ObjectChildIdBoundary body = new ObjectChildIdBoundary();
			body.setChildId(new ObjectId(sectionAId, sys));
			mvc.perform(put(BASE + "/objects/{sys}/{id}/children", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(body)))
					.andExpect(status().isOk());

			mvc.perform(get(BASE + "/objects/{sys}/{id}/children", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].id.objectId").value(sectionAId));
		}

		@Test
		@DisplayName("GET /objects/{child}/parents returns parent after bind")
		void getParents() throws Exception {
			// bind sectionA as child of lot
			ObjectChildIdBoundary body = new ObjectChildIdBoundary();
			body.setChildId(new ObjectId(sectionAId, sys));
			mvc.perform(put(BASE + "/objects/{sys}/{id}/children", sys, lotId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(body)))
					.andExpect(status().isOk());

			mvc.perform(get(BASE + "/objects/{sys}/{id}/parents", sys, sectionAId)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].id.objectId").value(lotId));
		}
	}

	// ───────────────────── SPRINT 4 SEARCH & PAGINATION ─────────────────────

	@Nested
	@DisplayName("Sprint 4 search queries")
	class SearchTests {

		private org.springframework.test.web.servlet.ResultActions search(String path) throws Exception {
			return mvc.perform(get(BASE + path)
					.header("API-Version", "1.4")
					.param("userEmail", "operator@test.org").param("userPassword", "Test@2"));
		}

		@Test
		@DisplayName("GET /objects/search/byAlias returns exact matches only")
		void searchByAlias() throws Exception {
			search("/objects/search/byAlias/A-1")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].alias").value("A-1"));
		}

		@Test
		@DisplayName("GET /objects/search/byAliasPattern matches substrings")
		void searchByAliasPattern() throws Exception {
			// seeded aliases: Test Lot, Section A/B, A-1, A-2, B-1, B-2
			search("/objects/search/byAliasPattern/A-")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(2));
		}

		@Test
		@DisplayName("GET /objects/search/byType returns all of a type")
		void searchByType() throws Exception {
			search("/objects/search/byType/PARKING_SLOT")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(4));
		}

		@Test
		@DisplayName("GET /objects/search/byStatus returns matching slots")
		void searchByStatus() throws Exception {
			search("/objects/search/byStatus/FREE")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(3));
		}

		private void createLotAt(String alias, double lat, double lng) throws Exception {
			ObjectBoundary b = new ObjectBoundary();
			b.setType(ObjectType.PARKING_LOT);
			b.setAlias(alias);
			b.setStatus("OPEN");
			b.setActive(true);
			b.setLocation(new Location(lat, lng));
			b.setObjectDetails(new ParkingLotDetails("Somewhere").toMap());
			b.setCreatedBy(new CreatedBy(new UserId("operator@test.org", sys)));
			mvc.perform(post(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(b)))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("GET /objects/search/byLocation finds objects within the square")
		void searchByLocationSquare() throws Exception {
			createLotAt("Near Lot", 32.0, 34.0);
			createLotAt("Far Lot", 33.0, 35.0);
			search("/objects/search/byLocation/32.0/34.0/0.5")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].alias").value("Near Lot"));
		}

		@Test
		@DisplayName("byLocation converts KM distances (units=KM)")
		void searchByLocationKm() throws Exception {
			createLotAt("Near Lot", 32.0, 34.0);
			createLotAt("Far Lot", 33.0, 35.0);
			// 60 km ~ 0.54 degrees: only the near lot is inside the square
			search("/objects/search/byLocation/32.0/34.0/60?units=KM")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1));
		}

		@Test
		@DisplayName("byLocation bonus: circle excludes the square's corners")
		void searchByLocationCircle() throws Exception {
			createLotAt("Center Lot", 32.0, 34.0);
			// corner point: inside the 0.5-degree square, outside the 0.5 circle
			createLotAt("Corner Lot", 32.45, 34.45);
			search("/objects/search/byLocation/32.0/34.0/0.5?shape=SQUARE")
					.andExpect(jsonPath("$.length()").value(2));
			search("/objects/search/byLocation/32.0/34.0/0.5?shape=CIRCLE")
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].alias").value("Center Lot"));
		}

		@Test
		@DisplayName("byLocation rejects invalid units with 400")
		void searchByLocationBadUnits() throws Exception {
			search("/objects/search/byLocation/32.0/34.0/1?units=PARSEC")
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("GET /objects pagination slices deterministically")
		void paginationSlices() throws Exception {
			// 7 seeded objects: size=3 -> pages of 3 / 3 / 1
			search("/objects?size=3&page=0").andExpect(jsonPath("$.length()").value(3));
			search("/objects?size=3&page=1").andExpect(jsonPath("$.length()").value(3));
			search("/objects?size=3&page=2").andExpect(jsonPath("$.length()").value(1));
			search("/objects?size=3&page=3").andExpect(jsonPath("$.length()").value(0));
		}

		@Test
		@DisplayName("pagination rejects size=0 with 400")
		void paginationBadSize() throws Exception {
			search("/objects?size=0&page=0").andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("search requires valid credentials (401 on wrong password)")
		void searchWrongPassword() throws Exception {
			mvc.perform(get(BASE + "/objects/search/byType/PARKING_SLOT")
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "wrong"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("listing objects without credentials returns 401")
		void listWithoutCredentials() throws Exception {
			mvc.perform(get(BASE + "/objects").header("API-Version", "1.4"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("END_USER search excludes inactive objects; OPERATOR sees them")
		void endUserSearchActiveOnly() throws Exception {
			// operator deactivates the FREE slot B-2
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, slotB2Id)
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"active\": false}"))
					.andExpect(status().isOk());
			// 3 FREE slots seeded; the operator still sees all 3, the end user 2
			search("/objects/search/byStatus/FREE")
					.andExpect(jsonPath("$.length()").value(3));
			mvc.perform(get(BASE + "/objects/search/byStatus/FREE")
							.header("API-Version", "1.4")
							.param("userEmail", "enduser@test.org").param("userPassword", "Test@3"))
					.andExpect(jsonPath("$.length()").value(2));
		}
	}

	// ───────────────────────── CORS TESTS ─────────────────────────

	@Nested
	@DisplayName("CORS")
	class CorsTests {

		private void assertPreflightAllowed(String origin) throws Exception {
			mvc.perform(options(BASE + "/objects")
							.header("Origin", origin)
							.header("Access-Control-Request-Method", "POST")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk())
					.andExpect(header().string("Access-Control-Allow-Origin", origin));
		}

		@Test
		@DisplayName("preflight allows the Capacitor Android WebView origins")
		void allowsCapacitorOrigins() throws Exception {
			assertPreflightAllowed("capacitor://localhost");
			assertPreflightAllowed("https://localhost");
		}

		@Test
		@DisplayName("preflight allows the desktop admin and mobile dev servers")
		void allowsDevServerOrigins() throws Exception {
			assertPreflightAllowed("http://localhost:8090");
			assertPreflightAllowed("http://localhost:8080");
		}

		@Test
		@DisplayName("preflight allows LAN demo addresses")
		void allowsLanOrigins() throws Exception {
			assertPreflightAllowed("http://192.168.1.50:8080");
			assertPreflightAllowed("http://10.0.0.7:8080");
		}

		@Test
		@DisplayName("preflight refuses an unknown external origin")
		void refusesUnknownOrigin() throws Exception {
			mvc.perform(options(BASE + "/objects")
							.header("Origin", "https://evil.example.com")
							.header("Access-Control-Request-Method", "POST")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isForbidden());
		}
	}

	// ───────────────────────── ADMIN TESTS ─────────────────────────

	@Nested
	@DisplayName("Admin API")
	class AdminTests {

		@Test
		@DisplayName("GET /admin/users returns all users")
		void exportAllUsers() throws Exception {
			mvc.perform(get(BASE + "/admin/users")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(3));
		}

		@Test
		@DisplayName("GET /admin/commands returns command history")
		void exportAllCommands() throws Exception {
			invokeCommandJson("availabilityInLot", lotId, Map.of());

			mvc.perform(get(BASE + "/admin/commands")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].command").value("availabilityInLot"));
		}

		@Test
		@DisplayName("DELETE /admin/objects clears all objects")
		void deleteAllObjects() throws Exception {
			mvc.perform(delete(BASE + "/admin/objects")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk());

			// verify as the OPERATOR (ADMIN may not read objects)
			mvc.perform(get(BASE + "/objects")
							.header("API-Version", "1.4")
							.param("userEmail", "operator@test.org").param("userPassword", "Test@2"))
					.andExpect(jsonPath("$.length()").value(0));
		}

		@Test
		@DisplayName("DELETE /admin/users clears all users")
		void deleteAllUsers() throws Exception {
			mvc.perform(delete(BASE + "/admin/users")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk());

			// the acting admin was deleted with everyone else, so its credentials
			// no longer authenticate — and the store is verifiably empty
			mvc.perform(get(BASE + "/admin/users")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isUnauthorized());
			assertThat(usersService.getAllUsers(10, 0)).isEmpty();
		}

		@Test
		@DisplayName("DELETE /admin/commands clears command history")
		void deleteAllCommands() throws Exception {
			invokeCommandJson("availabilityInLot", lotId, Map.of());

			mvc.perform(delete(BASE + "/admin/commands")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(status().isOk());

			mvc.perform(get(BASE + "/admin/commands")
							.header("API-Version", "1.4")
							.param("userEmail", "admin@test.org").param("userPassword", "Test@1"))
					.andExpect(jsonPath("$.length()").value(0));
		}
	}

	// ───────────────────── PARKING_LOT COMMANDS ─────────────────────

	@Nested
	@DisplayName("Parking Lot Commands")
	class LotCommandTests {

		@Test
		@DisplayName("availabilityInLot returns free count and worthEntering flag")
		void availabilityInLot() throws Exception {
			JsonNode result = invokeCommandJson("availabilityInLot", lotId, Map.of()).get(0);
			assertThat(result.get("freeSlots").asInt()).isEqualTo(3);
			assertThat(result.get("searchingDrivers").asInt()).isEqualTo(0);
			assertThat(result.get("worthEntering").asBoolean()).isTrue();
		}

		@Test
		@DisplayName("availabilityInLot with slotKindFilter=HANDICAP")
		void availabilityInLotFiltered() throws Exception {
			JsonNode result = invokeCommandJson("availabilityInLot", lotId,
					Map.of("slotKindFilter", "HANDICAP")).get(0);
			assertThat(result.get("freeSlots").asInt()).isEqualTo(1);
		}

		@Test
		@DisplayName("bestSectionInLot returns section with most free slots")
		void bestSectionInLot() throws Exception {
			JsonNode result = invokeCommandJson("bestSectionInLot", lotId, Map.of()).get(0);
			assertThat(result.get("bestSectionId").asText()).isEqualTo(sectionBId);
			assertThat(result.get("freeSlots").asInt()).isEqualTo(2);
		}

		@Test
		@DisplayName("predictAvailability returns a prediction result with required fields")
		void predictAvailability() throws Exception {
			long future = System.currentTimeMillis() + 3600_000;
			JsonNode result = invokeCommandJson("predictAvailability", lotId,
					Map.of("targetTimestamp", future)).get(0);
			assertThat(result.get("predictedFreeSlots").asInt()).isGreaterThanOrEqualTo(0);
			assertThat(result.get("note").asText()).isNotEmpty();
		}

		@Test
		@DisplayName("runHealthCheck returns empty list when all slots are OK")
		void runHealthCheckAllOk() throws Exception {
			JsonNode arr = invokeCommandJson("runHealthCheck", lotId, Map.of());
			assertThat(arr).isEmpty();
		}

		@Test
		@DisplayName("runHealthCheck detects FAULTY slots")
		void runHealthCheckFaulty() throws Exception {
			ObjectBoundary current = objectsService.getSpecificObject(sys, slotA1Id).orElseThrow();
			ParkingSlotDetails det = ParkingSlotDetails.from(current.getObjectDetails());
			ObjectBoundary update = new ObjectBoundary();
			update.setObjectDetails(det.withHealth("FAULTY", det.lastHeartbeat()).toMap());
			objectsService.updateObject(sys, slotA1Id, update);

			JsonNode arr = invokeCommandJson("runHealthCheck", lotId, Map.of());
			assertThat(arr).hasSize(1);
			assertThat(arr.get(0).get("slotId").asText()).isEqualTo(slotA1Id);
			assertThat(arr.get(0).get("healthStatus").asText()).isEqualTo("FAULTY");
		}
	}

	// ───────────────────── PARKING_SECTION COMMANDS ─────────────────────

	@Nested
	@DisplayName("Parking Section Commands")
	class SectionCommandTests {

		@Test
		@DisplayName("availabilityInSection returns free and total slots for section A")
		void availabilityInSectionA() throws Exception {
			JsonNode result = invokeCommandJson("availabilityInSection", sectionAId, Map.of()).get(0);
			assertThat(result.get("freeSlots").asInt()).isEqualTo(1);
			assertThat(result.get("totalSlots").asInt()).isEqualTo(2);
		}

		@Test
		@DisplayName("availabilityInSection returns free and total slots for section B")
		void availabilityInSectionB() throws Exception {
			JsonNode result = invokeCommandJson("availabilityInSection", sectionBId, Map.of()).get(0);
			assertThat(result.get("freeSlots").asInt()).isEqualTo(2);
			assertThat(result.get("totalSlots").asInt()).isEqualTo(2);
		}
	}

	// ───────────────────── PARKING_SLOT COMMANDS ─────────────────────

	@Nested
	@DisplayName("Parking Slot Commands")
	class SlotCommandTests {

		@Test
		@DisplayName("checkSlotAvailability returns correct status for FREE slot")
		void checkSlotFree() throws Exception {
			JsonNode result = invokeCommandJson("checkSlotAvailability", slotA1Id, Map.of()).get(0);
			assertThat(result.get("status").asText()).isEqualTo("FREE");
			assertThat(result.get("isFree").asBoolean()).isTrue();
			assertThat(result.get("slotKind").asText()).isEqualTo("REGULAR");
			assertThat(result.get("healthStatus").asText()).isEqualTo("OK");
		}

		@Test
		@DisplayName("checkSlotAvailability returns OCCUPIED for occupied slot")
		void checkSlotOccupied() throws Exception {
			JsonNode result = invokeCommandJson("checkSlotAvailability", slotA2Id, Map.of()).get(0);
			assertThat(result.get("status").asText()).isEqualTo("OCCUPIED");
			assertThat(result.get("isFree").asBoolean()).isFalse();
		}

		@Test
		@DisplayName("updateSlotState changes slot status and refreshes heartbeat")
		void updateSlotState() throws Exception {
			JsonNode result = invokeCommandJson("updateSlotState", slotA1Id,
					Map.of("newState", "OCCUPIED")).get(0);
			assertThat(result.get("slotId").asText()).isEqualTo(slotA1Id);
			assertThat(result.get("newState").asText()).isEqualTo("OCCUPIED");

			JsonNode updated = invokeCommandJson("checkSlotAvailability", slotA1Id, Map.of()).get(0);
			assertThat(updated.get("status").asText()).isEqualTo("OCCUPIED");
			assertThat(updated.get("isFree").asBoolean()).isFalse();
		}

		@Test
		@DisplayName("updateSlotState without newState returns 400")
		void updateSlotStateMissing() throws Exception {
			invokeCommandResult("updateSlotState", slotA1Id, Map.of())
					.andExpect(status().isBadRequest());
		}
	}

	// ───────────────────── VEHICLE COMMANDS ─────────────────────

	@Nested
	@DisplayName("Vehicle Commands")
	class VehicleCommandTests {

		private String createVehicle() throws Exception {
			ObjectBoundary v = new ObjectBoundary();
			v.setType(ObjectType.VEHICLE);
			v.setAlias("test vehicle");
			v.setStatus("SEARCHING");
			v.setActive(true);
			v.setObjectDetails(new VehicleDetails(lotId, "operator@test.org", null, "ABC-123").toMap());
			v.setCreatedBy(new CreatedBy(new UserId("operator@test.org", sys)));
			MvcResult r = mvc.perform(post(BASE + "/objects")
							.param("userPassword", "Test@2")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(v)))
					.andExpect(status().isOk())
					.andReturn();
			return mapper.readTree(r.getResponse().getContentAsString()).get("id").get("objectId").asText();
		}

		@Test
		@DisplayName("vehicleEnters creates a VEHICLE object with PARKED status")
		void vehicleEnters() throws Exception {
			String vehicleId = createVehicle();
			JsonNode result = invokeCommandJson("vehicleEnters", vehicleId,
					Map.of("lotId", lotId, "plate", "TEST-001")).get(0);
			assertThat(result.get("type").asText()).isEqualTo("VEHICLE");
			assertThat(result.get("status").asText()).isEqualTo("PARKED");
		}

		@Test
		@DisplayName("vehicleExits creates a VEHICLE object with LEAVING status")
		void vehicleExits() throws Exception {
			String vehicleId = createVehicle();
			JsonNode result = invokeCommandJson("vehicleExits", vehicleId,
					Map.of("lotId", lotId, "plate", "TEST-001")).get(0);
			assertThat(result.get("status").asText()).isEqualTo("LEAVING");
		}

		@Test
		@DisplayName("reportSession creates a VEHICLE event record")
		void reportSession() throws Exception {
			String vehicleId = createVehicle();
			JsonNode result = invokeCommandJson("reportSession", vehicleId,
					Map.of("lotId", lotId, "state", "SEARCHING")).get(0);
			assertThat(result.get("type").asText()).isEqualTo("VEHICLE");
			assertThat(result.get("status").asText()).isEqualTo("SEARCHING");
		}
	}

	// ───────────────────── COMMAND TYPE VALIDATION ─────────────────────

	@Nested
	@DisplayName("Command-Type Validation")
	class CommandTypeValidationTests {

		@Test
		@DisplayName("lot command on a section returns 400")
		void lotCommandOnSection() throws Exception {
			invokeCommandResult("availabilityInLot", sectionAId, Map.of())
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("section command on a lot returns 400")
		void sectionCommandOnLot() throws Exception {
			invokeCommandResult("availabilityInSection", lotId, Map.of())
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("slot command on a lot returns 400")
		void slotCommandOnLot() throws Exception {
			invokeCommandResult("checkSlotAvailability", lotId, Map.of())
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("vehicle command on a slot returns 400")
		void vehicleCommandOnSlot() throws Exception {
			invokeCommandResult("vehicleEnters", slotA1Id, Map.of("lotId", lotId))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("POST /users creating ADMIN with wrong actingPassword returns 401")
		void createAdminWithWrongActingPasswordRejected() throws Exception {
			String body = """
					{
						"email": "admin2@test.org",
						"password": "Admin@2",
						"role": "ADMIN",
						"username": "Admin 2",
						"avatar": ":)"
					}
					""";
			mvc.perform(post(BASE + "/users")
							.param("actingEmail", "admin@test.org")
							.param("actingPassword", "WrongPass@1")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("predictAvailability excludes inactive slots from fallback count")
		void predictAvailabilityExcludesInactiveSlots() throws Exception {
			ObjectBoundary deactivate = new ObjectBoundary();
			deactivate.setActive(false);
			mvc.perform(put(BASE + "/objects/{sys}/{id}", sys, slotA1Id)
							.param("userSystemID", sys)
							.param("userEmail", "operator@test.org")
							.param("userPassword", "Test@2")
							.header("API-Version", "1.4")
							.contentType(MediaType.APPLICATION_JSON)
							.content(mapper.writeValueAsString(deactivate)))
					.andExpect(status().isOk());

			JsonNode result = invokeCommandJson("predictAvailability", lotId, Map.of()).get(0);
			assertThat(result.get("predictedFreeSlots").asLong()).isEqualTo(2L);
		}
	}
}