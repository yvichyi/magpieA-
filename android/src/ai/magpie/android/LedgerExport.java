package ai.magpie.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit export through Android's document picker. */
final class LedgerExport {
    private static final int REQUEST = 55;
    private static final Map<Activity, Selection> pending = new WeakHashMap<>();
    private static boolean copying;

    private static final class Selection {
        final InsightsScope scope;
        final String path;

        Selection(InsightsScope scope, LedgerQuery query) {
            this.scope = scope;
            this.path = query.csv(scope.zone);
        }
    }

    static void start(Activity activity, InsightsScope scope, LedgerQuery query) {
        if (!scope.isCurrent(activity)) return;
        if (copying || pending.containsKey(activity)) {
            message(activity, "已有导出进行中");
            return;
        }
        LocalDate nameDay = query.day == null ? LocalDate.now(ZoneId.of(scope.zone)) : query.day;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("text/csv")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, "magpie-requests-" + nameDay + ".csv");
        pending.put(activity, new Selection(scope, query));
        try {
            activity.startActivityForResult(intent, REQUEST);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            pending.remove(activity);
            message(activity, "无法打开系统文件选择器");
        }
    }

    static boolean onActivityResult(Activity activity, int request, int result, Intent data) {
        if (request != REQUEST) return false;
        Selection selection = pending.remove(activity);
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) {
            message(activity, "无效的导出位置");
            return true;
        }
        if (selection == null || !selection.scope.isCurrent(activity)) {
            message(activity, "导出已取消，请重新选择文件");
            return true;
        }
        Context context = activity.getApplicationContext();
        copying = true;
        message(activity, "正在导出筛选后的请求…");
        new Thread(() -> copy(context, uri, selection), "magpie-ledger-export").start();
        return true;
    }

    private static void copy(Context context, Uri uri, Selection selection) {
        ContentResolver resolver = context.getContentResolver();
        AtomicBoolean opened = new AtomicBoolean();
        String result;
        try {
            LedgerCsv.copy(selection.scope.origin, selection.scope.key, selection.path, () -> {
                OutputStream out = resolver.openOutputStream(uri, "wt");
                if (out == null) throw new IOException("无法打开导出文件");
                opened.set(true);
                return out;
            }, () -> selection.scope.isCurrent(context));
            result = "CSV 已导出";
        } catch (IOException | RuntimeException failure) {
            result = "导出失败，请重试";
            if (opened.get()) {
                boolean deleted = false;
                try {
                    deleted = DocumentsContract.deleteDocument(resolver, uri);
                } catch (Exception unavailable) {
                    // Some document providers only grant the app permission to write.
                }
                if (!deleted) result = "导出失败，文件可能不完整";
            }
        }
        String status = result;
        new Handler(Looper.getMainLooper()).post(() -> {
            copying = false;
            message(context, status);
        });
    }

    private static void message(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
