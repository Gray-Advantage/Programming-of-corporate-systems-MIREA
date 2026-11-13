export async function wavFromRecording(blob: Blob): Promise<Blob> {
  const context = new AudioContext();
  try {
    const audio = await context.decodeAudioData(await blob.arrayBuffer());
    const samples = audio.length;
    const buffer = new ArrayBuffer(44 + samples * 2);
    const view = new DataView(buffer);
    const text = (offset: number, value: string) =>
      [...value].forEach((char, i) =>
        view.setUint8(offset + i, char.charCodeAt(0)),
      );
    text(0, "RIFF");
    view.setUint32(4, 36 + samples * 2, true);
    text(8, "WAVE");
    text(12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, audio.sampleRate, true);
    view.setUint32(28, audio.sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    text(36, "data");
    view.setUint32(40, samples * 2, true);
    const channels = Array.from({ length: audio.numberOfChannels }, (_, i) =>
      audio.getChannelData(i),
    );
    for (let i = 0; i < samples; i++) {
      const sample = Math.max(
        -1,
        Math.min(1, channels.reduce((s, c) => s + c[i], 0) / channels.length),
      );
      view.setInt16(
        44 + i * 2,
        sample < 0 ? sample * 0x8000 : sample * 0x7fff,
        true,
      );
    }
    return new Blob([buffer], { type: "audio/wav" });
  } finally {
    await context.close();
  }
}
