type Control = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
let nextId = 0;

export function passwordError(value: string) {
  if (value.length < 10) return "Используйте не менее 10 символов.";
  if (new TextEncoder().encode(value).length > 72)
    return "Пароль слишком длинный: максимум 72 байта (кириллица занимает больше места).";
  if (
    !/[A-ZА-Я]/.test(value) ||
    !/[a-zа-я]/.test(value) ||
    !/[0-9]/.test(value)
  )
    return "Добавьте прописную и строчную буквы и хотя бы одну цифру.";
  return "";
}

export function tagsError(value: string) {
  const tags = value
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean);
  if (tags.length > 20) return "Оставьте не более 20 тегов.";
  if (tags.some((t) => t.length > 60))
    return "Каждый тег должен быть не длиннее 60 символов.";
  return "";
}

function fieldMessage(control: Control, form: HTMLFormElement): string {
  if (
    control.disabled ||
    ("readOnly" in control && control.readOnly) ||
    control.type === "hidden"
  )
    return "";
  if (control.required && !control.value.trim())
    return control instanceof HTMLSelectElement
      ? "Выберите значение из списка."
      : "Заполните это поле.";
  const v = control.validity;
  if (v.valueMissing) return "Заполните это поле.";
  if (v.badInput) return "Введите число.";
  if (v.rangeUnderflow)
    return `Укажите значение не меньше ${(control as HTMLInputElement).min}.`;
  if (v.rangeOverflow)
    return `Укажите значение не больше ${(control as HTMLInputElement).max}.`;
  if (v.stepMismatch) return "Введите целое число.";
  if (v.tooLong)
    return `Максимум ${(control as HTMLInputElement).maxLength} символов.`;
  if (v.typeMismatch || v.patternMismatch) return "Проверьте формат значения.";
  if (control.dataset.validation === "password" && control.value)
    return passwordError(control.value);
  if (control.dataset.validation === "tags") return tagsError(control.value);
  if (control.dataset.match) {
    const other = form.elements.namedItem(
      control.dataset.match,
    ) as HTMLInputElement | null;
    if (control.value !== other?.value)
      return "Пароли не совпадают. Повторите новый пароль.";
  }
  return "";
}

function display(control: Control, text: string) {
  const id = (control.dataset.errorId ||= `field-error-${++nextId}`);
  let element = document.getElementById(id);
  if (text && !element) {
    element = document.createElement("span");
    element.id = id;
    element.className = "field-error";
    element.setAttribute("aria-live", "polite");
    (control.closest("label") || control.parentElement)?.append(element);
  }
  element && (element.textContent = text);
  if (text) {
    control.setAttribute("aria-invalid", "true");
    const ids = new Set(
      (control.getAttribute("aria-describedby") || "")
        .split(" ")
        .filter(Boolean),
    );
    ids.add(id);
    control.setAttribute("aria-describedby", [...ids].join(" "));
  } else {
    control.removeAttribute("aria-invalid");
    const ids = (control.getAttribute("aria-describedby") || "")
      .split(" ")
      .filter((x) => x && x !== id);
    if (ids.length) control.setAttribute("aria-describedby", ids.join(" "));
    else control.removeAttribute("aria-describedby");
    element?.remove();
  }
}

export function setFieldError(
  form: HTMLFormElement,
  name: string,
  text: string,
) {
  const control = form.elements.namedItem(name);
  if (
    control instanceof HTMLInputElement ||
    control instanceof HTMLSelectElement ||
    control instanceof HTMLTextAreaElement
  ) {
    display(control, text);
    control.focus();
    return true;
  }
  return false;
}

export function clearFormErrors(form: HTMLFormElement) {
  for (const control of form.querySelectorAll<Control>(
    "input, select, textarea",
  ))
    display(control, "");
}

/** Native constraints, with persistent Russian feedback instead of browser bubbles. */
export function validateForm(form: HTMLFormElement) {
  const original = form.noValidate;
  form.noValidate = true;
  let attempted = false;
  function controls() {
    return [...form.querySelectorAll<Control>("input, select, textarea")];
  }
  function submit(event: Event) {
    attempted = true;
    let first: Control | undefined;
    for (const control of controls()) {
      const error = fieldMessage(control, form);
      display(control, error);
      if (error) first ??= control;
    }
    if (first) {
      event.preventDefault();
      event.stopImmediatePropagation();
      first.focus();
    }
  }
  function update(event: Event) {
    const control = event.target;
    if (!(
      control instanceof HTMLInputElement ||
      control instanceof HTMLSelectElement ||
      control instanceof HTMLTextAreaElement
    ))
      return;
    if (
      control.hasAttribute("aria-invalid") ||
      (event.type === "focusout" && (attempted || control.value))
    )
      display(control, fieldMessage(control, form));
    for (const dependent of controls().filter(
      (c) => c.dataset.match === control.name && c.hasAttribute("aria-invalid"),
    ))
      display(dependent, fieldMessage(dependent, form));
  }
  form.addEventListener("submit", submit, true);
  form.addEventListener("input", update);
  form.addEventListener("change", update);
  form.addEventListener("focusout", update);
  return {
    destroy() {
      form.noValidate = original;
      form.removeEventListener("submit", submit, true);
      form.removeEventListener("input", update);
      form.removeEventListener("change", update);
      form.removeEventListener("focusout", update);
      for (const control of controls()) display(control, "");
    },
  };
}
