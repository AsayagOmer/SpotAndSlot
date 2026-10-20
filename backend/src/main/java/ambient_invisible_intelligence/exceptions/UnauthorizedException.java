package ambient_invisible_intelligence.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when authentication fails (e.g. wrong credentials). Mapped to HTTP 401 by Spring.
@ResponseStatus(code = HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UnauthorizedException() {
		super();
	}

	public UnauthorizedException(String message) {
		super(message);
	}

	public UnauthorizedException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnauthorizedException(Throwable cause) {
		super(cause);
	}
}
