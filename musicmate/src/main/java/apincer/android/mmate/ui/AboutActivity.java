package apincer.android.mmate.ui;

import static apincer.android.mmate.Constants.TITLE_DSD_SHORT;
import static apincer.android.mmate.Constants.TITLE_HIFI_LOSSLESS_SHORT;
import static apincer.android.mmate.Constants.TITLE_HIGH_QUALITY_SHORT;
import static apincer.android.mmate.Constants.TITLE_HIRES_SHORT;
import static apincer.android.mmate.Constants.TITLE_MQA_SHORT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.vanniktech.textbuilder.TextBuilder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.worker.MusicMateExecutors;
import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;

public class AboutActivity extends AppCompatActivity {

    public static void showAbout(Activity activity) {
        Intent myIntent = new Intent(activity, AboutActivity.class);
        activity.startActivity(myIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        // set status bar color to black
        Window window = getWindow();
        //window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        //window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        // If the background is dark, use light icons
        insetsController.setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_fragement);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
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
            version.setText(ApplicationUtils.getVersionNumber(getContext()));

            TextView encodingHeader = v.findViewById(R.id.encoding_header);
            TextView encodingDetail = v.findViewById(R.id.encoding_details);

           // TextView groupingsHeader = v.findViewById(R.id.groupings_header);
           // TextView groupingsDetail = v.findViewById(R.id.groupings_details);

            v.findViewById(R.id.encoding_btn_lc).setOnClickListener(view -> updateEncodings(TITLE_HIGH_QUALITY_SHORT, encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_sq).setOnClickListener(view -> updateEncodings(TITLE_HIFI_LOSSLESS_SHORT, encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_hr).setOnClickListener(view -> updateEncodings(TITLE_HIRES_SHORT, encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_dsd).setOnClickListener(view -> updateEncodings(TITLE_DSD_SHORT, encodingHeader, encodingDetail));
            v.findViewById(R.id.encoding_btn_mqa).setOnClickListener(view -> updateEncodings(TITLE_MQA_SHORT, encodingHeader, encodingDetail));
            updateEncodings(TITLE_HIGH_QUALITY_SHORT, encodingHeader, encodingDetail);

            // groupings description
            /*
            LinearLayout groupingBtnPanel = v.findViewById(R.id.groupingBtnPanel);
            List<String> groupList = TagRepository.getDefaultGroupingList(getContext());
            for(String name: groupList) {
                TextView btn = new TextView(getContext());
                btn.setText(name);
                btn.setTextColor(Color.BLACK);
                btn.setTypeface(null, Typeface.BOLD);
                btn.setPadding(12, 6, 12, 6);
                // Set margin
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(8, 6, 8, 6);
                btn.setLayoutParams(params);
                if(Constants.GROUPING_CONTEMPORARY.equals(name)) {
                    btn.setText(StringUtils.truncate(name, 10));
                    btn.setBackgroundResource(R.drawable.shape_background_contemporary);
                }else if(Constants.GROUPING_OLDEIS.equals(name)) {
                    btn.setBackgroundResource(R.drawable.shape_background_oldies);
                }else if(Constants.GROUPING_LOUNGE.equals(name)) {
                    btn.setBackgroundResource(R.drawable.shape_background_lounge);
                }else if(Constants.GROUPING_CLASSICAL.equals(name)) {
                    btn.setBackgroundResource(R.drawable.shape_background_classical);
                    btn.setText(StringUtils.truncate(name, 8));
                }else if(Constants.GROUPING_TRADITIONAL.equals(name)) {
                    btn.setText(StringUtils.truncate(name, 7));
                    btn.setBackgroundResource(R.drawable.shape_background_traditional);
                }else {
                    btn.setBackgroundResource(R.drawable.shape_background_unkown);
                }
                btn.setOnClickListener(view -> {
                    if(Constants.GROUPING_CONTEMPORARY.equals(name)) {
                        groupingsHeader.setText(Constants.GROUPING_CONTEMPORARY);
                       // groupingsDetail.setText(R.string.groupings_contemporary_content);
                        String details = getString(R.string.groupings_contemporary_content);
                        renderMarkdown(details, groupingsDetail);
                    }else if(Constants.GROUPING_LOUNGE.equals(name)) {
                        groupingsHeader.setText(Constants.GROUPING_LOUNGE);
                       // groupingsDetail.setText(R.string.groupings_lounge_content);
                        String details = getString(R.string.groupings_lounge_content);
                        renderMarkdown(details, groupingsDetail);
                    }else if(Constants.GROUPING_CLASSICAL.equals(name)) {
                        groupingsHeader.setText(Constants.GROUPING_CLASSICAL);
                       // groupingsDetail.setText(R.string.groupings_classical_content);
                        String details = getString(R.string.groupings_classical_content);
                        renderMarkdown(details, groupingsDetail);
                    }else if(Constants.GROUPING_TRADITIONAL.equals(name)) {
                        groupingsHeader.setText(Constants.GROUPING_TRADITIONAL);
                       // groupingsDetail.setText(R.string.groupings_traditional_content);
                        String details = getString(R.string.groupings_traditional_content);
                        renderMarkdown(details, groupingsDetail);
                    }
                });
                groupingBtnPanel.addView(btn);
            }

            groupingsHeader.setText(Constants.GROUPING_CLASSICAL);
            groupingsDetail.setText(R.string.groupings_classical_content);

             */

            TextView qualityDetail = v.findViewById(R.id.quality_details);
            String content = ApplicationUtils.getAssetsText(getActivity(),"music_quality_info.md");

            renderMarkdown(content, qualityDetail);

            MusicMateExecutors.executeUI(() -> {
                List<MusicTag> tags = TagRepository.getAllMusics();
                Map<String, Integer> encList = new HashMap<>();
                Map<String, Integer> grpList = new HashMap<>();
                for(MusicTag tag: tags) {

                    String enc = MusicTagUtils.getEncodingTypeShort(tag);
                    if(encList.containsKey(enc)) {
                        Integer cnt = encList.get(enc);
                        encList.put(enc, cnt + 1);
                    }else {
                        encList.put(enc, 1);
                    }

                    // grouping
                    String grp = tag.getGrouping();
                    if(StringUtils.isEmpty(grp)) grp = Constants.UNKNOWN;
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
                    UIUtils.buildStoragesStatus(requireActivity().getApplication(), panel);
                   // UIUtils.buildStoragesUsed(requireActivity().getApplication(), panel, actualSize, estimatedSize);

                    // file type piechart
                    setupResolutionChart(v, encList, "");
                    //setupGroupingChart(v, grpList, "");

                    // setup digital music details
                });
            });

            return v;
        }

        private void renderMarkdown(String content, TextView qualityDetail) {
            Markwon markwon = Markwon.builder(context)
                    .usePlugin(HtmlPlugin.create()).build();
            markwon.setMarkdown(qualityDetail, content);
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
            if(TITLE_HIGH_QUALITY_SHORT.equalsIgnoreCase(btn)) return getString(R.string.encoding_hq_header);
            if(TITLE_HIFI_LOSSLESS_SHORT.equalsIgnoreCase(btn)) return getString(R.string.encoding_lossless_header);
            if(TITLE_HIRES_SHORT.equalsIgnoreCase(btn)) return getString(R.string.encoding_hires_header);
            if(TITLE_DSD_SHORT.equalsIgnoreCase(btn)) return getString(R.string.encoding_dsd_header);
            if(TITLE_MQA_SHORT.equalsIgnoreCase(btn)) return getString(R.string.encoding_mqa_header);
            return btn;
        }

        private TextBuilder getEncodingDetailBuilder(String btn) {
            if(TITLE_HIGH_QUALITY_SHORT.equalsIgnoreCase(btn)) {
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
            if(TITLE_HIFI_LOSSLESS_SHORT.equalsIgnoreCase(btn)) {
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
            if(TITLE_HIRES_SHORT.equalsIgnoreCase(btn)) {
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
            if(TITLE_DSD_SHORT.equalsIgnoreCase(btn)) {
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
            if(TITLE_MQA_SHORT.equalsIgnoreCase(btn)) {
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

        private void setupResolutionChart(View v, Map<String, Integer> encList, String title) {
            PieChart chart = v.findViewById(R.id.chartResolutions);
            chart.setUsePercentValues(false);
            //chart.setUsePercentValues(true);
            chart.getDescription().setEnabled(false);
            chart.setExtraOffsets(0, 4, 0, 0);

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
            l.setTextColor(Color.WHITE);
            l.setWordWrapEnabled(true);

            // entry label styling
            chart.setEntryLabelColor(Color.WHITE);
            chart.setDrawEntryLabels(false);
            //  chart.setEntryLabelTypeface(tfRegular);
            chart.setEntryLabelTextSize(10f);
            setDataForEncodings(chart, encList, title);
        }

        /*
        private void setupGroupingChart(View v, Map<String, Integer> encList, String title) {
            PieChart chart = v.findViewById(R.id.chartGroupings);
            chart.setUsePercentValues(false);
            //chart.setUsePercentValues(true);
            chart.getDescription().setEnabled(false);
            chart.setExtraOffsets(0, 4, 0, 0);

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
            l.setTextColor(Color.WHITE);
            l.setWordWrapEnabled(true);

            // entry label styling
            chart.setEntryLabelColor(Color.WHITE);
            chart.setDrawEntryLabels(false);
            //  chart.setEntryLabelTypeface(tfRegular);
            chart.setEntryLabelTextSize(10f);
            setDataForGroupings(chart, encList, title);
        } */

        private void setDataForGroupings(PieChart chart, Map<String, Integer> list, String title) {
            ArrayList<PieEntry> entries = new ArrayList<>();

            // NOTE: The order of the entries when being added to the entries array determines their position around the center of
            // the chart.
            // add a lot of colors
            ArrayList<Integer> colors = new ArrayList<>();
            Map<String, Integer> mappedColors = new HashMap<>();

            mappedColors.put(Constants.GROUPING_TRADITIONAL, ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_400));// ColorTemplate.rgb("#4b7a9b")); //""#f48558"));
            mappedColors.put(Constants.GROUPING_LOUNGE, ContextCompat.getColor(getContext(), R.color.material_color_green_400));// ColorTemplate.rgb("#4b7a9b")); //""#f48558"));
            mappedColors.put(Constants.UNKNOWN, ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_800)); //ColorTemplate.rgb("#488f31"));
            mappedColors.put(Constants.GROUPING_CLASSICAL, ContextCompat.getColor(getContext(), R.color.material_color_lime_400)); //ColorTemplate.rgb("#488f31"));
            mappedColors.put(Constants.GROUPING_OLDEIS, ContextCompat.getColor(getContext(), R.color.material_color_teal_900)); //ColorTemplate.rgb("#dcb85a"));
            mappedColors.put(Constants.GROUPING_CONTEMPORARY, ContextCompat.getColor(getContext(), R.color.material_color_green_800)); //ColorTemplate.rgb("#dcb85a"));
           // mappedColors.put("World", ContextCompat.getColor(getContext(), R.color.material_color_blue_grey_900)); //ColorTemplate.rgb("#f48558"));
            for(String enc: list.keySet()) {
                entries.add(new PieEntry(list.get(enc), enc));
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
           // data.setValueFormatter(new PercentFormatter());
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

            /*
            mappedColors.put(TITLE_MQA_SHORT, ContextCompat.getColor(getContext(), R.color.resolution_mqa_studio));
            mappedColors.put(TITLE_DSD_SHORT, ContextCompat.getColor(getContext(), R.color.resolution_dsd));
            mappedColors.put(TITLE_HIRES_SHORT, ContextCompat.getColor(getContext(), R.color.resolution_hires));
            mappedColors.put(TITLE_HIFI_LOSSLESS_SHORT, ContextCompat.getColor(getContext(), R.color.resolution_lossless));
            mappedColors.put(TITLE_HIGH_QUALITY_SHORT, ContextCompat.getColor(getContext(), R.color.resolution_lossy));
            */
            mappedColors.put(TITLE_MQA_SHORT, ContextCompat.getColor(getContext(), R.color.quality_mqa_background));
            mappedColors.put(TITLE_DSD_SHORT, ContextCompat.getColor(getContext(), R.color.quality_dsd_background));
            mappedColors.put(TITLE_HIRES_SHORT, ContextCompat.getColor(getContext(), R.color.quality_hr_background));
            mappedColors.put(TITLE_HIFI_LOSSLESS_SHORT, ContextCompat.getColor(getContext(), R.color.quality_sq_background));
            mappedColors.put(TITLE_HIGH_QUALITY_SHORT, ContextCompat.getColor(getContext(), R.color.quality_lc_background));

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