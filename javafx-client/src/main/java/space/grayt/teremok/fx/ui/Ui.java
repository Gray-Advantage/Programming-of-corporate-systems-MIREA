package space.grayt.teremok.fx.ui;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import javafx.scene.control.*;
import javafx.stage.*;
public final class Ui {
    private Ui() {}
    public static void error(Window owner, Throwable error) {
        error.printStackTrace(System.err); // Diagnostics stay in logs, never in the user dialog.
        String text = message(error);
        var alert = new Alert(Alert.AlertType.ERROR, text, ButtonType.OK);
        alert.setTitle("Ошибка");
        alert.initOwner(owner); alert.setHeaderText("Не удалось выполнить действие"); alert.showAndWait();
    }
    public static String message(Throwable error) {
        return error instanceof ConnectException || error instanceof HttpTimeoutException
                ? "Нет связи с backend. Проверьте адрес сервера и запуск Docker."
                : error instanceof javafx.scene.media.MediaException
                ? "Не удалось воспроизвести аудио. Формат может не поддерживаться. Готовый файл можно сохранить и открыть в другом проигрывателе."
                : error.getMessage() == null ? "Не удалось выполнить действие. Повторите попытку." : error.getMessage();
    }
    public static boolean confirm(Window owner, String text) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Подтверждение");
        alert.initOwner(owner); alert.setHeaderText("Подтвердите действие");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    public static Path save(Window owner, String name, String title, String extension) {
        var picker = new FileChooser(); picker.setTitle(title); picker.setInitialFileName(name);
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter(extension.toUpperCase(), "*." + extension));
        var result = picker.showSaveDialog(owner);
        return result == null ? null : result.toPath();
    }
    public static String bytes(long size) {
        return String.format(java.util.Locale.ROOT, "%.1f МБ", size / 1048576.0);
    }
    public static String date(java.time.Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(instant);
    }
}
