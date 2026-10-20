package ambient_invisible_intelligence.boundaries;

import java.util.Date;
import java.util.Map;

public class ObjectBoundary {
	private ObjectId id;
	private String type;
	private String alias;
	private String status;
	private Boolean active;
	private Date creationTimestamp;
	private CreatedBy createdBy;
	private Location location;
	private Map<String, Object> objectDetails;

	public ObjectBoundary() {}

	public ObjectId getId() { return id; }
	public void setId(ObjectId id) { this.id = id; }
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }
	public String getAlias() { return alias; }
	public void setAlias(String alias) { this.alias = alias; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public Boolean getActive() { return active; }
	public void setActive(Boolean active) { this.active = active; }
	public Date getCreationTimestamp() { return creationTimestamp; }
	public void setCreationTimestamp(Date creationTimestamp) { this.creationTimestamp = creationTimestamp; }
	public CreatedBy getCreatedBy() { return createdBy; }
	public void setCreatedBy(CreatedBy createdBy) { this.createdBy = createdBy; }
	public Location getLocation() { return location; }
	public void setLocation(Location location) { this.location = location; }
	public Map<String, Object> getObjectDetails() { return objectDetails; }
	public void setObjectDetails(Map<String, Object> objectDetails) { this.objectDetails = objectDetails; }
}
