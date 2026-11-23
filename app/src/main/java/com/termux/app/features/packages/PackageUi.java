package com.termux.app.features.packages;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

final class PackageUi {

    private PackageUi() {}

    static int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static LinearLayout vertical(@NonNull Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static TextView text(@NonNull Context context, @NonNull CharSequence value, float size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(0, dp(view, 4), 0, dp(view, 4));
        return view;
    }

    static TextView text(@NonNull Context context, @StringRes int value, float size) {
        return text(context, context.getString(value), size);
    }

    static TextView heading(@NonNull Context context, @StringRes int value) {
        return heading(context, context.getString(value));
    }

    static TextView heading(@NonNull Context context, @NonNull CharSequence value) {
        TextView view = text(context, value, 20);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(view, 18), 0, dp(view, 8));
        return view;
    }

    static MaterialButton button(@NonNull Context context, @StringRes int label) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    static MaterialCardView card(@NonNull Context context) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(card, 4), 0, dp(card, 4));
        card.setLayoutParams(params);
        card.setCardElevation(0);
        return card;
    }

    static LinearLayout cardContent(@NonNull MaterialCardView card) {
        LinearLayout content = vertical(card.getContext());
        int padding = dp(card, 16);
        content.setPadding(padding, padding, padding, padding);
        card.addView(content);
        return content;
    }

    static void weight(@NonNull View view, float weight) {
        view.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
    }

}
