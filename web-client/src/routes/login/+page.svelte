<script lang="ts">
  import { validateForm, setFieldError } from "$lib/validation";
  import { goto } from "$app/navigation";
  import { post, message } from "$lib/api";
  import { session } from "$lib/session.svelte";
  import Icon from "$lib/components/Icon.svelte";
  let register = $state(false),
    login = $state(""),
    password = $state(""),
    name = $state(""),
    error = $state(""),
    busy = $state(false),
    visible = $state(false);
  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget as HTMLFormElement;
    busy = true;
    error = "";
    try {
      const result = await post(`session/${register ? "register" : "login"}`, {
        login,
        password,
        name,
      });
      session.user = result.user;
      await goto(result.user.mustChangePassword ? "/password" : "/");
    } catch (e) {
      if (register && (e as { status?: number }).status === 409)
        setFieldError(form, "login", message(e));
      else error = message(e);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head
  ><title>{register ? "Регистрация" : "Вход"} — Теремок</title></svelte:head
>
<div class="auth-page">
  <section class="auth-about">
    <a href="/" class="auth-brand"><Icon name="book" size={30} />теремок.</a>
    <div class="about-content">
      <span class="about-kicker">Библиотека голосов</span>
      <h1>У каждой истории<br />может быть<br /><em>ваш голос.</em></h1>
      <p>
        Выбирайте произведение, записывайте реплики<br />и собирайте истории из
        голосов разных людей.
      </p>
      <div class="about-books">
        <div class="mini-book one">Теремок</div>
        <div class="mini-book two">Красная<br />Шапочка</div>
        <div class="mini-book three">Колобок</div>
      </div>
    </div>
    <span class="about-footer">Учебный проект · Озвучивание по ролям</span>
  </section>
  <section class="auth-form">
    <div class="auth-form-inner">
      <div class="auth-mark"><Icon name="book" size={25} /></div>
      <h2>{register ? "Создать аккаунт" : "С возвращением"}</h2>
      <p class="muted">
        {register
          ? "Ваша первая история начинается здесь"
          : "Войдите, чтобы продолжить работу с произведениями"}
      </p>
      {#key register}<form
          onsubmit={submit}
          class="stack"
          use:validateForm
          aria-busy={busy}
        >
          {#if error}<div class="error" role="alert">
              {error}
            </div>{/if}{#if register}<label
              >Имя<input
                name="name"
                disabled={busy}
                bind:value={name}
                required
                maxlength="160"
                autocomplete="name"
                placeholder="Как к вам обращаться"
              /></label
            >{/if}<label
            >Логин<input
              name="login"
              spellcheck="false"
              autocapitalize="none"
              disabled={busy}
              bind:value={login}
              required
              maxlength="100"
              autocomplete="username"
              placeholder="Введите логин"
            /></label
          ><label
            >Пароль<span class="password-control"
              ><input
                bind:value={password}
                required
                type={visible ? "text" : "password"}
                name="password"
                disabled={busy}
                data-validation={register ? "password" : undefined}
                autocomplete={register ? "new-password" : "current-password"}
                placeholder="Введите пароль"
              /><button
                type="button"
                class="icon-btn"
                aria-label={visible ? "Скрыть пароль" : "Показать пароль"}
                aria-pressed={visible}
                onclick={() => (visible = !visible)}
                ><Icon name={visible ? "eyeOff" : "eye"} size={19} /></button
              ></span
            >{#if register}<span class="form-help"
                >От 10 символов, прописная и строчная буквы, цифра.</span
              >{/if}</label
          ><button class="btn" disabled={busy}
            >{busy
              ? "Подождите…"
              : register
                ? "Зарегистрироваться"
                : "Войти"}</button
          >
        </form>{/key}
      <p class="switch">
        {register ? "Уже есть аккаунт?" : "Ещё нет аккаунта?"}
        <button
          disabled={busy}
          onclick={() => {
            register = !register;
            error = "";
            password = "";
            visible = false;
          }}>{register ? "Войти" : "Зарегистрироваться"}</button
        >
      </p>
    </div>
  </section>
</div>

<style>
  .auth-page {
    min-height: 100vh;
    display: grid;
    grid-template-columns: 1fr 1fr;
    background: white;
  }
  .auth-about {
    background: #eaf0e9;
    display: flex;
    flex-direction: column;
    padding: 38px 52px;
    overflow: hidden;
  }
  .auth-brand {
    display: flex;
    gap: 10px;
    align-items: center;
    font-size: 27px;
    font-weight: 650;
    letter-spacing: -1px;
    color: #315f3e;
  }
  .about-content {
    margin: auto 0;
    padding: 60px 0 30px;
  }
  .about-kicker {
    color: #7b927f;
    font-size: 12px;
  }
  h1 {
    font-size: clamp(36px, 4vw, 58px);
    font-weight: 500;
    line-height: 1.15;
    letter-spacing: -2px;
    color: #35533e;
    margin: 26px 0;
  }
  h1 em {
    font-family: Georgia, serif;
    font-weight: 400;
  }
  p {
    font-size: 12px;
    line-height: 1.8;
  }
  .about-content p {
    color: #7c8e7e;
  }
  .about-books {
    display: flex;
    gap: 15px;
    margin-top: 45px;
    align-items: center;
  }
  .mini-book {
    height: 150px;
    width: 110px;
    border-radius: 2px 5px 5px 2px;
    box-shadow: 5px 8px 15px #3a533b18;
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    font-family: Georgia, serif;
    font-size: 17px;
    padding: 15px;
    border-left: 5px solid #ffffff15;
  }
  .one {
    background: #315e49;
    color: #ede4c6;
    transform: rotate(-7deg);
  }
  .two {
    background: #963e35;
    color: #f5e4cc;
    transform: rotate(4deg);
    height: 164px;
  }
  .three {
    background: #d8bb7c;
    color: #6b4e29;
    transform: rotate(-4deg);
  }
  .about-footer {
    font-size: 10px;
    color: #93a093;
  }
  .auth-form {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
  }
  .auth-form-inner {
    max-width: 355px;
    width: 100%;
  }
  .auth-mark {
    height: 50px;
    width: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--pale);
    color: var(--green);
    border-radius: 12px;
    margin-bottom: 24px;
  }
  h2 {
    font-size: 26px;
    font-weight: 600;
  }
  .auth-form p.muted {
    margin: 12px 0 32px;
    font-size: 12px;
  }
  .auth-form .btn {
    margin-top: 6px;
  }
  .switch {
    text-align: center;
    font-size: 11px;
    margin-top: 22px;
    color: var(--muted);
  }
  .switch button {
    color: var(--green);
    font-size: 11px;
    font-weight: 550;
  }
</style>
