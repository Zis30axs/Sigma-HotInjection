package dev.zis30axs.sigma.hotinjection.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered set of ClickGUI buttons. Modules register their own entries here. */
public final class ClickGuiRegistry {
    private final Map<String, ClickGuiButton> buttons = new LinkedHashMap<String, ClickGuiButton>();

    public synchronized ClickGuiButton register(ClickGuiButton button) {
        if (button == null) {
            throw new NullPointerException("button");
        }
        if (buttons.containsKey(button.getId())) {
            throw new IllegalArgumentException("Duplicate ClickGUI button id: " + button.getId());
        }
        buttons.put(button.getId(), button);
        return button;
    }

    public synchronized ClickGuiButton register(String id, String label, String description, ClickGuiAction action) {
        return register(new ClickGuiButton(id, label, description, action));
    }

    public synchronized boolean unregister(String id) {
        return id != null && buttons.remove(id) != null;
    }

    public synchronized ClickGuiButton get(String id) {
        return id == null ? null : buttons.get(id);
    }

    public synchronized List<ClickGuiButton> all() {
        return Collections.unmodifiableList(new ArrayList<ClickGuiButton>(buttons.values()));
    }

    public synchronized int size() {
        return buttons.size();
    }
}
