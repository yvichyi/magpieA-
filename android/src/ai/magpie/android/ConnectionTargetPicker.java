package ai.magpie.android;

import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Searchable catalog picker. Models and readable routing groups use the same workflow. */
final class ConnectionTargetPicker implements AppScreen {
    private final LinearLayout root;
    private final EditText query;

    ConnectionTargetPicker(ScreenHost host, List<ConnectScreen.Target> targets, BooleanSupplier current,
                           Consumer<String> selected) {
        Ui ui = host.ui();
        root = ui.column();
        root.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
        root.setFocusableInTouchMode(true);
        query = new EditText(ui.a);
        query.setSingleLine(true);
        query.setTextSize(16);
        query.setTypeface(ui.font(false, 400));
        query.setTextColor(ui.ink);
        query.setHintTextColor(ui.muted);
        query.setHint("搜索模型、分组或服务商");
        query.setContentDescription("搜索接入目标");
        query.setMinHeight(ui.dp(48));
        query.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        query.setBackground(ui.shape(ui.card, 14, true));
        root.addView(query, new LinearLayout.LayoutParams(-1, -2));
        TextView count = ui.text("", 13, ui.muted, false);
        count.setPadding(0, ui.dp(10), 0, ui.dp(10));
        root.addView(count);
        ListView list = new ListView(ui.a);
        list.setDivider(new ColorDrawable(ui.line));
        list.setDividerHeight(ui.dp(1));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        class Choices extends BaseAdapter {
            final List<ConnectScreen.Target> rows = new ArrayList<>();
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                ConnectScreen.Target row = rows.get(position);
                return CatalogViews.row(ui, row.name, row.group + " · " + row.reference, () -> {
                    if (!current.getAsBoolean()) {
                        Toast.makeText(ui.a, "工作空间已变化，请重新选择", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    hideKeyboard(ui);
                    selected.accept(row.reference);
                });
            }
            void applyFilter() {
                String keyword = query.getText().toString().trim().toLowerCase(Locale.ROOT);
                rows.clear();
                for (ConnectScreen.Target target : targets) {
                    String text = target.name + " " + target.group + " " + target.reference;
                    if (text.toLowerCase(Locale.ROOT).contains(keyword)) rows.add(target);
                }
                count.setText(rows.isEmpty() ? "没有找到匹配的接入目标" : rows.size() + " 个接入目标");
                notifyDataSetChanged();
            }
        }
        Choices choices = new Choices();
        list.setAdapter(choices);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { choices.applyFilter(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        choices.applyFilter();
        root.requestFocus();
    }

    @Override public View view() { return root; }

    private void hideKeyboard(Ui ui) {
        InputMethodManager ime = ui.a.getSystemService(InputMethodManager.class);
        if (ime != null) ime.hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
    }
}
