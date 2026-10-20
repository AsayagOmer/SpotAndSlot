package ambient_invisible_intelligence.boundaries;

public class TargetObject {
	private ObjectId id;

	public TargetObject() {}
	public TargetObject(ObjectId id) { this.id = id; }
	public ObjectId getId() { return id; }
	public void setId(ObjectId id) { this.id = id; }
}
