package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.sessions.SessionListModel;
import com.termux.app.features.sessions.SessionMenu;
import com.termux.app.features.sessions.SessionRegistry;
import com.termux.app.features.sessions.SessionWorkspaces;
import com.termux.app.features.sessions.WorkspaceBar;
import com.termux.app.features.sessions.WorkspaceStore;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * The sessions list in the drawer. It lists the sessions of the workspace selected in the
 * {@link WorkspaceBar} above it, or of all workspaces grouped by workspace, with pinned sessions
 * first, see {@link SessionListModel}.
 */
public class TermuxSessionsListViewController extends BaseAdapter implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    /** The sessions list of the service. */
    private final List<TermuxSession> mSessionList;

    /** The rows currently shown, only changed in {@link #notifyDataSetChanged()}. */
    private List<SessionListModel.Row> mRows = new ArrayList<>();

    @Nullable private final WorkspaceBar mWorkspaceBar;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        this.mActivity = activity;
        this.mSessionList = sessionList;
        this.mWorkspaceBar = WorkspaceBar.attach(activity, activity.findViewById(R.id.terminal_sessions_list));
        updateRows();
    }

    @Override
    public void notifyDataSetChanged() {
        updateRows();
        if (mWorkspaceBar != null) mWorkspaceBar.update();
        super.notifyDataSetChanged();
        updateCheckedItem();
    }

    private void updateRows() {
        SessionRegistry.retain(mSessionList);
        mRows = SessionWorkspaces.buildRows(mActivity, mSessionList);
    }

    /** Check the row of the current session, since rows may have moved. */
    private void updateCheckedItem() {
        ListView listView = mActivity.findViewById(R.id.terminal_sessions_list);
        if (listView == null || listView.getAdapter() != this) return;
        int position = getRowPosition(mActivity.getCurrentSession());
        if (position >= 0)
            listView.setItemChecked(position, true);
        else
            listView.clearChoices();
    }

    /** Get the position of the row of {@code session}, or -1 if it is not listed. */
    public int getRowPosition(@Nullable TerminalSession session) {
        if (session == null) return -1;
        for (int i = 0; i < mRows.size(); i++) {
            TermuxSession rowSession = getItem(i);
            if (rowSession != null && rowSession.getTerminalSession() == session) return i;
        }
        return -1;
    }

    @Override
    public int getCount() {
        return mRows.size();
    }

    @Nullable
    @Override
    public TermuxSession getItem(int position) {
        SessionListModel.Row row = mRows.get(position);
        if (row.type != SessionListModel.ROW_SESSION || row.sessionIndex >= mSessionList.size()) return null;
        return mSessionList.get(row.sessionIndex);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return SessionListModel.ROW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position).type;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return mRows.get(position).type == SessionListModel.ROW_SESSION;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        SessionListModel.Row row = mRows.get(position);
        LayoutInflater inflater = mActivity.getLayoutInflater();
        switch (row.type) {
            case SessionListModel.ROW_HEADER: {
                View view = convertView != null ? convertView : inflater.inflate(R.layout.feature_sessions_item_workspace_header, parent, false);
                ((TextView) view).setText(WorkspaceStore.getWorkspaceName(mActivity, row.workspaceId));
                return view;
            }
            case SessionListModel.ROW_EMPTY:
                return convertView != null ? convertView : inflater.inflate(R.layout.feature_sessions_item_empty, parent, false);
            default:
                return getSessionView(row, convertView, parent);
        }
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    private View getSessionView(@NonNull SessionListModel.Row row, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);

        TerminalSession sessionAtRow = row.sessionIndex < mSessionList.size() ? mSessionList.get(row.sessionIndex).getTerminalSession() : null;
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return sessionRowView;
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + row.number + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        // Colours come from the activity theme (day/night and dynamic colours), see session_item_text.xml.
        ColorStateList textColor;
        if (sessionRunning || sessionAtRow.getExitStatus() == 0) {
            textColor = AppCompatResources.getColorStateList(mActivity, R.color.session_item_text);
        } else {
            textColor = ColorStateList.valueOf(MaterialColors.getColor(sessionTitleView, androidx.appcompat.R.attr.colorError));
        }
        sessionTitleView.setTextColor(textColor);

        // Pinned sessions show a pin at the end.
        boolean pinned = SessionRegistry.get(sessionAtRow).pinned;
        sessionTitleView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, pinned ? R.drawable.feature_sessions_ic_pin : 0, 0);
        TextViewCompat.setCompoundDrawableTintList(sessionTitleView, textColor);
        sessionTitleView.setContentDescription(pinned ? fullSessionTitle + ", " + mActivity.getString(R.string.feature_sessions_pinned) : null);
        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        if (clickedSession == null) return;
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        if (selectedSession == null) return false;
        SessionMenu.show(mActivity, selectedSession.getTerminalSession());
        return true;
    }

}
