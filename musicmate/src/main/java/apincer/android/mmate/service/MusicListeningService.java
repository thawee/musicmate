package apincer.android.mmate.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.palette.graphics.Palette;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.ui.MediaBrowserActivity;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import timber.log.Timber;

/**
 * Created by e1022387 on 5/29/2017.
 */

public class MusicListeningService extends Service {
    public Bitmap DEFAULT_PLAYER_ICON;
	
	/**
	 * Poweramp package name
	 */
	public static final String PAAPI_ACTION_API_COMMAND="com.maxmpz.audioplayer.API_COMMAND";
	public static final String PAAPI_COMMAND="cmd";
	public static final int PAAPI_COMMAND_NEXT=4;
	public static final String PAAPI_PACKAGE_NAME = "com.maxmpz.audioplayer";
	public static final String PAAPI_PLAYER_SERVICE_NAME = "com.maxmpz.audioplayer.player.PlayerService";
	public static final ComponentName PAAPI_PLAYER_SERVICE_COMPONENT_NAME = new ComponentName(PAAPI_PACKAGE_NAME, PAAPI_PLAYER_SERVICE_NAME);

    // android 5 SD card permissions
    public static final int REQUEST_CODE_STORAGE_PERMISSION = 1010;

    public static final String FLAG_SHOW_LISTENING = "__FLAG_SHOW_LISTENING";

    public static String[] PERMISSIONS_ALL = {Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private static final String CHANNEL_ID = "music_mate_now_listening";
    private static final  int NOTIFICATION_ID = 19099;
    private Context context;
    private static MusicListeningService instance;
    private AudioTag playingSong;
    private static final List<ListeningReceiver> receivers = new ArrayList<>();

    private NotificationCompat.Builder builder;
    private NotificationChannel mChannel;
    private MusicPlayerInfo playerInfo;

    @Override
    public void onCreate() {
        super.onCreate();
        playingSong = null;
        context = getApplicationContext();
        mChannel = createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        builder.setContentTitle("Music Mate")
                .setContentText("")
                .setTicker("Music Mate");
        startForeground(NOTIFICATION_ID, builder.build());
        DEFAULT_PLAYER_ICON = BitmapFactory.decodeResource(getResources(), R.drawable.ic_broken_image_black_24dp);
        registerReceiver(new MusicMateBroadcastReceiver());
        instance = this;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private NotificationChannel createNotificationChannel() {
        NotificationManager
                mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // The user-visible name of the channel.
        CharSequence name = "Music Mate Listening";
        // The user-visible description of the channel.
        String description = "Now playing song...";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
        // Configure the notification channel.
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mChannel.setSound(null,null);

        mChannel.setLightColor(Color.RED);
        mChannel.setDescription(description);
		mChannel.canShowBadge();
        mChannel.setShowBadge(true);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        mNotificationManager.createNotificationChannel(mChannel);
        return mChannel;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        finishNotification();
        unregisterReceivers();
        instance = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerReceiver(ListeningReceiver receiver) {
        if(receiver instanceof MusicMateBroadcastReceiver) {
            ((MusicMateBroadcastReceiver)receiver).register(context);
        }
        receivers.add(receiver);
    }

    private void unregisterReceivers() {
        if(!receivers.isEmpty()) {
            for (ListeningReceiver receiver : receivers) {
                try {
                    if(receiver instanceof MusicMateBroadcastReceiver) {
                        unregisterReceiver((BroadcastReceiver)receiver);
                    }
                } catch (Exception ex) {
                    Timber.e( ex);
                }
            }
            receivers.clear();
        }
    }

    public static MusicListeningService getInstance() {
        return instance;
    }

    protected void setPlayingSong(String currentTitle, String currentArtist, String currentAlbum) {
        // FIXME move to RXAndroid
        currentTitle = StringUtils.trimTitle(currentTitle);
        currentArtist = StringUtils.trimTitle(currentArtist);
        currentAlbum = StringUtils.trimTitle(currentAlbum);
        populatePlayingSong(currentTitle, currentArtist, currentAlbum);

        if(StringUtils.isEmpty(currentTitle) && StringUtils.isEmpty(currentArtist) && StringUtils.isEmpty(currentAlbum)) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }

        // display song info from music library
        if(playingSong!=null) {
           sendBroadcast();
           if(Preferences.isShowNotification(getApplicationContext())) {
                Notification notification = createNowPlayingNotification(playingSong, currentTitle,currentArtist,currentAlbum);
                displayNotification(context, notification);
           }
        }
    }

    private PendingIntent getPendingIntent(AudioTag item) {
        Intent intent = new Intent(this, MediaBrowserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (item!=null) {
            intent.putExtra(FLAG_SHOW_LISTENING, "yes");
            intent.putExtra(Constants.KEY_MEDIA_TAG, item);
        }
        return PendingIntent.getActivity(this, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification createNowPlayingNotification(AudioTag item, String currentTitle, String currentArtist, String currentAlbum) {
        builder = createCustomNotificationBuilder(context);
        if(Preferences.isShowNotification(getApplicationContext()) && item !=null) {
            RemoteViews notificationLayout = createRemoteView(item, currentTitle, currentArtist, currentAlbum, false);//new RemoteViews(getPackageName(), R.layout.notification_small);
            RemoteViews notificationLayoutExpanded = createRemoteView(item, currentTitle, currentArtist, currentAlbum, true);//new RemoteViews(getPackageName(), R.layout.notification_large);

            if(notificationLayout != null) {
                builder.setCustomContentView(notificationLayout);
            }
            if(notificationLayoutExpanded != null) {
                builder.setCustomBigContentView(notificationLayoutExpanded);
            }

            builder.setContentIntent(getPendingIntent(item));
            builder.setColorized(true);
            builder.setNumber(1);
        }
        return builder.build();
    }

    private RemoteViews createRemoteView(AudioTag item, String currentTitle, String currentArtist, String currentAlbum, boolean bigContent) {
        try {
                int layoutId = R.layout.view_notification;
                if (item == null) {
                    layoutId = R.layout.view_notification_missing;
                }
                RemoteViews contentView = new RemoteViews(getPackageName(), layoutId);

                int panelLabelColor = getColor(R.color.now_playing);
                int panelColor = getColor(R.color.grey200);
                int textColor = Color.WHITE;

                // get icon from package
                Bitmap bmpCoverArt = null;
                Bitmap iconPlayer = getPlayerIconBitmap();
                iconPlayer = iconPlayer == null ? DEFAULT_PLAYER_ICON : iconPlayer;

                if (item != null) {
                    // get small coverart
                    bmpCoverArt = AudioFileRepository.getArtwork(item, false);
                    if (bmpCoverArt != null) {
                        contentView.setViewVisibility(R.id.item_player, View.VISIBLE);
                    } else {
                        // not show player icon if album artist is null
                        contentView.setViewVisibility(R.id.item_player, View.GONE);
                    }
                }

             bmpCoverArt = bmpCoverArt==null?iconPlayer:bmpCoverArt;
            if (bmpCoverArt != null) {
                contentView.setImageViewBitmap(R.id.notification_coverart, bmpCoverArt);
                Palette palette = Palette.from(bmpCoverArt).generate();
                panelColor = palette.getDominantColor(panelColor);
                panelLabelColor = palette.getMutedColor(panelLabelColor);
            }

                if (item != null) {
                    contentView.setTextViewText(R.id.notification_title, AudioTagUtils.getFormattedTitle(getApplicationContext(), item));
                    contentView.setTextViewText(R.id.notification_artist, AudioTagUtils.getFormattedSubtitle(item));

                  //  int qualityColor = getSampleRateColor(item);
                   // contentView.setImageViewBitmap(R.id.notification_bitsample_img, MediaItemUtils.createBitmapFromText(context, Constants.INFO_SAMPLE_RATE_WIDTH, 32, item.getMetadata().getAudioBitCountAndSampleRate(), getColor(R.color.black), getColor(R.color.black), qualityColor));
                    contentView.setImageViewBitmap(R.id.notification_bitsample_img, AudioTagUtils.getSampleRateIcon(context, item));
                    // contentView.setImageViewBitmap(R.id.notification_duration_img, MediaItemUtils.createBitmapFromText(context,80, 32, item.getMetadata().getAudioDurationAsString(), getColor(R.color.black), getColor(R.color.black), qualityColor));
                    contentView.setImageViewBitmap(R.id.notification_duration_img, AudioTagUtils.getDurationIcon(context,item));
                    // contentView.setImageViewBitmap(R.id.notification_filesize_img, MediaItemUtils.createBitmapFromText(context,100, 32, item.getMetadata().getMediaSize(), getColor(R.color.black), getColor(R.color.black), qualityColor));
                    contentView.setImageViewBitmap(R.id.notification_filesize_img, AudioTagUtils.getFileSizeIcon(context, item));

                    if(AudioTagUtils.isHiResOrDSD(item)) {
                        contentView.setImageViewBitmap(R.id.notification_hires_icon, AudioTagUtils.createBitmapFromDrawable(getApplicationContext(), 1, 1, R.drawable.ic_format_hires, Color.WHITE, Color.TRANSPARENT));
                        contentView.setViewVisibility(R.id.notification_hires_icon, View.VISIBLE);
                    }else  {
                        contentView.setViewVisibility(R.id.notification_hires_icon, View.GONE);
                    }
                  //  contentView.setImageViewBitmap(R.id.notification_hires_icon, AudioTagUtils.getSourceIcon(getApplicationContext(), item));

                    // show MQA, DSDx, HRA
                    Bitmap resBitmap = AudioTagUtils.getResIcon(context, item);
                    if(resBitmap != null) {
                        contentView.setViewVisibility(R.id.notification_mqa_icon, View.VISIBLE);
                        contentView.setImageViewBitmap(R.id.notification_mqa_icon, resBitmap);
                    }else{
                        contentView.setViewVisibility(R.id.notification_mqa_icon, View.GONE);
                    }
                    /*
                    if(item.getMetadata().isMQA() ) {
                        contentView.setViewVisibility(R.id.notification_mqa_icon, View.VISIBLE);
                     //   contentView.setImageViewBitmap(R.id.notification_mqa_icon, MediaItemUtils.createBitmapFromDrawable(context, 80, 32, R.drawable.ic_item_mqa, getColor(R.color.black), qualityColor));
                            contentView.setImageViewBitmap(R.id.notification_mqa_icon, MediaItemUtils.createBitmapFromText(getApplicationContext(), 60, 32, "MQA", qualityColor ,qualityColor, Color.TRANSPARENT)); //MediaItemUtils.createBitmapFromDrawable(getApplicationContext(), 100, 32, R.drawable.ic_item_mqa_logo, Color.WHITE, getApplicationContext().getColor(item.getEncodingColorId())));

                        //      contentView.setImageViewResource(R.id.notification_mqa_icon, R.drawable.ic_item_mqa);
                    }else if(item.getMetadata().isDSD() ) {
                        contentView.setViewVisibility(R.id.notification_mqa_icon, View.VISIBLE);
                        contentView.setImageViewBitmap(R.id.notification_mqa_icon, MediaItemUtils.createBitmapFromText(getApplicationContext(), 60, 32, item.getMetadata().getDSDRate(), qualityColor ,qualityColor, Color.TRANSPARENT));
                       // contentView.setImageViewBitmap(R.id.notification_mqa_icon, MediaItemUtils.createBitmapFromDrawable(context, 80, 32, R.drawable.ic_item_dsd, getColor(R.color.black), qualityColor));
                        //    contentView.setImageViewResource(R.id.notification_mqa_icon, R.drawable.ic_item_dsd);
                    }else if(item.getMetadata().isPCMHRA() ) {
                        contentView.setViewVisibility(R.id.notification_mqa_icon, View.VISIBLE);
                        contentView.setImageViewBitmap(R.id.notification_mqa_icon, MediaItemUtils.createBitmapFromText(getApplicationContext(), 60, 32, "HRA", qualityColor ,qualityColor, Color.TRANSPARENT)); //MediaItemUtils.createBitmapFromText(getApplicationContext(), 100, 32, item.getMetadata().getDSDRate(), Color.WHITE ,Color.WHITE, getApplicationContext().getColor(item.getEncodingColorId())));
                        // contentView.setImageViewBitmap(R.id.notification_mqa_icon, MediaItemUtils.createBitmapFromDrawable(context, 80, 32, R.drawable.ic_item_dsd, getColor(R.color.black), qualityColor));
                        //    contentView.setImageViewResource(R.id.notification_mqa_icon, R.drawable.ic_item_dsd);
                    }else {
                        contentView.setViewVisibility(R.id.notification_mqa_icon, View.GONE);
                    } */

                   // contentView.setImageViewBitmap(R.id.notification_encoding_icon, MediaItemUtils.createBitmapFromText(context,60, 32, item.getMetadata().getAudioEncoding(), Color.WHITE,Color.WHITE, getApplicationContext().getColor(item.getEncodingColorId())));
                   contentView.setImageViewBitmap(R.id.notification_encoding_icon, AudioTagUtils.getFileFormatIcon(context, item));
                    // contentView.setImageViewResource(R.id.notification_encoding_icon, item.getEncodingResId(getApplicationContext()));

                } else {
                    contentView.setTextViewText(R.id.notification_title, currentTitle);
                    contentView.setTextViewText(R.id.notification_artist, getSubtitle(currentAlbum, currentArtist));
                    contentView.setTextViewText(R.id.notification_player, getPlayerName());
                    contentView.setTextColor(R.id.notification_player, textColor);
                }

                contentView.setTextColor(R.id.notification_title, textColor);
                contentView.setTextColor(R.id.notification_artist, textColor);

                Bitmap background = UIUtils.buildGradientBitmap(getApplicationContext(), panelLabelColor, 1024, 60, 4, 4, 4, 4);
                // background.

                contentView.setImageViewBitmap(R.id.notification_bgcolor, UIUtils.buildGradientBitmap(getApplicationContext(), panelColor, 1024, 60, 4, 4, 10, 10));
                contentView.setImageViewBitmap(R.id.notification_text_bgcolor, background);
                contentView.setImageViewBitmap(R.id.item_player, AudioTagUtils.createBitmapFromDrawable(getApplicationContext(), 1, 1,  getPlayerIconBitmap(), Color.WHITE, Color.TRANSPARENT));
            return contentView;
        }catch(Exception ex) {
            Timber.e( ex);
        }
        return null;
    }

    public ApplicationInfo getApplicationInfo(String packageName) {
        ApplicationInfo ai;
        try {
            ai = getPackageManager().getApplicationInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            ai = null;
        }
        return ai;
    }

    public Bitmap getPlayerIconBitmap() {
        if(playerInfo!=null) {
            return playerInfo.playerIconBitmap;
        }
        return null;
    }

    public Drawable getPlayerIconDrawable() {
        if(playerInfo!=null) {
            return playerInfo.playerIconDrawable;
        }
        return null;
    }

    public String getPlayerName() {
        if(playerInfo!=null) {
            return playerInfo.playerName;
        }
        return "UNKNOWN Player";
    }

    private NotificationCompat.Builder createCustomNotificationBuilder(Context context) {

        return new NotificationCompat.Builder(context, mChannel.getId())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setShowWhen(true)
                .setOngoing(true)
                .setGroup(mChannel.getId())
                .setGroupSummary(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
               // .setChannelId(CHANNEL_ID)
               // .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setAutoCancel(false);
    }

    private void displayNotification(Context context, Notification notification) {
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification);
     //   }else {
      //      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
      //      notificationManager.notify(NOTIFICATION_ID, notification);
       // }
    }

    public static String getSubtitle(String album, String artist) {
        String title;
        if(StringUtils.isEmpty(artist) && StringUtils.isEmpty(album)) {
            title = "Tab to open on Music Mate...";
        }else if(StringUtils.isEmpty(artist)){
            title = album;
        }else if(StringUtils.isEmpty(album)){
            title = artist;
        }else {
            title = artist+ StringUtils.ARTIST_SEP+album;
        }
        return title;
    }

    public void playNextSong() {
        if(playerInfo==null) {
            return;
        }

        if(Preferences.isVibrateOnNextSong(getApplicationContext())) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
				long[] pattern = {10, 40, 10, 40,10 };
                // Vibrate for 500 milliseconds
               // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    //vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)); 
					vibrator.vibrate(VibrationEffect.createWaveform(pattern, VibrationEffect.DEFAULT_AMPLITUDE));
               // } else {
                    //deprecated in API 26
                    //vibrator.vibrate(100);
			//		vibrator.vibrate(pattern, -1);
              //  }
            } catch (Exception ex) {
                Timber.e(ex);
            }
        }

        if(ListeningReceiver.PACKAGE_NEUTRON.equals(playerInfo.playerPackage)) {
            // Neutron MP use
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
        }else if(ListeningReceiver.PACKAGE_POWERAMP.equals(playerInfo.playerPackage)) {
			// call PowerAmp API
			//PowerampAPIHelper.startPAService(this, new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
			Intent intent = new Intent(PAAPI_ACTION_API_COMMAND).putExtra(PAAPI_COMMAND, PAAPI_COMMAND_NEXT);
			intent.setComponent(PAAPI_PLAYER_SERVICE_COMPONENT_NAME);
            context.startForegroundService(intent);
		}else if(ListeningReceiver.PACKAGE_UAPP.equals(playerInfo.playerPackage) ||
                ListeningReceiver.PACKAGE_FOOBAR2000.equals(playerInfo.playerPackage) ||
                ListeningReceiver.PREFIX_VLC.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
             KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
             audioManager.dispatchMediaKeyEvent(event);
			//long eventTime = SystemClock.uptimeMillis();
		    //audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
		    //audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (MusicMateNotificationListener.HIBY_MUSIC.equals(playerInfo.playerPackage)) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            long eventTime = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0));
        }else if (Preferences.isUseMediaButtons(getApplicationContext())) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
            sendOrderedBroadcast(i, null);

            i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT));
            sendOrderedBroadcast(i, null);
        } else {
            // used for most player
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
            audioManager.dispatchMediaKeyEvent(event);
        }
    }

    public AudioTag getPlayingSong() {
        return playingSong;
    }

    /*
    public AudioTag getPrvPlayingSong() {
        return prvPlayingSong;
    } */

    private void populatePlayingSong(String currentTitle, String currentArtist, String currentAlbum) {
        AudioFileRepository provider = AudioFileRepository.getInstance(getApplication());
      //  prvPlayingSong = playingSong;
        playingSong = null;
        if(provider!=null) {
            try {
                playingSong = provider.findMediaItem(currentTitle, currentArtist, currentAlbum);
            } catch (Exception ex) {
                Timber.e( ex);
            }
        }
    }

    public void finishNotification() {
       // if(builder!=null) {
       //     builder.setOngoing(false);
       //     displayNotification(context, builder.build());
       // }else {
           // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(true);
           // }else {
           //     NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
           //     notificationManager.cancel(NOTIFICATION_ID);
           // }
       // }
    }

    protected void sendBroadcast(){
        // Fire the broadcast with intent packaged
        /*
        Intent intent = new Intent(ACTION);
        // Add extras to the bundle
        intent.putExtra("resultCode", Activity.RESULT_OK);
        if(playingSong != null) {
            intent.putExtra(Constants.KEY_MEDIA_TAG, playingSong);
        }
        if(prvPlayingSong != null) {
            intent.putExtra(Constants.KEY_MEDIA_PRV_TAG, prvPlayingSong);
        }
        intent.putExtra("command", "playing");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
*/
        BroadcastData data = new BroadcastData()
                .setAction(BroadcastData.Action.PLAYING)
                .setStatus(BroadcastData.Status.IN_PROGRESS)
                .setTagInfo(playingSong)
                .setMessage("playing "+playingSong.getTitle());
        Intent intent = data.getIntent();
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    protected void setPlayerInfo(MusicPlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
    }

    public void playNextSongOnMatched(AudioTag item) {
        if(item.equals(getPlayingSong())) {
            playNextSong();
        }
    }
}
