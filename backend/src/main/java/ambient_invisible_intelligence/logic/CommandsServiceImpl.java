package ambient_invisible_intelligence.logic;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Micro-services + ML integration
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.boundaries.CommandId;
import ambient_invisible_intelligence.boundaries.CreatedBy;
import ambient_invisible_intelligence.boundaries.InvokedBy;
import ambient_invisible_intelligence.boundaries.ObjectBoundary;
import ambient_invisible_intelligence.boundaries.ObjectId;
import ambient_invisible_intelligence.boundaries.TargetObject;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.data.CommandEntity;
import ambient_invisible_intelligence.data.CommandRepository;
import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.ObjectRepository;
import ambient_invisible_intelligence.exceptions.GoneException;
import ambient_invisible_intelligence.exceptions.InvalidInputException;
import ambient_invisible_intelligence.exceptions.NotFoundException;
import ambient_invisible_intelligence.smartparking.ObjectType;
import ambient_invisible_intelligence.smartparking.ParkingSlotDetails;
import ambient_invisible_intelligence.smartparking.VehicleDetails;

@Service
public class CommandsServiceImpl implements CommandsService {

	private static final Logger logger = LoggerFactory.getLogger(CommandsServiceImpl.class);

	private final CommandRepository commandRepository;
	private final ObjectsService objectsService;
	private final ObjectRepository objectRepository;
	private final String systemID;
	private final String mlServiceBaseUrl;
	private final RestTemplate restTemplate;

	private static final Map<String, Set<String>> COMMANDS_BY_TYPE = Map.of(
			ObjectType.PARKING_LOT, Set.of(
					"availabilityInLot",
					"bestSectionInLot",
					"predictAvailability",
					"runHealthCheck"),
			ObjectType.PARKING_SECTION, Set.of(
					"availabilityInSection"),
			ObjectType.PARKING_SLOT, Set.of(
					"checkSlotAvailability",
					"updateSlotState"),
			ObjectType.VEHICLE, Set.of(
					"vehicleEnters",
					"vehicleExits",
					"reportSession")
	);

	@Autowired
	public CommandsServiceImpl(CommandRepository commandRepository,
			ObjectsService objectsService,
			ObjectRepository objectRepository,
			@Value("${spring.application.name}") String systemID,
			@Value("${ML_URL:http://localhost:5000}") String mlServiceBaseUrl) {
		this.commandRepository = commandRepository;
		this.objectsService = objectsService;
		this.objectRepository = objectRepository;
		this.systemID = systemID;
		this.mlServiceBaseUrl = mlServiceBaseUrl.endsWith("/")
				? mlServiceBaseUrl.substring(0, mlServiceBaseUrl.length() - 1)
				: mlServiceBaseUrl;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(2000);
		requestFactory.setReadTimeout(3000);
		this.restTemplate = new RestTemplate(requestFactory);
	}

	public CommandsServiceImpl(CommandRepository commandRepository,
			ObjectsService objectsService,
			ObjectRepository objectRepository,
			String systemID) {
		this(commandRepository, objectsService, objectRepository, systemID, "http://localhost:5000");
	}

	@Override
	@Transactional
	public List<Object> invokeCommand(CommandBoundary input) {
		// validate the command string itself (missing/blank -> HTTP 400)
		if (isBlank(input.getCommand()))
			throw new InvalidInputException("command is required and may not be blank");
		// validate invoker identity
		if (input.getInvokedBy() == null
				|| input.getInvokedBy().getUserId() == null
				|| isBlank(input.getInvokedBy().getUserId().getEmail())
				|| isBlank(input.getInvokedBy().getUserId().getSystemID())) {
			throw new InvalidInputException(
					"invokedBy.userId.email and invokedBy.userId.systemID are required");
		}
		// validate target object identity
		if (input.getTargetObject() == null
				|| input.getTargetObject().getId() == null
				|| isBlank(input.getTargetObject().getId().getObjectId())
				|| isBlank(input.getTargetObject().getId().getSystemID())) {
			throw new InvalidInputException(
					"targetObject.objectId.objectId and targetObject.objectId.systemID are required");
		}

		String generatedId = UUID.randomUUID().toString();
		CommandEntity e = new CommandEntity();
		e.setCommandId(generatedId);
		e.setSystemID(systemID);
		e.setId(CommandEntity.key(generatedId, systemID));
		e.setCommand(input.getCommand());
		e.setTargetObjectId(input.getTargetObject().getId().getObjectId());
		e.setTargetSystemID(input.getTargetObject().getId().getSystemID());
		e.setInvocationTimestamp(new Date());
		e.setInvokedByEmail(input.getInvokedBy().getUserId().getEmail());
		e.setInvokedBySystemID(input.getInvokedBy().getUserId().getSystemID());
		e.setCommandAttributes(input.getCommandAttributes());
		commandRepository.save(e);

		String cmd = input.getCommand() == null ? "" : input.getCommand();
		Map<String, Object> attrs = input.getCommandAttributes() == null ? new HashMap<>() : input.getCommandAttributes();
		String targetObjectId = e.getTargetObjectId();

		// validate that the command is defined for the target object's type
		if (targetObjectId != null) {
			ObjectBoundary target = objectsService.getSpecificObject(
					e.getTargetSystemID() != null ? e.getTargetSystemID() : systemID, targetObjectId)
					.orElseThrow(() -> new NotFoundException("target object not found: " + targetObjectId));
			// Sprint 4: commands may only target an active object; an inactive
			// one is treated as if it does not exist
			if (!Boolean.TRUE.equals(target.getActive()))
				throw new NotFoundException("target object not found: " + targetObjectId);
			String targetType = target.getType();
			Set<String> allowed = COMMANDS_BY_TYPE.getOrDefault(targetType, Set.of());
			if (!allowed.contains(cmd)) {
				throw new InvalidInputException(
						"command '" + cmd + "' is not defined for object type " + targetType
						+ "; allowed commands: " + allowed);
			}
		}

		return switch (cmd) {
			// --- PARKING_LOT commands ---
			case "availabilityInLot"      -> availabilityInLot(targetObjectId, attrs);
			case "bestSectionInLot"       -> bestSectionInLot(targetObjectId, attrs);
			case "predictAvailability"    -> predictAvailability(targetObjectId, attrs);
			case "runHealthCheck"         -> runHealthCheck(targetObjectId);
			// --- PARKING_SECTION commands ---
			case "availabilityInSection"  -> availabilityInSection(targetObjectId, attrs);
			// --- PARKING_SLOT commands ---
			case "checkSlotAvailability"  -> checkSlotAvailability(targetObjectId);
			case "updateSlotState"        -> updateSlotState(targetObjectId, attrs);
			// --- VEHICLE commands ---
			case "vehicleEnters"  -> vehicleEvent(targetObjectId, attrs, e, "PARKED");
			case "vehicleExits"   -> vehicleEvent(targetObjectId, attrs, e, "LEAVING");
			case "reportSession"  -> vehicleEvent(targetObjectId, attrs, e, (String) attrs.getOrDefault("state", "SEARCHING"));
			default -> throw new InvalidInputException("unknown command: " + cmd);
		};
	}

	// ──────────────────── PARKING_LOT commands ────────────────────

	private List<Object> availabilityInLot(String lotId, Map<String, Object> attrs) {
		String slotKindFilter = (String) attrs.get("slotKindFilter");
		long freeSlots = objectRepository.findAllByTypeAndStatusAndActiveTrue(ObjectType.PARKING_SLOT, "FREE").stream()
				.map(b -> ParkingSlotDetails.from(b.getObjectDetails()))
				.filter(d -> lotId.equals(d.parentLotId()))
				.filter(d -> slotKindFilter == null || slotKindFilter.equals(d.slotKind()))
				.count();
		long searchers = objectRepository.findAllByTypeAndStatusAndActiveTrue(ObjectType.VEHICLE, "SEARCHING").stream()
				.map(b -> VehicleDetails.from(b.getObjectDetails()))
				.filter(d -> lotId.equals(d.lotId()))
				.count();
		Map<String, Object> result = new HashMap<>();
		result.put("lotId", lotId);
		result.put("freeSlots", freeSlots);
		result.put("searchingDrivers", searchers);
		result.put("worthEntering", freeSlots > searchers);
		return List.of(result);
	}

	private List<Object> bestSectionInLot(String lotId, Map<String, Object> attrs) {
		String slotKindFilter = (String) attrs.get("slotKindFilter");
		Map<String, Long> freePerSection = new HashMap<>();
		objectRepository.findAllByTypeAndStatusAndActiveTrue(ObjectType.PARKING_SLOT, "FREE").stream()
				.map(b -> ParkingSlotDetails.from(b.getObjectDetails()))
				.filter(d -> lotId.equals(d.parentLotId()))
				.filter(d -> slotKindFilter == null || slotKindFilter.equals(d.slotKind()))
				.forEach(d -> {
					if (d.parentSectionId() != null) freePerSection.merge(d.parentSectionId(), 1L, Long::sum);
				});
		String best = freePerSection.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);
		Map<String, Object> result = new HashMap<>();
		result.put("lotId", lotId);
		result.put("bestSectionId", best);
		result.put("freeSlots", best == null ? 0 : freePerSection.get(best));
		result.put("perSection", freePerSection);
		return List.of(result);
	}

	/**
	 * Predicts future parking availability using an external ML microservice.
	 * * If the target timestamp is missing or the ML service fails, it safely falls
	 * back to returning the current number of free slots. It also ensures the
	 * prediction never exceeds the lot's total physical capacity.
	 *
	 * @param lotId The ID of the parking lot.
	 * @param attrs Contains the "targetTimestamp" (ISO-8601, trust me Eyal) for the prediction.
	 * @return A list containing the lot ID, target time, predicted slots, and a status note.
	 */
	@SuppressWarnings("unchecked")
	private List<Object> predictAvailability(String lotId, Map<String, Object> attrs) {
		Object targetTimeObj = attrs.get("targetTimestamp");
		String targetTimeStr = targetTimeObj != null ? String.valueOf(targetTimeObj) : null;

		// 1. Single-pass active slot count for totalSlots and currentFree
		List<ObjectEntity> activeSlotsInLot = objectRepository.findAllByTypeAndActiveTrue(ObjectType.PARKING_SLOT).stream()
				.filter(b -> lotId.equals(ParkingSlotDetails.from(b.getObjectDetails()).parentLotId()))
				.toList();

		long totalSlots = activeSlotsInLot.size();
		long currentFree = activeSlotsInLot.stream()
				.filter(b -> "FREE".equals(b.getStatus()))
				.count();

		long predictedFreeSlots = currentFree;
		Long predictedFreeLower = null;
		Long predictedFreeUpper = null;
		String note = "Prediction generated by ML Microservice";

		// 2. Call the Python ML Microservice
		try {
			if (targetTimeStr != null) {
				String pythonMlUrl = this.mlServiceBaseUrl + "/predict";

				// Prepare the JSON request body
				Map<String, Object> requestBody = new HashMap<>();
				requestBody.put("lotId", lotId);
				requestBody.put("targetTimestamp", targetTimeStr);
				requestBody.put("currentFreeSlots", currentFree);
				requestBody.put("totalSlots", totalSlots);

				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.APPLICATION_JSON);
				HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

				// Send POST request and receive response
				@SuppressWarnings("rawtypes")
				ResponseEntity<Map> response = this.restTemplate.postForEntity(pythonMlUrl, requestEntity, Map.class);
				Map<String, Object> mlResponse = response.getBody();

				// 3. Extract the prediction result
				if (mlResponse != null && mlResponse.containsKey("predictedFreeSlots")) {
					// Use Number to handle both Integer and Double safely
					predictedFreeSlots = ((Number) mlResponse.get("predictedFreeSlots")).longValue();

					// Ensure the prediction is within physical boundaries
					predictedFreeSlots = Math.min(totalSlots, Math.max(0, predictedFreeSlots));

					if (mlResponse.get("predictedFreeLower") instanceof Number lowerNum) {
						predictedFreeLower = Math.min(totalSlots, Math.max(0, lowerNum.longValue()));
					}
					if (mlResponse.get("predictedFreeUpper") instanceof Number upperNum) {
						predictedFreeUpper = Math.min(totalSlots, Math.max(0, upperNum.longValue()));
					}
				} else {
					note = "ML Service response invalid. Fallback to current free slots.";
				}
			} else {
				note = "No targetTimestamp provided. Returning current free slots.";
			}
		} catch (Exception ex) {
			// Fallback in case of network error or if the ML service is down
			note = "ML Service unavailable. Fallback to current free slots. Error: " + ex.getMessage();
			logger.warn("Prediction service failed for lot {}: {}", lotId, ex.getMessage());
		}

		// 4. Build and return the response
		Map<String, Object> result = new HashMap<>();
		result.put("lotId", lotId);
		result.put("targetTimestamp", targetTimeStr);
		result.put("predictedFreeSlots", predictedFreeSlots);
		if (predictedFreeLower != null) {
			result.put("predictedFreeLower", predictedFreeLower);
		}
		if (predictedFreeUpper != null) {
			result.put("predictedFreeUpper", predictedFreeUpper);
		}
		result.put("note", note);

		return List.of(result);
	}

	private List<Object> runHealthCheck(String lotId) {
		List<Object> faulty = new ArrayList<>();
		objectRepository.findAllByTypeAndActiveTrue(ObjectType.PARKING_SLOT).stream()
				.filter(b -> {
					ParkingSlotDetails d = ParkingSlotDetails.from(b.getObjectDetails());
					return lotId.equals(d.parentLotId());
				})
				.forEach(slot -> {
					ParkingSlotDetails det = ParkingSlotDetails.from(slot.getObjectDetails());
					if (!"OK".equals(det.healthStatus())) {
						Map<String, Object> info = new HashMap<>();
						info.put("slotId", slot.getObjectId());
						info.put("alias", slot.getAlias());
						info.put("healthStatus", det.healthStatus());
						faulty.add(info);
					}
				});
		return faulty;
	}

	// ──────────────────── PARKING_SECTION commands ────────────────────

	private List<Object> availabilityInSection(String sectionId, Map<String, Object> attrs) {
		String slotKindFilter = (String) attrs.get("slotKindFilter");
		List<ObjectEntity> sectionSlots = objectRepository.findAllByTypeAndActiveTrue(ObjectType.PARKING_SLOT).stream()
				.filter(b -> {
					ParkingSlotDetails d = ParkingSlotDetails.from(b.getObjectDetails());
					return sectionId.equals(d.parentSectionId())
							&& (slotKindFilter == null || slotKindFilter.equals(d.slotKind()));
				})
				.toList();
		long totalSlots = sectionSlots.size();
		long freeSlots = sectionSlots.stream()
				.filter(b -> "FREE".equals(b.getStatus()))
				.count();
		Map<String, Object> result = new HashMap<>();
		result.put("sectionId", sectionId);
		result.put("freeSlots", freeSlots);
		result.put("totalSlots", totalSlots);
		return List.of(result);
	}

	// ──────────────────── PARKING_SLOT commands ────────────────────

	private List<Object> checkSlotAvailability(String slotId) {
		ObjectBoundary slot = objectsService.getSpecificObject(systemID, slotId)
				.orElseThrow(() -> new NotFoundException("slot not found: " + slotId));
		ParkingSlotDetails det = ParkingSlotDetails.from(slot.getObjectDetails());
		Map<String, Object> result = new HashMap<>();
		result.put("slotId", slotId);
		result.put("alias", slot.getAlias());
		result.put("status", slot.getStatus());
		result.put("isFree", "FREE".equals(slot.getStatus()));
		result.put("slotKind", det.slotKind());
		result.put("healthStatus", det.healthStatus());
		return List.of(result);
	}

	private List<Object> updateSlotState(String slotId, Map<String, Object> attrs) {
		String newState = (String) attrs.get("newState");
		if (isBlank(newState)) {
			throw new InvalidInputException("updateSlotState requires a non-blank newState attribute");
		}
		ObjectBoundary current = objectsService.getSpecificObject(systemID, slotId)
				.orElseThrow(() -> new NotFoundException("slot not found: " + slotId));
		ParkingSlotDetails det = ParkingSlotDetails.from(current.getObjectDetails());
		ObjectBoundary update = new ObjectBoundary();
		update.setStatus(newState);
		update.setObjectDetails(det.withHealth("OK", System.currentTimeMillis()).toMap());
		objectsService.updateObject(systemID, slotId, update);
		return List.of(Map.of("slotId", slotId, "newState", newState));
	}

	// ──────────────────── VEHICLE commands ────────────────────

	private List<Object> vehicleEvent(String targetId, Map<String, Object> attrs, CommandEntity origin, String state) {
		ObjectBoundary vehicle = new ObjectBoundary();
		vehicle.setType(ObjectType.VEHICLE);
		vehicle.setStatus(state);
		vehicle.setActive(true);
		vehicle.setAlias("vehicle of " + origin.getInvokedByEmail());
		String lotId = (String) attrs.get("lotId");
		VehicleDetails det = new VehicleDetails(
				lotId != null ? lotId : targetId,
				origin.getInvokedByEmail(),
				new Date().toString(),
				(String) attrs.get("plate"));
		vehicle.setObjectDetails(det.toMap());
		if (origin.getInvokedByEmail() != null) {
			vehicle.setCreatedBy(new CreatedBy(new UserId(origin.getInvokedByEmail(), origin.getInvokedBySystemID())));
		}
		ObjectBoundary saved = objectsService.createObject(vehicle);
		return List.of(saved);
	}

	// ──────────────────── Admin / history ────────────────────

	@Override
	@Deprecated
	public List<CommandBoundary> getAllCommandsHistory() {
		throw new GoneException("Replaced by pagination-supporting getAllCommandsHistory(size, page)");
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommandBoundary> getAllCommandsHistory(int size, int page) {
		if (size < 1)
			throw new InvalidInputException("size must be at least 1");
		if (page < 0)
			throw new InvalidInputException("page may not be negative");
		return commandRepository
				.findAll(org.springframework.data.domain.PageRequest.of(
						page, size, org.springframework.data.domain.Sort.by("id").ascending()))
				.map(this::toBoundary)
				.getContent();
	}

	@Override
	@Transactional
	public void deleteAllCommands() {
		commandRepository.deleteAll();
	}

	private boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private CommandBoundary toBoundary(CommandEntity e) {
		CommandBoundary b = new CommandBoundary();
		b.setId(new CommandId(e.getCommandId(), e.getSystemID()));
		b.setCommand(e.getCommand());
		if (e.getTargetObjectId() != null) {
			b.setTargetObject(new TargetObject(new ObjectId(e.getTargetObjectId(), e.getTargetSystemID())));
		}
		b.setInvocationTimestamp(e.getInvocationTimestamp());
		if (e.getInvokedByEmail() != null) {
			b.setInvokedBy(new InvokedBy(new UserId(e.getInvokedByEmail(), e.getInvokedBySystemID())));
		}
		b.setCommandAttributes(e.getCommandAttributes());
		return b;
	}
}
