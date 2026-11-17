package ai.magpie.android;
import android.content.Context;
import android.widget.ImageView;
/** Pinned official Phosphor SVGs, with original duotone opacity, converted to native vectors. */
final class LineIcon extends ImageView {
 LineIcon(Context context,String name,int color){super(context);int id;
 switch(name){
  case "search":id=R.drawable.icon_search;break;
  case "add":id=R.drawable.icon_add;break;
  case "home":id=R.drawable.icon_home;break;
  case "providers":id=R.drawable.icon_providers;break;
  case "routing":id=R.drawable.icon_routing;break;
  case "usage":id=R.drawable.icon_usage;break;
  case "library":id=R.drawable.icon_library;break;
  case "server":id=R.drawable.icon_server;break;
  case "settings":id=R.drawable.icon_settings;break;
  case "more":id=R.drawable.icon_more;break;
  case "chevron":id=R.drawable.icon_chevron;break;
  case "down":id=R.drawable.icon_down;break;
  case "back":id=R.drawable.icon_back;break;
  case "power":id=R.drawable.icon_power;break;
  case "plugins":
  case "plug":id=R.drawable.icon_plug;break;
  case "refresh":id=R.drawable.icon_refresh;break;
  case "copy":id=R.drawable.icon_copy;break;
  case "arrow":id=R.drawable.icon_arrow;break;
  case "bird":id=R.drawable.icon_bird;break;
  case "cloud":id=R.drawable.icon_cloud;break;
  case "sparkle":id=R.drawable.icon_sparkle;break;
  case "network":id=R.drawable.icon_network;break;
  case "check":id=R.drawable.icon_check;break;
  case "clock":id=R.drawable.icon_clock;break;
  case "brand":id=R.drawable.brand_mark;break;
  default:id=R.drawable.icon_arrow;
 }setImageResource(id);setScaleType(ScaleType.FIT_CENTER);setColorFilter(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
 }
 void tint(int color){setColorFilter(color);}
}
