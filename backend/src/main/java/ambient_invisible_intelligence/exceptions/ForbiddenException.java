package ambient_invisible_intelligence.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when an authenticated user lacks permission for an action
// (e.g. not an ADMIN, or not the owner of the target lot). Mapped to HTTP 403.
@ResponseStatus(code = HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ForbiddenException() {
		super();
	}

	public ForbiddenException(String message) {
		super(message);
	}

	public ForbiddenException(String message, Throwable cause) {
		super(message, cause);
	}

	public ForbiddenException(Throwable cause) {
		super(cause);
	}
}
