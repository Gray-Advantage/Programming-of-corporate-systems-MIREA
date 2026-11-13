<script lang="ts">
  import WorkForm from "$lib/components/WorkForm.svelte";
  import { page } from "$app/state";
</script>

{#key page.params.id}<WorkForm id={page.params.id!} />{/key}
