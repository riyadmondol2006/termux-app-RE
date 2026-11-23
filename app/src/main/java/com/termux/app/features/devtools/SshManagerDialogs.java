package com.termux.app.features.devtools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxCommandRunner;
import com.termux.app.features.TermuxSessionLauncher;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Dialog flows for the SSH connection manager feature. Hosted by the TermuxActivity. */
public final class SshManagerDialogs {

    private SshManagerDialogs() {}

    /** Whether the openssh package (the {@code ssh} binary) is installed. */
    public static boolean isSshInstalled() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/ssh").canExecute();
    }

    /** Entry point: connection list, with add/import/keys actions. */
    public static void showConnections(@NonNull TermuxActivity activity) {
        if (!isSshInstalled()) {
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.feature_devtools_ssh_install_title)
                .setMessage(R.string.feature_devtools_ssh_install_message)
                .setPositiveButton(R.string.feature_devtools_ssh_install, (d, w) -> installSsh(activity))
                .setNegativeButton(R.string.feature_devtools_cancel, (d, w) -> showConnectionList(activity))
                .show();
            return;
        }
        showConnectionList(activity);
    }

    private static void installSsh(@NonNull TermuxActivity activity) {
        TermuxSessionLauncher.runInNewSession(activity, "pkg",
            "pkg install openssh", false, null);
    }

    private static void showConnectionList(@NonNull TermuxActivity activity) {
        List<SshConnection> connections = SshConnectionStore.load(activity);

        LinearLayout root = paddedColumn(activity);
        LinearLayout buttons = buttonRow(activity);
        buttons.addView(smallButton(activity, R.string.feature_devtools_ssh_add,
            v -> showEdit(activity, null)));
        buttons.addView(smallButton(activity, R.string.feature_devtools_ssh_import_config,
            v -> importConfig(activity)));
        buttons.addView(smallButton(activity, R.string.feature_devtools_ssh_keys_title,
            v -> showKeys(activity)));
        root.addView(buttons);

        ListView list = new ListView(activity);
        List<String> labels = new ArrayList<>();
        for (SshConnection c : connections) {
            String title = c.label.isEmpty() ? c.describeTarget() : c.label;
            labels.add(title + "\n" + c.describeTarget());
        }
        if (labels.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.feature_devtools_ssh_no_connections);
            empty.setPadding(0, dp(activity, 16), 0, dp(activity, 16));
            root.addView(empty);
        } else {
            list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_2,
                android.R.id.text1, labels) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull android.view.ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    String[] parts = getItem(position).split("\n", 2);
                    ((TextView) view.findViewById(android.R.id.text1)).setText(parts[0]);
                    ((TextView) view.findViewById(android.R.id.text2)).setText(parts.length > 1 ? parts[1] : "");
                    return view;
                }
            });
            list.setOnItemClickListener((parent, view, position, id) -> showActions(activity, connections.get(position)));
            root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 320)));
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_ssh_activity_title)
            .setView(root)
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void showActions(@NonNull TermuxActivity activity, @NonNull SshConnection connection) {
        String[] items = {
            activity.getString(R.string.feature_devtools_ssh_connect),
            activity.getString(R.string.feature_devtools_ssh_copy_setup),
            activity.getString(R.string.feature_devtools_ssh_edit),
            activity.getString(R.string.feature_devtools_ssh_delete),
        };
        new MaterialAlertDialogBuilder(activity)
            .setTitle(connection.label.isEmpty() ? connection.describeTarget() : connection.label)
            .setItems(items, (d, which) -> {
                switch (which) {
                    case 0:
                        connect(activity, connection);
                        break;
                    case 1:
                        copyKeySetup(activity, connection);
                        break;
                    case 2:
                        showEdit(activity, connection);
                        break;
                    case 3:
                        new MaterialAlertDialogBuilder(activity)
                            .setMessage(activity.getString(R.string.feature_devtools_ssh_delete_confirm, connection.label))
                            .setPositiveButton(R.string.feature_devtools_ssh_delete, (dd, ww) -> {
                                SshConnectionStore.delete(activity, connection.label);
                                showConnectionList(activity);
                            })
                            .setNegativeButton(R.string.feature_devtools_cancel, null)
                            .show();
                        break;
                    default:
                        break;
                }
            })
            .show();
    }

    private static void connect(@NonNull TermuxActivity activity, @NonNull SshConnection connection) {
        TermuxSessionLauncher.runInNewSession(activity, "ssh " + connection.label,
            SshCommandBuilder.buildSshCommand(connection), true, null);
    }

    private static void copyKeySetup(@NonNull TermuxActivity activity, @NonNull SshConnection connection) {
        File sshDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.ssh");
        File[] keys = sshDir.listFiles((dir, name) -> name.endsWith(".pub"));
        if (keys == null || keys.length == 0) {
            Toast.makeText(activity, R.string.feature_devtools_ssh_no_keys, Toast.LENGTH_LONG).show();
            showKeys(activity);
            return;
        }
        String[] names = new String[keys.length];
        for (int i = 0; i < keys.length; i++) names[i] = keys[i].getName();
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_ssh_copy_setup)
            .setItems(names, (d, which) -> TermuxSessionLauncher.runInNewSession(activity,
                "ssh-copy-id " + connection.label,
                SshCommandBuilder.buildCopyIdCommand(connection, keys[which].getAbsolutePath()),
                true, null))
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void showEdit(@NonNull TermuxActivity activity, @Nullable SshConnection existing) {
        LinearLayout root = paddedColumn(activity);
        EditText label = field(root, activity, R.string.feature_devtools_ssh_label, existing == null ? "" : existing.label);
        EditText user = field(root, activity, R.string.feature_devtools_ssh_user, existing == null ? "" : existing.user);
        EditText host = field(root, activity, R.string.feature_devtools_ssh_host, existing == null ? "" : existing.host);
        EditText port = field(root, activity, R.string.feature_devtools_ssh_port,
            existing == null || existing.port <= 0 ? "" : String.valueOf(existing.port));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText identity = field(root, activity, R.string.feature_devtools_ssh_identity, existing == null ? "" : existing.identityFile);
        EditText jump = field(root, activity, R.string.feature_devtools_ssh_jump, existing == null ? "" : existing.jumpHost);
        EditText extra = field(root, activity, R.string.feature_devtools_ssh_extra, existing == null ? "" : existing.extraOptions);
        extra.setSingleLine(false);
        extra.setMinLines(2);
        CheckBox sync = new CheckBox(activity);
        sync.setText(R.string.feature_devtools_ssh_sync);
        sync.setChecked(existing == null || existing.syncToConfig);
        root.addView(sync);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(existing == null ? R.string.feature_devtools_ssh_add : R.string.feature_devtools_ssh_edit)
            .setView(new ScrollView(activity) {{ addView(root); }})
            .setPositiveButton(R.string.feature_devtools_save, (d, w) -> {
                String labelText = label.getText().toString().trim();
                String hostText = host.getText().toString().trim();
                if (labelText.isEmpty() || hostText.isEmpty()) {
                    Toast.makeText(activity, R.string.feature_devtools_ssh_label_required, Toast.LENGTH_LONG).show();
                    return;
                }
                String userText = user.getText().toString().trim();
                if (!SshCommandBuilder.isValidDestinationPart(hostText) ||
                    (!userText.isEmpty() && !SshCommandBuilder.isValidDestinationPart(userText))) {
                    Toast.makeText(activity, R.string.feature_devtools_ssh_invalid_destination, Toast.LENGTH_LONG).show();
                    return;
                }
                int portValue = 0;
                try {
                    String portText = port.getText().toString().trim();
                    if (!portText.isEmpty()) portValue = Integer.parseInt(portText);
                } catch (NumberFormatException e) {
                    portValue = 0;
                }
                SshConnection connection = new SshConnection(labelText,
                    user.getText().toString().trim(), hostText, portValue,
                    identity.getText().toString().trim(), jump.getText().toString().trim(),
                    extra.getText().toString().trim(), sync.isChecked());
                SshConnectionStore.upsert(activity, connection);
                showConnectionList(activity);
            })
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void importConfig(@NonNull TermuxActivity activity) {
        String config = SshConnectionStore.readConfigFile();
        if (config == null) {
            Toast.makeText(activity, R.string.feature_devtools_ssh_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        List<SshConnection> imported = SshConfigCodec.toConnections(SshConfigCodec.parse(config));
        List<SshConnection> current = SshConnectionStore.load(activity);
        int added = 0;
        for (SshConnection c : imported) {
            boolean exists = false;
            for (SshConnection e : current) {
                if (e.label.equalsIgnoreCase(c.label)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                current.add(c);
                added++;
            }
        }
        if (added > 0) SshConnectionStore.save(activity, current);
        Toast.makeText(activity, activity.getString(R.string.feature_devtools_ssh_imported, added),
            Toast.LENGTH_LONG).show();
        showConnectionList(activity);
    }

    private static void showKeys(@NonNull TermuxActivity activity) {
        File sshDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.ssh");
        File[] files = sshDir.listFiles();
        List<File> pubs = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".pub")) pubs.add(f);
            }
        }
        String[] names = new String[pubs.size()];
        for (int i = 0; i < pubs.size(); i++) names[i] = pubs.get(i).getName();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_ssh_keys_title)
            .setPositiveButton(R.string.feature_devtools_ssh_generate_key, (d, w) -> showGenerateKey(activity))
            .setNegativeButton(R.string.feature_devtools_cancel, null);
        if (names.length == 0) {
            builder.setMessage(R.string.feature_devtools_ssh_no_keys);
        } else {
            builder.setItems(names, (d, which) -> showKeyActions(activity, pubs.get(which)));
        }
        builder.show();
    }

    private static void showKeyActions(@NonNull TermuxActivity activity, @NonNull File publicKey) {
        String[] items = {activity.getString(R.string.feature_devtools_ssh_copy_public)};
        new MaterialAlertDialogBuilder(activity)
            .setTitle(publicKey.getName())
            .setItems(items, (d, which) -> {
                String content = readFile(publicKey);
                if (content == null) content = "";
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("ssh public key", content.trim()));
                    Toast.makeText(activity, R.string.feature_devtools_ssh_copied, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private static void showGenerateKey(@NonNull TermuxActivity activity) {
        LinearLayout root = paddedColumn(activity);
        EditText name = field(root, activity, R.string.feature_devtools_ssh_key_name, "id_ed25519");
        EditText passphrase = field(root, activity, R.string.feature_devtools_ssh_passphrase, "");
        passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_ssh_generate_key)
            .setView(root)
            .setPositiveButton(R.string.feature_devtools_ok, (d, w) -> {
                String keyName = name.getText().toString().trim();
                if (keyName.isEmpty()) keyName = "id_ed25519";
                if (keyName.contains("/")) keyName = new File(keyName).getName();
                String keyPath = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.ssh/" + keyName;
                List<String> argv = SshCommandBuilder.buildKeygenArgv(
                    keyPath, passphrase.getText().toString(), "");
                TermuxCommandRunner.run(activity,
                    argv.toArray(new String[0]), null, null, 60000, result -> {
                        if (result.isSuccessful()) {
                            Toast.makeText(activity, R.string.feature_devtools_ssh_key_generated,
                                Toast.LENGTH_SHORT).show();
                            showKeys(activity);
                        } else {
                            String error = result.error != null ? result.error : result.stderr;
                            Toast.makeText(activity,
                                activity.getString(R.string.feature_devtools_ssh_key_failed, error),
                                Toast.LENGTH_LONG).show();
                        }
                    });
            })
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    @Nullable
    private static String readFile(@NonNull File file) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    static LinearLayout paddedColumn(@NonNull TermuxActivity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 16);
        root.setPadding(padding, dp(activity, 8), padding, dp(activity, 8));
        return root;
    }

    static LinearLayout buttonRow(@NonNull TermuxActivity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    static Button smallButton(@NonNull TermuxActivity activity, int text, @NonNull View.OnClickListener listener) {
        Button button = new Button(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int margin = dp(activity, 4);
        params.setMargins(margin, margin, margin, margin);
        button.setLayoutParams(params);
        return button;
    }

    static EditText field(@NonNull LinearLayout root, @NonNull TermuxActivity activity, int hint, @NonNull String value) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        root.addView(edit);
        return edit;
    }

    static int dp(@NonNull TermuxActivity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
