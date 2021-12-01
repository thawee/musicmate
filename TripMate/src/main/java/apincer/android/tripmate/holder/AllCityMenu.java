package apincer.android.tripmate.holder;

import java.util.Vector;

import apincer.android.tripmate.model.CityMenuList;


public class AllCityMenu {
	public static Vector<CityMenuList> allCityMenuList = new Vector<CityMenuList>();

	public static Vector<CityMenuList> getAllCityMenu() {
		return AllCityMenu.allCityMenuList;
	}

	public static void setAllCityMenu(Vector<CityMenuList> allCityMenuList) {
		AllCityMenu.allCityMenuList = allCityMenuList;
	}

	public static CityMenuList getCityMenuList(int pos) {
		return AllCityMenu.allCityMenuList.elementAt(pos);
	}

	public static void setCityMenuList(CityMenuList CityMenuList) {
		AllCityMenu.allCityMenuList.addElement(CityMenuList);
	}

	public static void removeAll() {
		AllCityMenu.allCityMenuList.removeAllElements();
	}

}
