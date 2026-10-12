package space.grayt.teremok.fx.ui;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.StringConverter;
import space.grayt.teremok.client.contract.*;
import space.grayt.teremok.fx.audio.AudioService;
import space.grayt.teremok.fx.net.RestApi;

public final class RecordingController {
    @FXML private VBox body, tools;
    @FXML private Label workTitle, progress, status;
    @FXML private TextArea fragmentText;
    @FXML private ComboBox<TextWorkContentResponse.VoicePart> role;
    @FXML private TableView<FragmentRow> fragments;
    @FXML private TableColumn<FragmentRow, String> segmentColumn, fragmentColumn, textColumn, selectedColumn;
    @FXML private TableView<DraftRecordingResponse> drafts;
    @FXML private TableColumn<DraftRecordingResponse, String> fileColumn, sizeColumn, dateColumn;
    @FXML private Button startButton, stopButton, retryButton;
    private final AudioService audio = new AudioService();
    private final Map<UUID, UUID> selections = new HashMap<>();
    private final Set<Path> temporaryFiles = ConcurrentHashMap.newKeySet();
    private final List<FragmentRow> rows = new ArrayList<>();
    private RestApi api;
    private Stage stage;
    private TextWorkContentResponse content;
    private Map<UUID, List<DraftRecordingResponse>> variants = Map.of();
    private boolean busy, recording, closed;
    private UUID recordingFragment;
    private UUID pendingFragment;
    private Path pending;
    private Timeline recordingTimer;
    private long began;

    @FXML private void initialize() {
        segmentColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().segment()));
        fragmentColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(Integer.toString(v.getValue().order())));
        textColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().text()));
        selectedColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(selections.containsKey(v.getValue().id()) ? "Выбран" : "—"));
        fileColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().originalFileName()));
        sizeColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(Ui.bytes(v.getValue().sizeBytes())));
        dateColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(Ui.date(v.getValue().createdAt())));
        role.setConverter(new StringConverter<>() {
            public String toString(TextWorkContentResponse.VoicePart value) { return value == null ? "" : value.name(); }
            public TextWorkContentResponse.VoicePart fromString(String value) { return null; }
        });
        role.valueProperty().addListener((o,previous,current) -> { if (current != null) changeRole(current); });
        fragments.getSelectionModel().selectedItemProperty().addListener((o,a,c) -> {
            fragmentText.setText(c == null ? "" : c.text()); showVariants();
        });
        drafts.setPlaceholder(new Label("Вариантов ещё нет. Запишите или загрузите аудио."));
        stopButton.setDisable(true); retryButton.setDisable(true);
    }

    public void initializeApp(RestApi api, Stage stage, CatalogTextWorkResponse work) {
        this.api = api; this.stage = stage; workTitle.setText(work.name());
        run(() -> api.content(work.id()), result -> {
            content = result; role.getItems().setAll(result.voiceParts());
            if (role.getItems().isEmpty()) status.setText("У произведения нет ролей");
            else role.getSelectionModel().selectFirst();
        });
        stage.setOnCloseRequest(event -> {
            if (recording && !Ui.confirm(stage, "Закрыть окно? Незавершённая запись с микрофона будет отменена.")) event.consume();
        });
    }

    private void changeRole(TextWorkContentResponse.VoicePart chosen) {
        selections.clear(); rows.clear(); variants = Map.of(); drafts.getItems().clear();
        for (var segment : content.segments()) for (var fragment : segment.fragments()) {
            if (fragment.voicePartId().equals(chosen.id())) rows.add(new FragmentRow(fragment.id(),
                    segment.name(), fragment.orderInSegment(), fragment.content()));
        }
        fragments.setItems(FXCollections.observableArrayList(rows)); fragments.getSelectionModel().selectFirst(); refresh();
    }

    @FXML private void refresh() {
        var chosen = role.getValue(); if (chosen == null) return;
        run(() -> api.drafts(chosen.id()), response -> {
            var result = new HashMap<UUID, List<DraftRecordingResponse>>();
            response.fragments().forEach(fragment -> result.put(fragment.fragmentId(), fragment.recordings()));
            variants = result;
            selections.entrySet().removeIf(entry -> variants.getOrDefault(entry.getKey(), List.of()).stream()
                    .noneMatch(draft -> draft.id().equals(entry.getValue())));
            showVariants(); updateProgress();
        });
    }

    private void showVariants() {
        var chosen = fragments.getSelectionModel().getSelectedItem();
        drafts.setItems(FXCollections.observableArrayList(chosen == null ? List.of() : variants.getOrDefault(chosen.id(), List.of())));
        if (chosen != null) {
            var selected = selections.get(chosen.id());
            drafts.getItems().stream().filter(d -> d.id().equals(selected)).findFirst().ifPresent(drafts.getSelectionModel()::select);
        }
    }

    @FXML private void choose() {
        var fragment = fragments.getSelectionModel().getSelectedItem();
        var draft = drafts.getSelectionModel().getSelectedItem();
        if (fragment == null || draft == null) { status.setText("Выберите фрагмент и вариант записи"); return; }
        selections.put(fragment.id(), draft.id()); updateProgress(); status.setText("Вариант выбран для публикации");
    }

    private void updateProgress() {
        fragments.refresh(); progress.setText("Выбрано вариантов: " + selections.size() + " из " + rows.size());
    }

    @FXML private void upload() {
        var fragment = fragments.getSelectionModel().getSelectedItem();
        if (fragment == null) { status.setText("Выберите фрагмент текста"); return; }
        var picker = new FileChooser(); picker.setTitle("Загрузить вариант записи");
        picker.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Аудио", "*.wav", "*.mp3", "*.m4a", "*.flac", "*.ogg"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*"));
        var file = picker.showOpenDialog(stage); if (file != null) uploadFile(fragment.id(), file.toPath(), false);
    }

    private void uploadFile(UUID fragment, Path file, boolean localRecording) {
        run(() -> {
            var response = api.upload(fragment, file);
            if (localRecording) { Files.deleteIfExists(file); temporaryFiles.remove(file); }
            return response;
        }, result -> {
            if (localRecording) { pending = null; retryButton.setDisable(true); }
            status.setText("Вариант сохранён на сервере"); refresh();
        });
    }

    @FXML private void startRecording() {
        var fragment = fragments.getSelectionModel().getSelectedItem();
        if (fragment == null) { status.setText("Выберите фрагмент текста"); return; }
        recordingFragment = fragment.id();
        audio.stopPlayback();
        run(() -> { audio.startRecording(); return true; }, result -> {
            if (closed) { audio.close(); return; }
            recording = true; began = System.currentTimeMillis();
            body.setDisable(false); role.setDisable(true); fragments.setDisable(true); tools.setDisable(true);
            startButton.setDisable(true); stopButton.setDisable(false);
            recordingTimer = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
                long seconds = (System.currentTimeMillis() - began) / 1000;
                status.setText("Запись: " + seconds + " с · максимум 120 с");
                if (seconds >= 120) stopRecording();
            }));
            recordingTimer.setCycleCount(Timeline.INDEFINITE); recordingTimer.play();
        });
    }

    @FXML private void stopRecording() {
        if (!recording) return;
        recording = false; recordingTimer.stop(); stopButton.setDisable(true);
        role.setDisable(false); fragments.setDisable(false); tools.setDisable(false); startButton.setDisable(false);
        UUID capturedFragment = recordingFragment;
        run(audio::stopRecording, path -> {
            pendingFragment = capturedFragment;
            pending = path; temporaryFiles.add(path); retryButton.setDisable(false);
            uploadFile(capturedFragment, path, true);
        });
    }

    @FXML private void retry() { if (pending != null) uploadFile(pendingFragment, pending, true); }

    @FXML private void delete() {
        var selected = drafts.getSelectionModel().getSelectedItem();
        if (selected == null) { status.setText("Выберите вариант записи"); return; }
        if (!Ui.confirm(stage, "Удалить вариант «" + selected.originalFileName() + "»?")) return;
        run(() -> { api.deleteDraft(selected.id()); return true; }, ignored -> refresh());
    }

    @FXML private void preview() {
        var selected = drafts.getSelectionModel().getSelectedItem();
        if (selected == null) { status.setText("Выберите вариант записи"); return; }
        run(() -> {
            String name = selected.originalFileName();
            String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9.]", "") : ".wav";
            var path = Files.createTempFile("teremok-preview-", extension);
            temporaryFiles.add(path); api.draftAudio(selected.id(), path); return path;
        }, path -> {
            audio.play(path, error -> Ui.error(stage, error), () -> status.setText("Прослушивание завершено"));
            status.setText("Прослушиваем вариант…");
        });
    }
    @FXML private void stopPlayback() { audio.stopPlayback(); status.setText("Прослушивание остановлено"); }

    @FXML private void publish() {
        var chosen = role.getValue();
        if (chosen == null || rows.isEmpty() || rows.stream().anyMatch(row -> !selections.containsKey(row.id()))) {
            status.setText("Сначала выберите по одному варианту для каждого фрагмента роли"); return;
        }
        if (!Ui.confirm(stage, "Опубликовать роль «" + chosen.name() + "» целиком? Выбранные варианты станут опубликованной озвучкой.")) return;
        var request = rows.stream().map(row -> new PublishRoleRecordingRequest.Selection(row.id(), selections.get(row.id()))).toList();
        run(() -> api.publish(chosen.id(), request), result -> {
            selections.clear(); status.setText("Роль опубликована. Остальные варианты остались в черновиках."); refresh();
        });
    }

    private <T> void run(Callable<T> work, Consumer<T> success) {
        if (closed) return;
        busy = true; body.setDisable(true); status.setText("Выполняем действие…");
        FxTasks.run(work, result -> {
            busy = false;
            if (closed) {
                if (result instanceof Path path) temporaryFiles.add(path);
                audio.close(); cleanup(); return;
            }
            body.setDisable(false); status.setText("Готово"); success.accept(result);
        }, error -> {
            busy = false;
            if (closed) { audio.close(); cleanup(); return; }
            body.setDisable(false); status.setText("Действие не выполнено");
            Ui.error(stage, error);
        });
    }
    public void close() {
        closed = true; if (recordingTimer != null) recordingTimer.stop();
        audio.close(); if (!busy) cleanup();
    }
    private void cleanup() {
        FxTasks.run(() -> {
            for (var path : temporaryFiles) Files.deleteIfExists(path);
            temporaryFiles.clear(); return true;
        }, ignored -> {}, error -> error.printStackTrace(System.err));
    }
    public record FragmentRow(UUID id, String segment, int order, String text) {}
}
