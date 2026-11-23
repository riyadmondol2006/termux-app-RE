package com.termux.app.features.input;

import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Registry descriptor; all input entry points share InputActions. */
public final class InputFeature extends TermuxFeature {
    private final String id;
    private final int title, summary;

    public InputFeature(String id, int title, int summary) {
        this.id = id;
        this.title = title;
        this.summary = summary;
    }

    @Override
    public String getId() {
        return "input_" + id;
    }

    @Override
    public int getTitle() {
        return title;
    }

    @Override
    public int getSummary() {
        return summary;
    }

    @Override
    public void open(TermuxActivity activity) {
        switch (id) {
            case "palette":
                InputUi.palette(activity);
                break;
            case "quick":
                InputUi.quick(activity);
                break;
            case "toolbar":
                InputUi.toolbarEditor(activity);
                break;
            case "keyboard":
                KeyboardEditor.open(activity);
                break;
            case "history":
                HistoryManager.open(activity);
                break;
            default:
                throw new IllegalStateException(id);
        }
    }
}
