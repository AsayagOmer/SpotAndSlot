package ambient_invisible_intelligence.controllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.logic.AccessGuard;
import ambient_invisible_intelligence.logic.CommandsService;

@RestController
@RequestMapping(path = "/ambient-invisible-intelligence/commands", version = "1.4")
public class CommandController {

	private final CommandsService commandsService;
	private final AccessGuard guard;

	public CommandController(CommandsService commandsService, AccessGuard guard) {
		this.commandsService = commandsService;
		this.guard = guard;
	}

	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public Object[] invokeCommand(
			@RequestBody CommandBoundary input,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		// Sprint 4: only END_USER accounts may invoke commands, and the invoker's
		// identity (invokedBy) is verified with the userPassword parameter. A
		// missing/blank invokedBy is left to the service (400). The service also
		// checks that the target object exists and is active.
		String email = input != null && input.getInvokedBy() != null && input.getInvokedBy().getUserId() != null
				? input.getInvokedBy().getUserId().getEmail() : null;
		String sid = input != null && input.getInvokedBy() != null && input.getInvokedBy().getUserId() != null
				? input.getInvokedBy().getUserId().getSystemID() : null;
		if (email != null && !email.isBlank()) {
			guard.authenticateEndUser(sid, email, userPassword);
		}
		return commandsService.invokeCommand(input).toArray();
	}
}
