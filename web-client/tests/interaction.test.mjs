import { test } from "node:test";
import assert from "node:assert/strict";
import { selectIds, toggleRange, SELECTION_LIMIT } from "../src/lib/selection.ts";
import { passwordError, tagsError } from "../src/lib/validation.ts";
import { safeCatalogPath } from "../src/lib/catalog-context.ts";

test("page selection retains other pages and deselects only this page", () => {
  assert.deepEqual(selectIds(["old", "a"], ["a", "b"], true), ["old", "a", "b"]);
  assert.deepEqual(selectIds(["old", "a", "b"], ["a", "b"], false), ["old"]);
});
test("shift range works in both directions and can deselect a range", () => {
  const visible = ["a", "b", "c", "d"];
  assert.deepEqual(toggleRange(["a"], visible, "c", "a", true), ["a", "b", "c"]);
  assert.deepEqual(toggleRange(["d"], visible, "b", "d", true), ["d", "b", "c"]);
  assert.deepEqual(toggleRange(["a", "b", "c"], visible, "b", "c", true), ["a"]);
});
test("a stale page anchor falls back to toggling one book", () => {
  assert.deepEqual(toggleRange(["old"], ["a", "b"], "b", "old", true), ["old", "b"]);
});
test("selection respects the backend limit without discarding earlier pages", () => {
  const initial = Array.from({ length: 99 }, (_, i) => String(i));
  const selected = selectIds(initial, ["new", "overflow"], true);
  assert.equal(selected.length, SELECTION_LIMIT);
  assert.deepEqual(selected.slice(0, 99), initial);
  assert.equal(selected[99], "new");
});
test("password feedback matches the backend length, case, digit and UTF-8 policy", () => {
  assert.equal(passwordError("StrongPass1"), "");
  assert.equal(passwordError("ПарольТест1"), "");
  assert.ok(passwordError("short1A"));
  assert.ok(passwordError("alllowercase1"));
  assert.ok(passwordError("NoDigitsHere"));
  assert.ok(passwordError("А".repeat(35) + "а1"));
});
test("tags are counted after trimming and blank entries are ignored", () => {
  assert.equal(tagsError("сказка, , короткое,"), "");
  assert.ok(tagsError(Array(21).fill("тег").join(",")));
  assert.ok(tagsError("т".repeat(61)));
});

test("return navigation preserves catalog filters but rejects other destinations", () => {
  assert.equal(decodeURI(safeCatalogPath("/?q=теремок&page=2")), "/?q=теремок&page=2");
  assert.equal(decodeURI(safeCatalogPath("/archive?q=тест", true)), "/archive?q=тест");
  for (const unsafe of ["https://example.org/", "//example.org/", "/login", "/works/123", "/archive", "/\\example.org/"]) assert.equal(safeCatalogPath(unsafe), "/");
});
