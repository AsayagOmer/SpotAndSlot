package ambient_invisible_intelligence.smartparking;

import java.util.HashMap;
import java.util.Map;

// typed view over an ObjectBoundary's objectDetails when type == PARKING_LOT
public record ParkingLotDetails(String address) {

	public static ParkingLotDetails from(Map<String, Object> details) {
		if (details == null) return new ParkingLotDetails(null);
		return new ParkingLotDetails((String) details.get("address"));
	}

	public Map<String, Object> toMap() {
		Map<String, Object> m = new HashMap<>();
		if (address != null) m.put("address", address);
		return m;
	}
}
