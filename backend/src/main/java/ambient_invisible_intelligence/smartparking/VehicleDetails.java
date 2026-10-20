package ambient_invisible_intelligence.smartparking;

import java.util.HashMap;
import java.util.Map;

// typed view over an ObjectBoundary's objectDetails when type == VEHICLE
public record VehicleDetails(
		String lotId,
		String driverEmail,
		String entryTime,
		String plate) {

	public static VehicleDetails from(Map<String, Object> details) {
		if (details == null) return new VehicleDetails(null, null, null, null);
		return new VehicleDetails(
				(String) details.get("lotId"),
				(String) details.get("driverEmail"),
				(String) details.get("entryTime"),
				(String) details.get("plate"));
	}

	public Map<String, Object> toMap() {
		Map<String, Object> m = new HashMap<>();
		if (lotId != null)       m.put("lotId", lotId);
		if (driverEmail != null) m.put("driverEmail", driverEmail);
		if (entryTime != null)   m.put("entryTime", entryTime);
		if (plate != null)       m.put("plate", plate);
		return m;
	}
}
