import re

with open("app/src/main/java/apincer/android/mmate/ui/AboutActivity.java", "r") as f:
    content = f.read()

import_lines = "import androidx.compose.ui.platform.ComposeView;\nimport apincer.android.mmate.ui.compose.ChartInterop;\nimport apincer.android.mmate.ui.compose.PieEntry;"
if "import androidx.compose.ui.platform.ComposeView;" not in content:
    content = content.replace("import android.view.View;", "import android.view.View;\n" + import_lines)

old_method = """        private void setupQualityChart(View v, Map<String, Integer> encList, String title) {
            apincer.android.mmate.ui.widget.QualityPieChartView chart = v.findViewById(R.id.chartResolutions);
            
            List<apincer.android.mmate.ui.widget.QualityPieChartView.PieEntry> entries = new ArrayList<>();
            Map<String, Integer> mappedColors = new HashMap<>();
            mappedColors.put(Constants.LEGEND_MQA, ContextCompat.getColor(getContext(), R.color.quality_mqa_background));
            mappedColors.put(Constants.LEGEND_DSD, ContextCompat.getColor(getContext(), R.color.quality_dsd_background));
            mappedColors.put(Constants.LEGEND_HIRES, ContextCompat.getColor(getContext(), R.color.quality_hr_background));
            mappedColors.put(Constants.LEGEND_CD, ContextCompat.getColor(getContext(), R.color.quality_cd_background));
            mappedColors.put(Constants.LEGEND_STUDIO, ContextCompat.getColor(getContext(), R.color.quality_cd_ext_background));
            mappedColors.put(Constants.LEGEND_LOSSY, ContextCompat.getColor(getContext(), R.color.quality_lc_background));

            for(String enc: encList.keySet()) {
                int color = mappedColors.containsKey(enc) ? mappedColors.get(enc) : Color.GRAY;
                entries.add(new apincer.android.mmate.ui.widget.QualityPieChartView.PieEntry(enc, encList.get(enc), color));
            }

            chart.setEntries(entries);
        }"""

new_method = """        private void setupQualityChart(View v, Map<String, Integer> encList, String title) {
            ComposeView chart = v.findViewById(R.id.chartResolutions);
            
            List<PieEntry> entries = new ArrayList<>();
            Map<String, Integer> mappedColors = new HashMap<>();
            mappedColors.put(Constants.LEGEND_MQA, ContextCompat.getColor(getContext(), R.color.quality_mqa_background));
            mappedColors.put(Constants.LEGEND_DSD, ContextCompat.getColor(getContext(), R.color.quality_dsd_background));
            mappedColors.put(Constants.LEGEND_HIRES, ContextCompat.getColor(getContext(), R.color.quality_hr_background));
            mappedColors.put(Constants.LEGEND_CD, ContextCompat.getColor(getContext(), R.color.quality_cd_background));
            mappedColors.put(Constants.LEGEND_STUDIO, ContextCompat.getColor(getContext(), R.color.quality_cd_ext_background));
            mappedColors.put(Constants.LEGEND_LOSSY, ContextCompat.getColor(getContext(), R.color.quality_lc_background));

            for(String enc: encList.keySet()) {
                int color = mappedColors.containsKey(enc) ? mappedColors.get(enc) : Color.GRAY;
                entries.add(new PieEntry(enc, encList.get(enc), new androidx.compose.ui.graphics.Color(color)));
            }

            ChartInterop.setQualityPieChartContent(chart, entries);
        }"""

content = content.replace(old_method, new_method)

with open("app/src/main/java/apincer/android/mmate/ui/AboutActivity.java", "w") as f:
    f.write(content)
