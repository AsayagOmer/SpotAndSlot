package ambient_invisible_intelligence.boundaries;

// wraps a UserId so the JSON shape matches the spec:  {"userId":{"email":..., "systemID":...}}
public class CreatedBy {
	private UserId userId;

	public CreatedBy() {}
	public CreatedBy(UserId userId) { this.userId = userId; }
	public UserId getUserId() { return userId; }
	public void setUserId(UserId userId) { this.userId = userId; }
}
