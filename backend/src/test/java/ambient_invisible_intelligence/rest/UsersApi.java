package ambient_invisible_intelligence.rest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserWithPasswordBoundary;

// Declarative HTTP client for the /users endpoints, used by integration tests.
public interface UsersApi {

	@PostExchange(
			url = "/users",
			contentType = MediaType.APPLICATION_JSON_VALUE,
			accept = MediaType.APPLICATION_JSON_VALUE)
	UserBoundary create(@RequestBody UserWithPasswordBoundary user);

	@GetExchange(url = "/users/login/{systemID}/{email}", accept = MediaType.APPLICATION_JSON_VALUE)
	UserBoundary login(
			@PathVariable("systemID") String systemID,
			@PathVariable("email") String email,
			@RequestParam("password") String password);

	@PutExchange(url = "/users/{systemID}/{email}", contentType = MediaType.APPLICATION_JSON_VALUE)
	void update(
			@PathVariable("systemID") String systemID,
			@PathVariable("email") String email,
			@RequestParam("password") String existingPassword,
			@RequestBody UserWithPasswordBoundary update);
}
