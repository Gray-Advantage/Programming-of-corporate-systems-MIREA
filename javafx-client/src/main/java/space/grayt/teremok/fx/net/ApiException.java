package space.grayt.teremok.fx.net;
import java.io.IOException;
public class ApiException extends IOException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
