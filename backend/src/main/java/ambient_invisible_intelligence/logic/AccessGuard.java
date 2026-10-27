package ambient_invisible_intelligence.logic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ambient_invisible_intelligence.data.ObjectEntity;
import ambient_invisible_intelligence.data.ObjectRepository;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.data.UserRepository;
import ambient_invisible_intelligence.data.UserRole;
import ambient_invisible_intelligence.exceptions.ForbiddenException;
import ambient_invisible_intelligence.exceptions.NotFoundException;
import ambient_invisible_intelligence.exceptions.UnauthorizedException;
import ambient_invisible_intelligence.smartparking.ObjectType;

/**
 * Sprint 4 authentication and role gates. Every guarded operation carries
 * userSystemID / userEmail / userPassword; the password is verified against
 * the stored user. Role model per the Sprint 4 instructions:
 * - ADMIN may perform only the Admin API operations
 * - OPERATOR creates/updates objects, binds relations, reads without limits
 * - END_USER reads active objects only and is the only role invoking commands
 */
@Component
public class AccessGuard {

	private final UserRepository userRepository;
	private final ObjectRepository objectRepository;
	private final String systemID;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public AccessGuard(UserRepository userRepository, ObjectRepository objectRepository,
			@Value("${spring.application.name}") String systemID) {
		this.userRepository = userRepository;
		this.objectRepository = objectRepository;
		this.systemID = systemID;
	}

	/**
	 * Verify raw password against stored BCrypt hash (with plaintext fallback for legacy rows).
	 */
	public boolean matchesPassword(String rawPassword, String storedPassword) {
		if (rawPassword == null || storedPassword == null)
			return false;
		if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
			return passwordEncoder.matches(rawPassword, storedPassword);
		}
		return storedPassword.equals(rawPassword);
	}

	public String encodePassword(String rawPassword) {
		return passwordEncoder.encode(rawPassword);
	}

	/** Verify identity + password. Throws 401 for missing/unknown/wrong credentials. */
	public UserEntity authenticate(String userSystemID, String email, String password) {
		if (email == null || email.isBlank() || password == null || password.isBlank())
			throw new UnauthorizedException("userEmail and userPassword are required");
		String sid = (userSystemID == null || userSystemID.isBlank()) ? systemID : userSystemID;
		UserEntity user = userRepository.findById(UserEntity.key(email, sid))
				.orElseThrow(() -> new UnauthorizedException("unknown user: " + email));
		if (!matchesPassword(password, user.getPassword()))
			throw new UnauthorizedException("invalid credentials for " + email);
		return user;
	}

	/** Authenticate and require the ADMIN role (Admin API only). */
	public UserEntity authenticateAdmin(String userSystemID, String email, String password) {
		return requireRole(authenticate(userSystemID, email, password), UserRole.ADMIN);
	}

	/** Authenticate and require the OPERATOR role (object create/update/bind). */
	public UserEntity authenticateOperator(String userSystemID, String email, String password) {
		return requireRole(authenticate(userSystemID, email, password), UserRole.OPERATOR);
	}

	/** Authenticate and require the END_USER role (command invocation). */
	public UserEntity authenticateEndUser(String userSystemID, String email, String password) {
		return requireRole(authenticate(userSystemID, email, password), UserRole.END_USER);
	}

	/**
	 * Authenticate a reader: OPERATOR (sees everything) or END_USER (active
	 * objects only). ADMINs may use only the Admin API, so reads are forbidden.
	 */
	public UserEntity authenticateReader(String userSystemID, String email, String password) {
		UserEntity user = authenticate(userSystemID, email, password);
		if (UserRole.ADMIN.name().equals(user.getRole()))
			throw new ForbiddenException("ADMIN users may only use the Admin API");
		return user;
	}

	/**
	 * Extension: creating ADMIN/OPERATOR accounts requires an existing admin
	 * (actingEmail param on POST /users; identity-only per the user-API rules,
	 * or verified against actingPassword when supplied).
	 */
	public UserEntity requireAdminByEmail(String email, String userSystemID) {
		return requireAdminByEmail(email, userSystemID, null);
	}

	public UserEntity requireAdminByEmail(String email, String userSystemID, String actingPassword) {
		if (email == null || email.isBlank())
			throw new UnauthorizedException("acting user email is required");
		String sid = (userSystemID == null || userSystemID.isBlank()) ? systemID : userSystemID;
		UserEntity user = userRepository.findById(UserEntity.key(email, sid))
				.orElseThrow(() -> new UnauthorizedException("unknown user: " + email));
		if (actingPassword != null && !matchesPassword(actingPassword, user.getPassword()))
			throw new UnauthorizedException("invalid credentials for acting admin: " + email);
		return requireRole(user, UserRole.ADMIN);
	}

	public UserEntity requireRole(UserEntity user, UserRole role) {
		if (!role.name().equals(user.getRole()))
			throw new ForbiddenException(
					"user " + user.getEmail() + " has role " + user.getRole() + "; " + role + " is required");
		return user;
	}

	public static boolean isEndUser(UserEntity user) {
		return UserRole.END_USER.name().equals(user.getRole());
	}

	/**
	 * Extension beyond the spec (kept without changing the REST API): each lot
	 * is controlled by the OPERATOR who created it; sections/slots follow the
	 * parent lot.
	 */
	public void requireOwnsLot(UserEntity operator, String lotObjectId) {
		ObjectEntity lot = objectRepository.findById(ObjectEntity.key(lotObjectId, systemID))
				.orElseThrow(() -> new NotFoundException("parking lot not found: " + lotObjectId));
		if (!ObjectType.PARKING_LOT.equals(lot.getType()))
			throw new ForbiddenException("object is not a parking lot: " + lotObjectId);
		if (!operator.getEmail().equals(lot.getCreatedByEmail()))
			throw new ForbiddenException(
					"user " + operator.getEmail() + " does not own lot " + lotObjectId);
	}

	/** Ownership check on a lot, section or slot (resolves the owning lot). */
	public void requireOwnsTarget(UserEntity operator, String targetObjectId) {
		ObjectEntity target = objectRepository.findById(ObjectEntity.key(targetObjectId, systemID))
				.orElseThrow(() -> new NotFoundException("object not found: " + targetObjectId));
		String lotId = owningLotId(target);
		if (lotId == null)
			throw new ForbiddenException("cannot determine owning lot for object " + targetObjectId);
		requireOwnsLot(operator, lotId);
	}

	// The id of the lot that owns this object (itself if it is a lot).
	private String owningLotId(ObjectEntity o) {
		if (ObjectType.PARKING_LOT.equals(o.getType()))
			return o.getObjectId();
		if (o.getObjectDetails() == null)
			return null;
		Object parentLotId = o.getObjectDetails().get("parentLotId");
		return parentLotId == null ? null : String.valueOf(parentLotId);
	}
}
