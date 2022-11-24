package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.format;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.lang.reflect.Field;
import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.ffmpeg.FFMPegUtils;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.utils.StringUtils;
import de.esoco.lib.reflect.ReflectUtil;

public class TagsTechnicalFragment extends Fragment {
    protected Context context;
    protected TagsActivity tagsActivity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        this.tagsActivity = (TagsActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ffmpeg, container, false);
        return v;
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView filename = view.findViewById(R.id.filename);
        TextView metada = view.findViewById(R.id.ffmpeg_info);
        TableLayout table = view.findViewById(R.id.tags);

        MusicTag tag = tagsActivity.getEditItems().get(0);
        String musicMatePath = FileRepository.newInstance(getContext()).buildCollectionPath(tag);
        filename.setText("MediaPath:\n"+tag.getPath()+"\n\nMuicMatePath:\n"+musicMatePath);
        MusicTag ffmpegTag = FFMPegUtils.readMusicTag(tag.getPath());
        if(ffmpegTag!=null) {
            metada.setText(ffmpegTag.getData());
        }

        TableRow tbrow0 = new TableRow(getContext());
        TextView tv0 = new TextView(getContext());
        TableRow.LayoutParams llp = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 0, 2, 0);//2px right-margin
        LinearLayout cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        tv0.setText(" Attribute ");
        tv0.setTextColor(Color.WHITE);
        cell.addView(tv0);
        tbrow0.addView(cell);

        cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        TextView tv1 = new TextView(getContext());
        tv1.setText(" MusicMate Library ");
        tv1.setTextColor(Color.WHITE);
        cell.addView(tv1);
        tbrow0.addView(cell);

        cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        TextView tv2 = new TextView(getContext());
        tv2.setText(" From File ");
        tv2.setTextColor(Color.WHITE);
        cell.addView(tv2);
        tbrow0.addView(cell);
        table.addView(tbrow0);

        List<Field> fields =  ReflectUtil.getAllFields(MusicTag.class);
       // String text = "\nField Name\t--> Library\t<>\tFFMPeg\n";
        for(Field field: fields) {
            if (field.getName().equals("path")
                    || field.getName().equals("simpleName")
                    || field.getName().equals("uniqueKey")
                    || field.getName().equals("data")
                    || field.getName().equals("storageId")
                    || field.getName().equals("CREATOR")
                    || field.getName().equals("originTag")
                    || field.getName().startsWith("shadow"))  {
                continue;
            };
            TableRow tr = new TableRow(getContext());
            tr.setBackgroundColor(Color.BLACK);
            tr.setPadding(0, 0, 0, 2); //Border between rows

           // TableRow.LayoutParams llp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
           // llp.setMargins(0, 0, 2, 0);//2px right-margin

            //New Cell
            String mateVal = format(ReflectUtil.getFieldValue(field,tag),12,"\n");
            String ffmpegVal = format(ReflectUtil.getFieldValue(field,ffmpegTag),12,"\n");
            cell = new LinearLayout(getContext());
            cell.setBackgroundColor(Color.DKGRAY);
            cell.setLayoutParams(llp);//2px border on the right for the cell
            TextView t1v = new TextView(getContext());
            t1v.setText(field.getName());
            t1v.setTextColor(StringUtils.equals(mateVal, ffmpegVal)?Color.WHITE:Color.RED);
            t1v.setGravity(Gravity.LEFT);
            t1v.setPadding(24,0,0,0);
            cell.addView(t1v);
            tr.addView(cell);

            cell = new LinearLayout(getContext());
            cell.setBackgroundColor(Color.GRAY);
            cell.setLayoutParams(llp);//2px border on the right for the cell
            TextView t2v = new TextView(getContext());
            t2v.setText(mateVal);
            t2v.setTextColor(Color.WHITE);
            t2v.setGravity(Gravity.CENTER);
            cell.addView(t2v);
            tr.addView(cell);

            cell = new LinearLayout(getContext());
            cell.setBackgroundColor(Color.GRAY);
            cell.setLayoutParams(llp);//2px border on the right for the cell
            TextView t3v = new TextView(getContext());
            t3v.setText(ffmpegVal);
            t3v.setTextColor(Color.WHITE);
            t3v.setGravity(Gravity.CENTER);
            cell.addView(t3v);
            tr.addView(cell);

            table.addView(tr);
        }

      //  tags.setText(text);

        /*
        String cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format \""+tag.getPath()+"\"";
        FFprobeSession session = FFprobeKit.execute(cmd);

        //if (!ReturnCode.isSuccess(session.getReturnCode())) {
        //   metada.setText(session.getOutput());
        //}else {
        //    metada.setText(session.getOutput());
       // }
        String output1 = session.getOutput();


        cmd ="-hide_banner -of default=noprint_wrappers=0 -show_format -print_format json \""+tag.getPath()+"\"";
        session = FFprobeKit.execute(cmd);

        metada.setText(output1 +"\n\n==========\n\n"+session.getOutput()); */
        //if (!ReturnCode.isSuccess(session.getReturnCode())) {
        //    metada.setText(session.getOutput());
        //}else {
        //    metada.setText(session.getOutput());
        //}
    }
}
