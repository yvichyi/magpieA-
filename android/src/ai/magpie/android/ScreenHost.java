package ai.magpie.android;
import android.app.Activity;
/** Navigation boundary shared by native pages and authenticated advanced tools. */
interface ScreenHost {
    Activity activity();
    Ui ui();
    void openRoot(String id);
    void openDetail(String title, AppScreen screen);
    void advanced(String view);
    void advanced(String view, String key, String value);
    void connect(String reference);
    void refresh();
}
