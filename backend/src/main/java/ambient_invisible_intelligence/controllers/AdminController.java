package ambient_invisible_intelligence.controllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.logic.AccessGuard;
import ambient_invisible_intelligence.logic.CommandsService;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;

// Sprint 4 contract: every admin operation authenticates via
// userSystemID / userEmail / userPassword and requires the ADMIN role;
// the exports are paginated.
@RestController
@RequestMapping(path = "/ambient-invisible-intelligence/admin", version = "1.4")
public class AdminController {

	private final UsersService usersService;
	private final ObjectsService objectsService;
	private final CommandsService commandsService;
	private final AccessGuard guard;

	public AdminController(UsersService usersService,
			ObjectsService objectsService,
			CommandsService commandsService,
			AccessGuard guard) {
		this.usersService = usersService;
		this.objectsService = objectsService;
		this.commandsService = commandsService;
		this.guard = guard;
	}

	@DeleteMapping("/users")
	public void deleteAllUsers(
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		guard.authenticateAdmin(userSystemID, userEmail, userPassword);
		usersService.deleteAllUsers();
	}

	@DeleteMapping("/objects")
	public void deleteAllObjects(
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		guard.authenticateAdmin(userSystemID, userEmail, userPassword);
		// deletes all objects including all objects' relations (the parent/child
		// id collections are part of the object rows and are removed with them)
		objectsService.deleteAllObjects();
	}

	@DeleteMapping("/commands")
	public void deleteAllCommands(
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		guard.authenticateAdmin(userSystemID, userEmail, userPassword);
		commandsService.deleteAllCommands();
	}

	@GetMapping(path = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
	public UserBoundary[] exportAllUsers(
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		guard.authenticateAdmin(userSystemID, userEmail, userPassword);
		return usersService.getAllUsers(size, page).toArray(new UserBoundary[0]);
	}

	@GetMapping(path = "/commands", produces = MediaType.APPLICATION_JSON_VALUE)
	public CommandBoundary[] exportAllCommands(
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		guard.authenticateAdmin(userSystemID, userEmail, userPassword);
		return commandsService.getAllCommandsHistory(size, page).toArray(new CommandBoundary[0]);
	}
}
