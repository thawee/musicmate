/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package apincer.android.tripmate.extra;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

/**
 * This helper class download images from the Internet and binds those with the
 * provided ImageView.
 * 
 * <p>
 * It requires the INTERNET permission, which should be added to your
 * application's manifest file.
 * </p>
 * 
 * A local cache of downloaded images is maintained internally to improve
 * performance.
 */
public class CacheImageDownloader {
	private static final String LOG_TAG = "CacheImageDownloader";

	private static final int HARD_CACHE_CAPACITY = 40;
	private static final int DELAY_BEFORE_PURGE = 30 * 1000; // in milliseconds

	// Hard cache, with a fixed maximum capacity and a life duration
	private final HashMap<String, Bitmap> sHardBitmapCache = new LinkedHashMap<String, Bitmap>(
			CacheImageDownloader.HARD_CACHE_CAPACITY / 2, 0.75f, true) {
		/**
			 * 
			 */
		private static final long serialVersionUID = 1L;

		
		@Override
		protected boolean removeEldestEntry(
				final Entry<String, Bitmap> eldest) {
			if (size() > CacheImageDownloader.HARD_CACHE_CAPACITY) {
				// Entries push-out of hard reference cache are transferred to
				// soft reference cache
				CacheImageDownloader.sSoftBitmapCache.put(eldest.getKey(),
						new SoftReference<Bitmap>(eldest.getValue()));
				return true;
			} else {
				return false;
			}
		}
	};

	// Soft cache for bitmap kicked out of hard cache
	private final static ConcurrentHashMap<String, SoftReference<Bitmap>> sSoftBitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>(
			CacheImageDownloader.HARD_CACHE_CAPACITY / 2);

	private final Handler purgeHandler = new Handler();

	private final Runnable purger = new Runnable() {
		
		public void run() {
			clearCache();
		}
	};

	/**
	 * Download the specified image from the Internet and binds it to the
	 * provided ImageView. The binding is immediate if the image is found in the
	 * cache and will be done asynchronously otherwise. A null bitmap will be
	 * associated to the ImageView if an error occurs.
	 * 
	 * @param url
	 *            The URL of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 * @param next
	 * @param pre
	 */
	public void download(final String url, final ImageView imageView) {

		download(url, imageView, null);
	}

	/**
	 * Same as {@link #download(String, ImageView)}, with the possibility to
	 * provide an additional cookie that will be used when the image will be
	 * retrieved.
	 * 
	 * @param url
	 *            The URL of the image to download.
	 * @param imageView
	 *            The ImageView to bind the downloaded image to.
	 * @param cookie
	 *            A cookie String that will be used by the http connection.
	 */
	public void download(final String url, final ImageView imageView,
			final String cookie) {
		resetPurgeTimer();
		final Bitmap bitmap = getBitmapFromCache(url);

		if (bitmap == null) {
			forceDownload(url, imageView, cookie);
		} else {
			CacheImageDownloader.cancelPotentialDownload(url, imageView);
			imageView.setImageBitmap(bitmap);
			imageView.setVisibility(View.VISIBLE);

		}
	}

	/*
	 * Same as download but the image is always downloaded and the cache is not
	 * used. Kept private at the moment as its interest is not clear. private
	 * void forceDownload(String url, ImageView view) { forceDownload(url, view,
	 * null); }
	 */

	/**
	 * Same as download but the image is always downloaded and the cache is not
	 * used. Kept private at the moment as its interest is not clear.
	 */
	private void forceDownload(final String url, final ImageView imageView,
			final String cookie) {
		// State sanity: url is guaranteed to never be null in
		// DownloadedDrawable and cache keys.
		if (url == null) {
			imageView.setImageDrawable(null);
			imageView.setVisibility(View.GONE);
			return;
		}

		if (CacheImageDownloader.cancelPotentialDownload(url, imageView)) {
			final BitmapDownloaderTask task = new BitmapDownloaderTask(
					imageView);
			final DownloadedDrawable downloadedDrawable = new DownloadedDrawable(
					task);
			imageView.setImageDrawable(downloadedDrawable);
			task.execute(url, cookie);
		}
	}

	/**
	 * Clears the image cache used internally to improve performance. Note that
	 * for memory efficiency reasons, the cache will automatically be cleared
	 * after a certain inactivity delay.
	 */
	public void clearCache() {
		sHardBitmapCache.clear();
		CacheImageDownloader.sSoftBitmapCache.clear();
	}

	private void resetPurgeTimer() {
		purgeHandler.removeCallbacks(purger);
		purgeHandler.postDelayed(purger,
				CacheImageDownloader.DELAY_BEFORE_PURGE);
	}

	/**
	 * Returns true if the current download has been canceled or if there was no
	 * download in progress on this image view. Returns false if the download in
	 * progress deals with the same url. The download is not stopped in that
	 * case.
	 */
	private static boolean cancelPotentialDownload(final String url,
			final ImageView imageView) {
		final BitmapDownloaderTask bitmapDownloaderTask = CacheImageDownloader
				.getBitmapDownloaderTask(imageView);

		if (bitmapDownloaderTask != null) {
			final String bitmapUrl = bitmapDownloaderTask.url;
			if (bitmapUrl == null || !bitmapUrl.equals(url)) {
				bitmapDownloaderTask.cancel(true);
			} else {
				// The same URL is already being downloaded.
				return false;
			}
		}
		return true;
	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active download task (if any) associated
	 *         with this imageView. null if there is no such task.
	 */
	private static BitmapDownloaderTask getBitmapDownloaderTask(
			final ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof DownloadedDrawable) {
				final DownloadedDrawable downloadedDrawable = (DownloadedDrawable) drawable;
				return downloadedDrawable.getBitmapDownloaderTask();
			}
		}
		return null;
	}

	/**
	 * @param url
	 *            The URL of the image that will be retrieved from the cache.
	 * @return The cached bitmap or null if it was not found.
	 */
	private Bitmap getBitmapFromCache(final String url) {
		// First try the hard reference cache
		synchronized (sHardBitmapCache) {
			final Bitmap bitmap = sHardBitmapCache.get(url);
			if (bitmap != null) {
				// Bitmap found in hard cache
				// Move element to first position, so that it is removed last
				sHardBitmapCache.remove(url);
				sHardBitmapCache.put(url, bitmap);
				return bitmap;
			}
		}

		// Then try the soft reference cache
		final SoftReference<Bitmap> bitmapReference = CacheImageDownloader.sSoftBitmapCache
				.get(url);
		if (bitmapReference != null) {
			final Bitmap bitmap = bitmapReference.get();
			if (bitmap != null) {
				// Bitmap found in soft cache
				return bitmap;
			} else {
				// Soft reference has been Garbage Collected
				CacheImageDownloader.sSoftBitmapCache.remove(url);
			}
		}

		return null;
	}

	/**
	 * The actual AsyncTask that will asynchronously download the image.
	 */
	class BitmapDownloaderTask extends AsyncTask<String, Void, Bitmap> {
		private static final int IO_BUFFER_SIZE = 4 * 1024;
		private String url;
		private final WeakReference<ImageView> imageViewReference;

		public BitmapDownloaderTask(final ImageView imageView) {
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		/**
		 * Actual download method.
		 */
		
		@Override
		protected Bitmap doInBackground(final String... params) {
			final HttpClient client = new DefaultHttpClient();
			url = params[0];
			final HttpGet getRequest = new HttpGet(url);
			final String cookie = params[1];
			if (cookie != null) {
				getRequest.setHeader("cookie", cookie);
			}

			try {
				final HttpResponse response = client.execute(getRequest);
				final int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK) {
					Log.w("CacheImageDownloader", "Error " + statusCode
							+ " while retrieving bitmap from " + url);
					return null;
				}

				final HttpEntity entity = response.getEntity();
				if (entity != null) {
					InputStream inputStream = null;
					OutputStream outputStream = null;
					try {
						inputStream = entity.getContent();
						final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
						outputStream = new BufferedOutputStream(dataStream,
								BitmapDownloaderTask.IO_BUFFER_SIZE);
						copy(inputStream, outputStream);
						outputStream.flush();

						final byte[] data = dataStream.toByteArray();
						final Bitmap bitmap = BitmapFactory.decodeByteArray(
								data, 0, data.length);

						// FIXME : Should use
						// BitmapFactory.decodeStream(inputStream) instead.
						// final Bitmap bitmap =
						// BitmapFactory.decodeStream(inputStream);

						return bitmap;

					} finally {
						if (inputStream != null) {
							inputStream.close();
						}
						if (outputStream != null) {
							outputStream.close();
						}
						entity.consumeContent();
					}
				}
			} catch (final IOException e) {
				getRequest.abort();
				Log.w(CacheImageDownloader.LOG_TAG,
						"I/O error while retrieving bitmap from " + url, e);
			} catch (final IllegalStateException e) {
				getRequest.abort();
				Log.w(CacheImageDownloader.LOG_TAG, "Incorrect URL: " + url);
			} catch (final Exception e) {
				getRequest.abort();
				Log.w(CacheImageDownloader.LOG_TAG,
						"Error while retrieving bitmap from " + url, e);
			} finally {
				if (client != null) {
					// client.close();
				}
			}
			return null;
		}

		/**
		 * Once the image is downloaded, associates it to the imageView
		 */
		
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) {
				bitmap = null;
			}

			// Add bitmap to cache
			if (bitmap != null) {
				synchronized (sHardBitmapCache) {
					sHardBitmapCache.put(url, bitmap);
				}
			}

			if (imageViewReference != null) {
				final ImageView imageView = imageViewReference.get();
				final BitmapDownloaderTask bitmapDownloaderTask = CacheImageDownloader
						.getBitmapDownloaderTask(imageView);
				// Change bitmap only if this process is still associated with
				// it
				if (this == bitmapDownloaderTask) {
					imageView.setImageBitmap(bitmap);
					imageView.setVisibility(View.VISIBLE);

				}
			}
		}

		public void copy(final InputStream in, final OutputStream out)
				throws IOException {
			final byte[] b = new byte[BitmapDownloaderTask.IO_BUFFER_SIZE];
			int read;
			while ((read = in.read(b)) != -1) {
				out.write(b, 0, read);
			}
		}
	}

	/**
	 * A fake Drawable that will be attached to the imageView while the download
	 * is in progress.
	 * 
	 * <p>
	 * Contains a reference to the actual download task, so that a download task
	 * can be stopped if a new binding is required, and makes sure that only the
	 * last started download process can bind its result, independently of the
	 * download finish order.
	 * </p>
	 */
	static class DownloadedDrawable extends ColorDrawable {
		private final WeakReference<BitmapDownloaderTask> bitmapDownloaderTaskReference;

		public DownloadedDrawable(
				final BitmapDownloaderTask bitmapDownloaderTask) {
			super(Color.BLACK);
			bitmapDownloaderTaskReference = new WeakReference<BitmapDownloaderTask>(
					bitmapDownloaderTask);
		}

		public BitmapDownloaderTask getBitmapDownloaderTask() {
			return bitmapDownloaderTaskReference.get();
		}
	}
}
