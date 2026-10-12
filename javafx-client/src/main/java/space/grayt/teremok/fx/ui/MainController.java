package space.grayt.teremok.fx.ui;

import java.util.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.StringConverter;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse;
import space.grayt.teremok.fx.*;
import space.grayt.teremok.fx.net.RestApi;

public final class MainController {
    @FXML private Label identity, status, statsUpdated;
    @FXML private TextField search;
    @FXML private ComboBox<String> language, origin;
    @FXML private TableView<CatalogTextWorkResponse> works;
    @FXML private TableColumn<CatalogTextWorkResponse, String> titleColumn, authorsColumn, languageColumn, kindColumn;
    @FXML private Button recordButton, renderButton, refreshButton, exportButton;
    @FXML private FlowPane metrics;
    @FXML private VBox services;
    private final ObservableList<CatalogTextWorkResponse> source = FXCollections.observableArrayList();
    private final List<CheckBox> exportChoices = new ArrayList<>();
    private final List<Stage> children = new ArrayList<>();
    private TeremokApplication app;
    private RestApi api;
    private boolean closed;

    @FXML private void initialize() {
        titleColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().name()));
        authorsColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(String.join(", ", row.getValue().authors())));
        languageColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().language()));
        kindColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(
                row.getValue().origin().type() == CatalogTextWorkResponse.OriginType.ORIGINAL ? "Оригинал" : "Перевод"));
        var filtered = new FilteredList<>(source);
        var sorted = new SortedList<>(filtered); sorted.comparatorProperty().bind(works.comparatorProperty()); works.setItems(sorted);
        Runnable filter = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(work -> work.name().toLowerCase(Locale.ROOT).contains(query)
                    && (language.getValue() == null || language.getValue().equals("Все языки") || language.getValue().equals(work.language()))
                    && (origin.getValue() == null || origin.getValue().equals("Все типы")
                    || origin.getValue().equals(work.origin().type() == CatalogTextWorkResponse.OriginType.ORIGINAL ? "Оригинал" : "Перевод")));
            status.setText("Произведений: " + filtered.size());
        };
        search.textProperty().addListener((o,a,c) -> filter.run());
        language.valueProperty().addListener((o,a,c) -> filter.run()); origin.valueProperty().addListener((o,a,c) -> filter.run());
        origin.getItems().setAll("Все типы", "Оригинал", "Перевод"); origin.getSelectionModel().selectFirst();
        recordButton.disableProperty().bind(works.getSelectionModel().selectedItemProperty().isNull());
        renderButton.disableProperty().bind(works.getSelectionModel().selectedItemProperty().isNull());
        works.setPlaceholder(new Label("Произведения не найдены. Обновите список или измените поиск."));
        works.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && works.getSelectionModel().getSelectedItem() != null) record(); });
    }

    public void initializeApp(TeremokApplication app, RestApi api) {
        this.app = app; this.api = api; identity.setText(api.login() + " · " + api.address());
        String[][] options = {{"auth-service","Пользователи"},{"catalog-service","Каталог произведений"},
                {"text-work-content-service","Тексты, роли и фрагменты"},{"draft-recordings-service","Черновые записи"},
                {"recordings-service","Опубликованные озвучки и сборки"}};
        for (var option : options) {
            var box = new CheckBox(option[1]); box.setUserData(option[0]); box.setSelected(true);
            exportChoices.add(box); services.getChildren().add(box);
        }
        refresh(); statistics();
    }

    @FXML private void refresh() {
        refreshButton.setDisable(true); status.setText("Загружаем каталог…");
        FxTasks.run(api::catalog, result -> {
            if (closed) return; refreshButton.setDisable(false); source.setAll(result);
            String previous = language.getValue();
            language.getItems().setAll("Все языки");
            result.stream().map(CatalogTextWorkResponse::language).distinct().sorted().forEach(language.getItems()::add);
            language.setValue(language.getItems().contains(previous) ? previous : "Все языки");
            status.setText("Произведений: " + works.getItems().size());
        }, error -> { if (!closed) { refreshButton.setDisable(false); status.setText("Каталог не загружен"); Ui.error(app.stage(), error); } });
    }

    @FXML private void statistics() {
        if (api == null) return;
        statsUpdated.setText("Обновляем показатели…");
        FxTasks.run(api::statistics, result -> {
            if (closed) return;
            metrics.getChildren().clear();
            for (var item : result.metrics()) {
                var title = new Label(item.title()); title.setWrapText(true);
                var value = new Label(item.value() == null ? "—" : item.value().toString()); value.getStyleClass().add("metric-value");
                var note = new Label(item.error() == null ? "" : item.error()); note.setWrapText(true); note.getStyleClass().add("muted");
                var card = new VBox(10, title, value, note); card.setPrefWidth(250); card.getStyleClass().add("card");
                metrics.getChildren().add(card);
            }
            statsUpdated.setText("Обновлено: " + Ui.date(result.updatedAt()));
        }, error -> {
            if (!closed) {
                statsUpdated.setText("Статистика недоступна. Проверьте backend и нажмите «Обновить».");
                error.printStackTrace(System.err);
            }
        });
    }

    @FXML private void export() {
        var selected = exportChoices.stream().filter(CheckBox::isSelected).map(c -> (String)c.getUserData()).toList();
        if (selected.isEmpty()) { Ui.error(app.stage(), new IllegalArgumentException("Выберите хотя бы один сервис")); return; }
        var path = Ui.save(app.stage(), "teremok-export.xlsx", "Сохранить Excel", "xlsx");
        if (path == null) return;
        exportButton.setDisable(true); status.setText("Готовим Excel-выгрузку…");
        FxTasks.run(() -> api.export(selected, path), result -> {
            if (closed) return; exportButton.setDisable(false);
            status.setText("Excel сохранён: " + result.path());
            if (!"0".equals(result.failedTables())) Ui.error(app.stage(),
                    new IllegalStateException("Файл сохранён, но часть таблиц недоступна. Проверьте лист _export_status."));
        }, error -> { if (!closed) { exportButton.setDisable(false); Ui.error(app.stage(), error); } });
    }

    @FXML private void record() { open("recording.fxml", true); }
    @FXML private void render() { open("render.fxml", false); }

    private void open(String fxml, boolean recording) {
        var work = works.getSelectionModel().getSelectedItem();
        if (work == null) return;
        try {
            var loader = new FXMLLoader(TeremokApplication.class.getResource("/space/grayt/teremok/fx/" + fxml));
            javafx.scene.Parent root = loader.load();
            var stage = new Stage(); stage.initOwner(app.stage()); stage.initModality(Modality.NONE);
            stage.setTitle((recording ? "Запись · " : "Сборка · ") + work.name());
            stage.setScene(TeremokApplication.scene(root, 1050, 780)); stage.setMinWidth(900); stage.setMinHeight(650);
            if (recording) {
                RecordingController controller = loader.getController(); controller.initializeApp(api, stage, work);
                stage.setOnHidden(event -> { controller.close(); children.remove(stage); });
            } else {
                RenderController controller = loader.getController(); controller.initializeApp(api, stage, work);
                stage.setOnHidden(event -> { controller.close(); children.remove(stage); });
            }
            children.add(stage); stage.show();
        } catch (Exception error) { Ui.error(app.stage(), error); }
    }

    @FXML private void logout() {
        if (!Ui.confirm(app.stage(), "Выйти из аккаунта? Несохранённая запись с микрофона будет отменена.")) return;
        close();
        FxTasks.run(() -> { api.logout(); return true; }, ignored -> showLogin(), error -> showLogin());
    }
    private void showLogin() { try { app.login(); } catch (Exception error) { Ui.error(app.stage(), error); } }
    public boolean canClose() {
        return children.isEmpty() || Ui.confirm(app.stage(),
                "Закрыть приложение? Незавершённая запись с микрофона будет отменена.");
    }
    public void close() {
        closed = true;
        for (var child : new ArrayList<>(children)) {
            child.setOnCloseRequest(null); child.close();
        }
        children.clear();
    }
}
