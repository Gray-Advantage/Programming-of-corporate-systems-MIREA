<script lang="ts">
  import Recording from "$lib/components/Recording.svelte";
  import { page } from "$app/state";
</script>

{#key page.params.id}<Recording id={page.params.id!} />{/key}
