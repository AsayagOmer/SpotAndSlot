package ambient_invisible_intelligence.logic;

import java.util.List;
import java.util.Optional;

import ambient_invisible_intelligence.boundaries.ObjectBoundary;

public interface ObjectsService {
	ObjectBoundary createObject(ObjectBoundary object);
	void updateObject(String systemID, String objectId, ObjectBoundary update);
	Optional<ObjectBoundary> getSpecificObject(String systemID, String objectId);
	// Unbounded multi-fetch: replaced by pagination. Throws GoneException (410).
	@Deprecated
	List<ObjectBoundary> getAllObjects();
	// Sprint 4: paginated listing and custom search queries. activeOnly=true is
	// the END_USER view: objects with active=false are never returned.
	List<ObjectBoundary> getAllObjects(int size, int page, boolean activeOnly);
	List<ObjectBoundary> searchByAlias(String alias, int size, int page, boolean activeOnly);
	List<ObjectBoundary> searchByAliasPattern(String pattern, int size, int page, boolean activeOnly);
	List<ObjectBoundary> searchByType(String type, int size, int page, boolean activeOnly);
	List<ObjectBoundary> searchByStatus(String status, int size, int page, boolean activeOnly);
	// square around (lat,lng), each edge within distance units of the point;
	// units: NEUTRAL (map degrees, default) | KM | MILES. circle=true is the
	// bonus variant: a circle of radius distance around the point.
	List<ObjectBoundary> searchByLocation(double lat, double lng, double distance,
	                                      String units, boolean circle, int size, int page, boolean activeOnly);
	// Delete a single object, cascading to its parking children:
	// a lot removes its sections + slots, a section removes its slots.
	void deleteObject(String systemID, String objectId);
	void deleteAllObjects();
	void bindObjects(String parentSystemID, String parentObjectId,
	                 String childSystemID, String childObjectId);
	List<ObjectBoundary> getChildren(String parentSystemID, String parentObjectId, int size, int page, boolean activeOnly);
	List<ObjectBoundary> getParents(String childSystemID, String childObjectId, int size, int page, boolean activeOnly);
}
