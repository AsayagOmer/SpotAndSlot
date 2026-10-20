package ambient_invisible_intelligence.boundaries;

// inbound user representation used for create + update -- includes the password
public class UserWithPasswordBoundary {
	private String email;
	private String password;
	private String role;
	private String username;
	private String avatar;

	public UserWithPasswordBoundary() {}

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }
	public String getRole() { return role; }
	public void setRole(String role) { this.role = role; }
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getAvatar() { return avatar; }
	public void setAvatar(String avatar) { this.avatar = avatar; }
}
