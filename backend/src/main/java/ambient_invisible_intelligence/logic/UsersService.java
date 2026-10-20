package ambient_invisible_intelligence.logic;

import java.util.List;
import java.util.Optional;

import ambient_invisible_intelligence.boundaries.UserBoundary;

public interface UsersService {
	UserBoundary createUser(UserBoundary user, String newPassword);
	Optional<UserBoundary> loginUser(String systemID, String userEmail, String password);
	void updateUser(String systemID, String userEmail, String existingPassword,
	                UserBoundary update, String updatedPassword);
	// Unbounded multi-fetch: replaced by pagination. Throws GoneException (410).
	@Deprecated
	List<UserBoundary> getAllUsers();
	List<UserBoundary> getAllUsers(int size, int page);
	void deleteAllUsers();
}
