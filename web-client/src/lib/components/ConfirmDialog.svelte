<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "./Icon.svelte";
  let {
    title,
    description,
    label = "Подтвердить",
    danger = false,
    busy = false,
    onconfirm,
    onclose,
  }: {
    title: string;
    description: string;
    label?: string;
    danger?: boolean;
    busy?: boolean;
    onconfirm: () => void;
    onclose: () => void;
  } = $props();
  let dialog: HTMLDialogElement;
  onMount(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.showModal();
    return () => {
      dialog.close();
      if (previous?.isConnected) previous.focus();
    };
  });
</script>

<dialog
  bind:this={dialog}
  aria-labelledby="confirmation-title"
  aria-describedby="confirmation-description"
  oncancel={(e) => {
    e.preventDefault();
    if (!busy) onclose();
  }}
  onclick={(e) => {
    if (e.target === dialog && !busy) {
      const r = dialog.getBoundingClientRect();
      if (
        e.clientX < r.left ||
        e.clientX > r.right ||
        e.clientY < r.top ||
        e.clientY > r.bottom
      )
        onclose();
    }
  }}
>
  <div class="dialog-heading">
    <span class:danger class="dialog-icon"
      ><Icon name={danger ? "trash" : "info"} size={24} /></span
    ><button
      class="icon-btn"
      aria-label="Закрыть подтверждение"
      disabled={busy}
      onclick={onclose}><Icon name="close" size={19} /></button
    >
  </div>
  <h2 id="confirmation-title">{title}</h2>
  <p id="confirmation-description">{description}</p>
  <div class="row">
    <button class="btn secondary" disabled={busy} onclick={onclose}
      >Отмена</button
    ><button class="btn" class:danger disabled={busy} onclick={onconfirm}
      >{#if busy}<Icon name="loading" size={17} />{/if}{busy
        ? "Выполняем…"
        : label}</button
    >
  </div>
</dialog>

<style>
  dialog {
    width: 460px;
    max-width: calc(100vw - 48px);
    padding: 26px;
    border: 1px solid var(--line);
    border-radius: 16px;
    color: inherit;
    box-shadow: 0 24px 90px #15271d33;
    overscroll-behavior: contain;
    animation: enter 160ms ease-out;
  }
  dialog::backdrop {
    background: #16251d66;
  }
  .dialog-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 18px;
  }
  .dialog-icon {
    width: 46px;
    height: 46px;
    display: grid;
    place-items: center;
    border-radius: 12px;
    background: var(--pale);
    color: var(--green);
  }
  .dialog-icon.danger {
    background: #fff0e7;
    color: #a3542b;
  }
  h2 {
    font-size: 21px;
  }
  p {
    color: var(--muted);
    font-size: 13px;
    margin: 12px 0 26px;
  }
  .row {
    justify-content: flex-end;
  }
  @keyframes enter {
    from {
      opacity: 0;
      transform: translateY(8px) scale(0.98);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
</style>
