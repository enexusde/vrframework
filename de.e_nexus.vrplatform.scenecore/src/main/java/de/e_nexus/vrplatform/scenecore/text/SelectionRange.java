package de.e_nexus.vrplatform.scenecore.text;

/** A text selection as absolute offsets into the buffer: [start, end). */
public record SelectionRange(int start, int end) {}
