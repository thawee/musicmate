package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.format;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FFMPeg;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.JustFLAC;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.ReplayGain;
import apincer.android.mmate.utils.StringUtils;
import de.esoco.lib.reflect.ReflectUtil;

public class TagsTechnicalFragment extends Fragment {
    protected Context context;
    protected TagsActivity tagsActivity;
    AlertDialog progressDialog;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
        this.tagsActivity = (TagsActivity) getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ffmpeg, container, false);
    }

    public Toolbar.OnMenuItemClickListener getOnMenuItemClickListener() {
        return item -> {
            if(item.getItemId() == R.id.menu_editor_tech_reload) {
                doReloadTagFromFile();
            }else if(item.getItemId() == R.id.menu_editor_tech_extract_coverart) {
                doExtractEmbedCoverart();
            }if(item.getItemId() == R.id.menu_editor_tech_remove_coverart) {
                doRemoveEmbedCoverart();
            }if(item.getItemId() == R.id.menu_editor_tech_replaygain) {
                doUpdateReplayGain();
            }
            return false;
        };
    }

    private void doUpdateReplayGain() {
        // calculate RG
        // update RG on files
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                   for(MusicTag tag:tagsActivity.getEditItems()) {
                        //calculate track RG
                        FFMPeg.readReplayGain(getActivity(), tag);
                       // FFMPeg.readLoudness(getContext(), tag);
                    }
                    // calculate album RG
                    //ReplayGain.calculate(tagsActivity.getEditItems());
                    /*
                    if(tagsActivity.getEditItems().size()>=1) {
                        double rg = tagsActivity.getEditItems().get(0).getTrackRG();
                        double tp = tagsActivity.getEditItems().get(0).getTrackTruePeak();
                        for(MusicTag tag:tagsActivity.getEditItems()) {
                            if(tag.getTrackTruePeak() > tp) {
                                tp = tag.getTrackTruePeak();
                            }
                            if(rg < tag.getTrackRG()) {
                                rg = tag.getTrackRG();
                            }
                        }
                        for(MusicTag tag:tagsActivity.getEditItems()) {
                            //calculate track RG
                            tag.setAlbumRG(rg);
                            tag.setAlbumTruePeak(tp);
                        }
                    }*/

                    // save RG to media file
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        //write RG to file
                        FFMPeg.writeReplayGain(getActivity(), tag);
                        // update MusicMate Library
                        MusicTagRepository.saveTag(tag);
                    }
                }
        ).thenAccept(
                unused -> stopProgressBar()
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView filename = view.findViewById(R.id.filename);
        TextView metada = view.findViewById(R.id.ffmpeg_info);
        TableLayout table = view.findViewById(R.id.tags);

        MusicTag tag = tagsActivity.getEditItems().get(0);
        String musicMatePath = FileRepository.newInstance(getContext()).buildCollectionPath(tag, true);
        filename.setText(String.format("MediaPath:\n%s\n\nMusicMatePath:\n%s", tag.getPath(), musicMatePath));

        //if(tag.getData()!=null) {
        //    metada.setText(String.format("MusicTag:\n%s\n\n", tag.getData()));
        //}
        MusicTag tt = null;
        if(JustFLAC.isSupportedFileFormat(tag.getPath())) {
            tt = JustFLAC.readMusicTag(getActivity().getApplicationContext(), tag.getPath());
        }else {
            tt = FFMPeg.readFFmpeg(getActivity().getApplicationContext(), tag.getPath());
        }
        //MusicTag tt = FFMPeg.readFFprobe(getActivity().getApplicationContext(), tag.getPath());
        if(tt!=null) {
           // metada.setText(String.format("%s\n\nFFmpeg:\n%s", metada.getText(), tt.getData()));
            metada.setText(String.format("FFmpeg:\n%s", tt.getData()));
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
        tv1.setText(" Tags from MusicMate ");
        tv1.setTextColor(Color.WHITE);
        cell.addView(tv1);
        tbrow0.addView(cell);

        cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        TextView tv = new TextView(getContext());
        tv.setText(" Tags from file");
        tv.setTextColor(Color.WHITE);
        cell.addView(tv);
        tbrow0.addView(cell);
        table.addView(tbrow0);

        List<Field> fields =  ReflectUtil.getAllFields(MusicTag.class);
       // String text = "\nField Name\t--> Library\t<>\tFFMPeg\n";
        for(Field field: fields) {
            if (field.getName().equals("path")
                    || field.getName().equals("simpleName")
                    || field.getName().equals("mmManaged")
                    || field.getName().equals("fileSizeRatio")
                    || field.getName().equals("id")
                    || field.getName().equals("uniqueKey")
                    || field.getName().equals("data")
                    //|| field.getName().equals("storageId")
                    || field.getName().equals("CREATOR")
                    || field.getName().equals("originTag")
                    || field.getName().startsWith("shadow"))  {
                continue;
            }
            TableRow tr = new TableRow(getContext());
            tr.setBackgroundColor(Color.BLACK);
            tr.setPadding(0, 0, 0, 2); //Border between rows

           // TableRow.LayoutParams llp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
           // llp.setMargins(0, 0, 2, 0);//2px right-margin

            //New Cell
            String mateVal = format(ReflectUtil.getFieldValue(field,tag),20,"\n");
           // String ffprobeVal = format(ReflectUtil.getFieldValue(field,ffmpegTag),20,"\n");
            String ffmpegVal = format(ReflectUtil.getFieldValue(field,tt),20,"\n");

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
    }

    private void doRemoveEmbedCoverart() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    FileRepository repos = FileRepository.newInstance(getActivity().getApplicationContext());
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        String path = FFMPeg.removeCoverArt(getActivity().getApplicationContext(), tag);
                        repos.scanMusicFile(new File(path), true);
                    }
                }
        ).thenAccept(
                unused -> stopProgressBar()
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }

    private void doExtractEmbedCoverart() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        String coverArtPath = tag.getEmbedCoverArt();
                        if(!isEmpty(coverArtPath)) {
                            String path = tag.getPath();
                            coverArtPath = path.substring(0, path.lastIndexOf("."))+"."+coverArtPath;
                            FFMPeg.extractCoverArt(tag, new File(coverArtPath));
                        }
                    }
                }
        ).thenAccept(
                unused -> stopProgressBar()
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }


    private void doReloadTagFromFile() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    FileRepository repos = FileRepository.newInstance(getActivity().getApplicationContext());
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        repos.scanMusicFile(new File(tag.getPath()), true);
                    }
                }
        ).thenAccept(
                unused -> stopProgressBar()
        ).exceptionally(
                throwable -> {
                    stopProgressBar();
                    return null;
                }
        );
    }

    private void startProgressBar() {
        getActivity().runOnUiThread(() -> {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
            dialogBuilder.setView(R.layout.progress_dialog_layout);
            dialogBuilder.setCancelable(false);
            progressDialog = dialogBuilder.create();
            progressDialog.show();
        });
    }


    private void stopProgressBar() {
        getActivity().runOnUiThread(() -> {
            if(progressDialog!=null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }
}
