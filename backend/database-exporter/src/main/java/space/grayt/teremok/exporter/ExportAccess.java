package space.grayt.teremok.exporter;

import java.util.Set;

/** The assignment restricts audit viewing to administrators, including exports. */
final class ExportAccess {
    private static final Set<String> AUDIT_TABLES = Set.of("auth_audit", "library_audit");

    static boolean canExport(String table, boolean admin) {
        return admin || !AUDIT_TABLES.contains(table);
    }
}
