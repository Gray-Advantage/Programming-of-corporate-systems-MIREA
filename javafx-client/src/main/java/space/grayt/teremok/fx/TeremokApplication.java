package space.grayt.teremok.fx;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import space.grayt.teremok.fx.net.RestApi;
import space.grayt.teremok.fx.ui.*;

public final class TeremokApplication extends Application {
    private Stage stage;
    @Override public void start(Stage stage) throws Exception {
        this.stage = stage; stage.setTitle("Теремок — озвучка по ролям");
        login(); stage.show();
    }
    public void login() throws Exception {
        var loader = new FXMLLoader(getClass().getResource("/space/grayt/teremok/fx/login.fxml"));
        var root = loader.load();
        LoginController controller = loader.getController(); controller.initializeApp(this);
        stage.setScene(scene((javafx.scene.Parent)root, 650, 510));
        stage.setMinWidth(560); stage.setMinHeight(460);
        stage.setOnCloseRequest(event -> {});
    }
    public void signedIn(RestApi api) throws Exception {
        var loader = new FXMLLoader(getClass().getResource("/space/grayt/teremok/fx/main.fxml"));
        var root = loader.load();
        MainController controller = loader.getController(); controller.initializeApp(this, api);
        stage.setScene(scene((javafx.scene.Parent)root, 1160, 760));
        stage.setMinWidth(920); stage.setMinHeight(650);
        stage.setOnCloseRequest(event -> {
            if (controller.canClose()) controller.close();
            else event.consume();
        });
    }
    public Stage stage() { return stage; }
    public static Scene scene(javafx.scene.Parent root, double width, double height) {
        var scene = new Scene(root, width, height);
        scene.getStylesheets().add(TeremokApplication.class.getResource("/space/grayt/teremok/fx/app.css").toExternalForm());
        return scene;
    }
}
