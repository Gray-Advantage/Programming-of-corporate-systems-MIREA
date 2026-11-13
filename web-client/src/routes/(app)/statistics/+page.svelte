<script lang="ts">
  import { onMount } from "svelte";
  import { api, message, statusLabels } from "$lib/api";
  import Icon from "$lib/components/Icon.svelte";
  let summary = $state<any>(null),
    system = $state<any>(null),
    error = $state(""),
    loading = $state(true);
  async function load() {
    loading = true;
    error = "";
    try {
      summary = await api("library/analytics/summary");
    } catch (e) {
      error = message(e);
    }
    try {
      system = await api("export/statistics");
    } catch (e) {
      error += (error ? " · " : "") + message(e);
    } finally {
      loading = false;
    }
  }
  const groups = $derived(
    summary
      ? [
          { title: "По статусу", rows: summary.byStatus },
          { title: "По сложности озвучивания", rows: summary.byComplexity },
          { title: "По коллекциям", rows: summary.byCollection },
        ]
      : [],
  );
  onMount(() => {
    void load();
  });
</script>

<svelte:head><title>Статистика — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Статистика</h1>
    <p>Состояние библиотеки и результаты совместной работы</p>
  </div>
  <button class="btn secondary" onclick={load}
    ><Icon name="refresh" size={17} />Обновить</button
  >
</div>
{#if error}<div class="error" role="alert">{error}</div>{/if}{#if loading}<div
    class="skeleton"
  >
    Считаем показатели…
  </div>{:else}{#if summary}<div class="stats-grid">
      <div class="stat">
        <small>Всего произведений</small><strong>{summary.totals.total}</strong>
      </div>
      <div class="stat">
        <small>В библиотеке</small><strong>{summary.totals.active}</strong>
      </div>
      <div class="stat">
        <small>В архиве</small><strong>{summary.totals.archived}</strong>
      </div>
      <div class="stat">
        <small>Рекомендованы редакцией</small><strong
          >{summary.totals.featured}</strong
        >
      </div>
    </div>
    <div class="charts">
      {#each groups as group}<section class="panel">
          <h2>{group.title}</h2>
          {#each group.rows as item}<div class="bar-row">
              <span>{statusLabels[item.name] || item.name}</span>
              <div
                class="bar-track"
                role="img"
                aria-label={`${statusLabels[item.name] || item.name}: ${item.count}`}
              >
                <div
                  style={`width:${Math.min(100, (Number(item.count) / Math.max(1, ...group.rows.map((r: any) => Number(r.count)))) * 100)}%`}
                ></div>
              </div>
              <strong>{item.count}</strong>
            </div>{:else}<p class="hint">Данных пока нет</p>{/each}
        </section>{/each}
      <section class="panel">
        <h2>Плановая длительность</h2>
        <div class="average">
          {Number(summary.totals.average_minutes).toLocaleString("ru-RU", {
            maximumFractionDigits: 1,
          })}<span>мин.</span>
        </div>
        <p class="hint">
          Средняя плановая длительность произведения в каталоге.
        </p>
        <p class="hint complexity">
          Сложность учитывает длительность и число ролей: от 30 минут или 6
          ролей — сложное произведение; от 10 минут или 3 ролей — среднее.
        </p>
      </section>
    </div>{/if}{#if system}<section class="panel system-stats">
      <h2>Озвучивание и участники</h2>
      <div class="system-grid">
        {#each system.metrics as metric}<div>
            <small>{metric.title}</small>{#if metric.error}<p class="hint">
                {metric.error}
              </p>{:else}<strong>{metric.value}</strong>{/if}
          </div>{/each}
      </div>
    </section>{/if}{/if}

<style>
  .average {
    font-size: 50px;
    font-weight: 500;
    letter-spacing: -2px;
    margin: 20px 0 14px;
    color: #547c5d;
  }
  .average span {
    font-size: 15px;
    color: #9ca79e;
    margin-left: 8px;
    letter-spacing: 0;
  }
  .complexity {
    margin-top: 18px;
  }
  .system-stats {
    margin-top: 24px;
  }
  .system-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 20px;
  }
  .system-grid small {
    font-size: 10px;
    display: block;
    min-height: 32px;
    line-height: 1.5;
  }
  .system-grid strong {
    display: block;
    margin-top: 12px;
    font-weight: 500;
    font-size: 26px;
  }
  @media (max-width: 1100px) {
    .system-grid {
      grid-template-columns: repeat(3, 1fr);
    }
  }
</style>
