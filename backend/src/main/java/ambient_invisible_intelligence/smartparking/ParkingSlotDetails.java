package ambient_invisible_intelligence.smartparking;

import java.util.HashMap;
import java.util.Map;

public record ParkingSlotDetails(
		String parentLotId,
		String parentSectionId,
		String slotKind,        // "REGULAR" | "HANDICAP"
		String healthStatus,    // "OK" | "FAULTY"
		Long lastHeartbeat) {

	public static ParkingSlotDetails from(Map<String, Object> details) {
		if (details == null) return new ParkingSlotDetails(null, null, null, null, null);
		Object hb = details.get("lastHeartbeat");
		Long hbVal = hb instanceof Number n ? n.longValue() : null;
		return new ParkingSlotDetails(
				(String) details.get("parentLotId"),
				(String) details.get("parentSectionId"),
				(String) details.get("slotKind"),
				(String) details.get("healthStatus"),
				hbVal);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> m = new HashMap<>();
		if (parentLotId != null)     m.put("parentLotId", parentLotId);
		if (parentSectionId != null) m.put("parentSectionId", parentSectionId);
		if (slotKind != null)        m.put("slotKind", slotKind);
		if (healthStatus != null)    m.put("healthStatus", healthStatus);
		if (lastHeartbeat != null)   m.put("lastHeartbeat", lastHeartbeat);
		return m;
	}

	public ParkingSlotDetails withHealth(String status, Long heartbeat) {
		return new ParkingSlotDetails(parentLotId, parentSectionId, slotKind, status, heartbeat);
	}
}
