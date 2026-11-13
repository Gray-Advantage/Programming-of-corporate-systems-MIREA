<script lang="ts">
  import Icon from "./Icon.svelte";
  let {
    title,
    author = "",
    large = false,
  }: { title: string; author?: string; large?: boolean } = $props();
  const palettes = [
    ["#355846", "#e5d8ad"],
    ["#8d4439", "#f7e9d6"],
    ["#d2bd8e", "#493c24"],
    ["#536a7a", "#e4e8db"],
    ["#705b6f", "#e9d9d0"],
    ["#686b48", "#e8dfb9"],
  ];
  const hash = $derived([...title].reduce((s, c) => s + c.charCodeAt(0), 0));
  const palette = $derived(palettes[hash % palettes.length]);
</script>

<div
  class="book-cover"
  class:large
  style={`--cover:${palette[0]};--ink:${palette[1]};--title-size:${title.length > 35 ? 17 : title.length > 22 ? 19 : 23}px`}
  aria-hidden="true"
>
  <div class="cover-frame">
    <span class="cover-author">{author}</span>
    <div class="cover-title">{title}</div>
    <div class="cover-emblem">
      <Icon name="book" size={large ? 43 : 30} strokeWidth={1} />
    </div>
    <span class="cover-caption">Произведение для озвучивания</span>
  </div>
  <div class="book-spine"></div>
</div>

<style>
  .book-cover {
    aspect-ratio: 3/4;
    background: var(--cover);
    color: var(--ink);
    border-radius: 3px 7px 7px 3px;
    position: relative;
    overflow: hidden;
    box-shadow: 1px 3px 6px #293c2d12;
    display: flex;
    padding: 14px 11px 14px 17px;
  }
  .book-spine {
    position: absolute;
    inset: 0 auto 0 0;
    width: 8px;
    border-right: 1px solid #00000018;
    background: #00000012;
  }
  .cover-frame {
    border: 1px solid color-mix(in srgb, var(--ink) 30%, transparent);
    width: 100%;
    display: grid;
    grid-template-rows: 28px minmax(0, 1fr) 32px 12px;
    row-gap: 9px;
    align-items: center;
    padding: 16px 10px 13px;
    position: relative;
  }
  .cover-author {
    font-size: 8px;
    line-height: 1.5;
    text-align: center;
    max-width: 100%;
    opacity: 0.8;
    min-height: 24px;
  }
  .cover-title {
    font-family: Georgia, "Times New Roman", serif;
    font-size: var(--title-size);
    line-height: 1.2;
    text-align: center;
    margin: 0;
    overflow-wrap: break-word;
    hyphens: auto;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    overflow: hidden;
  }
  .cover-emblem {
    display: flex;
    justify-content: center;
    margin: 0;
    opacity: 0.8;
  }
  .cover-caption {
    font-size: 6px;
    text-align: center;
    opacity: 0.65;
    line-height: 1.4;
  }
  .large .cover-title {
    font-size: 29px;
  }
  .large .cover-author {
    font-size: 11px;
  }
  .large .cover-caption {
    font-size: 8px;
  }
  .large .cover-frame {
    padding: 20px 10px 16px;
    grid-template-rows: 32px minmax(0, 1fr) 40px 16px;
  }
  .large {
    padding: 20px 15px 20px 23px;
  }
</style>
