const keys = {
  active: "teremok.catalog.active",
  archived: "teremok.catalog.archived",
};

export function safeCatalogPath(value: string | null, archived = false) {
  const fallback = archived ? "/archive" : "/";
  try {
    if (!value?.startsWith("/") || value.startsWith("//")) return fallback;
    const url = new URL(value, "https://teremok.local");
    return url.origin === "https://teremok.local" && url.pathname === fallback
      ? url.pathname + url.search
      : fallback;
  } catch {
    return fallback;
  }
}
export function rememberCatalog(path: string, archived = false) {
  try {
    sessionStorage.setItem(
      keys[archived ? "archived" : "active"],
      safeCatalogPath(path, archived),
    );
  } catch {
    /* Browsing works without storage. */
  }
}
export function previousCatalog(archived = false) {
  try {
    return safeCatalogPath(
      sessionStorage.getItem(keys[archived ? "archived" : "active"]),
      archived,
    );
  } catch {
    return archived ? "/archive" : "/";
  }
}
