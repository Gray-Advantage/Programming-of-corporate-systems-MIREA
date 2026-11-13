/** Selection is independent of pagination; a range is scoped to the visible page. */
export const SELECTION_LIMIT = 100;

export function selectIds(selected: string[], ids: string[], checked: boolean) {
  return checked
    ? [...new Set([...selected, ...ids])].slice(0, SELECTION_LIMIT)
    : selected.filter((id) => !ids.includes(id));
}

export function toggleRange(
  selected: string[],
  visible: string[],
  id: string,
  anchor: string | null,
  shift: boolean,
) {
  const start = anchor ? visible.indexOf(anchor) : -1;
  const end = visible.indexOf(id);
  const ids =
    shift && start >= 0 && end >= 0
      ? visible.slice(Math.min(start, end), Math.max(start, end) + 1)
      : [id];
  return selectIds(selected, ids, !selected.includes(id));
}
