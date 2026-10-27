package ambient_invisible_intelligence.data;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObjectRepository extends JpaRepository<ObjectEntity, String> {

	// ─── Internal, non-paginated scans ─────────────────────────────────────────
	// Used only by internal callers (seeding, the health scheduler, command
	// handlers) that need the full result set rather than one page of it. Never
	// call the deprecated ObjectsService#getAllObjects() for this purpose.

	List<ObjectEntity> findAllByType(String type);

	List<ObjectEntity> findAllByTypeAndActiveTrue(String type);

	List<ObjectEntity> findAllByTypeAndStatus(String type, String status);

	List<ObjectEntity> findAllByTypeAndStatusAndActiveTrue(String type, String status);

	// ─── Sprint 4 custom queries (all paginated) ───────────────────────────────
	// Each query has an active-only variant: OPERATORs read everything, while
	// END_USERs only ever see objects with active=true.

	Page<ObjectEntity> findAllByActiveTrue(Pageable pageable);

	Page<ObjectEntity> findAllByAlias(String alias, Pageable pageable);

	Page<ObjectEntity> findAllByAliasAndActiveTrue(String alias, Pageable pageable);

	// "pattern" search: the pattern is matched as a substring of the alias
	Page<ObjectEntity> findAllByAliasContaining(String pattern, Pageable pageable);

	Page<ObjectEntity> findAllByAliasContainingAndActiveTrue(String pattern, Pageable pageable);

	Page<ObjectEntity> findAllByType(String type, Pageable pageable);

	Page<ObjectEntity> findAllByTypeAndActiveTrue(String type, Pageable pageable);

	Page<ObjectEntity> findAllByStatus(String status, Pageable pageable);

	Page<ObjectEntity> findAllByStatusAndActiveTrue(String status, Pageable pageable);

	// location search within a square: lat/lng each within +-distance (in degrees)
	Page<ObjectEntity> findAllByLatBetweenAndLngBetween(
			double minLat, double maxLat, double minLng, double maxLng, Pageable pageable);

	Page<ObjectEntity> findAllByLatBetweenAndLngBetweenAndActiveTrue(
			double minLat, double maxLat, double minLng, double maxLng, Pageable pageable);

	// bonus: location search within a circle of the given radius (in degrees)
	@Query("""
			select o from ObjectEntity o
			where o.lat is not null and o.lng is not null
			and (:activeOnly = false or o.active = true)
			and ((o.lat - :lat) * (o.lat - :lat) + (o.lng - :lng) * (o.lng - :lng)) <= :radiusSquared
			""")
	Page<ObjectEntity> findAllWithinCircle(
			@Param("lat") double lat,
			@Param("lng") double lng,
			@Param("radiusSquared") double radiusSquared,
			@Param("activeOnly") boolean activeOnly,
			Pageable pageable);
}
