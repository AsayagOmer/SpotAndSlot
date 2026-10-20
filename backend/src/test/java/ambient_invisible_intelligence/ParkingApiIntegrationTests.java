package ambient_invisible_intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.CreatedBy;
import ambient_invisible_intelligence.boundaries.InvokedBy;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectId;
import ambient_invisible_intelligence.boundaries.TargetObject;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;
import ambient_invisible_intelligence.logic.UsersService;
import ambient_invisible_intelligence.rest.AdminApi;
import ambient_invisible_intelligence.rest.CommandsApi;
import ambient_invisible_intelligence.rest.ObjectsApi;
import ambient_invisible_intelligence.rest.UsersApi;
import ambient_invisible_intelligence.smartparking.ObjectType;
import ambient_invisible_intelligence.smartparking.ParkingLotDetails;

/**
 * Black-box integration tests that exercise the running server over real HTTP,
 * using declarative HTTP-interface clients and a Testcontainers-managed MongoDB.
 * Follows the course reference style: Given/When/Then, AssertJ recursive comparison,
 * and 4xx assertions for invalid input.
 */
@Import(TestContainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@DisplayName("Parking API — HTTP integration tests")
class ParkingApiIntegrationTests {

	private ObjectsApi objectsApi;
	private CommandsApi commandsApi;
	private UsersApi usersApi;
	private AdminApi adminApi;
	private RecursiveComparisonConfiguration config;

	@Value("${spring.application.name}")
	private String sys;

	// Direct service access to seed the acting admin (object creation for parking
	// types is guarded by AccessGuard and requires an existing ADMIN).
	@Autowired
	private UsersService usersService;

	@Autowired
	public void setApis(ObjectsApi objectsApi, CommandsApi commandsApi,
			UsersApi usersApi, AdminApi adminApi) {
		this.objectsApi = objectsApi;
		this.commandsApi = commandsApi;
		this.usersApi = usersApi;
		this.adminApi = adminApi;
		// tolerate sub-second rounding when comparing Date fields after JSON round-trips
		this.config = new RecursiveComparisonConfiguration();
		this.config.registerComparatorForType(
				(Date d1, Date d2) -> Long.compare(d1.getTime() / 1000L, d2.getTime() / 1000L),
				Date.class);
	}

	@BeforeEach
	@AfterEach
	public void cleanup() {
		// users go last: deleting them revokes the admin credentials used here
		this.adminApi.deleteAllObjects("admin@demo.org", "Admin@1");
		this.adminApi.deleteAllCommands("admin@demo.org", "Admin@1");
		this.adminApi.deleteAllUsers("admin@demo.org", "Admin@1");
		// re-seed the three roles used by these tests: ADMIN for the Admin API,
		// OPERATOR to build/own objects, END_USER to invoke commands
		seed("admin@demo.org",    "ADMIN",    "Admin@1");
		seed("operator@demo.org", "OPERATOR", "Oper@1");
		seed("enduser@demo.org",  "END_USER", "User@1");
	}

	private void seed(String email, String role, String password) {
		UserBoundary u = new UserBoundary();
		u.setUserId(new UserId(email, null));
		u.setRole(role);
		u.setUsername("Test " + role);
		u.setAvatar(":-]");
		this.usersService.createUser(u, password);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private ObjectBoundary newLot(String alias) {
		ObjectBoundary b = new ObjectBoundary();
		b.setType(ObjectType.PARKING_LOT);
		b.setAlias(alias);
		b.setStatus("OPEN");
		b.setActive(true);
		b.setObjectDetails(new ParkingLotDetails("Tel Aviv").toMap());
		b.setCreatedBy(new CreatedBy(new UserId("operator@demo.org", sys)));
		return b;
	}

	private CommandBoundary command(String name, String targetId, UserId invoker) {
		CommandBoundary cmd = new CommandBoundary();
		cmd.setCommand(name);
		cmd.setTargetObject(new TargetObject(new ObjectId(targetId, sys)));
		if (invoker != null) cmd.setInvokedBy(new InvokedBy(invoker));
		cmd.setCommandAttributes(Map.of());
		return cmd;
	}

	// ── happy paths ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("create an object and fetch it back by id")
	public void testCreateObjectAndGetItById() {
		// Given: the server is up with an empty database

		// When: I POST a new PARKING_LOT
		ObjectBoundary created = this.objectsApi.create(newLot("Lot A"), "Oper@1");

		// Then: it can be fetched back by its generated id, unchanged
		assertThat(this.objectsApi.getById(created.getId().getSystemID(), created.getId().getObjectId(), "operator@demo.org", "Oper@1"))
				.usingRecursiveComparison(this.config)
				.isEqualTo(created);
	}

	@Test
	@DisplayName("create several objects and get them all")
	public void testCreateManyObjectsAndGetThemAll() {
		// Given: the server contains 5 lots
		List<ObjectBoundary> expected = IntStream.range(0, 5)
				.mapToObj(i -> this.objectsApi.create(newLot("Lot #" + i), "Oper@1"))
				.toList();

		// When: I GET all objects
		// Then: all created lots are returned
		assertThat(this.objectsApi.getAll("operator@demo.org", "Oper@1", 100, 0))
				.usingRecursiveFieldByFieldElementComparator(this.config)
				.containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	@DisplayName("invoking a command returns a result and stores it in history")
	public void testInvokeCommandAndCheckHistory() {
		// Given: a lot exists
		ObjectBoundary lot = this.objectsApi.create(newLot("Lot A"), "Oper@1");

		// When: I invoke a lot-level command with a valid invoker
		Object[] result = this.commandsApi.invoke(
				command("availabilityInLot", lot.getId().getObjectId(),
						new UserId("enduser@demo.org", sys)), "User@1");

		// Then: a result is returned and the command is persisted in history
		assertThat(result).hasSize(1);
		assertThat(this.adminApi.exportCommands("admin@demo.org", "Admin@1", 100, 0)).hasSize(1);
	}

	// ── unexpected input / error handling ──────────────────────────────────────

	@Test
	@DisplayName("posting a command without invokedBy fails with a 4xx status")
	public void testCommandWithoutInvokerFailsWith4xx() {
		// Given: a lot exists
		ObjectBoundary lot = this.objectsApi.create(newLot("Lot A"), "Oper@1");

		// When: I post a command with no invokedBy identity
		// Then: the server rejects it with a client error (4xx)
		assertThatThrownBy(() -> this.commandsApi.invoke(
						command("availabilityInLot", lot.getId().getObjectId(), null), "User@1"))
				.isInstanceOf(Exception.class)
				.extracting("statusCode.4xxClientError")
				.isEqualTo(true);
	}

	@Test
	@DisplayName("fetching a non-existent object fails with a 4xx status")
	public void testGetMissingObjectFailsWith4xx() {
		// Given: an empty database
		// When: I GET an object id that does not exist
		// Then: the server responds with a client error (404)
		assertThatThrownBy(() -> this.objectsApi.getById(sys, "does-not-exist", "operator@demo.org", "Oper@1"))
				.isInstanceOf(Exception.class)
				.extracting("statusCode.4xxClientError")
				.isEqualTo(true);
	}

	@Test
	@DisplayName("creating a user with a weak password fails with a 4xx status")
	public void testCreateUserWeakPasswordFailsWith4xx() {
		// Given: the server is up
		UserWithPasswordBoundary u = new UserWithPasswordBoundary();
		u.setEmail("weak@demo.org");
		u.setPassword("abc"); // too short, no digit / special char
		u.setRole("END_USER");
		u.setUsername("Weak");
		u.setAvatar(":(");

		// When/Then: creating the user is rejected with a client error
		assertThatThrownBy(() -> this.usersApi.create(u))
				.isInstanceOf(Exception.class)
				.extracting("statusCode.4xxClientError")
				.isEqualTo(true);
	}

	@Test
	@DisplayName("login with a wrong password fails with a 4xx status")
	public void testLoginWrongPasswordFailsWith4xx() {
		// Given: a registered user
		UserWithPasswordBoundary u = new UserWithPasswordBoundary();
		u.setEmail("user@demo.org");
		u.setPassword("Secret@1");
		u.setRole("END_USER");
		u.setUsername("User");
		u.setAvatar(":)");
		this.usersApi.create(u);

		// When/Then: logging in with the wrong password is rejected (401)
		assertThatThrownBy(() -> this.usersApi.login(sys, "user@demo.org", "wrong"))
				.isInstanceOf(Exception.class)
				.extracting("statusCode.4xxClientError")
				.isEqualTo(true);
	}
}
