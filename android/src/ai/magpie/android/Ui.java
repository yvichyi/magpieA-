package ai.magpie.android;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

final class Ui {
    private static final java.util.Map<String,Typeface> fonts=new java.util.HashMap<>();
    final Activity a;
    final int bg, card, ink, muted, line, accent, soft, accentSoft;
    final boolean dark;
    final int dialogTheme;
    Ui(Activity activity) {
        a = activity;
        dark = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dialogTheme = dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
        // Neutral surfaces; hierarchy comes from alignment, spacing and rules.
        bg = Color.parseColor(dark ? "#11151A" : "#F3F5F7");
        card = Color.parseColor(dark ? "#181D23" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#E9EDF2" : "#1A2027");
        muted = Color.parseColor(dark ? "#A3A3A3" : "#65717C");
        line = Color.parseColor(dark ? "#333333" : "#DDE3E9");
        accent = Color.parseColor(dark ? "#E9EDF2" : "#1A2027");
        soft = Color.parseColor(dark ? "#262626" : "#E9EDF2");
        accentSoft = Color.parseColor(dark ? "#262626" : "#E9EDF2");
        a.getWindow().setStatusBarColor(bg); a.getWindow().setNavigationBarColor(bg);
        a.getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    int dp(float n) { return (int) (n * a.getResources().getDisplayMetrics().density + .5f); }
    LinearLayout column() { LinearLayout v = new LinearLayout(a); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(a); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(a); v.setText(s); v.setTextSize(Math.max(12,size)); v.setTextColor(color);
        v.setIncludeFontPadding(false);v.setLineSpacing(dp(4), 1.04f);v.setTypeface(font(false,bold?500:400));
        return v;
    }
    Typeface font(boolean numeric,int weight){
        String key=(numeric?"Inter":"NotoSansSC")+weight;
        synchronized(fonts){
            Typeface found=fonts.get(key);if(found!=null)return found;
            try{found=new Typeface.Builder(a.getAssets(),"fonts/"+(numeric?"Inter.ttf":"NotoSansSC.ttf")).setFontVariationSettings("'wght' "+weight).setWeight(weight).build();}
            catch(Exception e){found=Typeface.create("sans-serif",weight>=500?Typeface.BOLD:Typeface.NORMAL);}
            fonts.put(key,found);return found;
        }
    }
    TextView digits(String s,float size){TextView v=text(s,size,ink,true);v.setTypeface(font(true,600));v.setFontFeatureSettings("tnum");return v;}
    LinearLayout section(){LinearLayout v=column();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(24);v.setLayoutParams(p);return v;}
    GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (border) d.setStroke(dp(1), line); return d;
    }
    RippleDrawable glassControl(boolean primary) {
        if (GlassOptions.solidSurface(a)) return ripple(primary ? accent : soft, 16);
        int[] colors;
        int edge;
        if (primary) {
            colors = dark ? new int[]{0xffeef2f5, 0xffbcc7d0} : new int[]{0xff404b56, 0xff202830, 0xff171d24};
            edge = dark ? 0xfff6f8fa : 0xff54616d;
        } else {
            colors = dark ? new int[]{0xff343d46, 0xff232930} : new int[]{0xffffffff, 0xfff5f8fa, 0xffe9eef3};
            edge = dark ? 0xff58616b : 0xffcfd8e1;
        }
        GradientDrawable material = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        material.setCornerRadius(dp(16));
        material.setStroke(dp(1), edge);
        int feedback = primary && !dark ? 0x22ffffff : 0x1517222e;
        return new RippleDrawable(ColorStateList.valueOf(feedback), material, shape(Color.WHITE, 16, false));
    }
    LinearLayout card() {
        LinearLayout v = column(); v.setPadding(0, dp(12), 0, dp(12));
        v.setClipToOutline(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); v.setLayoutParams(p); return v;
    }
    void space(LinearLayout v, int h) { View s = new View(a); v.addView(s, new LinearLayout.LayoutParams(1, dp(h))); }
    Button button(String s, boolean primary, Runnable action) {
        Button b = new Button(a); b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        int[] disabled = {-android.R.attr.state_enabled};
        b.setTextColor(new ColorStateList(new int[][]{disabled, new int[0]},
                new int[]{muted, primary ? (dark ? bg : Color.WHITE) : ink}));
        b.setTypeface(font(false,500)); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setStateListAnimator(null);b.setIncludeFontPadding(false);b.setPadding(dp(14), dp(6), dp(14), dp(6));
        StateListDrawable material = new StateListDrawable();
        material.addState(disabled, shape(soft, 16, false));
        material.addState(new int[0], glassControl(primary));
        b.setBackground(material);
        b.setOnClickListener(v -> action.run()); UiMotion.press(b); return b;
    }
    void grow(LinearLayout row, View v) { row.addView(v, new LinearLayout.LayoutParams(0, -2, 1)); }
    void gap(LinearLayout row, int n) { row.addView(new View(a), new LinearLayout.LayoutParams(dp(n), 1)); }
    RippleDrawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x18000000),shape(color,radius,false),shape(android.graphics.Color.WHITE,radius,false));}
    View iconButton(String name,String description,Runnable action){FrameLayout box=new FrameLayout(a);box.setBackground(ripple(android.graphics.Color.TRANSPARENT,12));box.setContentDescription(description);LineIcon icon=new LineIcon(a,name,ink);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);box.addView(icon,p);box.setOnClickListener(v->action.run());UiMotion.press(box);box.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return box;}
    View iconTile(String name,int size){FrameLayout tile=new FrameLayout(a);tile.addView(new LineIcon(a,name,accent),new FrameLayout.LayoutParams(dp(size-14),dp(size-14),Gravity.CENTER));tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return tile;}
    void rule(LinearLayout box,int left){View v=new View(a);v.setBackgroundColor(line);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.leftMargin=dp(left);box.addView(v,p);}
    View copyButton(String description,Runnable action){
        FrameLayout box=new FrameLayout(a);box.setContentDescription(description);box.setBackground(ripple(Color.TRANSPARENT,8));
        box.addView(new LineIcon(a,"copy",ink),new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));
        LineIcon check=new LineIcon(a,"check",ink);check.setAlpha(0);box.addView(check,new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));
        box.setOnClickListener(v->{action.run();UiMotion.copyFeedback(box);box.announceForAccessibility("已复制");});UiMotion.press(box);box.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return box;
    }
    void viewport(LinearLayout root,View compactHeader,View... keyboardBars){
        root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            if(root.getHeight()==0)return;boolean ime=false;
            if(android.os.Build.VERSION.SDK_INT>=30&&root.getRootWindowInsets()!=null)ime=root.getRootWindowInsets().isVisible(WindowInsets.Type.ime());
            android.graphics.Rect visible=new android.graphics.Rect();root.getWindowVisibleDisplayFrame(visible);ime=ime||a.getResources().getDisplayMetrics().heightPixels-visible.bottom>dp(160);
            for(View bar:keyboardBars){int state=ime?View.GONE:View.VISIBLE;if(bar.getVisibility()!=state)bar.setVisibility(state);}
            if(compactHeader!=null){int state=root.getHeight()<dp(320)?View.GONE:View.VISIBLE;if(compactHeader.getVisibility()!=state)compactHeader.setVisibility(state);}
        });
    }
    View bottomNav(String selected,java.util.function.Consumer<String> action){
        GlassNavigation navigation = new GlassNavigation(this, action);
        navigation.select(NavigationModel.section(selected), false);
        return navigation;
    }
}
