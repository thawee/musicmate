package apincer.android.cardashboard;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.model.Header;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import org.jspecify.annotations.NonNull;

public class CarDashboardScreen extends Screen implements SurfaceCallback {
    protected CarDashboardScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @Override
    public @NonNull Template onGetTemplate() {
        // In a real app, you would get these values from a background service
        // that is connected to the OBD-II adapter.
        String coolantTemp = "195°F";
        String oilTemp = "210°F";
        String transmissionTemp = "180°F";
        String batteryVoltage = "14.2V";
// Create a Row for each piece of data
        Row coolantTempRow = new Row.Builder()
                .setTitle("Coolant Temp")
                .addText(coolantTemp)
                .build();

        Row oilTempRow = new Row.Builder()
                .setTitle("Oil Temp")
                .addText(oilTemp)
                .build();

        Row transmissionTempRow = new Row.Builder()
                .setTitle("Transmission Temp")
                .addText(transmissionTemp)
                .build();

        Row voltageRow = new Row.Builder()
                .setTitle("Battery Voltage")
                .addText(batteryVoltage)
                .build();

        // Build the Pane, which contains all the data rows
        Pane pane = new Pane.Builder()
                .addRow(coolantTempRow)
                .addRow(oilTempRow)
                .addRow(transmissionTempRow)
                .addRow(voltageRow)
                .build();

        Header header = new Header.Builder()
                .setTitle("Engine Info")
                .build();

        // Wrap the Pane in a PaneTemplate to create the final screen layout
        return new PaneTemplate.Builder(pane)
                //.setHeaderAction(Action.BACK)
                .setHeader(header)
                .build();
    }
}
