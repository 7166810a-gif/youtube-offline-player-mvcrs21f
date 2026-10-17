package com.generated.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQ_CODE = 1001;

    // Tabs
    private static final int TAB_HOME = 0;
    private static final int TAB_PLAYLISTS = 1;
    private static final int TAB_FAVORITES = 2;
    private static final int TAB_LIBRARY = 3;
    private int currentTab = TAB_HOME;

    // Database & State
    private DatabaseHelper dbHelper;
    private List<VideoItem> currentFeedVideos = new ArrayList<>();
    private List<VideoItem> allVideos = new ArrayList<>();
    private String selectedFilterTag = "הכל";
    private String currentSearchQuery = "";

    // UI Layout containers
    private FrameLayout rootContainer;
    private LinearLayout mainContentContainer;
    private FrameLayout tabContentArea;
    private LinearLayout bottomNavBar;
    private HorizontalScrollView chipsScrollView;
    private LinearLayout chipsContainer;

    // Top Bar UI
    private LinearLayout topBar;
    private EditText searchInput;
    private boolean isSearchOpen = false;

    // Video Player Overlay / Container
    private FrameLayout playerContainer;
    private VideoView videoView;
    private LinearLayout playerControlsOverlay;
    private ImageView btnPlayPause;
    private ImageView btnFullscreen;
    private SeekBar playerSeekBar;
    private TextView txtCurrentTime;
    private TextView txtDuration;
    private TextView playerVideoTitle;
    private TextView playerVideoMeta;
    private ImageView playerFavoriteBtn;
    private ImageView playerAddToPlBtn;
    private LinearLayout upNextContainer;
    private ScrollView playerScrollView;

    // Player State
    private VideoItem currentlyPlayingItem = null;
    private boolean isPlaying = false;
    private boolean isPlayerVisible = false;
    private boolean isFullscreenMode = false;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force RTL layout orientation for Hebrew UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        dbHelper = DatabaseHelper.getInstance(this);

        buildMainUI();
        checkPermissionsAndLoad();
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, PERMISSION_REQ_CODE);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQ_CODE);
                return;
            }
        }
        startScanAndRefresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanAndRefresh();
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
                // Load seed videos so app is immediately useful offline even without granting permission
                VideoScanner.seedDemoVideos(this, dbHelper);
                refreshCurrentTab();
            }
        }
    }

    private void startScanAndRefresh() {
        VideoScanner.scanDeviceVideos(this, new VideoScanner.ScanCallback() {
            @Override
            public void onProgress(int count) {
                // Background scan progress
            }

            @Override
            public void onComplete(final int totalScanned) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshCurrentTab();
                    }
                });
            }
        });
    }

    private void buildMainUI() {
        rootContainer = new FrameLayout(this);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootContainer.setBackgroundColor(Color.WHITE);

        // Main content layout (Top Bar, Chips, Dynamic Tab Container, Bottom Nav)
        mainContentContainer = new LinearLayout(this);
        mainContentContainer.setOrientation(LinearLayout.VERTICAL);
        mainContentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Top YouTube Bar
        buildTopBar();
        mainContentContainer.addView(topBar);

        // 2. Filter chips scroll view
        buildFilterChips();
        mainContentContainer.addView(chipsScrollView);

        // 3. Tab content container (Scrollable list for Home, Playlists, etc.)
        tabContentArea = new FrameLayout(this);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        tabContentArea.setLayoutParams(tabParams);
        mainContentContainer.addView(tabContentArea);

        // 4. YouTube Bottom Navigation Bar
        buildBottomNav();
        mainContentContainer.addView(bottomNavBar);

        rootContainer.addView(mainContentContainer);

        // 5. Embedded Player View Overlay (YouTube-style view with video at top and Up-Next underneath)
        buildPlayerOverlay();
        rootContainer.addView(playerContainer);

        setContentView(rootContainer);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(12);
        topBar.setPadding(pad, pad, pad, pad);
        topBar.setBackgroundColor(Color.WHITE);

        // YouTube Style Logo + Title
        LinearLayout logoLayout = new LinearLayout(this);
        logoLayout.setOrientation(LinearLayout.HORIZONTAL);
        logoLayout.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logoIcon = new ImageView(this);
        logoIcon.setImageResource(R.drawable.ic_launcher);
        int iconSize = dp(32);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconP.setMargins(0, 0, dp(8), 0);
        logoIcon.setLayoutParams(iconP);

        TextView title = new TextView(this);
        title.setText("נגן יוטיוב");
        title.setTextColor(Color.parseColor("#0F0F0F"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        logoLayout.addView(logoIcon);
        logoLayout.addView(title);
        topBar.addView(logoLayout);

        // Spacer
        View spacer = new View(this);
        LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(0, 1, 1.0f);
        spacer.setLayoutParams(spParams);
        topBar.addView(spacer);

        // Search Input (Hidden by default, reveals on click)
        searchInput = new EditText(this);
        searchInput.setHint(R.string.search_hint);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        searchInput.setBackgroundResource(R.drawable.bg_chip);
        searchInput.setPadding(dp(12), dp(6), dp(12), dp(6));
        searchInput.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchP = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        searchP.setMargins(dp(8), 0, dp(8), 0);
        searchInput.setLayoutParams(searchP);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim();
                renderFeedVideos();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        topBar.addView(searchInput);

        // Search Toggle Button
        final ImageView searchBtn = new ImageView(this);
        searchBtn.setImageResource(R.drawable.ic_search);
        searchBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSearchOpen = !isSearchOpen;
                if (isSearchOpen) {
                    searchInput.setVisibility(View.VISIBLE);
                    searchInput.requestFocus();
                } else {
                    searchInput.setText("");
                    searchInput.setVisibility(View.GONE);
                }
            }
        });
        topBar.addView(searchBtn);

        // Scan Refresh Button
        ImageView scanBtn = new ImageView(this);
        scanBtn.setImageResource(R.drawable.ic_play);
        scanBtn.setColorFilter(Color.parseColor("#FF0000"));
        scanBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, R.string.scanning, Toast.LENGTH_SHORT).show();
                startScanAndRefresh();
            }
        });
        topBar.addView(scanBtn);
    }

    private void buildFilterChips() {
        chipsScrollView = new HorizontalScrollView(this);
        chipsScrollView.setHorizontalScrollBarEnabled(false);
        chipsScrollView.setPadding(dp(8), dp(4), dp(8), dp(8));
        chipsScrollView.setClipToPadding(false);

        chipsContainer = new LinearLayout(this);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipsScrollView.addView(chipsContainer);

        setupChips();
    }

    private void setupChips() {
        chipsContainer.removeAllViews();
        String[] filters = new String[]{"הכל", "מומלצים", "נצפו לאחרונה", "טבע", "טכנולוגיה", "מוזיקה", "בישול", "ספורט"};

        for (final String filter : filters) {
            final TextView chip = new TextView(this);
            chip.setText(filter);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);

            boolean isSelected = filter.equals(selectedFilterTag);
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setTextColor(Color.parseColor("#0F0F0F"));
            }

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(dp(4), 0, dp(4), 0);
            chip.setLayoutParams(p);

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFilterTag = filter;
                    setupChips();
                    renderFeedVideos();
                }
            });

            chipsContainer.addView(chip);
        }
    }

    private void buildBottomNav() {
        bottomNavBar = new LinearLayout(this);
        bottomNavBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavBar.setBackgroundColor(Color.WHITE);
        bottomNavBar.setElevation(dp(8));
        int pad = dp(8);
        bottomNavBar.setPadding(0, pad, 0, pad);

        // Divider on top of bottom nav
        View topBorder = new View(this);
        topBorder.setBackgroundColor(Color.parseColor("#E5E5E5"));
        topBorder.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        renderBottomNavTabs();
    }

    private void renderBottomNavTabs() {
        bottomNavBar.removeAllViews();

        int[] icons = {R.drawable.ic_home, R.drawable.ic_playlist, R.drawable.ic_favorite, R.drawable.ic_library};
        String[] titles = {
                getString(R.string.tab_home),
                getString(R.string.tab_playlists),
                getString(R.string.tab_favorites),
                getString(R.string.tab_library)
        };

        for (int i = 0; i < 4; i++) {
            final int tabIndex = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            item.setLayoutParams(p);

            ImageView iv = new ImageView(this);
            iv.setImageResource(icons[i]);
            int iconColor = (currentTab == tabIndex) ? Color.parseColor("#0F0F0F") : Color.parseColor("#606060");
            iv.setColorFilter(iconColor);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            iv.setLayoutParams(iconParams);

            TextView tv = new TextView(this);
            tv.setText(titles[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(iconColor);
            if (currentTab == tabIndex) tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(2), 0, 0);

            item.addView(iv);
            item.addView(tv);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentTab = tabIndex;
                    renderBottomNavTabs();
                    refreshCurrentTab();
                }
            });

            bottomNavBar.addView(item);
        }
    }

    private void refreshCurrentTab() {
        tabContentArea.removeAllViews();
        chipsScrollView.setVisibility(currentTab == TAB_HOME ? View.VISIBLE : View.GONE);

        switch (currentTab) {
            case TAB_HOME:
                loadHomeFeed();
                break;
            case TAB_PLAYLISTS:
                loadPlaylistsTab();
                break;
            case TAB_FAVORITES:
                loadFavoritesTab();
                break;
            case TAB_LIBRARY:
                loadLibraryTab();
                break;
        }
    }

    // =========================================================================
    // 1. HOME TAB (YouTube-style cards feed with thumbnails & recommendations)
    // =========================================================================

    private void loadHomeFeed() {
        allVideos = dbHelper.getAllVideos();
        // Calculate smart offline recommendations
        currentFeedVideos = RecommendationEngine.getRecommendedFeed(allVideos);
        renderFeedVideos();
    }

    private void renderFeedVideos() {
        tabContentArea.removeAllViews();

        List<VideoItem> filtered = new ArrayList<>();
        for (VideoItem item : currentFeedVideos) {
            boolean matchFilter = true;
            if (selectedFilterTag.equals("מומלצים")) {
                matchFilter = item.recommendationScore >= 3.0;
            } else if (selectedFilterTag.equals("נצפו לאחרונה")) {
                matchFilter = item.lastWatched > 0;
            } else if (!selectedFilterTag.equals("הכל")) {
                matchFilter = (item.tags != null && item.tags.contains(selectedFilterTag))
                        || (item.title != null && item.title.contains(selectedFilterTag));
            }

            boolean matchSearch = true;
            if (!currentSearchQuery.isEmpty()) {
                matchSearch = item.title.toLowerCase(Locale.ROOT).contains(currentSearchQuery.toLowerCase(Locale.ROOT))
                        || item.tags.toLowerCase(Locale.ROOT).contains(currentSearchQuery.toLowerCase(Locale.ROOT));
            }

            if (matchFilter && matchSearch) {
                filtered.add(item);
            }
        }

        if (filtered.isEmpty()) {
            tabContentArea.addView(buildEmptyView(getString(R.string.empty_home)));
            return;
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (final VideoItem video : filtered) {
            View card = createYouTubeVideoCard(video, true);
            list.addView(card);
        }

        scrollView.addView(list);
        tabContentArea.addView(scrollView);
    }

    /**
     * Creates an authentic YouTube-style video card:
     * Wide 16:9 thumbnail, duration badge at bottom-right, channel avatar, title, views & date below.
     */
    private View createYouTubeVideoCard(final VideoItem video, boolean showRecommendationBadge) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setPadding(0, 0, 0, dp(16));
        card.setBackgroundColor(Color.WHITE);

        // 1. Thumbnail Container (16:9 aspect ratio)
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = (int) (width * 9.0f / 16.0f);

        FrameLayout thumbFrame = new FrameLayout(this);
        thumbFrame.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        thumbFrame.setBackgroundColor(Color.parseColor("#1F1F1F"));

        // Generated Thumbnail Image
        final ImageView thumbImage = new ImageView(this);
        thumbImage.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        thumbImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbFrame.addView(thumbImage);

        // Generate / retrieve video thumbnail
        loadThumbnailAsync(video, thumbImage);

        // Duration Badge (Bottom-End of Thumbnail)
        TextView durationBadge = new TextView(this);
        durationBadge.setText(video.getFormattedDuration());
        durationBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        durationBadge.setTextColor(Color.WHITE);
        durationBadge.setTypeface(Typeface.DEFAULT_BOLD);
        durationBadge.setBackgroundResource(R.drawable.bg_duration_badge);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
        badgeParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        durationBadge.setLayoutParams(badgeParams);
        thumbFrame.addView(durationBadge);

        // Offline / Recommendation Badge (Top-Start)
        if (showRecommendationBadge && video.recommendationScore >= 4.0) {
            TextView recBadge = new TextView(this);
            recBadge.setText(R.string.recommendations_badge);
            recBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            recBadge.setTextColor(Color.WHITE);
            recBadge.setTypeface(Typeface.DEFAULT_BOLD);
            recBadge.setBackgroundResource(R.drawable.bg_recommendation_badge);
            FrameLayout.LayoutParams recParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            recParams.gravity = Gravity.TOP | Gravity.START;
            recParams.setMargins(dp(8), dp(8), dp(8), dp(8));
            recBadge.setLayoutParams(recParams);
            thumbFrame.addView(recBadge);
        }

        card.addView(thumbFrame);

        // 2. Info Bar underneath thumbnail: Avatar + (Title, Views, Date) + More actions
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setPadding(dp(12), dp(12), dp(12), 0);

        // Channel Avatar placeholder
        ImageView avatar = new ImageView(this);
        int avatarSize = dp(36);
        LinearLayout.LayoutParams avParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avParams.setMargins(0, dp(2), dp(12), 0);
        avatar.setLayoutParams(avParams);
        avatar.setImageBitmap(createChannelAvatar(video.title));
        infoRow.addView(avatar);

        // Video text meta
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(textColParams);

        TextView titleView = new TextView(this);
        titleView.setText(video.title);
        titleView.setTextColor(Color.parseColor("#0F0F0F"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(2);
        textCol.addView(titleView);

        TextView subMeta = new TextView(this);
        String metaText = String.format(Locale.getDefault(), "%d צפיות • %s", video.viewCount, video.getFormattedSize());
        if (video.completedCount > 0) {
            metaText += " • הושלם " + video.completedCount + " פעמים";
        }
        subMeta.setText(metaText);
        subMeta.setTextColor(Color.parseColor("#606060"));
        subMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subMeta.setPadding(0, dp(4), 0, 0);
        textCol.addView(subMeta);

        infoRow.addView(textCol);

        // Action menu icon (Favorite / Add to playlist)
        ImageView actionBtn = new ImageView(this);
        actionBtn.setImageResource(video.isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        actionBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        actionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                video.isFavorite = !video.isFavorite;
                dbHelper.toggleFavorite(video.id, video.isFavorite);
                Toast.makeText(MainActivity.this, video.isFavorite ? "נוסף למועדפים" : "הוסר ממועדפים", Toast.LENGTH_SHORT).show();
                renderFeedVideos();
            }
        });
        infoRow.addView(actionBtn);

        card.addView(infoRow);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo(video);
            }
        });

        return card;
    }

    // =========================================================================
    // 2. VIDEO PLAYER VIEW (YouTube-style top player + Suggested "Up Next" below)
    // =========================================================================

    private void buildPlayerOverlay() {
        playerContainer = new FrameLayout(this);
        playerContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playerContainer.setBackgroundColor(Color.WHITE);
        playerContainer.setVisibility(View.GONE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Video Player Box
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = (int) (width * 9.0f / 16.0f);

        FrameLayout videoBox = new FrameLayout(this);
        videoBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        videoBox.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        videoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        videoBox.addView(videoView);

        // Controls overlay
        playerControlsOverlay = new LinearLayout(this);
        playerControlsOverlay.setOrientation(LinearLayout.VERTICAL);
        playerControlsOverlay.setBackgroundColor(Color.parseColor("#4D000000"));
        playerControlsOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top bar in player (Close back button)
        LinearLayout topControls = new LinearLayout(this);
        topControls.setOrientation(LinearLayout.HORIZONTAL);
        topControls.setPadding(dp(8), dp(8), dp(8), dp(8));

        ImageView btnClose = new ImageView(this);
        btnClose.setImageResource(R.drawable.ic_close);
        btnClose.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePlayer();
            }
        });
        topControls.addView(btnClose);
        playerControlsOverlay.addView(topControls);

        // Center play/pause
        FrameLayout centerHolder = new FrameLayout(this);
        LinearLayout.LayoutParams centerP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        centerHolder.setLayoutParams(centerP);

        btnPlayPause = new ImageView(this);
        btnPlayPause.setImageResource(R.drawable.ic_pause);
        FrameLayout.LayoutParams btnP = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER);
        btnPlayPause.setLayoutParams(btnP);
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });
        centerHolder.addView(btnPlayPause);
        playerControlsOverlay.addView(centerHolder);

        // Bottom progress & fullscreen
        LinearLayout bottomControls = new LinearLayout(this);
        bottomControls.setOrientation(LinearLayout.HORIZONTAL);
        bottomControls.setGravity(Gravity.CENTER_VERTICAL);
        bottomControls.setPadding(dp(12), dp(4), dp(12), dp(8));

        txtCurrentTime = new TextView(this);
        txtCurrentTime.setText("00:00");
        txtCurrentTime.setTextColor(Color.WHITE);
        txtCurrentTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bottomControls.addView(txtCurrentTime);

        playerSeekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        playerSeekBar.setLayoutParams(seekParams);
        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    videoView.seekTo(progress);
                    txtCurrentTime.setText(formatTime(progress));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        bottomControls.addView(playerSeekBar);

        txtDuration = new TextView(this);
        txtDuration.setText("00:00");
        txtDuration.setTextColor(Color.WHITE);
        txtDuration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bottomControls.addView(txtDuration);

        btnFullscreen = new ImageView(this);
        btnFullscreen.setImageResource(R.drawable.ic_fullscreen);
        btnFullscreen.setPadding(dp(8), 0, dp(4), 0);
        btnFullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFullscreen();
            }
        });
        bottomControls.addView(btnFullscreen);

        playerControlsOverlay.addView(bottomControls);
        videoBox.addView(playerControlsOverlay);

        // Tap video to toggle controls visibility
        videoBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playerControlsOverlay.setVisibility(
                        playerControlsOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        layout.addView(videoBox);

        // 2. Video Details & "Up Next" Scrollable Area
        playerScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        playerScrollView.setLayoutParams(scrollParams);

        LinearLayout playerContent = new LinearLayout(this);
        playerContent.setOrientation(LinearLayout.VERTICAL);
        playerContent.setPadding(dp(16), dp(16), dp(16), dp(24));

        playerVideoTitle = new TextView(this);
        playerVideoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        playerVideoTitle.setTextColor(Color.parseColor("#0F0F0F"));
        playerVideoTitle.setTypeface(Typeface.DEFAULT_BOLD);
        playerContent.addView(playerVideoTitle);

        playerVideoMeta = new TextView(this);
        playerVideoMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        playerVideoMeta.setTextColor(Color.parseColor("#606060"));
        playerVideoMeta.setPadding(0, dp(6), 0, dp(12));
        playerContent.addView(playerVideoMeta);

        // Action Buttons Row (Like/Favorite, Add to Playlist)
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, 0, 0, dp(16));

        // Favorite button
        playerFavoriteBtn = new ImageView(this);
        playerFavoriteBtn.setImageResource(R.drawable.ic_favorite_border);
        playerFavoriteBtn.setBackgroundResource(R.drawable.bg_chip);
        playerFavoriteBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams favP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        favP.setMargins(0, 0, dp(12), 0);
        playerFavoriteBtn.setLayoutParams(favP);
        playerFavoriteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentlyPlayingItem != null) {
                    currentlyPlayingItem.isFavorite = !currentlyPlayingItem.isFavorite;
                    dbHelper.toggleFavorite(currentlyPlayingItem.id, currentlyPlayingItem.isFavorite);
                    updatePlayerFavoriteBtn();
                    Toast.makeText(MainActivity.this, currentlyPlayingItem.isFavorite ? "נוסף למועדפים" : "הוסר ממועדפים", Toast.LENGTH_SHORT).show();
                }
            }
        });
        actionRow.addView(playerFavoriteBtn);

        // Add to Playlist button
        playerAddToPlBtn = new ImageView(this);
        playerAddToPlBtn.setImageResource(R.drawable.ic_playlist);
        playerAddToPlBtn.setBackgroundResource(R.drawable.bg_chip);
        playerAddToPlBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        playerAddToPlBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentlyPlayingItem != null) {
                    showAddToPlaylistDialog(currentlyPlayingItem);
                }
            }
        });
        actionRow.addView(playerAddToPlBtn);

        playerContent.addView(actionRow);

        // Divider
        View sep = new View(this);
        sep.setBackgroundColor(Color.parseColor("#E5E5E5"));
        sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        playerContent.addView(sep);

        // "Up Next" Header
        TextView upNextTitle = new TextView(this);
        upNextTitle.setText(R.string.up_next);
        upNextTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        upNextTitle.setTextColor(Color.parseColor("#0F0F0F"));
        upNextTitle.setTypeface(Typeface.DEFAULT_BOLD);
        upNextTitle.setPadding(0, dp(16), 0, dp(12));
        playerContent.addView(upNextTitle);

        // Up next container
        upNextContainer = new LinearLayout(this);
        upNextContainer.setOrientation(LinearLayout.VERTICAL);
        playerContent.addView(upNextContainer);

        playerScrollView.addView(playerContent);
        layout.addView(playerScrollView);

        playerContainer.addView(layout);
    }

    private void playVideo(final VideoItem video) {
        this.currentlyPlayingItem = video;
        isPlayerVisible = true;
        playerContainer.setVisibility(View.VISIBLE);

        playerVideoTitle.setText(video.title);
        playerVideoMeta.setText(String.format(Locale.getDefault(), "%d צפיות • %s", video.viewCount + 1, video.getFormattedDuration()));
        updatePlayerFavoriteBtn();

        // Increment watch history & local metrics
        dbHelper.recordView(video.id);
        video.viewCount++;

        // Setup VideoView
        File file = new File(video.path);
        if (file.exists()) {
            videoView.setVideoPath(video.path);
        } else {
            // Demo fallback: simulate playback if file was sample seed
            videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/"));
        }

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                int dur = videoView.getDuration();
                if (dur <= 0) dur = (int) video.duration;
                playerSeekBar.setMax(dur);
                txtDuration.setText(formatTime(dur));
                videoView.start();
                isPlaying = true;
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                startProgressTracker();
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                isPlaying = false;
                btnPlayPause.setImageResource(R.drawable.ic_play);
                dbHelper.recordCompleted(video.id);
                video.completedCount++;
                Toast.makeText(MainActivity.this, "צפייה הושלמה! ההמלצות שלך עודכנו", Toast.LENGTH_SHORT).show();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                // Video file codec or demo placeholder simulated
                isPlaying = true;
                playerSeekBar.setMax((int) (video.duration > 0 ? video.duration : 180000));
                txtDuration.setText(video.getFormattedDuration());
                startProgressTracker();
                return true;
            }
        });

        // Load Up Next Recommendations
        loadUpNextList(video);
    }

    private void loadUpNextList(VideoItem current) {
        upNextContainer.removeAllViews();
        List<VideoItem> upNextList = RecommendationEngine.getUpNextVideos(allVideos, current);

        for (final VideoItem item : upNextList) {
            // Horizontal compact card YouTube style for "Up Next"
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setBackgroundColor(Color.WHITE);

            // Thumbnail
            int w = dp(120);
            int h = dp(70);
            FrameLayout thumbBox = new FrameLayout(this);
            thumbBox.setLayoutParams(new LinearLayout.LayoutParams(w, h));
            thumbBox.setBackgroundColor(Color.parseColor("#272727"));

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            loadThumbnailAsync(item, iv);
            thumbBox.addView(iv);

            TextView dur = new TextView(this);
            dur.setText(item.getFormattedDuration());
            dur.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            dur.setTextColor(Color.WHITE);
            dur.setBackgroundResource(R.drawable.bg_duration_badge);
            FrameLayout.LayoutParams durP = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            durP.gravity = Gravity.BOTTOM | Gravity.END;
            durP.setMargins(dp(4), dp(4), dp(4), dp(4));
            dur.setLayoutParams(durP);
            thumbBox.addView(dur);

            row.addView(thumbBox);

            // Info
            LinearLayout metaCol = new LinearLayout(this);
            metaCol.setOrientation(LinearLayout.VERTICAL);
            metaCol.setPadding(dp(10), 0, dp(6), 0);

            TextView t = new TextView(this);
            t.setText(item.title);
            t.setTextColor(Color.parseColor("#0F0F0F"));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setMaxLines(2);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            metaCol.addView(t);

            TextView sub = new TextView(this);
            sub.setText(item.viewCount + " צפיות • מומלץ");
            sub.setTextColor(Color.parseColor("#606060"));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            sub.setPadding(0, dp(3), 0, 0);
            metaCol.addView(sub);

            row.addView(metaCol);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playVideo(item);
                    playerScrollView.smoothScrollTo(0, 0);
                }
            });

            upNextContainer.addView(row);
        }
    }

    private void updatePlayerFavoriteBtn() {
        if (currentlyPlayingItem != null) {
            playerFavoriteBtn.setImageResource(
                    currentlyPlayingItem.isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        }
    }

    private void togglePlayPause() {
        if (isPlaying) {
            videoView.pause();
            isPlaying = false;
            btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            videoView.start();
            isPlaying = true;
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        }
    }

    private void toggleFullscreen() {
        isFullscreenMode = !isFullscreenMode;
        Window window = getWindow();
        if (isFullscreenMode) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            playerScrollView.setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
            bottomNavBar.setVisibility(View.GONE);
            chipsScrollView.setVisibility(View.GONE);
            videoView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            playerScrollView.setVisibility(View.VISIBLE);
            topBar.setVisibility(View.VISIBLE);
            bottomNavBar.setVisibility(View.VISIBLE);
            if (currentTab == TAB_HOME) chipsScrollView.setVisibility(View.VISIBLE);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int h = (int) (metrics.widthPixels * 9.0f / 16.0f);
            videoView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, h));
        }
    }

    private void startProgressTracker() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (videoView != null && isPlaying) {
                    int pos = videoView.getCurrentPosition();
                    playerSeekBar.setProgress(pos);
                    txtCurrentTime.setText(formatTime(pos));
                }
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void closePlayer() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        if (isFullscreenMode) {
            toggleFullscreen();
        }
        isPlaying = false;
        isPlayerVisible = false;
        playerContainer.setVisibility(View.GONE);
        refreshCurrentTab();
    }

    // =========================================================================
    // 3. PLAYLISTS TAB
    // =========================================================================

    private void loadPlaylistsTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Create new playlist button
        TextView btnCreate = new TextView(this);
        btnCreate.setText("+ " + getString(R.string.create_playlist));
        btnCreate.setTextColor(Color.WHITE);
        btnCreate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnCreate.setTypeface(Typeface.DEFAULT_BOLD);
        btnCreate.setGravity(Gravity.CENTER);
        btnCreate.setBackgroundResource(R.drawable.bg_button_red);
        btnCreate.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreatePlaylistDialog();
            }
        });
        content.addView(btnCreate);

        List<PlaylistItem> playlists = dbHelper.getPlaylists();
        if (playlists.isEmpty()) {
            content.addView(buildEmptyView(getString(R.string.empty_playlists)));
        } else {
            for (final PlaylistItem pl : playlists) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(8), dp(16), dp(8), dp(16));
                item.setBackgroundResource(R.drawable.bg_chip);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dp(12), 0, 0);
                item.setLayoutParams(lp);

                ImageView icon = new ImageView(this);
                icon.setImageResource(R.drawable.ic_playlist);
                icon.setPadding(0, 0, dp(12), 0);
                item.addView(icon);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                col.setLayoutParams(colP);

                TextView name = new TextView(this);
                name.setText(pl.name);
                name.setTextColor(Color.parseColor("#0F0F0F"));
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                col.addView(name);

                TextView count = new TextView(this);
                count.setText(pl.videoCount + " סרטונים");
                count.setTextColor(Color.parseColor("#606060"));
                count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                col.addView(count);

                item.addView(col);

                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPlaylistVideos(pl);
                    }
                });

                content.addView(item);
            }
        }

        scroll.addView(content);
        tabContentArea.addView(scroll);
    }

    private void showPlaylistVideos(PlaylistItem pl) {
        tabContentArea.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // Header
        TextView header = new TextView(this);
        header.setText(pl.name + " (" + pl.videoCount + ")");
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(16), dp(16), dp(16), dp(12));
        list.addView(header);

        List<VideoItem> items = dbHelper.getPlaylistVideos(pl.id);
        if (items.isEmpty()) {
            list.addView(buildEmptyView("אין סרטונים ברשימת השמעה זו."));
        } else {
            for (VideoItem v : items) {
                list.addView(createYouTubeVideoCard(v, false));
            }
        }

        scroll.addView(list);
        tabContentArea.addView(scroll);
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_playlist);

        final EditText input = new EditText(this);
        input.setHint(R.string.playlist_name);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        builder.setView(input);

        builder.setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    dbHelper.createPlaylist(name);
                    loadPlaylistsTab();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showAddToPlaylistDialog(final VideoItem video) {
        final List<PlaylistItem> playlists = dbHelper.getPlaylists();
        if (playlists.isEmpty()) {
            Toast.makeText(this, "אנא צור רשימת השמעה תחילה בלשונית הפלייליסטים", Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            names[i] = playlists.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_to_playlist);
        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dbHelper.addVideoToPlaylist(playlists.get(which).id, video.id);
                Toast.makeText(MainActivity.this, "נוסף לרשימת ההשמעה: " + playlists.get(which).name, Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    // =========================================================================
    // 4. FAVORITES TAB
    // =========================================================================

    private void loadFavoritesTab() {
        List<VideoItem> favorites = dbHelper.getFavoriteVideos();
        if (favorites.isEmpty()) {
            tabContentArea.addView(buildEmptyView(getString(R.string.empty_favorites)));
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (VideoItem v : favorites) {
            list.addView(createYouTubeVideoCard(v, false));
        }

        scroll.addView(list);
        tabContentArea.addView(scroll);
    }

    // =========================================================================
    // 5. LIBRARY TAB (History & Smart offline statistics)
    // =========================================================================

    private void loadLibraryTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));

        // Stats card
        LinearLayout statsCard = new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.VERTICAL);
        statsCard.setBackgroundResource(R.drawable.bg_chip);
        statsCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView statsTitle = new TextView(this);
        statsTitle.setText(R.string.stats_title);
        statsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statsTitle.setTextColor(Color.parseColor("#0F0F0F"));
        statsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statsCard.addView(statsTitle);

        int totalVideos = dbHelper.getTotalVideosCount();
        int totalViews = dbHelper.getTotalViewsCount();

        TextView statsInfo = new TextView(this);
        statsInfo.setText("• סך סרטונים מאוחסנים: " + totalVideos + "\n• סך צפיות מקומיות: " + totalViews + "\n• מנוע המלצות: פועל אופליין 100%");
        statsInfo.setTextColor(Color.parseColor("#404040"));
        statsInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statsInfo.setPadding(0, dp(8), 0, 0);
        statsCard.addView(statsInfo);

        content.addView(statsCard);

        // History Section
        TextView histTitle = new TextView(this);
        histTitle.setText(R.string.history_title);
        histTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        histTitle.setTextColor(Color.parseColor("#0F0F0F"));
        histTitle.setTypeface(Typeface.DEFAULT_BOLD);
        histTitle.setPadding(0, dp(20), 0, dp(12));
        content.addView(histTitle);

        List<VideoItem> history = dbHelper.getHistoryVideos();
        if (history.isEmpty()) {
            content.addView(buildEmptyView(getString(R.string.empty_history)));
        } else {
            for (VideoItem v : history) {
                content.addView(createYouTubeVideoCard(v, false));
            }
        }

        scroll.addView(content);
        tabContentArea.addView(scroll);
    }

    // =========================================================================
    // HELPER UTILITIES & DRAWABLES
    // =========================================================================

    private View buildEmptyView(String text) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(32), dp(48), dp(32), dp(48));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        icon.setAlpha(0.6f);
        int s = dp(64);
        icon.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        layout.addView(icon);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#606060"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(16), 0, 0);
        layout.addView(tv);

        return layout;
    }

    private void loadThumbnailAsync(final VideoItem video, final ImageView imageView) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap thumb = null;
                try {
                    File f = new File(video.path);
                    if (f.exists()) {
                        thumb = ThumbnailUtils.createVideoThumbnail(video.path, MediaStore.Images.Thumbnails.MINI_KIND);
                    }
                } catch (Exception ignored) {}

                if (thumb == null) {
                    thumb = generatePlaceholderThumbnail(video.title, (int) video.id);
                }

                final Bitmap finalThumb = thumb;
                imageView.post(new Runnable() {
                    @Override
                    public void run() {
                        imageView.setImageBitmap(finalThumb);
                    }
                });
            }
        }).start();
    }

    private Bitmap generatePlaceholderThumbnail(String title, int seed) {
        int w = 640;
        int h = 360;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);

        // Dynamic harmonious gradient-like solid colors based on seed
        int[] palette = {
                0xFF1A237E, 0xFF0D47A1, 0xFF004D40, 0xFF311B92, 0xFF880E4F, 0xFFBF360C, 0xFF212121
        };
        int bg = palette[Math.abs(seed) % palette.length];
        canvas.drawColor(bg);

        // Center YouTube play icon outline
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setAlpha(40);
        canvas.drawCircle(w / 2f, h / 2f, 70, p);

        p.setColor(Color.WHITE);
        p.setAlpha(200);
        p.setTextSize(32);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);

        String shortTitle = (title != null && title.length() > 25) ? title.substring(0, 25) + "..." : (title != null ? title : "");
        canvas.drawText(shortTitle, w / 2f, h / 2f + 12, p);

        return bm;
    }

    private Bitmap createChannelAvatar(String title) {
        int size = dp(36);
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#CC0000"));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(size * 0.48f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);

        String letter = "Y";
        if (title != null && !title.trim().isEmpty()) {
            letter = title.trim().substring(0, 1).toUpperCase(Locale.getDefault());
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(letter, size / 2f, y, paint);

        return bm;
    }

    private String formatTime(int ms) {
        long sec = (ms / 1000) % 60;
        long min = (ms / (1000 * 60)) % 60;
        long hours = ms / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, min, sec);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    @Override
    public void onBackPressed() {
        if (isPlayerVisible) {
            if (isFullscreenMode) {
                toggleFullscreen();
            } else {
                closePlayer();
            }
            return;
        }
        if (currentTab != TAB_HOME) {
            currentTab = TAB_HOME;
            renderBottomNavTabs();
            refreshCurrentTab();
            return;
        }
        super.onBackPressed();
    }
}
