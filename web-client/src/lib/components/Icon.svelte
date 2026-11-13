<script lang="ts">
  import { HugeiconsIcon } from "@hugeicons/svelte";
  import {
    Loading03Icon,
    ArrowUp01Icon,
    CheckmarkSquare02Icon,
    FilterHorizontalIcon,
    ViewIcon,
    ViewOffIcon,
    ArrowTurnBackwardIcon,
    BookOpen01Icon,
    Search01Icon,
    Mic01Icon,
    HeadphonesIcon,
    ChartHistogramIcon,
    FileDownloadIcon,
    Settings01Icon,
    Logout01Icon,
    Add01Icon,
    ArrowLeft01Icon,
    ArrowRight01Icon,
    Cancel01Icon,
    Archive01Icon,
    UserGroupIcon,
    File01Icon,
    CheckmarkCircle01Icon,
    Delete02Icon,
    PlayIcon,
    StopIcon,
    Upload01Icon,
    Edit02Icon,
    MoreHorizontalIcon,
    ArrowDown01Icon,
    RefreshIcon,
    InformationCircleIcon,
  } from "@hugeicons/core-free-icons";
  let {
    name,
    size = 21,
    strokeWidth = 1.65,
  }: { name: string; size?: number; strokeWidth?: number } = $props();
  const icons: Record<string, typeof BookOpen01Icon> = {
    loading: Loading03Icon,
    up: ArrowUp01Icon,
    select: CheckmarkSquare02Icon,
    filter: FilterHorizontalIcon,
    eye: ViewIcon,
    eyeOff: ViewOffIcon,
    undo: ArrowTurnBackwardIcon,
    book: BookOpen01Icon,
    search: Search01Icon,
    mic: Mic01Icon,
    headphones: HeadphonesIcon,
    chart: ChartHistogramIcon,
    download: FileDownloadIcon,
    settings: Settings01Icon,
    logout: Logout01Icon,
    plus: Add01Icon,
    left: ArrowLeft01Icon,
    right: ArrowRight01Icon,
    close: Cancel01Icon,
    archive: Archive01Icon,
    users: UserGroupIcon,
    file: File01Icon,
    check: CheckmarkCircle01Icon,
    trash: Delete02Icon,
    play: PlayIcon,
    stop: StopIcon,
    upload: Upload01Icon,
    edit: Edit02Icon,
    more: MoreHorizontalIcon,
    down: ArrowDown01Icon,
    refresh: RefreshIcon,
    info: InformationCircleIcon,
  };
</script>

<span class:spinning={name === "loading"} class="icon"
  ><HugeiconsIcon
    icon={icons[name] || BookOpen01Icon}
    {size}
    {strokeWidth}
    aria-hidden="true"
  /></span
>

<style>
  .icon {
    display: inline-flex;
    flex-shrink: 0;
  }
  .spinning {
    animation: spin 900ms linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
