export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T = any>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  const response = await fetch(`/api/${path}`, { ...options, headers }).catch(
    (error) => {
      if (error instanceof DOMException && error.name === "AbortError")
        throw error;
      throw new ApiError(
        0,
        "Нет связи с сервером. Проверьте подключение и повторите действие.",
      );
    },
  );
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      body.message ||
        (response.status === 401
          ? "Сеанс завершён. Войдите заново"
          : response.status === 403
            ? "Недостаточно прав для этого действия"
            : `Не удалось выполнить запрос (${response.status})`),
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export const post = <T = any>(path: string, data?: unknown) =>
  api<T>(path, {
    method: "POST",
    body: data instanceof FormData ? data : JSON.stringify(data ?? {}),
  });
export const put = <T = any>(path: string, data: unknown) =>
  api<T>(path, { method: "PUT", body: JSON.stringify(data) });
export const remove = (path: string) => api(path, { method: "DELETE" });
export function message(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "Не удалось выполнить действие";
}
export async function download(path: string, filename: string) {
  const response = await fetch(`/api/${path}`).catch(() => {
    throw new ApiError(
      0,
      "Не удалось скачать файл: нет связи с сервером. Повторите попытку.",
    );
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || "Не удалось скачать файл");
  }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return Number(response.headers.get("X-Export-Failed-Tables") || 0);
}
export const date = (value: string) =>
  new Date(value).toLocaleString("ru-RU", {
    dateStyle: "medium",
    timeStyle: "short",
  });
export const statusLabels: Record<string, string> = {
  DRAFT: "Черновик",
  RECORDING: "В записи",
  READY: "Готово",
  LOW: "Простой",
  MEDIUM: "Средний",
  HIGH: "Сложный",
  PENDING: "В очереди",
  PROCESSING: "Обрабатывается",
  COMPLETED: "Готово",
  FAILED: "Ошибка",
};
export const roleLabels: Record<string, string> = {
  ADMIN: "Администратор",
  EDITOR: "Редактор",
  READER: "Читатель",
};
export function plural(count: number, one: string, few: string, many: string) {
  const rest = Math.abs(count) % 100,
    last = rest % 10;
  return `${count} ${rest >= 11 && rest <= 14 ? many : last === 1 ? one : last >= 2 && last <= 4 ? few : many}`;
}
