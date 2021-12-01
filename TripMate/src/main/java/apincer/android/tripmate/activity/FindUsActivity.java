package apincer.android.tripmate.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;


import apincer.android.tripmate.extra.AllConstants;

public class FindUsActivity extends Activity {
    private Context con;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(apincer.android.tripmate.R.layout.find_us);
        con = this;
        getActionBar().setDisplayShowTitleEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle("Find us");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main2, menu);
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
            default:
                return super.onOptionsItemSelected(item);
        }

    }


    // .............Top Bar Details Change--------------//

    public void btnFacebook(View v) {
        AllConstants.webUrl = "https://www.facebook.com/nsniteshsahni";

        Intent next = new Intent(con, DroidWebViewActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
    }

    public void btnYoutube(View v) {
        AllConstants.webUrl = "https://www.youtube.com/channel/UCOhHO2ICt0ti9KAh-QHvttQ";

        Intent next = new Intent(con, DroidWebViewActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
    }

    public void btnLinkedin(View v) {
        AllConstants.webUrl = "https://www.linkedin.com/in/nitesh-sahni-1b8226a3";

        Intent next = new Intent(con, DroidWebViewActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
    }

    public void btnGoogleplus(View v) {
        AllConstants.webUrl = "https://plus.google.com/u/0/+NiteshSahni007/posts";

        Intent next = new Intent(con, DroidWebViewActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
    }

    public void btnTwitter(View v) {
        AllConstants.webUrl = "https://twitter.com/nsniteshsahni";

        Intent next = new Intent(con, DroidWebViewActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
    }

    public void btnPinterest(View v) {
        AllConstants.webUrl = "https://github.com/nsniteshsahni";

        Intent next = new Intent(con, DroidWebViewActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
    }

}
