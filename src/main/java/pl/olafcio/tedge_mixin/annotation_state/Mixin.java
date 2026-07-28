package pl.olafcio.tedge_mixin.annotation_state;

import java.util.List;

public record Mixin(List<String> targets, int priority, boolean remap) {}
