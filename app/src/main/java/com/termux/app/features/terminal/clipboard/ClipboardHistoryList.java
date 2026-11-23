package com.termux.app.features.terminal.clipboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The items of a clipboard history, most recent first, without duplicates and limited in count and
 * size, so that a history of large copies does not use much memory and storage.
 */
final class ClipboardHistoryList {

    /** The max number of items. */
    static final int MAX_ITEMS = 50;
    /** Longer text is not kept, since a truncated paste would be wrong. */
    static final int MAX_ITEM_LENGTH = 100_000;
    /** The max total length of the items, older items are removed above it. */
    static final int MAX_TOTAL_LENGTH = 500_000;

    private final ArrayList<String> mItems = new ArrayList<>();
    private long mTotalLength;

    /**
     * Add {@code text} as the most recent item, moving it to the top if it is already in the list.
     *
     * @return Whether the list changed.
     */
    boolean add(@Nullable String text) {
        if (text == null || text.length() > MAX_ITEM_LENGTH || text.trim().isEmpty()) return false;
        if (!mItems.isEmpty() && mItems.get(0).equals(text)) return false;

        remove(text);
        mItems.add(0, text);
        mTotalLength += text.length();

        // Remove the oldest items, but keep the one just added.
        while (mItems.size() > 1 && (mItems.size() > MAX_ITEMS || mTotalLength > MAX_TOTAL_LENGTH))
            mTotalLength -= mItems.remove(mItems.size() - 1).length();
        return true;
    }

    /** Add the items of {@code items}, most recent first, like when loading a saved history. */
    void addAll(@NonNull List<String> items) {
        for (int i = items.size() - 1; i >= 0; i--) add(items.get(i));
    }

    /** @return Whether {@code text} was in the list. */
    boolean remove(@Nullable String text) {
        int index = text != null ? mItems.indexOf(text) : -1;
        if (index < 0) return false;
        mTotalLength -= mItems.remove(index).length();
        return true;
    }

    void clear() {
        mItems.clear();
        mTotalLength = 0;
    }

    int size() {
        return mItems.size();
    }

    /** Get the items, most recent first. */
    @NonNull
    List<String> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(mItems));
    }

}
