package ambient_invisible_intelligence.boundaries;

public class UserId {
	private String email;
	private String systemID;

	public UserId() {}
	public UserId(String email, String systemID) {
		this.email = email;
		this.systemID = systemID;
	}
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getSystemID() { return systemID; }
	public void setSystemID(String systemID) { this.systemID = systemID; }
}
