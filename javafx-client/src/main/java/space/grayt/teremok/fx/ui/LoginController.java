package space.grayt.teremok.fx.ui;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import space.grayt.teremok.fx.TeremokApplication;
import space.grayt.teremok.fx.net.RestApi;

public final class LoginController {
    @FXML private TextField address, login;
    @FXML private PasswordField password;
    @FXML private Label status;
    @FXML private VBox form;
    private TeremokApplication app;
    public void initializeApp(TeremokApplication app) {
        this.app = app;
        address.setText(System.getProperty("teremok.api", System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080")));
    }
    @FXML private void signIn() { authenticate(false); }
    @FXML private void register() { authenticate(true); }
    private void authenticate(boolean register) {
        final RestApi api;
        try { api = new RestApi(address.getText()); }
        catch (RuntimeException error) { status.setText(error.getMessage()); return; }
        String username = login.getText(), secret = password.getText();
        if (username.isBlank() || secret.isBlank()) { status.setText("Введите логин и пароль"); return; }
        if (username.trim().length() > 100 || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            status.setText("Логин — до 100 символов, пароль — до 72 байт UTF-8"); return;
        }
        form.setDisable(true); status.setText(register ? "Создаём аккаунт…" : "Входим…");
        FxTasks.run(() -> { api.authenticate(username, secret, register); return api; }, result -> {
            form.setDisable(false); password.clear();
            try { app.signedIn(result); }
            catch (Exception error) { Ui.error(app.stage(), error); }
        }, error -> { form.setDisable(false); status.setText(Ui.message(error)); });
    }
}
