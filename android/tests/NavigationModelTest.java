package ai.magpie.android;
import java.util.*;
public final class NavigationModelTest {
 private static void same(String e,String a,String why){if(!e.equals(a))throw new AssertionError(why+": expected "+e+", got "+a);}
 public static void main(String[]args){
  if(!Arrays.equals(NavigationModel.ROOTS,new String[]{"connect","home","plugins","usage"}))throw new AssertionError("Four task roots missing");
  for(String page:new String[]{"home","groups","providers","routing"})same("home",NavigationModel.section(page),"Model object context");
  for(String page:new String[]{"plugins","library","agents","sessions","plugin-example"})same("plugins",NavigationModel.section(page),"Plugin-owned details");
  for(String page:new String[]{"connect","gateway","settings","masking","tools"})same("connect",NavigationModel.section(page),"Global setup has a stable return");
  same("usage",NavigationModel.parent("usage"),"Ledger parent");same("home",NavigationModel.parent("groups"),"Groups within models");same("plugins",NavigationModel.parent("plugins"),"Native plugin root");
  same("模型分组",NavigationModel.title("groups"),"Groups title");same("请求路由",NavigationModel.title("routing"),"Routing distinct");
  System.out.println("PASS four task roots and canonical detail ownership");
 }
}
