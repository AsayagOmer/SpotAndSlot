package ambient_invisible_intelligence.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when trying to create a resource that already exists. Mapped to HTTP 400 by Spring.
@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class AlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AlreadyExistsException() {
		super();
	}

	public AlreadyExistsException(String message) {
		super(message);
	}

	public AlreadyExistsException(String message, Throwable cause) {
		super(message, cause);
	}

	public AlreadyExistsException(Throwable cause) {
		super(cause);
	}
}
