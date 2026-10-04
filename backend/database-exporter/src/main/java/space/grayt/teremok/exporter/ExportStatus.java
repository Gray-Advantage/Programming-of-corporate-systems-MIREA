package space.grayt.teremok.exporter;

record ExportStatus(
        String serviceName,
        String databaseName,
        String tableName,
        String requestedSheetName,
        String actualSheetName,
        String status,
        long exportedRows,
        String error) {
}
