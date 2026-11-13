<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import { api, download, message, date, statusLabels } from "$lib/api";
  import type { Render } from "$lib/types";
  import Icon from "./Icon.svelte";
  let { id }: { id: string } = $props();
  let job = $state<Render | null>(null),
    error = $state(""),
    audioUrl = $state(""),
    loading = $state(false);
  let closed = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  async function refresh() {
    if (closed) return;
    try {
      job = await api<Render>(`recordings/renders/${id}`);
      error = "";
      if (!["COMPLETED", "FAILED"].includes(job.status))
        timer = setTimeout(() => {
          void refresh();
        }, 2000);
    } catch (e) {
      error = message(e);
    }
  }
  async function listen(index: number) {
    loading = true;
    error = "";
    try {
      const response = await fetch(
        `/api/recordings/renders/${id}/outputs/${index}`,
      );
      if (!response.ok) throw new Error("Не удалось загрузить аудио");
      if (audioUrl) URL.revokeObjectURL(audioUrl);
      audioUrl = URL.createObjectURL(await response.blob());
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  async function save(index: number) {
    try {
      await download(
        `recordings/renders/${id}/outputs/${index}`,
        `Теремок — сборка ${index + 1}.mp3`,
      );
    } catch (e) {
      error = message(e);
    }
  }
  onMount(() => {
    void refresh();
  });
  onDestroy(() => {
    closed = true;
    if (timer) clearTimeout(timer);
    if (audioUrl) URL.revokeObjectURL(audioUrl);
  });
</script>

<section class="panel">
  <div class="row between">
    <h2>Результат сборки</h2>
    {#if job}<span class={`badge ${job.status}`}
        >{statusLabels[job.status] || "Обрабатывается"}</span
      >{/if}
  </div>
  {#if error}<div class="error" role="alert">
      {error}<button class="btn ghost" onclick={refresh}>Повторить</button>
    </div>{/if}{#if job}<p class="hint">
      Создано {date(job.createdAt)} · {job.outputMode === "SINGLE_FILE"
        ? "Один MP3-файл"
        : "Отдельные MP3 по частям"}
    </p>
    {#if job.status === "FAILED"}<div class="error result-error">
        Не удалось собрать аудио. {job.error || "Повторите создание сборки."}
      </div>{:else if job.status !== "COMPLETED"}<div class="processing">
        <Icon name="headphones" size={28} />
        <p>Собираем ваши голоса в одну историю</p>
        <small
          >Статус обновляется автоматически. Можно перейти в другой раздел.</small
        >
      </div>{/if}{#each job.outputs as output, index}<div class="output">
        <div class="row">
          <span class="output-icon"><Icon name="file" size={23} /></span>
          <div>
            <strong
              >{job.outputMode === "SINGLE_FILE"
                ? "Полное произведение"
                : `Часть ${index + 1}`}</strong
            ><small>Аудиофайл MP3</small>
          </div>
        </div>
        <div class="row">
          <button
            class="btn ghost"
            disabled={loading}
            onclick={() => listen(index)}
            ><Icon name="play" size={16} />Слушать</button
          ><button class="btn secondary" onclick={() => save(index)}
            ><Icon name="download" size={16} />Скачать</button
          >
        </div>
      </div>{/each}{#if audioUrl}<audio
        controls
        autoplay
        src={audioUrl}
        aria-label="Прослушивание готовой сборки"
      ></audio>{/if}{:else if !error}<p class="hint">
      Проверяем состояние сборки…
    </p>{/if}
</section>

<style>
  .panel > .row h2 {
    font-size: 17px;
  }
  .panel > .hint {
    margin-top: 15px;
  }
  .output {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-top: 1px solid var(--line);
    padding-top: 18px;
    margin-top: 20px;
    gap: 16px;
  }
  .output strong {
    font-size: 12px;
    font-weight: 500;
  }
  .output small {
    display: block;
    font-size: 10px;
    margin-top: 6px;
  }
  .output-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #7c9e82;
    background: #f0f5f0;
    width: 43px;
    height: 47px;
    border-radius: 7px;
  }
  .processing {
    text-align: center;
    padding: 40px 20px;
    color: #9aada0;
  }
  .processing p {
    font-size: 13px;
    color: #63806a;
    margin-top: 15px;
  }
  .processing small {
    font-size: 10px;
    display: block;
    margin-top: 8px;
  }
  .result-error {
    margin: 20px 0 0;
  }
  audio {
    width: 100%;
    height: 40px;
    margin-top: 24px;
  }
</style>
