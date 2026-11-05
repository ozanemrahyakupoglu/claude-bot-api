package com.claudebot.api.dto;

import java.util.List;

public record RunDetail(RunSummary run, List<RunEvent> events, int droppedEvents) {
}
