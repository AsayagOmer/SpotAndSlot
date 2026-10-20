package ambient_invisible_intelligence.data;

import java.util.Map;

class EntityUtils {

	static String mapToJson(Map<String, Object> m) {
		if (m == null) return "{}";
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> {
			sb.append("\"").append(k).append("\": ");
			if (v instanceof String) sb.append("\"").append(v).append("\"");
			else sb.append(v);
			sb.append(", ");
		});
		if (!m.isEmpty()) sb.setLength(sb.length() - 2);
		return sb.append("}").toString();
	}
}
