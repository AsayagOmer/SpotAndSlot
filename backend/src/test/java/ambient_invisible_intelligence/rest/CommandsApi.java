package ambient_invisible_intelligence.rest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

import ambient_invisible_intelligence.boundaries.CommandBoundary;

// Declarative HTTP client for the /commands endpoint, used by integration tests.
public interface CommandsApi {

	// Command results are arbitrary JSON objects, so the response is a generic array.
	// Sprint 4: the invoker's identity (invokedBy) is verified with userPassword.
	@PostExchange(
			url = "/commands",
			contentType = MediaType.APPLICATION_JSON_VALUE,
			accept = MediaType.APPLICATION_JSON_VALUE)
	Object[] invoke(@RequestBody CommandBoundary command,
			@RequestParam("userPassword") String userPassword);
}
