package ambient_invisible_intelligence.rest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;

// Declarative HTTP client for the /admin endpoints, used by integration tests.
// Sprint 4: every operation authenticates an ADMIN; exports are paginated.
public interface AdminApi {

	@DeleteExchange(url = "/admin/objects")
	void deleteAllObjects(
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@DeleteExchange(url = "/admin/users")
	void deleteAllUsers(
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@DeleteExchange(url = "/admin/commands")
	void deleteAllCommands(
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(url = "/admin/users", accept = MediaType.APPLICATION_JSON_VALUE)
	UserBoundary[] exportUsers(
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(url = "/admin/commands", accept = MediaType.APPLICATION_JSON_VALUE)
	CommandBoundary[] exportCommands(
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);
}
