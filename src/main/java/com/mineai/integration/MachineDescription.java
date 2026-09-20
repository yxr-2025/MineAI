package com.mineai.integration;

import java.util.List;

public record MachineDescription(String id, String displayName, List<SlotInfo> slots, List<String> operations) {
}
