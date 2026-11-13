package space.grayt.teremok.exporter;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseExporterTest {
    @TempDir Path directory;

    @Test void longTextIsSplitWithoutLosingContentOrBreakingUnicode() throws Exception {
        String text = "а".repeat(32766) + "😀" + "б".repeat(40000);
        var parts = DatabaseExporter.splitText(text);
        assertEquals(text, String.join("", parts));
        assertTrue(parts.stream().allMatch(p -> p.length() <= 32767));
        assertFalse(Character.isHighSurrogate(parts.getFirst().charAt(parts.getFirst().length()-1)));
    }

    @Test void readerGetsEveryBusinessColumnWhileAuditRemainsAdminOnly() throws Exception {
        String url = "jdbc:h2:mem:export_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE library_work(id INT PRIMARY KEY, title VARCHAR(100), description VARCHAR(500), archived BOOLEAN, version BIGINT, source_payload VARCHAR(100000))");
            statement.execute("INSERT INTO library_work VALUES(1,'Теремок','Полное описание',false,7,'{\"all\":true}')");
            statement.execute("CREATE TABLE library_audit(id INT PRIMARY KEY, description VARCHAR(100))");
            statement.execute("INSERT INTO library_audit VALUES(1,'Изменение')");
            try (var insert = connection.prepareStatement("INSERT INTO library_work VALUES(2,'Длинный текст','Описание',false,1,?)")) {
                insert.setString(1, "я".repeat(70000)); insert.executeUpdate();
            }
        }
        var target = new DatabaseTarget("catalog-service", "catalog", "test", url, "sa", "", List.of("library_work", "library_audit"));
        var readerPath = directory.resolve("reader.xlsx");
        var report = new DatabaseExporter().export(List.of(target), readerPath, table -> ExportAccess.canExport(table, false));
        assertEquals(0, report.failedTables());
        try (var workbook = new XSSFWorkbook(Files.newInputStream(readerPath))) {
            assertEquals(2, workbook.getNumberOfSheets()); // status + business table
            var sheet = workbook.getSheetAt(1);
            var columns = new ArrayList<String>();
            sheet.getRow(0).forEach(cell -> columns.add(cell.getStringCellValue()));
            assertEquals(List.of("id", "title", "description", "archived", "version", "source_payload"), columns.subList(0, 6));
            assertEquals(List.of("source_payload [2]", "source_payload [3]"), columns.subList(6, 8));
            assertEquals("Полное описание", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(7, sheet.getRow(1).getCell(4).getNumericCellValue());
            assertEquals("я".repeat(70000), sheet.getRow(2).getCell(5).getStringCellValue() + sheet.getRow(2).getCell(6).getStringCellValue() + sheet.getRow(2).getCell(7).getStringCellValue());
        }
        var adminPath = directory.resolve("admin.xlsx");
        new DatabaseExporter().export(List.of(target), adminPath, table -> ExportAccess.canExport(table, true));
        try (var workbook = new XSSFWorkbook(Files.newInputStream(adminPath))) { assertEquals(3, workbook.getNumberOfSheets()); }
        assertFalse(ExportAccess.canExport("auth_audit", false));
        assertTrue(ExportAccess.canExport("user_account", false));
    }
}
