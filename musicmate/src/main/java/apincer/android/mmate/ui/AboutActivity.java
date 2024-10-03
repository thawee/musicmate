package apincer.android.mmate.ui;

import static apincer.android.mmate.Constants.TITLE_DSD;
import static apincer.android.mmate.Constants.TITLE_HQ;
import static apincer.android.mmate.Constants.TITLE_MQA;

import android.annotation.SuppressLint;
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

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.vanniktech.textbuilder.TextBuilder;

import java.text.DecimalFormat;
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
        if (item.getItemId() == android.R.id.home) {//do whatever
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class AboutFragment extends Fragment {
        protected Context context;

        @Override
        public void onAttach(@NonNull Context context) {
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
            catch (PackageManager.NameNotFoundException ignore) {}

            TextView encodingHeader = v.findViewById(R.id.encoding_header);
            TextView encodingDetail = v.findViewById(R.id.encoding_details);
            v.findViewById(R.id.encoding_btn_hq).setOnClickListener(view -> updateEncodings(TITLE_HQ, encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_lossless).setOnClickListener(view -> updateEncodings("Hi-FI", encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_hires).setOnClickListener(view -> updateEncodings("Hi-Res", encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_dsd).setOnClickListener(view -> updateEncodings(TITLE_DSD, encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_mqa).setOnClickListener(view -> updateEncodings(TITLE_MQA, encodingHeader, encodingDetail));
            updateEncodings(TITLE_HQ, encodingHeader, encodingDetail);

            TextView qualityDetail = v.findViewById(R.id.quality_details);
            String content = ApplicationUtils.getAssetsText(getActivity(),"music_quality_info.txt");
            new TextBuilder(getContext())
                    .addFormableText(content)
                    .format("Dynamic Range")
                        .bold()
                        .textColor(Color.BLACK)
                    .done()
                    .format("DRMeter")
                        .bold()
                        .textColor(Color.BLACK)
                    .done()
                    .into(qualityDetail);

            MusicMateExecutors.ui(() -> {
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
                getActivity().runOnUiThread(() -> {
                    // storage
                    LinearLayout panel = v.findViewById(R.id.storage_bar);
                    UIUtils.buildStoragesUsed(requireActivity().getApplication(), panel, actualSize, estimatedSize);

                    // file type piechart
                    //setupEncodingChart(v, encList, "Music File Format");
                   // setupEncodingChart(v, grpList, "");
                    setupEncodingChart(v, encList, "");

                    // setup digital music details
                   // String content = ApplicationUtils.getAssetsText(getActivity(),"digital_music.html");
                   // Editor renderer = v.findViewById(R.id.editor);
                  //  renderer.render(content);
                });
            });

            return v;
        }

        private void updateEncodings(String btn, TextView encodingHeader, TextView encodingDetail) {
            encodingHeader.setText(getEncodingHeader(btn));
            TextBuilder detailBuilder = getEncodingDetailBuilder(btn);
            if(detailBuilder!=null) {
                detailBuilder.into(encodingDetail);
            }else {
                encodingDetail.setText("");
            }
        }

        private String getEncodingHeader(String btn) {
            if(TITLE_HQ.equalsIgnoreCase(btn)) return getString(R.string.encoding_hq_header);
            if("Hi-FI".equalsIgnoreCase(btn)) return getString(R.string.encoding_lossless_header);
            if("Hi-Res".equalsIgnoreCase(btn)) return getString(R.string.encoding_hires_header);
            if(TITLE_DSD.equalsIgnoreCase(btn)) return getString(R.string.encoding_dsd_header);
            if(TITLE_MQA.equalsIgnoreCase(btn)) return getString(R.string.encoding_mqa_header);
            return btn;
        }

        private TextBuilder getEncodingDetailBuilder(String btn) {
            if(TITLE_HQ.equalsIgnoreCase(btn)) {
                return new TextBuilder(getContext())
                        .addColoredTextRes(R.string.encoding_hq_desc, R.color.encoding_desc)
                        .addNewLine()
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_format, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_hq_format, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_resolution, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_hq_resolution, R.color.encoding_detail);
                       // .addNewLine()
                       // .addColoredTextRes(R.string.label_file_recommended, R.color.black)
                       // .addWhiteSpace()
                      //  .addColoredTextRes(R.string.encoding_hq_recommended, R.color.encoding_desc);
            }
            if("Hi-FI".equalsIgnoreCase(btn)) {
                return new TextBuilder(getContext())
                        .addColoredTextRes(R.string.encoding_lossless_desc, R.color.encoding_desc)
                        .addNewLine()
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_format, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_lossless_format, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_resolution, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_lossless_resolution, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_recommended, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_lossless_recommended, R.color.encoding_detail);
            }
            if("Hi-Res".equalsIgnoreCase(btn)) {
                return new TextBuilder(getContext())
                        .addColoredTextRes(R.string.encoding_hires_desc, R.color.encoding_desc)
                        .addNewLine()
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_format, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_hires_format, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_resolution, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_hires_resolution, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_recommended, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_hires_recommended, R.color.encoding_detail);
            }
            if(TITLE_DSD.equalsIgnoreCase(btn)) {
                return new TextBuilder(getContext())
                        .addColoredTextRes(R.string.encoding_dsd_desc, R.color.encoding_desc)
                        .addNewLine()
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_format, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_dsd_format, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_resolution, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_dsd_resolution, R.color.encoding_detail)
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_recommended, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_dsd_recommended, R.color.encoding_detail);
            }
            if(TITLE_MQA.equalsIgnoreCase(btn)) {
                return new TextBuilder(getContext())
                        .addColoredTextRes(R.string.encoding_mqa_desc, R.color.encoding_desc)
                        .addNewLine()
                        .addNewLine()
                        .addColoredTextRes(R.string.label_file_format, R.color.encoding_label)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_mqa_format, R.color.encoding_detail)
                        .addNewLine()
                       // .addColoredTextRes(R.string.label_file_resolution, R.color.encoding_label)
                       // .addWhiteSpace()
                       // .addColoredTextRes(R.string.encoding_mqa_resolution, R.color.encoding_detail)
                       // .addNewLine()
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_mqa_green_header, R.color.mqa_master)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_mqa_green_detail, R.color.mqa_master)
                        .addNewLine()
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_mqa_blue_header, R.color.mqa_studio)
                        .addWhiteSpace()
                        .addColoredTextRes(R.string.encoding_mqa_blue_detail, R.color.mqa_studio);
            }

            return null;
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
            setDataForEncodings(chart, encList, title);
        }

        private void setDataForGroupings(PieChart chart, Map<String, Integer> encList, String title) {
            ArrayList<PieEntry> entries = new ArrayList<>();

            // NOTE: The order of the entries when being added to the entries array determines their position around the center of
            // the chart.
            // add a lot of colors
            ArrayList<Integer> colors = new ArrayList<>();
            Map<String, Integer> mappedColors = new HashMap<>();

            mappedColors.put("Lounge", ContextCompat.getColor(getContext(), R.color.material_color_green_400));// ColorTemplate.rgb("#4b7a9b")); //""#f48558"));
            mappedColors.put("Acoustic", ContextCompat.getColor(getContext(), R.color.material_color_lime_400));//ColorTemplate.rgb("#a8aa41"));
            mappedColors.put("Live", ContextCompat.getColor(getContext(), R.color.material_color_yellow_100)); //ColorTemplate.rgb("#a8aa41"));
            mappedColors.put("English", ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_600)); //ColorTemplate.rgb("#488f31"));
            mappedColors.put(Constants.UNKNOWN_GROUP, ContextCompat.getColor(getContext(), R.color.red_light)); //ColorTemplate.rgb("#de425b"));
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

        private void setDataForEncodings(PieChart chart, Map<String, Integer> encList, String title) {
            ArrayList<PieEntry> entries = new ArrayList<>();

            // NOTE: The order of the entries when being added to the entries array determines their position around the center of
            // the chart.
            // add a lot of colors
            ArrayList<Integer> colors = new ArrayList<>();
            Map<String, Integer> mappedColors = new HashMap<>();

            mappedColors.put(Constants.TITLE_DSD, ContextCompat.getColor(getContext(), R.color.resolution_dsd));
            mappedColors.put(Constants.TITLE_MQA, ContextCompat.getColor(getContext(), R.color.resolution_mqa_studio));
            mappedColors.put(Constants.TITLE_HIRES, ContextCompat.getColor(getContext(), R.color.resolution_hires));
            mappedColors.put(Constants.TITLE_HIFI_LOSSLESS, ContextCompat.getColor(getContext(), R.color.resolution_lossless));
            mappedColors.put(Constants.TITLE_HIGH_QUALITY, ContextCompat.getColor(getContext(), R.color.resolution_lossy));

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

            dataSet.setValueFormatter(new ValueFormatter() {
                private final DecimalFormat mFormat = new DecimalFormat("#,###");
                @SuppressLint("DefaultLocale")
                @Override
                public String getFormattedValue(float value) {
                   // return String.format("%.1f", value); // Format to one decimal place
                    return mFormat.format(value);
                }
            });

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
         //   data.setValueFormatter(new PercentFormatter());
            data.setValueTextSize(10f);
            data.setValueTextColor(Color.BLACK);
            chart.setData(data);

            // undo all highlights
            chart.highlightValues(null);

            chart.invalidate();
        }
    }
}