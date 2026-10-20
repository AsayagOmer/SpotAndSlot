package ambient_invisible_intelligence.controllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectChildIdBoundary;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.exceptions.InvalidInputException;
import ambient_invisible_intelligence.exceptions.NotFoundException;
import ambient_invisible_intelligence.logic.AccessGuard;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.smartparking.ObjectType;

// Sprint 4 contract: every operation authenticates the acting user via the
// userSystemID / userEmail / userPassword request parameters. Role rules:
// - OPERATOR creates/updates objects and binds relations; reads everything
// - END_USER reads active objects only (inactive -> 404, as if absent)
// - ADMIN may use only the Admin API, so all object routes reject ADMIN
@RestController
@RequestMapping(path = "/ambient-invisible-intelligence/objects", version = "1.4")
public class ObjectController {

	private final ObjectsService objectsService;
	private final AccessGuard guard;

	public ObjectController(ObjectsService objectsService, AccessGuard guard) {
		this.objectsService = objectsService;
		this.guard = guard;
	}

	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary createObject(
			@RequestBody ObjectBoundary input,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		// creation is an OPERATOR action; identity comes from createdBy, the
		// password proves it. A missing createdBy is left to the service (400).
		String email = createdByEmail(input);
		String sid = createdBySystemID(input);
		if (email != null && !email.isBlank()) {
			UserEntity operator = guard.authenticateOperator(sid, email, userPassword);
			// our ownership extension: sections/slots must sit under a lot the
			// operator created (kept without changing the REST API surface)
			if (input != null && !ObjectType.PARKING_LOT.equals(input.getType())
					&& isParkingType(input.getType())) {
				String parentLotId = detail(input, "parentLotId");
				if (parentLotId == null || parentLotId.isBlank())
					throw new InvalidInputException("objectDetails.parentLotId is required");
				guard.requireOwnsLot(operator, parentLotId);
			}
		}
		return objectsService.createObject(input);
	}

	@PutMapping(
			path = "/{systemID}/{objectId}",
			consumes = MediaType.APPLICATION_JSON_VALUE)
	public void updateObject(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestBody ObjectBoundary input) {
		UserEntity operator = guard.authenticateOperator(userSystemID, userEmail, userPassword);
		objectsService.getSpecificObject(systemID, objectId)
				.orElseThrow(() -> new NotFoundException("object not found: " + objectId));
		if (isParkingType(currentType(systemID, objectId))) {
			guard.requireOwnsTarget(operator, objectId);
		}
		objectsService.updateObject(systemID, objectId, input);
	}

	// Deleting a single object is an extension (not in the spec); keep it as an
	// OPERATOR+owner action, cascading to children.
	@DeleteMapping(path = "/{systemID}/{objectId}")
	public void deleteObject(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		UserEntity operator = guard.authenticateOperator(userSystemID, userEmail, userPassword);
		guard.requireOwnsTarget(operator, objectId);
		objectsService.deleteObject(systemID, objectId);
	}

	@GetMapping(
			path = "/{systemID}/{objectId}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary getSpecificObject(
			@PathVariable("systemID") String systemID,
			@PathVariable("objectId") String objectId,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword) {
		UserEntity reader = guard.authenticateReader(userSystemID, userEmail, userPassword);
		ObjectBoundary obj = objectsService.getSpecificObject(systemID, objectId)
				.orElseThrow(() -> new NotFoundException("object not found: " + objectId));
		// END_USER sees active objects only; an inactive one is reported as absent
		if (AccessGuard.isEndUser(reader) && !Boolean.TRUE.equals(obj.getActive()))
			throw new NotFoundException("object not found: " + objectId);
		return obj;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] getAllObjects(
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.getAllObjects(size, page, activeOnly).toArray(new ObjectBoundary[0]);
	}

	// ─── Sprint 4 custom search queries ────────────────────────────────────────

	@GetMapping(path = "/search/byAlias/{alias}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] searchByAlias(
			@PathVariable("alias") String alias,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.searchByAlias(alias, size, page, activeOnly).toArray(new ObjectBoundary[0]);
	}

	@GetMapping(path = "/search/byAliasPattern/{pattern}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] searchByAliasPattern(
			@PathVariable("pattern") String pattern,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.searchByAliasPattern(pattern, size, page, activeOnly).toArray(new ObjectBoundary[0]);
	}

	@GetMapping(path = "/search/byType/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] searchByType(
			@PathVariable("type") String type,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.searchByType(type, size, page, activeOnly).toArray(new ObjectBoundary[0]);
	}

	@GetMapping(path = "/search/byStatus/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] searchByStatus(
			@PathVariable("status") String status,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.searchByStatus(status, size, page, activeOnly).toArray(new ObjectBoundary[0]);
	}

	@GetMapping(path = "/search/byLocation/{lat}/{lng}/{distance}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] searchByLocation(
			@PathVariable("lat") double lat,
			@PathVariable("lng") double lng,
			@PathVariable("distance") double distance,
			@RequestParam(value = "units", defaultValue = "NEUTRAL") String units,
			@RequestParam(value = "shape", defaultValue = "SQUARE") String shape,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		// shape=CIRCLE is the bonus variant (radius = distance around the point)
		boolean circle = "CIRCLE".equalsIgnoreCase(shape);
		if (!circle && !"SQUARE".equalsIgnoreCase(shape))
			throw new InvalidInputException("invalid shape: " + shape + ". Must be SQUARE or CIRCLE");
		return objectsService.searchByLocation(lat, lng, distance, units, circle, size, page, activeOnly)
				.toArray(new ObjectBoundary[0]);
	}

	// ─── Object relationship API ───────────────────────────────────────────────

	@PutMapping(
			path = "/{parentSystemID}/{parentObjectId}/children",
			consumes = MediaType.APPLICATION_JSON_VALUE)
	public void bindChild(
			@PathVariable("parentSystemID") String parentSystemID,
			@PathVariable("parentObjectId") String parentObjectId,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestBody ObjectChildIdBoundary body) {
		// binding relations between objects is an OPERATOR action
		guard.authenticateOperator(userSystemID, userEmail, userPassword);
		objectsService.bindObjects(
				parentSystemID, parentObjectId,
				body.getChildId().getSystemID(),
				body.getChildId().getObjectId());
	}

	@GetMapping(
			path = "/{parentSystemID}/{parentObjectId}/children",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] getChildren(
			@PathVariable("parentSystemID") String parentSystemID,
			@PathVariable("parentObjectId") String parentObjectId,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.getChildren(parentSystemID, parentObjectId, size, page, activeOnly)
				.toArray(new ObjectBoundary[0]);
	}

	@GetMapping(
			path = "/{childSystemID}/{childObjectId}/parents",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ObjectBoundary[] getParents(
			@PathVariable("childSystemID") String childSystemID,
			@PathVariable("childObjectId") String childObjectId,
			@RequestParam(value = "userSystemID", required = false) String userSystemID,
			@RequestParam(value = "userEmail", required = false) String userEmail,
			@RequestParam(value = "userPassword", required = false) String userPassword,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "page", defaultValue = "0") int page) {
		boolean activeOnly = activeOnlyFor(userSystemID, userEmail, userPassword);
		return objectsService.getParents(childSystemID, childObjectId, size, page, activeOnly)
				.toArray(new ObjectBoundary[0]);
	}

	// ─── helpers ───────────────────────────────────────────────────

	// authenticate a reader and report whether it sees active objects only
	private boolean activeOnlyFor(String userSystemID, String userEmail, String userPassword) {
		return AccessGuard.isEndUser(guard.authenticateReader(userSystemID, userEmail, userPassword));
	}

	private String currentType(String systemID, String objectId) {
		return objectsService.getSpecificObject(systemID, objectId).map(ObjectBoundary::getType).orElse(null);
	}

	private static boolean isParkingType(String type) {
		return ObjectType.PARKING_LOT.equals(type)
				|| ObjectType.PARKING_SECTION.equals(type)
				|| ObjectType.PARKING_SLOT.equals(type);
	}

	private static String createdByEmail(ObjectBoundary o) {
		return o != null && o.getCreatedBy() != null && o.getCreatedBy().getUserId() != null
				? o.getCreatedBy().getUserId().getEmail() : null;
	}

	private static String createdBySystemID(ObjectBoundary o) {
		return o != null && o.getCreatedBy() != null && o.getCreatedBy().getUserId() != null
				? o.getCreatedBy().getUserId().getSystemID() : null;
	}

	private static String detail(ObjectBoundary o, String key) {
		if (o.getObjectDetails() == null) return null;
		Object v = o.getObjectDetails().get(key);
		return v == null ? null : String.valueOf(v);
	}
}
