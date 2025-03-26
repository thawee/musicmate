package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.format;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import apincer.android.mmate.codec.FFMPegReader;
import apincer.android.mmate.codec.TagReader;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.codec.FFMpegHelper;
import apincer.android.mmate.utils.ReflectUtil;
import apincer.android.mmate.utils.StringUtils;

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
            if(item.getItemId() == R.id.menu_editor_tech_refresh) {
               // doReloadTagFromFile();
                doResetTagFromFile();
                getActivity().finish();
            }else if(item.getItemId() == R.id.menu_editor_tech_extract_coverart) {
                doExtractEmbedCoverart();
            }if(item.getItemId() == R.id.menu_editor_tech_remove_coverart) {
                doRemoveEmbedCoverart();
           // }if(item.getItemId() == R.id.menu_editor_tech_replaygain) {
           //     doUpdateReplayGain();
            }
            return false;
        };
    }

    /*
    private void doUpdateReplayGain() {
        // calculate RG
        // update RG on files
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                   for(MusicTag tag:tagsActivity.getEditItems()) {
                        //calculate track RG
                        FFMPeg.detectQuality(tag);
                    }

                    // save RG to media file
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        //write RG to file
                        FFMPeg.writeTagQualityToFile(getActivity(), tag);
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
    } */

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView filename = view.findViewById(R.id.filename);
        TextView metada = view.findViewById(R.id.ffmpeg_info);
        TableLayout table = view.findViewById(R.id.tags);

        MusicTag tag = tagsActivity.getEditItems().get(0);
        String musicMatePath = FileRepository.newInstance(getContext()).buildCollectionPath(tag, true);
        filename.setText(String.format("Current Path:\n%s\n\nMusicMate Path:\n%s", tag.getPath(), musicMatePath));

        MusicTag tt = TagReader.readTag(context, tag.getPath()).get(0); // .readMusicTag(getContext(), tag.getPath()).get(0);

        MusicTag ffmpegTag = (new FFMPegReader(getContext())).extractTagFromFile(tag.getPath());
        metada.setText(ffmpegTag.getData());
       // metada.setText(ffmpegTag.getData() +"\r\n"+ MusicTagUtils.getEncResolutionIcon(context, tag));

        TableRow tbrow0 = new TableRow(getContext());
        TextView tv0 = new TextView(getContext());
        TableRow.LayoutParams llp = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 0, 2, 0);//2px right-margin
        LinearLayout cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        tv0.setText(R.string.label_field);
        tv0.setTextColor(Color.WHITE);
        cell.addView(tv0);
        tbrow0.addView(cell);

        cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        TextView tv1 = new TextView(getContext());
        tv1.setText(R.string.label_current);
        tv1.setTextColor(Color.WHITE);
        cell.addView(tv1);
        tbrow0.addView(cell);

        cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        TextView tv = new TextView(getContext());
        tv.setText(R.string.label_default_reader);
        tv.setTextColor(Color.WHITE);
        cell.addView(tv);
        tbrow0.addView(cell);

        cell = new LinearLayout(getContext());
        cell.setBackgroundColor(Color.DKGRAY);
        cell.setLayoutParams(llp);//2px border on the right for the cell
        tv = new TextView(getContext());
        tv.setText(R.string.label_ffmpeg_reader);
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
                    || field.getName().equals("id")
                    || field.getName().equals("uniqueKey")
                    || field.getName().equals("albumUniqueKey")
                    || field.getName().equals("data")
                    || field.getName().equals("storageId")
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
            String stdVal = format(ReflectUtil.getFieldValue(field,tt),20,"\n");
            String ffmpegVal = format(ReflectUtil.getFieldValue(field,ffmpegTag),20,"\n");

            cell = new LinearLayout(getContext());
            cell.setBackgroundColor(Color.DKGRAY);
            cell.setLayoutParams(llp);//2px border on the right for the cell
            TextView t1v = new TextView(getContext());
            t1v.setText(field.getName());
            //t1v.setTextColor(StringUtils.equals(mateVal, ffmpegVal)?Color.WHITE:Color.RED);
            t1v.setTextColor(StringUtils.equals(stdVal, ffmpegVal)?Color.WHITE:Color.RED);
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
            t3v.setText(stdVal);
            t3v.setTextColor(Color.WHITE);
            t3v.setGravity(Gravity.CENTER);
            cell.addView(t3v);
            tr.addView(cell);

            cell = new LinearLayout(getContext());
            cell.setBackgroundColor(Color.GRAY);
            cell.setLayoutParams(llp);//2px border on the right for the cell
            t3v = new TextView(getContext());
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
                    FileRepository repos = FileRepository.newInstance(getContext());
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        FFMpegHelper.removeCoverArt(getContext(), tag);
                        repos.scanMusicFile(new File(tag.getPath()), false);
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
                        String coverArtPath = tag.getCoverartMime();
                      //  if(!isEmpty(coverArtPath)) {
                            coverArtPath = coverArtPath.replace("/", ".");
                            String path = tag.getPath();
                            coverArtPath = path.substring(0, path.lastIndexOf("."))+"."+coverArtPath;
                            FFMpegHelper.extractCoverArt(tag, new File(coverArtPath));
                       // }
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
                    FileRepository repos = FileRepository.newInstance(getContext());
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        repos.cleanCacheCover(getContext(), tag);
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

    private void doResetTagFromFile() {
        startProgressBar();
        CompletableFuture.runAsync(
                () -> {
                    FileRepository repos = FileRepository.newInstance(getContext());
                    for(MusicTag tag:tagsActivity.getEditItems()) {
                        repos.cleanCacheCover(getContext(), tag);
                        TagRepository.removeTag(tag);
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
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            try {
                if (!isAdded() || getActivity() == null) return;
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
                dialogBuilder.setView(R.layout.progress_dialog_layout);
                dialogBuilder.setCancelable(false);
                progressDialog = dialogBuilder.create();
                progressDialog.show();
            } catch (Exception e) {
                Log.e("TagsTechnicalFragment", "Error showing progress dialog", e);
            }
        });
    }

    private void stopProgressBar() {
        if (!isAdded() || getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            try {
                if(progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            } catch (Exception e) {
                Log.e("TagsTechnicalFragment", "Error dismissing progress dialog", e);
            }
        });
    }
}
