package ambient_invisible_intelligence.smartparking;

import java.util.HashMap;
import java.util.Map;

// typed view over an ObjectBoundary's objectDetails when type == PARKING_SECTION
public record ParkingSectionDetails(String parentLotId) {

	public static ParkingSectionDetails from(Map<String, Object> details) {
		if (details == null) return new ParkingSectionDetails(null);
		return new ParkingSectionDetails((String) details.get("parentLotId"));
	}

	public Map<String, Object> toMap() {
		Map<String, Object> m = new HashMap<>();
		if (parentLotId != null) m.put("parentLotId", parentLotId);
		return m;
	}
}
