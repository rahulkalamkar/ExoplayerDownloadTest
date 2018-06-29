package com.android.epressplaylibrary;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import com.intertrust.wasabi.ErrorCodeException;
import com.intertrust.wasabi.Runtime;
import com.intertrust.wasabi.media.PlaylistProxy;
import com.intertrust.wasabi.media.PlaylistProxy.MediaSourceParams;
import com.intertrust.wasabi.media.PlaylistProxy.MediaSourceType;
import com.intertrust.wasabi.media.PlaylistProxyListener;
import com.intertrust.wasabi.media.MediaDownload;

/*
 * this enum simply maps the media types to the mimetypes required for the playlist proxy
 */
enum ContentTypes {
    DASH("application/dash+xml"), HLS("application/vnd.apple.mpegurl"), PDCF(
            "video/mp4"), M4F("video/mp4"), DCF("application/vnd.oma.drm.dcf"), BBTS(
            "video/mp2t");
    String mediaSourceParamsContentType = null;

    private ContentTypes(String mediaSourceParamsContentType) {
        this.mediaSourceParamsContentType = mediaSourceParamsContentType;
    }

    public String getMediaSourceParamsContentType() {
        return mediaSourceParamsContentType;
    }
}

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new MBB_Playback_Fragment()).commit();
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class MBB_Playback_Fragment extends Fragment implements PlaylistProxyListener {

        private PlaylistProxy playerProxy;
        static final String TAG = "SampleBBPlayer";

        static final boolean dualDownload = false;

        public MBB_Playback_Fragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(
                    R.layout.fragment_marlin_broadband_example, container,
                    false);

			/*
             * Create a VideoView for playback
			 */
            final VideoView videoView = (VideoView) rootView
                    .findViewById(R.id.videoView);
            MediaController mediaController = new MediaController(
                    getActivity(), false);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);

            try {
                /*
                 * Initialize the Wasabi Runtime (necessary only once for each
				 * instantiation of the application)
				 *
				 * ** Note: Set Runtime Properties as needed for your
				 * environment
				 */
                Runtime.initialize(getActivity().getDir("wasabi", MODE_PRIVATE)
                        .getAbsolutePath());
                /*
                 * Personalize the application (acquire DRM keys). This is only
				 * necessary once each time the application is freshly installed
				 *
				 * ** Note: personalize() is a blocking call and may take long
				 * enough to complete to trigger ANR (Application Not
				 * Responding) errors. In a production application this should
				 * be called in a background thread.
				 */
                if (!Runtime.isPersonalized())
                    Runtime.personalize();

            } catch (NullPointerException e) {
                return rootView;
            } catch (ErrorCodeException e) {
                // Consult WasabiErrors.txt for resolution of the error codes
                Log.e(TAG, "runtime initialization or personalization error: "
                        + e.getLocalizedMessage());
                return rootView;
            } catch (Exception e) {
                Log.e(TAG, "new : "
                        + e.getLocalizedMessage());
                return rootView;
            }

			/*
             * Acquire a Marlin Broadband License. The license is acquired using
			 * a License Acquisition token. Such tokens for sample content can
			 * be obtained from http://content.intertrust.com/express/ and in
			 * this example are stored in the Android project /assets directory
			 * using the filename "license-token.xml".
			 *
			 * For instance, you can download such a token from
			 * http://content-access.intertrust-dev.com/Dash_OnDemand_Subtitle/bb, and save it
			 * to the assets directory as license-token.xml"
			 *
			 * *** Note: processServiceToken() is a blocking call and may take
			 * long enough to complete to trigger ANR (Application Not
			 * Responding) errors. In a production application this should be
			 * called in a background thread.
			 */
            String licenseAcquisitionToken = getActionTokenFromAssets("license-token.xml");
            if (licenseAcquisitionToken == null) {
                Log.e(TAG,
                        "Could not find action token in the assets directory - exiting");
                return rootView;
            }
            long start = System.currentTimeMillis();
            try {
                Runtime.processServiceToken(licenseAcquisitionToken);
                Log.i(TAG,
                        "License successfully acquired in (ms): "
                                + (System.currentTimeMillis() - start));
            } catch (ErrorCodeException e1) {
                Log.e(TAG,
                        "Could not acquire the license from the license acquisition token - exiting\n" + e1);
                return rootView;
            }

			/*
             * create a playlist proxy instance for later playback and start it
			 */
            try {
                EnumSet<PlaylistProxy.Flags> flags = EnumSet.noneOf(PlaylistProxy.Flags.class);
                playerProxy = new PlaylistProxy(flags, this, new Handler());
                playerProxy.start();
            } catch (ErrorCodeException e) {
                // Consult WasabiErrors.txt for resolution of the error codes
                Log.e(TAG, "playlist proxy error: " + e.getLocalizedMessage());
                return rootView;
            }

			/*
             * Acquire a DASH On-Demand media stream URL encrypted with the key delivered in
			 * the above license.
			 * For instance: http://content-access.intertrust-dev.com/content/onDemandprofile/Frozen-OnDemand/stream.mpd
			 */

            String dash_url = "http://85mum-content.hungama.com/1526/FF-2013-00000479/stream.mpd";


            /*
             * Setup download dirs and paths
             */
            final String downloadDir1 = "dlDir1";
            final String downloadDir2 = "dlDir2";
            final String dlDirPath1 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + downloadDir1;
            final String dlDirPath2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + downloadDir2;


            /*
             * Create  MediaDownload singleton
             */

            MediaDownload _mediaDownload;
            try {
                _mediaDownload = new MediaDownload();
            } catch (ErrorCodeException e) {
                e.printStackTrace();
                return rootView;
            }
            final MediaDownload mediaDownload = _mediaDownload;

            /*
            * determine if a resumable download is found
             */
            boolean resumableDownloadFound = false;
            try {
                MediaDownload.Status status = mediaDownload.queryStatus(); //structure
                MediaDownload.State state = status.state; //paused or running
                String[] path = status.path;
                for (String pathItem : path) {
                    MediaDownload.ContentStatus contentStatus = mediaDownload.queryContentStatus(pathItem);
                    MediaDownload.ContentState contentState = contentStatus.content_state;
                    int percentage = contentStatus.downloaded_percentage;
                    Log.i(TAG, pathItem + " in state " + contentState + " at % " + percentage);
                    if (state == MediaDownload.State.PAUSED
                            && contentState == MediaDownload.ContentState.PENDING
                            && pathItem.contains(downloadDir1)) {
                        resumableDownloadFound = true;
                        Log.i(TAG, "resumable download found in dlDir1");
                        break;
                    }
                }
            } catch (ErrorCodeException e) {
                e.printStackTrace();
            }

            /*
             * setup a simple MediaDownload object
             * we'll use one single content item for both downloads
             */
            MediaDownload.Constraints constraints = new MediaDownload.Constraints();
            // set some download parameters
            constraints.max_bandwidth_bps = 20 * 1024 * 1024;
            //use 2 connections for parallel download
            constraints.max_connections = 2;
            try {
                mediaDownload.setConstraints(constraints);
            } catch (ErrorCodeException e) {
                e.printStackTrace();
            }

            final MediaDownload.DashContent content = new MediaDownload.DashContent();
            //Specify the tracks/subtitles to download - note these are specific to the example
            //DASH URL above
            String[] tracks = {"video/avc1", "audio/und/mp4a"};
            content.track = tracks;
            content.media_file_name = "mydownload-media.m4f";
            content.url = dash_url;
            content.type = MediaDownload.SourceType.DASH;


            // a progress bar for one of the media
            final ProgressDialog progressDialog = new ProgressDialog(this.getActivity());
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);

            /*
             * if resumable download found, and not dual download case,
             *  offer choice of resuming or cleaning up
             */
            if (resumableDownloadFound && !dualDownload) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Resume Download ?");
                String dialogMessage = "A previously started Download to dlDir1 (possibly others) is Pending. " +
                        "Click OK to Resume Any Pending Downloads, or Cancel to Clear all Downloads)";
                builder.setMessage(dialogMessage);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                            mediaDownload.resume();
                            progressDialog.setMessage("Resuming Download.... to " + dlDirPath1);
                            progressDialog.show();
                        } catch (ErrorCodeException e) {
                            e.printStackTrace();
                        }
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
//                      cleanup previous downloads
                        cleanup(mediaDownload, content, dlDirPath1, dlDirPath1);
                        Toast.makeText(getActivity(), "All Downloads Canceled - Please Kill and Restart App", Toast.LENGTH_LONG).show();
                    }
                });
                builder.show();
            }

                /*
                 * define the single listener for the MediaDownload object
                 * The listener will start the playback using the Playlist Proxy once the
                 * first of the download is complete
                 */
            try {
                mediaDownload.setListener(new MediaDownload.Listener() {

                    @Override
                    public void state(MediaDownload.State state) {
                        Log.i(TAG, "Received State Update: " + state);
                    }

                    @Override
                    public void progress(final MediaDownload.ContentStatus contentStatus) {
                        Handler handler = new Handler(Looper.getMainLooper());
                        // playback of downloaded content starts at this percentage
                        int percentageStartPlay = 100;

                        if (contentStatus.content_state == MediaDownload.ContentState.FAILING) {
                            Log.i(TAG, "Media Download Failing on: " + contentStatus.path);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), "Media Download Failing: " + contentStatus.path, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        if (contentStatus.path.contains(downloadDir1)) {
                            int progress = contentStatus.downloaded_percentage;
                            progressDialog.setProgress(progress);
                        }

                        if (contentStatus.downloaded_percentage % 20 == 0
                                && contentStatus.downloaded_percentage < 100) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), "Media Download on " + contentStatus.path + " : " + contentStatus.downloaded_percentage + " Complete", Toast.LENGTH_SHORT).show();
                                }
                            });
                            Log.i(TAG, "Media Download on " + contentStatus.path + " : " + contentStatus.downloaded_percentage + " Complete");
                        }

                        if (contentStatus.content_state == MediaDownload.ContentState.COMPLETED) {
                            Log.i(TAG, "Media Download Complete on: " + contentStatus.path);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.cancel();
                                    Toast.makeText(getActivity(), "Media Download Complete on: " + contentStatus.path, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        if (contentStatus.downloaded_percentage == percentageStartPlay
                                && contentStatus.path.contains(downloadDir1)) {
                            //playback the 1st of the content downloads
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    /*
                                     * initialize and start Playlist Proxy
                                     */
                                    String dlFileUrl = "file://" + contentStatus.path + "/" + content.media_file_name;
                                    String subtitleUrl = "file://" + contentStatus.path + "/" + content.subtitles_file_name;
                                    ContentTypes contentType = ContentTypes.M4F;
                                    MediaSourceParams params = new MediaSourceParams();
                                    params.sourceContentType = contentType
                                            .getMediaSourceParamsContentType();
                                    if (subtitleUrl != null) {
                                        params.subtitleUrl = subtitleUrl;
                                        params.subtitleLang = "default";
                                        params.subtitleName = "default subtitle";
                                    }
                                    String contentTypeValue = contentType.toString();
                                    MediaSourceType mediaSourceType = MediaSourceType.valueOf(
                                            (contentTypeValue.equals("HLS") || contentTypeValue.equals("DASH")) ? contentTypeValue : "SINGLE_FILE");
                                    try {
                                        String proxy_url = playerProxy.makeUrl(dlFileUrl, mediaSourceType, params);
                                        videoView.setVideoURI(Uri.parse(proxy_url));
                                        progressDialog.cancel();
                                        videoView.start();
                                    } catch (Exception e) {
                                        // Consult WasabiErrors.txt for resolution of the error codes
                                        Log.e(TAG, "playback error: " + e.getLocalizedMessage());
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    }
                });


                /*
                 * start the downloads if no resumable download was found or it is a dual download case
                 */
                if (!resumableDownloadFound || dualDownload) {
                    //cleanup just in case...
                    cleanup(mediaDownload, content, dlDirPath1, dlDirPath1);

                    //start the 1st media download with progress bar
                    mediaDownload.resume();
                    mediaDownload.addContent(dlDirPath1, content);
                    progressDialog.setMessage("Downloading.... to " + dlDirPath1);
                    progressDialog.show();

                    //start another download of same media to another path, delayed for 5 seconds
                    if (dualDownload) {
                        HandlerThread handlerThread = new HandlerThread("downloaderThread");
                        handlerThread.start();
                        Handler handler = new Handler((handlerThread.getLooper()));
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mediaDownload.addContent(dlDirPath2, content);
                                } catch (ErrorCodeException e) {
                                    e.printStackTrace();
                                }
                            }
                        }, 5000);
                    }
                }
            } catch (ErrorCodeException e) {
                e.printStackTrace();
            }

            return rootView;

        }

        /**************************************
         * Helper methods to avoid cluttering *
         **************************************/

        void cleanup(MediaDownload mediaDownload, MediaDownload.DashContent content,
                     String dlDirPath1, String dlDirPath2) {
            //  cleanup previous downloads
            String[] pathItems = {dlDirPath1, dlDirPath2};
            for (String pathItem : pathItems) {
                try {
                    File mediaFile = new File(pathItem + "/" + content.media_file_name);
                    if (mediaFile.exists()) {
                        mediaFile.delete();
                        Log.i(TAG, "deleted file: " + mediaFile.getAbsolutePath());
                    }
                    File subtitleFile = new File(pathItem + "/" + content.subtitles_file_name);
                    if (subtitleFile.exists()) {
                        subtitleFile.delete();
                        Log.i(TAG, "deleted file: " + subtitleFile.getAbsolutePath());
                    }
                    MediaDownload.Status status = mediaDownload.queryStatus();
                    String[] paths = status.path;
                    if (paths != null) {
                        List<String> pathList = Arrays.asList(paths);
                        for (String item : pathList) {
                            MediaDownload.ContentStatus contentStatus = mediaDownload.queryContentStatus(item);
                            mediaDownload.cancelContent(item);
                            Log.i(TAG, "canceling path " + item);
                        }
                    }
                } catch (ErrorCodeException e) {
                    e.printStackTrace();
                }
            }
        }

        /*
         * Read an action token file from the assets directory
         */
        protected String getActionTokenFromAssets(String tokenFileName) {
            String token = null;
            byte[] readBuffer = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = null;
            int bytesRead = 0;

            try {
                is = getActivity().getAssets()
                        .open(tokenFileName, MODE_PRIVATE);
                while ((bytesRead = is.read(readBuffer)) != -1) {
                    baos.write(readBuffer, 0, bytesRead);
                }
                baos.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            token = new String(baos.toByteArray());
            return token;
        }

        public void onErrorNotification(int errorCode, String errorString) {
            Log.e(TAG, "PlaylistProxy Event: Error Notification, error code = " +
                    Integer.toString(errorCode) + ", error string = " +
                    errorString);
        }
    }
}
