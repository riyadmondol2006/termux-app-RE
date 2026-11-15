package com.termux.shared.termux.interact;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Selection;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.shared.theme.ThemeUtils;

public final class TextInputDialogUtils {

    public interface TextSetListener {
        void onTextSet(String text);
    }

    public static void textInput(Activity activity, int titleText, String initialText,
                                 int positiveButtonText, final TextSetListener onPositive,
                                 int neutralButtonText, final TextSetListener onNeutral,
                                 int negativeButtonText, final TextSetListener onNegative,
                                 final DialogInterface.OnDismissListener onDismiss) {
        final Context context = ThemeUtils.getMaterialThemedContext(activity);

        // Material 3 outlined text field.
        final TextInputLayout inputLayout = new TextInputLayout(context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle);
        final EditText input = new TextInputEditText(inputLayout.getContext());
        inputLayout.addView(input, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        input.setSingleLine();
        if (initialText != null) {
            input.setText(initialText);
            // The text may have been truncated, like by the 5000 chars limit Android sets for
            // single line EditText, so use the length of the text actually set.
            Selection.setSelection(input.getText(), input.getText().length());
        }

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        input.setImeActionLabel(activity.getResources().getString(positiveButtonText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((v, actionId, event) -> {
            onPositive.onTextSet(input.getText().toString());
            dialogHolder[0].dismiss();
            return true;
        });

        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics());
        // https://m3.material.io/components/dialogs/specs
        int paddingSides = Math.round(24 * dipInPixels);
        int paddingTop = Math.round(8 * dipInPixels);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        layout.setPadding(paddingSides, paddingTop, paddingSides, 0);
        layout.addView(inputLayout);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(titleText).setView(layout)
            .setPositiveButton(positiveButtonText, (d, whichButton) -> onPositive.onTextSet(input.getText().toString()));

        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText, (dialog, which) -> onNeutral.onTextSet(input.getText().toString()));
        }

        if (onNegative == null) {
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> onNegative.onTextSet(input.getText().toString()));
        }

        if (onDismiss != null)
            builder.setOnDismissListener(onDismiss);

        dialogHolder[0] = builder.create();
        dialogHolder[0].setCanceledOnTouchOutside(false);
        dialogHolder[0].show();
    }

}
