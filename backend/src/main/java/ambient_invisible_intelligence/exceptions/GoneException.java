package ambient_invisible_intelligence.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown by deprecated non-paginated multi-fetch operations. Mapped to HTTP 410 by Spring.
@ResponseStatus(code = HttpStatus.GONE)
public class GoneException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public GoneException(String message) {
		super(message);
	}
}
