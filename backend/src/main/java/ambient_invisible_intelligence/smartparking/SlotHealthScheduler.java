package ambient_invisible_intelligence.smartparking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ambient_invisible_intelligence.data.ObjectRepository;

@Component
public class SlotHealthScheduler {

	private static final long STALE_AFTER_MS = 60_000L;

	private final ObjectRepository objectRepository;

	public SlotHealthScheduler(ObjectRepository objectRepository) {
		this.objectRepository = objectRepository;
	}

	@Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
	@Transactional
	public void checkSlots() {
		long now = System.currentTimeMillis();
		objectRepository.findAllByType(ObjectType.PARKING_SLOT)
				.forEach(slot -> {
					ParkingSlotDetails det = ParkingSlotDetails.from(slot.getObjectDetails());
					Long hb = det.lastHeartbeat();
					if (hb != null && now - hb > STALE_AFTER_MS && !"FAULTY".equals(det.healthStatus())) {
						slot.setObjectDetails(det.withHealth("FAULTY", det.lastHeartbeat()).toMap());
						objectRepository.save(slot);
					}
				});
	}
}
