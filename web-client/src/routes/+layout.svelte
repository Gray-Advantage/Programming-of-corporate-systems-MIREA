<script lang="ts">
  import "@fontsource/golos-text/400.css";
  import "@fontsource/golos-text/500.css";
  import "@fontsource/golos-text/600.css";
  import "@fontsource/golos-text/700.css";
  import "../app.css";
  let { children } = $props();
</script>

<svelte:head
  ><title>Теремок — библиотека голосов</title><meta
    name="description"
    content="Библиотека произведений и студия озвучивания по ролям"
  /></svelte:head
>
{@render children()}
