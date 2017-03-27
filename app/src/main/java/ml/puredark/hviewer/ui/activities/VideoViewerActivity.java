package ml.puredark.hviewer.ui.activities;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.Toast;

import com.facebook.drawee.view.SimpleDraweeView;
import com.gc.materialdesign.views.ProgressBarCircularIndeterminate;
import com.shuyu.gsyvideoplayer.GSYPreViewManager;
import com.shuyu.gsyvideoplayer.GSYVideoPlayer;
import com.shuyu.gsyvideoplayer.listener.LockClickListener;
import com.shuyu.gsyvideoplayer.listener.StandardVideoAllCallBack;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.shuyu.gsyvideoplayer.video.CustomGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.GSYBaseVideoPlayer;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import butterknife.BindView;
import butterknife.ButterKnife;
import ml.puredark.hviewer.HViewerApplication;
import ml.puredark.hviewer.R;
import ml.puredark.hviewer.beans.Video;
import ml.puredark.hviewer.helpers.Logger;
import ml.puredark.hviewer.helpers.MDStatusBarCompat;
import ml.puredark.hviewer.http.ImageLoader;
import ml.puredark.hviewer.ui.listeners.SimpleVideoListener;

public class VideoViewerActivity extends BaseActivity {

    @BindView(R.id.coordinator_layout)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.web_view)
    WebView webView;
    @BindView(R.id.video_player)
    CustomGSYVideoPlayer videoPlayer;
    @BindView(R.id.progress_bar)
    ProgressBarCircularIndeterminate progressBar;

    private Video video;

    private OrientationUtils orientationUtils;

    // 表示页面加载完毕，允许第一次重定向，加载完毕后阻止用户点击广告的跳转
    private boolean mLoaded = false;
    // 表示是否应该拦截视频，当嵌入播放器播放失败则尝试使用WebView播放，此时不拦截视频
    private boolean shouldInterceptVideo = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_viewer);
        ButterKnife.bind(this);
        setContainer(coordinatorLayout);
        MDStatusBarCompat.setImageTransparent(this);

        // 开启按两次返回退出
        setDoubleBackExitEnabled(true);

        //获取传递过来的Video实例
        if (HViewerApplication.temp instanceof Video)
            video = (Video) HViewerApplication.temp;

        //获取失败则结束此界面
        if (video == null || TextUtils.isEmpty(video.content)) {
            Toast.makeText(this, "数据错误，请刷新后重试", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        HViewerApplication.temp = null;

        Logger.d("VideoViewerActivity", "video:" + video);

        orientationUtils = new OrientationUtils(this, videoPlayer);

        initWebView();
        initVideoPlayer();

        if (video.content.startsWith("http"))
            webView.loadUrl(video.content);
        else
            webView.loadData(video.content, "text/html", "utf-8");


    }

    private void initWebView(){
        WebSettings settings = webView.getSettings();
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
//        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
//        settings.setLoadWithOverviewMode(true);
//        settings.setUseWideViewPort(true);
        settings.setUserAgentString(getResources().getString(R.string.UA));
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(0); // 设置背景色
        webView.getBackground().setAlpha(0); // 设置填充透明度 范围：0-255
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Logger.d("VideoViewerActivity", "shouldOverrideUrlLoading:" + url);
                if (!mLoaded || url.contains(".mp4") || url.contains(".webm") || url.contains(".m3u8") || url.contains(".sdp")) {
                    Logger.d("VideoViewerActivity", "shouldOverrideUrlLoading: true");
                    webView.loadUrl(url);
                } else {
                    Logger.d("VideoViewerActivity", "shouldOverrideUrlLoading: false");
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                mLoaded = true;
                super.onPageFinished(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                Logger.d("VideoViewerActivity", "shouldInterceptRequest:" + url);
                if (shouldInterceptVideo && (url.contains(".mp4") || url.contains(".webm") || url.contains(".m3u8") || url.contains(".sdp"))) {
                    try {
                        Logger.d("VideoViewerActivity", "Intercepted video");
                        runOnUiThread(()->{
                            videoPlayer.setVisibility(View.VISIBLE);
                            boolean isCache = !(url.contains(".m3u8") || url.contains(".sdp"));
                            //设置播放url
                            videoPlayer.setUp(url, isCache , null, "");
                            //立即播放
                            videoPlayer.startPlayLogic();
                            progressBar.setVisibility(View.GONE);
                        });
                        return new WebResourceResponse(null,null,null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return super.shouldInterceptRequest(view, url);//正常加载
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            private View myView = null;
            private CustomViewCallback myCallback = null;

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (myCallback != null) {
                    myCallback.onCustomViewHidden();
                    myCallback = null;
                    return;
                }
                ViewGroup parent = (ViewGroup) webView.getParent();
                parent.removeView(webView);
                parent.addView(view);
                myView = view;
                view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                myCallback = callback;
                showStatus(false);
                //设置横屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }

            public void onHideCustomView() {
                if (myView != null) {
                    if (myCallback != null) {
                        myCallback.onCustomViewHidden();
                        myCallback = null;
                    }
                    ViewGroup parent = (ViewGroup) myView.getParent();
                    parent.removeView(myView);
                    parent.addView(webView);
                    myView = null;
                }
                showStatus(true);
                // 设置竖屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        });
    }

    private void initVideoPlayer(){
        videoPlayer.setVisibility(View.GONE);
        //初始化不打开外部的旋转
        orientationUtils.setEnable(false);
        //关闭自动旋转
        videoPlayer.setRotateViewAuto(false);
        //全屏动画效果
        videoPlayer.setShowFullAnimation(false);
        //需要横屏锁屏播放按键
        videoPlayer.setNeedLockFull(true);
        //非全屏下，不显示title
        videoPlayer.getTitleTextView().setVisibility(View.GONE);
        //非全屏下不显示返回键
        videoPlayer.getBackButton().setVisibility(View.GONE);
        //打开非全屏下触摸效果
        videoPlayer.setIsTouchWiget(true);
        //关闭进度条小窗口预览（不完善）
        videoPlayer.setOpenPreView(false);
        videoPlayer.getFullscreenButton().setOnClickListener(v -> {
            //直接横屏
            orientationUtils.resolveByClick();
            //第一个true是否需要隐藏actionbar，第二个true是否需要隐藏statusbar
            GSYBaseVideoPlayer fullScreenPlayer = videoPlayer.startWindowFullscreen(VideoViewerActivity.this, true, true);
            fullScreenPlayer.getBackButton().setOnClickListener((v1)->onBackPressed());
        });
        videoPlayer.getBackButton().setOnClickListener((v)->onBackPressed());
        videoPlayer.setStandardVideoAllCallBack(new SimpleVideoListener() {
            @Override
            public void onPrepared(String url, Object... objects) {
                super.onPrepared(url, objects);
                //开始播放了才能旋转和全屏
                orientationUtils.setEnable(true);
            }
            @Override
            public void onPlayError(String s, Object... objects) {
                videoPlayer.setVisibility(View.GONE);
                shouldInterceptVideo = false;
                webView.reload();
            }

            @Override
            public void onQuitFullscreen(String url, Object... objects) {
                orientationUtils.backToProtVideo();
            }
        });
        videoPlayer.setLockClickListener((view, lock) -> {
            //屏幕触摸锁定时禁止转屏
            orientationUtils.setEnable(!lock);
        });
    }

    @Override
    public void onBackPressed() {
        if (orientationUtils != null) {
            orientationUtils.backToProtVideo();
        }

        if (StandardGSYVideoPlayer.backFromWindowFull(this)) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onResume() {
        webView.onResume();
        super.onResume();
    }

    @Override
    public void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        webView.loadUrl("");
        webView.destroy();
        GSYVideoPlayer.releaseAllVideos();
        GSYPreViewManager.instance().releaseMediaPlayer();
        if (orientationUtils != null)
            orientationUtils.releaseListener();
        super.onDestroy();
    }

}
