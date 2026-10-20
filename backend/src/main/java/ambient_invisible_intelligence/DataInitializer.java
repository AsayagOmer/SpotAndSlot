package ambient_invisible_intelligence;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ambient_invisible_intelligence.boundaries.CreatedBy;
import ambient_invisible_intelligence.boundaries.Location;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.ObjectRepository;
import ambient_invisible_intelligence.logic.ObjectsService;
import ambient_invisible_intelligence.logic.UsersService;
import ambient_invisible_intelligence.smartparking.ParkingLotDetails;
import ambient_invisible_intelligence.smartparking.ParkingSectionDetails;
import ambient_invisible_intelligence.smartparking.ParkingSlotDetails;
import ambient_invisible_intelligence.smartparking.ObjectType;

@Component
@Profile("initDemos")
public class DataInitializer implements CommandLineRunner {

	private final UsersService usersService;
	private final ObjectsService objectsService;
	private final ObjectRepository objectRepository;
	private final String systemID;

	public DataInitializer(UsersService usersService, ObjectsService objectsService,
			ObjectRepository objectRepository,
			@Value("${spring.application.name}") String systemID) {
		this.usersService = usersService;
		this.objectsService = objectsService;
		this.objectRepository = objectRepository;
		this.systemID = systemID;
	}

	@Override
	public void run(String... args) {
		seedUsers();
		// Idempotent: with a persistent database, only seed the lots once so
		// restarts do not accumulate duplicate lots/slots.
		if (lotAlreadySeeded()) {
			normalizeSeededDemoHealth();
			return;
		}
		seedLots();
	}

	private boolean lotAlreadySeeded() {
		return !objectRepository.findAllByType(ObjectType.PARKING_LOT).isEmpty();
	}

	private void seedUsers() {
		// ADMIN: Admin API only (export/delete). OPERATOR: builds and owns lots.
		// END_USER: browses active lots and invokes commands.
		seedUser("admin@demo.org",     "ADMIN",    "Demo Admin",       ":-)", "Admin@1");
		seedUser("operator@demo.org",  "OPERATOR", "Demo Operator",    ":-D", "Oper@1");
		seedUser("operator2@demo.org", "OPERATOR", "Second Operator",  ":-|", "Oper@2");
		seedUser("user@demo.org",      "END_USER", "Demo Driver",      ":-P", "User@1");
	}

	private void seedUser(String email, String role, String username, String avatar, String password) {
		try {
			UserBoundary user = new UserBoundary();
			user.setUserId(new UserId(email, null));
			user.setRole(role);
			user.setUsername(username);
			user.setAvatar(avatar);
			usersService.createUser(user, password);
		} catch (Exception ignored) { /* already seeded */ }
	}

	// Three demo lots owned by two different operators, so per-operator scoping
	// and the end-user lot picker are visible out of the box.
	private void seedLots() {
		String north = seedLot("חניון צפון תל אביב", "תל אביב", 32.114, 34.818, "operator@demo.org");
		seedSection(north, "Section A", "A", "operator@demo.org", new String[] {
				"H0", "H1", "H0", "1", "1", "1", "1", "1", "0", "1", "0" });
		seedSection(north, "Section B", "B", "operator@demo.org", new String[] {
				"1", "1", "0", "1", "1", "1", "1", "1", "0", "0" });
		seedSection(north, "Section C", "C", "operator@demo.org", new String[] {
				"0", "0", "1", "1", "1", "1", "0", "1", "1", "0", "0", "0" });

		String central = seedLot("חניון מרכזי", "תל אביב", 32.0853, 34.7818, "operator@demo.org");
		seedSection(central, "Section A", "A", "operator@demo.org", new String[] {
				"H1", "H0", "1", "1", "0", "1", "1", "0" });
		seedSection(central, "Section B", "B", "operator@demo.org", new String[] {
				"1", "1", "1", "1", "1", "0", "1", "1", "1", "0" });

		String south = seedLot("חוף הצוק", "תל אביב", 32.0158, 34.7874, "operator2@demo.org");
		seedSection(south, "Section A", "A", "operator2@demo.org", new String[] {
				"H0", "0", "0", "1", "0", "0", "1", "0" });
		seedSection(south, "Section B", "B", "operator2@demo.org", new String[] {
				"0", "1", "0", "0", "0", "0" });
	}

	private String seedLot(String alias, String city, double lat, double lng, String ownerEmail) {
		// status is required (Sprint 4); a lot is "OPEN" for business
		ObjectBoundary lot = obj(ObjectType.PARKING_LOT, alias, "OPEN", true,
				new ParkingLotDetails(city).toMap(), ownerEmail);
		lot.setLocation(new Location(lat, lng));
		return objectsService.createObject(lot).getId().getObjectId();
	}

	private void seedSection(String lotId, String alias, String prefix, String ownerEmail, String[] codes) {
		ObjectBoundary section = objectsService.createObject(obj(ObjectType.PARKING_SECTION, alias, "OPEN", true,
				new ParkingSectionDetails(lotId).toMap(), ownerEmail));
		seedRow(lotId, section.getId().getObjectId(), prefix, ownerEmail, codes);
	}

	// "1" = OCCUPIED, "0" = FREE; an "H" prefix marks a HANDICAP slot.
	private void seedRow(String lotId, String sectionId, String prefix, String ownerEmail, String[] codes) {
		for (int i = 0; i < codes.length; i++) {
			String code = codes[i];
			boolean handicap = code.startsWith("H");
			String digit = handicap ? code.substring(1) : code;
			String kind = handicap ? "HANDICAP" : "REGULAR";
			String status = "1".equals(digit) ? "OCCUPIED" : "FREE";
			seedSlot(lotId, sectionId, prefix + "-" + (i + 1), kind, status, ownerEmail);
		}
	}

	private void seedSlot(String lotId, String sectionId, String alias, String kind, String status, String ownerEmail) {
		ParkingSlotDetails slotDet = new ParkingSlotDetails(lotId, sectionId, kind, "OK", null);
		objectsService.createObject(obj(ObjectType.PARKING_SLOT, alias, status, true, slotDet.toMap(), ownerEmail));
	}

	private static final java.util.Set<String> SEEDED_LOT_ALIASES =
			java.util.Set.of("חניון צפון תל אביב", "חניון מרכזי", "חוף הצוק", "Demo Lot");

	// Demo slots are normalized to healthy with no heartbeat so the scheduler
	// does not mark them FAULTY; real slots that report heartbeats are still checked.
	private void normalizeSeededDemoHealth() {
		java.util.Set<String> seededLotIds = objectRepository.findAllByType(ObjectType.PARKING_LOT).stream()
				.filter(o -> SEEDED_LOT_ALIASES.contains(o.getAlias()))
				.map(ObjectEntity::getObjectId)
				.collect(java.util.stream.Collectors.toSet());
		if (seededLotIds.isEmpty()) return;

		objectRepository.findAllByType(ObjectType.PARKING_SLOT).forEach(slot -> {
			ParkingSlotDetails det = ParkingSlotDetails.from(slot.getObjectDetails());
			if (!seededLotIds.contains(det.parentLotId())) return;
			if ("OK".equals(det.healthStatus()) && det.lastHeartbeat() == null) return;

			slot.setObjectDetails(det.withHealth("OK", null).toMap());
			objectRepository.save(slot);
		});
	}

	private ObjectBoundary obj(String type, String alias, String status, boolean active,
			Map<String, Object> details, String ownerEmail) {
		ObjectBoundary b = new ObjectBoundary();
		b.setType(type);
		b.setAlias(alias);
		b.setStatus(status);
		b.setActive(active);
		b.setObjectDetails(details);
		b.setCreatedBy(new CreatedBy(new UserId(ownerEmail, systemID)));
		return b;
	}
}
