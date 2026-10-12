package space.grayt.teremok.fx.ui;

import java.nio.file.*;
import java.util.*;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import space.grayt.teremok.client.contract.*;
import space.grayt.teremok.fx.audio.AudioService;
import space.grayt.teremok.fx.net.RestApi;

public final class RenderController {
    @FXML private Label workTitle, status;
    @FXML private VBox roles, outputs;
    @FXML private ComboBox<String> mode;
    @FXML private Button renderButton, refreshButton;
    private final Map<UUID, ComboBox<PublishedRolesResponse.PublishedRecording>> choices = new LinkedHashMap<>();
    private final AudioService audio = new AudioService();
    private final Set<Path> previews = new HashSet<>();
    private RestApi api;
    private Stage stage;
    private UUID workId;
    private RenderJobResponse job;
    private Timeline polling;
    private boolean pollingRequest, closed;

    public void initializeApp(RestApi api, Stage stage, CatalogTextWorkResponse work) {
        this.api = api; this.stage = stage; workId = work.id(); workTitle.setText(work.name());
        mode.getItems().setAll("Один MP3-файл", "Отдельный MP3 для каждого сегмента"); mode.getSelectionModel().selectFirst();
        refresh();
    }
    @FXML private void refresh() {
        refreshButton.setDisable(true); renderButton.setDisable(true); status.setText("Загружаем опубликованные роли…");
        FxTasks.run(() -> api.published(workId), response -> {
            if (closed) return; refreshButton.setDisable(false); roles.getChildren().clear(); choices.clear();
            for (var role : response.roles()) {
                var selector = new ComboBox<PublishedRolesResponse.PublishedRecording>();
                selector.setMaxWidth(Double.MAX_VALUE);
                selector.getItems().setAll(role.recordings());
                selector.setConverter(new StringConverter<>() {
                    public String toString(PublishedRolesResponse.PublishedRecording item) {
                        if (item == null) return "";
                        return "Вариант " + (selector.getItems().indexOf(item) + 1) + " · " + Ui.date(item.publishedAt())
                                + (api.userId().equals(item.userId()) ? " · ваша озвучка" : " · другой участник");
                    }
                    public PublishedRolesResponse.PublishedRecording fromString(String value) { return null; }
                });
                selector.setPromptText(role.recordings().isEmpty() ? "Для этой роли пока нет озвучек" : "Выберите озвучку роли");
                choices.put(role.roleId(), selector);
                var row = new VBox(7, new Label(role.roleName()), selector); row.getStyleClass().add("card"); roles.getChildren().add(row);
            }
            boolean complete = !choices.isEmpty() && choices.values().stream().allMatch(c -> !c.getItems().isEmpty());
            renderButton.setDisable(!complete || job != null && "PENDING".equals(job.status()));
            status.setText(complete ? "Выберите одну озвучку для каждой роли" : "Для сборки нужны опубликованные озвучки всех ролей");
        }, error -> { if (!closed) { refreshButton.setDisable(false); status.setText("Опубликованные роли недоступны"); Ui.error(stage, error); } });
    }

    @FXML private void render() {
        var selected = new ArrayList<CreateRenderRequest.RoleSelection>();
        for (var entry : choices.entrySet()) {
            var value = entry.getValue().getValue();
            if (value == null) { status.setText("Выберите озвучку для каждой роли"); return; }
            selected.add(new CreateRenderRequest.RoleSelection(entry.getKey(), value.id()));
        }
        var outputMode = mode.getSelectionModel().getSelectedIndex() == 0 ? CreateRenderRequest.OutputMode.SINGLE_FILE : CreateRenderRequest.OutputMode.BY_SEGMENTS;
        renderButton.setDisable(true); outputs.getChildren().clear(); status.setText("Отправляем задание на сборку…");
        FxTasks.run(() -> api.render(new CreateRenderRequest(workId, outputMode, selected)), response -> {
            if (closed) return; job = response;
            status.setText("Собираем аудио. Можно оставить окно открытым.");
            polling = new Timeline(new KeyFrame(javafx.util.Duration.seconds(2), event -> checkJob()));
            polling.setCycleCount(Timeline.INDEFINITE); polling.play(); checkJob();
        }, error -> { if (!closed) { renderButton.setDisable(false); Ui.error(stage, error); } });
    }

    private void checkJob() {
        if (closed || pollingRequest || job == null) return;
        pollingRequest = true; UUID id = job.id();
        FxTasks.run(() -> api.job(id), response -> {
            pollingRequest = false; if (closed) return; job = response;
            if ("COMPLETED".equals(job.status())) {
                polling.stop(); renderButton.setDisable(false); showOutputs(); status.setText("Аудиосборка готова");
            } else if ("FAILED".equals(job.status())) {
                polling.stop(); renderButton.setDisable(false); status.setText("Не удалось собрать аудио");
                Ui.error(stage, new IllegalStateException("Сборка не удалась. Проверьте, что все исходники — корректные аудиофайлы."));
            }
        }, error -> {
            pollingRequest = false;
            if (!closed) {
                polling.stop(); renderButton.setDisable(false); status.setText("Нет связи. Нажмите «Проверить сборку», чтобы продолжить.");
                Ui.error(stage, error);
            }
        });
    }
    @FXML private void resume() { checkJob(); if (polling != null && job != null && "PENDING".equals(job.status())) polling.play(); }

    private void showOutputs() {
        outputs.getChildren().clear();
        for (int i = 0; i < job.outputs().size(); i++) {
            final int index = i; UUID id = job.id();
            String name = job.outputs().size() == 1 ? "Озвучка целиком" : "Сегмент " + (i + 1);
            var save = new Button("Сохранить MP3"); save.setOnAction(event -> {
                var path = Ui.save(stage, "recording-" + (index + 1) + ".mp3", "Сохранить озвучку", "mp3"); if (path == null) return;
                save.setDisable(true);
                FxTasks.run(() -> api.output(id, index, path), result -> {
                    save.setDisable(false); if (!closed) status.setText("Сохранено: " + result.path());
                }, error -> { save.setDisable(false); if (!closed) Ui.error(stage, error); });
            });
            var play = new Button("Слушать"); play.setOnAction(event -> {
                play.setDisable(true);
                FxTasks.run(() -> {
                    var path = Files.createTempFile("teremok-render-", ".mp3");
                    try { api.output(id, index, path); return path; }
                    catch (Exception error) { Files.deleteIfExists(path); throw error; }
                }, path -> {
                    play.setDisable(false);
                    if (closed) { deletePreview(path); return; }
                    previews.add(path);
                    audio.play(path, error -> Ui.error(stage, error), () -> status.setText("Прослушивание завершено"));
                }, error -> { play.setDisable(false); if (!closed) Ui.error(stage, error); });
            });
            var row = new HBox(12, new Label(name), play, save); row.getStyleClass().add("card"); outputs.getChildren().add(row);
        }
    }
    @FXML private void stopPlayback() { audio.stopPlayback(); }
    private void deletePreview(Path path) {
        FxTasks.run(() -> { Files.deleteIfExists(path); return true; }, ignored -> {}, error -> error.printStackTrace(System.err));
    }
    public void close() {
        closed = true; if (polling != null) polling.stop(); audio.close(); previews.forEach(this::deletePreview); previews.clear();
    }
}
