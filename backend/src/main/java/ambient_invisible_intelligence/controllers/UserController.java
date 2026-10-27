package ambient_invisible_intelligence.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;
import ambient_invisible_intelligence.data.UserRole;
import ambient_invisible_intelligence.exceptions.UnauthorizedException;
import ambient_invisible_intelligence.logic.AccessGuard;
import ambient_invisible_intelligence.logic.UsersService;

@RestController
@RequestMapping(path = "/ambient-invisible-intelligence/users", version = "1.4")
public class UserController {

	private final UsersService usersService;
	private final AccessGuard guard;

	public UserController(UsersService usersService, AccessGuard guard) {
		this.usersService = usersService;
		this.guard = guard;
	}

	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public UserBoundary createUser(
			@RequestBody UserWithPasswordBoundary input,
			@RequestParam(value = "actingEmail", required = false) String actingEmail,
			@RequestParam(value = "actingSystemID", required = false) String actingSystemID,
			@RequestParam(value = "actingPassword", required = false) String actingPassword) {
		// Public sign-up may create END_USER accounts only. Creating an ADMIN or
		// OPERATOR requires an existing administrator to authorize it.
		String role = input.getRole();
		if (UserRole.ADMIN.name().equals(role) || UserRole.OPERATOR.name().equals(role)) {
			guard.requireAdminByEmail(actingEmail, actingSystemID, actingPassword);
		}
		UserBoundary boundary = new UserBoundary();
		boundary.setUserId(new UserId(input.getEmail(), null));
		boundary.setRole(input.getRole());
		boundary.setUsername(input.getUsername());
		boundary.setAvatar(input.getAvatar());
		return usersService.createUser(boundary, input.getPassword());
	}

	@GetMapping(
			path = "/login/{systemID}/{userEmail}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public UserBoundary loginUser(
			@PathVariable("systemID") String systemID,
			@PathVariable("userEmail") String userEmail,
			@RequestParam("password") String password) {
		return usersService.loginUser(systemID, userEmail, password)
				.orElseThrow(() -> new UnauthorizedException("invalid credentials"));
	}

	@PutMapping(
			path = "/{systemID}/{userEmail}",
			consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public void updateUser(
			@PathVariable("systemID") String systemID,
			@PathVariable("userEmail") String userEmail,
			@RequestParam("password") String existingPassword,
			@RequestBody UserWithPasswordBoundary input) {
		UserBoundary update = new UserBoundary();
		update.setRole(input.getRole());
		update.setUsername(input.getUsername());
		update.setAvatar(input.getAvatar());
		usersService.updateUser(systemID, userEmail, existingPassword, update, input.getPassword());
	}
}
