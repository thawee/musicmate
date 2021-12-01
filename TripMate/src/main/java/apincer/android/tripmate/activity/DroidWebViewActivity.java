package apincer.android.tripmate.activity;



import apincer.android.tripmate.extra.AlertMessage;
import apincer.android.tripmate.extra.AllConstants;
import apincer.android.tripmate.extra.SharedPreferencesHelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebSettings.PluginState;
import android.widget.ProgressBar;

public class DroidWebViewActivity extends Activity {
	/** Called when the activity is first created. */
	Context con;
	private WebView fweBview;
	private WebSettings webSettings;
	ProgressBar progressBar;

	String url = AllConstants.webUrl;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(apincer.android.tripmate.R.layout.webview);
		con = this;
		progressBar = (ProgressBar) findViewById(apincer.android.tripmate.R.id.progressBar12);
		

		
		try {
			if (!SharedPreferencesHelper.isOnline(con)) {
				AlertMessage.showMessage(con, "", "No internet connection");
				return;
			}
			updateWebView(url);
			
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	private class HelloWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			// TODO Auto-generated method stub
			super.onPageFinished(view, url);

			progressBar.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && fweBview.canGoBack()) {
			fweBview.goBack();
			return true;
		}
		return super.onKeyDown(keyCode, event);

	}

	private void updateWebView(String url) {
		// TODO Auto-generated method stub
		
		fweBview = (WebView) findViewById(apincer.android.tripmate.R.id.fbwebView);
		fweBview.getSettings().setJavaScriptEnabled(true);
		fweBview.getSettings().setDomStorageEnabled(true);
		fweBview.getSettings().setPluginState(PluginState.ON);
		webSettings = fweBview.getSettings();

		webSettings.setLayoutAlgorithm(LayoutAlgorithm.NARROW_COLUMNS);
		webSettings.setBuiltInZoomControls(true);

//		fweBview.getSettings().setPluginsEnabled(true);
		fweBview.loadUrl(url);

		fweBview.setWebViewClient(new HelloWebViewClient());

	}

	public void btnHome(View v) {

		Intent next = new Intent(con, MainActivity.class);
		next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(next);

	}

	public void btnBack(View v) {
		finish();

	}
	
}