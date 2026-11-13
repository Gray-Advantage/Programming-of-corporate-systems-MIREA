<script lang="ts">
  import { validateForm, setFieldError } from "$lib/validation";
  import { session } from "$lib/session.svelte";
  import { goto } from "$app/navigation";
  import { post, message } from "$lib/api";
  import Icon from "$lib/components/Icon.svelte";
  let current = $state(""),
    next = $state(""),
    repeat = $state(""),
    error = $state(""),
    busy = $state(false);
  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget as HTMLFormElement;
    error = "";
    busy = true;
    try {
      await post("session/password", {
        currentPassword: current,
        newPassword: next,
      });
      await post("session/logout");
      await goto("/login");
    } catch (e) {
      const text = message(e);
      if (/текущий пароль/i.test(text)) setFieldError(form, "current", text);
      else if (/новый пароль/i.test(text)) setFieldError(form, "next", text);
      else error = text;
    } finally {
      busy = false;
    }
  }
</script>

<div class="password-page">
  <form class="panel stack" onsubmit={submit} use:validateForm aria-busy={busy}>
    <Icon name="settings" size={28} />
    <h1>
      {session.user?.mustChangePassword
        ? "Смените временный пароль"
        : "Смена пароля"}
    </h1>
    <p class="muted">
      {session.user?.mustChangePassword
        ? "Перед началом работы задайте собственный пароль."
        : "Введите текущий пароль и придумайте новый."} После сохранения потребуется
      войти заново.
    </p>
    {#if error}<div class="error" role="alert">{error}</div>{/if}<label
      >Текущий пароль<input
        type="password"
        required
        name="current"
        disabled={busy}
        bind:value={current}
        autocomplete="current-password"
      /></label
    ><label
      >Новый пароль<input
        type="password"
        required
        data-validation="password"
        name="next"
        disabled={busy}
        bind:value={next}
        autocomplete="new-password"
      /><span class="form-help"
        >От 10 символов, прописная и строчная буквы, цифра.</span
      ></label
    ><label
      >Повторите новый пароль<input
        type="password"
        required
        data-match="next"
        name="repeat"
        disabled={busy}
        bind:value={repeat}
        autocomplete="new-password"
      /></label
    ><button class="btn" disabled={busy}
      >{busy ? "Сохраняем…" : "Сохранить пароль"}</button
    ><a
      href={session.user && !session.user.mustChangePassword
        ? "/profile"
        : "/login"}
      class="btn ghost"
      >{session.user && !session.user.mustChangePassword
        ? "Отмена"
        : "Вернуться ко входу"}</a
    >
  </form>
</div>

<style>
  .password-page {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .panel {
    width: 420px;
    padding: 32px;
  }
  h1 {
    font-size: 25px;
  }
  p {
    font-size: 12px;
  }
</style>
