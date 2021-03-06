/**
 *
 * CobaltActivity
 * Cobalt
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Cobaltians
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.cobaltians.cobalt.activities;

import org.cobaltians.cobalt.Cobalt;
import org.cobaltians.cobalt.R;
import org.cobaltians.cobalt.customviews.ActionViewMenuItem;
import org.cobaltians.cobalt.customviews.ActionViewMenuItemListener;
import org.cobaltians.cobalt.customviews.BottomBar;
import org.cobaltians.cobalt.font.CobaltFontManager;
import org.cobaltians.cobalt.fragments.CobaltFragment;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import org.cobaltians.cobalt.pubsub.PubSub;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@link Activity} containing a {@link CobaltFragment}.
 * @author Diane
 */
public class CobaltActivity extends AppCompatActivity implements ActionViewMenuItemListener {

    protected static final String TAG = CobaltActivity.class.getSimpleName();

    // NAVIGATION
    private boolean mAnimatedTransition;
    private JSONObject mDataNavigation;

    // Pop
    protected static ArrayList<Activity> sActivitiesArrayList = new ArrayList<>();

    // Modal
    private boolean mWasPushedAsModal;
    private static boolean sWasPushedFromModal = false;

    // BARS
    protected HashMap<String, ActionViewMenuItem> mMenuItemsHashMap = new HashMap<>();
    protected HashMap<Integer, String> mMenuItemsIdMap = new HashMap<>();
    protected HashMap<String, MenuItem> mMenuItemByNameMap = new HashMap<>();
	protected CobaltFragment mMenuListener;

    /***********************************************************************************************
     *
     * LIFECYCLE
     *
     **********************************************************************************************/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(getLayoutToInflate());
        sActivitiesArrayList.add(this);

        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            bundle = new Bundle();
        }
        Bundle extras = bundle.getBundle(Cobalt.kExtras);
        if (extras == null) {
            extras = Cobalt.getInstance(this.getApplicationContext()).getConfigurationForController(getController());
            extras.putString(Cobalt.kPage, getPage());
            bundle.putBundle(Cobalt.kExtras, extras);
        }

        if (bundle.containsKey(Cobalt.kJSData)) {
            try {
                mDataNavigation = new JSONObject(bundle.getString(Cobalt.kJSData));
            } catch (JSONException e) {
                if (Cobalt.DEBUG) Log.e(Cobalt.TAG, TAG + " - onCreate: data navigation parsing failed. " + extras.getString(Cobalt.kJSData));
                e.printStackTrace();
            }
        }

        if (savedInstanceState == null) {
            applyBackgroundColor(extras.getString(Cobalt.kBackgroundColor));

            CobaltFragment fragment = getFragment();
            mMenuListener = fragment;

            if (fragment != null) {
                if (fragment.getArguments() == null)
                {
                    fragment.setArguments(extras);
                }
                mAnimatedTransition = bundle.getBoolean(Cobalt.kJSAnimated, true);

                if (mAnimatedTransition) {
                    mWasPushedAsModal = bundle.getBoolean(Cobalt.kPushAsModal, false);
                    if (mWasPushedAsModal) {
                        sWasPushedFromModal = true;
                        overridePendingTransition(R.anim.modal_open_enter, android.R.anim.fade_out);
                    }
                    else if (bundle.getBoolean(Cobalt.kPopAsModal, false)) {
                        sWasPushedFromModal = false;
                        overridePendingTransition(android.R.anim.fade_in, R.anim.modal_close_exit);
                    }
                    else if (sWasPushedFromModal) overridePendingTransition(R.anim.modal_push_enter, R.anim.modal_push_exit);
                }
                else overridePendingTransition(0, 0);
            }
            else if (Cobalt.DEBUG) {
                Log.e(Cobalt.TAG, TAG + " - onCreate: getFragment() returned null");
            }

            if (findViewById(getFragmentContainerId()) != null) {
                getSupportFragmentManager().beginTransaction().replace(getFragmentContainerId(), fragment).commit();
            }
            else if (Cobalt.DEBUG) {
                Log.e(Cobalt.TAG, TAG + " - onCreate: fragment container not found");
            }
        }

        if (extras.containsKey(Cobalt.kBars)) {
            try {
                JSONObject actionBar = new JSONObject(extras.getString(Cobalt.kBars));
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(getFragmentContainerId());
                setupBars(actionBar,    currentFragment != null && CobaltFragment.class.isAssignableFrom(currentFragment.getClass()) ?
                        (CobaltFragment) currentFragment : mMenuListener);
            }
            catch (JSONException exception) {
                setupBars(null, null);
                if (Cobalt.DEBUG) {
                    Log.e(Cobalt.TAG, TAG + " - onCreate: bars configuration parsing failed. " + extras.getString(Cobalt.kBars));
                }
                exception.printStackTrace();
            }
        }
        else {
            setupBars(null, null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Cobalt.getInstance(getApplicationContext()).onActivityStarted(this);
    }

    public void onAppStarted()
    {
        PubSub.getInstance().publishMessage(null, Cobalt.JSEventOnAppStarted);
    }

    public void onAppForeground()
    {
        Fragment fragment = getSupportFragmentManager().findFragmentById(getFragmentContainerId());
        if (fragment != null
            && CobaltFragment.class.isAssignableFrom(fragment.getClass()))
        {
            ((CobaltFragment) fragment).executeToJSWaitingCalls();
        }
        else if (Cobalt.DEBUG)
        {
            Log.i(Cobalt.TAG, TAG + " - onAppForeground: no fragment container found \n"
                              + " or fragment found is not an instance of CobaltFragment. \n"
                              + "Drop executing waiting calls.");
        }
    
        PubSub.getInstance().publishMessage(null, Cobalt.JSEventOnAppForeground);
    }

    @Override
    public void finish() {
        super.finish();

        if (mAnimatedTransition) {
            if (mWasPushedAsModal) {
                sWasPushedFromModal = false;
                overridePendingTransition(android.R.anim.fade_in, R.anim.modal_close_exit);
            } else if (sWasPushedFromModal) overridePendingTransition(R.anim.modal_pop_enter, R.anim.modal_pop_exit);
        }
        else overridePendingTransition(0, 0);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Cobalt.getInstance(getApplicationContext()).onActivityStopped(this);
    }

    public void onAppBackground()
    {
        PubSub.getInstance().publishMessage(null, Cobalt.JSEventOnAppBackground);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        sActivitiesArrayList.remove(this);
    }
    
    @Nullable
    public String getController()
    {
	    Bundle bundle = getIntent().getExtras();
	    if (bundle != null)
	    {
	        Bundle extras = bundle.getBundle(Cobalt.kExtras);
	        if (extras != null)
	        {
	            return extras.getString(Cobalt.kController);
	        }
	    }
	    
        return null;
    }
    
    @Nullable
    public String getPage()
    {
	    Bundle bundle = getIntent().getExtras();
	    if (bundle != null)
	    {
	        Bundle extras = bundle.getBundle(Cobalt.kExtras);
	        if (extras != null)
	        {
	            return extras.getString(Cobalt.kPage);
	        }
	    }
	    
        return null;
    }

    /***********************************************************************************************
     *
     * MENU
     *
     **********************************************************************************************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            bundle = new Bundle();
        }
        Bundle extras = bundle.getBundle(Cobalt.kExtras);
        if (extras == null) {
            extras = Cobalt.getInstance(getApplicationContext()).getConfigurationForController(getController());
        }
        if (extras.containsKey(Cobalt.kBars)) {
            try {
                JSONObject bars = new JSONObject(extras.getString(Cobalt.kBars));

                int colorInt = Cobalt.getInstance(this).getThemedBarIconColor(this);
                String color = bars.optString(Cobalt.kBarsColor, null);
                if (color != null) {
                    try {
                        colorInt = Cobalt.parseColor(color);
                    }
                    catch(IllegalArgumentException exception) {
                        exception.printStackTrace();
                    }
                }

                JSONArray actions = bars.optJSONArray(Cobalt.kBarsActions);
                if (actions != null) setupOptionsMenu(menu, colorInt, actions);
            }
            catch (JSONException exception) {
                if (Cobalt.DEBUG) {
                    Log.e(Cobalt.TAG, TAG + " - onCreate: action bar configuration parsing failed. " + extras.getString(Cobalt.kBars));
                }
                exception.printStackTrace();
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (mMenuItemsIdMap.containsKey(itemId)) {
            onPressed(mMenuItemsIdMap.get(itemId));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /***********************************************************************************************
     *
     * COBALT
     *
     **********************************************************************************************/

    /***********************************************************************************************
     *
     * UI
     *
     **********************************************************************************************/

	/**
	 * Returns a new instance of the contained fragment. 
	 * This method should be overridden in subclasses.
	 * @return a new instance of the fragment contained.
	 */
	protected CobaltFragment getFragment()
    {
	    return new CobaltFragment();
    }

	protected int getLayoutToInflate() {
		return R.layout.activity_cobalt;
	}

    public int getFragmentsContainerId() {
        return R.id.fragments_container;
    }

	public int getFragmentContainerId() {
		return R.id.webview_fragment_container;
	}

    public int getWebLayerFragmentContainerId() {
        return R.id.weblayer_fragment_container;
    }

    public int getTopBarId() {
        return R.id.top_bar;
    }

    public int getBottomBarId() {
        return R.id.bottom_bar;
    }

    public void setupBars(JSONObject configuration, CobaltFragment currentFragment) {
        mMenuListener = currentFragment;

        Toolbar topBar = (Toolbar) findViewById(getTopBarId());
        // TODO: use LinearLayout for bottomBar instead to handle groups
        //LinearLayout bottomBar = (LinearLayout) findViewById(getBottomBarId());
        BottomBar bottomBar = (BottomBar) findViewById(getBottomBarId());

        // TODO: make bars more flexible
        if (topBar == null
                || bottomBar == null) {
            if (Cobalt.DEBUG) Log.w(Cobalt.TAG, TAG + " - setupBars: activity does not have an action bar and/or does not contain a bottom bar.");
            return;
        }

        setSupportActionBar(topBar);
        ActionBar actionBar = getSupportActionBar();

        // Default
        if (actionBar != null) {
            actionBar.setTitle(null);
            if (sActivitiesArrayList.size() == 1) {
                actionBar.setDisplayHomeAsUpEnabled(false);
            }
            else {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        if (configuration != null) {
            // Background color
            // TODO: apply on overflow popup
            int backgroundColorInt = Cobalt.getInstance(this).getThemedBarBackgroundColor(this);
            String backgroundColor = configuration.optString(Cobalt.kBackgroundColor, null);
            if (backgroundColor != null) {
                try {
                    backgroundColorInt = Cobalt.parseColor(backgroundColor);
                }
                catch(IllegalArgumentException exception) {
                    if (Cobalt.DEBUG) {
                        Log.w(Cobalt.TAG, TAG + " - setupBars: backgroundColor " + backgroundColor + " format not supported, use (#)RGB or (#)RRGGBB(AA).");
                    }
                    exception.printStackTrace();
                }
            }
            if (actionBar != null) {
                actionBar.setBackgroundDrawable(new ColorDrawable(backgroundColorInt));
            }
            bottomBar.setBackgroundColor(backgroundColorInt);

            // Color (default: system)
            int colorInt = Cobalt.getInstance(this).getThemedBarTextColor(this);
            String color = configuration.optString(Cobalt.kBarsColor, null);
            if (color != null) {
                try {
                    colorInt = Cobalt.parseColor(color);
                }
                catch (IllegalArgumentException exception) {
                    if (Cobalt.DEBUG) {
                        Log.w(Cobalt.TAG, TAG + " - setupBars: color " + color + " format not supported, use (#)RGB or (#)RRGGBB(AA).");
                    }
                    exception.printStackTrace();
                }
            }

            // Logo
            String logo = configuration.optString(Cobalt.kBarsIcon, null);
            if (logo != null) {
                Drawable logoDrawable = null;

                int logoResId = getResourceIdentifier(logo);
                if (logoResId != 0) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            logoDrawable = getResources().getDrawable(logoResId, null);
                        } else {
                            logoDrawable = getResources().getDrawable(logoResId);
                        }

                        if (logoDrawable != null) {
                            logoDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);
                        }
                    }
                    catch(Resources.NotFoundException exception) {
                        Log.w(Cobalt.TAG, TAG + " - setupBars: " + logo + " resource not found.");
                        exception.printStackTrace();
                    }
                }
                else {
                    logoDrawable = CobaltFontManager.getCobaltFontDrawable(this, logo, colorInt);
                }
                topBar.setLogo(logoDrawable);
                if (actionBar != null) actionBar.setDisplayShowHomeEnabled(true);
            }
            else {
                if (actionBar != null) actionBar.setDisplayShowHomeEnabled(false);
            }

            // Title
            String title = configuration.optString(Cobalt.kBarsTitle, null);
            if (title != null) {
                if (actionBar != null) actionBar.setTitle(title);
            }
            else {
                if (actionBar != null) actionBar.setDisplayShowTitleEnabled(false);
            }

            // Visible
            JSONObject visible = configuration.optJSONObject(Cobalt.kBarsVisible);
            if (visible != null) setActionBarVisible(visible);

            // Up
            JSONObject navigationIcon = configuration.optJSONObject(Cobalt.kBarsNavigationIcon);
            if (navigationIcon != null) {
                boolean enabled = navigationIcon.optBoolean(Cobalt.kNavigationIconEnabled, true);
                if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(enabled);
                Drawable navigationIconDrawable = null;

                String icon = navigationIcon.optString(Cobalt.kNavigationIconIcon, null);
                if (icon != null) {
                    int iconResId = getResourceIdentifier(icon);
                    if (iconResId != 0) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                navigationIconDrawable = getResources().getDrawable(iconResId, null);
                            }
                            else {
                                navigationIconDrawable = getResources().getDrawable(iconResId);
                            }
                        }
                        catch(Resources.NotFoundException exception) {
                            Log.w(Cobalt.TAG, TAG + " - setupBars: " + logo + " resource not found.");
                            exception.printStackTrace();
                        }
                    }
                    else {
                        navigationIconDrawable = CobaltFontManager.getCobaltFontDrawable(this, icon, colorInt);
                    }
                    topBar.setNavigationIcon(navigationIconDrawable);
                }
            }

            topBar.setTitleTextColor(colorInt);

            Drawable overflowIconDrawable = topBar.getOverflowIcon();
            // should never be null but sometimes....
            if (overflowIconDrawable != null) overflowIconDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);

            Drawable navigationIconDrawable = topBar.getNavigationIcon();
            // should never be null but sometimes....
            if (navigationIconDrawable != null) navigationIconDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);
        }
    }

    protected void setupOptionsMenu(Menu menu, int color, JSONArray actions) {
        ActionBar actionBar = getSupportActionBar();
        // TODO: use LinearLayout for bottomBar instead to handle groups
        //LinearLayout bottomBar = (LinearLayout) findViewById(getBottomBarId());
        BottomBar bottomBar = (BottomBar) findViewById(getBottomBarId());

        // TODO: make bars more flexible
        if (actionBar == null
            || bottomBar == null) {
            if (Cobalt.DEBUG) {
                Log.w(Cobalt.TAG, TAG + " - setupOptionsMenu: activity does not have an action bar and/or does not contain a bottom bar.");
            }
            return;
        }

        Menu bottomMenu = bottomBar.getMenu();

        menu.clear();
        bottomMenu.clear();

        int actionId = 0;
        int menuItemsAddedToTop = 0;
        int menuItemsAddedToOverflow = 0;
        int menuItemsAddedToBottom = 0;

        int length = actions.length();
        for (int i = 0; i < length; i++) {
            try {
                JSONObject action = actions.getJSONObject(i);
                String position = action.getString(Cobalt.kActionPosition);             // must be "top", "bottom", "overflow"
                JSONArray groupActions = action.optJSONArray(Cobalt.kActionActions);

                Menu addToMenu = menu;
                int order;

                switch (position) {
                    case Cobalt.kPositionTop:
                        order = menuItemsAddedToTop++;
                        break;
                    case Cobalt.kPositionOverflow:
                        order = menuItemsAddedToOverflow++;
                        break;
                    case Cobalt.kPositionBottom:
                        order = menuItemsAddedToBottom++;
                        addToMenu = bottomMenu;
                        // TODO find a way to add same space between each actionViewItem
                        /*MenuItem spaceMenuItem = addToMenu.add(Menu.NONE, Menu.NONE, order++, "");
                        MenuItemCompat.setShowAsAction(spaceMenuItem, MenuItem.SHOW_AS_ACTION_ALWAYS);
                        spaceMenuItem.setVisible(true);
                        spaceMenuItem.setEnabled(false);*/
                        break;
                    default:
                        throw new JSONException("androidPosition attribute must be top, overflow or bottom.");
                }

                if (groupActions != null) {
                    addGroup(addToMenu, order, groupActions, actionId, position, color);
                }
                else {
                    addMenuItem(addToMenu, order, action, actionId++, position, color);
                }
            }
            catch (JSONException exception) {
                if (Cobalt.DEBUG) {
                    Log.w(Cobalt.TAG,   TAG + " - setupOptionsMenu: action " + i + " of actions array below is not an object, does not contain a position field or its value is not top, overflow or bottom.\n"
                                        + actions.toString());
                }

                exception.printStackTrace();
            }
        }

        mMenuListener = null;
    }

    protected void addGroup(Menu menu, int order, JSONArray actions, int actionId, String position, int color) {
        int length = actions.length();
        for (int i = 0; i < length; i++) {
            try {
                JSONObject action = actions.getJSONObject(i);
                addMenuItem(menu, order++, action, actionId++, position, color);
            }
            catch (JSONException exception) {
                if (Cobalt.DEBUG) {
                    Log.w(Cobalt.TAG,   TAG + " - addGroup: action " + i + " of actions array below is not an object.\n"
                                        + actions.toString());
                }
                exception.printStackTrace();
            }
        }
    }

    public void setBadgeMenuItem(String name, String badgeText) {
        if (mMenuItemsHashMap.containsKey(name)) {
            ActionViewMenuItem item = mMenuItemsHashMap.get(name);
            item.setActionBadge(badgeText);
        }
    }

    public void setContentMenuItem(String name, JSONObject content){
        if (mMenuItemsHashMap.containsKey(name)) {
            ActionViewMenuItem item = mMenuItemsHashMap.get(name);
            item.setActionContent(content);
        }
    }

    public void setActionBarVisible(JSONObject visible) {
        ActionBar actionBar = getSupportActionBar();
        BottomBar bottomBar = (BottomBar) findViewById(getBottomBarId());
        if (visible.has(Cobalt.kVisibleTop) && actionBar != null) {
            boolean top = visible.optBoolean(Cobalt.kVisibleTop);

            if (!top && actionBar.isShowing()) {
                actionBar.hide();
            }
            else if (top && !actionBar.isShowing()){
                actionBar.show();
            }
        }

        if (visible.has(Cobalt.kVisibleBottom)) {
            boolean bottom = visible.optBoolean(Cobalt.kVisibleBottom);
            if (bottom) {
                bottomBar.setVisibility(View.VISIBLE);
            }
            else bottomBar.setVisibility(View.GONE);
        }
    }

    public void setBarContent(JSONObject content) {
        Toolbar topBar = (Toolbar) findViewById(getTopBarId());
        ActionBar actionBar = getSupportActionBar();
        BottomBar bottomBar = (BottomBar) findViewById(getBottomBarId());
        int colorInt = Cobalt.getInstance(this).getThemedBarIconColor(this);

        try {
            String backgroundColor = content.optString(Cobalt.kBackgroundColor, null);
            // TODO: apply on overflow popup?
            if (backgroundColor != null) {
                int backgroundColorInt = Cobalt.parseColor(backgroundColor);
                if (actionBar != null) {
                    actionBar.setBackgroundDrawable(new ColorDrawable(backgroundColorInt));
                }
                if (bottomBar != null) {
                    bottomBar.setBackgroundColor(backgroundColorInt);
                }
            }
        }
        catch (IllegalArgumentException exception) {
            if (Cobalt.DEBUG) {
                Log.w(Cobalt.TAG, TAG + " - setBarContent: backgroundColor format not supported, use (#)RGB or (#)RRGGBB(AA).");
            }
            exception.printStackTrace();
        }

        try {
            String color = content.optString(Cobalt.kBarsColor, null);
            if (color != null) {
                colorInt = Cobalt.parseColor(color);

                if (topBar != null) {
                    topBar.setTitleTextColor(colorInt);

                    Drawable overflowIconDrawable = topBar.getOverflowIcon();
                    // should never be null but sometimes....
                    if (overflowIconDrawable != null) {
                        overflowIconDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);
                    }

                    Drawable navigationIconDrawable = topBar.getNavigationIcon();
                    // should never be null but sometimes....
                    if (navigationIconDrawable != null) {
                        navigationIconDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);
                    }
                }
            }
        }
        catch (IllegalArgumentException exception) {
            if (Cobalt.DEBUG) {
                Log.w(Cobalt.TAG, TAG + " - setupBars: color format not supported, use (#)RGB or (#)RRGGBB(AA).");
            }
            exception.printStackTrace();
        }

        String logo = content.optString(Cobalt.kBarsIcon, null);
        if (logo != null
            && ! logo.equals("")) {
            Drawable logoDrawable = null;

            int logoResId = getResourceIdentifier(logo);
            if (logoResId != 0) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        logoDrawable = getResources().getDrawable(logoResId, null);
                    }
                    else {
                        logoDrawable = getResources().getDrawable(logoResId);
                    }

                    if (logoDrawable != null) {
                        logoDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);
                    }
                }
                catch(Resources.NotFoundException exception) {
                    Log.w(Cobalt.TAG, TAG + " - setupBars: " + logo + " resource not found.");
                    exception.printStackTrace();
                }
            }
            else {
                logoDrawable = CobaltFontManager.getCobaltFontDrawable(getApplicationContext(), logo, colorInt);
            }

            if (topBar != null) {
                topBar.setLogo(logoDrawable);
            }
            if (actionBar != null) {
                actionBar.setDisplayShowHomeEnabled(true);
            }
        }
        else if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
        }

        if (content.has(Cobalt.kBarsNavigationIcon)) {
            try {
                JSONObject navigationIcon = content.getJSONObject(Cobalt.kBarsNavigationIcon);
                if (navigationIcon == null) {
                    navigationIcon = new JSONObject();
                }
                boolean enabled = navigationIcon.optBoolean(Cobalt.kNavigationIconEnabled, true);
                if (actionBar != null) {
                    actionBar.setDisplayHomeAsUpEnabled(enabled);
                }
                Drawable navigationIconDrawable = null;

                String icon = navigationIcon.optString(Cobalt.kNavigationIconIcon);
                if (icon != null) {
                    int iconResId = getResourceIdentifier(icon);
                    if (iconResId != 0) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                navigationIconDrawable = getResources().getDrawable(iconResId, null);
                            }
                            else {
                                navigationIconDrawable = getResources().getDrawable(iconResId);
                            }

                            if (navigationIconDrawable != null) {
                                navigationIconDrawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_ATOP);
                            }
                        }
                        catch(Resources.NotFoundException exception) {
                            Log.w(Cobalt.TAG, TAG + " - setupBars: " + icon + " resource not found.");
                            exception.printStackTrace();
                        }
                    }
                    else {
                        navigationIconDrawable = CobaltFontManager.getCobaltFontDrawable(getApplicationContext(), icon, colorInt);
                    }

                    if (topBar != null) {
                        topBar.setNavigationIcon(navigationIconDrawable);
                    }
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (content.has(Cobalt.kBarsTitle) && actionBar != null) {
            try {
                String title = content.getString(Cobalt.kBarsTitle);
                if (title != null) {
                    actionBar.setDisplayShowTitleEnabled(true);
                    actionBar.setTitle(title);
                }
                else {
                    actionBar.setDisplayShowTitleEnabled(false);
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void setActionItemVisible(String actionName, boolean visible) {
        MenuItem menuItem = mMenuItemByNameMap.get(actionName);
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    public void setActionItemEnabled(String actionName, boolean enabled) {
        MenuItem menuItem = mMenuItemByNameMap.get(actionName);
        if (menuItem != null) {
            menuItem.setEnabled(enabled);
        }

        if (mMenuItemsHashMap.containsKey(actionName)) {
            ActionViewMenuItem actionViewMenuItem = mMenuItemsHashMap.get(actionName);
            actionViewMenuItem.setEnabled(enabled);
        }
    }

    protected void addMenuItem(Menu menu, int order, JSONObject action, final int id, String position, int barsColor) {
        try {
            final String name = action.getString(Cobalt.kActionName);
            String title = action.getString(Cobalt.kActionTitle);
            boolean visible = action.optBoolean(Cobalt.kActionVisible, true);
            boolean enabled = action.optBoolean(Cobalt.kActionEnabled, true);

            final MenuItem menuItem = menu.add(Menu.NONE, id, order, title);

            int showAsAction = MenuItemCompat.SHOW_AS_ACTION_IF_ROOM;
            switch(position) {
                case Cobalt.kPositionBottom:
                    showAsAction = MenuItemCompat.SHOW_AS_ACTION_ALWAYS;
                    break;
                case Cobalt.kPositionOverflow:
                    showAsAction = MenuItemCompat.SHOW_AS_ACTION_NEVER;
                    break;
            }
            MenuItemCompat.setShowAsAction(menuItem, showAsAction);

            ActionViewMenuItem actionView = new ActionViewMenuItem(this, action, barsColor);
            actionView.setActionViewMenuItemListener(this);
            actionView.setFragmentHostingWebView(mMenuListener);

            MenuItemCompat.setActionView(menuItem, actionView);
            menuItem.setVisible(visible);
            menuItem.setEnabled(enabled);
            mMenuItemsHashMap.put(name, actionView);
            //need this next hashmap to send onPressed when item is on overflow
            mMenuItemsIdMap.put(id, name);
            //need this next hashmap to set menuItem
            mMenuItemByNameMap.put(name, menuItem);
        }
        catch (JSONException exception) {
            if (Cobalt.DEBUG) {
                Log.w(Cobalt.TAG, TAG + "addMenuItem: action " + action.toString() + " format not supported, use at least {\n"
                        + "\tname: \"name\",\n"
                        + "\ttitle: \"title\",\n"
                        + "}");
            }

            exception.printStackTrace();
        }
    }

    public int getResourceIdentifier(String resource) {
        int resId = 0;

        try {
            if (resource == null || resource.length() == 0) {
                throw new IllegalArgumentException();
            }

            String[] resourceSplit = resource.split(":");
            String packageName, resourceName;
            switch(resourceSplit.length) {
                case 1:
                    packageName = getPackageName();
                    resourceName = resourceSplit[0];
                    break;
                case 2:
                    packageName = resourceSplit[0].length() != 0 ? resourceSplit[0] : getPackageName();
                    resourceName = resourceSplit[1];
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            resId = getResources().getIdentifier(resourceName, "drawable", packageName);
            if (resId == 0) {
                Log.w(Cobalt.TAG, TAG + "getResourceIdentifier: resource " + resource + " not found.");
            }
        }
        catch (IllegalArgumentException exception) {
            if (Cobalt.DEBUG) {
                Log.w(Cobalt.TAG, TAG + "getResourceIdentifier: resource " + resource + " format not supported, use resource, :resource or package:resource.");
            }
            exception.printStackTrace();
        }

        return resId;
    }

    /**
     * Applies the specified color to the @link{org.cobaltians.cobalt.R.id#fragments_container} background.
     * If color is null, default to "#FFFFFF" (white)
     * @param color the color to apply to the @link{org.cobaltians.cobalt.R.id#fragments_container} background
     */
    private void applyBackgroundColor(@Nullable String color) {
        View fragmentsContainer = findViewById(getFragmentsContainerId());
        if (fragmentsContainer == null) {
            if (Cobalt.DEBUG) {
                Log.e(Cobalt.TAG, TAG + " - applyBackgroundColor: no fragments container found with id " + getFragmentsContainerId());
            }

            return;
        }

        int backgroundColorInt = Cobalt.parseColor(Cobalt.BACKGROUND_COLOR_DEFAULT);
        try {
            backgroundColorInt = Cobalt.parseColor(color);
        }
        catch(IllegalArgumentException exception) {
            exception.printStackTrace();
        }

        fragmentsContainer.setBackgroundColor(backgroundColorInt);
    }

    /***********************************************************************************************
     *
     * BACK
     *
     **********************************************************************************************/

	/**
	 * Called when back button is pressed. 
	 * This method should NOT be overridden in subclasses.
	 */
	@Override
	public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(getFragmentContainerId());
        if (fragment != null
            && CobaltFragment.class.isAssignableFrom(fragment.getClass())) {
            ((CobaltFragment) fragment).askWebViewForBackPermission();
        }
        else {
            super.onBackPressed();
            if (Cobalt.DEBUG) Log.i(Cobalt.TAG,     TAG + " - onBackPressed: no fragment container found \n"
                                                    + " or fragment found is not an instance of CobaltFragment. \n"
                                                    + "Call super.onBackPressed()");
        }
	}

	/**
	 * Called from the contained {@link CobaltFragment} when the Web view has authorized the back event.
	 * This method should NOT be overridden in subclasses.
	 */
	public void back() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                backWithSuper();
            }
        });
	}

	private void backWithSuper() {
        // Catch IllegalStateException to fix a crash on API > 11
        try {
            super.onBackPressed();
        }
        catch (IllegalStateException exception) {
            exception.printStackTrace();
        }
	}

    public void popTo(String controller, String page, JSONObject data){
        Intent popToIntent = Cobalt.getInstance(this).getIntentForController(controller, page);

        if (popToIntent != null) {
            Bundle popToExtras = popToIntent.getBundleExtra(Cobalt.kExtras);
            String popToActivityClassName = popToExtras.getString(Cobalt.kActivity);

            try {
                Class<?> popToActivityClass = Class.forName(popToActivityClassName);

                boolean popToControllerFound = false;
                int popToControllerIndex = -1;

                for (int i = sActivitiesArrayList.size() - 1; i >= 0; i--) {
                    Activity oldActivity = sActivitiesArrayList.get(i);
                    Class<?> oldActivityClass = oldActivity.getClass();

                    Bundle oldBundle = oldActivity.getIntent().getExtras();
                    Bundle oldExtras = (oldBundle != null) ? oldBundle.getBundle(Cobalt.kExtras) : null;
                    String oldPage = (oldExtras != null) ? oldExtras.getString(Cobalt.kPage) : null;

                    if (oldPage == null
                        && CobaltActivity.class.isAssignableFrom(oldActivityClass)) {
                        Fragment fragment = ((CobaltActivity) oldActivity).getSupportFragmentManager().findFragmentById(((CobaltActivity) oldActivity).getFragmentContainerId());
                        if (fragment != null) {
                            oldExtras = fragment.getArguments();
                            oldPage = (oldExtras != null) ? oldExtras.getString(Cobalt.kPage) : null;
                        }
                    }

                    if (popToActivityClass.equals(oldActivityClass)
                        &&  (! CobaltActivity.class.isAssignableFrom(oldActivityClass)
                            || (CobaltActivity.class.isAssignableFrom(oldActivityClass) && page.equals(oldPage)))) {
                        popToControllerFound = true;
                        popToControllerIndex = i;
                        ((CobaltActivity)oldActivity).setDataNavigation(data);
                        break;
                    }
                }

                if (popToControllerFound) {
                    while (popToControllerIndex + 1 < sActivitiesArrayList.size()) {
                        sActivitiesArrayList.get(popToControllerIndex + 1).finish();
                    }
                }
                else if (Cobalt.DEBUG) Log.w(Cobalt.TAG, TAG + " - popTo: controller " + controller + (page == null ? "" : " with page " + page) + " not found in history. Abort.");
            }
            catch (ClassNotFoundException exception) {
                exception.printStackTrace();
            }
        }
        else if (Cobalt.DEBUG) Log.e(Cobalt.TAG, TAG + " - popTo: unable to pop to null controller");
    }

    public void dataForPop(JSONObject data) {
        if (sActivitiesArrayList.size() >= 2) {
            boolean cobaltActivityFound = false;
            int index = sActivitiesArrayList.size()-2;
            while (!cobaltActivityFound && index >= 0) {
                Activity activity = sActivitiesArrayList.get(index);
                if (CobaltActivity.class.isAssignableFrom(activity.getClass())) {
                    ((CobaltActivity) activity).setDataNavigation(data);
                    cobaltActivityFound = true;
                }
                index--;
            }
            if (!cobaltActivityFound && Cobalt.DEBUG) Log.e(Cobalt.TAG,  TAG + " - dataForPop: CobaltActivity not found");
        }
    }

    public JSONObject getDataNavigation() { return mDataNavigation; }

    public void setDataNavigation(JSONObject data) {
        this.mDataNavigation = data;
    }

    /***********************************************************************************************
     *
     * ACTION VIEW MENU ITEM
     *
     **********************************************************************************************/

    @Override
    public void onPressed(String name) {
        CobaltFragment fragment = mMenuItemsHashMap.get(name).getFragmentHostingWebView();
        if (fragment != null) {
            JSONObject message = new JSONObject();
            JSONObject data = new JSONObject();
            try {
                message.put(Cobalt.kJSType, Cobalt.JSTypeUI);
                message.put(Cobalt.kJSUIControl, Cobalt.JSControlBars);
                data.put(Cobalt.kJSAction, Cobalt.JSActionActionPressed);
                data.put(Cobalt.kJSActionName, name);
                message.put(Cobalt.kJSData, data);
                fragment.sendMessage(message);
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }
        else if (Cobalt.DEBUG) {
            Log.i(Cobalt.TAG, TAG + "onPressed " + name + ": fragment == null");
        }
    }
}
