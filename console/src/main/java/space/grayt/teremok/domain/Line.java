package space.grayt.teremok.domain;

/** Одна реплика книги. Номер сквозной, начинается с 1, и служит именем файла аудио. */
public record Line(int number, String speakerId, String text) {
}
