package ambient_invisible_intelligence.data;

import java.util.Date;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "commands")
public class CommandEntity {
	@Id
	private String id;
	private String commandId;
	private String systemID;
	@Column(name = "cmd")
	private String command;
	private String targetObjectId;
	private String targetSystemID;
	@Temporal(TemporalType.TIMESTAMP)
	private Date invocationTimestamp;
	private String invokedByEmail;
	private String invokedBySystemID;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private Map<String, Object> commandAttributes;

	public CommandEntity() {}

	public static String key(String commandId, String systemID) {
		return commandId + "|" + systemID;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getCommandId() { return commandId; }
	public void setCommandId(String commandId) { this.commandId = commandId; }
	public String getSystemID() { return systemID; }
	public void setSystemID(String systemID) { this.systemID = systemID; }
	public String getCommand() { return command; }
	public void setCommand(String command) { this.command = command; }
	public String getTargetObjectId() { return targetObjectId; }
	public void setTargetObjectId(String targetObjectId) { this.targetObjectId = targetObjectId; }
	public String getTargetSystemID() { return targetSystemID; }
	public void setTargetSystemID(String targetSystemID) { this.targetSystemID = targetSystemID; }
	public Date getInvocationTimestamp() { return invocationTimestamp; }
	public void setInvocationTimestamp(Date ts) { this.invocationTimestamp = ts; }
	public String getInvokedByEmail() { return invokedByEmail; }
	public void setInvokedByEmail(String email) { this.invokedByEmail = email; }
	public String getInvokedBySystemID() { return invokedBySystemID; }
	public void setInvokedBySystemID(String sid) { this.invokedBySystemID = sid; }
	public Map<String, Object> getCommandAttributes() { return commandAttributes; }
	public void setCommandAttributes(Map<String, Object> attrs) { this.commandAttributes = attrs; }
}
