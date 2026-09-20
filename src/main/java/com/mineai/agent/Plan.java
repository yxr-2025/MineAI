package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A plan is a dependency graph of steps, not a flat list. A step only becomes
 * runnable once every step it depends on is DONE; a failed step skips its
 * transitive dependents so the planner can revise.
 */
public final class Plan {

    public enum Status {
        PENDING, DONE, FAILED, SKIPPED
    }

    public record Step(String id, String goal, List<String> dependsOn, Objective objective, Status status) {
        public Step withStatus(Status newStatus) {
            return new Step(id, goal, dependsOn, objective, newStatus);
        }
    }

    private final List<Step> steps = new ArrayList<>();

    public void set(List<Step> newSteps) {
        steps.clear();
        steps.addAll(newSteps);
    }

    public void clear() {
        steps.clear();
    }

    public int size() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public Step find(String id) {
        for (Step step : steps) {
            if (step.id().equals(id)) {
                return step;
            }
        }
        return null;
    }

    /**
     * The next runnable step whose dependencies are all satisfied.
     */
    public Step next() {
        for (Step step : steps) {
            if (step.status() == Status.PENDING && dependenciesMet(step)) {
                return step;
            }
        }
        return null;
    }

    public void markDone(String id) {
        replace(id, Status.DONE);
    }

    public void markFailed(String id) {
        replace(id, Status.FAILED);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Step step : steps) {
                if (step.status() != Status.PENDING) {
                    continue;
                }
                for (String dependency : step.dependsOn()) {
                    Step dep = find(dependency);
                    if (dep != null && (dep.status() == Status.FAILED || dep.status() == Status.SKIPPED)) {
                        replace(step.id(), Status.SKIPPED);
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    public boolean hasPending() {
        for (Step step : steps) {
            if (step.status() == Status.PENDING) {
                return true;
            }
        }
        return false;
    }

    public int doneCount() {
        int count = 0;
        for (Step step : steps) {
            if (step.status() == Status.DONE) {
                count++;
            }
        }
        return count;
    }

    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Step step : steps) {
            JsonObject object = new JsonObject();
            object.addProperty("id", step.id());
            object.addProperty("goal", step.goal());
            object.addProperty("status", step.status().name());
            JsonArray deps = new JsonArray();
            for (String dependency : step.dependsOn()) {
                deps.add(dependency);
            }
            object.add("depends_on", deps);
            if (step.objective() != null && step.objective().isSet()) {
                object.add("objective", step.objective().toJson());
            }
            array.add(object);
        }
        return array;
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        for (Step step : steps) {
            builder.append('[').append(step.status()).append("] ")
                    .append(step.id()).append(": ").append(step.goal());
            if (!step.dependsOn().isEmpty()) {
                builder.append(" (after ").append(String.join(",", step.dependsOn())).append(')');
            }
            if (step.objective() != null && step.objective().isSet()) {
                builder.append(" {done when: ").append(step.objective().describe()).append('}');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public static boolean hasCycle(List<Step> steps) {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (Step step : steps) {
            if (detectCycle(step, steps, visited, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean detectCycle(Step step, List<Step> all, Set<String> visited, Set<String> stack) {
        if (stack.contains(step.id())) {
            return true;
        }
        if (!visited.add(step.id())) {
            return false;
        }
        stack.add(step.id());
        for (String dependency : step.dependsOn()) {
            Step dep = null;
            for (Step candidate : all) {
                if (candidate.id().equals(dependency)) {
                    dep = candidate;
                    break;
                }
            }
            if (dep != null && detectCycle(dep, all, visited, stack)) {
                return true;
            }
        }
        stack.remove(step.id());
        return false;
    }

    private boolean dependenciesMet(Step step) {
        for (String dependency : step.dependsOn()) {
            Step dep = find(dependency);
            if (dep == null || dep.status() != Status.DONE) {
                return false;
            }
        }
        return true;
    }

    private void replace(String id, Status status) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(id)) {
                steps.set(i, steps.get(i).withStatus(status));
                return;
            }
        }
    }
}
