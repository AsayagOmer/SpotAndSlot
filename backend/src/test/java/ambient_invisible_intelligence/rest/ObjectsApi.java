package ambient_invisible_intelligence.rest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;

// Declarative HTTP client for the /objects endpoints, used by integration tests.
// Sprint 4: operations carry the acting user's credentials; lists are paginated.
public interface ObjectsApi {

	@PostExchange(
			url = "/objects",
			contentType = MediaType.APPLICATION_JSON_VALUE,
			accept = MediaType.APPLICATION_JSON_VALUE)
	ObjectBoundary create(@RequestBody ObjectBoundary object,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(url = "/objects", accept = MediaType.APPLICATION_JSON_VALUE)
	ObjectBoundary[] getAll(
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestParam("size") int size,
			@RequestParam("page") int page);

	@GetExchange(url = "/objects/{systemID}/{objectId}", accept = MediaType.APPLICATION_JSON_VALUE)
	ObjectBoundary getById(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@PutExchange(url = "/objects/{systemID}/{objectId}", contentType = MediaType.APPLICATION_JSON_VALUE)
	void update(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestBody ObjectBoundary update);

	@PutExchange(url = "/objects/{systemID}/{objectId}/children", contentType = MediaType.APPLICATION_JSON_VALUE)
	void bindChild(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword,
			@RequestBody ObjectChildIdBoundary child);

	@GetExchange(url = "/objects/{systemID}/{objectId}/children", accept = MediaType.APPLICATION_JSON_VALUE)
	ObjectBoundary[] getChildren(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);

	@GetExchange(url = "/objects/{systemID}/{objectId}/parents", accept = MediaType.APPLICATION_JSON_VALUE)
	ObjectBoundary[] getParents(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam("userEmail") String userEmail,
			@RequestParam("userPassword") String userPassword);
}
