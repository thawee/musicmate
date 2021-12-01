package apincer.android.tripmate.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import apincer.android.tripmate.extra.AlertMessage;
import apincer.android.tripmate.extra.AllConstants;
import apincer.android.tripmate.extra.AllURL;
import apincer.android.tripmate.extra.CacheImageDownloader;
import apincer.android.tripmate.extra.PrintLog;
import apincer.android.tripmate.extra.SharedPreferencesHelper;
import apincer.android.tripmate.holder.AllCityDetails;
import apincer.android.tripmate.holder.AllCityReview;
import apincer.android.tripmate.holder.BiCyleDetails;
import apincer.android.tripmate.holder.DrivingDetails;
import apincer.android.tripmate.holder.WalkingDetails;
import apincer.android.tripmate.model.BicyleTime;
import apincer.android.tripmate.model.CityDetailsList;
import apincer.android.tripmate.model.DrivingTime;
import apincer.android.tripmate.model.ReviewList;
import apincer.android.tripmate.model.WalkingTime;
import apincer.android.tripmate.parser.BiCyleDetailsParser;
import apincer.android.tripmate.parser.CityDetailsParser;
import apincer.android.tripmate.parser.CityReviewParser;
import apincer.android.tripmate.parser.DrivingDetailsParser;
import apincer.android.tripmate.parser.WalkingDetailsParser;
//import com.google.android.gms.ads.AdListener;
//import com.google.android.gms.ads.AdRequest;
//import com.google.android.gms.ads.AdView;
//import com.google.android.gms.ads.InterstitialAd;


public class ListDetailsActivity extends Activity {
	/** Called when the activity is first created. */
	//AdView adView;
	//AdRequest bannerRequest, fullScreenAdRequest;
	//InterstitialAd fullScreenAdd;
	private Context con;
	private String pos = "";
	private TextView cName, cAdd,bDetails,wDetails,dDetails, cPhone,textDis;
	private CacheImageDownloader downloader;
	private Bitmap defaultBit;
	private ProgressDialog pDialog;
	private CityDetailsList CD;
	private DrivingTime DT;
	private BicyleTime BT;
	private WalkingTime WT;
	private RatingBar detailsRat;
	private RestaurantAdapter adapter;
	private ListView list;
	private String Demourl = "https://maps.googleapis.com/maps/api/place/details/json?reference=CnRkAAAAj2VWwXQIX-TFfx6XaexF9rN6Kc005BMP8h0V2pKj7IuyLPWUBCt7gnHr8q9RYWeIva06HuChuwhxsio4f7c9s5aLynGzzX19Oatq8Q9Oz8w2Zj54B8PUNgDNcQ6rHKuKmpAPJBXitOcAYugvPZshDBIQsYMRaNz0n5VfpHx6C2GCFRoUsCD2Zx0P_a-rqyxHN-GTC1QZz2U&sensor=true&key=AIzaSyC6zHflVgVCLKEMWBFMFm5qj0Jis-eoR4U";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
//		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(apincer.android.tripmate.R.layout.newdetailslayout);

		getActionBar().setDisplayShowTitleEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setTitle("Details");


		con = this;
		//enableAd();
		initUI();
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id) {


			case android.R.id.home:
				this.finish();
				return true;

			case apincer.android.tripmate.R.id.share_update:
				ShareIntent();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}

	}












/*

	private void enableAd() {
		// TODO Auto-generated method stub

		// adding banner add
		adView = (AdView) findViewById(apincer.android.tripmate.R.id.adView);
		bannerRequest = new AdRequest.Builder().build();
		adView.loadAd(bannerRequest);

		// adding full screen add
		fullScreenAdd = new InterstitialAd(this);
		fullScreenAdd.setAdUnitId(getString(apincer.android.tripmate.R.string.interstitial_ad_unit_id));
		fullScreenAdRequest = new AdRequest.Builder().build();
		fullScreenAdd.loadAd(fullScreenAdRequest);

		fullScreenAdd.setAdListener(new AdListener() {

			@Override
			public void onAdLoaded() {

				Log.i("FullScreenAdd", "Loaded successfully");
				fullScreenAdd.show();

			}

			@Override
			public void onAdFailedToLoad(int errorCode) {

				Log.i("FullScreenAdd", "failed to Load");
			}
		});

		// TODO Auto-generated method stub

	}
	*/
	private void initUI() {
		// TODO Auto-generated method stub
		list = (ListView) findViewById(apincer.android.tripmate.R.id.reviewListView);
		list.setOnTouchListener(new ListView.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getAction();
				switch (action) {
					case MotionEvent.ACTION_DOWN:
						// Disallow ScrollView to intercept touch events.
						v.getParent().requestDisallowInterceptTouchEvent(true);
						break;

					case MotionEvent.ACTION_UP:
						// Allow ScrollView to intercept touch events.
						v.getParent().requestDisallowInterceptTouchEvent(false);
						break;
				}

				// Handle ListView touch events.
				v.onTouchEvent(event);
				return true;
			}
		});
		cName = (TextView) findViewById(apincer.android.tripmate.R.id.cName);
		cAdd = (TextView) findViewById(apincer.android.tripmate.R.id.cAddress);
		cPhone = (TextView) findViewById(apincer.android.tripmate.R.id.cPhone);
		wDetails = (TextView) findViewById(apincer.android.tripmate.R.id.walkD);
		bDetails = (TextView) findViewById(apincer.android.tripmate.R.id.bycleD);
		dDetails = (TextView) findViewById(apincer.android.tripmate.R.id.driveD);
		textDis = (TextView) findViewById(apincer.android.tripmate.R.id.textD);
		detailsRat = (RatingBar) findViewById(apincer.android.tripmate.R.id.detailsRating);
		downloader = new CacheImageDownloader();
		defaultBit = BitmapFactory.decodeResource(getResources(),
				apincer.android.tripmate.R.drawable.not_found_banner);

		updateUI();

	}

	/****
	 * 
	 * update wrestler info
	 * 
	 */

	private void updateUI() {
		if (!SharedPreferencesHelper.isOnline(con)) {
			AlertMessage.showMessage(con, "Error", "No internet connection");
			return;
		}

		pDialog = ProgressDialog.show(this, "", "Loading..", false, false);

		final Thread d = new Thread(new Runnable() {

			public void run() {
				// TODO Auto-generated method stub

				try {
					if (CityDetailsParser.connect(con, AllURL
							.cityGuideDetailsURL(AllConstants.referrence,
									AllConstants.apiKey))) {
					}

				} catch (final Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}






				try {
					if (DrivingDetailsParser.connect(con, AllURL
							.drivingURL(AllConstants.UPlat, AllConstants.UPlng, AllConstants.Dlat, AllConstants.Dlng,
									AllConstants.apiKey))) {
					}

				} catch (final Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}


				try {
					if (BiCyleDetailsParser.connect(con, AllURL
							.bicycleURL(AllConstants.UPlat, AllConstants.UPlng, AllConstants.Dlat, AllConstants.Dlng,
									AllConstants.apiKey))) {
					}

				} catch (final Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}

				try {
					if (WalkingDetailsParser.connect(con, AllURL
							.walkURL(AllConstants.UPlat, AllConstants.UPlng, AllConstants.Dlat, AllConstants.Dlng,
									AllConstants.apiKey))) {
					}

				} catch (final Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}




				try {
					if (CityReviewParser.connect(con, AllURL
							.cityGuideDetailsURL(AllConstants.referrence,
									AllConstants.apiKey))) {

						PrintLog.myLog("Size of City : ", AllCityReview
								.getAllCityReview().size()
								+ "");

					}

				} catch (final Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}

				runOnUiThread(new Runnable() {

					public void run() {
						// TODO Auto-generated method stub
						if (pDialog != null) {
							pDialog.cancel();
						}
//						Typeface title = Typeface.createFromAsset(getAssets(),
//								"fonts/ROBOTO-REGULAR.TTF");
//						Typeface add = Typeface.createFromAsset(getAssets(),
//								"fonts/ROBOTO-LIGHT.TTF");
						try {

							CD = AllCityDetails.getAllCityDetails()
									.elementAt(0);


							try {
//								getActionBar().setTitle("Custom Text");
								cName.setText(CD.getName().trim());
//								cName.setTypeface(title);
							} catch (Exception e) {
								// TODO: handle exception
							}
							try {

								cAdd.setText(CD.getFormatted_address().trim());
//								cAdd.setTypeface(add);
							} catch (Exception e) {
								// TODO: handle exception
							}
							try {

								cPhone.setText(CD.getFormatted_phone_number().trim());
//								cPhone.setTypeface(add);
							} catch (Exception e) {
								// TODO: handle exception
							}

							try {

								AllConstants.lat = CD.getLat().trim();

								AllConstants.lng = CD.getLng().trim();
								PrintLog.myLog("GEO", AllConstants.lat);
							} catch (Exception e) {
								// TODO: handle exception
							}



							PrintLog.myLog("DDDLatLng : ",AllConstants.UPlat+" "+AllConstants.UPlng+" "+AllConstants.lat+" "+AllConstants.lng+" "
							+AllConstants.Dlat+" "+AllConstants.Dlng);


							// ------Rating ---

							String rating = CD.getRating();

							try {

								Float count = Float.parseFloat(rating);
								// PrintLog.myLog("Rating as float", count +
								// "");
								detailsRat.setRating(count);

							} catch (Exception e) {

								PrintLog.myLog("error at rating", e.toString());
							}


                                  //	Distance

							try {

								DT = DrivingDetails.getAlldrivingdetails()
										.elementAt(0);

								dDetails.setText(DT.getTime().trim());


								// ------Rating ---


							} catch (Exception e) {
								// TODO: handle exception
							}
							try {

								textDis.setText(DT.getDistance().trim());


								// ------Rating ---


							} catch (Exception e) {
								// TODO: handle exception
							}




							try {

								BT = BiCyleDetails.getAllBicyledetails()
										.elementAt(0);

								bDetails.setText(BT.getTime().trim());


								// ------Rating ---


							} catch (Exception e) {
								// TODO: handle exception
							}	try {

								WT = WalkingDetails.getAllWalkingDetails()
										.elementAt(0);

								wDetails.setText(WT.getTime().trim());


								// ------Rating ---


							} catch (Exception e) {
								// TODO: handle exception
							}

							// ---Photo---
							try {

								String imgUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photoreference="
										+ AllConstants.photoReferrence
										+ "&sensor=true&key="
										+ AllConstants.apiKey;
								PrintLog.myLog("imgUrl", imgUrl);

								final ImageView lImage = (ImageView) findViewById(apincer.android.tripmate.R.id.imageViewL);

								if (AllConstants.photoReferrence.length() != 0) {

									downloader.download(imgUrl.trim(), lImage);

								}

								else {
									lImage.setImageBitmap(defaultBit);

									// downloader.download(AllConstants.detailsiconUrl.trim(),
									// lImage);
								}

							} catch (Exception e) {
								// TODO: handle exception
							}

						} catch (Exception e) {
							// TODO: handle exception
						}
						if (AllCityReview.getAllCityReview().size() == 0) {

						} else {

							adapter = new RestaurantAdapter(con);
							list.setAdapter(adapter);
							adapter.notifyDataSetChanged();
						}

					}
				});

			}
		});
		d.start();
	}

	class RestaurantAdapter extends ArrayAdapter<ReviewList> {
		private final Context con;

		public RestaurantAdapter(Context context) {
			super(context, apincer.android.tripmate.R.layout.review, AllCityReview.getAllCityReview());
			con = context;
			// TODO Auto-generated constructor stub

		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				final LayoutInflater vi = (LayoutInflater) con
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(apincer.android.tripmate.R.layout.review, null);
			}

			if (position < AllCityReview.getAllCityReview().size()) {
				final ReviewList CM = AllCityReview.getReviewList(position);

//				Typeface add = Typeface.createFromAsset(getAssets(),
//						"fonts/ROBOTO-LIGHT.TTF");

				// ----Address----

				final TextView address = (TextView) v
						.findViewById(apincer.android.tripmate.R.id.AuthorName);

				try {
					address.setText(CM.getAuthor_name().toString().trim());
//					address.setTypeface(add);
				} catch (Exception e) {
					// TODO: handle exception
				}

				// final RatingBar rRating = (RatingBar)
				// v.findViewById(R.id.rRatingBar);
				try {

					String reviewRating = CM.getAuthor_rating();

					try {

						Float rcount = Float.parseFloat(reviewRating);
						// PrintLog.myLog("Rating as float", count +
						// "");
						// rRating.setRating(rcount);

					} catch (Exception e) {

						PrintLog.myLog("rRating:", reviewRating);
					}
				} catch (Exception e) {
					// TODO: handle exception
				}

				// ---Image---

				final TextView name = (TextView) v.findViewById(apincer.android.tripmate.R.id.reView);
				try {
					name.setText(CM.getAuthor_text().toString().trim());
//					name.setTypeface(add);
				} catch (Exception e) {
					// TODO: handle exception
				}

			}

			// TODO Auto-generated method stub
			return v;
		}

	}

	private void call() {
		try {
			Intent callIntent = new Intent(Intent.ACTION_CALL);
			callIntent.setData(Uri.parse("tel:" + AllConstants.cCell + ""));
			startActivity(callIntent);
		} catch (ActivityNotFoundException activityException) {

		}
	}

	public void cPhone(View v) {
		if (CD.getFormatted_phone_number().length() != 0) {
			try {

				call();

				AllConstants.cCell = CD.getFormatted_phone_number().trim();

				PrintLog.myLog("Tel::", AllConstants.cCell);

			} catch (Exception e) {
				// TODO: handle exception
			}

		} else {

			Toast.makeText(ListDetailsActivity.this, "Sorry!No Phone Number Found.",
					Toast.LENGTH_LONG).show();
		}

	}

	public void webView(View v) {

		if (CD.getWebsite().length() != 0) {
			try {

				AllConstants.webUrl = CD.getWebsite().trim();
				Intent next = new Intent(con, DroidWebViewActivity.class);
				next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(next);
				PrintLog.myLog("Website::", AllConstants.cWeb);
			} catch (Exception e) {
				// TODO: handle exception
			}

		} else {

			Toast.makeText(ListDetailsActivity.this, "Sorry!No URL Found.",
					Toast.LENGTH_LONG).show();
		}

	}
	private void ShareIntent() {
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_TEXT, "This is my text to Share.");
		sendIntent.setType("text/plain");
		startActivity(sendIntent);
	}
	public void mapViewBtn(View v) {

		Intent next = new Intent(con, MapViewActivity.class);
		next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(next);

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
