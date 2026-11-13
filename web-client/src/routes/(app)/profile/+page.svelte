<script lang="ts">
  import { session, isAdmin } from "$lib/session.svelte";
  import { roleLabels } from "$lib/api";
  import Icon from "$lib/components/Icon.svelte";
</script>

<svelte:head><title>Профиль — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Настройки профиля</h1>
    <p>Учётная запись и безопасность</p>
  </div>
</div>
<section class="panel profile-panel">
  <div class="row">
    <span class="profile-avatar"
      >{session.user?.name.slice(0, 1).toUpperCase()}</span
    >
    <div>
      <h2>{session.user?.name}</h2>
      <p class="muted">{session.user?.login}</p>
    </div>
  </div>
  <div class="divider"></div>
  <p class="roles-label">Уровни доступа</p>
  <div class="row wrap roles">
    {#each session.user?.roles || [] as role}<span class="badge"
        >{roleLabels[role]}</span
      >{/each}
  </div>
  <p class="hint">
    Вход сохраняется между посещениями. После смены пароля потребуется войти
    заново.
  </p>
  <a href="/password" class="btn secondary"
    ><Icon name="settings" size={17} />Сменить пароль</a
  >{#if isAdmin()}<div class="divider"></div>
    <h3>Документация API</h3>
    <p class="hint docs-hint">
      Описание REST API библиотеки для разработки и защиты проекта.
    </p>
    <a
      href="/swagger-ui.html"
      target="_blank"
      rel="noreferrer"
      class="btn ghost"><Icon name="file" size={17} />Открыть Swagger</a
    >{/if}
</section>

<style>
  .profile-panel {
    max-width: 630px;
  }
  .profile-avatar {
    width: 58px;
    height: 58px;
    border-radius: 50%;
    background: #e5efe7;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #638c6b;
    font-size: 23px;
  }
  .profile-panel h2 {
    font-size: 20px;
  }
  .profile-panel p.muted {
    font-size: 12px;
    margin-top: 7px;
  }
  .roles {
    margin: 14px 0 25px;
  }
  .profile-panel > .hint {
    margin-bottom: 20px;
  }
  .docs-hint {
    margin-top: 12px;
  }
  .profile-panel > .roles-label {
    font-size: 12px;
  }
</style>
