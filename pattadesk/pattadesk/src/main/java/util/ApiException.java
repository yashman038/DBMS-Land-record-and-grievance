package util;

/** A problem the user should see (bad input, no permission, not found). */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
