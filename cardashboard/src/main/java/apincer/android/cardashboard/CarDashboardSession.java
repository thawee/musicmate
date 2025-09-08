package apincer.android.cardashboard;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.AppManager;
import androidx.car.app.Session;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.notification.CarAppExtender;
import androidx.core.graphics.drawable.IconCompat;

import java.util.ArrayList;
import java.util.List;

import apincer.android.cardashboard.CustomDashboardView;

/**
 * The Session manages the app's state and screens on the car display.
 * It handles the creation of the initial screen and manages the lifecycle of the custom view.
 *
 * NOTE: The use of CarAppExtender to display a custom view is an advanced feature and your app
 * must be approved by Google for this functionality to work on a real car.
 */
public class CarDashboardSession extends Session {

    private static final String TAG = "CarDashboardSession";
    private AppManager appManager;
    private CustomDashboardView customView;

    public CarDashboardSession() {
        super();
    }

    /**
     * Requests the first {@link Screen} for the application.
     * This method is called by the car host when the app is launched.
     *
     * @param intent the intent that was used to start this app.
     */
    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        Log.d(TAG, "onCreateScreen: Creating the initial screen.");

        // Get the AppManager to control the custom view extender.
        appManager = getCarContext().getCarService(AppManager.class);

        // Create the custom view. This view will be displayed on the car screen.
        customView = new CustomDashboardView(getCarContext());

        // We must still return a standard Screen. This one serves as a placeholder
        // because the custom view is the primary display.
        return new MyDashboardScreen(getCarContext());
    }

    private static class MyDashboardScreen extends Screen {

        public MyDashboardScreen(@NonNull CarContext carContext) {
            super(carContext);
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            // Create the list of data points to display
            List<Row> dataRows = new ArrayList<>();
            dataRows.add(
                    new Row.Builder()
                            .setTitle("HP")
                            .addText("120.5 hp")
                            .setImage(
                                    new CarIcon.Builder(
                                            IconCompat.createWithResource(getCarContext(), R.drawable.hp_icon)
                                    ).build()
                            )
                            .build()
            );
            dataRows.add(
                    new Row.Builder()
                            .setTitle("Coolant")
                            .addText("92.0 °C")
                            .setImage(
                                    new CarIcon.Builder(
                                            IconCompat.createWithResource(getCarContext(), R.drawable.coolant_icon)
                                    ).build()
                            )
                            .build()
            );
            dataRows.add(
                    new Row.Builder()
                            .setTitle("Oil Pressure")
                            .addText("65 PSI")
                            .setImage(
                                    new CarIcon.Builder(
                                            IconCompat.createWithResource(getCarContext(), R.drawable.oil_icon)
                                    ).build()
                            )
                            .build()
            );

            // Create an ItemList from the rows
            ItemList itemList = new ItemList.Builder()
                    .addItem(dataRows.get(0))
                    .addItem(dataRows.get(1))
                    .addItem(dataRows.get(2))
                    .build();

            Header header = new Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .setTitle("Car Gauges")
                    .build();

            return new ListTemplate.Builder()
                    .setHeader(header)
                    .setSingleList(itemList)
                    .build();
        }
    }
}
