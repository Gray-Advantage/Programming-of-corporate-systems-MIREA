package space.grayt.teremok.exporter;

import java.nio.file.Path;
import java.util.List;

public final class DatabaseExporterApplication {

    private static final String DEFAULT_EXPORT_PATH = "/exports/teremok-database-export.xlsx";

    private DatabaseExporterApplication() {
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Укажите не более одного пути к выходному XLSX-файлу.");
            System.exit(1);
        }

        var outputPath = Path.of(args.length == 1 ? args[0] : env("EXPORT_PATH", DEFAULT_EXPORT_PATH));
        var targets = List.of(
                new DatabaseTarget(
                        "catalog-service",
                        "catalog",
                        "catalog_db",
                        env("CATALOG_DATABASE_URL", "jdbc:postgresql://postgres:5432/catalog_db"),
                        env("CATALOG_DATABASE_USERNAME", "catalog_service"),
                        env("CATALOG_DATABASE_PASSWORD", "catalog_service"),
                        List.of(
                                "catalog_text_work",
                                "catalog_text_work_author",
                                "catalog_text_work_translator")),
                new DatabaseTarget(
                        "text-work-content-service",
                        "content",
                        "text_work_content_db",
                        env("CONTENT_DATABASE_URL", "jdbc:postgresql://postgres:5432/text_work_content_db"),
                        env("CONTENT_DATABASE_USERNAME", "text_work_content_service"),
                        env("CONTENT_DATABASE_PASSWORD", "text_work_content_service"),
                        List.of(
                                "text_work_content",
                                "voice_part",
                                "segment",
                                "voice_part_fragment")));

        try {
            var report = new DatabaseExporter().export(targets, outputPath);
            System.out.printf(
                    "Экспорт сохранён: %s. Таблиц: %d, ошибок: %d.%n",
                    outputPath.toAbsolutePath(),
                    report.totalTables(),
                    report.failedTables());
            if (report.failedTables() > 0) {
                System.err.println("Часть данных не получена. Подробности записаны в лист _export_status.");
            }
        } catch (Exception exception) {
            System.err.println("Не удалось создать XLSX-файл: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static String env(String name, String defaultValue) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
