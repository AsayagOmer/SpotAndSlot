package ambient_invisible_intelligence.logic;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ambient_invisible_intelligence.boundaries.CreatedBy;
import ambient_invisible_intelligence.boundaries.Location;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectId;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.ObjectRepository;
import ambient_invisible_intelligence.exceptions.GoneException;
import ambient_invisible_intelligence.exceptions.InvalidInputException;
import ambient_invisible_intelligence.exceptions.NotFoundException;
import ambient_invisible_intelligence.smartparking.ObjectType;

@Service
public class ObjectsServiceImpl implements ObjectsService {

	private final ObjectRepository objectRepository;
	private final String systemID;

	public ObjectsServiceImpl(ObjectRepository objectRepository,
			@Value("${spring.application.name}") String systemID) {
		this.objectRepository = objectRepository;
		this.systemID = systemID;
	}

	@Override
	@Transactional
	public ObjectBoundary createObject(ObjectBoundary object) {
		// validate required input (missing fields -> HTTP 400 via InvalidInputException)
		if (object == null)
			throw new InvalidInputException("object is required");
		if (object.getType() == null || object.getType().isBlank())
			throw new InvalidInputException("object type is required");
		if (object.getAlias() == null || object.getAlias().isBlank())
			throw new InvalidInputException("object alias is required");
		// Sprint 4: status is required and may not be null or blank
		if (object.getStatus() == null || object.getStatus().isBlank())
			throw new InvalidInputException("object status is required and may not be blank");
		if (object.getCreatedBy() == null
				|| object.getCreatedBy().getUserId() == null
				|| object.getCreatedBy().getUserId().getEmail() == null
				|| object.getCreatedBy().getUserId().getEmail().isBlank())
			throw new InvalidInputException("createdBy.userId.email is required");

		String generatedId = UUID.randomUUID().toString();
		ObjectEntity e = toEntity(object);
		e.setObjectId(generatedId);
		e.setSystemID(systemID);
		e.setId(ObjectEntity.key(generatedId, systemID));
		e.setCreationTimestamp(new Date());
		if (e.getActive() == null) e.setActive(true);
		objectRepository.save(e);
		return toBoundary(e);
	}

	@Override
	@Transactional
	public void updateObject(String systemID, String objectId, ObjectBoundary update) {
		ObjectEntity e = objectRepository.findById(ObjectEntity.key(objectId, systemID))
				.orElseThrow(() -> new NotFoundException("object not found: " + objectId));
		// the ID, createdBy, and creationTimestamp are immutable; everything else may be
		// replaced. null means "leave unchanged"; blank strings are invalid input.
		if (update.getType() != null) {
			if (update.getType().isBlank())
				throw new InvalidInputException("object type may not be blank");
			e.setType(update.getType());
		}
		if (update.getAlias() != null) {
			if (update.getAlias().isBlank())
				throw new InvalidInputException("object alias may not be blank");
			e.setAlias(update.getAlias());
		}
		if (update.getStatus() != null) {
			if (update.getStatus().isBlank())
				throw new InvalidInputException("object status may not be blank");
			e.setStatus(update.getStatus());
		}
		if (update.getActive() != null)        e.setActive(update.getActive());
		if (update.getLocation() != null) {
			e.setLat(update.getLocation().getLat());
			e.setLng(update.getLocation().getLng());
		}
		if (update.getObjectDetails() != null) e.setObjectDetails(update.getObjectDetails());
		objectRepository.save(e);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ObjectBoundary> getSpecificObject(String systemID, String objectId) {
		return objectRepository.findById(ObjectEntity.key(objectId, systemID))
				.map(this::toBoundary);
	}

	@Override
	@Deprecated
	public List<ObjectBoundary> getAllObjects() {
		throw new GoneException("Replaced by pagination-supporting getAllObjects(size, page, activeOnly)");
	}

	// ─── Sprint 4: paginated listing and custom search queries ────────────────

	// deterministic paging order across requests
	private static Pageable pageOf(int size, int page) {
		if (size < 1)
			throw new InvalidInputException("size must be at least 1");
		if (page < 0)
			throw new InvalidInputException("page may not be negative");
		return PageRequest.of(page, size, Sort.by("id").ascending());
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> getAllObjects(int size, int page, boolean activeOnly) {
		var pageable = pageOf(size, page);
		return (activeOnly
				? objectRepository.findAllByActiveTrue(pageable)
				: objectRepository.findAll(pageable))
				.map(this::toBoundary).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByAlias(String alias, int size, int page, boolean activeOnly) {
		var pageable = pageOf(size, page);
		return (activeOnly
				? objectRepository.findAllByAliasAndActiveTrue(alias, pageable)
				: objectRepository.findAllByAlias(alias, pageable))
				.map(this::toBoundary).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByAliasPattern(String pattern, int size, int page, boolean activeOnly) {
		var pageable = pageOf(size, page);
		return (activeOnly
				? objectRepository.findAllByAliasContainingAndActiveTrue(pattern, pageable)
				: objectRepository.findAllByAliasContaining(pattern, pageable))
				.map(this::toBoundary).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByType(String type, int size, int page, boolean activeOnly) {
		var pageable = pageOf(size, page);
		return (activeOnly
				? objectRepository.findAllByTypeAndActiveTrue(type, pageable)
				: objectRepository.findAllByType(type, pageable))
				.map(this::toBoundary).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByStatus(String status, int size, int page, boolean activeOnly) {
		var pageable = pageOf(size, page);
		return (activeOnly
				? objectRepository.findAllByStatusAndActiveTrue(status, pageable)
				: objectRepository.findAllByStatus(status, pageable))
				.map(this::toBoundary).getContent();
	}

	// approximate degrees per distance unit (course-grade conversion)
	private static double toDegrees(double distance, String units) {
		if (distance < 0)
			throw new InvalidInputException("distance may not be negative");
		String u = units == null || units.isBlank() ? "NEUTRAL" : units.toUpperCase();
		return switch (u) {
			case "NEUTRAL" -> distance;              // generic map units = degrees
			case "KM"      -> distance / 111.32;     // ~111.32 km per degree
			case "MILES"   -> distance / 69.17;      // ~69.17 miles per degree
			default -> throw new InvalidInputException(
					"invalid units: " + units + ". Must be one of: NEUTRAL, KM, MILES");
		};
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> searchByLocation(double lat, double lng, double distance,
	                                             String units, boolean circle, int size, int page,
	                                             boolean activeOnly) {
		double d = toDegrees(distance, units);
		var pageable = pageOf(size, page);
		org.springframework.data.domain.Page<ObjectEntity> result;
		if (circle) {
			// bonus: circle of radius d around the point
			result = objectRepository.findAllWithinCircle(lat, lng, d * d, activeOnly, pageable);
		} else if (activeOnly) {
			result = objectRepository.findAllByLatBetweenAndLngBetweenAndActiveTrue(
					lat - d, lat + d, lng - d, lng + d, pageable);
		} else {
			// spec: square around the point, each edge within d of it
			result = objectRepository.findAllByLatBetweenAndLngBetween(
					lat - d, lat + d, lng - d, lng + d, pageable);
		}
		return result.map(this::toBoundary).getContent();
	}

	@Override
	@Transactional
	public void deleteObject(String systemID, String objectId) {
		ObjectEntity target = objectRepository.findById(ObjectEntity.key(objectId, systemID))
				.orElseThrow(() -> new NotFoundException("object not found: " + objectId));
		String type = target.getType();
		if (ObjectType.PARKING_LOT.equals(type)) {
			// cascade: remove every section and slot that belongs to this lot
			objectRepository.findAll().stream()
					.filter(o -> objectId.equals(detail(o, "parentLotId")))
					.forEach(o -> objectRepository.deleteById(o.getId()));
		} else if (ObjectType.PARKING_SECTION.equals(type)) {
			// cascade: remove every slot in this section
			objectRepository.findAll().stream()
					.filter(o -> objectId.equals(detail(o, "parentSectionId")))
					.forEach(o -> objectRepository.deleteById(o.getId()));
		}
		objectRepository.deleteById(target.getId());
	}

	@Override
	@Transactional
	public void deleteAllObjects() {
		objectRepository.deleteAll();
	}

	// Read a String field out of an object's details map (null-safe).
	private static String detail(ObjectEntity o, String key) {
		if (o.getObjectDetails() == null) return null;
		Object v = o.getObjectDetails().get(key);
		return v == null ? null : String.valueOf(v);
	}

	@Override
	@Transactional
	public void bindObjects(String parentSystemID, String parentObjectId,
	                        String childSystemID, String childObjectId) {
		String parentKey = ObjectEntity.key(parentObjectId, parentSystemID);
		String childKey  = ObjectEntity.key(childObjectId, childSystemID);
		ObjectEntity parent = objectRepository.findById(parentKey)
				.orElseThrow(() -> new NotFoundException("parent object not found: " + parentObjectId));
		ObjectEntity child = objectRepository.findById(childKey)
				.orElseThrow(() -> new NotFoundException("child object not found: " + childObjectId));
		parent.getChildrenIds().add(childKey);
		child.getParentIds().add(parentKey);
		objectRepository.save(parent);
		objectRepository.save(child);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> getChildren(String parentSystemID, String parentObjectId, int size, int page,
	                                        boolean activeOnly) {
		ObjectEntity parent = objectRepository.findById(ObjectEntity.key(parentObjectId, parentSystemID))
				.orElseThrow(() -> new NotFoundException("object not found: " + parentObjectId));
		return pageOfIds(parent.getChildrenIds(), size, page, activeOnly);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectBoundary> getParents(String childSystemID, String childObjectId, int size, int page,
	                                       boolean activeOnly) {
		ObjectEntity child = objectRepository.findById(ObjectEntity.key(childObjectId, childSystemID))
				.orElseThrow(() -> new NotFoundException("object not found: " + childObjectId));
		return pageOfIds(child.getParentIds(), size, page, activeOnly);
	}

	// Relations are stored as id sets on the entity: filter (END_USER sees only
	// active objects), sort for determinism, then slice the requested page.
	private List<ObjectBoundary> pageOfIds(java.util.Set<String> ids, int size, int page, boolean activeOnly) {
		pageOf(size, page); // validates size/page
		List<ObjectBoundary> all = new ArrayList<>();
		objectRepository.findAllById(ids.stream().sorted().toList()).forEach(o -> {
			if (!activeOnly || Boolean.TRUE.equals(o.getActive())) all.add(toBoundary(o));
		});
		all.sort(java.util.Comparator.comparing(b -> b.getId().getObjectId()));
		int from = Math.min(page * size, all.size());
		int to = Math.min(from + size, all.size());
		return all.subList(from, to);
	}

	private ObjectEntity toEntity(ObjectBoundary b) {
		ObjectEntity e = new ObjectEntity();
		e.setType(b.getType());
		e.setAlias(b.getAlias());
		e.setStatus(b.getStatus());
		e.setActive(b.getActive());
		if (b.getCreatedBy() != null && b.getCreatedBy().getUserId() != null) {
			e.setCreatedByEmail(b.getCreatedBy().getUserId().getEmail());
			e.setCreatedBySystemID(b.getCreatedBy().getUserId().getSystemID());
		}
		if (b.getLocation() != null) {
			e.setLat(b.getLocation().getLat());
			e.setLng(b.getLocation().getLng());
		}
		e.setObjectDetails(b.getObjectDetails());
		return e;
	}

	private ObjectBoundary toBoundary(ObjectEntity e) {
		ObjectBoundary b = new ObjectBoundary();
		b.setId(new ObjectId(e.getObjectId(), e.getSystemID()));
		b.setType(e.getType());
		b.setAlias(e.getAlias());
		b.setStatus(e.getStatus());
		b.setActive(e.getActive());
		b.setCreationTimestamp(e.getCreationTimestamp());
		if (e.getCreatedByEmail() != null) {
			b.setCreatedBy(new CreatedBy(new UserId(e.getCreatedByEmail(), e.getCreatedBySystemID())));
		}
		if (e.getLat() != null && e.getLng() != null) {
			b.setLocation(new Location(e.getLat(), e.getLng()));
		}
		b.setObjectDetails(e.getObjectDetails());
		return b;
	}
}
