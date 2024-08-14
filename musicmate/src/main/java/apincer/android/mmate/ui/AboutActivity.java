package apincer.android.mmate.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.irshulx.Editor;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Settings;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.worker.MusicMateExecutors;

public class AboutActivity extends AppCompatActivity {
    public static void showAbout(Activity activity) {
        Intent myIntent = new Intent(activity, AboutActivity.class);
        activity.startActivity(myIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Settings.isOnNightModeOnly(getApplicationContext())) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); //must place before super.onCreate();
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fragement);
        getSupportActionBar().setTitle(R.string.app_name);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_content, new AboutFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //do whatever
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class AboutFragment extends Fragment {
        protected Context context;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            this.context = context;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_about, container, false);

            TextView version = v.findViewById(R.id.app_version);
            //version.setText("");
            try {
                PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                version.setText(packageInfo.versionName);

            }
            catch (PackageManager.NameNotFoundException e) {

            }
            MusicMateExecutors.db(new Runnable() {
                @Override
                public void run() {
                    List<MusicTag> tags = TagRepository.getAllMusics();
                    Map<String, Integer> encList = new HashMap<>();
                    Map<String, Integer> grpList = new HashMap<>();
                    Map<String, Long> estimatedSize = new HashMap<>();
                    Map<String, Long> actualSize = new HashMap<>();
                    FileRepository repos = FileRepository.newInstance(getActivity().getApplicationContext());
                    for(MusicTag tag: tags) {
                        String sid = repos.getStorageIdFor(tag);
                        String asid = tag.getStorageId();
                        if(estimatedSize.containsKey(sid)) {
                            Long s = estimatedSize.get(sid);
                            estimatedSize.put(sid, s+tag.getFileSize());
                        }else {
                            estimatedSize.put(sid, tag.getFileSize());
                        }
                        if(actualSize.containsKey(asid)) {
                            Long s = actualSize.get(asid);
                            actualSize.put(asid, s+tag.getFileSize());
                        }else {
                            actualSize.put(asid, tag.getFileSize());
                        }

                        String enc = MusicTagUtils.getEncodingType(tag);
                        if(encList.containsKey(enc)) {
                            Integer cnt = encList.get(enc);
                            encList.put(enc, cnt + 1);
                        }else {
                            encList.put(enc, 1);
                        }

                        // grouping
                        String grp = tag.getGrouping();
                        if(StringUtils.isEmpty(grp)) grp = Constants.UNKNOWN_GROUP;
                        if(grpList.containsKey(grp)) {
                            Integer cnt = grpList.get(grp);
                            grpList.put(grp, cnt + 1);
                        }else {
                            grpList.put(grp, 1);
                        }
                    }
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // storage
                            LinearLayout panel = v.findViewById(R.id.storage_bar);
                            UIUtils.buildStoragesUsed(requireActivity().getApplication(), panel, actualSize, estimatedSize);

                            // file type piechart
                            //setupEncodingChart(v, encList, "Music File Format");
                            setupEncodingChart(v, grpList, "");

                            // setup digital music details
                            String content = ApplicationUtils.getAssetsText(getActivity(),"digital_music.html");
                            Editor renderer = v.findViewById(R.id.editor);
                            renderer.render(content);
                        }
                    });
                }
            });

            return v;
        }

        private void setupEncodingChart(View v, Map<String, Integer> encList, String title) {
            PieChart chart = v.findViewById(R.id.chart1);
            chart.setUsePercentValues(false);
            //chart.setUsePercentValues(true);
            chart.getDescription().setEnabled(false);
            chart.setExtraOffsets(0, 4, 0, 0);
            //chart.setExtraOffsets(5, 10, 5, 10);
            //chart.setExtraOffsets(20f, 130f, 20f, 20f);

            chart.setDragDecelerationFrictionCoef(0.95f);

            chart.setDrawRoundedSlices(true);
            chart.setDrawHoleEnabled(true);
            chart.setHoleColor(Color.TRANSPARENT);

            //chart.setHoleRadius(42f);
            chart.setHoleRadius(32f);
            chart.setTransparentCircleRadius(56f);

            chart.setDrawCenterText(false);
            chart.setCenterText("Songs");
            chart.setCenterTextColor(Color.WHITE);

            chart.setRotationAngle(0);
            // disable rotation of the chart by touch
            chart.setRotationEnabled(true);
            chart.setHighlightPerTapEnabled(false);

            Legend l = chart.getLegend();
            //l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
            //l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
           // l.setOrientation(Legend.LegendOrientation.VERTICAL);
           // l.setDrawInside(false);
           // l.setXEntrySpace(5f);
           // l.setYEntrySpace(0f);
           // l.setYOffset(2f);
           // l.setStackSpace(12);
            l.setTextColor(Color.WHITE);
            l.setWordWrapEnabled(true);

            // entry label styling
            chart.setEntryLabelColor(Color.WHITE);
            chart.setDrawEntryLabels(false);
            //  chart.setEntryLabelTypeface(tfRegular);
            chart.setEntryLabelTextSize(10f);
            setData(chart, encList, title);
        }

        private void setData(PieChart chart, Map<String, Integer> encList, String title) {
            ArrayList<PieEntry> entries = new ArrayList<>();

            // NOTE: The order of the entries when being added to the entries array determines their position around the center of
            // the chart.
            // add a lot of colors
            ArrayList<Integer> colors = new ArrayList<>();
            Map<String, Integer> mappedColors = new HashMap<>();

            /*mappedColors.put("DSD", ColorTemplate.rgb("#003f5c"));
            mappedColors.put("MQA", ColorTemplate.rgb("#58508d"));
            mappedColors.put("Hi-Res Lossless", ColorTemplate.rgb("#ff1600"));
            mappedColors.put("Lossless", ColorTemplate.rgb("#bc5090"));
            mappedColors.put("High Quality", ColorTemplate.rgb("#ff6361")); */

          //  mappedColors.put("DSD", ColorTemplate.rgb("#4b7a9b")); //""#f48558"));
            mappedColors.put("Lounge", ContextCompat.getColor(getContext(), R.color.material_color_green_400));// ColorTemplate.rgb("#4b7a9b")); //""#f48558"));
            mappedColors.put("Acoustic", ContextCompat.getColor(getContext(), R.color.material_color_lime_400));//ColorTemplate.rgb("#a8aa41"));
          //  mappedColors.put("MQA", ColorTemplate.rgb("#a8aa41"));
            mappedColors.put("Live", ContextCompat.getColor(getContext(), R.color.material_color_yellow_100)); //ColorTemplate.rgb("#a8aa41"));
          //  mappedColors.put("Hi-Res", ColorTemplate.rgb("#488f31"));
            mappedColors.put("English", ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_600)); //ColorTemplate.rgb("#488f31"));
           // mappedColors.put("Lossless", ColorTemplate.rgb("#dcb85a"));
            mappedColors.put(Constants.UNKNOWN_GROUP, ContextCompat.getColor(getContext(), R.color.red_light)); //ColorTemplate.rgb("#de425b"));
           // mappedColors.put("High Quality", ColorTemplate.rgb("#de425b"));
            mappedColors.put("Thai", ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_200)); //ColorTemplate.rgb("#de425b"));
            mappedColors.put("Thai Acoustic", ContextCompat.getColor(getContext(), R.color.material_color_lime_800)); //ColorTemplate.rgb("#488f31"));
            mappedColors.put("Thai Country", ContextCompat.getColor(getContext(), R.color.material_color_teal_900)); //ColorTemplate.rgb("#dcb85a"));
            mappedColors.put("Thai Lounge", ContextCompat.getColor(getContext(), R.color.material_color_green_800)); //ColorTemplate.rgb("#dcb85a"));
            mappedColors.put("World", ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_900)); //ColorTemplate.rgb("#f48558"));
            for(String enc: encList.keySet()) {
                entries.add(new PieEntry(encList.get(enc), enc));
                if(mappedColors.containsKey(enc)) {
                    colors.add(mappedColors.get(enc));
                }else {
                    colors.add(ColorTemplate.COLORFUL_COLORS[0]);
                }
            }

            PieDataSet dataSet = new PieDataSet(entries, title);
            //setting size of the value
            dataSet.setValueLinePart1OffsetPercentage(0.0f);
            dataSet.setValueLinePart1Length(1f);
            dataSet.setValueLinePart2Length(0.4f);

            dataSet.setDrawIcons(false);
            dataSet.setSliceSpace(2f); //space between each slice
            dataSet.setValueLineColor(Color.WHITE);
            dataSet.setSelectionShift(2f);
            //setting position of the value
            dataSet.setYValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE); // display value outside with pointing line
            dataSet.setUsingSliceColorAsValueLineColor(true);
            dataSet.setAutomaticallyDisableSliceSpacing(true);

            dataSet.setColors(colors);

            PieData data = new PieData(dataSet);
            data.setValueFormatter(new PercentFormatter());
            data.setValueTextSize(10f);
            data.setValueTextColor(Color.BLACK);
            chart.setData(data);

            // undo all highlights
            chart.highlightValues(null);

            chart.invalidate();
        }
    }
}