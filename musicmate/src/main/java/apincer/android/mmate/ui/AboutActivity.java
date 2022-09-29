package apincer.android.mmate.ui;

/**
 * Created by Administrator on 8/26/17.
 */

import android.content.Context;
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
import androidx.fragment.app.Fragment;

import com.github.irshulx.Editor;
import com.github.irshulx.models.EditorContent;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.BuildConfig;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioTagRepository;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import ir.androidexception.datatable.DataTable;
import ir.androidexception.datatable.model.DataTableHeader;
import ir.androidexception.datatable.model.DataTableRow;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragement);
        getSupportActionBar().setTitle(R.string.app_about);
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
            version.setText(BuildConfig.VERSION_NAME);

            List< AudioTag> tags = AudioTagRepository.getInstance().getAllMusics();

            Map<String, Integer> encList = new HashMap<>();
            Map<String, Integer> songs = new HashMap<>();
            Map<String, Long> size = new HashMap<>();
            Map<String, Long> duration = new HashMap<>();
            long totalSongs = 0;
            long totalSize=0;
            long totalDuration =0;
            for(AudioTag tag: tags) {
                String enc = AudioTagUtils.getEncodingType(tag);
                if(encList.containsKey(enc)) {
                    Integer cnt = encList.get(enc);
                    encList.remove(enc);
                    encList.put(enc, new Integer((cnt+1)));
                }else {
                    encList.put(enc, new Integer(1));
                }

                String grouping = StringUtils.trim(tag.getGrouping(), " - ");
                if(songs.containsKey(grouping)) {
                    Integer cnt = songs.get(grouping);
                    songs.remove(grouping);
                    songs.put(grouping, new Integer((cnt+1)));
                }else {
                    songs.put(grouping, new Integer(1));
                }
                if(size.containsKey(grouping)) {
                    Long cnt = size.get(grouping);
                    size.remove(grouping);
                    size.put(grouping, new Long((cnt+tag.getFileSize())));
                }else {
                    size.put(grouping, new Long(tag.getFileSize()));
                }
                if(duration.containsKey(grouping)) {
                    Long cnt = duration.get(grouping);
                    duration.remove(grouping);
                    duration.put(grouping, new Long(cnt+tag.getAudioDuration()));
                }else {
                    duration.put(grouping, new Long(tag.getAudioDuration()));
                }
                totalSongs++;
                totalSize=totalSize+tag.getFileSize();
                totalDuration=totalDuration+ tag.getAudioDuration();
            }

            // storage
            LinearLayout panel = v.findViewById(R.id.storage_bar);
            UIUtils.buildStoragesUsed(getActivity().getApplication(), panel);

            // genre
           // List<String> labels = new ArrayList();
           // LineChart chart = setupLineChart(v,labels);
            // setLineChartData(chart, tags, labels);

           // CombinedChart chart = setupCombineChart(v, labels);
           // setCombineChartData(chart, tags, labels);

            // file type piechart
            setupEncodingChart(v, encList);

            // table grouping
            setupGroupingTable(v, songs, size, duration, totalSongs, totalSize, totalDuration);

            // setup digital music details
            String content = ApplicationUtils.getAssetsText(getActivity(),"digital_music.html");
            Editor renderer = v.findViewById(R.id.editor);
            //EditorContent deserialized= renderer.getContentDeserialized(content);
            renderer.render(content);
            return v;
        }

        /*
        private void setCombineChartData(CombinedChart chart, List<AudioTag> tags, List<String> labels) {
            Map<String, Integer> encMemList = new HashMap<>();
            Map<String, Integer> encSDList = new HashMap<>();
            Observable.fromCallable(() -> {
                for(AudioTag tag: tags) {
                    String enc = AudioTagUtils.getEncodingType(tag); ///getAudioEncoding(tag);
                    if(AudioTagUtils.isOnPrimaryStorage(getContext(), tag)) {
                        if(encMemList.containsKey(enc)) {
                            Integer cnt = encMemList.get(enc);
                            encMemList.remove(enc);
                            encMemList.put(enc, new Integer((cnt+1)));
                        }else {
                            encMemList.put(enc, new Integer(1));
                        }
                    }else {
                        if(encSDList.containsKey(enc)) {
                            Integer cnt = encSDList.get(enc);
                            encSDList.remove(enc);
                            encSDList.put(enc, new Integer((cnt+1)));
                        }else {
                            encSDList.put(enc, new Integer(1));
                        }
                    }
                }
                return true;
            }).subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new BlockingBaseObserver<Boolean>() {
                        @Override
                        public void onNext(@io.reactivex.rxjava3.annotations.NonNull Boolean aBoolean) {
                           // setLineDataforCombineChart(chart, encMemList, encSDList, labels);
                            CombinedData data = new CombinedData();
                            data.setData(setupCombineLineData(encMemList, encSDList, labels));
                            data.setData(setupCombineBarData(encMemList, encSDList, labels));
                          //  data.setData(setupCombineLineData(encSDList, labels, "Secondary"));
                            // xAxis.setAxisMaximum(data.getXMax() + 0.25f);
                            chart.setData(data);
                            chart.invalidate();
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {

                        }
                    });
        }

        private BarData setupCombineBarData(Map<String, Integer> encMemList, Map<String, Integer> encSDList, List<String> labels) {
            ArrayList<BarEntry> entries1 = new ArrayList<>();
            ArrayList<BarEntry> entries2 = new ArrayList<>();

            int i=0;
            for(String enc: encMemList.keySet()) {
                float val = 0f;
                if (encMemList.containsKey(enc)) {
                    val = (float) encMemList.get(enc);
                }
                entries1.add(new BarEntry(++i, val, enc));
                val = 0f;
                if(encSDList.containsKey(enc)) {
                    val = (float) encSDList.get(enc);
                }
                entries2.add(new BarEntry(i, val, enc));
            }

            BarDataSet set1 = new BarDataSet(entries1, "Bar 1");
            set1.setColor(Color.rgb(60, 220, 78));
            set1.setValueTextColor(Color.rgb(60, 220, 78));
            set1.setValueTextSize(10f);
            set1.setAxisDependency(YAxis.AxisDependency.LEFT);

            BarDataSet set2 = new BarDataSet(entries2, "");
            set2.setStackLabels(new String[]{"Stack 1", "Stack 2"});
            set2.setColors(Color.rgb(61, 165, 255), Color.rgb(23, 197, 255));
            set2.setValueTextColor(Color.rgb(61, 165, 255));
            set2.setValueTextSize(10f);
            set2.setAxisDependency(YAxis.AxisDependency.LEFT);

            float groupSpace = 0.06f;
            float barSpace = 0.02f; // x2 dataset
            float barWidth = 0.45f; // x2 dataset
            // (0.45 + 0.02) * 2 + 0.06 = 1.00 -> interval per "group"

            BarData d = new BarData(set1, set2);
            d.setBarWidth(barWidth);

            // make this BarData object grouped
            d.groupBars(0, groupSpace, barSpace); // start at x = 0

            return d;
        } */

        /*
        private LineData setupCombineLineData(Map<String, Integer> encMemList,Map<String, Integer> encSDList, List<String> labels) {
            LineData d = new LineData();
            // add data
            ArrayList<Entry> memValues = new ArrayList<>();
            ArrayList<Entry> sdValues = new ArrayList<>();
            int i=0;
            for(String enc: encMemList.keySet()) {
                // for (int i = 0; i < count; i++) {
                float val = 0f;
                if (encMemList.containsKey(enc)) {
                    val = (float) encMemList.get(enc);
                }
                memValues.add(new Entry(++i, val, enc));
                val = 0f;
                if(encSDList.containsKey(enc)) {
                    val = (float) encSDList.get(enc);
                }
                sdValues.add(new Entry(i, val, enc));
                labels.add(enc);
            }

            LineDataSet set1;

            // create a dataset and give it a type
            set1 = new LineDataSet(memValues, "Internal");

            set1.setDrawIcons(false);

            // draw dashed line
            set1.enableDashedLine(10f, 5f, 0f);

            // black lines and points
            set1.setColor(Color.GREEN);
            set1.setCircleColor(Color.GREEN);

            // line thickness and point size
            set1.setLineWidth(2.0f);
            set1.setCircleRadius(4f);
            //set1.setLineWidth(1f);
            //set1.setCircleRadius(3f);

            // draw points as solid circles
            set1.setDrawCircleHole(false);

            // customize legend entry
            set1.setFormLineWidth(1f);
            set1.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
            set1.setFormSize(15.f);

            // text size of values
            set1.setValueTextSize(9f);
            set1.setValueTextColor(Color.rgb(240, 238, 70));

            // draw selection line as dashed
            set1.enableDashedHighlightLine(10f, 5f, 0f);

            // set the filled area
            set1.setDrawFilled(false);


            LineDataSet set2;

            // create a dataset and give it a type
            set2 = new LineDataSet(sdValues, "Secondary");

            set2.setDrawIcons(false);

            // draw dashed line
            set2.enableDashedLine(10f, 5f, 0f);

            // black lines and points
            set2.setColor(Color.RED);
            set2.setCircleColor(Color.RED);

            // line thickness and point size
            set2.setLineWidth(2.0f);
            set2.setCircleRadius(4f);

            // draw points as solid circles
            set2.setDrawCircleHole(false);

            // customize legend entry
            set2.setFormLineWidth(1f);
            set2.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
            set2.setFormSize(15.f);

            // text size of values
            set2.setValueTextSize(9f);
            set2.setValueTextColor(Color.rgb(255, 255, 255));

            // draw selection line as dashed
            set2.enableDashedHighlightLine(10f, 5f, 0f);

            // set the filled area
            set2.setDrawFilled(false);

           // ArrayList<ILineDataSet> dataSets = new ArrayList<>();
           // dataSets.add(set1); // add the data sets
           // dataSets.add(set2); // add the data sets

            d.addDataSet(set1);
            d.addDataSet(set2);

            return d;
        } */

        /*
        private CombinedChart setupCombineChart(View v, List<String> labels) {
            CombinedChart chart = v.findViewById(R.id.combineChart);
            chart.getDescription().setEnabled(false);
            chart.setBackgroundColor(Color.BLACK);
            chart.setDrawGridBackground(false);
            chart.setDrawBarShadow(false);
            chart.setHighlightFullBarEnabled(false);
            chart.setNoDataText("Processing...");

            // draw bars behind lines
            chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                    CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.BUBBLE, CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.SCATTER
            });

            Legend l = chart.getLegend();
            l.setYEntrySpace(5f);
            l.setWordWrapEnabled(true);
            l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
            l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
            l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
            l.setDrawInside(false);
            l.setTextColor(Color.WHITE);

            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setDrawGridLines(false);
            rightAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setDrawGridLines(false);
            leftAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)
            leftAxis.setTextColor(Color.WHITE);

            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setAxisMinimum(0f);
            xAxis.setGranularity(1f);
            xAxis.setTextColor(Color.WHITE);
            return chart;
        } */

        /*
        private void setLineChartData(LineChart chart, List<AudioTag> tags, List<String> labels) {
            Map<String, Integer> encMemList = new HashMap<>();
            Map<String, Integer> encSDList = new HashMap<>();
            Observable.fromCallable(() -> {
                for(AudioTag tag: tags) {
                    String enc = AudioTagUtils.getEncodingType(tag);
                    if(AudioTagUtils.isOnPrimaryStorage(getContext(), tag)) {
                        if(encMemList.containsKey(enc)) {
                            Integer cnt = encMemList.get(enc);
                            encMemList.remove(enc);
                            encMemList.put(enc, new Integer((cnt+1)));
                        }else {
                            encMemList.put(enc, new Integer(1));
                        }
                    }else {
                        if(encSDList.containsKey(enc)) {
                            Integer cnt = encSDList.get(enc);
                            encSDList.remove(enc);
                            encSDList.put(enc, new Integer((cnt+1)));
                        }else {
                            encSDList.put(enc, new Integer(1));
                        }
                    }
                }
                return true;
            }).subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new BlockingBaseObserver<Boolean>() {
                        @Override
                        public void onNext(@io.reactivex.rxjava3.annotations.NonNull Boolean aBoolean) {
                            setLineChartData(chart, encMemList, encSDList, labels);
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {

                        }
                    });
        } */

        /*
        private void setLineChartData(LineChart chart, Map<String, Integer> encMemList, Map<String, Integer> encSDList, List<String> encLabels) {
            // add data
            ArrayList<Entry> memValues = new ArrayList<>();
            ArrayList<Entry> sdValues = new ArrayList<>();
            int i=0;
            for(String enc: encMemList.keySet()) {
                // for (int i = 0; i < count; i++) {
                float val = 0f;
                if (encMemList.containsKey(enc)) {
                    val = (float) encMemList.get(enc);
                }
                memValues.add(new Entry(++i, val, enc));
                val = 0f;
                if(encSDList.containsKey(enc)) {
                    val = (float) encSDList.get(enc);
                }
                sdValues.add(new Entry(i, val, enc));
                encLabels.add(enc);
            }

            LineDataSet set1;

            // create a dataset and give it a type
            set1 = new LineDataSet(memValues, "Internal");

            set1.setDrawIcons(false);

            // draw dashed line
            set1.enableDashedLine(10f, 5f, 0f);

            // black lines and points
            set1.setColor(Color.GREEN);
            set1.setCircleColor(Color.GREEN);

            // line thickness and point size
            set1.setLineWidth(1f);
            set1.setCircleRadius(3f);

            // draw points as solid circles
            set1.setDrawCircleHole(false);

            // customize legend entry
            set1.setFormLineWidth(1f);
            set1.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
            set1.setFormSize(15.f);

            // text size of values
            set1.setValueTextSize(9f);

            // draw selection line as dashed
            set1.enableDashedHighlightLine(10f, 5f, 0f);

            // set the filled area
            set1.setDrawFilled(false);


            LineDataSet set2;

            // create a dataset and give it a type
            set2 = new LineDataSet(sdValues, "Secondary");

            set2.setDrawIcons(false);

            // draw dashed line
            set2.enableDashedLine(10f, 5f, 0f);

            // black lines and points
            set2.setColor(Color.RED);
            set2.setCircleColor(Color.RED);

            // line thickness and point size
            set2.setLineWidth(1f);
            set2.setCircleRadius(3f);

            // draw points as solid circles
            set2.setDrawCircleHole(false);

            // customize legend entry
            set2.setFormLineWidth(1f);
            set2.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
            set2.setFormSize(15.f);

            // text size of values
            set2.setValueTextSize(9f);

            // draw selection line as dashed
            set2.enableDashedHighlightLine(10f, 5f, 0f);

            // set the filled area
            set2.setDrawFilled(false);

            ArrayList<ILineDataSet> dataSets = new ArrayList<>();
            dataSets.add(set1); // add the data sets
            dataSets.add(set2); // add the data sets

            // create a data object with the data sets
            LineData data = new LineData(dataSets);

            // set data
            chart.setData(data);

            // draw points over time
            //chart.animateX(1500);

            // get the legend (only possible after setting data)
            Legend l = chart.getLegend();

            // draw legend entries as lines
            l.setForm(Legend.LegendForm.CIRCLE);
            l.setDrawInside(false);
            l.setTextColor(Color.WHITE);
            chart.invalidate();
        } */

        /*
        private LineChart setupLineChart(View v, List<String> labels) {
            LineChart chart = v.findViewById(R.id.genreChart);
            // background color
            chart.setBackgroundColor(Color.BLACK);

            // disable description text
            chart.getDescription().setEnabled(false);

            // enable touch gestures
            chart.setTouchEnabled(false);

            // set listeners
            //chart.setOnChartValueSelectedListener(this);
            chart.setDrawGridBackground(false);

            // enable scaling and dragging
            chart.setDragEnabled(false);
            chart.setScaleEnabled(false);
            // chart.setScaleXEnabled(true);
            // chart.setScaleYEnabled(true);

            XAxis xAxis;
            {   // // X-Axis Style // //
                xAxis = chart.getXAxis();

                // vertical grid lines
                xAxis.enableGridDashedLine(10f, 10f, 0f);
                xAxis.setTextColor(Color.WHITE);
                //xAxis.setAxisMinimum(0f);
                xAxis.setAvoidFirstLastClipping(true);
                xAxis.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getAxisLabel(float value, AxisBase axis) {
                        //if (value == (((int) value) * 1.0) ) {
                            return StringUtils.trimToEmpty(labels.get((int) value));
                        //}else {
                        //    return "";
                        //}
                        //return super.getAxisLabel(value, axis);
                    }
                });
            }

            YAxis yAxis;
            {   // // Y-Axis Style // //
                yAxis = chart.getAxisLeft();

                // disable dual axis (only use LEFT axis)
                chart.getAxisRight().setEnabled(false);

                // horizontal grid lines
                yAxis.enableGridDashedLine(10f, 10f, 0f);

                // axis range
                //yAxis.setAxisMaximum(200f);
                yAxis.setAxisMinimum(0f);
                yAxis.setTextColor(Color.WHITE);
            }

            return chart;
        } */

        private void setupGroupingTable(View v, Map<String, Integer> songs, Map<String, Long> size, Map<String, Long> duration, long totalSongs, long totalSize, long totalDuration) {
            DataTable dataTable = v.findViewById(R.id.data_table);
            DataTableHeader header = new DataTableHeader.Builder()
                    .item("Grouping", 140)
                    .item("Songs", 100)
                    .item("Size (GB)", 120)
                    .item("Duration", 220)
                    .build();
            ArrayList<DataTableRow> rows = new ArrayList<>();
            // songs = Collections.sort();
            // define 200 fake rows for table
            List<String> keys = (List<String>) Arrays.asList(songs.keySet().toArray(new String[0]));
            Collections.sort(keys);
            for(String grouping: keys) { // songs.keySet()) {
                DataTableRow row = new DataTableRow.Builder()
                        .value(grouping)
                        .value(StringUtils.formatNumber(songs.get(grouping)))
                        .value(StringUtils.formatStorageSizeGB(size.get(grouping)))
                        .value(StringUtils.formatDuration(duration.get(grouping),true)+"  ")
                        .build();
                rows.add(row);
            }
            DataTableRow row = new DataTableRow.Builder()
                    .value("")
                    .value("")
                    .value("")
                    .value("")
                    .build();
            rows.add(row);
            row = new DataTableRow.Builder()
                    .value("Total")
                    .value(StringUtils.formatNumber(totalSongs))
                    .value(StringUtils.formatStorageSizeGB(totalSize))
                    .value(StringUtils.formatDuration(totalDuration,true)+"  ")
                    .build();
            rows.add(row);

            // dataTable.setTypeface(typeface);
            dataTable.setHeader(header);
            dataTable.setRows(rows);
            dataTable.inflate(getContext());
        }

        private void setupEncodingChart(View v, Map<String, Integer> encList) {
            PieChart chart = v.findViewById(R.id.chart1);
            chart.setUsePercentValues(false);
            chart.getDescription().setEnabled(false);
            chart.setExtraOffsets(5, 10, 5, 5);
            // chart.setExtraOffsets(20f, 30f, 20f, 20f);

            chart.setDragDecelerationFrictionCoef(0.95f);

            chart.setDrawRoundedSlices(true);
            chart.setDrawHoleEnabled(true);
            chart.setHoleColor(Color.TRANSPARENT);

            // chart.setTransparentCircleColor(Color.WHITE);
            // chart.setTransparentCircleAlpha(110);

            chart.setHoleRadius(42f);
            chart.setTransparentCircleRadius(72f);

            chart.setDrawCenterText(true);
            // chart.setCenterTextTypeface(tfLight);
            chart.setCenterText("Songs");
            chart.setCenterTextColor(Color.WHITE);

            chart.setRotationAngle(0);
            // enable rotation of the chart by touch
            chart.setRotationEnabled(false);
            chart.setHighlightPerTapEnabled(true);

            // chart.setUnit(" €");
            // chart.setDrawUnitsInChart(true);

            // add a selection listener
            // chart.setOnChartValueSelectedListener(this);

            // chart.animateY(1400, Easing.EaseInOutQuad);
            // chart.spin(2000, 0, 360);

            Legend l = chart.getLegend();
            l.setVerticalAlignment(Legend.LegendVerticalAlignment.CENTER);
            l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
            l.setOrientation(Legend.LegendOrientation.VERTICAL);
            l.setDrawInside(false);
            l.setXEntrySpace(5f);
            l.setYEntrySpace(0f);
            l.setYOffset(0f);
            l.setTextColor(Color.WHITE);

            // entry label styling
            chart.setEntryLabelColor(Color.WHITE);
            chart.setDrawEntryLabels(false);
            //  chart.setEntryLabelTypeface(tfRegular);
            chart.setEntryLabelTextSize(12f);
            setData(chart, encList);
        }

        private void setData(PieChart chart, Map<String, Integer> encList) {
            ArrayList<PieEntry> entries = new ArrayList<>();

            // NOTE: The order of the entries when being added to the entries array determines their position around the center of
            // the chart.
            for(String enc: encList.keySet()) {
                entries.add(new PieEntry(encList.get(enc), enc));
            }

            PieDataSet dataSet = new PieDataSet(entries, "");

            //setting size of the value
           // dataSet.valueTextSize = 10f
            dataSet.setValueLinePart1OffsetPercentage(90.0f);
            dataSet.setValueLinePart1Length(1f);
            dataSet.setValueLinePart2Length(0.4f);

            dataSet.setDrawIcons(false);
            dataSet.setSliceSpace(3f);
           // dataSet.setIconsOffset(new MPPointF(0, 40));
            dataSet.setSelectionShift(5f);
            //setting position of the value
            dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            dataSet.setUsingSliceColorAsValueLineColor(true);
            dataSet.setAutomaticallyDisableSliceSpacing(false);

            // add a lot of colors

            ArrayList<Integer> colors = new ArrayList<>();

            for (int c : ColorTemplate.MATERIAL_COLORS)
                colors.add(c);

            for (int c : ColorTemplate.JOYFUL_COLORS)
                colors.add(c);

            for (int c : ColorTemplate.COLORFUL_COLORS)
                colors.add(c);

            for (int c : ColorTemplate.LIBERTY_COLORS)
                colors.add(c);

            for (int c : ColorTemplate.PASTEL_COLORS)
                colors.add(c);

            colors.add(ColorTemplate.getHoloBlue());

            dataSet.setColors(colors);
            //dataSet.setSelectionShift(0f);

            PieData data = new PieData(dataSet);
            data.setValueFormatter(new PercentFormatter());
            data.setValueTextSize(11f);
            data.setValueTextColor(Color.WHITE);
            //data.setValueTypeface(tfLight);
            chart.setData(data);

            // undo all highlights
            chart.highlightValues(null);

            chart.invalidate();
        }
    }
}