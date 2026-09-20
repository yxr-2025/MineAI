package com.mineai.integration;

import com.google.gson.JsonElement;

public record MachineResult(boolean success, String message, JsonElement data) {

    public static MachineResult ok(String message) {
        return new MachineResult(true, message, null);
    }

    public static MachineResult ok(String message, JsonElement data) {
        return new MachineResult(true, message, data);
    }

    public static MachineResult fail(String message) {
        return new MachineResult(false, message, null);
    }
}
