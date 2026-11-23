package com.termux.app.features;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;

import java.util.ArrayList;
import java.util.List;

/** The Tools dialog that lists all available {@link TermuxFeature}s. */
public final class FeaturesDialog {

    private FeaturesDialog() {}

    public static void show(@NonNull TermuxActivity activity) {
        List<TermuxFeature> features = new ArrayList<>();
        for (TermuxFeature feature : FeatureRegistry.getFeatures()) {
            if (feature.isAvailable(activity)) features.add(feature);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_tools);
        if (features.isEmpty()) {
            builder.setMessage(R.string.msg_no_tools).setPositiveButton(android.R.string.ok, null).show();
            return;
        }

        ArrayAdapter<TermuxFeature> adapter = new ArrayAdapter<TermuxFeature>(activity, R.layout.item_feature, features) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.item_feature, parent, false);
                TermuxFeature feature = getItem(position);
                if (feature != null) {
                    ((TextView) view.findViewById(R.id.feature_title)).setText(feature.getTitle());
                    ((TextView) view.findViewById(R.id.feature_summary)).setText(feature.getSummary());
                    Drawable icon = AppCompatResources.getDrawable(getContext(), feature.getIcon());
                    ((ImageView) view.findViewById(R.id.feature_icon)).setImageDrawable(icon);
                }
                return view;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            dialog.dismiss();
            features.get(which).open(activity);
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

}
