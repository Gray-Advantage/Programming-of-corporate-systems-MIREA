<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/state";
  import { goto } from "$app/navigation";
  import Icon from "$lib/components/Icon.svelte";
  import { api, message } from "$lib/api";
  import { session, isAdmin, type User } from "$lib/session.svelte";
  import AccountMenu from "$lib/components/AccountMenu.svelte";
  let { children } = $props();
  let ready = $state(false);
  let error = $state("");
  const navigation = [
    { href: "/", label: "Все произведения", icon: "book" },
    { href: "/renders", label: "Мои сборки", icon: "headphones" },
    { href: "/archive", label: "Архив", icon: "archive" },
  ];
  const tools = [
    { href: "/statistics", label: "Статистика", icon: "chart" },
    { href: "/exchange", label: "Экспорт данных", icon: "download" },
  ];
  const admin = [
    { href: "/collections", label: "Коллекции", icon: "book" },
    { href: "/users", label: "Пользователи", icon: "users" },
    { href: "/audit", label: "Журнал аудита", icon: "file" },
  ];
  async function loadSession() {
    error = "";
    try {
      session.user = await api<User>("session/me");
      if (session.user.mustChangePassword) {
        await goto("/password");
        return;
      }
      ready = true;
    } catch (e: any) {
      if (e.status === 401) await goto("/login");
      else error = message(e);
    }
  }
  onMount(() => {
    void loadSession();
  });
  const current = $derived(
    page.url.pathname.startsWith("/works")
      ? "Произведение"
      : page.url.pathname === "/"
        ? "Библиотека"
        : [
            ...navigation,
            ...tools,
            ...admin,
            { href: "/profile", label: "Профиль" },
          ].find((n) => n.href === page.url.pathname)?.label || "Библиотека",
  );
</script>

<a class="skip-link" href="#main">К основному содержимому</a>
<div class="app-shell">
  <aside class="sidebar">
    <a class="brand" href="/" aria-label="Теремок — главная"
      ><span class="brand-symbol"><Icon name="book" size={25} /></span
      >теремок<span class="brand-dot">.</span></a
    >
    <nav aria-label="Основная навигация">
      <div class="nav-label">Библиотека</div>
      {#each navigation as item}<a
          class:active={page.url.pathname === item.href}
          href={item.href}
          aria-current={page.url.pathname === item.href ? "page" : undefined}
          ><Icon name={item.icon} size={20} /><span>{item.label}</span></a
        >{/each}
      <div class="nav-label tools-label">Инструменты</div>
      {#each tools as item}<a
          class:active={page.url.pathname === item.href}
          href={item.href}
          aria-current={page.url.pathname === item.href ? "page" : undefined}
          ><Icon name={item.icon} size={20} /><span>{item.label}</span></a
        >{/each}
      {#if isAdmin()}<div class="nav-label tools-label">Управление</div>
        {#each admin as item}<a
            class:active={page.url.pathname === item.href}
            href={item.href}
            aria-current={page.url.pathname === item.href ? "page" : undefined}
            ><Icon name={item.icon} size={20} /><span>{item.label}</span></a
          >{/each}{/if}
    </nav>
  </aside>
  <div class="app-body">
    <header class="topbar">
      <span>{current}</span>
      <AccountMenu />
    </header>
    <main id="main" tabindex="-1">
      {#if error}<div class="error" role="alert">{error}</div>
        <button class="btn secondary" onclick={loadSession}>Повторить</button
        >{:else if ready}{@render children()}{:else}<div class="skeleton">
          Открываем библиотеку…
        </div>{/if}
    </main>
    <footer><span>Теремок</span><span>Литература обретает голос</span></footer>
  </div>
</div>

<style>
  .app-shell {
    min-height: 100vh;
  }
  .sidebar {
    width: 230px;
    position: fixed;
    inset: 0 auto 0 0;
    background: #fff;
    border-right: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    padding: 0 16px;
    z-index: 10;
  }
  .brand {
    height: 92px;
    display: flex;
    align-items: center;
    padding: 0 10px;
    font-size: 25px;
    font-weight: 650;
    letter-spacing: -1.1px;
    gap: 9px;
  }
  .brand-symbol {
    display: flex;
    align-items: center;
    color: var(--green);
  }
  .brand-dot {
    color: var(--green);
    margin-left: -8px;
  }
  .nav-label {
    font-size: 11px;
    color: #657269;
    margin: 10px 14px 14px;
  }
  .sidebar nav {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }
  .tools-label {
    margin-top: 32px;
  }
  .sidebar nav a {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 7px;
    color: #5e6d63;
    font-size: 13px;
    margin-bottom: 4px;
    transition:
      background 150ms,
      color 150ms;
  }
  .sidebar nav a:hover {
    background: #f0f4f1;
    color: #283e30;
  }
  .sidebar nav a.active {
    background: #eaf3ec;
    color: #326c43;
    font-weight: 550;
  }
  .app-body {
    margin-left: 230px;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }
  .topbar {
    height: 72px;
    min-height: 72px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #fff;
    border-bottom: 1px solid var(--line);
    padding: 0 40px;
    font-size: 12px;
  }
  .topbar > span {
    color: #627067;
  }
  main {
    padding: 38px 40px 50px;
    width: 100%;
    max-width: 1540px;
    margin: 0 auto;
    flex: 1;
  }
  footer {
    display: flex;
    justify-content: space-between;
    margin: 0 40px;
    padding: 20px 0;
    border-top: 1px solid #e3e7e4;
    color: #6b786f;
    font-size: 10px;
  }
  footer span:first-child {
    font-weight: 550;
  }
  @media (max-width: 1100px) {
    .sidebar {
      width: 208px;
    }
    .app-body {
      margin-left: 208px;
    }
    main {
      padding: 30px 24px;
    }
    .topbar {
      padding: 0 24px;
    }
  }
</style>
