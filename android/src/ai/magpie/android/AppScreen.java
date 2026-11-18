package ai.magpie.android;
import android.view.View;
/** A native page independent of the shared app chrome. */
interface AppScreen {
    View view();
    default void onResume() {}
    default void onPause() {}
    default Object saveAppearanceState() { return null; }
    default void restoreAppearanceState(Object state) {}
    default boolean onBack() { return false; }
    default void dispose() { onPause(); }
}
