package ambient_invisible_intelligence.logic;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.boundaries.UserId;
import ambient_invisible_intelligence.data.UserEntity;
import ambient_invisible_intelligence.data.UserRepository;
import ambient_invisible_intelligence.data.UserRole;
import ambient_invisible_intelligence.exceptions.AlreadyExistsException;
import ambient_invisible_intelligence.exceptions.GoneException;
import ambient_invisible_intelligence.exceptions.InvalidInputException;
import ambient_invisible_intelligence.exceptions.NotFoundException;

@Service
public class UsersServiceImpl implements UsersService {

	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final UserRepository userRepository;
	private final String systemID;
	private Log logger = LogFactory.getLog(UsersServiceImpl.class);

	public UsersServiceImpl(UserRepository userRepository,
			@Value("${spring.application.name}") String systemID) {
		this.userRepository = userRepository;
		this.systemID = systemID;
	}

	@Override
	@Transactional
	public UserBoundary createUser(UserBoundary user, String newPassword) {
		if (user == null || user.getUserId() == null)
			throw new InvalidInputException("user and userId are required");
		String email = user.getUserId().getEmail();
		if (email == null || email.isBlank())
			throw new InvalidInputException("email is required");
		if (!EMAIL_PATTERN.matcher(email).matches())
			throw new InvalidInputException("invalid email address: " + email);
		validatePassword(newPassword);
		validateRole(user.getRole());
		if (user.getUsername() == null || user.getUsername().isBlank())
			throw new InvalidInputException("username is required and may not be blank");
		if (user.getAvatar() == null || user.getAvatar().isBlank())
			throw new InvalidInputException("avatar is required and may not be blank");

		String key = UserEntity.key(email, systemID);
		if (userRepository.existsById(key))
			throw new AlreadyExistsException("user already exists: " + email);

		UserEntity e = new UserEntity();
		e.setId(key);
		e.setEmail(email);
		e.setSystemID(systemID);
		e.setPassword(newPassword);
		e.setRole(user.getRole());
		e.setUsername(user.getUsername());
		e.setAvatar(user.getAvatar());
		userRepository.save(e);
		this.logger.trace("User created: " + e.getEmail());
		return toBoundary(e);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserBoundary> loginUser(String systemID, String userEmail, String password) {
		return userRepository.findById(UserEntity.key(userEmail, systemID))
				.filter(u -> u.getPassword().equals(password))
				.map(this::toBoundary);
	}

	@Override
	@Transactional
	public void updateUser(String systemID, String userEmail, String existingPassword,
	                       UserBoundary update, String updatedPassword) {
		UserEntity e = userRepository.findById(UserEntity.key(userEmail, systemID))
				.orElseThrow(() -> new NotFoundException("user not found: " + userEmail));
		if (!e.getPassword().equals(existingPassword))
			throw new InvalidInputException("invalid password");
		// email and systemID are immutable
		if (updatedPassword != null) {
			validatePassword(updatedPassword);
			e.setPassword(updatedPassword);
		}
		if (update.getRole() != null) {
			validateRole(update.getRole());
			e.setRole(update.getRole());
		}
		// null means "leave unchanged"; a blank string is invalid input
		if (update.getUsername() != null) {
			if (update.getUsername().isBlank())
				throw new InvalidInputException("username may not be blank");
			e.setUsername(update.getUsername());
		}
		if (update.getAvatar() != null) {
			if (update.getAvatar().isBlank())
				throw new InvalidInputException("avatar may not be blank");
			e.setAvatar(update.getAvatar());
		}
		userRepository.save(e);
		this.logger.trace("User updated: " + e.getEmail());
	}

	@Override
	@Deprecated
	public List<UserBoundary> getAllUsers() {
		throw new GoneException("Replaced by pagination-supporting getAllUsers(size, page)");
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserBoundary> getAllUsers(int size, int page) {
		if (size < 1)
			throw new InvalidInputException("size must be at least 1");
		if (page < 0)
			throw new InvalidInputException("page may not be negative");
		return userRepository
				.findAll(org.springframework.data.domain.PageRequest.of(
						page, size, org.springframework.data.domain.Sort.by("id").ascending()))
				.map(this::toBoundary)
				.getContent();
	}

	@Override
	@Transactional
	public void deleteAllUsers() {
		userRepository.deleteAll();
		this.logger.trace("Deleted all users");
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 5)
			throw new InvalidInputException("password must be at least 5 characters");
		boolean hasDigit = password.chars().anyMatch(Character::isDigit);
		boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
		if (!hasDigit || !hasSpecial)
			throw new InvalidInputException(
					"password must contain at least one digit and one non-alphanumeric character");
	}

	private void validateRole(String role) {
		if (role == null || role.isBlank())
			throw new InvalidInputException("role is required. Must be one of: ADMIN, OPERATOR, END_USER");
		try {
			UserRole.valueOf(role);
		} catch (IllegalArgumentException e) {
			throw new InvalidInputException("invalid role: " + role +
					". Must be one of: ADMIN, OPERATOR, END_USER");
		}
	}

	private UserBoundary toBoundary(UserEntity e) {
		UserBoundary b = new UserBoundary();
		b.setUserId(new UserId(e.getEmail(), e.getSystemID()));
		b.setRole(e.getRole());
		b.setUsername(e.getUsername());
		b.setAvatar(e.getAvatar());
		return b;
	}
}
