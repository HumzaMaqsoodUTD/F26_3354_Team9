package occupan.domain;

/** Domain errors. The API layer turns each one into an HTTP status. */
public class DomainException extends RuntimeException {
    private final int statusCode;

    public DomainException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }

    public static DomainException validation(String message) { return new DomainException(400, message); }
    public static DomainException authentication(String message) { return new DomainException(401, message); }
    public static DomainException permissionDenied(String message) { return new DomainException(403, message); }
    public static DomainException notFound(String message) { return new DomainException(404, message); }
    public static DomainException conflict(String message) { return new DomainException(409, message); }
}
