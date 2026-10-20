package ambient_invisible_intelligence.boundaries;

public class CommandId {
	private String commandId;
	private String systemID;

	public CommandId() {}
	public CommandId(String commandId, String systemID) {
		this.commandId = commandId;
		this.systemID = systemID;
	}
	public String getCommandId() { return commandId; }
	public void setCommandId(String commandId) { this.commandId = commandId; }
	public String getSystemID() { return systemID; }
	public void setSystemID(String systemID) { this.systemID = systemID; }
}
