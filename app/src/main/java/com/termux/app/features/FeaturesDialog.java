package com.termux.app.features;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.R;
import com.termux.app.TermuxActivity;

import java.util.ArrayList;
import java.util.List;

/** Searchable Tools sheet, grouped in registry order. */
public final class FeaturesDialog {
    private FeaturesDialog() {}

    public static void show(@NonNull TermuxActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(dialog.getContext()).inflate(R.layout.feature_tools_sheet, null);
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        RecyclerView list = content.findViewById(R.id.tools_list);
        TextView empty = content.findViewById(R.id.tools_empty);
        TextInputEditText search = content.findViewById(R.id.tools_search);
        ToolsAdapter adapter = new ToolsAdapter(activity, dialog);
        list.setLayoutManager(new LinearLayoutManager(dialog.getContext()));
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        Runnable filter = () -> {
            adapter.setSections(FeatureToolsModel.filter(FeatureRegistry.getSections(),
                search.getText() == null ? "" : search.getText().toString(),
                feature -> feature.isAvailable(activity),
                feature -> activity.getString(feature.getTitle()),
                feature -> activity.getString(feature.getSummary())));
            empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            list.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
            list.scrollToPosition(0);
        };
        // In landscape the keyboard leaves little room, so leave out the title.
        if (activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            content.findViewById(R.id.tools_title).setVisibility(View.GONE);
        // The search key opens the first match, so "git" and Enter opens Git integration.
        search.setOnEditorActionListener((view, actionId, event) -> {
            TermuxFeature first = adapter.getFirstFeature();
            if (first != null) {
                dialog.dismiss();
                first.open(activity);
            }
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        filter.run();
        dialog.setOnShowListener(ignored -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.requestLayout();
            }
            // Start expanded even in landscape: resizing leaves the weighted list scrollable.
            dialog.getBehavior().setSkipCollapsed(true);
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        dialog.show();
    }

    private static final class ToolsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final TermuxActivity activity;
        private final BottomSheetDialog dialog;
        // A row contains either a section title resource or a feature, never both.
        private final List<Object> rows = new ArrayList<>();

        ToolsAdapter(TermuxActivity activity, BottomSheetDialog dialog) {
            this.activity = activity;
            this.dialog = dialog;
        }

        void setSections(List<FeatureToolsModel.Section<TermuxFeature>> sections) {
            rows.clear();
            for (FeatureToolsModel.Section<TermuxFeature> section : sections) {
                rows.add(section.title);
                rows.addAll(section.features);
            }
            notifyDataSetChanged();
        }

        @Override public int getItemCount() { return rows.size(); }

        /** The first feature shown, or {@code null} if nothing matches. */
        TermuxFeature getFirstFeature() {
            for (Object row : rows) if (row instanceof TermuxFeature) return (TermuxFeature) row;
            return null;
        }
        @Override public int getItemViewType(int position) { return rows.get(position) instanceof Integer ? 0 : 1; }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                type == 0 ? R.layout.feature_tools_header : R.layout.item_feature, parent, false);
            if (type == 0) ViewCompat.setAccessibilityHeading(view, true);
            else {
                view.setFocusable(true);
                ViewCompat.setScreenReaderFocusable(view, true);
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (row instanceof Integer) {
                ((TextView) holder.itemView).setText((Integer) row);
                return;
            }
            TermuxFeature feature = (TermuxFeature) row;
            ((TextView) holder.itemView.findViewById(R.id.feature_title)).setText(feature.getTitle());
            ((TextView) holder.itemView.findViewById(R.id.feature_summary)).setText(feature.getSummary());
            ((ImageView) holder.itemView.findViewById(R.id.feature_icon)).setImageResource(feature.getIcon());
            holder.itemView.setOnClickListener(view -> {
                dialog.dismiss();
                feature.open(activity);
            });
        }
    }
}
