package space.grayt.teremok.domain;

/** A text-work fragment. Numbers run through the whole text work from 1. */
public record TextWorkFragment(int number, String voicePartId, String text) {
}
