package space.grayt.teremok.domain;

/** One line of a book. Numbers run through the whole book from 1 and name the audio file. */
public record Line(int number, String speakerId, String text) {
}
