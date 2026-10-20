package ambient_invisible_intelligence.data;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "objects")
public class ObjectEntity {
	@Id
	private String id;             // composite "objectId|systemID"
	private String objectId;
	private String systemID;
	@Column(name = "obj_type")
	private String type;
	private String alias;
	@Column(name = "obj_status")
	private String status;
	private Boolean active;
	@Temporal(TemporalType.TIMESTAMP)
	private Date creationTimestamp;
	private String createdByEmail;
	private String createdBySystemID;
	private Double lat;
	private Double lng;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private Map<String, Object> objectDetails;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "object_children", joinColumns = @JoinColumn(name = "owner_id"))
	@Column(name = "child_id")
	private Set<String> childrenIds = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "object_parents", joinColumns = @JoinColumn(name = "owner_id"))
	@Column(name = "parent_id")
	private Set<String> parentIds = new HashSet<>();

	public ObjectEntity() {}

	public static String key(String objectId, String systemID) {
		return objectId + "|" + systemID;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getObjectId() { return objectId; }
	public void setObjectId(String objectId) { this.objectId = objectId; }
	public String getSystemID() { return systemID; }
	public void setSystemID(String systemID) { this.systemID = systemID; }
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
	public String getCreatedByEmail() { return createdByEmail; }
	public void setCreatedByEmail(String email) { this.createdByEmail = email; }
	public String getCreatedBySystemID() { return createdBySystemID; }
	public void setCreatedBySystemID(String sid) { this.createdBySystemID = sid; }
	public Double getLat() { return lat; }
	public void setLat(Double lat) { this.lat = lat; }
	public Double getLng() { return lng; }
	public void setLng(Double lng) { this.lng = lng; }
	public Map<String, Object> getObjectDetails() { return objectDetails; }
	public void setObjectDetails(Map<String, Object> objectDetails) { this.objectDetails = objectDetails; }

	public Set<String> getChildrenIds() {
		if (childrenIds == null) childrenIds = new HashSet<>();
		return childrenIds;
	}
	public void setChildrenIds(Set<String> childrenIds) { this.childrenIds = childrenIds; }

	public Set<String> getParentIds() {
		if (parentIds == null) parentIds = new HashSet<>();
		return parentIds;
	}
	public void setParentIds(Set<String> parentIds) { this.parentIds = parentIds; }
}
