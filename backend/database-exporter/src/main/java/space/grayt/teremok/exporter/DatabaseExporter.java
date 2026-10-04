package space.grayt.teremok.exporter;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class DatabaseExporter {

    private static final String STATUS_SHEET_NAME = "_export_status";
    private static final String PUBLIC_SCHEMA = "public";
    private static final int MAX_CELL_TEXT_LENGTH = 32_767;
    private static final int MAX_COLUMN_WIDTH_CHARS = 50;
    private static final int MAX_ERROR_LENGTH = 1_000;

    ExportReport export(List<DatabaseTarget> targets, Path outputPath) throws IOException {
        var statuses = new ArrayList<ExportStatus>();
        var sheetNames = new SheetNameGenerator();

        try (var workbook = new XSSFWorkbook()) {
            var styles = createStyles(workbook);
            var statusSheet = workbook.createSheet(STATUS_SHEET_NAME);

            for (var target : targets) {
                exportDatabase(workbook, styles, sheetNames, target, statuses);
            }
            writeStatusSheet(statusSheet, styles, statuses);

            var parent = outputPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(outputPath)) {
                workbook.write(output);
            }
        }

        var failed = statuses.stream().filter(status -> !"SUCCESS".equals(status.status())).count();
        return new ExportReport(statuses.size(), failed);
    }

    private void exportDatabase(
            Workbook workbook,
            WorkbookStyles styles,
            SheetNameGenerator sheetNames,
            DatabaseTarget target,
            List<ExportStatus> statuses) {
        try (var connection = DriverManager.getConnection(target.jdbcUrl(), target.username(), target.password())) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            var tables = new LinkedHashSet<>(target.expectedTables());
            try {
                tables.addAll(discoverTables(connection));
            } catch (SQLException exception) {
                statuses.add(new ExportStatus(
                        target.serviceName(),
                        target.databaseName(),
                        "<table-discovery>",
                        "",
                        "",
                        "DATA_NOT_RECEIVED",
                        0,
                        errorMessage(exception)));
            }

            for (var tableName : tables) {
                exportTable(workbook, styles, sheetNames, connection, target, tableName, statuses);
            }
        } catch (SQLException exception) {
            var error = errorMessage(exception);
            for (var tableName : target.expectedTables()) {
                writeFailedTable(workbook, styles, sheetNames, target, tableName, error, statuses);
            }
        }
    }

    private List<String> discoverTables(Connection connection) throws SQLException {
        var tables = new ArrayList<String>();
        try (var resultSet = connection.getMetaData().getTables(null, PUBLIC_SCHEMA, "%", new String[] {"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        tables.sort(String.CASE_INSENSITIVE_ORDER);
        return tables;
    }

    private void exportTable(
            Workbook workbook,
            WorkbookStyles styles,
            SheetNameGenerator sheetNames,
            Connection connection,
            DatabaseTarget target,
            String tableName,
            List<ExportStatus> statuses) {
        var requestedSheetName = target.serviceName() + "__" + tableName;
        var actualSheetName = sheetNames.create(target.sheetPrefix(), tableName);
        var sheet = workbook.createSheet(actualSheetName);
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);

        try {
            var rowCount = writeTableData(connection, tableName, sheet, styles);
            statuses.add(new ExportStatus(
                    target.serviceName(),
                    target.databaseName(),
                    tableName,
                    requestedSheetName,
                    actualSheetName,
                    "SUCCESS",
                    rowCount,
                    ""));
        } catch (Exception exception) {
            clearSheet(sheet);
            writeFailureContent(sheet, styles, errorMessage(exception));
            statuses.add(new ExportStatus(
                    target.serviceName(),
                    target.databaseName(),
                    tableName,
                    requestedSheetName,
                    actualSheetName,
                    "DATA_NOT_RECEIVED",
                    0,
                    errorMessage(exception)));
        }
    }

    private long writeTableData(Connection connection, String tableName, Sheet sheet, WorkbookStyles styles)
            throws SQLException, IOException {
        var sql = "SELECT * FROM " + quoteIdentifier(PUBLIC_SCHEMA) + "." + quoteIdentifier(tableName);
        var primaryKeys = primaryKeyColumns(connection, tableName);
        if (!primaryKeys.isEmpty()) {
            sql += " ORDER BY " + primaryKeys.stream().map(DatabaseExporter::quoteIdentifier).reduce((a, b) -> a + ", " + b).orElseThrow();
        }

        try (var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setFetchSize(500);
            try (var resultSet = statement.executeQuery(sql)) {
                var metadata = resultSet.getMetaData();
                var columnCount = metadata.getColumnCount();
                var maxWidths = new int[columnCount];
                var header = sheet.createRow(0);
                for (var column = 1; column <= columnCount; column++) {
                    var name = metadata.getColumnLabel(column);
                    var cell = header.createCell(column - 1);
                    cell.setCellValue(name);
                    cell.setCellStyle(styles.header());
                    maxWidths[column - 1] = name.length();
                }

                var rowIndex = 1;
                while (resultSet.next()) {
                    if (rowIndex > SpreadsheetVersion.EXCEL2007.getLastRowIndex()) {
                        throw new IOException("Table has more rows than one XLSX sheet can contain");
                    }
                    var row = sheet.createRow(rowIndex++);
                    for (var column = 1; column <= columnCount; column++) {
                        var value = resultSet.getObject(column);
                        var cell = row.createCell(column - 1);
                        writeCell(cell, value, styles);
                        maxWidths[column - 1] = Math.max(maxWidths[column - 1], displayLength(value));
                    }
                }

                if (columnCount > 0) {
                    sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, columnCount - 1));
                }
                setColumnWidths(sheet, maxWidths);
                return rowIndex - 1L;
            }
        }
    }

    private List<String> primaryKeyColumns(Connection connection, String tableName) throws SQLException {
        Map<Short, String> columns = new TreeMap<>();
        try (var resultSet = connection.getMetaData().getPrimaryKeys(null, PUBLIC_SCHEMA, tableName)) {
            while (resultSet.next()) {
                columns.put(resultSet.getShort("KEY_SEQ"), resultSet.getString("COLUMN_NAME"));
            }
        }
        return List.copyOf(columns.values());
    }

    private void writeFailedTable(
            Workbook workbook,
            WorkbookStyles styles,
            SheetNameGenerator sheetNames,
            DatabaseTarget target,
            String tableName,
            String error,
            List<ExportStatus> statuses) {
        var requestedSheetName = target.serviceName() + "__" + tableName;
        var actualSheetName = sheetNames.create(target.sheetPrefix(), tableName);
        var sheet = workbook.createSheet(actualSheetName);
        writeFailureContent(sheet, styles, error);
        statuses.add(new ExportStatus(
                target.serviceName(),
                target.databaseName(),
                tableName,
                requestedSheetName,
                actualSheetName,
                "DATA_NOT_RECEIVED",
                0,
                error));
    }

    private void writeFailureContent(Sheet sheet, WorkbookStyles styles, String error) {
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);
        var header = sheet.createRow(0);
        writeStyledText(header, 0, "EXPORT_STATUS", styles.header());
        writeStyledText(header, 1, "ERROR", styles.header());
        var data = sheet.createRow(1);
        writeStyledText(data, 0, "DATA_NOT_RECEIVED", styles.error());
        writeStyledText(data, 1, error, styles.error());
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 80 * 256);
        sheet.setAutoFilter(new CellRangeAddress(0, 1, 0, 1));
    }

    private void writeStatusSheet(Sheet sheet, WorkbookStyles styles, List<ExportStatus> statuses) {
        var headers = List.of(
                "SERVICE",
                "DATABASE",
                "TABLE",
                "REQUESTED_SHEET_NAME",
                "ACTUAL_SHEET_NAME",
                "STATUS",
                "EXPORTED_ROWS",
                "ERROR");
        var widths = new int[headers.size()];
        var header = sheet.createRow(0);
        for (var column = 0; column < headers.size(); column++) {
            writeStyledText(header, column, headers.get(column), styles.header());
            widths[column] = headers.get(column).length();
        }

        statuses.sort(Comparator.comparing(ExportStatus::serviceName).thenComparing(ExportStatus::tableName));
        for (var index = 0; index < statuses.size(); index++) {
            var status = statuses.get(index);
            var row = sheet.createRow(index + 1);
            var values = List.of(
                    status.serviceName(),
                    status.databaseName(),
                    status.tableName(),
                    status.requestedSheetName(),
                    status.actualSheetName(),
                    status.status(),
                    Long.toString(status.exportedRows()),
                    status.error());
            for (var column = 0; column < values.size(); column++) {
                var cell = row.createCell(column);
                if (column == 6) {
                    cell.setCellValue(status.exportedRows());
                } else {
                    cell.setCellValue(truncate(values.get(column)));
                }
                if (!"SUCCESS".equals(status.status())) {
                    cell.setCellStyle(styles.error());
                }
                widths[column] = Math.max(widths[column], values.get(column).length());
            }
        }
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);
        if (!statuses.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(0, statuses.size(), 0, headers.size() - 1));
        }
        setColumnWidths(sheet, widths);
    }

    private void writeCell(Cell cell, Object value, WorkbookStyles styles) throws SQLException, IOException {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Boolean booleanValue) {
            cell.setCellValue(booleanValue);
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof BigDecimal) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof java.sql.Date date) {
            cell.setCellValue(date.toLocalDate());
            cell.setCellStyle(styles.date());
        } else if (value instanceof Timestamp timestamp) {
            cell.setCellValue(timestamp.toLocalDateTime());
            cell.setCellStyle(styles.dateTime());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date());
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(styles.dateTime());
        } else if (value instanceof Instant || value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            cell.setCellValue(value.toString());
        } else if (value instanceof byte[] bytes) {
            cell.setCellValue(Base64.getEncoder().encodeToString(bytes));
        } else if (value instanceof java.sql.Array sqlArray) {
            try {
                cell.setCellValue(truncate(arrayToString(sqlArray.getArray())));
            } finally {
                sqlArray.free();
            }
        } else if (value instanceof SQLXML xml) {
            try {
                cell.setCellValue(truncate(xml.getString()));
            } finally {
                xml.free();
            }
        } else if (value instanceof Clob clob) {
            cell.setCellValue(truncate(clob.getSubString(1, (int) Math.min(clob.length(), MAX_CELL_TEXT_LENGTH))));
        } else if (value instanceof Blob blob) {
            var length = Math.min(blob.length(), Integer.MAX_VALUE);
            cell.setCellValue(Base64.getEncoder().encodeToString(blob.getBytes(1, (int) length)));
        } else if (value instanceof UUID) {
            cell.setCellValue(value.toString());
        } else {
            cell.setCellValue(truncate(value.toString()));
        }
    }

    private int displayLength(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof byte[] bytes) {
            return Math.min(MAX_COLUMN_WIDTH_CHARS, Base64.getEncoder().encodeToString(bytes).length());
        }
        return Math.min(MAX_COLUMN_WIDTH_CHARS, value.toString().length());
    }

    private static String arrayToString(Object array) {
        if (array == null || !array.getClass().isArray()) {
            return String.valueOf(array);
        }
        var values = new ArrayList<String>(Array.getLength(array));
        for (var index = 0; index < Array.getLength(array); index++) {
            values.add(String.valueOf(Array.get(array, index)));
        }
        return values.toString();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_CELL_TEXT_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_CELL_TEXT_LENGTH - 1) + "…";
    }

    private static void setColumnWidths(Sheet sheet, int[] maxWidths) {
        for (var column = 0; column < maxWidths.length; column++) {
            var width = Math.max(12, Math.min(MAX_COLUMN_WIDTH_CHARS, maxWidths[column] + 2));
            sheet.setColumnWidth(column, width * 256);
        }
    }

    private static void writeStyledText(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(truncate(value));
        cell.setCellStyle(style);
    }

    private static void clearSheet(Sheet sheet) {
        for (var index = sheet.getLastRowNum(); index >= 0; index--) {
            var row = sheet.getRow(index);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String errorMessage(Throwable exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        } else {
            message = exception.getClass().getSimpleName() + ": " + message;
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private static WorkbookStyles createStyles(Workbook workbook) {
        var header = workbook.createCellStyle();
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        var headerFont = workbook.createFont();
        headerFont.setFontName("Aptos");
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        header.setFont(headerFont);

        var error = workbook.createCellStyle();
        error.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        error.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var errorFont = workbook.createFont();
        errorFont.setFontName("Aptos");
        errorFont.setColor(IndexedColors.DARK_RED.getIndex());
        error.setFont(errorFont);

        var date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        var dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return new WorkbookStyles(header, error, date, dateTime);
    }

    record ExportReport(long totalTables, long failedTables) {
    }

    private record WorkbookStyles(CellStyle header, CellStyle error, CellStyle date, CellStyle dateTime) {
    }
}
