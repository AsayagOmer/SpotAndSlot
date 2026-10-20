package ambient_invisible_intelligence.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when a requested resource does not exist. Mapped to HTTP 404 by Spring.
@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NotFoundException() {
		super();
	}

	public NotFoundException(String message) {
		super(message);
	}

	public NotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public NotFoundException(Throwable cause) {
		super(cause);
	}
}
