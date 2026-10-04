package space.grayt.teremok.exporter;

import java.util.List;

record DatabaseTarget(
        String serviceName,
        String sheetPrefix,
        String databaseName,
        String jdbcUrl,
        String username,
        String password,
        List<String> expectedTables) {

    DatabaseTarget {
        expectedTables = List.copyOf(expectedTables);
    }
}
