package ai.magpie.android;
import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Native searchable, bounded choices; only currently available models enter this list. */
final class ModelPicker {
    static Dialog show(Activity a,Ui ui,WorkspaceSnapshot catalog,Set<String> favorites,Consumer<String> selected){
        Dialog d=new Dialog(a,ui.dialogTheme);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=ui.column();root.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),0);root.setBackgroundColor(ui.bg);
        LinearLayout title=ui.row();ui.grow(title,ui.text("选择接入模型",22,ui.ink,true));title.addView(ui.iconButton("down","关闭模型选择",d::dismiss));root.addView(title);
        EditText query=new EditText(a);query.setSingleLine();query.setTextSize(16);query.setTypeface(ui.font(false,400));query.setTextColor(ui.ink);query.setHintTextColor(ui.muted);query.setHint("搜索模型或服务商");query.setContentDescription("搜索接入模型");query.setMinHeight(ui.dp(48));query.setPadding(0,ui.dp(8),0,ui.dp(8));query.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.line));root.addView(query,new LinearLayout.LayoutParams(-1,-2));
        TextView count=ui.text("",13,ui.muted,false);count.setPadding(0,ui.dp(10),0,ui.dp(10));root.addView(count);
        ListView list=new ListView(a);list.setDivider(new ColorDrawable(ui.line));list.setDividerHeight(ui.dp(1));list.setCacheColorHint(Color.TRANSPARENT);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        class Choices extends BaseAdapter {
            java.util.List<CatalogSelection.Choice> rows=Collections.emptyList();
            public int getCount(){return rows.size();}public Object getItem(int i){return rows.get(i);}public long getItemId(int i){return i;}
            public View getView(int i,View recycled,ViewGroup parent){CatalogSelection.Choice choice=rows.get(i);LinearLayout row=ui.column();row.setPadding(0,ui.dp(13),0,ui.dp(13));
                TextView name=ui.text(choice.model.name,16,ui.ink,true);row.addView(name);ui.space(row,5);row.addView(ui.text(choice.provider.name+(favorites.contains(choice.model.reference)?" · 已收藏":""),12,ui.muted,false));ui.space(row,4);row.addView(ui.text(choice.model.reference,12,ui.muted,false));row.setContentDescription("接入模型 "+choice.model.reference);row.setBackground(ui.ripple(Color.TRANSPARENT,0));row.setOnClickListener(v->{((InputMethodManager)a.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);d.dismiss();selected.accept(choice.model.reference);});return row;}
            void refresh(){rows=CatalogSelection.choices(catalog,query.getText().toString(),favorites);count.setText(rows.isEmpty()?"没有可接入的匹配模型\n清除或修改搜索条件":rows.size()+" 个可用模型 · 收藏优先");notifyDataSetChanged();}
        }
        Choices adapter=new Choices();list.setAdapter(adapter);query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){adapter.refresh();}public void afterTextChanged(Editable e){}});adapter.refresh();
        root.setFocusableInTouchMode(true);root.requestFocus();d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);w.setDimAmount(.3f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);w.setWindowAnimations(UiMotion.enabled()?R.style.MagpieSheetAnimation:0);}
        d.show();if(w!=null)w.setLayout(-1,(int)(a.getResources().getDisplayMetrics().heightPixels*.88f));return d;
    }
}
