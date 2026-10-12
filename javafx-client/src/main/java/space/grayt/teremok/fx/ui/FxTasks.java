package space.grayt.teremok.fx.ui;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;
/** JDBC is absent; REST, disk and audio operations run outside the JavaFX thread. */
public final class FxTasks {
    private FxTasks() {}
    public static <T> Task<T> run(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
        Thread.ofVirtual().name("teremok-client-task").start(task);
        return task;
    }
}
