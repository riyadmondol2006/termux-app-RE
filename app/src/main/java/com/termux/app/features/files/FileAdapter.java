package com.termux.app.features.files;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The list of files and folders. Rows whose path is in the selection are drawn as selected. */
final class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder> {
    interface Listener {
        void onClick(FileEntry entry);
        void onLongClick(FileEntry entry);
    }

    private final Listener listener;
    private final Set<String> selection;
    private List<FileEntry> entries = new ArrayList<>();

    FileAdapter(Listener listener, Set<String> selection) {
        this.listener = listener;
        this.selection = selection;
    }

    void submit(List<FileEntry> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    List<FileEntry> entries() { return entries; }

    /** Redraw one row after its selection changed. */
    void selectionChanged(FileEntry entry) {
        int index = entries.indexOf(entry);
        if (index >= 0) notifyItemChanged(index);
    }

    void allSelectionChanged() { notifyDataSetChanged(); }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.feature_files_item_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FileEntry entry = entries.get(position);
        Context context = holder.itemView.getContext();
        holder.icon.setImageResource(entry.link ? R.drawable.feature_files_ic_link :
            entry.directory ? R.drawable.feature_files_ic_folder : R.drawable.feature_files_ic_file);
        holder.icon.setImageTintList(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(holder.icon,
            entry.directory ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant)));
        holder.name.setText(entry.name);
        holder.info.setText(info(context, entry));
        boolean selected = selection.contains(entry.path());
        holder.itemView.setActivated(selected);
        holder.check.setVisibility(selected ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onClick(entry));
        holder.itemView.setOnLongClickListener(v -> { listener.onLongClick(entry); return true; });
    }

    /** The second line: item count or size, and the modified date. */
    static String info(Context context, FileEntry entry) {
        String date = DateUtils.formatDateTime(context, entry.modified,
            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_TIME);
        String first = null;
        if (entry.directory) {
            if (entry.children != FileEntry.UNKNOWN)
                first = context.getResources().getQuantityString(R.plurals.feature_files_items, entry.children, entry.children);
        } else {
            first = FileFormat.size(entry.size, Locale.getDefault());
        }
        String text = first == null ? date : context.getString(R.string.feature_files_info_file, first, date);
        return entry.link ? context.getString(R.string.feature_files_info_link, text) : text;
    }

    @Override public int getItemCount() { return entries.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon, check;
        final TextView name, info;
        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.feature_files_entry_icon);
            check = view.findViewById(R.id.feature_files_entry_check);
            name = view.findViewById(R.id.feature_files_entry_name);
            info = view.findViewById(R.id.feature_files_entry_info);
        }
    }
}
